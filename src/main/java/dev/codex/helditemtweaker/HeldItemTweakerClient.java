package dev.codex.helditemtweaker;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.EquipmentSlot;
import org.lwjgl.glfw.GLFW;

import dev.codex.helditemtweaker.mixin.AbstractContainerScreenAccessor;

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
        if (client.player == null || client.screen != null) return;
        String target = firstNonEmptyTarget(client);
        client.setScreen(new HeldItemScreen(target == null ? HeldItemTweaker.TARGET_MAIN_HAND : target));
    }

    private static void duplicate(Minecraft client) {
        if (client.player == null) return;
        String target = duplicateTarget(client);
        if (target == null) return;
        ClientPlayNetworking.send(new HeldItemTweaker.DuplicatePayload(target));
    }

    private static String duplicateTarget(Minecraft client) {
        if (!client.player.containerMenu.getCarried().isEmpty()) {
            return HeldItemTweaker.TARGET_CURSOR;
        }

        if (client.screen instanceof InventoryScreen inventoryScreen
                && client.player.containerMenu instanceof InventoryMenu) {
            Slot slot = ((AbstractContainerScreenAccessor) inventoryScreen).helditemtweaker$getHoveredSlot();
            if (slot != null && slot.container == client.player.getInventory() && !slot.getItem().isEmpty()) {
                return HeldItemTweaker.inventoryTarget(slot.getContainerSlot());
            }
        }

        if (client.screen == null && !client.player.getMainHandItem().isEmpty()) {
            return HeldItemTweaker.TARGET_MAIN_HAND;
        }
        return null;
    }

    private static String firstNonEmptyTarget(Minecraft client) {
        if (!client.player.getMainHandItem().isEmpty()) return HeldItemTweaker.TARGET_MAIN_HAND;
        if (!client.player.getOffhandItem().isEmpty()) return HeldItemTweaker.TARGET_OFF_HAND;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!client.player.getItemBySlot(slot).isEmpty()) {
                return switch (slot) {
                    case HEAD -> HeldItemTweaker.TARGET_ARMOR_HEAD;
                    case CHEST -> HeldItemTweaker.TARGET_ARMOR_CHEST;
                    case LEGS -> HeldItemTweaker.TARGET_ARMOR_LEGS;
                    case FEET -> HeldItemTweaker.TARGET_ARMOR_FEET;
                    default -> null;
                };
            }
        }
        int selected = client.player.getInventory().getSelectedSlot();
        for (int index = 0; index < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE; index++) {
            if (index == selected || client.player.getInventory().getItem(index).isEmpty()) continue;
            return HeldItemTweaker.inventoryTarget(index);
        }
        return TrinketsCompat.list(client.player).stream().findFirst().map(TrinketsCompat.Slot::id).orElse(null);
    }
}
