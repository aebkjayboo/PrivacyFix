package dev.sai.privacyfix.compat;

import dev.sai.privacyfix.gui.Painter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** 26.2: GuiGraphicsExtractor render-state pipeline. Custom drawing happens between the background and the widgets. */
public abstract class CompatScreen extends Screen {
    protected CompatScreen(Component title) {
        super(title);
    }

    protected abstract void paint(Painter p, int mouseX, int mouseY);

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        paint(new Painter() {
            @Override public void fill(int x1, int y1, int x2, int y2, int argb) { g.fill(x1, y1, x2, y2, argb); }
            @Override public void text(String s, int x, int y, int argb) { g.text(font, s, x, y, argb); }
            @Override public int textWidth(String s) { return font.width(s); }
            @Override public void texture(Identifier tex, int x, int y, int w, int h, int tint) {
                g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0.0F, 0.0F, w, h, w, h, tint);
            }
        }, mouseX, mouseY);
    }
}
