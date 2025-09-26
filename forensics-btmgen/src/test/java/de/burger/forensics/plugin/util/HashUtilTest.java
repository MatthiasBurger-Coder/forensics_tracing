package de.burger.forensics.plugin.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashUtilTest {

    @Test
    void stableForSameKey() {
        int shards = 8;
        String key = "com.example.Demo#run:42";
        int first = HashUtil.stableShard(key, shards);
        int second = HashUtil.stableShard(key, shards);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void distributionIsReasonable() {
        int shards = 8;
        int samples = 10_000;
        int[] counts = new int[shards];
        for (int i = 0; i < samples; i++) {
            String key = "C" + (i % 1000) + "#m" + (i % 77) + ":" + (i % 123);
            int shard = HashUtil.stableShard(key, shards);
            counts[shard]++;
        }
        double ideal = samples / (double) shards;
        int lower = (int) Math.floor(ideal * 0.6);
        int upper = (int) Math.ceil(ideal * 1.4);
        for (int count : counts) {
            assertThat(count).isBetween(lower, upper);
        }
    }
}
