package com.pokato.whatchanged.c1202;

import com.pokato.whatchanged.core.CoreCompat;
import com.pokato.whatchanged.core.Names;
import com.pokato.whatchanged.core.WorldTracker;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.nio.charset.StandardCharsets;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** 1.20.2 - 1.20.4: RecipeHolder with a ResourceLocation id; recipes hashed via their network serializer. */
public final class CoreCompat1202 implements CoreCompat {

    @Override
    public List<RecipeEntry> recipes(MinecraftServer server) {
        RegistryAccess access = server.registryAccess();
        List<RecipeEntry> out = new ArrayList<>();
        for (RecipeHolder<?> h : new ArrayList<>(server.getRecipeManager().getRecipes())) {
            String id;
            try {
                id = String.valueOf(h.id());
            } catch (Throwable t) {
                continue;
            }
            Recipe<?> r = h.value();
            out.add(new RecipeEntry(id, r.getClass().getName(), () -> encode(r, access)));
        }
        return out;
    }

    /**
     * There is no recipe codec yet: the bytes the server sends to clients (serializer.toNetwork) are a complete,
     * stable description of the recipe. The serializer id goes first so a type change also counts.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Encoded encode(Recipe<?> recipe, RegistryAccess access) {
        RecipeSerializer ser = recipe.getSerializer();
        ByteBuf raw = Unpooled.buffer();
        try {
            FriendlyByteBuf buf = new FriendlyByteBuf(raw);
            buf.writeUtf(String.valueOf(BuiltInRegistries.RECIPE_SERIALIZER.getKey(ser)));
            ser.toNetwork(buf, recipe);
            byte[] bytes = new byte[raw.readableBytes()];
            raw.getBytes(raw.readerIndex(), bytes);
            String output = null;
            try {
                ItemStack result = recipe.getResultItem(access);
                if (result != null && !result.isEmpty()) output = Names.itemId(result.getItem());
            } catch (Throwable ignored) {
            }
            return new Encoded(bytes, output);
        } finally {
            raw.release();
        }
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
