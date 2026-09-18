package dev.codex.helditemtweaker;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public final class HeldItemTweakerClient implements ClientModInitializer {
    private static KeyMapping openKey;
    private static KeyMapping duplicateKey;
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(HeldItemTweaker.MOD_ID, "keys"));

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.helditemtweaker.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J,
                CATEGORY));
        duplicateKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.helditemtweaker.duplicate", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL,
                CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) open(client);
            while (duplicateKey.consumeClick()) duplicate(client);
        });
    }

    private static void open(Minecraft client) {
        if (client.player == null || client.screen != null || client.player.getMainHandItem().isEmpty()) {
            if (client.player != null && client.player.getMainHandItem().isEmpty()) {
                client.player.displayClientMessage(Component.literal("Hold an item first."), true);
            }
            return;
        }
        client.setScreen(new HeldItemScreen());
    }

    private static void duplicate(Minecraft client) {
        if (client.player == null || client.screen != null || client.player.getMainHandItem().isEmpty()) return;
        ClientPlayNetworking.send(new HeldItemTweaker.DuplicatePayload());
    }
}
