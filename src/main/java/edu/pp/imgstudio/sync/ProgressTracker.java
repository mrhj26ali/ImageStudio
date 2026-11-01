package edu.pp.imgstudio.sync;

import java.util.concurrent.atomic.AtomicInteger;

public class ProgressTracker {
    private final AtomicInteger processedImages = new AtomicInteger(0);
    private final int totalImages;

    public ProgressTracker(int totalImages) {
        this.totalImages = totalImages;
    }

    public void increment() {
        processedImages.incrementAndGet();
    }

    public int getProcessed() {
        return processedImages.get();
    }

    public double getPercentage() {
        return (processedImages.get() * 100.0) / totalImages;
    }
}
