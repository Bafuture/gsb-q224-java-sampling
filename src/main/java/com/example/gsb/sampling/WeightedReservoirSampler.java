package com.example.gsb.sampling;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * 带权蓄水池抽样（A-Res 算法，Efraimidis &amp; Spirakis 2005）。
 *
 * <p>算法原理：每条权重为 w &gt; 0 的数据到达时，取 u ~ Uniform(0,1)，
 * 计算键值 key = u^(1/w)。维护一个容量为 k 的最小堆，始终保留 key 最大的
 * k 条数据：池未满时直接入堆；池满时若新 key 大于堆顶最小 key，则弹出堆顶、
 * 插入新元素，否则丢弃。
 *
 * <p>直观解释：u^(1/w) 的分布函数为 P(key &le; x) = x^w，权重越大，key 越倾向
 * 于接近 1，因此最终留在 top-k 中的概率随权重单调上升。可以证明该算法等价于
 * “按权重做不放回抽样”，即每条数据的入选概率（近似）与其权重成正比；
 * 特别地，当 k = 1 时，元素 i 被选中的概率恰好为 w_i / Σw。
 *
 * <p>可复现性：内部使用显式播种的 {@link Random}，相同种子 + 相同输入序列
 * 必然产生完全相同的样本。
 */
public final class WeightedReservoirSampler<T> {

    private record Entry<T>(T item, double key) {}

    private final int capacity;
    private final Random random;
    private final PriorityQueue<Entry<T>> heap;
    private long processed;
    private double totalWeight;

    public WeightedReservoirSampler(int capacity, long seed) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, got " + capacity);
        }
        this.capacity = capacity;
        this.random = new Random(Seeds.mix64(seed));
        this.heap = new PriorityQueue<>(capacity, Comparator.comparingDouble(Entry::key));
    }

    /**
     * 处理一条带权数据。
     *
     * @param weight 必须为正有限数；权重越大，被选中概率越高
     */
    public void offer(T item, double weight) {
        Objects.requireNonNull(item, "item must not be null");
        if (!(weight > 0.0) || Double.isNaN(weight) || Double.isInfinite(weight)) {
            throw new IllegalArgumentException("weight must be a positive finite number, got " + weight);
        }
        processed++;
        totalWeight += weight;
        double key = Math.pow(random.nextDouble(), 1.0 / weight);
        if (heap.size() < capacity) {
            heap.add(new Entry<>(item, key));
        } else if (key > heap.peek().key()) {
            heap.poll();
            heap.add(new Entry<>(item, key));
        }
    }

    /** 返回当前样本的独立快照（不可变副本），不影响后续抽样。 */
    public List<T> snapshot() {
        return heap.stream().map(Entry::item).toList();
    }

    /** 已处理的数据总量。 */
    public long processedCount() {
        return processed;
    }

    /** 样本容量 k。 */
    public int capacity() {
        return capacity;
    }

    /** 当前池中样本数（未满时小于容量）。 */
    public int size() {
        return heap.size();
    }

    /** 已处理数据的累计权重。 */
    public double totalWeight() {
        return totalWeight;
    }

    /**
     * 一条权重为 {@code weight} 的数据的期望入选概率上界估计：
     * min(1, k * weight / Σw)。k = 1 时该值是精确概率；
     * k &gt; 1 时为常用的近似（不放回抽样下精确值无闭式解）。
     */
    public double expectedSelectionProbability(double weight) {
        if (totalWeight <= 0.0) {
            return 0.0;
        }
        return Math.min(1.0, capacity * weight / totalWeight);
    }
}
