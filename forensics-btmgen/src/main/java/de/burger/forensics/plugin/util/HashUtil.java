package de.burger.forensics.plugin.util;

/**
 * Stable hashing utilities for deterministic sharding.
 */
public final class HashUtil {
    private HashUtil() {
    }

    public static int stableShard(String key, int shards) {
        if (shards <= 1) {
            return 0;
        }
        int safeHash = (key == null ? 0 : key.hashCode()) & 0x7fffffff;
        return safeHash % shards;
    }
}
