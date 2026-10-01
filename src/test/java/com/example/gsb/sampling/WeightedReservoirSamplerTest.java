package com.example.gsb.sampling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class WeightedReservoirSamplerTest {

    @Test
    void selectionProbabilityFollowsWeightRatio() {
        int trials = 60_000;
        long highChosen = 0;
        for (int t = 0; t < trials; t++) {
            WeightedReservoirSampler<String> sampler = new WeightedReservoirSampler<>(1, 777L + t);
            sampler.offer("low", 1.0);
            sampler.offer("high", 3.0);
            if ("high".equals(sampler.snapshot().get(0))) {
                highChosen++;
            }
        }
        double ratio = (double) highChosen / trials;
        System.out.printf("weighted ratio check: high(w=3) chosen %.3f%% of %d trials (expected 75%%)%n",
                100.0 * ratio, trials);
        assertThat(ratio).isBetween(0.73, 0.77);
    }

    @Test
    void heavierItemDominatesLargerReservoir() {
        int trials = 20_000;
        long[] hits = new long[3];
        double[] weights = {1.0, 2.0, 4.0};
        for (int t = 0; t < trials; t++) {
            WeightedReservoirSampler<Integer> sampler = new WeightedReservoirSampler<>(2, 4242L + t);
            for (int i = 0; i < 3; i++) {
                sampler.offer(i, weights[i]);
            }
            for (Integer chosen : sampler.snapshot()) {
                hits[chosen]++;
            }
        }
        System.out.printf("weighted k=2 hits over %d trials: w=1 -> %d, w=2 -> %d, w=4 -> %d%n",
                trials, hits[0], hits[1], hits[2]);
        assertThat(hits[2]).isGreaterThan(hits[1]);
        assertThat(hits[1]).isGreaterThan(hits[0]);
    }

    @Test
    void rejectsNonPositiveOrNonFiniteWeights() {
        WeightedReservoirSampler<String> sampler = new WeightedReservoirSampler<>(2, 1L);
        assertThatThrownBy(() -> sampler.offer("a", 0.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sampler.offer("a", -1.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sampler.offer("a", Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sampler.offer("a", Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameSeedProducesIdenticalResults() {
        WeightedReservoirSampler<Integer> first = new WeightedReservoirSampler<>(5, 31415L);
        WeightedReservoirSampler<Integer> second = new WeightedReservoirSampler<>(5, 31415L);
        for (int i = 0; i < 500; i++) {
            double weight = 1.0 + (i % 7);
            first.offer(i, weight);
            second.offer(i, weight);
        }
        assertThat(first.snapshot()).containsExactlyInAnyOrderElementsOf(second.snapshot());
        assertThat(first.processedCount()).isEqualTo(500);
        assertThat(first.totalWeight()).isEqualTo(second.totalWeight());
    }

    @Test
    void snapshotIsIsolatedFromLaterOffers() {
        WeightedReservoirSampler<Integer> sampler = new WeightedReservoirSampler<>(3, 9L);
        for (int i = 0; i < 10; i++) {
            sampler.offer(i, 1.0);
        }
        List<Integer> snapshot = sampler.snapshot();
        for (int i = 10; i < 1_000; i++) {
            sampler.offer(i, 1.0);
        }
        assertThat(snapshot).hasSize(3).allMatch(i -> i < 10);
        assertThat(sampler.processedCount()).isEqualTo(1_000);
    }

    @Test
    void expectedSelectionProbabilityIsProportionalToWeight() {
        WeightedReservoirSampler<String> sampler = new WeightedReservoirSampler<>(1, 5L);
        sampler.offer("a", 1.0);
        sampler.offer("b", 3.0);
        assertThat(sampler.expectedSelectionProbability(3.0)).isEqualTo(0.75);
        assertThat(sampler.expectedSelectionProbability(1.0)).isEqualTo(0.25);
    }
}
