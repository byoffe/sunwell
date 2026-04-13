package dev.sunwell.toy;

import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CpuHogBenchmark {

    private List<String> tags;
    private CpuHog cpuHog;

    @Setup(Level.Trial)
    public void setup() {
        cpuHog = new CpuHog();

        // Pool has 10 distinct base tags; with 200 draws there are ~20% duplicates on average.
        // Some tags include non-alphanumeric characters to exercise the regex path.
        String[] pool = {
            "Alpha", "beta", "GAMMA", "delta!", "Epsilon",
            "zeta#1", "eta", "Theta-X", "iota", "kappa_2"
        };

        Random rng = new Random(42);
        tags = new ArrayList<>(200);
        for (int i = 0; i < 200; i++) {
            tags.add(pool[rng.nextInt(pool.length)]);
        }
    }

    @Benchmark
    public List<String> deduplicateTags() {
        return cpuHog.deduplicateTags(tags);
    }
}
