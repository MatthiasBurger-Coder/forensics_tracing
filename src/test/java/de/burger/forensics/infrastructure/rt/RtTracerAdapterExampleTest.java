package de.burger.forensics.infrastructure.rt;

import de.burger.forensics.application.tracing.Tracer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RtTracerAdapterExampleTest {

    private static final Path EXAMPLE_SOURCE = Path.of("examples", "RtTracerAdapter.java");

    @BeforeAll
    static void enableRuntime() {
        System.setProperty("forensics.rt.enabled", "true");
    }

    @Test
    void exampleAdapterCompilesAgainstTheCurrentTracerInterfaceAndDelegatesSetVariable(@TempDir Path tempDir) throws Exception {
        assertThat(EXAMPLE_SOURCE)
                .as("example adapter source")
                .exists();

        try (URLClassLoader classLoader = compileExample(tempDir)) {
            Class<?> adapterClass = Class.forName("examples.RtTracerAdapter", true, classLoader);

            assertThat(Tracer.class.isAssignableFrom(adapterClass)).isTrue();
            assertThat(Arrays.stream(adapterClass.getDeclaredMethods()).map(Method::getName).toList())
                    .contains("setVariable")
                    .doesNotContain("var");

            Object adapter = adapterClass.getConstructor().newInstance();
            String output = RtTraceLogCapture.capture(() -> invokeSetVariable(adapterClass, adapter));

            assertThat(output)
                    .contains("\"event\":\"VAR_SET\"")
                    .contains("\"name\":\"answer\"")
                    .contains("\"value\":\"42\"");
        }
    }

    private static URLClassLoader compileExample(Path tempDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler)
                .as("system Java compiler")
                .isNotNull();

        Path classesDir = Files.createDirectories(tempDir.resolve("compiled-example"));
        StringWriter compilerOutput = new StringWriter();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(EXAMPLE_SOURCE.toFile());
            List<String> options = List.of(
                    "--release", "17",
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classesDir.toString()
            );

            Boolean success = compiler.getTask(compilerOutput, fileManager, null, options, null, compilationUnits).call();
            assertThat(success)
                    .withFailMessage("Compiling %s failed:%n%s", EXAMPLE_SOURCE, compilerOutput)
                    .isTrue();
        }

        return new URLClassLoader(new URL[]{classesDir.toUri().toURL()}, Tracer.class.getClassLoader());
    }

    private static void invokeSetVariable(Class<?> adapterClass, Object adapter) {
        try {
            adapterClass.getMethod("setVariable", String.class, Object.class).invoke(adapter, "answer", 42);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke setVariable on compiled example adapter", exception);
        }
    }
}
