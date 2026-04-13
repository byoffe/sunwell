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
public class MemoryHogBenchmark {

    private List<Integer> values;
    private MemoryHog memoryHog;

    @Setup(Level.Trial)
    public void setup() {
        memoryHog = new MemoryHog();

        Random rng = new Random(42);
        values = new ArrayList<>(500);
        for (int i = 0; i < 500; i++) {
            values.add(rng.nextInt(10_000));
        }
    }

    @Benchmark
    public String buildReport() {
        return memoryHog.buildReport(values);
    }
}
