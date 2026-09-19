package dev.sai.privacyfix.gui;

import net.minecraft.resources.Identifier;

/** Minimal 2D drawing surface; implemented per game version by CompatScreen. */
public interface Painter {
    void fill(int x1, int y1, int x2, int y2, int argb);

    void text(String s, int x, int y, int argb);

    int textWidth(String s);

    /** Draws the whole texture scaled into the rect, multiplied by {@code tint}. */
    void texture(Identifier texture, int x, int y, int w, int h, int tint);

    default void outline(int x1, int y1, int x2, int y2, int argb) {
        fill(x1, y1, x2, y1 + 1, argb);
        fill(x1, y2 - 1, x2, y2, argb);
        fill(x1, y1, x1 + 1, y2, argb);
        fill(x2 - 1, y1, x2, y2, argb);
    }
}
