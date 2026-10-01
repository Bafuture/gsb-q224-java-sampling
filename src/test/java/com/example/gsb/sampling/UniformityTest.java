package com.example.gsb.sampling;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 用小规模全量数据（n=10）反复完整抽样，验证每个元素被选中的实测频率
 * 与理论值 k/n 一致（卡方检验 + 偏差上限），并打印实测分布数据。
 */
class UniformityTest {

    @Test
    void everyItemIsSelectedWithEqualFrequency() {
        int n = 10;
        int k = 3;
        int trials = 60_000;
        long[] hits = SamplingDemo.measureUniform(n, k, trials, 20240601L);

        double expected = (double) trials * k / n;
        double chiSquare = 0.0;
        System.out.printf("uniformity check: n=%d, k=%d, trials=%d, expected hits per item=%.0f%n",
                n, k, trials, expected);
        for (int i = 0; i < n; i++) {
            chiSquare += (hits[i] - expected) * (hits[i] - expected) / expected;
            System.out.printf("  item %d: hits=%d (%.3f%%)%n", i, hits[i], 100.0 * hits[i] / trials);
        }
        System.out.printf("  chi2=%.3f (df=9, critical value at alpha=0.001 is 27.877)%n", chiSquare);

        for (long h : hits) {
            assertThat(h).isGreaterThan(0);
            assertThat((double) h / trials).isBetween(0.28, 0.32);
        }
        assertThat(chiSquare).isLessThan(27.877);
    }
}
