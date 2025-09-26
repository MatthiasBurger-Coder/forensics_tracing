package de.burger.forensics.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class BtmGenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "forensicsBtmGen",
            BtmGenExtension::class.java,
            project.objects,
            project.layout
        )

        project.tasks.register("generateBtmRules", GenerateBtmTask::class.java) { task ->
            task.group = "forensics"
            task.description = "Generates Byteman tracing rules into build/forensics/tracing-0001-00001.btm"
            task.srcDirs.set(extension.srcDirs)
            task.packagePrefix.set(extension.pkgPrefix)
            task.helperFqn.set(extension.helperFqn)
            task.entryExit.set(extension.entryExit)
            task.trackedVars.set(extension.trackedVars)
            task.includeJava.set(extension.includeJava)
            task.includeTimestamp.set(extension.includeTimestamp)
            task.outputDir.set(extension.outputDir)
            task.maxStringLength.set(extension.maxStringLength)
            // New DSL options
            task.pkgPrefixes.set(extension.pkgPrefixes)
            task.includePatterns.set(extension.include)
            task.excludePatterns.set(extension.exclude)
            task.parallelism.set(extension.parallelism)
            task.shards.set(extension.shardsProperty)
            task.gzipOutput.set(extension.gzipOutputProperty)
            task.filePrefix.set(extension.filePrefixProperty)
            task.rotateMaxBytesPerFile.set(extension.rotateMaxBytesPerFileProperty)
            task.rotateIntervalSeconds.set(extension.rotateIntervalSecondsProperty)
            task.flushThresholdBytes.set(extension.flushThresholdBytesProperty)
            task.flushIntervalMillis.set(extension.flushIntervalMillisProperty)
            task.writerThreadSafe.set(extension.writerThreadSafeProperty)
            task.minBranchesPerMethod.set(extension.minBranchesPerMethod)
            task.safeMode.set(extension.safeMode)
            task.forceHelperForWhitelist.set(extension.forceHelperForWhitelist)
            task.maxFileBytes.set(extension.maxFileBytes)
            task.useAstScanner.set(extension.useAstScanner)
        }
    }
}
