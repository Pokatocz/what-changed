package com.pokato.whatchanged.g120;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.pokato.whatchanged.client.Icons;
import com.pokato.whatchanged.client.AbstractWhatChangedScreen;
import com.pokato.whatchanged.client.Painter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** 1.20 - 1.20.1: Screen.render does not draw the background yet, and mouseScrolled has one amount. */
public final class WhatChangedScreen120 extends AbstractWhatChangedScreen {
    public WhatChangedScreen120(Screen parent) {
        super(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, delta);
        paint(new ImmediatePainter(g, this.font), mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scrollList(amount);
    }

    /** Immediate-mode GuiGraphics (1.20 - 1.21.5). */
    record ImmediatePainter(GuiGraphics g, Font font) implements Painter {
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

        @Override
        // [recovered 1.3.0: decompiled from the release jar, original comments lost]
        public boolean item(ItemStack stack, int x, int y) {
            PoseStack pose = this.g.pose();
            Pose top = pose.last();

            try {
                this.g.renderItem(stack, x, y);
                return true;
            } catch (Throwable error) {
                try {
                    for (int i = 0; i < 32 && pose.last() != top; i++) {
                        pose.popPose();
                    }

                    this.g.flush();
                } catch (Throwable ignored) {
                }

                Icons.failed(stack.getItem(), error);
                return false;
            }
        }

        @Override
        public void tooltip(List<Component> lines, int mouseX, int mouseY) {
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }
}
