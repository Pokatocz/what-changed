package com.pokato.whatchanged.c1212;

import com.pokato.whatchanged.core.CoreCompat;
import com.pokato.whatchanged.core.Names;
import com.pokato.whatchanged.core.WorldTracker;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** 1.21.2 - 1.21.4: RecipeHolder ids are ResourceKeys; ClickEvent(Action, String). */
public final class CoreCompat1212 implements CoreCompat {

    @Override
    public List<RecipeEntry> recipes(MinecraftServer server) {
        DynamicOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        Codec<Recipe<?>> codec = Recipe.CODEC;
        List<RecipeEntry> out = new ArrayList<>();
        for (RecipeHolder<?> h : new ArrayList<>(server.getRecipeManager().getRecipes())) {
            String id;
            try {
                id = Names.keyId(h.id());
            } catch (Throwable t) {
                continue;
            }
            Recipe<?> r = h.value();
            out.add(new RecipeEntry(id, r.getClass().getName(),
                    () -> WorldTracker.encodedJson(codec.encodeStart(ops, r).result().orElseThrow())));
        }
        return out;
    }

    @Override
    public void sendSystemMessage(ServerPlayer player, Component message) {
        player.sendSystemMessage(message);
    }

    @Override
    public MutableComponent withRunCommand(MutableComponent text, String command) {
        return text.withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }
}
