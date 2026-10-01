package com.example.gsb.sampling;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * 等概率蓄水池抽样（Reservoir Sampling，Vitter 的 Algorithm R）。
 *
 * <h2>算法原理</h2>
 * <ol>
 *   <li>前 k 条数据（k = 蓄水池容量）直接放入蓄水池。</li>
 *   <li>处理第 i 条数据时（i 从 0 开始，且 i &ge; k），以 {@code k/(i+1)} 的概率决定
 *       是否接纳该数据：在 [0, i] 范围内均匀取一个随机整数 j，若 j &lt; k，
 *       则用新数据替换蓄水池第 j 个位置的元素，否则丢弃。</li>
 * </ol>
 *
 * <h2>等概率性证明（归纳法）</h2>
 * <p>处理完第 i 条数据后，假设蓄水池中每个元素都是前 i+1 条数据中等概率选出的，
 * 即每条历史数据留在蓄水池中的概率为 {@code k/(i+1)}。处理第 i+1 条数据时：
 * <ul>
 *   <li>新数据进入蓄水池的概率为 {@code k/(i+2)}；</li>
 *   <li>蓄水池中某个旧元素存活的概率 =
 *       {@code (i+2-k)/(i+2) + k/(i+2) · (k-1)/k}
 *       （不被替换，或发生替换但替换的不是自己）= {@code (i+1)/(i+2)}，
 *       故旧元素最终留在蓄水池中的概率为
 *       {@code k/(i+1) · (i+1)/(i+2) = k/(i+2)}。</li>
 * </ul>
 * 归纳可知：处理完任意 N 条数据后，每条数据出现在样本中的概率都是
 * {@code min(1, k/N)}，与到达顺序无关，且全程只需 O(k) 内存、单次 O(1) 均摊开销，
 * 不需要事先知道数据总量。
 *
 * <p>所有随机性来自构造时传入的随机种子，相同种子 + 相同输入序列
 * 必然产生完全相同的样本。
 *
 * @param <T> 数据元素类型
 */
public final class ReservoirSampler<T> {

    private final int capacity;
    private final Random random;
    private final ArrayList<T> reservoir;
    private long processed;

    /**
     * @param capacity 蓄水池容量 k，必须为正数
     * @param seed     随机种子，相同种子产生完全可复现的抽样结果
     */
    public ReservoirSampler(int capacity, long seed) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, but was: " + capacity);
        }
        this.capacity = capacity;
        this.random = new Random(seed);
        this.reservoir = new ArrayList<>(capacity);
    }

    /**
     * 向数据流投喂一条数据（Algorithm R）。
     */
    public void offer(T item) {
        Objects.requireNonNull(item, "item");
        if (reservoir.size() < capacity) {
            reservoir.add(item);
        } else {
            long j = random.nextLong(processed + 1);
            if (j < capacity) {
                reservoir.set((int) j, item);
            }
        }
        processed++;
    }

    /**
     * 取当前样本的防御性拷贝，修改返回列表不影响抽样器内部状态，
     * 也不消耗后续投喂所需的任何随机数。
     */
    public List<T> snapshot() {
        return new ArrayList<>(reservoir);
    }

    public int sampleSize() {
        return reservoir.size();
    }

    public int capacity() {
        return capacity;
    }

    public long processedCount() {
        return processed;
    }

    /**
     * 每条已处理数据当前出现在样本中的概率：{@code min(1, k/N)}。
     */
    public double selectionProbability() {
        return processed == 0 ? 0.0 : Math.min(1.0, (double) capacity / processed);
    }

    public SamplingStats stats() {
        String explanation;
        if (processed == 0) {
            explanation = "N/A (no data processed yet)";
        } else {
            explanation = String.format("min(1, %d/%d) = %.6f (every item is selected with equal probability)",
                    capacity, processed, selectionProbability());
        }
        return new SamplingStats(processed, capacity, reservoir.size(), explanation);
    }
}
