package dev.sai.privacyfix.gui;

import dev.sai.privacyfix.PrivacyConfig;
import dev.sai.privacyfix.PrivacyFix;
import dev.sai.privacyfix.compat.CompatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Privacy Settings, drawn in Sodium's visual language: a page list on the
 * left headed by the mod icon/name/version, an option list on the right with
 * tickbox and slider controls, a tooltip panel under it, and Undo / Apply /
 * Close along the bottom. Everything is custom-drawn (no vanilla widgets) so
 * it matches Sodium exactly and renders identically on both game versions.
 *
 * Edits go to a working copy; Apply writes them to the live config and saves.
 */
public final class PrivacySettingsScreen extends CompatScreen {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath(PrivacyFix.MOD_ID, "icon.png");
    private static final Identifier MONERO = Identifier.fromNamespaceAndPath(PrivacyFix.MOD_ID, "monero.png");

    /** Where a coffee would go, on a privacy mod. Clicking copies it. */
    public static final String MONERO_ADDRESS =
            "43hsr5xEuU4ELzcGkTG5DSbs7qgjvZpdyKK1Uef8CCnCEoA88ZBZtAuRZeVNZedTTb82PT9v8dh4Cikx4gwELwVbGdSnxdB";
    public static final String DISCORD = "7phd";

    /** When the address was last copied, so the button can say so for a moment. */
    private long copiedAt;

    /** One option, or -- when {@code setting} is null -- a section heading. */
    private record Row(Settings.Setting setting, int y, int height, String header) {
        Row(Settings.Setting setting, int y, int height) { this(setting, y, height, null); }
    }

    private final Screen parent;
    private final PrivacyConfig live;
    private final PrivacyConfig work = new PrivacyConfig();
    private final List<Settings.Page> pages = List.of(Settings.Page.values());

    private Settings.Page page = Settings.Page.PRIVACY;
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, int[]> rowBounds = new LinkedHashMap<>();
    private Settings.Setting hovered;
    private int hoveredRowY;
    private int originX, optionsX, optionsY, optionsW, listBottom, tooltipX, tooltipW;
    private int undoX, applyX, closeX, buttonsY;
    private boolean draggingSlider;
    private Settings.Int draggedSlider;

    public PrivacySettingsScreen(Screen parent, PrivacyConfig live) {
        super(Component.literal("Privacy Settings"));
        this.parent = parent;
        this.live = live;
        Settings.copy(live, work);
    }

    @Override
    protected void init() {
        // Sodium centres the whole page-list + options + tooltip block
        int contentW = Theme.PAGE_LIST_WIDTH + Theme.OPTION_PAGE_MARGIN + Theme.OPTION_WIDTH
                + Theme.OPTION_PAGE_MARGIN + Theme.MAX_TOOLTIP_WIDTH;
        originX = Math.max(Theme.OPTION_PAGE_MARGIN, (width - contentW) / 2);
        optionsX = originX + Theme.PAGE_LIST_WIDTH + Theme.OPTION_PAGE_MARGIN;
        tooltipX = optionsX + Theme.OPTION_WIDTH + Theme.OPTION_PAGE_MARGIN;
        tooltipW = Math.min(Theme.MAX_TOOLTIP_WIDTH, Math.max(Theme.MIN_TOOLTIP_WIDTH, width - tooltipX - Theme.OPTION_PAGE_MARGIN));
        optionsY = Theme.OPTION_PAGE_MARGIN + headerHeight();
        optionsW = Theme.OPTION_WIDTH;
        buttonsY = height - Theme.BUTTON_SHORT - Theme.INNER_MARGIN;
        closeX = width - Theme.BUTTON_LONG - Theme.INNER_MARGIN;
        applyX = closeX - Theme.BUTTON_LONG - Theme.INNER_MARGIN;
        undoX = applyX - Theme.BUTTON_LONG - Theme.INNER_MARGIN;
        layoutRows();
    }

    private int headerHeight() {
        return font.lineHeight * 3;
    }

    private void layoutRows() {
        rows.clear();
        int y = optionsY;
        int entry = font.lineHeight * 2;
        for (Settings.Setting s : Settings.ALL) {
            if (s.page() != page) continue;
            String header = Settings.sectionBefore(s.id());
            if (header != null) {
                int h = font.lineHeight * 2;
                rows.add(new Row(null, y, h, header));
                y += h;
            }
            rows.add(new Row(s, y, entry));
            y += entry;
        }
        listBottom = y;
    }

    // ---- painting -----------------------------------------------------

    @Override
    protected void paint(Painter p, int mouseX, int mouseY) {
        hovered = null;
        p.fill(0, 0, width, height, Theme.BACKGROUND_DEFAULT);
        paintPageList(p, mouseX, mouseY);
        paintOptions(p, mouseX, mouseY);
        paintTooltip(p);
        paintButtons(p, mouseX, mouseY);
        paintDonate(p, mouseX, mouseY);
    }

    private void paintPageList(Painter p, int mouseX, int mouseY) {
        int x = originX;
        int w = Theme.PAGE_LIST_WIDTH;
        p.fill(x, Theme.OPTION_PAGE_MARGIN, x + w, height - Theme.BUTTON_SHORT - Theme.INNER_MARGIN * 2, Theme.BACKGROUND_LIGHT);

        // mod header: icon + name + version, exactly like Sodium's page list header
        int hy = Theme.OPTION_PAGE_MARGIN;
        int iconSize = font.lineHeight * 2;
        p.texture(ICON, x + Theme.TEXT_LEFT_PADDING, hy + Theme.ICON_MARGIN, iconSize, iconSize, Theme.THEME);
        int textX = x + Theme.TEXT_LEFT_PADDING + iconSize + Theme.ICON_MARGIN;
        p.text("PrivacyFix", textX, hy + Theme.ICON_MARGIN + 1, Theme.THEME_LIGHTER);
        p.text(PrivacyFix.version(), textX, hy + Theme.ICON_MARGIN + font.lineHeight + 1, Theme.THEME_DARKER);

        int y = hy + headerHeight();
        int entry = font.lineHeight * 2;
        for (Settings.Page pg : pages) {
            boolean selected = pg == page;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + entry;
            if (selected) p.fill(x, y, x + w, y + entry, Theme.BACKGROUND_HIGHLIGHT);
            if (hover) p.fill(x, y, x + w, y + entry, Theme.BACKGROUND_HIGHLIGHT);
            if (selected) p.fill(x, y, x + Theme.PAGE_ENTRY_SELECTION_BAR_WIDTH, y + entry, Theme.THEME);
            p.text(Settings.pageName(pg), x + Theme.TEXT_LEFT_PADDING, y + entry / 2 - font.lineHeight / 2,
                    selected ? Theme.THEME_LIGHTER : Theme.FOREGROUND);
            y += entry;
        }
        // "Packets" opens the rule editor, listed like an external page in Sodium
        boolean hoverPackets = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + entry;
        if (hoverPackets) p.fill(x, y, x + w, y + entry, Theme.BACKGROUND_HIGHLIGHT);
        p.text("Packets…", x + Theme.TEXT_LEFT_PADDING, y + entry / 2 - font.lineHeight / 2, Theme.FOREGROUND);
        rowBounds.put("packets", new int[]{x, y, x + w, y + entry});
    }

    private void paintOptions(Painter p, int mouseX, int mouseY) {
        p.fill(optionsX, Theme.OPTION_PAGE_MARGIN, optionsX + optionsW, height - Theme.BUTTON_SHORT - Theme.INNER_MARGIN * 2,
                Theme.BACKGROUND_LIGHT);
        p.text(Settings.pageName(page), optionsX + Theme.TEXT_LEFT_PADDING,
                Theme.OPTION_PAGE_MARGIN + Theme.ICON_MARGIN + font.lineHeight / 2, Theme.THEME_LIGHTER);

        for (Row row : rows) {
            if (row.header() != null) {
                paintSectionHeader(p, row);
                continue;
            }
            boolean hover = mouseX >= optionsX && mouseX < optionsX + optionsW
                    && mouseY >= row.y() && mouseY < row.y() + row.height();
            if (hover) {
                hovered = row.setting();
                hoveredRowY = row.y();
                p.fill(optionsX, row.y(), optionsX + optionsW, row.y() + row.height(), Theme.BACKGROUND_HOVER);
            }
            int textY = row.y() + row.height() / 2 - font.lineHeight / 2;
            boolean enabled = work.enabled || row.setting().id().equals("enabled");
            int labelColor = enabled ? Theme.FOREGROUND : Theme.FOREGROUND_DISABLED;

            if (row.setting() instanceof Settings.Bool b) {
                p.text(trim(p, b.name(), optionsW - Theme.OPTION_LABEL_END_PADDING - Theme.TICKBOX_CONTROL_WIDTH),
                        optionsX + Theme.OPTION_TEXT_SIDE_PADDING, textY, labelColor);
                paintTickbox(p, row, b.get().apply(work), enabled);
            } else if (row.setting() instanceof Settings.Choice ch) {
                String value = ch.get().apply(work);
                p.text(trim(p, ch.name(), optionsW - Theme.CYCLING_CONTROL_WIDTH - Theme.OPTION_LABEL_END_PADDING),
                        optionsX + Theme.OPTION_TEXT_SIDE_PADDING, textY, labelColor);
                int vx = optionsX + optionsW - Theme.OPTION_TEXT_SIDE_PADDING - p.textWidth(value);
                p.text(value, vx, textY, enabled ? Theme.THEME : Theme.FOREGROUND_DISABLED);
            } else if (row.setting() instanceof Settings.Int in) {
                int value = in.get().apply(work);
                String valueText = in.id().equals("viewdistance") && value == 0 ? "off" : String.valueOf(value);
                // the slider is laid out from the right, past its value text,
                // so the label has to be trimmed against what is actually left
                int room = optionsW - Theme.SLIDER_WIDTH - p.textWidth(valueText)
                        - Theme.OPTION_TEXT_SIDE_PADDING * 2 - Theme.OPTION_LABEL_END_PADDING;
                p.text(trim(p, in.name(), room), optionsX + Theme.OPTION_TEXT_SIDE_PADDING, textY, labelColor);
                paintSlider(p, row, in, value, valueText, enabled);
            }
        }
    }

    /** A heading with a rule beside it, so a page reads as blocks rather than one list. */
    private void paintSectionHeader(Painter p, Row row) {
        int textY = row.y() + row.height() - font.lineHeight - 2;
        int x = optionsX + Theme.OPTION_TEXT_SIDE_PADDING;
        p.text(row.header(), x, textY, Theme.THEME_LIGHTER);
        int lineX = x + p.textWidth(row.header()) + Theme.OPTION_TEXT_SIDE_PADDING;
        int lineY = textY + font.lineHeight / 2;
        int end = optionsX + optionsW - Theme.OPTION_TEXT_SIDE_PADDING;
        if (lineX < end) p.fill(lineX, lineY, end, lineY + 1, Theme.BACKGROUND_DARKER);
    }

    private void paintTickbox(Painter p, Row row, boolean ticked, boolean enabled) {
        int x = optionsX + optionsW - Theme.OPTION_TEXT_SIDE_PADDING - Theme.CONTROL_ICON_SIZE;
        int y = row.y() + row.height() / 2 - Theme.CONTROL_ICON_SIZE / 2;
        int xEnd = x + Theme.CONTROL_ICON_SIZE, yEnd = y + Theme.CONTROL_ICON_SIZE;
        int color = enabled ? (ticked ? Theme.THEME : Theme.FOREGROUND) : Theme.FOREGROUND_DISABLED;
        if (ticked) p.fill(x + 2, y + 2, xEnd - 2, yEnd - 2, color);
        p.outline(x, y, xEnd, yEnd, color);
    }

    private void paintSlider(Painter p, Row row, Settings.Int in, int value, String valueText, boolean enabled) {
        int trackEnd = optionsX + optionsW - Theme.OPTION_TEXT_SIDE_PADDING;
        int labelW = p.textWidth(valueText) + Theme.OPTION_TEXT_SIDE_PADDING;
        int x = trackEnd - Theme.SLIDER_WIDTH - labelW;
        int y = row.y() + row.height() / 2 - Theme.SLIDER_HEIGHT / 2;
        int w = Theme.SLIDER_WIDTH;
        float pct = (float) (value - in.min()) / Math.max(1, in.max() - in.min());
        int thumb = x + Math.round(pct * (w - 4));
        int color = enabled ? Theme.THEME : Theme.FOREGROUND_DISABLED;
        p.fill(x, y + Theme.SLIDER_HEIGHT / 2 - 1, x + w, y + Theme.SLIDER_HEIGHT / 2 + 1, Theme.BACKGROUND_DARKER);
        p.fill(x, y + Theme.SLIDER_HEIGHT / 2 - 1, thumb, y + Theme.SLIDER_HEIGHT / 2 + 1, color);
        p.fill(thumb, y, thumb + 4, y + Theme.SLIDER_HEIGHT, color);
        p.text(valueText, trackEnd - p.textWidth(valueText), row.y() + row.height() / 2 - font.lineHeight / 2,
                enabled ? Theme.FOREGROUND : Theme.FOREGROUND_DISABLED);
        rowBounds.put("slider:" + in.id(), new int[]{x, y, x + w, y + Theme.SLIDER_HEIGHT});
    }

    private void paintTooltip(Painter p) {
        if (hovered == null) return;
        int x = tooltipX, w = tooltipW;
        int inner = w - Theme.TOOLTIP_OUTER_MARGIN * 2 - 4;
        List<String> lines = wrap(p, hovered.tooltip(), inner);
        int h = Theme.TOOLTIP_OUTER_MARGIN * 2 + font.lineHeight * (lines.size() + 1) + Theme.TEXT_PARAGRAPH_SPACING;
        // anchor beside the hovered row, clamped into the panel like Sodium does
        int y = hoveredRowY;
        int limit = height - Theme.BUTTON_SHORT - Theme.INNER_MARGIN * 2;
        if (y + h > limit) y = Math.max(Theme.OPTION_PAGE_MARGIN, limit - h);
        p.fill(x, y, x + w, y + h, Theme.BACKGROUND_OVERLAY);
        p.text(trim(p, hovered.name(), inner), x + Theme.TOOLTIP_OUTER_MARGIN + 2, y + Theme.TOOLTIP_OUTER_MARGIN + 2, Theme.THEME_LIGHTER);
        int ly = y + Theme.TOOLTIP_OUTER_MARGIN + 2 + font.lineHeight + Theme.TEXT_PARAGRAPH_SPACING / 2;
        for (String line : lines) {
            p.text(line, x + Theme.TOOLTIP_OUTER_MARGIN + 2, ly, Theme.FOREGROUND);
            ly += font.lineHeight + Theme.TEXT_LINE_SPACING - 1;
        }
    }

    private void paintButtons(Painter p, int mouseX, int mouseY) {
        boolean dirty = Settings.differs(work, live);
        button(p, undoX, "Undo", dirty, mouseX, mouseY);
        button(p, applyX, "Apply", dirty, mouseX, mouseY);
        button(p, closeX, "Close", true, mouseX, mouseY);
        String preset = "Preset: " + Settings.presetName(Settings.currentPreset(work));
        p.text(preset, originX + Theme.TEXT_LEFT_PADDING,
                buttonsY + Theme.BUTTON_SHORT / 2 - font.lineHeight / 2, Theme.THEME_DARKER);
        rowBounds.put("preset", new int[]{originX, buttonsY,
                originX + Theme.TEXT_LEFT_PADDING + p.textWidth(preset) + 4, buttonsY + Theme.BUTTON_SHORT});
    }

    /**
     * Sodium puts "Buy us a coffee!" in the top-right corner; this is the
     * same spot with a Monero mark. Hovering shows the address and a Discord
     * handle, clicking copies the address and says so for two seconds.
     */
    private void paintDonate(Painter p, int mouseX, int mouseY) {
        boolean justCopied = System.currentTimeMillis() - copiedAt < 2000;
        String label = justCopied ? "Copied!" : "Donate";
        int icon = font.lineHeight;
        int w = Theme.BUTTON_SHORT + icon + p.textWidth(label), h = Theme.BUTTON_SHORT;
        int x = width - Theme.INNER_MARGIN - w, y = Theme.OPTION_PAGE_MARGIN;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        p.fill(x, y, x + w, y + h, hover ? Theme.BACKGROUND_HOVER : Theme.BACKGROUND_LIGHT);
        p.outline(x, y, x + w, y + h, Theme.BUTTON_BORDER);
        p.texture(MONERO, x + Theme.BUTTON_SHORT / 4, y + (h - icon) / 2, icon, icon, 0xFFFFFFFF);
        p.text(label, x + Theme.BUTTON_SHORT / 4 + icon + Theme.BUTTON_SHORT / 4, y + h / 2 - font.lineHeight / 2,
                justCopied ? Theme.THEME_LIGHTER : Theme.FOREGROUND);
        rowBounds.put("donate", new int[]{x, y, x + w, y + h});

        if (!hover) return;
        // the address is long: wrap it hard, it has no spaces to break on
        List<String> lines = new ArrayList<>();
        lines.add("Monero (click to copy)");
        int inner = tooltipW - Theme.TOOLTIP_OUTER_MARGIN * 2 - 4;
        StringBuilder chunk = new StringBuilder();
        for (char c : MONERO_ADDRESS.toCharArray()) {
            if (p.textWidth(chunk.toString() + c) > inner) { lines.add(chunk.toString()); chunk.setLength(0); }
            chunk.append(c);
        }
        if (chunk.length() > 0) lines.add(chunk.toString());
        lines.add("");
        lines.add("Discord: " + DISCORD);
        int th = Theme.TOOLTIP_OUTER_MARGIN * 2 + (font.lineHeight + Theme.TEXT_LINE_SPACING - 1) * lines.size();
        int tx = Math.max(Theme.INNER_MARGIN, x + w - tooltipW), ty = y + h + Theme.INNER_MARGIN;
        p.fill(tx, ty, tx + tooltipW, ty + th, Theme.BACKGROUND_OVERLAY);
        int ly = ty + Theme.TOOLTIP_OUTER_MARGIN + 2;
        for (int i = 0; i < lines.size(); i++) {
            p.text(lines.get(i), tx + Theme.TOOLTIP_OUTER_MARGIN + 2, ly, i == 0 ? Theme.THEME_LIGHTER : Theme.FOREGROUND);
            ly += font.lineHeight + Theme.TEXT_LINE_SPACING - 1;
        }
    }

    private void button(Painter p, int x, String label, boolean enabled, int mouseX, int mouseY) {
        int y = buttonsY, w = Theme.BUTTON_LONG, h = Theme.BUTTON_SHORT;
        boolean hover = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        p.fill(x, y, x + w, y + h, enabled ? (hover ? Theme.BACKGROUND_HOVER : Theme.BACKGROUND_DEFAULT) : Theme.BACKGROUND_LIGHT);
        p.outline(x, y, x + w, y + h, Theme.BUTTON_BORDER);
        p.text(label, x + w / 2 - p.textWidth(label) / 2, y + h / 2 - font.lineHeight / 2,
                enabled ? Theme.THEME_LIGHTER : Theme.THEME_DARKER);
        rowBounds.put("btn:" + label, new int[]{x, y, x + w, y + h});
    }

    private String trim(Painter p, String s, int max) {
        if (p.textWidth(s) <= max) return s;
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (p.textWidth(sb.toString() + c + "…") > max) break;
            sb.append(c);
        }
        return sb.append('…').toString();
    }

    private List<String> wrap(Painter p, String text, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (p.textWidth(candidate) > max && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    // ---- interaction --------------------------------------------------

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        int mx = (int) event.x(), my = (int) event.y();

        if (in("donate", mx, my)) {
            minecraft.keyboardHandler.setClipboard(MONERO_ADDRESS);
            copiedAt = System.currentTimeMillis();
            return true;
        }
        if (in("btn:Close", mx, my)) { onClose(); return true; }
        if (in("btn:Apply", mx, my) && Settings.differs(work, live)) { apply(); return true; }
        if (in("btn:Undo", mx, my)) { Settings.copy(live, work); return true; }
        if (in("preset", mx, my)) { cyclePreset(); return true; }
        if (in("packets", mx, my)) { minecraft.setScreenAndShow(new PacketsScreen(this, live)); return true; }

        // page list entries
        int x = originX, w = Theme.PAGE_LIST_WIDTH;
        int entry = font.lineHeight * 2, y = Theme.OPTION_PAGE_MARGIN + headerHeight();
        for (Settings.Page pg : pages) {
            if (mx >= x && mx < x + w && my >= y && my < y + entry) {
                page = pg;
                layoutRows();
                return true;
            }
            y += entry;
        }

        // option rows
        for (Row row : rows) {
            if (row.header() != null) continue;
            if (mx < optionsX || mx >= optionsX + optionsW || my < row.y() || my >= row.y() + row.height()) continue;
            if (row.setting() instanceof Settings.Bool b) {
                b.set().accept(work, !b.get().apply(work));
                return true;
            }
            if (row.setting() instanceof Settings.Choice ch) {
                java.util.List<String> values = ch.values();
                int i = values.indexOf(ch.get().apply(work));
                boolean back = (event.modifiers() & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
                ch.set().accept(work, values.get(Math.floorMod(i + (back ? -1 : 1), values.size())));
                return true;
            }
            if (row.setting() instanceof Settings.Int in) {
                int[] bounds = rowBounds.get("slider:" + in.id());
                if (bounds != null && mx >= bounds[0] && mx <= bounds[2]) {
                    draggingSlider = true;
                    draggedSlider = in;
                    setSliderFromMouse(in, mx, bounds);
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (draggingSlider && draggedSlider != null) {
            int[] bounds = rowBounds.get("slider:" + draggedSlider.id());
            if (bounds != null) setSliderFromMouse(draggedSlider, (int) event.x(), bounds);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        draggingSlider = false;
        draggedSlider = null;
        return super.mouseReleased(event);
    }

    private void setSliderFromMouse(Settings.Int in, int mx, int[] bounds) {
        float pct = Math.max(0f, Math.min(1f, (float) (mx - bounds[0]) / Math.max(1, bounds[2] - bounds[0])));
        int steps = Math.max(1, (in.max() - in.min()) / in.step());
        int value = in.min() + Math.round(pct * steps) * in.step();
        in.set().accept(work, Math.max(in.min(), Math.min(in.max(), value)));
    }

    private void cyclePreset() {
        Settings.Preset[] all = Settings.Preset.values();
        Settings.Preset now = Settings.currentPreset(work);
        Settings.Preset next = all[(now.ordinal() + 1) % all.length];
        if (next == Settings.Preset.CUSTOM) next = all[(next.ordinal() + 1) % all.length];
        Settings.applyPreset(work, next);
    }

    private boolean in(String key, int mx, int my) {
        int[] b = rowBounds.get(key);
        return b != null && mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3];
    }

    private void apply() {
        Settings.copy(work, live);
        live.save();
        dev.sai.privacyfix.names.Names.invalidate();
        PrivacyFix.LOGGER.info("settings applied from the Privacy Settings screen");
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }
}
