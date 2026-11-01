package edu.pp.imgstudio.model;

public enum FilterType {
    GRAYSCALE("Grayscale"),
    GAUSSIAN_BLUR("Gaussian Blur (Noise Removal)"),
    SOBEL_EDGE("Sobel Edge Detection");

    private final String displayName;
    FilterType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}