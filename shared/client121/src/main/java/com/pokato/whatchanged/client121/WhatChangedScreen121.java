package com.pokato.whatchanged.client121;

import com.pokato.whatchanged.client.AbstractWhatChangedScreen;
import com.pokato.whatchanged.client.Painter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.pokato.whatchanged.client.Icons;
import java.util.List;

public final class WhatChangedScreen121 extends AbstractWhatChangedScreen {
    public WhatChangedScreen121(Screen parent) {
        super(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        paint(new GraphicsPainter(g, this.font), mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return scrollList(verticalAmount);
    }

    private record GraphicsPainter(GuiGraphics g, Font font) implements Painter {
        @Override
        public void text(String text, int x, int y, int argb) {
            g.drawString(font, text, x, y, argb);
        }

        @Override
        public void centered(Component text, int centerX, int y, int argb) {
            g.drawCenteredString(font, text, centerX, y, argb);
        }

        @Override
        public void fill(int x1, int y1, int x2, int y2, int argb) {
            g.fill(x1, y1, x2, y2, argb);
        }

        @Override
        public void scissor(int x1, int y1, int x2, int y2) {
            g.enableScissor(x1, y1, x2, y2);
        }

        @Override
        public void endScissor() {
            g.disableScissor();
        }

        // [recovered 1.3.0: decompiled from the release jar, original comments lost]
        @Override
        public boolean drawsItemsLater() {
            return true;
        }

        // [recovered 1.3.0: decompiled from the release jar, original comments lost]
        @Override
        public boolean item(ItemStack stack, int x, int y) {
            try {
                this.g.renderItem(stack, x, y);
                return true;
            } catch (Throwable error) {
                Icons.failed(stack.getItem(), error);
                return false;
            }
        }

        @Override
        public void tooltip(java.util.List<Component> lines, int mouseX, int mouseY) {
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }
}
