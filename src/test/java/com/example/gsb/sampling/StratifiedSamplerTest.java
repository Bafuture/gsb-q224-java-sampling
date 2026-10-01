package com.example.gsb.sampling;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StratifiedSamplerTest {

    private record User(String tier, int id) {
    }

    private static StratifiedSampler<User, String> samplerWithSeed(long seed) {
        return new StratifiedSampler<>(User::tier, tier -> switch (tier) {
            case "vip" -> 2;
            case "normal" -> 3;
            default -> 1;
        }, seed);
    }

    @Test
    void eachStratumMaintainsIndependentSample() {
        StratifiedSampler<User, String> sampler = samplerWithSeed(42L);

        for (int i = 0; i < 50; i++) {
            sampler.offer(new User("vip", i));
        }
        for (int i = 0; i < 50; i++) {
            sampler.offer(new User("normal", 1000 + i));
        }

        List<User> vip = sampler.snapshot("vip");
        List<User> normal = sampler.snapshot("normal");

        assertThat(vip).hasSize(2);
        assertThat(normal).hasSize(3);
        assertThat(vip).allMatch(u -> u.tier().equals("vip"));
        assertThat(normal).allMatch(u -> u.tier().equals("normal"));
        assertThat(sampler.strata()).containsExactlyInAnyOrder("vip", "normal");
        assertThat(sampler.processedCount()).isEqualTo(100);
    }

    @Test
    void strataAreIsolatedOtherLayersDoNotTouchThisLayer() {
        StratifiedSampler<User, String> sampler = samplerWithSeed(7L);
        for (int i = 0; i < 40; i++) {
            sampler.offer(new User("vip", i));
        }
        List<User> vipBefore = sampler.snapshot("vip");

        // 大量投喂其它层的数据，vip 层样本必须完全不变
        for (int i = 0; i < 500; i++) {
            sampler.offer(new User("normal", 5000 + i));
        }
        List<User> vipAfter = sampler.snapshot("vip");
        assertThat(vipAfter).isEqualTo(vipBefore);
    }

    @Test
    void perStratumCapacityIsConfigurable() {
        StratifiedSampler<User, String> uniformCapacity =
                new StratifiedSampler<>(User::tier, 4, 1L);
        for (int i = 0; i < 10; i++) {
            uniformCapacity.offer(new User("a", i));
            uniformCapacity.offer(new User("b", i));
        }
        assertThat(uniformCapacity.snapshot("a")).hasSize(4);
        assertThat(uniformCapacity.snapshot("b")).hasSize(4);
    }

    @Test
    void reproducibleWithSameSeedRegardlessOfArrivalOrder() {
        StratifiedSampler<User, String> a = samplerWithSeed(2024L);
        StratifiedSampler<User, String> b = samplerWithSeed(2024L);

        // 完全相同的交错顺序
        for (int i = 0; i < 100; i++) {
            a.offer(new User(i % 2 == 0 ? "vip" : "normal", i));
            b.offer(new User(i % 2 == 0 ? "vip" : "normal", i));
        }
        assertThat(b.snapshot()).usingRecursiveComparison().isEqualTo(a.snapshot());

        // 层的到达顺序改变：每层内部样本仍然一致（每层种子只与主种子和层键有关）
        StratifiedSampler<User, String> reordered = samplerWithSeed(2024L);
        for (int i = 0; i < 100; i += 2) {
            reordered.offer(new User("vip", i));
        }
        for (int i = 1; i < 100; i += 2) {
            reordered.offer(new User("normal", i));
        }
        assertThat(reordered.snapshot("vip")).usingRecursiveComparison()
                .isEqualTo(a.snapshot("vip"));
        assertThat(reordered.snapshot("normal")).usingRecursiveComparison()
                .isEqualTo(a.snapshot("normal"));
    }

    @Test
    void snapshotIsIsolated() {
        StratifiedSampler<User, String> sampler = samplerWithSeed(5L);
        sampler.offer(new User("vip", 1));
        List<User> vip = sampler.snapshot("vip");
        vip.clear();
        assertThat(sampler.snapshot("vip")).hasSize(1);

        Map<String, List<User>> all = sampler.snapshot();
        all.clear();
        sampler.offer(new User("vip", 2));
        assertThat(sampler.snapshot("vip")).hasSize(2);
        assertThat(sampler.snapshot("unknown")).isEmpty();
    }

    @Test
    void statsReportPerStratumCountsAndProbability() {
        StratifiedSampler<User, String> sampler = samplerWithSeed(9L);
        for (int i = 0; i < 20; i++) {
            sampler.offer(new User("vip", i));
        }
        for (int i = 0; i < 30; i++) {
            sampler.offer(new User("normal", i));
        }

        Map<String, SamplingStats> stats = sampler.stats();
        assertThat(stats).containsOnlyKeys("vip", "normal");

        SamplingStats vipStats = stats.get("vip");
        assertThat(vipStats.processedCount()).isEqualTo(20);
        assertThat(vipStats.capacity()).isEqualTo(2);
        assertThat(vipStats.sampleSize()).isEqualTo(2);
        assertThat(vipStats.selectionProbability()).contains("2/20").contains("0.100000");

        SamplingStats normalStats = stats.get("normal");
        assertThat(normalStats.processedCount()).isEqualTo(30);
        assertThat(normalStats.capacity()).isEqualTo(3);
        assertThat(normalStats.sampleSize()).isEqualTo(3);
        assertThat(normalStats.selectionProbability()).contains("3/30");
    }

    @Test
    void rejectsInvalidPerStratumCapacity() {
        StratifiedSampler<User, String> bad =
                new StratifiedSampler<>(User::tier, tier -> 0, 1L);
        assertThatThrownBy(() -> bad.offer(new User("x", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("x");
    }
}
