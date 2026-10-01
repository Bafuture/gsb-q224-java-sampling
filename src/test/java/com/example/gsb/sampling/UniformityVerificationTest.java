package com.example.gsb.sampling;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用小规模全量数据（10 个元素、容量 3）重复实验 120000 次，
 * 统计每个元素被选中的实测频率，验证 Algorithm R 的等概率性质。
 */
class UniformityVerificationTest {

    @Test
    void everyElementIsSelectedWithEqualProbability() {
        int population = 10;
        int capacity = 3;
        int trials = 120_000;

        long[] frequencies = new long[population];
        for (int trial = 0; trial < trials; trial++) {
            ReservoirSampler<Integer> sampler = new ReservoirSampler<>(capacity, trial);
            for (int item = 0; item < population; item++) {
                sampler.offer(item);
            }
            List<Integer> sample = sampler.snapshot();
            assertThat(sample).hasSize(capacity);
            for (int v : sample) {
                frequencies[v]++;
            }
        }

        double expectedCount = (double) trials * capacity / population;
        double expectedProbability = (double) capacity / population;

        System.out.printf("=== 均匀性验证：population=%d, capacity=%d, trials=%d, 期望概率=%.3f ===%n",
                population, capacity, trials, expectedProbability);
        System.out.println("元素 | 实测选中次数 | 实测频率 | 期望次数 | 偏差");
        System.out.println("-----|-------------|----------|----------|-------");

        double chiSquare = 0.0;
        for (int i = 0; i < population; i++) {
            double observed = frequencies[i];
            double empiricalProbability = observed / trials;
            double deviation = (observed - expectedCount) / expectedCount * 100.0;
            chiSquare += (observed - expectedCount) * (observed - expectedCount) / expectedCount;
            System.out.printf("  %d  | %11d | %.4f   | %8.0f | %+.2f%%%n",
                    i, frequencies[i], empiricalProbability, expectedCount, deviation);

            // 每个元素的理论选中次数为 36000，标准差约 159；
            // ±3% 约等于 ±6.8 个标准差，既严格又不会因随机抖动误报
            assertThat(observed)
                    .as("element %d selection count (expected ~%.0f)", i, expectedCount)
                    .isBetween(expectedCount * 0.97, expectedCount * 1.03);
        }
        System.out.printf("卡方统计量 chi-square = %.3f (自由度 9, 0.999 分位临界值 27.88)%n", chiSquare);
        assertThat(chiSquare)
                .as("chi-square should be well below the critical region for a uniform sampler")
                .isLessThan(40.0);
    }

    @Test
    void sampleIsAlwaysASizeKSubsetOfTheFullPopulation() {
        for (int trial = 0; trial < 1000; trial++) {
            ReservoirSampler<Integer> sampler = new ReservoirSampler<>(4, 900_000L + trial);
            for (int item = 0; item < 7; item++) {
                sampler.offer(item);
            }
            List<Integer> sample = sampler.snapshot();
            assertThat(sample).hasSize(4);
            assertThat(sample).doesNotHaveDuplicates();
            assertThat(sample).allMatch(v -> v >= 0 && v < 7);
        }
    }
}
