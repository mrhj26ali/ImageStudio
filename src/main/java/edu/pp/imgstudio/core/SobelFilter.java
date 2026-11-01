package edu.pp.imgstudio.core;

import java.awt.image.BufferedImage;

public class SobelFilter implements Filter {
    private static final int[][] kernelX = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};
    private static final int[][] kernelY = {{-1, -2, -1}, {0, 0, 0}, {1, 2, 1}};

    @Override
    public void apply(BufferedImage source, BufferedImage destination, int startRow, int endRow) {
        int width = source.getWidth();
        int height = source.getHeight();

        for (int y = startRow; y < endRow; y++) {
            for (int x = 0; x < width; x++) {
                int gx = 0, gy = 0;

                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int px = Math.max(0, Math.min(width - 1, x + kx));
                        int py = Math.max(0, Math.min(height - 1, y + ky));
                        int rgb = source.getRGB(px, py);
                        int gray = (rgb >> 16) & 0xFF; 
                        
                        gx += gray * kernelX[ky + 1][kx + 1];
                        gy += gray * kernelY[ky + 1][kx + 1];
                    }
                }

                int magnitude = (int) Math.sqrt(gx * gx + gy * gy);
                magnitude = Math.min(255, magnitude);
                int edgeRGB = (magnitude << 16) | (magnitude << 8) | magnitude;
                destination.setRGB(x, y, edgeRGB);
            }
        }
    }
}
