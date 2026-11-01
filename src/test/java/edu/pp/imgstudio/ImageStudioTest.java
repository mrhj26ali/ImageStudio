package edu.pp.imgstudio;

import edu.pp.imgstudio.core.Filter;
import edu.pp.imgstudio.core.GrayscaleFilter;
import edu.pp.imgstudio.core.GaussianBlurFilter;
import edu.pp.imgstudio.core.SobelFilter;
import edu.pp.imgstudio.engine.ParallelEngine;
import edu.pp.imgstudio.engine.SequentialEngine;
import edu.pp.imgstudio.sync.ProgressTracker;
import junit.framework.TestCase;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * JUnit tests verifying correctness, thread safety, and error handling.
 */
public class ImageStudioTest extends TestCase {

    private static final int TEST_WIDTH = 200;
    private static final int TEST_HEIGHT = 150;

    /**
     * Creates a deterministic test image with gradient + noise pattern.
     */
    private BufferedImage createTestImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (x * 255 / width);
                int g = (y * 255 / height);
                int b = (x + y) % 256;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    /**
     * Compares two images pixel-by-pixel.
     */
    private void assertImagesEqual(BufferedImage expected, BufferedImage actual, String message) {
        assertEquals(message + " (width)", expected.getWidth(), actual.getWidth());
        assertEquals(message + " (height)", expected.getHeight(), actual.getHeight());
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(message + " at (" + x + "," + y + ")",
                    expected.getRGB(x, y), actual.getRGB(x, y));
            }
        }
    }

    /**
     * Test: Parallel output equals sequential output pixel-for-pixel,
     * for all 3 filters and N = 1, 2, 4, 8, 16.
     */
    public void testParallelEqualsSequential() {
        Filter[] filters = {
            new GrayscaleFilter(),
            new GaussianBlurFilter(),
            new SobelFilter()
        };
        String[] names = {"Grayscale", "GaussianBlur", "Sobel"};
        int[] threadCounts = {1, 2, 4, 8, 16};

        for (int f = 0; f < filters.length; f++) {
            for (int n : threadCounts) {
                BufferedImage source = createTestImage(TEST_WIDTH, TEST_HEIGHT);

                BufferedImage seqDest = new BufferedImage(TEST_WIDTH, TEST_HEIGHT, BufferedImage.TYPE_INT_RGB);
                filters[f].apply(source, seqDest, 0, TEST_HEIGHT);

                BufferedImage parDest = new BufferedImage(TEST_WIDTH, TEST_HEIGHT, BufferedImage.TYPE_INT_RGB);
                List<Thread> workers = new java.util.ArrayList<>();
                int chunkSize = (TEST_HEIGHT + n - 1) / n;
                for (int i = 0; i < n; i++) {
                    int start = i * chunkSize;
                    int end = Math.min(start + chunkSize, TEST_HEIGHT);
                    if (start >= end) continue;
                    final int s = start, e = end;
                    workers.add(new Thread(() -> filters[f].apply(source, parDest, s, e)));
                }
                for (Thread t : workers) t.start();
                for (Thread t : workers) {
                    try { t.join(); } catch (InterruptedException ie) { fail("Join interrupted"); }
                }

                assertImagesEqual(seqDest, parDest,
                    names[f] + " with N=" + n + " produced different output than sequential");
            }
        }
    }

    /**
     * Test: Handles images shorter than N, and heights not divisible by N.
     */
    public void testEdgeCaseHeights() {
        Filter filter = new SobelFilter();
        int[] heights = {1, 3, 7, 13, 100, 101};
        int n = 8; // More threads than rows in some cases

        for (int h : heights) {
            BufferedImage source = createTestImage(50, h);
            BufferedImage seqDest = new BufferedImage(50, h, BufferedImage.TYPE_INT_RGB);
            filter.apply(source, seqDest, 0, h);

            BufferedImage parDest = new BufferedImage(50, h, BufferedImage.TYPE_INT_RGB);
            List<Thread> workers = new java.util.ArrayList<>();
            int chunkSize = (h + n - 1) / n;
            for (int i = 0; i < n; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, h);
                if (start >= end) continue;
                final int s = start, e = end;
                workers.add(new Thread(() -> filter.apply(source, parDest, s, e)));
            }
            for (Thread t : workers) t.start();
            for (Thread t : workers) {
                try { t.join(); } catch (InterruptedException ie) { fail("Join interrupted"); }
            }

            assertImagesEqual(seqDest, parDest, "Height=" + h + " with N=" + n);
        }
    }

    /**
     * Test: ProgressTracker reaches total under concurrent increments.
     */
    public void testProgressTrackerConcurrent() throws Exception {
        int total = 1000;
        int numThreads = 16;
        ProgressTracker tracker = new ProgressTracker(total);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(total);

        for (int i = 0; i < total; i++) {
            executor.submit(() -> {
                tracker.increment();
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals("ProgressTracker did not reach total", total, tracker.getProcessed());
        assertEquals("ProgressTracker percentage wrong", 100.0, tracker.getPercentage(), 0.001);
    }

    /**
     * Test: Corrupt file does not abort the engine.
     */
    public void testCorruptFileHandling() throws Exception {
        // Create a temp directory with a corrupt file
        File tempDir = new File("dataset/test_corrupt");
        tempDir.mkdirs();
        File corruptFile = new File(tempDir, "corrupt.jpg");
        java.io.FileWriter fw = new java.io.FileWriter(corruptFile);
        fw.write("this is not a real image");
        fw.close();

        try {
            // Sequential should not throw
            long seqTime = SequentialEngine.processBatch(
                Arrays.asList(corruptFile),
                new File("dataset/test_corrupt_out"),
                new SobelFilter(),
                new ProgressTracker(1)
            );
            assertTrue("Sequential should complete even with corrupt file", seqTime >= 0);

            // Parallel should not throw
            long parTime = ParallelEngine.processBatch(
                Arrays.asList(corruptFile),
                new File("dataset/test_corrupt_out"),
                new SobelFilter(),
                4,
                new ProgressTracker(1)
            );
            assertTrue("Parallel should complete even with corrupt file", parTime >= 0);
        } finally {
            corruptFile.delete();
            tempDir.delete();
            new File("dataset/test_corrupt_out").delete();
        }
    }

    /**
     * Test: Semaphore does not leak when run twice in a row.
     */
    public void testSemaphoreNoLeak() throws Exception {
        File tempDir = new File("dataset/test_semaphore");
        tempDir.mkdirs();

        // Create a small valid image
        BufferedImage img = createTestImage(100, 100);
        File imgFile = new File(tempDir, "test.jpg");
        javax.imageio.ImageIO.write(img, "jpg", imgFile);

        try {
            // Run batch twice - if semaphore leaks, second run will hang
            long time1 = ParallelEngine.processBatch(
                Arrays.asList(imgFile),
                new File("dataset/test_semaphore_out"),
                new SobelFilter(),
                4,
                new ProgressTracker(1)
            );
            long time2 = ParallelEngine.processBatch(
                Arrays.asList(imgFile),
                new File("dataset/test_semaphore_out"),
                new SobelFilter(),
                4,
                new ProgressTracker(1)
            );

            assertTrue("First run should complete", time1 >= 0);
            assertTrue("Second run should complete (semaphore not leaked)", time2 >= 0);
        } finally {
            imgFile.delete();
            tempDir.delete();
            new File("dataset/test_semaphore_out").delete();
        }
    }
}