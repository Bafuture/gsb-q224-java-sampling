package com.example.gsb.sampling;

/**
 * 抽样统计信息（不可变快照）。
 *
 * @param processedCount       已处理的数据总量 N
 * @param capacity             样本容量（蓄水池大小）k
 * @param sampleSize           当前样本实际大小（N &lt; k 时等于 N）
 * @param selectionProbability 选择概率说明（人可读的概率描述）
 */
public record SamplingStats(long processedCount, int capacity, int sampleSize,
                            String selectionProbability) {

    @Override
    public String toString() {
        return "SamplingStats{processed=" + processedCount
                + ", capacity=" + capacity
                + ", sampleSize=" + sampleSize
                + ", selectionProbability=" + selectionProbability + '}';
    }
}
