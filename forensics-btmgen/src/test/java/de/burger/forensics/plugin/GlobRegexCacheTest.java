// DEST: src/test/java/de/burger/forensics/plugin/GlobRegexCacheTest.java
package de.burger.forensics.plugin;

import static de.burger.forensics.plugin.GlobUtils.globToRegexCached;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class GlobRegexCacheTest {

    @Test
    void returnsIdenticalInstanceForIdenticalGlob() {
        Object first = globToRegexCached("**/*.java");
        Object second = globToRegexCached("**/*.java");

        assertSame(first, second, "Cache should return the same instance for identical glob");
    }

    @Test
    void returnsDifferentInstancesForDifferentGlobs() {
        Object javaGlob = globToRegexCached("**/*.java");
        Object kotlinGlob = globToRegexCached("**/*.kt");

        assertNotSame(javaGlob, kotlinGlob, "Different globs should yield different cached objects");
    }
}
