package de.burger.forensics.plugin.btmgen.gradle;

import org.jetbrains.annotations.NotNull;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.io.File;

/**
 * Legacy-style extension exposing Property<File>.
 * Prefer DirectoryProperty/RegularFileProperty if you can.
 */
public class BtmGenLegacyFileExtension {

    private final Property<@NotNull File> sourceRootFile;
    private final Property<@NotNull File> outputFile;

    @Inject
    public BtmGenLegacyFileExtension(ObjectFactory objects) {
        this.sourceRootFile = objects.property(File.class);
        this.outputFile = objects.property(File.class);
    }

    public Property<@NotNull File> getSourceRootFile() { return sourceRootFile; }
    public Property<@NotNull File> getOutputFile() { return outputFile; }
}
