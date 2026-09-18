package dev.codex.helditemtweaker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;

import java.util.ArrayList;
import java.util.List;

public final class HeldItemTweaker implements ModInitializer {
    public static final String MOD_ID = "helditemtweaker";

    public HeldItemTweaker() {
    }

    @Override
    public void onInitialize() {
        init();
    }

    public static void init() {
        PayloadTypeRegistry.playC2S().register(ApplyPayload.TYPE, ApplyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DuplicatePayload.TYPE, DuplicatePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ApplyPayload.TYPE, (payload, context) ->
                context.server().execute(() -> apply(context.player(), payload.entries(), payload.customName(),
                        payload.lore(), payload.damage(), payload.unbreakable(),
                        payload.trimMaterial(), payload.trimPattern(),
                        payload.dyeColor(), payload.attributeModifiers())));
        ServerPlayNetworking.registerGlobalReceiver(DuplicatePayload.TYPE, (payload, context) ->
                context.server().execute(() -> duplicate(context.player())));
    }

    private static void apply(ServerPlayer player, String encoded, String customName, String lore,
                              int damage, boolean unbreakable,
                              String trimMaterialId, String trimPatternId,
                              int dyeColor, String attributeModifiers) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        // --- Enchantments ---
        List<String> entries = encoded.isBlank() ? List.of() : List.of(encoded.split(";"));
        EnchantmentHelper.updateEnchantments(stack, mutable -> {
            mutable.removeIf(holder -> true);
            for (String entry : entries) {
                String[] parts = entry.split("=", 2);
                if (parts.length != 2) continue;
                Identifier id = Identifier.tryParse(parts[0]);
                if (id == null) continue;
                int level;
                try {
                    level = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                Holder<Enchantment> holder = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(id).orElse(null);
                if (holder == null) continue;
                Enchantment enchantment = holder.value();
                if (!enchantment.canEnchant(stack)) continue;
                if (level < enchantment.getMinLevel() || level > enchantment.getMaxLevel()) continue;
                mutable.set(holder, level);
            }
        });

        // --- Custom Name ---
        if (customName.isBlank()) {
            stack.remove(DataComponents.CUSTOM_NAME);
        } else {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(customName));
        }

        // --- Lore ---
        if (lore.isBlank()) {
            stack.remove(DataComponents.LORE);
        } else {
            List<Component> lines = List.of(lore.split("\\|", -1)).stream()
                    .limit(ItemLore.MAX_LINES)
                    .map(line -> (Component) Component.literal(line))
                    .toList();
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }

        // --- Damage / Durability ---
        if (stack.isDamageableItem()) {
            if (damage >= 0 && damage <= stack.getMaxDamage()) {
                stack.setDamageValue(damage);
            }
            if (unbreakable) {
                stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            } else {
                stack.remove(DataComponents.UNBREAKABLE);
            }
        }

        // --- Armor Trims (trimmable armor OR equippable) ---
        boolean trimCapable = stack.is(ItemTags.TRIMMABLE_ARMOR) || stack.has(DataComponents.TRIM);
        if (trimCapable) {
            if (trimMaterialId.isBlank() || trimPatternId.isBlank()) {
                stack.remove(DataComponents.TRIM);
            } else {
                Identifier materialId = Identifier.tryParse(trimMaterialId);
                Identifier patternId = Identifier.tryParse(trimPatternId);
                if (materialId != null && patternId != null) {
                    Holder<TrimMaterial> material = player.registryAccess().lookupOrThrow(
                            Registries.TRIM_MATERIAL).get(materialId).orElse(null);
                    Holder<TrimPattern> pattern = player.registryAccess().lookupOrThrow(
                            Registries.TRIM_PATTERN).get(patternId).orElse(null);
                    if (material != null && pattern != null) {
                        stack.set(DataComponents.TRIM, new ArmorTrim(material, pattern));
                    }
                }
            }
        }

        // --- Dye Color ---
        boolean dyeCapable = stack.is(ItemTags.DYEABLE) || stack.has(DataComponents.DYED_COLOR);
        if (dyeCapable) {
            if (dyeColor < 0) {
                stack.remove(DataComponents.DYED_COLOR);
            } else {
                stack.set(DataComponents.DYED_COLOR, new DyedItemColor(dyeColor));
            }
        }

        // --- Attribute Modifiers ---
        // Format: "attribute_id=slot=operation=amount;..."
        // Example: "minecraft:generic.attack_damage=mainhand=add_value=9.0"
        if (!attributeModifiers.isBlank()) {
            ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
            for (String entry : attributeModifiers.split(";")) {
                String[] parts = entry.split("=", 4);
                if (parts.length != 4) continue;
                Identifier attrId = Identifier.tryParse(parts[0]);
                if (attrId == null) continue;
                EquipmentSlotGroup slotGroup = parseSlotGroup(parts[1]);
                AttributeModifier.Operation operation = parseOperation(parts[2]);
                double amount;
                try {
                    amount = Double.parseDouble(parts[3]);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                if (slotGroup == null || operation == null) continue;
                Holder<net.minecraft.world.entity.ai.attributes.Attribute> attrHolder =
                        player.registryAccess().lookupOrThrow(Registries.ATTRIBUTE).get(attrId).orElse(null);
                if (attrHolder == null) continue;
                Identifier modifierId = Identifier.fromNamespaceAndPath(MOD_ID, "modifier." + attrId.getPath());
                builder.add(attrHolder, new AttributeModifier(modifierId, amount, operation), slotGroup);
            }
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
        }

        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }

    private static EquipmentSlotGroup parseSlotGroup(String s) {
        return switch (s) {
            case "any" -> EquipmentSlotGroup.ANY;
            case "mainhand" -> EquipmentSlotGroup.MAINHAND;
            case "offhand" -> EquipmentSlotGroup.OFFHAND;
            case "hand" -> EquipmentSlotGroup.HAND;
            case "feet" -> EquipmentSlotGroup.FEET;
            case "legs" -> EquipmentSlotGroup.LEGS;
            case "chest" -> EquipmentSlotGroup.CHEST;
            case "head" -> EquipmentSlotGroup.HEAD;
            case "armor" -> EquipmentSlotGroup.ARMOR;
            default -> null;
        };
    }

    private static AttributeModifier.Operation parseOperation(String s) {
        return switch (s) {
            case "add_value" -> AttributeModifier.Operation.ADD_VALUE;
            case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> null;
        };
    }

    private static void duplicate(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return;
        ItemStack copy = held.copy();
        if (!player.getInventory().add(copy)) {
            player.drop(copy, false);
        }
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }

    public record ApplyPayload(String entries, String customName, String lore, int damage,
                               boolean unbreakable, String trimMaterial, String trimPattern,
                               int dyeColor, String attributeModifiers) implements CustomPacketPayload {
        public static final Type<ApplyPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "apply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ApplyPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ApplyPayload::entries,
                ByteBufCodecs.STRING_UTF8, ApplyPayload::customName,
                ByteBufCodecs.STRING_UTF8, ApplyPayload::lore,
                ByteBufCodecs.VAR_INT, ApplyPayload::damage,
                ByteBufCodecs.BOOL, ApplyPayload::unbreakable,
                ByteBufCodecs.STRING_UTF8, ApplyPayload::trimMaterial,
                ByteBufCodecs.STRING_UTF8, ApplyPayload::trimPattern,
                ByteBufCodecs.VAR_INT, ApplyPayload::dyeColor,
                ByteBufCodecs.STRING_UTF8, ApplyPayload::attributeModifiers,
                ApplyPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DuplicatePayload() implements CustomPacketPayload {
        public static final Type<DuplicatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "duplicate"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DuplicatePayload> CODEC = StreamCodec.unit(new DuplicatePayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
