package com.example.gsb.sampling;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservoirSamplerTest {

    @Test
    void keepsFixedSizeSampleDrawnFromTheStream() {
        ReservoirSampler<Integer> sampler = new ReservoirSampler<>(10, 42L);

        for (int i = 0; i < 1000; i++) {
            sampler.offer(i);
            if (i >= 9) {
                assertThat(sampler.sampleSize()).as("sample size must never exceed capacity").isEqualTo(10);
            }
        }

        List<Integer> sample = sampler.snapshot();
        assertThat(sample).hasSize(10);
        assertThat(sample).allMatch(v -> v >= 0 && v < 1000);
        assertThat(sampler.processedCount()).isEqualTo(1000);
    }

    @Test
    void equalProbabilityAcrossPositions() {
        // 每个元素无论在流中的位置如何，都可能进入样本（小规模全枚举）
        ReservoirSampler<Integer> sampler = new ReservoirSampler<>(3, 7L);
        for (int i = 0; i < 100; i++) {
            sampler.offer(i);
        }
        // 前 k 个位置不是"永久保留"的：早期元素也必须可能被替换
        long earlyElementAppearances = 0;
        int runs = 2000;
        for (int run = 0; run < runs; run++) {
            ReservoirSampler<Integer> s = new ReservoirSampler<>(3, 1000L + run);
            for (int i = 0; i < 100; i++) {
                s.offer(i);
            }
            if (s.snapshot().contains(0)) {
                earlyElementAppearances++;
            }
        }
        // P(元素0入选) = 3/100 = 0.03，容许统计波动
        assertThat((double) earlyElementAppearances / runs).isBetween(0.015, 0.05);
    }

    @Test
    void reproducibleWithSameSeed() {
        List<Integer> first = runReservoir(10, 123L, 500);
        List<Integer> second = runReservoir(10, 123L, 500);
        List<Integer> third = runReservoir(10, 123L, 500);

        assertThat(second).isEqualTo(first);
        assertThat(third).isEqualTo(first);
    }

    @Test
    void differentSeedsProduceDifferentSamples() {
        List<Integer> a = runReservoir(10, 1L, 1000);
        List<Integer> b = runReservoir(10, 2L, 1000);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void snapshotIsIsolatedFromContinuedSamplingAndExternalMutation() {
        ReservoirSampler<Integer> sampler = new ReservoirSampler<>(5, 99L);
        for (int i = 0; i < 20; i++) {
            sampler.offer(i);
        }

        List<Integer> snapshot = sampler.snapshot();
        List<Integer> snapshotBackup = new ArrayList<>(snapshot);
        snapshot.set(0, -999);
        snapshot.clear();

        for (int i = 20; i < 200; i++) {
            sampler.offer(i);
        }

        List<Integer> later = sampler.snapshot();
        assertThat(later).hasSize(5);
        assertThat(later).allMatch(v -> v >= 0 && v < 200);
        // 后续抽样不改变先前拿到的快照
        assertThat(snapshotBackup).doesNotContain(-999);
        assertThat(snapshotBackup).allMatch(v -> v >= 0 && v < 20);
        // 外部修改不影响抽样器内部
        assertThat(sampler.snapshot()).hasSize(5);
    }

    @Test
    void statsReportProcessedCapacitySizeAndProbability() {
        ReservoirSampler<Integer> sampler = new ReservoirSampler<>(10, 0L);
        assertThat(sampler.stats().selectionProbability()).contains("N/A");

        for (int i = 0; i < 5; i++) {
            sampler.offer(i);
        }
        SamplingStats underfilled = sampler.stats();
        assertThat(underfilled.processedCount()).isEqualTo(5);
        assertThat(underfilled.sampleSize()).isEqualTo(5);
        assertThat(underfilled.selectionProbability()).contains("10/5");

        for (int i = 5; i < 100; i++) {
            sampler.offer(i);
        }
        SamplingStats full = sampler.stats();
        assertThat(full.processedCount()).isEqualTo(100);
        assertThat(full.capacity()).isEqualTo(10);
        assertThat(full.sampleSize()).isEqualTo(10);
        assertThat(full.selectionProbability()).contains("10/100").contains("0.100000");
        assertThat(full.toString()).contains("processed=100");
    }

    @Test
    void rejectsInvalidCapacityAndNullItem() {
        assertThatThrownBy(() -> new ReservoirSampler<>(0, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReservoirSampler<Integer>(1, 1L).offer(null))
                .isInstanceOf(NullPointerException.class);
    }

    private static List<Integer> runReservoir(int capacity, long seed, int n) {
        ReservoirSampler<Integer> sampler = new ReservoirSampler<>(capacity, seed);
        for (int i = 0; i < n; i++) {
            sampler.offer(i);
        }
        return sampler.snapshot();
    }
}
