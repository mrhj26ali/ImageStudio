package edu.pp.imgstudio.engine;

import edu.pp.imgstudio.core.Filter;
import edu.pp.imgstudio.sync.ProgressTracker;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

/**
 * Parallel image processing engine using a fixed thread pool.
 * Implements Data Parallelism by splitting each image into row bands.
 */
public class ParallelEngine {
    
    private static int getChunkSize(int nChunks, int nElements) {
        return (nElements + nChunks - 1) / nChunks;
    }

    private static int getChunkStart(int chunk, int nChunks, int nElements) {
        return chunk * getChunkSize(nChunks, nElements);
    }

    private static int getChunkEnd(int chunk, int nChunks, int nElements) {
        int end = (chunk + 1) * getChunkSize(nChunks, nElements);
        return Math.min(end, nElements);
    }

    /**
     * Processes a batch of images in parallel.
     * @return List of files that failed processing
     */
    public static ProcessingResult processBatch(List<File> inputFiles, File outputDir, Filter filter, 
                                                  int numThreads, ProgressTracker tracker) throws Exception {
        if (!outputDir.exists()) outputDir.mkdirs();

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        Semaphore memoryLimiter = new Semaphore(Math.min(5, numThreads));
        List<Future<File>> futures = new ArrayList<>();
        List<File> failedFiles = new ArrayList<>();

        long startTime = System.nanoTime();

        try {
            for (File inputFile : inputFiles) {
                futures.add(executor.submit(() -> {
                    memoryLimiter.acquire();
                    try {
                        BufferedImage source = ImageIO.read(inputFile);
                        if (source == null) {
                            System.err.println("Warning: Could not read " + inputFile.getName() + ", skipping.");
                            tracker.increment();
                            return inputFile;
                        }

                        BufferedImage destination = new BufferedImage(
                            source.getWidth(),
                            source.getHeight(),
                            BufferedImage.TYPE_INT_RGB
                        );

                        int height = source.getHeight();
                        List<Thread> workers = new ArrayList<>();
                        
                        for (int i = 0; i < numThreads; i++) {
                            int startRow = getChunkStart(i, numThreads, height);
                            int endRow = getChunkEnd(i, numThreads, height);
                            
                            if (startRow >= endRow) continue;
                            
                            Thread worker = new Thread(() -> {
                                filter.apply(source, destination, startRow, endRow);
                            });
                            workers.add(worker);
                        }

                        for (Thread worker : workers) worker.start();
                        for (Thread worker : workers) worker.join();

                        String outputName = ImageUtils.getOutputFilename(inputFile.getName());
                        ImageIO.write(destination, "jpg", new File(outputDir, outputName));
                        
                        tracker.increment();
                        return null;

                    } catch (Exception e) {
                        System.err.println("Error processing: " + inputFile.getName() + " - " + e.getMessage());
                        tracker.increment();
                        return inputFile;
                    } finally {
                        memoryLimiter.release();
                    }
                }));
            }

            for (Future<File> future : futures) {
                File failed = future.get();
                if (failed != null) {
                    failedFiles.add(failed);
                }
            }
        } finally {
            executor.shutdown();
        }

        long elapsed = (System.nanoTime() - startTime) / 1_000_000;
        return new ProcessingResult(elapsed, failedFiles);
    }

    /**
     * Result of a batch processing operation.
     */
    public static class ProcessingResult {
        public final long timeMs;
        public final List<File> failedFiles;

        public ProcessingResult(long timeMs, List<File> failedFiles) {
            this.timeMs = timeMs;
            this.failedFiles = failedFiles;
        }
    }
}