package de.burger.forensics.plugin.scan.kotlin

import de.burger.forensics.plugin.scan.ScanEvent
import de.burger.forensics.plugin.scan.SourceScanner
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

// English comments only in code.
class KotlinAstScanner : SourceScanner {

    override fun scan(root: Path, includePkgs: List<String>, excludePkgs: List<String>): List<ScanEvent> {
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, "scanner")
        }
        val environment = KotlinCoreEnvironment.createForProduction({}, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val psiFactory = KtPsiFactory(environment.project, false)
        val events = mutableListOf<ScanEvent>()
        Files.walk(root).use { stream ->
            stream.filter { it.toString().endsWith(".kt") }.forEach { path ->
                val fileText = Files.readString(path)
                val lineIndex = LineIndex(fileText)
                val psi = psiFactory.createFile(path.fileName.toString(), fileText)
                val pkg = psi.packageFqName.asString()
                if (!include(pkg, includePkgs) || exclude(pkg, excludePkgs)) {
                    return@forEach
                }
                psi.accept(object : KtTreeVisitorVoid() {
                    override fun visitNamedFunction(function: KtNamedFunction) {
                        val className = function.enclosingFqcn() ?: return
                        val methodName = function.name ?: return
                        val signature = buildString {
                            append(methodName)
                            append("(")
                            append(function.valueParameters.joinToString(",") { it.typeReference?.text ?: "Any" })
                            append(")")
                        }
                        function.bodyExpression?.accept(object : KtTreeVisitorVoid() {
                            override fun visitIfExpression(expression: KtIfExpression) {
                                val cond = expression.condition?.text ?: "true"
                                val line = expression.condition?.textRange?.startOffset?.let(lineIndex::lineAt) ?: -1
                                events += ScanEvent("kotlin", className, methodName, signature, "if-true", line, cond)
                                if (expression.`else` != null) {
                                    events += ScanEvent("kotlin", className, methodName, signature, "if-false", line, cond)
                                }
                                super.visitIfExpression(expression)
                            }

                            override fun visitWhenExpression(expression: KtWhenExpression) {
                                val subjectOffset = expression.subjectExpression?.textRange?.startOffset
                                val line = subjectOffset?.let(lineIndex::lineAt) ?: lineIndex.lineAt(expression.textRange.startOffset)
                                val subject = expression.subjectExpression?.text ?: "when"
                                events += ScanEvent("kotlin", className, methodName, signature, "switch", line, subject)
                                expression.entries.forEach { entry ->
                                    val label = entry.conditions.joinToString("|") { it.text }.ifBlank { "else" }
                                    val caseLine = lineIndex.lineAt(entry.textRange.startOffset)
                                    events += ScanEvent("kotlin", className, methodName, signature, "when-branch", caseLine, label)
                                }
                                super.visitWhenExpression(expression)
                            }

                            override fun visitReturnExpression(expression: KtReturnExpression) {
                                val line = lineIndex.lineAt(expression.textRange.startOffset)
                                events += ScanEvent("kotlin", className, methodName, signature, "return", line, null)
                                super.visitReturnExpression(expression)
                            }

                            override fun visitThrowExpression(expression: KtThrowExpression) {
                                val line = lineIndex.lineAt(expression.textRange.startOffset)
                                events += ScanEvent("kotlin", className, methodName, signature, "throw", line, expression.thrownExpression?.text ?: "throw")
                                super.visitThrowExpression(expression)
                            }
                        })
                        super.visitNamedFunction(function)
                    }
                })
            }
        }
        return events
    }

    private fun include(pkg: String, includes: List<String>): Boolean {
        return includes.isEmpty() || includes.any { pkg.startsWith(it) }
    }

    private fun exclude(pkg: String, excludes: List<String>): Boolean {
        return excludes.any { pkg.startsWith(it) }
    }

    private fun KtNamedFunction.enclosingFqcn(): String? {
        val container = containingClassOrObject
        val pkg = containingKtFile.packageFqName.asString().takeIf { it.isNotBlank() }
        return if (container == null) {
            val top = containingKtFile.name.substringBeforeLast('.') + "Kt"
            if (pkg == null) top else "$pkg.$top"
        } else {
            val segments = mutableListOf<String>()
            var current = container
            while (current != null) {
                val name = current.name ?: return null
                segments += name
                current = current.containingClassOrObject
            }
            val joined = segments.asReversed().joinToString("$")
            if (pkg == null) joined else "$pkg.$joined"
        }
    }

    private class LineIndex(source: String) {
        private val starts: IntArray = buildList {
            add(0)
            source.forEachIndexed { index, ch ->
                if (ch == '\n') {
                    add(index + 1)
                }
            }
        }.toIntArray()

        fun lineAt(offset: Int): Int {
            if (offset < 0) return -1
            var low = 0
            var high = starts.size - 1
            while (low <= high) {
                val mid = (low + high) ushr 1
                val start = starts[mid]
                val end = if (mid + 1 < starts.size) starts[mid + 1] else Int.MAX_VALUE
                when {
                    offset < start -> high = mid - 1
                    offset >= end -> low = mid + 1
                    else -> return mid + 1
                }
            }
            return starts.size
        }
    }
}
