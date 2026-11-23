package com.pokato.whatchanged.core;

import com.pokato.whatchanged.platform.Versions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.Supplier;

/**
 * Logical-server APIs that changed between 1.20 and 26.x: the recipe list and how a recipe is serialized,
 * and click events. One implementation per API generation, each compiled against its own Minecraft version:
 * <pre>
 *   c120   1.20 - 1.20.1   Recipe.getId(), hashed via the network serializer
 *   c1202  1.20.2 - 1.20.4 RecipeHolder (ResourceLocation id), network serializer
 *   c1205  1.20.5 - 1.21.1 RecipeHolder, Recipe.CODEC with registry ops
 *   c1212  1.21.2 - 1.21.4 RecipeHolder with a ResourceKey id
 *   c1215  1.21.5 - 26.x   ClickEvent.RunCommand (and Recipe.DIRECT_CODEC on 26.3+)
 * </pre>
 */
public interface CoreCompat {
    /** One recipe; {@code encode} may run off the server thread. */
    record RecipeEntry(String id, String type, Supplier<Encoded> encode) {}

    /** A stable byte form of the recipe (for hashing) and its output item id when known. */
    record Encoded(byte[] canonical, String output) {}

    /** Server thread: the current recipe list. */
    List<RecipeEntry> recipes(MinecraftServer server);

    /** ServerPlayer.sendSystemMessage: inherited from CommandSource up to 1.21.1, declared on ServerPlayer from 1.21.2. */
    void sendSystemMessage(ServerPlayer player, Component message);

    /** Adds a "run this command" click action to the text. */
    MutableComponent withRunCommand(MutableComponent text, String command);

    static CoreCompat get() {
        CoreCompat c = Holder.instance;
        if (c == null) {
            synchronized (Holder.class) {
                if (Holder.instance == null) {
                    Holder.instance = Versions.pick(CoreCompat.class,
                            "1.20", "com.pokato.whatchanged.c120.CoreCompat120",
                            "1.20.2", "com.pokato.whatchanged.c1202.CoreCompat1202",
                            "1.20.5", "com.pokato.whatchanged.c1205.CoreCompat1205",
                            "1.21.2", "com.pokato.whatchanged.c1212.CoreCompat1212",
                            "1.21.5", "com.pokato.whatchanged.c1215.CoreCompat1215");
                }
                c = Holder.instance;
            }
        }
        return c;
    }

    final class Holder {
        private static volatile CoreCompat instance;

        private Holder() {}
    }
}
