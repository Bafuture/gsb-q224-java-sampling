package com.example.gsb.sampling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class StratifiedSamplerTest {

    private static StratifiedSampler<Integer, String> paritySampler(long seed) {
        return new StratifiedSampler<Integer, String>(i -> i % 2 == 0 ? "even" : "odd", 2, seed)
                .withCapacity("odd", 5);
    }

    @Test
    void strataAreIsolatedAndKeepTheirOwnItems() {
        StratifiedSampler<Integer, String> sampler = paritySampler(11L);
        IntStream.rangeClosed(1, 100).forEach(sampler::offer);

        List<Integer> evens = sampler.snapshot("even");
        List<Integer> odds = sampler.snapshot("odd");

        assertThat(evens).hasSize(2).allMatch(i -> i % 2 == 0);
        assertThat(odds).hasSize(5).allMatch(i -> i % 2 != 0);
        assertThat(sampler.snapshotAll()).containsOnlyKeys("even", "odd");
        assertThat(sampler.snapshot("missing")).isEmpty();
    }

    @Test
    void perStratumCapacityIsConfigurable() {
        StratifiedSampler<Integer, String> sampler = paritySampler(3L);
        IntStream.rangeClosed(1, 50).forEach(sampler::offer);

        StratifiedSampler.Stats<String> stats = sampler.stats();
        assertThat(stats.strata().get("even").capacity()).isEqualTo(2);
        assertThat(stats.strata().get("odd").capacity()).isEqualTo(5);
        assertThat(stats.totalCapacity()).isEqualTo(7);
    }

    @Test
    void capacityCannotBeChangedAfterStratumSeen() {
        StratifiedSampler<Integer, String> sampler = paritySampler(3L);
        sampler.offer(2);
        assertThatThrownBy(() -> sampler.withCapacity("even", 9))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void statsReportTotalsAndProbabilityDescriptions() {
        StratifiedSampler<Integer, String> sampler = paritySampler(17L);
        IntStream.rangeClosed(1, 20).forEach(sampler::offer);

        StratifiedSampler.Stats<String> stats = sampler.stats();
        assertThat(stats.totalProcessed()).isEqualTo(20);
        assertThat(stats.strata().get("even").processed()).isEqualTo(10);
        assertThat(stats.strata().get("even").sampleSize()).isEqualTo(2);
        assertThat(stats.strata().get("even").selectionProbability())
                .contains("min(1, k/n)").contains("0.2");
        assertThat(stats.strata().get("odd").selectionProbability())
                .contains("0.5");
    }

    @Test
    void sameSeedProducesIdenticalStratifiedResults() {
        StratifiedSampler<Integer, String> first = paritySampler(2024L);
        StratifiedSampler<Integer, String> second = paritySampler(2024L);
        IntStream.rangeClosed(1, 200).forEach(i -> {
            first.offer(i);
            second.offer(i);
        });
        assertThat(first.snapshotAll()).isEqualTo(second.snapshotAll());
    }

    @Test
    void snapshotIsIsolatedFromLaterOffers() {
        StratifiedSampler<Integer, String> sampler = paritySampler(5L);
        IntStream.rangeClosed(1, 20).forEach(sampler::offer);
        Map<String, List<Integer>> snapshot = sampler.snapshotAll();

        IntStream.rangeClosed(21, 2_000).forEach(sampler::offer);

        assertThat(snapshot.get("even")).hasSize(2).allMatch(i -> i <= 20);
        assertThat(snapshot.get("odd")).hasSize(5).allMatch(i -> i <= 20);
        assertThat(sampler.totalProcessed()).isEqualTo(2_000);
    }

    @Test
    void weightedStrataRespectWeightRatioWithinEachStratum() {
        long heavyInA = 0;
        int trials = 20_000;
        for (int t = 0; t < trials; t++) {
            StratifiedSampler<String, String> run = new StratifiedSampler<>(
                    s -> s.startsWith("a") ? "A" : "B",
                    s -> s.endsWith("!") ? 4.0 : 1.0,
                    1,
                    88L + t);
            run.offer("a-light");
            run.offer("a-heavy!");
            run.offer("b-only");
            if ("a-heavy!".equals(run.snapshot("A").get(0))) {
                heavyInA++;
            }
            assertThat(run.snapshot("B")).containsExactly("b-only");
        }
        double ratio = (double) heavyInA / trials;
        System.out.printf("stratified weighted check: heavy item in stratum A chosen %.3f%% (expected 80%%)%n",
                100.0 * ratio);
        assertThat(ratio).isBetween(0.77, 0.83);
    }
}
