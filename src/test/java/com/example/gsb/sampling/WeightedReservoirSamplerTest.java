package com.example.gsb.sampling;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeightedReservoirSamplerTest {

    @Test
    void selectionFrequencyIsProportionalToWeight() {
        int trials = 60_000;
        double[] weights = {1.0, 2.0, 3.0};
        long[] counts = new long[3];

        for (int trial = 0; trial < trials; trial++) {
            WeightedReservoirSampler<Integer> sampler = new WeightedReservoirSampler<>(1, trial);
            for (int i = 0; i < weights.length; i++) {
                sampler.offer(i, weights[i]);
            }
            Integer picked = sampler.snapshot().get(0);
            counts[picked]++;
        }

        double[] expected = {1.0 / 6, 2.0 / 6, 3.0 / 6};
        System.out.println("=== 带权比例验证 (k=1, weights=1:2:3, trials=" + trials + ") ===");
        for (int i = 0; i < 3; i++) {
            double actual = (double) counts[i] / trials;
            System.out.printf("元素%d weight=%.0f  实测=%.5f  期望=%.5f  次数=%d%n",
                    i, weights[i], actual, expected[i], counts[i]);
            // 理论标准差约 0.0015~0.002，放宽到 ±0.02 避免统计抖动
            assertThat(actual).as("element %d frequency", i)
                    .isBetween(expected[i] - 0.02, expected[i] + 0.02);
        }
    }

    @Test
    void respectsCapacityAndKeepsHeavierItemsMoreOften() {
        // k=2 且流中出现更重元素时，重元素几乎总能挤入
        WeightedReservoirSampler<String> sampler = new WeightedReservoirSampler<>(2, 5L);
        sampler.offer("a", 1);
        sampler.offer("b", 1);
        assertThat(sampler.snapshot()).hasSize(2);

        long heavyWins = 0;
        int runs = 5000;
        for (int run = 0; run < runs; run++) {
            WeightedReservoirSampler<String> s = new WeightedReservoirSampler<>(2, 50L + run);
            s.offer("a", 1);
            s.offer("b", 1);
            s.offer("heavy", 100);
            if (s.snapshot().contains("heavy")) {
                heavyWins++;
            }
        }
        // heavy 入选概率 = 100/102 ≈ 0.980
        assertThat((double) heavyWins / runs).isBetween(0.96, 1.0);
    }

    @Test
    void reproducibleWithSameSeed() {
        List<String> first = runWeighted(3, 77L);
        List<String> second = runWeighted(3, 77L);
        List<String> otherSeed = runWeighted(3, 78L);
        assertThat(second).isEqualTo(first);
        assertThat(otherSeed).isNotEqualTo(first);
    }

    @Test
    void snapshotIsIsolated() {
        WeightedReservoirSampler<Integer> sampler = new WeightedReservoirSampler<>(2, 3L);
        sampler.offer(1, 1);
        sampler.offer(2, 2);
        List<Integer> snapshot = sampler.snapshot();
        snapshot.clear();

        sampler.offer(3, 4);
        assertThat(sampler.snapshot()).hasSize(2);
    }

    @Test
    void statsExplainWeightedSelectionProbability() {
        WeightedReservoirSampler<Integer> sampler = new WeightedReservoirSampler<>(4, 1L);
        sampler.offer(1, 1);
        sampler.offer(2, 5);
        SamplingStats stats = sampler.stats();
        assertThat(stats.processedCount()).isEqualTo(2);
        assertThat(stats.capacity()).isEqualTo(4);
        assertThat(stats.sampleSize()).isEqualTo(2);
        assertThat(stats.selectionProbability())
                .contains("weighted A-Res")
                .contains("w_i / sum(w)");
    }

    @Test
    void rejectsInvalidCapacityAndNonPositiveWeight() {
        assertThatThrownBy(() -> new WeightedReservoirSampler<>(0, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        WeightedReservoirSampler<Integer> sampler = new WeightedReservoirSampler<>(2, 1L);
        assertThatThrownBy(() -> sampler.offer(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sampler.offer(1, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sampler.offer(1, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sampler.offer(1, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<String> runWeighted(int capacity, long seed) {
        WeightedReservoirSampler<String> sampler = new WeightedReservoirSampler<>(capacity, seed);
        sampler.offer("a", 1);
        sampler.offer("b", 2);
        sampler.offer("c", 3);
        sampler.offer("d", 4);
        sampler.offer("e", 5);
        return sampler.snapshot();
    }
}
