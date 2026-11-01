package edu.pp.imgstudio.core;

import java.awt.image.BufferedImage;

public interface Filter {
    void apply(BufferedImage source, BufferedImage destination, int startRow, int endRow);
}
