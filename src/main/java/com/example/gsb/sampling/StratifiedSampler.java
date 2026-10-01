package com.example.gsb.sampling;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * 分层抽样：按指定维度把数据归入不同“层（stratum）”，各层独立维护一个
 * 蓄水池，层与层之间互不影响，每层样本量可单独配置。
 *
 * <p>典型场景：希望整体样本中不同类别（如地区、年龄段、渠道）的比例可控，
 * 而不是由到达频率自然决定。每个新层第一次出现时惰性创建，因此同样
 * 适用于事先不知道有哪些层、各层多少条数据的流式场景。
 *
 * <p>层内默认使用等概率蓄水池抽样（Algorithm R）；若构造时提供权重函数，
 * 则每层使用带权蓄水池抽样（A-Res）。各层使用由主种子经确定性混洗
 * （SplitMix64）派生出的独立随机源，保证给定种子后结果完全可复现。
 */
public final class StratifiedSampler<T, K> {

    private interface Stratum<T> {
        List<T> snapshot();
        long processed();
        int capacity();
        int size();
        double totalWeight();
        boolean weighted();
    }

    private static final class UnweightedStratum<T> implements Stratum<T> {
        private final ReservoirSampler<T> sampler;

        UnweightedStratum(int capacity, long seed) {
            this.sampler = new ReservoirSampler<>(capacity, seed);
        }

        void accept(T item) {
            sampler.offer(item);
        }

        @Override public List<T> snapshot() {
            return sampler.snapshot();
        }

        @Override public long processed() {
            return sampler.processedCount();
        }

        @Override public int capacity() {
            return sampler.capacity();
        }

        @Override public int size() {
            return sampler.size();
        }

        @Override public double totalWeight() {
            return 0.0;
        }

        @Override public boolean weighted() {
            return false;
        }
    }

    private static final class WeightedStratum<T> implements Stratum<T> {
        private final WeightedReservoirSampler<T> sampler;

        WeightedStratum(int capacity, long seed) {
            this.sampler = new WeightedReservoirSampler<>(capacity, seed);
        }

        void accept(T item, double weight) {
            sampler.offer(item, weight);
        }

        @Override public List<T> snapshot() {
            return sampler.snapshot();
        }

        @Override public long processed() {
            return sampler.processedCount();
        }

        @Override public int capacity() {
            return sampler.capacity();
        }

        @Override public int size() {
            return sampler.size();
        }

        @Override public double totalWeight() {
            return sampler.totalWeight();
        }

        @Override public boolean weighted() {
            return true;
        }
    }

    /** 单层统计信息。 */
    public record StratumStats<K>(
            K stratum,
            long processed,
            int capacity,
            int sampleSize,
            double totalWeight,
            boolean weighted,
            String selectionProbability) {
    }

    /** 全部分层统计快照。 */
    public record Stats<K>(
            long totalProcessed,
            int totalCapacity,
            Map<K, StratumStats<K>> strata) {
    }

    private final Function<T, K> classifier;
    private final ToDoubleFunction<T> weightFunction;
    private final int defaultCapacity;
    private final long seed;
    private final Map<K, Integer> capacityOverrides = new LinkedHashMap<>();
    private final Map<K, Stratum<T>> strata = new LinkedHashMap<>();
    private long totalProcessed;
    private int stratumCounter;

    public StratifiedSampler(Function<T, K> classifier, int defaultCapacity, long seed) {
        this(classifier, null, defaultCapacity, seed);
    }

    public StratifiedSampler(Function<T, K> classifier,
                             ToDoubleFunction<T> weightFunction,
                             int defaultCapacity,
                             long seed) {
        this.classifier = Objects.requireNonNull(classifier, "classifier must not be null");
        this.weightFunction = weightFunction;
        if (defaultCapacity <= 0) {
            throw new IllegalArgumentException("defaultCapacity must be positive, got " + defaultCapacity);
        }
        this.defaultCapacity = defaultCapacity;
        this.seed = seed;
    }

    /**
     * 为指定层配置独立样本量。必须在该层第一条数据到达之前调用；
     * 未配置的层使用默认容量。
     *
     * @return 当前对象，便于链式调用
     */
    public StratifiedSampler<T, K> withCapacity(K stratum, int capacity) {
        Objects.requireNonNull(stratum, "stratum must not be null");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, got " + capacity);
        }
        if (strata.containsKey(stratum)) {
            throw new IllegalStateException(
                    "stratum " + stratum + " already created; configure capacity before its first item");
        }
        capacityOverrides.put(stratum, capacity);
        return this;
    }

    /** 处理一条数据：归入对应层，并在该层内完成一次蓄水池抽样。 */
    public void offer(T item) {
        Objects.requireNonNull(item, "item must not be null");
        K key = Objects.requireNonNull(classifier.apply(item), "stratum key must not be null");
        Stratum<T> stratum = strata.computeIfAbsent(key, this::createStratum);
        totalProcessed++;
        if (stratum instanceof WeightedStratum<T> weighted) {
            weighted.accept(item, weightFunction.applyAsDouble(item));
        } else {
            ((UnweightedStratum<T>) stratum).accept(item);
        }
    }

    private Stratum<T> createStratum(K key) {
        int capacity = capacityOverrides.getOrDefault(key, defaultCapacity);
        long stratumSeed = deriveSeed(seed, stratumCounter++);
        return weightFunction == null
                ? new UnweightedStratum<>(capacity, stratumSeed)
                : new WeightedStratum<>(capacity, stratumSeed);
    }

    private static long deriveSeed(long masterSeed, long index) {
        return Seeds.mix64(masterSeed + 0x9E3779B97F4A7C15L * (index + 1));
    }

    /** 返回某一层当前样本的独立快照；该层尚未出现时返回空列表。 */
    public List<T> snapshot(K stratum) {
        Objects.requireNonNull(stratum, "stratum must not be null");
        Stratum<T> s = strata.get(stratum);
        return s == null ? List.of() : s.snapshot();
    }

    /** 返回所有层当前样本的独立快照（按层首次出现顺序），不影响后续抽样。 */
    public Map<K, List<T>> snapshotAll() {
        Map<K, List<T>> copy = new LinkedHashMap<>();
        strata.forEach((key, s) -> copy.put(key, s.snapshot()));
        return Map.copyOf(copy);
    }

    /** 当前已出现的层（按首次出现顺序）。 */
    public java.util.Set<K> strata() {
        return java.util.Collections.unmodifiableSet(strata.keySet());
    }

    /** 已处理数据总量。 */
    public long totalProcessed() {
        return totalProcessed;
    }

    /** 各层容量之和（仅统计已经出现的层）。 */
    public int totalCapacity() {
        return strata.values().stream().mapToInt(Stratum::capacity).sum();
    }

    /** 采集当前统计信息快照：总量、容量、各层样本数与选择概率说明。 */
    public Stats<K> stats() {
        Map<K, StratumStats<K>> perStratum = new LinkedHashMap<>();
        int capacitySum = 0;
        for (Map.Entry<K, Stratum<T>> e : strata.entrySet()) {
            Stratum<T> s = e.getValue();
            capacitySum += s.capacity();
            perStratum.put(e.getKey(), new StratumStats<>(
                    e.getKey(),
                    s.processed(),
                    s.capacity(),
                    s.size(),
                    s.totalWeight(),
                    s.weighted(),
                    describeProbability(s)));
        }
        return new Stats<>(totalProcessed, capacitySum, Map.copyOf(perStratum));
    }

    private static String describeProbability(Stratum<?> s) {
        long n = s.processed();
        int k = s.capacity();
        if (n == 0) {
            return "no items processed yet";
        }
        if (s.weighted()) {
            return "weighted: P(i) proportional to w_i; k=1 exact P(i)=w_i/SumW, "
                    + "k>1 approx min(1, k*w_i/SumW); capacity k=" + k + ", processed n=" + n;
        }
        return "uniform: P(i)=min(1, k/n)=" + String.format("%.6f", Math.min(1.0, (double) k / n))
                + ", capacity k=" + k + ", processed n=" + n;
    }
}
