package edu.pp.imgstudio.core;

import java.awt.image.BufferedImage;

public class GaussianBlurFilter implements Filter {
    private static final double[][] kernel = {
        {1.0/16, 2.0/16, 1.0/16},
        {2.0/16, 4.0/16, 2.0/16},
        {1.0/16, 2.0/16, 1.0/16}
    };

    @Override
    public void apply(BufferedImage source, BufferedImage destination, int startRow, int endRow) {
        int width = source.getWidth();
        int height = source.getHeight();

        for (int y = startRow; y < endRow; y++) {
            for (int x = 0; x < width; x++) {
                double r = 0, g = 0, b = 0;

                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int px = Math.max(0, Math.min(width - 1, x + kx));
                        int py = Math.max(0, Math.min(height - 1, y + ky));
                        int rgb = source.getRGB(px, py);
                        
                        r += ((rgb >> 16) & 0xFF) * kernel[ky + 1][kx + 1];
                        g += ((rgb >> 8) & 0xFF) * kernel[ky + 1][kx + 1];
                        b += (rgb & 0xFF) * kernel[ky + 1][kx + 1];
                    }
                }

                int finalRGB = ((int)r << 16) | ((int)g << 8) | (int)b;
                destination.setRGB(x, y, finalRGB);
            }
        }
    }
}