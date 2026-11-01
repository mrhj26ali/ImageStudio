package edu.pp.imgstudio.benchmark;

import edu.pp.imgstudio.core.Filter;
import edu.pp.imgstudio.core.GrayscaleFilter;
import edu.pp.imgstudio.core.GaussianBlurFilter;
import edu.pp.imgstudio.core.SobelFilter;
import edu.pp.imgstudio.engine.ParallelEngine;
import edu.pp.imgstudio.engine.SequentialEngine;
import edu.pp.imgstudio.sync.ProgressTracker;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Advanced benchmark runner that generates comprehensive performance data
 * for the technical report. Includes warm-up, multiple repetitions,
 * median/std-dev statistics, and machine info.
 */
public class BenchmarkRunner {

    private static final int WARMUP_RUNS = 1;
    private static final int MEASURED_RUNS = 5;
    private static final int[] THREAD_COUNTS = {1, 2, 4, 8, 16};

    public static void main(String[] args) throws Exception {
        File inputDir = new File("dataset/input");
        File outputDir = new File("dataset/output");

        List<File> files = discoverImages(inputDir);
        if (files.size() < 5) {
            System.out.println("ERROR: Please add at least 5 images to dataset/input/");
            return;
        }

        System.out.println("==============================================");
        System.out.println("  Parallel Image Studio - Benchmark Runner");
        System.out.println("==============================================");
        printMachineInfo();
        System.out.println("Images to process: " + files.size());
        System.out.println("Warm-up runs: " + WARMUP_RUNS);
        System.out.println("Measured runs per configuration: " + MEASURED_RUNS);
        System.out.println("==============================================\n");

        // Warm-up pass (JIT compilation)
        System.out.println("Warming up JVM...");
        for (int i = 0; i < WARMUP_RUNS; i++) {
            SequentialEngine.processBatch(files, outputDir, new SobelFilter(), new ProgressTracker(files.size()));
            ParallelEngine.processBatch(files, outputDir, new SobelFilter(), 4, new ProgressTracker(files.size()));
        }
        System.out.println("Warm-up complete.\n");

        Filter[] filters = {
            new GrayscaleFilter(),
            new GaussianBlurFilter(),
            new SobelFilter()
        };
        String[] filterNames = {"Grayscale", "GaussianBlur", "Sobel"};

        try (PrintWriter rawWriter = new PrintWriter(new FileWriter("benchmark_raw.csv"));
             PrintWriter summaryWriter = new PrintWriter(new FileWriter("benchmark_summary.csv"))) {

            rawWriter.println("Filter,Mode,Threads,Run,Time_ms");
            summaryWriter.println("Filter,Mode,Threads,Median_ms,StdDev_ms,Speedup,Efficiency");

            for (int f = 0; f < filters.length; f++) {
                System.out.println("\n>>> Benchmarking filter: " + filterNames[f]);

                // Sequential baseline (5 runs)
                long[] seqTimes = new long[MEASURED_RUNS];
                for (int r = 0; r < MEASURED_RUNS; r++) {
                    seqTimes[r] = SequentialEngine.processBatch(files, outputDir, filters[f], new ProgressTracker(files.size())).timeMs;
                    rawWriter.printf("%s,Sequential,1,%d,%d%n", filterNames[f], r + 1, seqTimes[r]);
                    System.out.printf("  Sequential run %d: %d ms%n", r + 1, seqTimes[r]);
                }
                double seqMedian = median(seqTimes);
                double seqStdDev = stdDev(seqTimes, seqMedian);
                summaryWriter.printf("%s,Sequential,1,%.2f,%.2f,1.00,1.00%n",
                    filterNames[f], seqMedian, seqStdDev);

                // Parallel runs for each N
                for (int n : THREAD_COUNTS) {
                    if (n == 1) continue; // Skip N=1 (already measured as sequential)
                    long[] parTimes = new long[MEASURED_RUNS];
                    for (int r = 0; r < MEASURED_RUNS; r++) {
                        parTimes[r] = ParallelEngine.processBatch(files, outputDir, filters[f], n, new ProgressTracker(files.size())).timeMs;
                        rawWriter.printf("%s,Parallel,%d,%d,%d%n", filterNames[f], n, r + 1, parTimes[r]);
                    }
                    double parMedian = median(parTimes);
                    double parStdDev = stdDev(parTimes, parMedian);
                    double speedup = seqMedian / parMedian;
                    double efficiency = speedup / n;

                    summaryWriter.printf("%s,Parallel,%d,%.2f,%.2f,%.2f,%.4f%n",
                        filterNames[f], n, parMedian, parStdDev, speedup, efficiency);

                    System.out.printf("  N=%2d | Median: %7.0f ms | StdDev: %6.0f ms | Speedup: %.2fx | Efficiency: %.1f%%%n",
                        n, parMedian, parStdDev, speedup, efficiency * 100);
                }
            }
        }

        System.out.println("\n==============================================");
        System.out.println("Benchmark complete!");
        System.out.println("Raw data:       benchmark_raw.csv");
        System.out.println("Summary data:   benchmark_summary.csv");
        System.out.println("==============================================");
    }

    private static List<File> discoverImages(File dir) {
        List<File> result = new ArrayList<>();
        if (!dir.exists() || !dir.isDirectory()) return result;
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
        });
        if (files != null) result.addAll(Arrays.asList(files));
        return result;
    }

    private static double median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[mid - 1] + sorted[mid]) / 2.0 : sorted[mid];
    }

    private static double stdDev(long[] values, double mean) {
        double sum = 0;
        for (long v : values) {
            double diff = v - mean;
            sum += diff * diff;
        }
        return Math.sqrt(sum / values.length);
    }

    private static void printMachineInfo() {
        System.out.println("--- Machine Info ---");
        System.out.println("OS:            " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println("Architecture:  " + System.getProperty("os.arch"));
        System.out.println("Java Vendor:   " + System.getProperty("java.vendor"));
        System.out.println("Java Version:  " + System.getProperty("java.version"));
        System.out.println("Available CPUs: " + Runtime.getRuntime().availableProcessors());
        long maxMem = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        long totalMem = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        System.out.println("JVM Max Heap:  " + maxMem + " MB");
        System.out.println("JVM Total Heap: " + totalMem + " MB");
        System.out.println("--------------------");
    }
}