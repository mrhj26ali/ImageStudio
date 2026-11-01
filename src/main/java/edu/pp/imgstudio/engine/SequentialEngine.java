package edu.pp.imgstudio.engine;

import edu.pp.imgstudio.core.Filter;
import edu.pp.imgstudio.sync.ProgressTracker;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Sequential image processing engine (single-threaded baseline).
 */
public class SequentialEngine {
    
    /**
     * Processes a batch of images sequentially.
     * @return Processing result with timing and failed files
     */
    public static ParallelEngine.ProcessingResult processBatch(List<File> inputFiles, File outputDir, 
                                                                 Filter filter, ProgressTracker tracker) throws Exception {
        if (!outputDir.exists()) outputDir.mkdirs();

        List<File> failedFiles = new ArrayList<>();
        long startTime = System.nanoTime();

        for (File inputFile : inputFiles) {
            try {
                BufferedImage source = ImageIO.read(inputFile);
                if (source == null) {
                    System.err.println("Warning: Could not read " + inputFile.getName() + ", skipping.");
                    tracker.increment();
                    failedFiles.add(inputFile);
                    continue;
                }

                BufferedImage destination = new BufferedImage(
                    source.getWidth(),
                    source.getHeight(),
                    BufferedImage.TYPE_INT_RGB
                );

                filter.apply(source, destination, 0, source.getHeight());

                String outputName = ImageUtils.getOutputFilename(inputFile.getName());
                ImageIO.write(destination, "jpg", new File(outputDir, outputName));
                
                tracker.increment();

            } catch (Exception e) {
                System.err.println("Error processing: " + inputFile.getName() + " - " + e.getMessage());
                tracker.increment();
                failedFiles.add(inputFile);
            }
        }

        long elapsed = (System.nanoTime() - startTime) / 1_000_000;
        return new ParallelEngine.ProcessingResult(elapsed, failedFiles);
    }
}