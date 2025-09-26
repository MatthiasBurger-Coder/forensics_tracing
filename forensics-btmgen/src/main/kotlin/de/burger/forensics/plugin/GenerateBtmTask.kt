package de.burger.forensics.plugin

import de.burger.forensics.plugin.engine.JavaRegexParser
import de.burger.forensics.plugin.strategy.DefaultStrategyFactory
import de.burger.forensics.plugin.strategy.StrategyFactory
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
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
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.zip.GZIPOutputStream

@CacheableTask
abstract class GenerateBtmTask : DefaultTask() {

    private val conditionStrategyFactory: StrategyFactory = DefaultStrategyFactory()

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

    @get:Input
    abstract val includeTimestamp: Property<Boolean>

    @get:Input
    abstract val maxStringLength: Property<Int>

    // New DSL inputs
    @get:Input
    abstract val pkgPrefixes: ListProperty<String>

    @get:Input
    abstract val includePatterns: ListProperty<String>

    @get:Input
    abstract val excludePatterns: ListProperty<String>

    @get:Input
    abstract val parallelism: Property<Int>

    @get:Input
    abstract val shardOutput: Property<Int>

    @get:Input
    abstract val gzipOutput: Property<Boolean>

    @get:Input
    abstract val minBranchesPerMethod: Property<Int>

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
        val includes = includePatterns.orNull?.takeIf { it.isNotEmpty() }
        val excludes = excludePatterns.orNull?.takeIf { it.isNotEmpty() } ?: emptyList()
        val result = mutableListOf<File>()
        directories.forEach { dir ->
            Files.walk(dir.toPath()).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(withExtension) }
                    .forEach { p ->
                        val rel = dir.toPath().relativize(p).toString().replace('\\', '/')
                        val incOk = includes?.any { globMatch(rel, it) } ?: true
                        val excOk = excludes.none { globMatch(rel, it) }
                        if (incOk && excOk) result.add(p.toFile())
                    }
            }
        }
        return result.sortedBy { it.absolutePath }
    }

    private fun globMatch(path: String, pattern: String): Boolean {
        // Convert simple glob (**/*.*) to regex
        val regex = globToRegex(pattern)
        return Regex(regex).matches(path)
    }

    private fun globToRegex(glob: String): String {
        val sb = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            val c = glob[i]
            when (c) {
                '*' -> {
                    if (i + 1 < glob.length && glob[i + 1] == '*') {
                        sb.append(".*")
                        i++
                    } else {
                        sb.append("[^/]*")
                    }
                }
                '?' -> sb.append(".")
                '.', '(', ')', '+', '|', '^', '$', '@', '%' -> sb.append("\\").append(c)
                '{' -> sb.append('(')
                '}' -> sb.append(')')
                ',' -> sb.append('|')
                '[' -> sb.append('[')
                ']' -> sb.append(']')
                else -> sb.append(c)
            }
            i++
        }
        sb.append("$")
        return sb.toString()
    }

    private fun extractClassName(rule: String): String? {
        val m = Regex("(?m)^\\s*CLASS\\s+([\\w.$]+)").find(rule)
        return m?.groupValues?.getOrNull(1)
    }

    private fun extractMethodKey(rule: String): String? {
        val m = Regex("(?m)^\\s*RULE\\s+([\\w.$]+)\\.([A-Za-z0-9_]+):").find(rule)
        return m?.let { it.groupValues[1] + "." + it.groupValues[2] }
    }

    private fun filterByPkgPrefixes(rules: List<String>, prefixes: List<String>): List<String> {
        if (prefixes.isEmpty()) return rules
        return rules.filter { rule ->
            val cls = extractClassName(rule)
            cls != null && prefixes.any { cls.startsWith(it) }
        }
    }

    private fun filterByMinBranches(rules: List<String>, minBranches: Int): List<String> {
        if (minBranches <= 0) return rules
        val byMethod = rules.groupBy { extractMethodKey(it) }
        val keepMethods = mutableSetOf<String>()
        byMethod.forEach { (methodKey, list) ->
            if (methodKey == null) return@forEach
            val branchCount = list.count { r ->
                r.contains(":if-") || r.contains(":is-") || r.contains(":when") || r.contains(":case")
            }
            if (branchCount >= minBranches) keepMethods += methodKey
        }
        return rules.filter { r ->
            val key = extractMethodKey(r)
            key == null || keepMethods.contains(key)
        }
    }

    private fun writeSharded(rules: List<String>, header: String, outputDirectory: File) {
        val shards = shardOutput.getOrElse(1).coerceAtLeast(1)
        val gzip = gzipOutput.getOrElse(false)
        if (shards <= 1) {
            val file = outputDirectory.resolve("tracing.btm" + if (gzip) ".gz" else "")
            writeFile(file, header, rules, gzip)
            return
        }
        val perShard = ((rules.size + shards - 1) / shards).coerceAtLeast(1)
        var index = 0
        for (s in 0 until shards) {
            val from = s * perShard
            if (from >= rules.size) break
            val to = minOf((s + 1) * perShard, rules.size)
            val part = rules.subList(from, to)
            val name = "tracing-%04d.btm".format(s + 1) + if (gzip) ".gz" else ""
            val file = outputDirectory.resolve(name)
            writeFile(file, header, part, gzip)
            index += part.size
        }
    }

    private fun writeFile(file: File, header: String, rules: List<String>, gzip: Boolean) {
        if (gzip) {
            file.outputStream().use { fos ->
                GZIPOutputStream(fos).bufferedWriter(Charsets.UTF_8).use { w ->
                    w.append(header)
                    if (rules.isEmpty()) {
                        w.append("# No matching sources were found.\n")
                    } else {
                        w.append('\n')
                        w.append(rules.joinToString("\n\n"))
                        w.append('\n')
                    }
                }
            }
        } else {
            val content = buildString {
                append(header)
                if (rules.isEmpty()) {
                    append("# No matching sources were found.\n")
                } else {
                    append('\n')
                    append(rules.joinToString("\n\n"))
                    append('\n')
                }
            }
            file.writeText(content)
        }
    }

    @TaskAction
    fun generate() {
        val outputDirectory = outputDir.get().asFile
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }
        val helper = helperFqn.get()
        val legacyPrefix = packagePrefix.orNull?.takeIf { it.isNotBlank() }
        val allPkgPrefixes = buildList {
            pkgPrefixes.orNull?.filter { it.isNotBlank() }?.let { addAll(it) }
            if (legacyPrefix != null) add(legacyPrefix)
        }
        val tracked = trackedVars.orNull?.toSet() ?: emptySet()
        val includeEntryExit = entryExit.getOrElse(true)
        val maxLen = maxStringLength.getOrElse(0)

        val rules = mutableListOf<String>()
        val ktFiles = kotlinSourceFiles
        if (ktFiles.isNotEmpty()) {
            val envHolder = createEnvironment()
            try {
                val psiFactory = KtPsiFactory(envHolder.environment.project, false)
                ktFiles.forEach { file ->
                    val text = file.readText()
                    val ktFile = psiFactory.createFile(file.name, text)
                    val fileRules = processKotlinFile(ktFile, text, helper, legacyPrefix, tracked, includeEntryExit)
                    rules += fileRules
                }
            } finally {
                Disposer.dispose(envHolder.disposable)
            }
        }

        if (includeJava.getOrElse(false)) {
            val scanner = JavaRegexParser()
            // Simple parallelism for Java files only
            val par = parallelism.getOrElse(1)
            if (par > 1) {
                javaSourceFiles.parallelStream().forEachOrdered { file ->
                    val text = file.readText()
                    val fileRules = scanner.scan(text, helper, legacyPrefix, includeEntryExit, maxLen)
                    synchronized(rules) { rules += fileRules }
                }
            } else {
                javaSourceFiles.forEach { file ->
                    val text = file.readText()
                    val fileRules = scanner.scan(text, helper, legacyPrefix, includeEntryExit, maxLen)
                    rules += fileRules
                }
            }
        }

        // Post-process: filter by pkg prefixes & minBranches
        var finalRules = if (allPkgPrefixes.isNotEmpty()) filterByPkgPrefixes(rules, allPkgPrefixes) else rules
        finalRules = filterByMinBranches(finalRules, minBranchesPerMethod.getOrElse(0))

        val header = buildString {
            if (includeTimestamp.getOrElse(false)) {
                append("# Generated at ")
                append(Instant.now())
                append(" by de.burger.forensics.btmgen\n")
            } else {
                append("# Generated by de.burger.forensics.btmgen\n")
            }
            append("# Helper: ")
            append(helper)
            append('\n')
            if (allPkgPrefixes.isNotEmpty()) {
                append("# Package prefix filters: ")
                append(allPkgPrefixes.joinToString(", "))
                append('\n')
            }
            if (tracked.isNotEmpty()) {
                append("# Tracked variables: ")
                append(tracked.joinToString(", "))
                append('\n')
            }
        }

        writeSharded(finalRules, header, outputDirectory)
    }

    private fun createEnvironment(): EnvironmentHolder {
        val disposable: Disposable = Disposer.newDisposable("btmgen")
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, "btmgen")
            put(JVMConfigurationKeys.JDK_HOME, File(System.getProperty("java.home")))
            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, SilentMessageCollector)
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
        // Fast path: if a package prefix is configured and the file's package doesn't match it,
        // we can skip traversing the PSI entirely for this file.
        val filePackage = ktFile.packageFqName.takeIf { !it.isRoot }?.asString().orEmpty()
        if (prefix != null && filePackage.isNotEmpty() && !filePackage.startsWith(prefix)) {
            return emptyList()
        }
        val functions = mutableListOf<KtNamedFunction>()
        ktFile.accept(object : KtTreeVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                if (!function.isLocalFunction()) {
                    functions += function
                }
                // Do not descend into function bodies here to avoid redundant traversal.
                // We'll traverse the body once in collectRulesForFunction.
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
            var current: KtClassOrObject? = classOrObject
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
        METHOD ${methodName}(..)
        HELPER ${helper}
        AT ENTRY
        DO enter("${className}","${methodName}", ${'$'}LINE)
        ENDRULE
        """.trimIndent()

    private fun buildExitRule(helper: String, className: String, methodName: String): String =
        """
        RULE exit@${className}.${methodName}
        CLASS ${className}
        METHOD ${methodName}(..)
        HELPER ${helper}
        AT EXIT
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
        val strategy = conditionStrategyFactory.from(condition.text)
        val renderedCondition = strategy.toBytemanIf()
        val trueRule = """
            RULE ${className}.${methodName}:${line}:if-true
            CLASS ${className}
            METHOD ${methodName}(..)
            HELPER ${helper}
            AT LINE ${line}
            IF (${renderedCondition})
            DO iff("${className}","${methodName}",${line},"${conditionText}", true)
            ENDRULE
        """.trimIndent()
        val falseRule = """
            RULE ${className}.${methodName}:${line}:if-false
            CLASS ${className}
            METHOD ${methodName}(..)
            HELPER ${helper}
            AT LINE ${line}
            IF (!(${renderedCondition}))
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
        val selectorText = subject ?: "when { … }"
        val whenRule = """
            RULE ${className}.${methodName}:${line}:when
            CLASS ${className}
            METHOD ${methodName}(..)
            HELPER ${helper}
            AT LINE ${line}
            DO sw("${className}","${methodName}",${line},"${escape(selectorText)}")
            ENDRULE
        """.trimIndent()
        rules += whenRule
        expression.entries.forEach { entry ->
            val label = buildWhenLabel(entry)
            val escapedLabel = escape(label)
            val caseLine = context.lineIndex.lineAt(entry.startOffset)
            val caseRule = """
                RULE ${className}.${methodName}:${caseLine}:case
                CLASS ${className}
                METHOD ${methodName}(..)
                HELPER ${helper}
                AT LINE ${caseLine}
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
        val strategy = conditionStrategyFactory.from(conditionText)
        val renderedCondition = strategy.toBytemanIf()
        val escaped = escape(conditionText)
        val className = context.className
        val methodName = context.methodName
        val helper = context.helperFqn
        val trueRule = """
            RULE ${className}.${methodName}:${line}:is-true
            CLASS ${className}
            METHOD ${methodName}(..)
            HELPER ${helper}
            AT LINE ${line}
            IF (${renderedCondition})
            DO iff("${className}","${methodName}",${line},"${escaped}", true)
            ENDRULE
        """.trimIndent()
        val falseRule = """
            RULE ${className}.${methodName}:${line}:is-false
            CLASS ${className}
            METHOD ${methodName}(..)
            HELPER ${helper}
            AT LINE ${line}
            IF (!(${renderedCondition}))
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
            METHOD ${methodName}(..)
            HELPER ${helper}
            AFTER WRITE ${'$'}$name
            DO writeVar("${className}","${methodName}",${line},"${escapedVar}", ${'$'}$name)
            ENDRULE
        """.trimIndent()
        return listOf(rule)
    }



    private fun escape(value: String): String {
        val limit = maxStringLength.getOrElse(0)
        val truncated = if (limit > 0 && value.length > limit) value.take(limit) + "…" else value
        return truncated
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
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
        return parent is KtBlockExpression || parent is KtWhenEntry
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
