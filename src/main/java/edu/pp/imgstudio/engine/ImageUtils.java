package edu.pp.imgstudio.engine;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Shared utilities for file discovery and naming.
 */
public class ImageUtils {
    
    /**
     * Discovers all image files (.jpg, .jpeg, .png) in the given directory.
     */
    public static List<File> discoverImages(File inputDir) {
        if (!inputDir.exists() || !inputDir.isDirectory()) {
            return Arrays.asList();
        }
        
        File[] files = inputDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
        });
        
        return files == null ? Arrays.asList() : Arrays.asList(files);
    }
    
    /**
     * Generates the output filename for a processed image.
     */
    public static String getOutputFilename(String inputFilename) {
        return "processed_" + inputFilename;
    }
}