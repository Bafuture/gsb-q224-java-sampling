package com.example.gsb.sampling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * 带权蓄水池抽样（Efraimidis &amp; Spirakis 的 Algorithm A-Res）。
 *
 * <h2>算法原理</h2>
 * <p>每条数据到达时，根据其权重 w 计算一个随机键：
 * <pre>key = u^(1/w)，其中 u ~ Uniform(0, 1]</pre>
 * 蓄水池始终保留当前所有数据中 key 最大的 k 条（用最小堆维护，
 * 堆满后新数据的 key 大于堆顶才替换，均摊 O(log k)）。
 *
 * <p>直觉解释：对 u^(1/w) 取对数得 ln(u)/w，即把标准指数分布随机变量
 * 按 1/w 缩放——权重越大的元素键值分布越靠大端，因此越容易被保留。
 * 数学上可证明：k = 1 时元素 i 被选中的概率恰为
 * {@code w_i / Σw}；k &gt; 1 时按 key 取前 k 名等价于不放回的
 * 逐次按权重比例抽取，是带权蓄水池抽样的标准算法。
 *
 * <p>所有随机性来自构造时传入的随机种子，相同种子 + 相同输入序列
 * 必然产生完全相同的样本。
 *
 * @param <T> 数据元素类型
 */
public final class WeightedReservoirSampler<T> {

    private record Entry<T>(T item, double key) {
    }

    private final int capacity;
    private final Random random;
    private final PriorityQueue<Entry<T>> heap;
    private long processed;

    /**
     * @param capacity 蓄水池容量 k，必须为正数
     * @param seed     随机种子，相同种子产生完全可复现的抽样结果
     */
    public WeightedReservoirSampler(int capacity, long seed) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, but was: " + capacity);
        }
        this.capacity = capacity;
        this.random = new Random(seed);
        this.heap = new PriorityQueue<>(capacity, Comparator.comparingDouble(Entry::key));
    }

    /**
     * 向数据流投喂一条带权数据。
     *
     * @param item   数据元素
     * @param weight 权重，必须为正的有限值；权重越大被选中概率越高
     */
    public void offer(T item, double weight) {
        Objects.requireNonNull(item, "item");
        if (!(weight > 0.0) || Double.isNaN(weight) || Double.isInfinite(weight)) {
            throw new IllegalArgumentException("weight must be a positive finite value, but was: " + weight);
        }
        double u = 1.0 - random.nextDouble();
        double key = Math.pow(u, 1.0 / weight);
        if (heap.size() < capacity) {
            heap.add(new Entry<>(item, key));
        } else if (key > heap.peek().key()) {
            heap.poll();
            heap.add(new Entry<>(item, key));
        }
        processed++;
    }

    /**
     * 取当前样本的防御性拷贝（按 key 从大到小排序），
     * 修改返回列表不影响抽样器内部状态与后续抽样。
     */
    public List<T> snapshot() {
        List<Entry<T>> entries = new ArrayList<>(heap);
        entries.sort(Comparator.comparingDouble(Entry<T>::key).reversed());
        List<T> result = new ArrayList<>(entries.size());
        for (Entry<T> entry : entries) {
            result.add(entry.item());
        }
        return result;
    }

    public int sampleSize() {
        return heap.size();
    }

    public int capacity() {
        return capacity;
    }

    public long processedCount() {
        return processed;
    }

    public SamplingStats stats() {
        String explanation = processed == 0
                ? "N/A (no data processed yet)"
                : String.format("weighted A-Res: key = u^(1/w), keep top-%d keys; "
                        + "for k=1, P(item i selected) = w_i / sum(w)", capacity);
        return new SamplingStats(processed, capacity, heap.size(), explanation);
    }
}
