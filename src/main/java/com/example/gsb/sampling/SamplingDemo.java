package com.example.gsb.sampling;

import java.util.Locale;
import java.util.Map;

/**
 * 用小规模全量数据实测抽样分布，直接运行：
 * <pre>./mvnw -q compile exec:java -Dexec.mainClass=com.example.gsb.sampling.SamplingDemo</pre>
 * 或在测试 {@code UniformityTest} 中随 {@code mvn verify} 自动打印同样的数据。
 */
public final class SamplingDemo {

    private SamplingDemo() {
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.US);
        printUniformDistribution();
        printWeightedDistribution();
        printStratifiedStats();
    }

    /**
     * 全量数据 {0..9}，每次对全量流做一次 k=3 的蓄水池抽样，
     * 重复 60_000 次，统计每个元素进入样本的次数与占比（理论上均为 k/n = 30%）。
     */
    public static void printUniformDistribution() {
        int n = 10;
        int k = 3;
        int trials = 60_000;
        long[] hits = measureUniform(n, k, trials, 20240601L);

        System.out.println("=== 等概率蓄水池抽样均匀性实测 ===");
        System.out.printf("总体大小 n=%d, 样本容量 k=%d, 重复试验 %d 次, 期望命中率 = k/n = %.1f%%%n",
                n, k, trials, 100.0 * k / n);
        System.out.println("元素  入选次数   实测命中率   相对期望偏差");
        double expected = (double) trials * k / n;
        double chiSquare = 0.0;
        for (int i = 0; i < n; i++) {
            double p = (double) hits[i] / trials;
            chiSquare += (hits[i] - expected) * (hits[i] - expected) / expected;
            System.out.printf("  %2d   %6d    %7.3f%%    %+.3f%%%n",
                    i, hits[i], 100.0 * p, 100.0 * (p - k / (double) n));
        }
        System.out.printf("卡方统计量 chi2=%.3f（自由度 %d，α=0.001 临界值 27.877，越小越均匀）%n%n",
                chiSquare, n - 1);
    }

    /**
     * 两个元素权重分别为 1 和 3，k=1 时理论选中概率为 25% / 75%。
     */
    public static void printWeightedDistribution() {
        int trials = 60_000;
        long chosenHigh = 0;
        for (int t = 0; t < trials; t++) {
            WeightedReservoirSampler<String> sampler = new WeightedReservoirSampler<>(1, 20240601L + t);
            sampler.offer("low(w=1)", 1.0);
            sampler.offer("high(w=3)", 3.0);
            if ("high(w=3)".equals(sampler.snapshot().get(0))) {
                chosenHigh++;
            }
        }
        System.out.println("=== 带权蓄水池抽样比例实测 ===");
        System.out.printf("权重 1:3，重复 %d 次 k=1 抽样，理论选中率 25%%:75%%%n", trials);
        System.out.printf("low(w=1) 实测 %6.3f%% | high(w=3) 实测 %6.3f%%%n%n",
                100.0 * (trials - chosenHigh) / trials, 100.0 * chosenHigh / trials);
    }

    /** 分层抽样的统计信息（已处理总量、容量、各层样本数、选择概率说明）。 */
    public static void printStratifiedStats() {
        StratifiedSampler<Integer, String> sampler =
                new StratifiedSampler<Integer, String>(i -> i % 2 == 0 ? "even" : "odd", 2, 42L)
                        .withCapacity("odd", 5);
        for (int i = 1; i <= 20; i++) {
            sampler.offer(i);
        }
        StratifiedSampler.Stats<String> stats = sampler.stats();
        System.out.println("=== 分层抽样统计 ===");
        System.out.printf("已处理总量=%d, 已出现层总容量=%d%n",
                stats.totalProcessed(), stats.totalCapacity());
        for (Map.Entry<String, StratifiedSampler.StratumStats<String>> e : stats.strata().entrySet()) {
            StratifiedSampler.StratumStats<String> s = e.getValue();
            System.out.printf("层[%s]: 已处理=%d, 容量=%d, 当前样本数=%d, 概率说明={%s}%n",
                    s.stratum(), s.processed(), s.capacity(), s.sampleSize(), s.selectionProbability());
        }
    }

    static long[] measureUniform(int n, int k, int trials, long baseSeed) {
        long[] hits = new long[n];
        for (int t = 0; t < trials; t++) {
            ReservoirSampler<Integer> sampler = new ReservoirSampler<>(k, baseSeed + t);
            for (int i = 0; i < n; i++) {
                sampler.offer(i);
            }
            for (Integer chosen : sampler.snapshot()) {
                hits[chosen]++;
            }
        }
        return hits;
    }
}
