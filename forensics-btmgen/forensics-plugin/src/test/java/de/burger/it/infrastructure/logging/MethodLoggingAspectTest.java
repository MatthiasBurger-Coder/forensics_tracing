package de.burger.it.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.JoinPoint.StaticPart;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.SourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;

class MethodLoggingAspectTest {

    @TempDir
    Path tempDir;

    private final MethodLoggingAspect aspect = MethodLoggingAspect.aspectOf();

    @AfterEach
    void tearDown() {
        System.clearProperty("forensics.btmgen.logFile");
        System.clearProperty("forensics.btmgen.logToFile");
        MDC.clear();
    }

    @Test
    void writesEntriesAndErrorsToLogFileWhenEnabled() throws IOException {
        Path logFile = tempDir.resolve("aspect.log");
        System.setProperty("forensics.btmgen.logFile", logFile.toString());
        System.setProperty("forensics.btmgen.logToFile", "true");

        TestJoinPoint jp = new TestJoinPoint(SampleService.class, "doWork", new Object[] {"alpha", null});

        aspect.onEnter(jp);
        aspect.onReturn(jp);
        aspect.onThrow(jp, new IllegalStateException("boom"));

        String content = Files.readString(logFile);
        assertThat(content)
            .contains("→ SampleService.doWork(..)")
            .contains("← SampleService.doWork(..) OK")
            .contains("failed");
    }

    @Test
    void skipsFileLoggingWhenDisabled() {
        Path logFile = tempDir.resolve("disabled.log");
        System.setProperty("forensics.btmgen.logFile", logFile.toString());
        System.setProperty("forensics.btmgen.logToFile", "false");

        TestJoinPoint jp = new TestJoinPoint(SampleService.class, "noop", new Object[] {});

        aspect.onEnter(jp);

        assertThat(Files.exists(logFile)).isFalse();
    }

    private static final class SampleService {
        void doWork() {
        }
    }

    private static final class TestJoinPoint implements JoinPoint {
        private final Object[] args;
        private final Signature signature;

        TestJoinPoint(Class<?> declaringType, String method, Object[] args) {
            this.args = args;
            this.signature = new SimpleSignature(declaringType, method);
        }

        @Override
        public Object[] getArgs() {
            return args;
        }

        @Override
        public Signature getSignature() {
            return signature;
        }

        @Override
        public Object getTarget() {
            return null;
        }

        @Override
        public Object getThis() {
            return null;
        }

        @Override
        public String getKind() {
            return "method-execution";
        }

        @Override
        public StaticPart getStaticPart() {
            return new StaticPart() {
                @Override
                public int getId() {
                    return 0;
                }

                @Override
                public String getKind() {
                    return "method-execution";
                }

                @Override
                public Signature getSignature() {
                    return signature;
                }

                @Override
                public SourceLocation getSourceLocation() {
                    return null;
                }

                @Override
                public String toString() {
                    return signature.toString();
                }

                @Override
                public String toShortString() {
                    return signature.toShortString();
                }

                @Override
                public String toLongString() {
                    return signature.toLongString();
                }
            };
        }

        @Override
        public SourceLocation getSourceLocation() {
            return null;
        }

        @Override
        public String toShortString() {
            return signature.toShortString();
        }

        @Override
        public String toLongString() {
            return signature.toLongString();
        }

        @Override
        public String toString() {
            return signature.toString();
        }
    }

    private static final class SimpleSignature implements Signature {
        private final Class<?> declaringType;
        private final String method;

        SimpleSignature(Class<?> declaringType, String method) {
            this.declaringType = declaringType;
            this.method = method;
        }

        @Override
        public String toShortString() {
            return declaringType.getSimpleName() + "." + method + "(..)";
        }

        @Override
        public String toLongString() {
            return toShortString();
        }

        @Override
        public String toString() {
            return toShortString();
        }

        @Override
        public String getName() {
            return method;
        }

        @Override
        public int getModifiers() {
            return 0;
        }

        @Override
        public Class<?> getDeclaringType() {
            return declaringType;
        }

        @Override
        public String getDeclaringTypeName() {
            return declaringType.getName();
        }
    }
}
