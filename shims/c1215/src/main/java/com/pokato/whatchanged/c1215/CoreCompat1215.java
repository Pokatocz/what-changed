package com.pokato.whatchanged.c1215;

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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** 1.21.5 - 26.x: ClickEvent.RunCommand; RecipeHolder ids are ResourceKeys; 26.3 moved the plain recipe codec to Recipe.DIRECT_CODEC. */
public final class CoreCompat1215 implements CoreCompat {

    @Override
    public List<RecipeEntry> recipes(MinecraftServer server) {
        DynamicOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        Codec<Recipe<?>> codec = recipeCodec();
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
        return text.withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(command)));
    }

    /** Recipe.CODEC encodes a Recipe up to 26.2; 26.3 turned CODEC into a holder codec and added DIRECT_CODEC. */
    @SuppressWarnings("unchecked")
    private static Codec<Recipe<?>> recipeCodec() {
        try {
            Field f = Recipe.class.getField("DIRECT_CODEC");
            if (Modifier.isStatic(f.getModifiers())) return (Codec<Recipe<?>>) f.get(null);
        } catch (Throwable ignored) {
        }
        return Recipe.CODEC;
    }
}
