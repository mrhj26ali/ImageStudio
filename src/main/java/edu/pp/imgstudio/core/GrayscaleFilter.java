package edu.pp.imgstudio.core;

import java.awt.image.BufferedImage;

public class GrayscaleFilter implements Filter {
    @Override
    public void apply(BufferedImage source, BufferedImage destination, int startRow, int endRow) {
        int width = source.getWidth();
        for (int y = startRow; y < endRow; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = source.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                int grayRGB = (gray << 16) | (gray << 8) | gray;
                destination.setRGB(x, y, grayRGB);
            }
        }
    }
}
