package com.pokato.whatchanged.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** The handful of drawing calls the screen needs; implemented on GuiGraphics (1.21.x) and GuiGraphicsExtractor (26.x). */
public interface Painter {
    void text(String text, int x, int y, int argb);

    void centered(Component text, int centerX, int y, int argb);

    void fill(int x1, int y1, int x2, int y2, int argb);

    void scissor(int x1, int y1, int x2, int y2);

    void endScissor();

    // [recovered 1.3.0: decompiled from the release jar, original comments lost]
    boolean item(ItemStack var1, int var2, int var3);

    // [recovered 1.3.0: decompiled from the release jar, original comments lost]
    default boolean drawsItemsLater() {
        return false;
    }

    void tooltip(java.util.List<Component> lines, int mouseX, int mouseY);
}
