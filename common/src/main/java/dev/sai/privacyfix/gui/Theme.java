package dev.sai.privacyfix.gui;

/**
 * Sodium's GUI palette and layout metrics, so the standalone screen is
 * visually identical to the page Sodium renders for us when it is installed.
 * Values mirror net.caffeinemc.mods.sodium.client.gui.{Colors,Layout}; the
 * theme colour is ours (the shield blue from the mod icon).
 */
public final class Theme {
    // --- colours (ARGB), from Sodium's Colors ---
    public static final int THEME = 0xFF6E86FF;
    public static final int THEME_LIGHTER = lighten(THEME);
    public static final int THEME_DARKER = darken(THEME);
    public static final int FOREGROUND = 0xFFFFFFFF;
    public static final int FOREGROUND_DISABLED = 0xFFAAAAAA;

    public static final int BACKGROUND_LIGHT = 0x40000000;
    public static final int BACKGROUND_MEDIUM = 0x60000000;
    public static final int BACKGROUND_HOVER = 0xE0000000;
    public static final int BACKGROUND_OVERLAY = 0xEA000000;
    public static final int BACKGROUND_DEFAULT = 0x90000000;
    public static final int BACKGROUND_DARKER = 0xB0000000;
    public static final int BACKGROUND_HIGHLIGHT = 0x08FFFFFF;
    public static final int BUTTON_BORDER = (0x80 << 24) | (THEME & 0x00FFFFFF);

    // --- layout, from Sodium's Layout ---
    public static final int BUTTON_SHORT = 20, BUTTON_LONG = 65, INNER_MARGIN = 5;
    public static final int OPTION_GROUP_MARGIN = 3, OPTION_PAGE_MARGIN = 6, OPTION_MOD_MARGIN = 12;
    public static final int SCROLLBAR_WIDTH = 7;
    public static final int TEXT_LEFT_PADDING = 8, TEXT_LINE_SPACING = 2, TEXT_PARAGRAPH_SPACING = 8;
    public static final int PAGE_LIST_WIDTH = 125;
    public static final int OPTION_WIDTH = 210;
    public static final int OPTION_TEXT_SIDE_PADDING = 6, OPTION_LABEL_END_PADDING = 20;
    public static final int CONTROL_ICON_SIZE = 10;
    public static final int TICKBOX_CONTROL_WIDTH = 30, CYCLING_CONTROL_WIDTH = 70, SLIDER_WIDTH = 90, SLIDER_HEIGHT = 10;
    public static final int ICON_MARGIN = 4;
    public static final int MIN_TOOLTIP_WIDTH = 100, MAX_TOOLTIP_WIDTH = 200, TOOLTIP_OUTER_MARGIN = 3;
    public static final int PAGE_ENTRY_SELECTION_BAR_WIDTH = 3, PAGE_ENTRY_LABEL_END_PADDING = 14;
    public static final int CONTENT_BORDER_HEIGHT = OPTION_MOD_MARGIN;

    private static final float LIGHTEN = 0.3f, DARKEN = -0.23f;

    private Theme() {}

    public static int lighten(int color) { return adjust(color, LIGHTEN); }

    public static int darken(int color) { return adjust(color, DARKEN); }

    /** Same HSV adjustment Sodium uses to derive the lighter/darker theme shades. */
    public static int adjust(int color, float factor) {
        int a = color >>> 24;
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0f;
        if (d != 0f) {
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h *= 60f;
            if (h < 0) h += 360f;
        }
        float s = max == 0f ? 0f : d / max;
        float v = max;
        s = clamp(s * (1 - Math.abs(factor)));
        v = clamp(v * (1 + factor));
        return (a << 24) | (fromHSV(h, s, v) & 0x00FFFFFF);
    }

    private static float clamp(float v) { return v < 0 ? 0 : Math.min(v, 1); }

    private static int fromHSV(float h, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }
}
