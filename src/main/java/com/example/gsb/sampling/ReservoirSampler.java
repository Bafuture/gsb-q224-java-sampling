package com.example.gsb.sampling;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * 等概率蓄水池抽样（Reservoir Sampling，Algorithm R，Vitter 1985）。
 *
 * <p>算法原理：维护一个容量为 k 的“蓄水池”。数据流中第 i 条数据（从 1 开始计数）到达时：
 * <ul>
 *   <li>若 i &le; k：直接放入蓄水池（此时尚未满）；</li>
 *   <li>若 i &gt; k：以 k/i 的概率用新元素替换池中均匀随机选中的一个位置，
 *       以 1 - k/i 的概率丢弃新元素。</li>
 * </ul>
 *
 * <p>正确性（归纳法）：处理完 i 条数据后，任意一条已到达数据留在池中的概率都是 k/i。
 * 归纳步骤：第 i+1 条以 k/(i+1) 入池；池中任一旧元素被替换出去的概率为
 * (k/(i+1)) * (1/k) = 1/(i+1)，故其留存概率为 (k/i) * (1 - 1/(i+1)) = k/(i+1)。
 * 因此任意时刻每条已处理数据被选中的概率完全相等，且全程只需 O(k) 额外空间、
 * O(1) 的单条处理时间，无需事先知道总量 N。
 *
 * <p>可复现性：内部使用显式播种的 {@link Random}，相同种子 + 相同输入序列
 * 必然产生完全相同的样本。
 */
public final class ReservoirSampler<T> {

    private final int capacity;
    private final Random random;
    private final ArrayList<T> reservoir;
    private long processed;

    public ReservoirSampler(int capacity, long seed) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, got " + capacity);
        }
        this.capacity = capacity;
        this.random = new Random(Seeds.mix64(seed));
        this.reservoir = new ArrayList<>(capacity);
    }

    /** 处理数据流中的一条新数据。 */
    public void offer(T item) {
        Objects.requireNonNull(item, "item must not be null");
        processed++;
        if (reservoir.size() < capacity) {
            reservoir.add(item);
        } else {
            long j = random.nextLong(processed);
            if (j < capacity) {
                reservoir.set((int) j, item);
            }
        }
    }

    /**
     * 返回当前样本的独立快照（不可变副本）。
     * 修改后续数据流不会影响已返回的快照，反之亦然。
     */
    public List<T> snapshot() {
        return List.copyOf(reservoir);
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
        return reservoir.size();
    }

    /**
     * 已处理的任意一条数据当前留在样本中的概率：min(1, k/n)，
     * 其中 n 为已处理总量。尚未处理任何数据时返回 0。
     */
    public double selectionProbability() {
        return processed == 0 ? 0.0 : Math.min(1.0, (double) capacity / processed);
    }
}
