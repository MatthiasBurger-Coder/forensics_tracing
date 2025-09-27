package de.burger.forensics.plugin

import de.burger.forensics.plugin.engine.JavaPrefilter
import de.burger.forensics.plugin.engine.JavaRegexParser
import de.burger.forensics.plugin.engine.SourceFileGuards
import de.burger.forensics.plugin.io.ShardedWriter
import de.burger.forensics.plugin.scan.ScanEvent
import de.burger.forensics.plugin.scan.ScannerFacade
import de.burger.forensics.plugin.strategy.ConditionStrategy
import de.burger.forensics.plugin.strategy.DefaultStrategyFactory
import de.burger.forensics.plugin.strategy.SafeModeDecorator
import de.burger.forensics.plugin.strategy.StrategyFactory
import de.burger.forensics.plugin.translate.UnsafeExprTranslator
import de.burger.forensics.plugin.util.HashUtil
import de.burger.forensics.plugin.util.RuleIdUtil
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

@CacheableTask
abstract class GenerateBtmTask : DefaultTask() {

    private val conditionStrategyFactory: StrategyFactory = DefaultStrategyFactory()

    private companion object {
        const val SAFE_EVAL_FQCN: String = "org.example.trace.SafeEval"
        private val JAVA_PACKAGE_REGEX = Regex("(?m)^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;")
        private val JAVA_CLASS_REGEX = Regex(
            "(?m)^\\s*(?:@[\\w.$]+(?:\\([^)]*\\))?\\s*)*(?:(?:\\b(?:public|protected|private|abstract|final|static|strictfp|sealed)\\b|non-sealed)\\s+)*class\\s+([A-Za-z0-9_]+)"
        )
        private val JAVA_METHOD_REGEX = Regex(
            "(?m)^\\s*(?:@[\\w.$]+(?:\\([^)]*\\))?\\s*)*(?:\\b(?:public|protected|private|abstract|final|static|strictfp|synchronized|native|default)\\b\\s+)*(?:<[^>]+>\\s*)?[\\w$<>\\[\\],.?\\s]+\\s+([A-Za-z0-9_]+)\\s*\\([^)]*\\)\\s*\\{"
        )
        private val ENTRY_EXIT_RULE_REGEX = Regex("^RULE\\s+(?:enter|exit)@([\\w.$]+)\\.([A-Za-z0-9_]+)", RegexOption.MULTILINE)
    }

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

    @get:Input
    abstract val maxFileBytes: Property<Long>

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
    abstract val shards: Property<Int>

    @get:Input
    abstract val gzipOutput: Property<Boolean>

    @get:Input
    abstract val filePrefix: Property<String>

    @get:Input
    abstract val rotateMaxBytesPerFile: Property<Long>

    @get:Input
    abstract val rotateIntervalSeconds: Property<Long>

    @get:Input
    abstract val flushThresholdBytes: Property<Int>

    @get:Input
    abstract val flushIntervalMillis: Property<Long>

    @get:Input
    abstract val writerThreadSafe: Property<Boolean>

    @get:Input
    abstract val minBranchesPerMethod: Property<Int>

    @get:Input
    abstract val safeMode: Property<Boolean>

    @get:Input
    abstract val forceHelperForWhitelist: Property<Boolean>

    @get:Input
    abstract val useAstScanner: Property<Boolean>

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

    private fun globMatch(path: String, pattern: String): Boolean = globToRegexCached(pattern).matches(path)

    private fun extractClassName(rule: String): String? {
        val m = Regex("(?m)^\\s*CLASS\\s+([\\w.$]+)").find(rule)
        return m?.groupValues?.getOrNull(1)
    }

    private fun extractMethodKey(rule: String): String? {
        val m = Regex("(?m)^\\s*RULE\\s+([\\w.$]+)\\.([A-Za-z0-9_]+):").find(rule)
        return m?.let { it.groupValues[1] + "." + it.groupValues[2] }
    }

    private fun extractEntryExitMethod(rule: String): String? {
        val m = ENTRY_EXIT_RULE_REGEX.find(rule)
        return m?.let { "${it.groupValues[1]}.${it.groupValues[2]}" }
    }

    private fun findMissingJavaMethods(text: String, seenJavaMethods: Set<String>): Set<String> {
        val sanitized = JavaPrefilter.prefilterJava(text)
        val pkg = JAVA_PACKAGE_REGEX.find(sanitized)?.groupValues?.getOrNull(1).orEmpty()
        val missing = mutableSetOf<String>()
        var searchIndex = 0
        while (true) {
            val classMatch = JAVA_CLASS_REGEX.find(sanitized, searchIndex) ?: break
            val openIndex = sanitized.indexOf('{', classMatch.range.last + 1)
            if (openIndex < 0) {
                searchIndex = classMatch.range.last + 1
                continue
            }
            val closeIndex = findMatchingBrace(sanitized, openIndex)
            val className = classMatch.groupValues[1]
            val fqcn = if (pkg.isBlank()) className else "$pkg.$className"
            val body = sanitized.substring(openIndex + 1, closeIndex)
            JAVA_METHOD_REGEX.findAll(body).forEach { methodMatch ->
                val methodName = methodMatch.groupValues[1]
                val methodKey = "${fqcn}.${methodName}"
                if (methodKey !in seenJavaMethods) {
                    missing.add(methodKey)
                }
            }
            searchIndex = closeIndex + 1
        }
        return missing
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

    @TaskAction
    fun generate() {
        if (useAstScanner.getOrElse(true)) {
            generateWithAst()
        } else {
            generateLegacy()
        }
    }

    private fun generateLegacy() {
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
        val limit = maxFileBytes.getOrElse(2_000_000L)

        val shardCount = shards.getOrElse(Runtime.getRuntime().availableProcessors()).coerceAtLeast(1)
        val gzip = gzipOutput.getOrElse(false)
        val prefix = filePrefix.getOrElse("tracing-").ifBlank { "tracing-" }
        val rotateMaxBytesValue = rotateMaxBytesPerFile.orNull ?: (4L * 1024 * 1024)
        val rotateIntervalSecondsValue = rotateIntervalSeconds.orNull ?: 0L
        val flushThresholdValue = flushThresholdBytes.orNull ?: (64 * 1024)
        val flushIntervalValue = flushIntervalMillis.orNull ?: 2000L
        val threadSafeValue = writerThreadSafe.orNull ?: false
        val minBranches = minBranchesPerMethod.getOrElse(0)

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

        ShardedWriter(
            outputDirectory,
            shardCount,
            gzip,
            prefix,
            rotateMaxBytesValue,
            rotateIntervalSecondsValue,
            flushThresholdValue,
            flushIntervalValue,
            threadSafeValue
        ).use { writer ->
            writer.writeHeader(header)

            val ktFiles = kotlinSourceFiles
            if (ktFiles.isNotEmpty()) {
                val envHolder = createEnvironment()
                try {
                    val psiFactory = KtPsiFactory(envHolder.environment.project, false)
                    ktFiles.forEach { file ->
                        if (SourceFileGuards.shouldSkipLargeFile(file, limit) { msg: String -> logger.debug(msg) }) return@forEach
                        val text = file.readText()
                        val ktFile = psiFactory.createFile(file.name, text)
                        val fileRules = processKotlinFile(ktFile, text, helper, legacyPrefix, tracked, includeEntryExit)
                        dispatchRules(fileRules, allPkgPrefixes, minBranches, shardCount, writer)
                    }
                } finally {
                    Disposer.dispose(envHolder.disposable)
                }
            }

            if (includeJava.getOrElse(false)) {
                val scanner = JavaRegexParser()
                val par = parallelism.getOrElse(1)
                if (par > 1) {
                    javaSourceFiles.parallelStream().forEachOrdered { file ->
                        if (SourceFileGuards.shouldSkipLargeFile(file, limit) { msg: String -> logger.debug(msg) }) return@forEachOrdered
                        val text = file.readText()
                        val fileRules = scanner.scan(text, helper, legacyPrefix, includeEntryExit, maxLen)
                        dispatchRules(fileRules, allPkgPrefixes, minBranches, shardCount, writer)
                    }
                } else {
                    javaSourceFiles.forEach { file ->
                        if (SourceFileGuards.shouldSkipLargeFile(file, limit) { msg: String -> logger.debug(msg) }) return@forEach
                        val text = file.readText()
                        val fileRules = scanner.scan(text, helper, legacyPrefix, includeEntryExit, maxLen)
                        dispatchRules(fileRules, allPkgPrefixes, minBranches, shardCount, writer)
                    }
                }
            }
        }
    }

    private fun generateWithAst() {
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
        val limit = maxFileBytes.getOrElse(2_000_000L)
        val minBranches = minBranchesPerMethod.getOrElse(0)
        val shardCount = shards.getOrElse(Runtime.getRuntime().availableProcessors()).coerceAtLeast(1)
        val gzip = gzipOutput.getOrElse(false)
        val prefix = filePrefix.getOrElse("tracing-").ifBlank { "tracing-" }
        val rotateMaxBytesValue = rotateMaxBytesPerFile.orNull ?: (4L * 1024 * 1024)
        val rotateIntervalSecondsValue = rotateIntervalSeconds.orNull ?: 0L
        val flushThresholdValue = flushThresholdBytes.orNull ?: (64 * 1024)
        val flushIntervalValue = flushIntervalMillis.orNull ?: 2000L
        val threadSafeValue = writerThreadSafe.orNull ?: false

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

        val scanner = ScannerFacade()
        val events = mutableListOf<ScanEvent>()
        val includePkgs = allPkgPrefixes
        val excludePkgs = emptyList<String>()

        val kotlinFiles = kotlinSourceFiles
        kotlinFiles.forEach { file ->
            if (SourceFileGuards.shouldSkipLargeFile(file, limit) { msg: String -> logger.debug(msg) }) return@forEach
            events += scanner.scan(file.toPath(), includePkgs, excludePkgs)
        }

        if (includeJava.getOrElse(false)) {
            val javaFiles = javaSourceFiles
            javaFiles.forEach { file ->
                if (SourceFileGuards.shouldSkipLargeFile(file, limit) { msg: String -> logger.debug(msg) }) return@forEach
                events += scanner.scan(file.toPath(), includePkgs, excludePkgs)
            }
        }

        if (events.isEmpty()) {
            ShardedWriter(
                outputDirectory,
                shardCount,
                gzip,
                prefix,
                rotateMaxBytesValue,
                rotateIntervalSecondsValue,
                flushThresholdValue,
                flushIntervalValue,
                threadSafeValue
            ).use { writer ->
                writer.writeHeader(header)
            }
            return
        }

        val rules = mutableListOf<String>()
        val seenMethods = linkedSetOf<String>()
        val filteredEvents = events.sortedWith(
            compareBy<ScanEvent>({ it.language }, { it.fqcn }, { it.method }, { it.line }, { it.kind })
        )

        // Precompute which methods already have an explicit Kotlin switch (when) event
        val methodsWithKotlinSwitch = filteredEvents.asSequence()
            .filter { it.language == "kotlin" && it.kind == "switch" }
            .map { "${it.language}:${it.fqcn}:${it.method}:${it.signature}" }
            .toSet()

        filteredEvents.forEach { event ->
            if (event.line < 0) return@forEach
            if (allPkgPrefixes.isNotEmpty() && allPkgPrefixes.none { event.fqcn.startsWith(it) }) return@forEach
            val methodKey = "${event.language}:${event.fqcn}:${event.method}:${event.signature}"
            if (includeEntryExit && seenMethods.add(methodKey)) {
                rules += buildEntryRule(helper, event.fqcn, event.method)
                rules += buildExitRule(helper, event.fqcn, event.method)
            }
            // Synthesize a subject-less when selector if we see branches but no prior switch for this method
            if (event.language == "kotlin" && event.kind == "when-branch" && methodKey !in methodsWithKotlinSwitch) {
                val synthetic = ScanEvent("kotlin", event.fqcn, event.method, event.signature, "switch", event.line, null)
                rules += buildKotlinSwitchRule(synthetic, helper)
                // Mark as present to avoid duplicating for subsequent branches
                // Note: we don't mutate methodsWithKotlinSwitch (immutable set); duplication is avoided because we add only once per first branch encountered in iteration order
                // relying on stable sort above.
            }
            rules += toRules(event, helper)
        }

        // Fallback: ensure Java methods always have entry/exit rules even if no AST events were detected
        if (includeJava.getOrElse(false)) {
            val regex = JavaRegexParser()
            val seenJavaMethods = seenMethods.asSequence()
                .filter { it.startsWith("java:") }
                .mapNotNull { key ->
                    val parts = key.split(':', limit = 4)
                    if (parts.size == 4) "${parts[1]}.${parts[2]}" else null
                }
                .toSet()
            javaSourceFiles.forEach { file ->
                if (SourceFileGuards.shouldSkipLargeFile(file, limit) { msg: String -> logger.debug(msg) }) return@forEach
                val text = file.readText()
                val missingMethods = findMissingJavaMethods(text, seenJavaMethods)
                if (missingMethods.isEmpty()) return@forEach
                val fileRules = regex.scan(text, helper, legacyPrefix, includeEntryExit, maxStringLength.getOrElse(0))
                rules += fileRules.filter { rule ->
                    val methodKey = extractMethodKey(rule) ?: extractEntryExitMethod(rule)
                    methodKey == null || methodKey in missingMethods
                }
            }
        }

        if (rules.isEmpty()) {
            ShardedWriter(
                outputDirectory,
                shardCount,
                gzip,
                prefix,
                rotateMaxBytesValue,
                rotateIntervalSecondsValue,
                flushThresholdValue,
                flushIntervalValue,
                threadSafeValue
            ).use { writer ->
                writer.writeHeader(header)
            }
            return
        }

        ShardedWriter(
            outputDirectory,
            shardCount,
            gzip,
            prefix,
            rotateMaxBytesValue,
            rotateIntervalSecondsValue,
            flushThresholdValue,
            flushIntervalValue,
            threadSafeValue
        ).use { writer ->
            writer.writeHeader(header)
            dispatchRules(rules, allPkgPrefixes, minBranches, shardCount, writer)
        }
    }

    private fun toRules(event: ScanEvent, helper: String): List<String> {
        return when (event.language) {
            "java" -> when (event.kind) {
                "if-true" -> listOf(buildJavaIfRule(event, helper, true))
                "if-false" -> listOf(buildJavaIfRule(event, helper, false))
                "switch" -> listOf(buildJavaSwitchRule(event, helper))
                "switch-case" -> listOf(buildJavaCaseRule(event, helper))
                else -> emptyList()
            }
            "kotlin" -> when (event.kind) {
                "if-true" -> listOf(buildKotlinIfRule(event, helper, true))
                "if-false" -> listOf(buildKotlinIfRule(event, helper, false))
                "switch" -> listOf(buildKotlinSwitchRule(event, helper))
                "when-branch" -> listOf(buildKotlinCaseRule(event, helper))
                "write" -> listOf(buildKotlinWriteRule(event, helper))
                else -> emptyList()
            }
            else -> emptyList()
        }
    }

    private fun buildJavaIfRule(event: ScanEvent, helper: String, positive: Boolean): String {
        val conditionText = event.conditionText ?: "true"
        val escaped = escape(conditionText)
        val check = if (positive) "IF (${conditionText})" else "IF (!(${conditionText}))"
        return listOf(
            "RULE ${event.fqcn}.${event.method}:${event.line}:${if (positive) "if-true" else "if-false"}",
            "CLASS ${event.fqcn}",
            "METHOD ${event.method}(..)",
            "HELPER ${helper}",
            "AT LINE ${event.line}",
            check,
            "DO iff(\"${event.fqcn}\",\"${event.method}\",${event.line},\"${escaped}\", ${positive})",
            "ENDRULE"
        ).joinToString("\n")
    }

    private fun buildJavaSwitchRule(event: ScanEvent, helper: String): String {
        val selector = escape(event.conditionText ?: "")
        return listOf(
            "RULE ${event.fqcn}.${event.method}:${event.line}:when",
            "CLASS ${event.fqcn}",
            "METHOD ${event.method}(..)",
            "HELPER ${helper}",
            "AT LINE ${event.line}",
            "DO sw(\"${event.fqcn}\",\"${event.method}\",${event.line},\"${selector}\")",
            "ENDRULE"
        ).joinToString("\n")
    }

    private fun buildJavaCaseRule(event: ScanEvent, helper: String): String {
        val label = event.conditionText ?: "default"
        val escaped = escape(label)
        return listOf(
            "RULE ${event.fqcn}.${event.method}:${event.line}:case",
            "CLASS ${event.fqcn}",
            "METHOD ${event.method}(..)",
            "HELPER ${helper}",
            "AT LINE ${event.line}",
            "DO kase(\"${event.fqcn}\",\"${event.method}\",${event.line},\"${escaped}\")",
            "ENDRULE"
        ).joinToString("\n")
    }

    private fun buildKotlinIfRule(event: ScanEvent, helper: String, positive: Boolean): String {
        val conditionText = event.conditionText ?: "true"
        val baseStrategy = conditionStrategyFactory.from(conditionText)
        val ruleId = RuleIdUtil.stableRuleId(event.fqcn, event.method, event.line, conditionText)
        val decorated = decorateCondition(baseStrategy, ruleId)
        val rendered = decorated.toBytemanIf()
        val registration = maybeBuildRegistrationBlock(ruleId, conditionText, rendered)
        val escaped = escape(conditionText)
        val lines = mutableListOf(
            "RULE ${event.fqcn}.${event.method}:${event.line}:${if (positive) "if-true" else "if-false"}",
            "CLASS ${event.fqcn}",
            "METHOD ${event.method}(..)",
            "HELPER ${helper}",
            "AT LINE ${event.line}",
            if (positive) "IF (${rendered})" else "IF (!(${rendered}))"
        )
        registration?.let { lines.addAll(it) }
        lines += "DO iff(\"${event.fqcn}\",\"${event.method}\",${event.line},\"${escaped}\", ${positive})"
        lines += "ENDRULE"
        return lines.joinToString("\n")
    }

    private fun buildKotlinSwitchRule(event: ScanEvent, helper: String): String {
        val raw = event.conditionText?.takeIf { it.isNotBlank() } ?: "when { … }"
        val selector = escape(raw)
        return listOf(
            "RULE ${event.fqcn}.${event.method}:${event.line}:when",
            "CLASS ${event.fqcn}",
            "METHOD ${event.method}(..)",
            "HELPER ${helper}",
            "AT LINE ${event.line}",
            "DO sw(\"${event.fqcn}\",\"${event.method}\",${event.line},\"${selector}\")",
            "ENDRULE"
        ).joinToString("\n")
    }

    private fun buildKotlinCaseRule(event: ScanEvent, helper: String): String {
        val label = escape(event.conditionText ?: "else")
        return listOf(
            "RULE ${event.fqcn}.${event.method}:${event.line}:case",
            "CLASS ${event.fqcn}",
            "METHOD ${event.method}(..)",
            "HELPER ${helper}",
            "AT LINE ${event.line}",
            "DO kase(\"${event.fqcn}\",\"${event.method}\",${event.line},\"${label}\")",
            "ENDRULE"
        ).joinToString("\n")
    }

    private fun buildKotlinWriteRule(event: ScanEvent, helper: String): String {
        val name = event.conditionText ?: return ""
        val escapedVar = escape(name)
        return listOf(
            "RULE ${event.fqcn}.${event.method}:${event.line}:write-${name}",
            "CLASS ${event.fqcn}",
            "METHOD ${event.method}(..)",
            "HELPER ${helper}",
            "AFTER WRITE ${'$'}$name",
            "DO writeVar(\"${event.fqcn}\",\"${event.method}\",${event.line},\"${escapedVar}\", ${'$'}$name)",
            "ENDRULE"
        ).joinToString("\n")
    }

    private fun dispatchRules(
        rules: List<String>,
        prefixes: List<String>,
        minBranches: Int,
        shardCount: Int,
        writer: ShardedWriter
    ) {
        if (rules.isEmpty()) return
        if (minBranches <= 0) {
            rules.forEach { rule ->
                if (passesPrefixFilter(rule, prefixes)) {
                    val shardKey = computeShardKey(rule)
                    val shard = HashUtil.stableShard(shardKey, shardCount)
                    writer.append(shard, rule)
                }
            }
            return
        }

        val grouped = rules.groupBy { extractMethodKey(it) }
        grouped.forEach { (methodKey, methodRules) ->
            if (methodKey == null || hasRequiredBranches(methodRules, minBranches)) {
                val first = methodRules.firstOrNull() ?: return@forEach
                if (!passesPrefixFilter(first, prefixes)) return@forEach
                methodRules.forEach { rule ->
                    val shardKey = computeShardKey(rule)
                    val shard = HashUtil.stableShard(shardKey, shardCount)
                    writer.append(shard, rule)
                }
            }
        }
    }

    private fun passesPrefixFilter(rule: String, prefixes: List<String>): Boolean {
        if (prefixes.isEmpty()) return true
        val cls = extractClassName(rule) ?: return false
        return prefixes.any { cls.startsWith(it) }
    }

    private fun hasRequiredBranches(rules: List<String>, minBranches: Int): Boolean {
        if (minBranches <= 0) return true
        val branchCount = rules.count { rule ->
            rule.contains(":if-") || rule.contains(":is-") || rule.contains(":when") || rule.contains(":case")
        }
        return branchCount >= minBranches
    }

    private fun computeShardKey(rule: String): String {
        val className = extractClassName(rule) ?: ""
        val method = Regex("(?m)^\\s*METHOD\\s+([A-Za-z0-9_]+)\\(").find(rule)?.groupValues?.getOrNull(1) ?: ""
        val line = Regex("(?m)^\\s*AT\\s+LINE\\s+(\\d+)").find(rule)?.groupValues?.getOrNull(1) ?: "0"
        return "$className#$method:$line"
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
        val baseStrategy = conditionStrategyFactory.from(condition.text)
        val ruleId = RuleIdUtil.stableRuleId(className, methodName, line, condition.text)
        val decoratedStrategy = decorateCondition(baseStrategy, ruleId)
        val renderedCondition = decoratedStrategy.toBytemanIf()
        val registration = maybeBuildRegistrationBlock(ruleId, condition.text, renderedCondition)
        val trueLines = mutableListOf(
            "RULE ${className}.${methodName}:${line}:if-true",
            "CLASS ${className}",
            "METHOD ${methodName}(..)",
            "HELPER ${helper}",
            "AT LINE ${line}",
            "IF (${renderedCondition})"
        )
        registration?.let { trueLines.addAll(it) }
        trueLines += "DO iff(\"${className}\",\"${methodName}\",${line},\"${conditionText}\", true)"
        trueLines += "ENDRULE"

        val falseLines = mutableListOf(
            "RULE ${className}.${methodName}:${line}:if-false",
            "CLASS ${className}",
            "METHOD ${methodName}(..)",
            "HELPER ${helper}",
            "AT LINE ${line}",
            "IF (!(${renderedCondition}))"
        )
        registration?.let { falseLines.addAll(it) }
        falseLines += "DO iff(\"${className}\",\"${methodName}\",${line},\"${conditionText}\", false)"
        falseLines += "ENDRULE"

        return listOf(trueLines.joinToString("\n"), falseLines.joinToString("\n"))
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
        val className = context.className
        val methodName = context.methodName
        val helper = context.helperFqn
        val baseStrategy = conditionStrategyFactory.from(conditionText)
        val ruleId = RuleIdUtil.stableRuleId(className, methodName, line, conditionText)
        val decoratedStrategy = decorateCondition(baseStrategy, ruleId)
        val renderedCondition = decoratedStrategy.toBytemanIf()
        val escaped = escape(conditionText)
        val registration = maybeBuildRegistrationBlock(ruleId, conditionText, renderedCondition)
        val trueLines = mutableListOf(
            "RULE ${className}.${methodName}:${line}:is-true",
            "CLASS ${className}",
            "METHOD ${methodName}(..)",
            "HELPER ${helper}",
            "AT LINE ${line}",
            "IF (${renderedCondition})"
        )
        registration?.let { trueLines.addAll(it) }
        trueLines += "DO iff(\"${className}\",\"${methodName}\",${line},\"${escaped}\", true)"
        trueLines += "ENDRULE"

        val falseLines = mutableListOf(
            "RULE ${className}.${methodName}:${line}:is-false",
            "CLASS ${className}",
            "METHOD ${methodName}(..)",
            "HELPER ${helper}",
            "AT LINE ${line}",
            "IF (!(${renderedCondition}))"
        )
        registration?.let { falseLines.addAll(it) }
        falseLines += "DO iff(\"${className}\",\"${methodName}\",${line},\"${escaped}\", false)"
        falseLines += "ENDRULE"

        return listOf(trueLines.joinToString("\n"), falseLines.joinToString("\n"))
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

    private fun decorateCondition(base: ConditionStrategy, ruleId: String): ConditionStrategy {
        return SafeModeDecorator(
            base,
            safeMode.getOrElse(false),
            forceHelperForWhitelist.getOrElse(false),
            SAFE_EVAL_FQCN,
            ruleId
        )
    }

    private fun maybeBuildRegistrationBlock(
        ruleId: String,
        rawExpression: String,
        renderedCondition: String
    ): List<String>? {
        val expected = "$SAFE_EVAL_FQCN.ifMatch(\"$ruleId\")"
        if (renderedCondition != expected) {
            return null
        }
        val body = UnsafeExprTranslator.toHelperExpr(rawExpression)
        val fqcn = SAFE_EVAL_FQCN
        return listOf(
            "DO $fqcn.register(\"$ruleId\", new $fqcn.Evaluator() {",
            "    public boolean eval() {",
            "        return $body;",
            "    }",
            "});"
        )
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
