package com.example.gsb.sampling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ReservoirSamplerTest {

    @Test
    void keepsAllItemsUntilCapacityIsReached() {
        ReservoirSampler<Integer> sampler = new ReservoirSampler<>(5, 1L);
        IntStream.rangeClosed(1, 5).forEach(sampler::offer);

        assertThat(sampler.snapshot()).containsExactly(1, 2, 3, 4, 5);
        assertThat(sampler.size()).isEqualTo(5);
        assertThat(sampler.processedCount()).isEqualTo(5);
        assertThat(sampler.selectionProbability()).isEqualTo(1.0);
    }

    @Test
    void sampleNeverExceedsCapacityAndContainsStreamItems() {
        ReservoirSampler<Integer> sampler = new ReservoirSampler<>(10, 7L);
        IntStream.range(0, 10_000).forEach(sampler::offer);

        List<Integer> sample = sampler.snapshot();
        assertThat(sample).hasSize(10);
        assertThat(sample).allMatch(i -> i >= 0 && i < 10_000);
        assertThat(sampler.processedCount()).isEqualTo(10_000);
        assertThat(sampler.selectionProbability()).isEqualTo(10.0 / 10_000);
    }

    @Test
    void rejectsInvalidCapacityAndNullItems() {
        assertThatThrownBy(() -> new ReservoirSampler<>(0, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReservoirSampler<Integer>(1, 1L).offer(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void snapshotIsIsolatedFromLaterOffers() {
        ReservoirSampler<Integer> sampler = new ReservoirSampler<>(3, 99L);
        IntStream.rangeClosed(1, 10).forEach(sampler::offer);
        List<Integer> snapshot = sampler.snapshot();

        IntStream.rangeClosed(11, 1_000).forEach(sampler::offer);

        assertThat(snapshot).hasSize(3);
        assertThat(snapshot).allMatch(i -> i <= 10);
        assertThatThrownBy(() -> snapshot.add(0)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void sameSeedProducesIdenticalResults() {
        ReservoirSampler<Integer> first = new ReservoirSampler<>(8, 12345L);
        ReservoirSampler<Integer> second = new ReservoirSampler<>(8, 12345L);
        ReservoirSampler<Integer> different = new ReservoirSampler<>(8, 54321L);

        IntStream.range(0, 1_000).forEach(i -> {
            first.offer(i);
            second.offer(i);
            different.offer(i);
        });

        assertThat(first.snapshot()).isEqualTo(second.snapshot());
        assertThat(first.snapshot()).isNotEqualTo(different.snapshot());
    }
}
