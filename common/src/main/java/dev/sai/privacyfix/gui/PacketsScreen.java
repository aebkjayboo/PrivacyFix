package dev.sai.privacyfix.gui;

import dev.sai.privacyfix.PrivacyConfig;
import dev.sai.privacyfix.PrivacyFix;
import dev.sai.privacyfix.compat.CompatScreen;
import dev.sai.privacyfix.packets.PacketCatalog;
import dev.sai.privacyfix.packets.PacketNode;
import dev.sai.privacyfix.packets.PacketRules;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The packet graph: draggable nodes wired together with links.
 *
 * Drag a node by its title bar to move it; drag from the dot on its right
 * edge to the dot on another node's left edge to wire them; click a row in
 * the body to change it; right-click a node to delete it. Dragging empty
 * space pans the canvas. Nothing reaches the config until Save.
 */
public final class PacketsScreen extends CompatScreen {
    private static final int NODE_W = 150, NODE_W_MAX = 250, HEADER_H = 14, ROW_H = 12, PORT = 5, GRID = 16;
    private static final int PICKER_W = 190, PICKER_H = 180;
    private static final String[] KINDS = {"when", "if", "set", "copy", "drop", "log"};

    private final Screen parent;
    private final PrivacyConfig config;
    private final List<PacketNode> nodes = new ArrayList<>();
    private final Map<String, int[]> hit = new LinkedHashMap<>();
    /** Measured node widths, refreshed each frame. */
    private final Map<String, Integer> widths = new java.util.HashMap<>();

    private int panX, panY;
    private PacketNode dragging;
    private int dragDX, dragDY;
    private boolean panning;
    private int panStartX, panStartY, panOriginX, panOriginY;
    private PacketNode linkFrom;
    private int mouseX, mouseY;
    private EditBox editor;
    private PacketNode editNode;
    private String editKey;
    /** The node whose packet dropdown is open, or null. */
    private PacketNode picking;
    private int pickScroll;

    public PacketsScreen(Screen parent, PrivacyConfig config) {
        super(Component.literal("Packets"));
        this.parent = parent;
        this.config = config;
        for (PacketNode n : config.packetNodes) nodes.add(n.copy());
    }

    @Override
    protected void init() {
        // The toolbar is painted and hit-tested by hand so it keeps the same
        // flat look as the rest of the screen.
    }

    private int nodeHeight(PacketNode n) {
        return HEADER_H + rows(n).size() * ROW_H + 4;
    }

    /** The editable rows inside a node, as label/value pairs. */
    private List<String[]> rows(PacketNode n) {
        List<String[]> out = new ArrayList<>();
        switch (n.kind) {
            case WHEN -> out.add(new String[]{"packet", shorten(n.packet)});
            case IF -> {
                out.add(new String[]{"field", fieldLabel(n, n.field)});
                out.add(new String[]{"is", n.compare.name().toLowerCase().replace('_', ' ')});
                out.add(new String[]{"value", n.value.isEmpty() ? "·" : n.value});
            }
            case SET, COPY -> {
                PacketNode when = whenFor(n);
                List<String> labels = when == null ? List.of() : PacketCatalog.fieldLabels(when.packet);
                for (int i = 0; i < labels.size(); i++) {
                    String v = n.set.getOrDefault(String.valueOf(i), "");
                    out.add(new String[]{labels.get(i), v.isEmpty() ? "·" : v});
                }
                if (labels.isEmpty()) out.add(new String[]{"wire to a when node", ""});
            }
            case DROP, LOG -> { }
        }
        return out;
    }

    /** "ServerboundMovePlayerPacket$Pos" reads as "MovePlayer Pos". */
    private String shorten(String packet) {
        int dollar = packet.indexOf('$');
        if (dollar > 0) return shorten(packet.substring(0, dollar)) + " " + packet.substring(dollar + 1);
        String s = packet.startsWith("Serverbound") ? packet.substring("Serverbound".length()) : packet;
        return s.endsWith("Packet") ? s.substring(0, s.length() - "Packet".length()) : s;
    }

    /** Walks back to the WHEN node feeding this one, so field names can be shown. */
    private PacketNode whenFor(PacketNode target) {
        for (PacketNode n : nodes) {
            if (n.kind == PacketNode.Kind.WHEN && reaches(n, target, 0)) return n;
        }
        return null;
    }

    private boolean reaches(PacketNode from, PacketNode target, int depth) {
        if (from == target) return true;
        if (depth > 16) return false;
        for (String id : from.next) {
            PacketNode n = byId(id);
            if (n != null && reaches(n, target, depth + 1)) return true;
        }
        return false;
    }

    private String fieldLabel(PacketNode n, int index) {
        PacketNode when = whenFor(n);
        List<String> labels = when == null ? List.of() : PacketCatalog.fieldLabels(when.packet);
        return index >= 0 && index < labels.size() ? labels.get(index) : "#" + index;
    }

    // ---- painting -----------------------------------------------------

    @Override
    protected void paint(Painter p, int mx, int my) {
        mouseX = mx;
        mouseY = my;
        hit.clear();
        measure(p);
        p.fill(0, 0, width, height, Theme.BACKGROUND_DEFAULT);
        paintGrid(p);
        paintWires(p);
        for (PacketNode n : nodes) paintNode(p, n);
        if (linkFrom != null) {
            int[] from = outputPort(linkFrom);
            wire(p, from[0], from[1], mx, my, Theme.THEME_LIGHTER);
        }
        paintToolbar(p);
        if (picking != null) paintPicker(p);
    }

    /** The packet dropdown: categories as headers, packets under them. */
    private void paintPicker(Painter p) {
        int x = Math.min(picking.x + panX, width - PICKER_W - 4);
        int y = Math.min(picking.y + panY + HEADER_H, height - PICKER_H - 4);
        p.fill(x, y, x + PICKER_W, y + PICKER_H, Theme.BACKGROUND_OVERLAY);
        p.outline(x, y, x + PICKER_W, y + PICKER_H, Theme.THEME);
        p.text("pick a packet", x + 6, y + 4, Theme.THEME_LIGHTER);

        int row = y + 16, shown = 0, index = 0;
        for (Map.Entry<PacketCatalog.Category, List<String>> group : PacketCatalog.byCategory().entrySet()) {
            if (index++ >= pickScroll && row < y + PICKER_H - ROW_H) {
                p.fill(x + 1, row, x + PICKER_W - 1, row + ROW_H, 0x30FFFFFF);
                p.text(group.getKey().label, x + 6, row + 2, Theme.THEME_DARKER);
                row += ROW_H;
                shown++;
            }
            for (String name : group.getValue()) {
                if (index++ < pickScroll) continue;
                if (row >= y + PICKER_H - ROW_H) break;
                boolean hov = mouseX >= x && mouseX < x + PICKER_W && mouseY >= row && mouseY < row + ROW_H;
                boolean current = name.equals(picking.packet);
                if (hov) p.fill(x + 1, row, x + PICKER_W - 1, row + ROW_H, Theme.BACKGROUND_HOVER);
                if (current) p.fill(x + 1, row, x + 3, row + ROW_H, Theme.THEME);
                p.text(ellipsise(p, shorten(name), PICKER_W - 16), x + 10, row + 2,
                        current ? Theme.THEME_LIGHTER : Theme.FOREGROUND);
                hit.put("pick:" + name, new int[]{x, row, x + PICKER_W, row + ROW_H});
                row += ROW_H;
                shown++;
            }
        }
        p.text("scroll for more", x + 6, y + PICKER_H - 10, Theme.THEME_DARKER);
    }

    /**
     * A node is as wide as its widest row needs, so a long packet or field
     * name is not squeezed against its value. Clamped, so one huge value
     * cannot stretch a node across the screen.
     */
    private void measure(Painter p) {
        widths.clear();
        for (PacketNode n : nodes) {
            int w = p.textWidth(n.title()) + 12;
            for (String[] row : rows(n)) w = Math.max(w, p.textWidth(row[0]) + 8 + p.textWidth(row[1]) + 9);
            widths.put(n.id, Math.max(NODE_W, Math.min(NODE_W_MAX, w)));
        }
    }

    private int widthOf(PacketNode n) {
        Integer w = widths.get(n.id);
        return w == null ? NODE_W : w;
    }

    private void paintGrid(Painter p) {
        int c = 0x10FFFFFF;
        for (int x = Math.floorMod(panX, GRID); x < width; x += GRID) p.fill(x, 0, x + 1, height, c);
        for (int y = Math.floorMod(panY, GRID); y < height; y += GRID) p.fill(0, y, width, y + 1, c);
    }

    private void paintWires(Painter p) {
        for (PacketNode n : nodes) {
            if (!n.hasOutput()) continue;
            int[] from = outputPort(n);
            for (String id : n.next) {
                PacketNode target = byId(id);
                if (target == null) continue;
                int[] to = inputPort(target);
                wire(p, from[0], from[1], to[0], to[1], Theme.THEME);
            }
        }
    }

    /** A link drawn as a stepped line, so crossing wires stay readable. */
    private void wire(Painter p, int x1, int y1, int x2, int y2, int color) {
        int mid = (x1 + x2) / 2;
        hline(p, x1, mid, y1, color);
        vline(p, mid, y1, y2, color);
        hline(p, mid, x2, y2, color);
        p.fill(x2 - 2, y2 - 2, x2 + 2, y2 + 2, color);
    }

    private void hline(Painter p, int xa, int xb, int y, int c) {
        p.fill(Math.min(xa, xb), y, Math.max(xa, xb) + 1, y + 1, c);
    }

    private void vline(Painter p, int x, int ya, int yb, int c) {
        p.fill(x, Math.min(ya, yb), x + 1, Math.max(ya, yb) + 1, c);
    }

    private void paintNode(Painter p, PacketNode n) {
        int x = n.x + panX, y = n.y + panY, h = nodeHeight(n), w = widthOf(n);
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        p.fill(x, y, x + w, y + h, Theme.BACKGROUND_OVERLAY);
        p.outline(x, y, x + w, y + h, hovered ? Theme.THEME_LIGHTER : Theme.BUTTON_BORDER);
        p.fill(x, y, x + w, y + HEADER_H, kindColor(n.kind));
        p.text(n.title(), x + 4, y + 3, Theme.FOREGROUND);
        if (n.kind == PacketNode.Kind.WHEN && hovered) {
            PacketCatalog.Entry e = PacketCatalog.byName(n.packet);
            if (e != null && !e.note().isEmpty()) p.text(e.note(), x, y - 10, Theme.THEME_DARKER);
        }
        hit.put("node:" + n.id, new int[]{x, y, x + w, y + HEADER_H});
        hit.put("body:" + n.id, new int[]{x, y, x + w, y + h});

        int ry = y + HEADER_H + 2;
        List<String[]> rows = rows(n);
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            boolean editing = editNode == n && editKey != null && editKey.equals(String.valueOf(i));
            // the value is laid out first and the label gets whatever is left,
            // so the two can never run into each other
            String value = editing ? "" : row[1];
            int valueW = value.isEmpty() ? 0 : p.textWidth(value);
            int labelRoom = w - 9 - valueW - 4;
            p.text(ellipsise(p, row[0], labelRoom), x + 4, ry + 2, Theme.FOREGROUND_DISABLED);
            if (!value.isEmpty()) p.text(value, x + w - 5 - valueW, ry + 2, Theme.THEME_LIGHTER);
            hit.put("row:" + n.id + ":" + i, new int[]{x, ry, x + w, ry + ROW_H});
            ry += ROW_H;
        }

        int[] in = inputPort(n);
        p.fill(in[0] - PORT / 2, in[1] - PORT / 2, in[0] + PORT / 2, in[1] + PORT / 2, Theme.FOREGROUND);
        hit.put("in:" + n.id, new int[]{in[0] - PORT, in[1] - PORT, in[0] + PORT, in[1] + PORT});
        if (n.hasOutput()) {
            int[] out = outputPort(n);
            p.fill(out[0] - PORT / 2, out[1] - PORT / 2, out[0] + PORT / 2, out[1] + PORT / 2, Theme.THEME);
            hit.put("out:" + n.id, new int[]{out[0] - PORT, out[1] - PORT, out[0] + PORT, out[1] + PORT});
        }
    }

    /** Cuts text to fit, with a trailing ellipsis, so nothing overlaps. */
    private String ellipsise(Painter p, String text, int max) {
        if (max <= 0) return "";
        if (p.textWidth(text) <= max) return text;
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (p.textWidth(sb.toString() + c + "\u2026") > max) break;
            sb.append(c);
        }
        return sb.append('\u2026').toString();
    }

    private static int kindColor(PacketNode.Kind k) {
        return switch (k) {
            case WHEN -> 0xB03A4A8C;
            case IF -> 0xB0806020;
            case DROP -> 0xB0802020;
            case SET -> 0xB0206040;
            case COPY -> 0xB0405080;
            case LOG -> 0xB0303030;
        };
    }

    private int[] inputPort(PacketNode n) {
        return new int[]{n.x + panX, n.y + panY + HEADER_H / 2};
    }

    private int[] outputPort(PacketNode n) {
        return new int[]{n.x + panX + widthOf(n), n.y + panY + HEADER_H / 2};
    }

    private void paintToolbar(Painter p) {
        int y = height - Theme.BUTTON_SHORT - Theme.INNER_MARGIN;
        int x = Theme.INNER_MARGIN;
        p.text("add:", x, y + 6, Theme.FOREGROUND_DISABLED);
        x += p.textWidth("add:") + 6;
        for (String a : KINDS) {
            int w = p.textWidth(a) + 10;
            boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + Theme.BUTTON_SHORT;
            p.fill(x, y, x + w, y + Theme.BUTTON_SHORT, hov ? Theme.BACKGROUND_HOVER : Theme.BACKGROUND_LIGHT);
            p.outline(x, y, x + w, y + Theme.BUTTON_SHORT, Theme.BUTTON_BORDER);
            p.text(a, x + 5, y + 6, Theme.THEME_LIGHTER);
            hit.put("add:" + a, new int[]{x, y, x + w, y + Theme.BUTTON_SHORT});
            x += w + 3;
        }
        button(p, width - Theme.BUTTON_LONG * 2 - Theme.INNER_MARGIN * 2, y, "Save");
        button(p, width - Theme.BUTTON_LONG - Theme.INNER_MARGIN, y, "Close");
        p.text(nodes.size() + " node" + (nodes.size() == 1 ? "" : "s")
                        + "  ·  drag title to move, drag a dot to wire, right-click to delete",
                Theme.INNER_MARGIN, Theme.INNER_MARGIN + 2, Theme.THEME_DARKER);
    }

    private void button(Painter p, int x, int y, String label) {
        int w = Theme.BUTTON_LONG, h = Theme.BUTTON_SHORT;
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        p.fill(x, y, x + w, y + h, hov ? Theme.BACKGROUND_HOVER : Theme.BACKGROUND_DEFAULT);
        p.outline(x, y, x + w, y + h, Theme.BUTTON_BORDER);
        p.text(label, x + w / 2 - p.textWidth(label) / 2, y + h / 2 - font.lineHeight / 2, Theme.THEME_LIGHTER);
        hit.put("btn:" + label, new int[]{x, y, x + w, y + h});
    }

    // ---- interaction --------------------------------------------------

    private PacketNode byId(String id) {
        for (PacketNode n : nodes) if (n.id.equals(id)) return n;
        return null;
    }

    private boolean in(String key, int mx, int my) {
        int[] b = hit.get(key);
        return b != null && mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3];
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        int mx = (int) event.x(), my = (int) event.y();
        if (editor != null && editor.mouseClicked(event, doubled)) return true;
        commitEditor();

        if (picking != null) {
            for (String name : PacketCatalog.names()) {
                if (in("pick:" + name, mx, my)) {
                    picking.packet = name;
                    picking = null;
                    return true;
                }
            }
            picking = null;   // clicking anywhere else closes it
            return true;
        }

        if (event.button() == 1) {
            for (PacketNode n : new ArrayList<>(nodes)) {
                if (in("body:" + n.id, mx, my)) {
                    nodes.remove(n);
                    for (PacketNode other : nodes) other.next.remove(n.id);
                    return true;
                }
            }
            return true;
        }

        if (in("btn:Save", mx, my)) { save(); return true; }
        if (in("btn:Close", mx, my)) { onClose(); return true; }
        for (String a : KINDS) {
            if (in("add:" + a, mx, my)) { add(PacketNode.Kind.valueOf(a.toUpperCase(java.util.Locale.ROOT))); return true; }
        }

        for (PacketNode n : nodes) {
            if (n.hasOutput() && in("out:" + n.id, mx, my)) { linkFrom = n; return true; }
        }
        if (linkFrom != null) {
            for (PacketNode n : nodes) {
                if (in("in:" + n.id, mx, my) && n != linkFrom) {
                    if (!linkFrom.next.contains(n.id)) linkFrom.next.add(n.id);
                    linkFrom = null;
                    return true;
                }
            }
            linkFrom = null;
            return true;
        }

        for (PacketNode n : nodes) {
            if (in("node:" + n.id, mx, my)) {
                dragging = n;
                dragDX = mx - (n.x + panX);
                dragDY = my - (n.y + panY);
                return true;
            }
            List<String[]> rows = rows(n);
            for (int i = 0; i < rows.size(); i++) {
                if (in("row:" + n.id + ":" + i, mx, my)) { editRow(n, i); return true; }
            }
        }

        panning = true;
        panStartX = mx;
        panStartY = my;
        panOriginX = panX;
        panOriginY = panY;
        return true;
    }

    private void add(PacketNode.Kind kind) {
        String id = kind.name().toLowerCase(java.util.Locale.ROOT) + "_" + (System.nanoTime() % 100000);
        nodes.add(new PacketNode(id, kind, -panX + 40 + nodes.size() % 4 * 24, -panY + 40 + nodes.size() % 6 * 20));
    }

    /** Clicking a row either cycles it or opens a text box, depending on the field. */
    private void editRow(PacketNode n, int row) {
        switch (n.kind) {
            case WHEN -> {
                picking = n;
                pickScroll = 0;
            }
            case IF -> {
                switch (row) {
                    case 0 -> n.field = nextFieldIndex(n);
                    case 1 -> {
                        PacketNode.Compare[] v = PacketNode.Compare.values();
                        n.compare = v[Math.floorMod(n.compare.ordinal() + 1, v.length)];
                    }
                    default -> openEditor(n, "2", n.value, "value");
                }
            }
            case SET, COPY -> openEditor(n, String.valueOf(row), n.set.getOrDefault(String.valueOf(row), ""), typeHint(n, row));
            default -> { }
        }
    }

    private int nextFieldIndex(PacketNode n) {
        PacketNode when = whenFor(n);
        int count = when == null ? 1 : PacketCatalog.fieldLabels(when.packet).size();
        return count == 0 ? 0 : Math.floorMod(n.field + 1, count);
    }

    private String typeHint(PacketNode n, int row) {
        PacketNode when = whenFor(n);
        if (when == null) return "value";
        PacketCatalog.Entry e = PacketCatalog.byName(when.packet);
        if (e == null) return "value";
        List<Field> fields = PacketRules.fields(e.type());
        return row < fields.size() ? PacketRules.typeLabel(fields.get(row).getType()) : "value";
    }

    private void openEditor(PacketNode n, String key, String value, String hint) {
        int[] b = hit.get("row:" + n.id + ":" + key);
        if (b == null) return;
        editNode = n;
        editKey = key;
        editor = new EditBox(this.font, b[0] + 4, b[1], b[2] - b[0] - 8, ROW_H, Component.literal(hint));
        editor.setMaxLength(128);
        editor.setValue(value);
        editor.setHint(Component.literal(hint));
        editor.setFocused(true);
        setFocused(editor);
        addRenderableWidget(editor);
    }

    private void commitEditor() {
        if (editor == null) return;
        String v = editor.getValue().trim();
        if (editNode != null && editKey != null) {
            if (editNode.kind == PacketNode.Kind.IF) {
                editNode.value = v;
            } else if (v.isEmpty()) {
                editNode.set.remove(editKey);
            } else {
                editNode.set.put(editKey, v);
            }
        }
        removeWidget(editor);
        editor = null;
        editNode = null;
        editKey = null;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (picking != null) {
            pickScroll = Math.max(0, pickScroll - (int) dy * 2);
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        int mx = (int) event.x(), my = (int) event.y();
        if (dragging != null) {
            dragging.x = mx - dragDX - panX;
            dragging.y = my - dragDY - panY;
            return true;
        }
        if (panning) {
            panX = panOriginX + (mx - panStartX);
            panY = panOriginY + (my - panStartY);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (dragging != null) {
            // settle onto the grid so graphs stay tidy
            dragging.x = Math.round(dragging.x / (float) GRID) * GRID;
            dragging.y = Math.round(dragging.y / (float) GRID) * GRID;
            dragging = null;
        }
        panning = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (editor != null && (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) {
            commitEditor();
            return true;
        }
        return super.keyPressed(event);
    }

    private void save() {
        commitEditor();
        config.packetNodes.clear();
        for (PacketNode n : nodes) config.packetNodes.add(n.copy());
        config.save();
        PrivacyFix.LOGGER.info("saved packet graph with {} node(s)", nodes.size());
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }
}
