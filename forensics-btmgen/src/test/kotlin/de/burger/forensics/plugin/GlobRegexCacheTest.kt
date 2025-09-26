package de.burger.forensics.plugin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GlobRegexCacheTest {
    @Test
    fun `returns identical instance for identical glob`() {
        val first = globToRegexCached("**/*.java")
        val second = globToRegexCached("**/*.java")

        assertThat(first).isSameAs(second)
    }

    @Test
    fun `returns different instances for different globs`() {
        val javaGlob = globToRegexCached("**/*.java")
        val kotlinGlob = globToRegexCached("**/*.kt")

        assertThat(javaGlob).isNotSameAs(kotlinGlob)
    }
}
