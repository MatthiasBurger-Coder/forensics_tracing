package de.burger.forensics.plugin.btmgen.gradle;

import de.burger.forensics.plugin.btmgen.render.BytemanRuleRenderer;
import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistries;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistry;
import de.burger.forensics.plugin.btmgen.writer.BtmFileWriter;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.Optional;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans Java sources and renders Byteman rules using StrategyRegistry.
 * This task uses a lightweight heuristic parser (no external AST deps).
 */
public abstract class GenerateBtmTask extends DefaultTask {

    // ---- Configurable inputs ----
    @InputDirectory
    @Optional
    public abstract DirectoryProperty getSourceRoot();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getOutputFile();

    /** Optional: render exactly one template with given class/method instead of scanning. */
    @Input @Optional public abstract Property<@NotNull String> getTemplateId();
    @Input @Optional public abstract Property<@NotNull String> getClassName();
    @Input @Optional public abstract Property<@NotNull String> getMethodName();
    @Input @Optional public abstract Property<@NotNull String> getMethodDesc();
    @Input @Optional public abstract Property<@NotNull Boolean> getIncludeEntryExit();
    @Input @Optional public abstract Property<@NotNull Integer>  getMinBranchesPerMethod();
    @Input @Optional public abstract Property<@NotNull Boolean> getLogToFile();
    @Input @Optional public abstract Property<@NotNull String>  getLogFilePath();

    // Provided by plugin (or set manually in build script)
    private BtmGenExtension extension;

    @Inject
    public GenerateBtmTask(ObjectFactory objects) {
        // sensible defaults
        getSourceRoot().convention(getProject().getLayout().getProjectDirectory().dir("src/main/java"));
        getOutputFile().convention(
                getProject().getLayout().getBuildDirectory().file("forensics/forensics.btm")
        );
    }

    /** Injected via plugin apply() */
    public void setExtension(BtmGenExtension ext) {
        this.extension = Objects.requireNonNull(ext, "BtmGenExtension must not be null");

        // ✅ Use direct setters (no Provider lambdas) to avoid "Provider is not a functional interface"
        if (ext.getSourceRoot().isPresent()) {
            getSourceRoot().set(ext.getSourceRoot().get());
        }
        if (ext.getOutputFile().isPresent()) {
            getOutputFile().fileValue(ext.getOutputFile().get());
        }
    }

    @TaskAction
    public void generate() {
        ensureExtension();
        final Path srcRoot = getSourceRoot().get().getAsFile().toPath();
        final Path outFile = getOutputFile().get().getAsFile().toPath();
        final StrategyRegistry registry = extension.getRegistry() != null
                ? extension.getRegistry() : StrategyRegistries.defaultRegistry();

        BytemanRuleRenderer renderer = BytemanRuleRenderer.of(registry);

        try {
            Files.createDirectories(outFile.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create output directory for " + outFile, e);
        }

        List<String> allRules = new ArrayList<>();

        if (hasMinimalInputs()) {
            // Single-template mode (explicit params)
            RuleParams params = new RuleParams(
                    templateIdOrDefault(),
                    getClassName().get(),
                    getMethodName().get(),
                    getMethodDesc().getOrNull(),
                    getClassName().get() + "#" + getMethodName().get(),
                    null,
                    null
            );
            allRules.add(renderer.render(templateIdOrDefault(), params));
        } else {
            // Scan all .java files and derive rules via simple heuristics
            getLogger().lifecycle("Scanning sources in {}", srcRoot.toAbsolutePath());
            try (var stream = Files.walk(srcRoot)) {
                stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                        .forEach(javaFile -> {
                            try {
                                var fileRules = analyzeFileAndRender(javaFile, renderer);
                                allRules.addAll(fileRules);
                            } catch (Exception ex) {
                                throw new RuntimeException("Failed analyzing " + javaFile, ex);
                            }
                        });
            } catch (IOException e) {
                throw new RuntimeException("Walking sourceRoot failed: " + srcRoot, e);
            }
        }

        // Write output once
        try {
            // Support both writer ctors to avoid signature drift issues
            BtmFileWriter writer;
            try {
                writer = new BtmFileWriter(Clock.systemDefaultZone(), outFile);
            } catch (NoSuchMethodError | NoClassDefFoundError e) {
                writer = new BtmFileWriter(outFile);
            }
            writer.write(allRules);
        } catch (Exception e) {
            throw new RuntimeException("Failed writing BTM file " + outFile, e);
        }

        getLogger().lifecycle("Generated {} rules -> {}", allRules.size(), outFile.toAbsolutePath());
    }

    private void ensureExtension() {
        if (this.extension == null) {
            BtmGenExtension ext = getProject().getExtensions().findByType(BtmGenExtension.class);
            if (ext == null) {
                ext = getProject().getObjects().newInstance(BtmGenExtension.class);
            }
            setExtension(ext);
        }
    }

    private boolean hasMinimalInputs() {
        return getTemplateId().isPresent() && getClassName().isPresent() && getMethodName().isPresent();
    }

    private String templateIdOrDefault() {
        String id = getTemplateId().getOrElse("METHOD_ENTER");
        return (id == null || id.isBlank()) ? "METHOD_ENTER" : id;
    }

    // ---- Heuristic parsing & rendering ----

    private List<String> analyzeFileAndRender(Path javaFile, BytemanRuleRenderer renderer) throws IOException {
        String code = Files.readString(javaFile);

        String pkg = findPackage(code);
        List<MethodSig> methods = findMethods(code, pkg);

        List<String> rules = new ArrayList<>();
        boolean hasSwitch = code.contains("switch");
        boolean hasIf = code.contains("if");

        for (MethodSig m : methods) {
            // Always add method enter/exit
            rules.add(renderer.render("METHOD_ENTER", new RuleParams(
                    "METHOD_ENTER", m.className, m.methodName, m.methodDesc, m.displayName(), null, null
            )));
            rules.add(renderer.render("METHOD_EXIT", new RuleParams(
                    "METHOD_EXIT", m.className, m.methodName, m.methodDesc, m.displayName(), null, null
            )));

            // Return / Throw
            if (m.hasReturn) {
                rules.add(renderer.render("RETURN", new RuleParams(
                        "RETURN", m.className, m.methodName, m.methodDesc, m.displayName(), null, null
                )));
            }
            if (m.hasThrow) {
                rules.add(renderer.render("THROW", new RuleParams(
                        "THROW", m.className, m.methodName, m.methodDesc, m.displayName(), null, null
                )));
            }

            // If / Switch – map both branches & switch markers (coarse grained)
            if (hasIf) {
                rules.add(renderer.render("IF_TRUE", new RuleParams(
                        "IF_TRUE", m.className, m.methodName, m.methodDesc, m.displayName(), "true", null
                )));
                rules.add(renderer.render("IF_FALSE", new RuleParams(
                        "IF_FALSE", m.className, m.methodName, m.methodDesc, m.displayName(), "false", null
                )));
            }
            if (hasSwitch) {
                rules.add(renderer.render("SWITCH", new RuleParams(
                        "SWITCH", m.className, m.methodName, m.methodDesc, m.displayName(), null, null
                )));
                rules.add(renderer.render("SWITCH_CASE", new RuleParams(
                        "SWITCH_CASE", m.className, m.methodName, m.methodDesc, m.displayName(), null, null
                )));
            }
        }

        return rules;
    }

    private String findPackage(String code) {
        Matcher m = Pattern.compile("\\bpackage\\s+([a-zA-Z0-9_.]+)\\s*;").matcher(code);
        if (m.find()) return m.group(1);
        return null;
    }

    /** Very small method signature finder (no full parser, but robust enough for typical code). */
    private List<MethodSig> findMethods(String code, String pkg) {
        String className = null;
        Matcher cls = Pattern.compile("(?:public\\s+)?class\\s+([A-Za-z0-9_]+)").matcher(code);
        if (cls.find()) className = cls.group(1);

        Pattern meth = Pattern.compile(
                "([\\w<>\\[\\]]+\\s+)*([A-Za-z_][A-Za-z0-9_<>\\[\\]]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
        Matcher mm = meth.matcher(code);

        List<MethodSig> out = new ArrayList<>();
        while (mm.find()) {
            String method = mm.group(3);
            int start = code.indexOf('{', mm.end());
            if (start < 0) continue;
            int end = findMatchingBrace(code, start);
            if (end < 0) end = Math.min(code.length(), start + 2000);

            String body = code.substring(start, end);
            boolean hasReturn = body.contains("return ");
            boolean hasThrow = body.contains("throw ");

            String fqcn = (pkg != null && className != null) ? (pkg + "." + className) : className;
            out.add(new MethodSig(fqcn != null ? fqcn : "Unknown", method, null, hasReturn, hasThrow));
        }
        return out;
    }

    private int findMatchingBrace(String code, int openPos) {
        int depth = 0;
        for (int i = openPos; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i + 1;
            }
        }
        return -1;
    }

    /** Minimal method model for heuristic scanning. */
    private static final class MethodSig {
        final String className;
        final String methodName;
        final String methodDesc; // left null (no asm desc here)
        final boolean hasReturn;
        final boolean hasThrow;

        MethodSig(String className, String methodName, String methodDesc, boolean hasReturn, boolean hasThrow) {
            this.className = className;
            this.methodName = methodName;
            this.methodDesc = methodDesc;
            this.hasReturn = hasReturn;
            this.hasThrow = hasThrow;
        }

        String displayName() { return className + "#" + methodName; }
    }
}
