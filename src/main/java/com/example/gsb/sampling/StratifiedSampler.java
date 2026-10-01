package com.example.gsb.sampling;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * 分层抽样（Stratified Sampling）。
 *
 * <p>按指定分类维度把数据流划分到若干互斥的层（stratum），每一层独立维护一个
 * 等概率蓄水池（{@link ReservoirSampler}）。层与层之间互不影响：
 * 某一层的数据流到达只会更新该层的样本。每层容量可通过
 * {@code Function<K, Integer>} 分别配置，从而控制不同层级的样本比例。
 *
 * <p>每一层使用由主种子与层键确定性派生的独立随机种子，因此：
 * <ul>
 *   <li>各层随机数流相互独立（层间隔离）；</li>
 *   <li>相同主种子 + 相同输入序列必然产生完全相同的分层结果，
 *       且与层的到达顺序无关。</li>
 * </ul>
 *
 * @param <T> 数据元素类型
 * @param <K> 层键类型（分类维度的值）
 */
public final class StratifiedSampler<T, K> {

    private static final long SEED_MIXER = 0x9E3779B97F4A7C15L;

    private final Function<T, K> classifier;
    private final Function<K, Integer> capacityFor;
    private final long seed;
    private final Map<K, ReservoirSampler<T>> reservoirs = new LinkedHashMap<>();
    private long processed;

    /**
     * @param classifier    分层维度，例如 {@code User::tier}
     * @param capacityFor   层键到样本容量的映射，返回值必须为正数
     * @param seed          随机种子
     */
    public StratifiedSampler(Function<T, K> classifier, Function<K, Integer> capacityFor, long seed) {
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.capacityFor = Objects.requireNonNull(capacityFor, "capacityFor");
        this.seed = seed;
    }

    /**
     * 所有层使用相同容量的便捷构造。
     */
    public StratifiedSampler(Function<T, K> classifier, int capacityPerStratum, long seed) {
        this(classifier, k -> capacityPerStratum, seed);
        if (capacityPerStratum <= 0) {
            throw new IllegalArgumentException(
                    "capacityPerStratum must be positive, but was: " + capacityPerStratum);
        }
    }

    /**
     * 向数据流投喂一条数据，自动归入对应层。
     */
    public void offer(T item) {
        Objects.requireNonNull(item, "item");
        K stratum = Objects.requireNonNull(classifier.apply(item), "stratum key");
        reservoirs.computeIfAbsent(stratum, this::createReservoir).offer(item);
        processed++;
    }

    private ReservoirSampler<T> createReservoir(K stratum) {
        Integer capacity = capacityFor.apply(stratum);
        if (capacity == null || capacity <= 0) {
            throw new IllegalArgumentException(
                    "capacity for stratum [" + stratum + "] must be positive, but was: " + capacity);
        }
        return new ReservoirSampler<>(capacity, deriveSeed(seed, stratum));
    }

    private static long deriveSeed(long seed, Object stratum) {
        return seed ^ ((long) stratum.hashCode() * SEED_MIXER);
    }

    /**
     * 取所有层当前样本的防御性拷贝（含每层列表的拷贝）。
     */
    public Map<K, List<T>> snapshot() {
        Map<K, List<T>> result = new LinkedHashMap<>();
        reservoirs.forEach((k, sampler) -> result.put(k, sampler.snapshot()));
        return result;
    }

    /**
     * 取指定层当前样本的防御性拷贝；该层尚无数据时返回空列表。
     */
    public List<T> snapshot(K stratum) {
        Objects.requireNonNull(stratum, "stratum");
        ReservoirSampler<T> sampler = reservoirs.get(stratum);
        return sampler == null ? List.of() : sampler.snapshot();
    }

    public Set<K> strata() {
        return Set.copyOf(reservoirs.keySet());
    }

    public long processedCount() {
        return processed;
    }

    /**
     * 各层统计信息（已处理量、容量、样本数、选择概率说明），按层首次出现顺序排列。
     */
    public Map<K, SamplingStats> stats() {
        Map<K, SamplingStats> result = new LinkedHashMap<>();
        reservoirs.forEach((k, sampler) -> result.put(k, sampler.stats()));
        return result;
    }
}
