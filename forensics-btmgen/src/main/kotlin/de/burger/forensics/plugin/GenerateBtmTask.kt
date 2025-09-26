package de.burger.forensics.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.lexer.KtTokens
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.regex.Pattern

abstract class GenerateBtmTask : DefaultTask() {

    @get:Input
    abstract val srcDirs: ListProperty<String>

    @get:Input
    abstract val packagePrefix: Property<String>

    @get:Input
    abstract val helperFqn: Property<String>

    @get:Input
    abstract val entryExit: Property<Boolean>

    @get:Input
    abstract val trackedVars: ListProperty<String>

    @get:Input
    abstract val includeJava: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    protected val kotlinSourceFiles: List<File>
        get() = resolveFiles(withExtension = ".kt")

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    protected val javaSourceFiles: List<File>
        get() = if (includeJava.getOrElse(false)) resolveFiles(withExtension = ".java") else emptyList()

    private fun resolveFiles(withExtension: String): List<File> {
        val directories = srcDirs.orNull?.map { project.file(it) }?.filter { it.exists() } ?: emptyList()
        val result = mutableListOf<File>()
        directories.forEach { dir ->
            Files.walk(dir.toPath()).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(withExtension) }
                    .forEach { result.add(it.toFile()) }
            }
        }
        return result.sortedBy { it.absolutePath }
    }

    @TaskAction
    fun generate() {
        val outputDirectory = outputDir.get().asFile
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }
        val outputFile = outputDirectory.resolve("tracing.btm")
        val helper = helperFqn.get()
        val prefix = packagePrefix.orNull?.takeIf { it.isNotBlank() }
        val tracked = trackedVars.orNull?.toSet() ?: emptySet()
        val includeEntryExit = entryExit.getOrElse(true)

        val rules = mutableListOf<String>()
        val ktFiles = kotlinSourceFiles
        if (ktFiles.isNotEmpty()) {
            val envHolder = createEnvironment()
            try {
                val psiFactory = KtPsiFactory(envHolder.environment.project, false)
                ktFiles.forEach { file ->
                    val text = file.readText()
                    val ktFile = psiFactory.createFile(file.name, text)
                    val fileRules = processKotlinFile(ktFile, text, helper, prefix, tracked, includeEntryExit)
                    rules += fileRules
                }
            } finally {
                Disposer.dispose(envHolder.disposable)
            }
        }

        if (includeJava.getOrElse(false)) {
            javaSourceFiles.forEach { file ->
                val text = file.readText()
                val fileRules = processJavaFile(text, helper, prefix, includeEntryExit)
                rules += fileRules
            }
        }

        val header = buildString {
            append("# Generated at ")
            append(Instant.now())
            append(" by de.burger.forensics.btmgen\n")
            append("# Helper: ")
            append(helper)
            append('\n')
            if (prefix != null) {
                append("# Package prefix filter: ")
                append(prefix)
                append('\n')
            }
            if (tracked.isNotEmpty()) {
                append("# Tracked variables: ")
                append(tracked.joinToString(", "))
                append('\n')
            }
        }

        val content = buildString {
            append(header)
            if (rules.isEmpty()) {
                append("# No matching Kotlin sources were found.\n")
            } else {
                append('\n')
                rules.joinTo(this, separator = "\n\n")
                append('\n')
            }
        }
        outputFile.writeText(content)
    }

    private fun createEnvironment(): EnvironmentHolder {
        val disposable: Disposable = Disposer.newDisposable("btmgen")
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, "btmgen")
            put(JVMConfigurationKeys.JDK_HOME, File(System.getProperty("java.home")))
            put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, SilentMessageCollector)
        }
        val environment = KotlinCoreEnvironment.createForProduction(
            disposable,
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
        return EnvironmentHolder(environment, disposable)
    }

    private fun processKotlinFile(
        ktFile: KtFile,
        text: String,
        helper: String,
        prefix: String?,
        tracked: Set<String>,
        includeEntryExit: Boolean
    ): List<String> {
        val lineIndex = LineIndex(text)
        val functions = mutableListOf<KtNamedFunction>()
        ktFile.accept(object : KtTreeVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                if (!function.isLocalFunction()) {
                    functions += function
                }
                super.visitNamedFunction(function)
            }
        })
        return functions.flatMap { function ->
            collectRulesForFunction(function, lineIndex, helper, prefix, tracked, includeEntryExit)
        }
    }

    private fun collectRulesForFunction(
        function: KtNamedFunction,
        lineIndex: LineIndex,
        helper: String,
        prefix: String?,
        tracked: Set<String>,
        includeEntryExit: Boolean
    ): List<String> {
        val methodName = function.name ?: return emptyList()
        val className = resolveBinaryClassName(function) ?: return emptyList()
        if (prefix != null && !className.startsWith(prefix)) {
            return emptyList()
        }
        val rules = mutableListOf<String>()
        if (includeEntryExit) {
            rules += buildEntryRule(helper, className, methodName)
            rules += buildExitRule(helper, className, methodName)
        }
        val body = function.bodyExpression ?: return rules
        val context = KotlinFunctionContext(helper, className, methodName, lineIndex, tracked)
        body.accept(object : KtTreeVisitorVoid() {
            override fun visitIfExpression(expression: KtIfExpression) {
                rules += buildIfRules(context, expression)
                super.visitIfExpression(expression)
            }

            override fun visitWhenExpression(expression: KtWhenExpression) {
                rules += buildWhenRules(context, expression)
                super.visitWhenExpression(expression)
            }

            override fun visitIsExpression(expression: KtIsExpression) {
                rules += buildIsRules(context, expression)
                super.visitIsExpression(expression)
            }

            override fun visitBinaryExpression(expression: KtBinaryExpression) {
                rules += buildWriteRule(context, expression)
                super.visitBinaryExpression(expression)
            }
        })
        return rules
    }

    private fun resolveBinaryClassName(function: KtNamedFunction): String? {
        val classOrObject = function.containingClassOrObject
        val packageName = function.containingKtFile.packageFqName.takeIf { !it.isRoot }?.asString().orEmpty()
        return if (classOrObject == null) {
            val fileName = function.containingKtFile.name.substringBeforeLast('.') + "Kt"
            if (packageName.isEmpty()) fileName else "$packageName.$fileName"
        } else {
            val nameSegments = mutableListOf<String>()
            var current: org.jetbrains.kotlin.psi.KtClassOrObject? = classOrObject
            while (current != null) {
                val simpleName = when (current) {
                    is KtObjectDeclaration -> {
                        when {
                            current.name != null -> current.name!!
                            current.isCompanion() -> "Companion"
                            else -> return null
                        }
                    }
                    else -> current.name ?: return null
                }
                nameSegments.add(simpleName)
                current = current.containingClassOrObject
            }
            val binaryName = nameSegments.asReversed().joinToString("$")
            if (packageName.isEmpty()) binaryName else "$packageName.$binaryName"
        }
    }

    private fun buildEntryRule(helper: String, className: String, methodName: String): String =
        """
        RULE enter@${className}.${methodName}
        CLASS ${className}
        METHOD ${methodName}
        HELPER ${helper}
        AT ENTRY
        IF true
        DO enter("${className}","${methodName}", ${'$'}LINE)
        ENDRULE
        """.trimIndent()

    private fun buildExitRule(helper: String, className: String, methodName: String): String =
        """
        RULE exit@${className}.${methodName}
        CLASS ${className}
        METHOD ${methodName}
        HELPER ${helper}
        AT EXIT
        IF true
        DO exit("${className}","${methodName}", ${'$'}LINE)
        ENDRULE
        """.trimIndent()

    private fun buildIfRules(context: KotlinFunctionContext, expression: KtIfExpression): List<String> {
        val condition = expression.condition ?: return emptyList()
        val line = context.lineIndex.lineAt(expression.startOffset)
        val conditionText = escape(condition.text)
        val className = context.className
        val methodName = context.methodName
        val helper = context.helperFqn
        val trueRule = """
            RULE ${className}.${methodName}:${line}:if-true
            CLASS ${className}
            METHOD ${methodName}
            HELPER ${helper}
            AT LINE ${line}
            IF (${condition.text})
            DO iff("${className}","${methodName}",${line},"${conditionText}", true)
            ENDRULE
        """.trimIndent()
        val falseRule = """
            RULE ${className}.${methodName}:${line}:if-false
            CLASS ${className}
            METHOD ${methodName}
            HELPER ${helper}
            AT LINE ${line}
            IF (!(${condition.text}))
            DO iff("${className}","${methodName}",${line},"${conditionText}", false)
            ENDRULE
        """.trimIndent()
        return listOf(trueRule, falseRule)
    }

    private fun buildWhenRules(context: KotlinFunctionContext, expression: KtWhenExpression): List<String> {
        val rules = mutableListOf<String>()
        val line = context.lineIndex.lineAt(expression.startOffset)
        val className = context.className
        val methodName = context.methodName
        val helper = context.helperFqn
        val subject = expression.subjectExpression?.text
        if (subject != null) {
            val subjectEscaped = escape(subject)
            val whenRule = """
                RULE ${className}.${methodName}:${line}:when
                CLASS ${className}
                METHOD ${methodName}
                HELPER ${helper}
                AT LINE ${line}
                IF true
                DO sw("${className}","${methodName}",${line},"${subjectEscaped}")
                ENDRULE
            """.trimIndent()
            rules += whenRule
        }
        expression.entries.forEach { entry ->
            val label = buildWhenLabel(entry)
            val escapedLabel = escape(label)
            val caseLine = context.lineIndex.lineAt(entry.startOffset)
            val caseRule = """
                RULE ${className}.${methodName}:${caseLine}:case
                CLASS ${className}
                METHOD ${methodName}
                HELPER ${helper}
                AT LINE ${caseLine}
                IF true
                DO kase("${className}","${methodName}",${caseLine},"${escapedLabel}")
                ENDRULE
            """.trimIndent()
            rules += caseRule
        }
        return rules
    }

    private fun buildWhenLabel(entry: KtWhenEntry): String {
        if (entry.isElse) {
            return "else"
        }
        val texts = entry.conditions.map { it.text.trim() }
        return texts.joinToString(" | ")
    }

    private fun buildIsRules(context: KotlinFunctionContext, expression: KtIsExpression): List<String> {
        val line = context.lineIndex.lineAt(expression.startOffset)
        val conditionText = expression.text
        val escaped = escape(conditionText)
        val className = context.className
        val methodName = context.methodName
        val helper = context.helperFqn
        val trueRule = """
            RULE ${className}.${methodName}:${line}:is-true
            CLASS ${className}
            METHOD ${methodName}
            HELPER ${helper}
            AT LINE ${line}
            IF (${conditionText})
            DO iff("${className}","${methodName}",${line},"${escaped}", true)
            ENDRULE
        """.trimIndent()
        val falseRule = """
            RULE ${className}.${methodName}:${line}:is-false
            CLASS ${className}
            METHOD ${methodName}
            HELPER ${helper}
            AT LINE ${line}
            IF (!(${conditionText}))
            DO iff("${className}","${methodName}",${line},"${escaped}", false)
            ENDRULE
        """.trimIndent()
        return listOf(trueRule, falseRule)
    }

    private fun buildWriteRule(context: KotlinFunctionContext, expression: KtBinaryExpression): List<String> {
        if (expression.operationToken != KtTokens.EQ) {
            return emptyList()
        }
        val left = expression.left ?: return emptyList()
        val name = left.text
        if (!context.trackedVars.contains(name)) {
            return emptyList()
        }
        val line = context.lineIndex.lineAt(expression.startOffset)
        val className = context.className
        val methodName = context.methodName
        val helper = context.helperFqn
        val escapedVar = escape(name)
        val rule = """
            RULE ${className}.${methodName}:${line}:write-${name}
            CLASS ${className}
            METHOD ${methodName}
            HELPER ${helper}
            AFTER WRITE ${'$'}$name
            IF true
            DO writeVar("${className}","${methodName}",${line},"${escapedVar}", ${'$'}$name)
            ENDRULE
        """.trimIndent()
        return listOf(rule)
    }

    private fun processJavaFile(
        text: String,
        helper: String,
        prefix: String?,
        includeEntryExit: Boolean
    ): List<String> {
        val lineIndex = LineIndex(text)
        val packageName = Regex("""package\s+([a-zA-Z0-9_.]+);""").find(text)?.groupValues?.get(1)?.trim()
        val classMatch = Regex("""class\s+([A-Za-z0-9_]+)""").find(text) ?: return emptyList()
        val className = classMatch.groupValues[1]
        val fqcn = if (packageName.isNullOrBlank()) className else "$packageName.$className"
        if (prefix != null && !fqcn.startsWith(prefix)) {
            return emptyList()
        }
        val rules = mutableListOf<String>()
        val methodPattern = Pattern.compile(
            "(?m)^[\\t ]*(?:public|protected|private)?[\\t ]*(?:static|final|synchronized|native|abstract|default|strictfp)?[\\t ]*[\\w<>\\[\\]]+[\\t ]+(\\w+)[\\t ]*\\([^;{]*\\)[\\t ]*\\{"
        )
        val matcher = methodPattern.matcher(text)
        while (matcher.find()) {
            val methodName = matcher.group(1)
            val braceIndex = text.indexOf('{', matcher.end() - 1)
            if (braceIndex == -1) continue
            val bodyEnd = findMatchingBrace(text, braceIndex)
            if (includeEntryExit) {
                rules += buildEntryRule(helper, fqcn, methodName)
                rules += buildExitRule(helper, fqcn, methodName)
            }
            val bodyText = text.substring(braceIndex + 1, bodyEnd)
            val offsetBase = braceIndex + 1
            val ifRegex = Regex("""if\s*\\(([^)]*)\)""")
            ifRegex.findAll(bodyText).forEach { match ->
                val condition = match.groupValues[1]
                val offset = offsetBase + match.range.first
                val line = lineIndex.lineAt(offset)
                val escaped = escape(condition)
                val trueRule = """
                    RULE ${fqcn}.${methodName}:${line}:if-true
                    CLASS ${fqcn}
                    METHOD ${methodName}
                    HELPER ${helper}
                    AT LINE ${line}
                    IF (${condition})
                    DO iff("${fqcn}","${methodName}",${line},"${escaped}", true)
                    ENDRULE
                """.trimIndent()
                val falseRule = """
                    RULE ${fqcn}.${methodName}:${line}:if-false
                    CLASS ${fqcn}
                    METHOD ${methodName}
                    HELPER ${helper}
                    AT LINE ${line}
                    IF (!(${condition}))
                    DO iff("${fqcn}","${methodName}",${line},"${escaped}", false)
                    ENDRULE
                """.trimIndent()
                rules += listOf(trueRule, falseRule)
            }
            val switchRegex = Regex("""switch\s*\\(([^)]*)\)""")
            switchRegex.findAll(bodyText).forEach { match ->
                val selector = match.groupValues[1]
                val offset = offsetBase + match.range.first
                val line = lineIndex.lineAt(offset)
                val escapedSelector = escape(selector)
                val switchRule = """
                    RULE ${fqcn}.${methodName}:${line}:when
                    CLASS ${fqcn}
                    METHOD ${methodName}
                    HELPER ${helper}
                    AT LINE ${line}
                    IF true
                    DO sw("${fqcn}","${methodName}",${line},"${escapedSelector}")
                    ENDRULE
                """.trimIndent()
                rules += switchRule
            }
            val caseRegex = Regex("""(?m)^[\\t ]*(case\s+[^:]+|default)\s*:""")
            caseRegex.findAll(bodyText).forEach { match ->
                val label = match.groupValues[1].replace(Regex("\\s+"), " ").trim()
                val offset = offsetBase + match.range.first
                val line = lineIndex.lineAt(offset)
                val escapedLabel = escape(label)
                val caseRule = """
                    RULE ${fqcn}.${methodName}:${line}:case
                    CLASS ${fqcn}
                    METHOD ${methodName}
                    HELPER ${helper}
                    AT LINE ${line}
                    IF true
                    DO kase("${fqcn}","${methodName}",${line},"${escapedLabel}")
                    ENDRULE
                """.trimIndent()
                rules += caseRule
            }
        }
        return rules
    }

    private fun findMatchingBrace(text: String, openIndex: Int): Int {
        var depth = 0
        for (i in openIndex until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return i
                    }
                }
            }
        }
        return text.length - 1
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    private data class KotlinFunctionContext(
        val helperFqn: String,
        val className: String,
        val methodName: String,
        val lineIndex: LineIndex,
        val trackedVars: Set<String>
    )

    private data class EnvironmentHolder(
        val environment: KotlinCoreEnvironment,
        val disposable: Disposable
    )

    private fun KtNamedFunction.isLocalFunction(): Boolean {
        val parent = this.parent
        return parent is org.jetbrains.kotlin.psi.KtBlockExpression || parent is KtWhenEntry
    }

    private class LineIndex(text: String) {
        private val lineStarts: IntArray

        init {
            val starts = mutableListOf(0)
            text.forEachIndexed { index, c ->
                if (c == '\n') {
                    starts.add(index + 1)
                }
            }
            lineStarts = starts.toIntArray()
        }

        fun lineAt(offset: Int): Int {
            if (lineStarts.isEmpty()) {
                return 1
            }
            var low = 0
            var high = lineStarts.size - 1
            while (low <= high) {
                val mid = (low + high) ushr 1
                val start = lineStarts[mid]
                val next = if (mid + 1 < lineStarts.size) lineStarts[mid + 1] else Int.MAX_VALUE
                when {
                    offset < start -> high = mid - 1
                    offset >= next -> low = mid + 1
                    else -> return mid + 1
                }
            }
            return lineStarts.size
        }
    }

    private object SilentMessageCollector : MessageCollector {
        override fun clear() {
        }

        override fun hasErrors(): Boolean = false

        override fun report(
            severity: CompilerMessageSeverity,
            message: String,
            location: CompilerMessageSourceLocation?
        ) {
        }
    }
}
