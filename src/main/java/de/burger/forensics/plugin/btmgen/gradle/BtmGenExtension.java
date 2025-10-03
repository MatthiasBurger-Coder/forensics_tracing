package de.burger.forensics.plugin.btmgen.gradle;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.file.DirectoryProperty;

import javax.inject.Inject;
import java.util.List;

public class BtmGenExtension {
    public final ListProperty<String> srcDirs;
    public final DirectoryProperty outputDir;
    public final Property<Boolean> includeJava;
    public final Property<Boolean> useAstScanner;
    public final Property<String>  helperFqn;
    public final Property<Boolean> entryExit;
    public final Property<Integer> minBranchesPerMethod;
    public final Property<Boolean> logToFile;
    public final Property<String>  logFilePath;

    @Inject
    public BtmGenExtension(ObjectFactory objects) {
        this.srcDirs = objects.listProperty(String.class);
        this.outputDir = objects.directoryProperty();
        this.includeJava = objects.property(Boolean.class).convention(true);
        this.useAstScanner = objects.property(Boolean.class).convention(true);
        this.helperFqn = objects.property(String.class).convention("de.burger.forensics.ForensicsHelper");
        this.entryExit = objects.property(Boolean.class).convention(true);
        this.minBranchesPerMethod = objects.property(Integer.class).convention(0);
        this.logToFile = objects.property(Boolean.class).convention(true);
        this.logFilePath = objects.property(String.class).convention("logs/forensics-btmgen.log");
        this.srcDirs.convention(List.of("src/main/java"));
    }
}
