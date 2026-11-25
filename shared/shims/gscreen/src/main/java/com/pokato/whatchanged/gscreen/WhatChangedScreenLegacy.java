package com.pokato.whatchanged.gscreen;

import com.pokato.whatchanged.client.AbstractWhatChangedScreen;
import com.pokato.whatchanged.client.Painter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.pokato.whatchanged.client.Icons;

/**
 * 1.20.2 - 1.21.5: Screen.render draws the background, mouseScrolled has horizontal + vertical amounts and
 * GuiGraphics still renders immediately (drawString returns int, renderComponentTooltip).
 * Used by the 1.20.2, 1.20.3 - 1.21.1 and 1.21.2 - 1.21.5 client layers.
 */
public final class WhatChangedScreenLegacy extends AbstractWhatChangedScreen {
    public WhatChangedScreenLegacy(Screen parent) {
        super(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        paint(new ImmediatePainter(g, this.font), mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return scrollList(verticalAmount);
    }

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

        // [recovered 1.3.0: decompiled from the release jar, original comments lost]
        @Override
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
