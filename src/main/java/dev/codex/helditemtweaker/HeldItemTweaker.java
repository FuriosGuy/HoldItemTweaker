package dev.codex.helditemtweaker;

import io.netty.buffer.ByteBuf;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.advancements.criterion.BlockPredicate;
import net.minecraft.advancements.criterion.DataComponentMatchers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class HeldItemTweaker implements ModInitializer {
    public static final String MOD_ID = "helditemtweaker";
    public static final String TARGET_MAIN_HAND = "main_hand";
    public static final String TARGET_OFF_HAND = "off_hand";
    public static final String TARGET_ARMOR_HEAD = "armor_head";
    public static final String TARGET_ARMOR_CHEST = "armor_chest";
    public static final String TARGET_ARMOR_LEGS = "armor_legs";
    public static final String TARGET_ARMOR_FEET = "armor_feet";
    public static final String TARGET_CURSOR = "cursor";
    /** Presence/value of this component makes the item behave as an infinite-use item. */
    public static final DataComponentType<Boolean> INFINITE_USE = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(MOD_ID, "infinite_use"),
            DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());
    private static final String TARGET_INVENTORY_PREFIX = "inventory:";

    public static String inventoryTarget(int index) {
        return TARGET_INVENTORY_PREFIX + index;
    }

    public static boolean isInfiniteUse(ItemStack stack) {
        return stack != null && stack.getOrDefault(INFINITE_USE, false);
    }
    private static final double MAX_ATTRIBUTE_AMOUNT = 1024.0D;
    public HeldItemTweaker() {
    }

    @Override
    public void onInitialize() {
        init();
    }

    public static void init() {
        PayloadTypeRegistry.playC2S().register(ApplyPayload.TYPE, ApplyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DuplicatePayload.TYPE, DuplicatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DeletePayload.TYPE, DeletePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PlayerEditPayload.TYPE, PlayerEditPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ItemVariantPayload.TYPE, ItemVariantPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ApplyPayload.TYPE, (payload, context) ->
                context.server().execute(() -> apply(context.player(), payload.target(), payload.entries(), payload.customName(),
                        payload.lore(), payload.damage(), payload.unbreakable(),
                        payload.trimMaterial(), payload.trimPattern(),
                        payload.dyeColor(), payload.attributeModifiers(),
                        payload.glintOverride(), payload.rarity(),
                        payload.customModelData(), payload.tooltipDisplay(),
                        payload.canPlaceOn(), payload.canBreak(),
                        payload.bannerBaseColor(), payload.bannerPatterns(),
                        payload.specialData(), payload.infiniteUse())));
        ServerPlayNetworking.registerGlobalReceiver(DuplicatePayload.TYPE, (payload, context) ->
                context.server().execute(() -> duplicate(context.player(), payload.target())));
        ServerPlayNetworking.registerGlobalReceiver(DeletePayload.TYPE, (payload, context) ->
                context.server().execute(() -> delete(context.player(), payload.target())));
        ServerPlayNetworking.registerGlobalReceiver(PlayerEditPayload.TYPE, (payload, context) ->
                context.server().execute(() -> editPlayer(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ItemVariantPayload.TYPE, (payload, context) ->
                context.server().execute(() -> changeVariant(context.player(), payload.target(), payload.itemId())));
    }

    private static void changeVariant(ServerPlayer player, String target, String itemId) {
        if (target == null || target.length() > 128 || itemId == null || itemId.length() > 256) return;
        ItemStack source = TARGET_CURSOR.equals(target) ? player.containerMenu.getCarried() : resolveTarget(player, target);
        if (source.isEmpty()) return;
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return;
        var holder = player.registryAccess().lookupOrThrow(Registries.ITEM).get(id).orElse(null);
        if (holder == null) return;
        ItemStack candidate = new ItemStack(holder, 1);
        if (!ItemVariantRules.isRelated(source, candidate)) return;
        int count = Math.min(source.getCount(), candidate.getMaxStackSize());
        ItemStack changed = source.transmuteCopy(holder.value(), Math.max(1, count));
        if (TARGET_CURSOR.equals(target)) {
            player.containerMenu.setCarried(changed);
        } else if (target.startsWith(TARGET_INVENTORY_PREFIX)) {
            try {
                int index = Integer.parseInt(target.substring(TARGET_INVENTORY_PREFIX.length()));
                if (index >= 0 && index < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE) {
                    player.getInventory().setItem(index, changed);
                }
            } catch (NumberFormatException ignored) {
                return;
            }
        } else if (target.startsWith("trinket:")) {
            TrinketsCompat.set(player, target, changed);
        } else if (target.startsWith("accessory:")) {
            AccessoriesCompat.set(player, target, changed);
        } else {
            EquipmentSlot slot = switch (target) {
                case TARGET_MAIN_HAND -> EquipmentSlot.MAINHAND;
                case TARGET_OFF_HAND -> EquipmentSlot.OFFHAND;
                case TARGET_ARMOR_HEAD -> EquipmentSlot.HEAD;
                case TARGET_ARMOR_CHEST -> EquipmentSlot.CHEST;
                case TARGET_ARMOR_LEGS -> EquipmentSlot.LEGS;
                case TARGET_ARMOR_FEET -> EquipmentSlot.FEET;
                default -> null;
            };
            if (slot == null) return;
            player.setItemSlot(slot, changed);
        }
        TrinketsCompat.markUpdated(player, target);
        AccessoriesCompat.markUpdated(player, target);
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }

    private static void delete(ServerPlayer player, String target) {
        if (target == null || target.length() > 128 || player.isSpectator()) return;
        boolean changed = false;
        if (TARGET_CURSOR.equals(target)) {
            if (!player.containerMenu.getCarried().isEmpty()) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
                changed = true;
            }
        } else if (TARGET_MAIN_HAND.equals(target)) {
            changed = clearEquipment(player, EquipmentSlot.MAINHAND);
        } else if (TARGET_OFF_HAND.equals(target)) {
            changed = clearEquipment(player, EquipmentSlot.OFFHAND);
        } else if (TARGET_ARMOR_HEAD.equals(target)) {
            changed = clearEquipment(player, EquipmentSlot.HEAD);
        } else if (TARGET_ARMOR_CHEST.equals(target)) {
            changed = clearEquipment(player, EquipmentSlot.CHEST);
        } else if (TARGET_ARMOR_LEGS.equals(target)) {
            changed = clearEquipment(player, EquipmentSlot.LEGS);
        } else if (TARGET_ARMOR_FEET.equals(target)) {
            changed = clearEquipment(player, EquipmentSlot.FEET);
        } else if (target.startsWith(TARGET_INVENTORY_PREFIX)) {
            try {
                int index = Integer.parseInt(target.substring(TARGET_INVENTORY_PREFIX.length()));
                if (index >= 0 && index < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE
                        && !player.getInventory().getItem(index).isEmpty()) {
                    player.getInventory().setItem(index, ItemStack.EMPTY);
                    changed = true;
                }
            } catch (NumberFormatException ignored) {
                return;
            }
        } else if (target.startsWith("trinket:")) {
            changed = TrinketsCompat.clear(player, target);
        } else if (target.startsWith("accessory:")) {
            changed = AccessoriesCompat.clear(player, target);
        }
        if (changed) {
            TrinketsCompat.markUpdated(player, target);
            AccessoriesCompat.markUpdated(player, target);
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static boolean clearEquipment(ServerPlayer player, EquipmentSlot slot) {
        if (player.getItemBySlot(slot).isEmpty()) return false;
        player.setItemSlot(slot, ItemStack.EMPTY);
        return true;
    }

    private static void editPlayer(ServerPlayer player, PlayerEditPayload payload) {
        if (payload.effects().length() > 8192) return;

        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && finite(payload.maxHealth()) && payload.maxHealth() >= 1.0 && payload.maxHealth() <= 1024.0) {
            maxHealth.setBaseValue(payload.maxHealth());
        }
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null && finite(payload.movementSpeed())
                && payload.movementSpeed() >= 0.0 && payload.movementSpeed() <= 10.0) {
            movementSpeed.setBaseValue(payload.movementSpeed());
        }

        double max = maxHealth == null ? player.getMaxHealth() : maxHealth.getValue();
        if (finite(payload.health()) && payload.health() >= 0.0 && payload.health() <= max) {
            player.setHealth((float) payload.health());
        }
        if (payload.food() >= 0 && payload.food() <= 20) {
            player.getFoodData().setFoodLevel(payload.food());
        }
        if (finite(payload.saturation()) && payload.saturation() >= 0.0 && payload.saturation() <= 20.0) {
            player.getFoodData().setSaturation(Math.min(payload.saturation(), player.getFoodData().getFoodLevel()));
        }
        if (payload.level() >= 0 && payload.level() <= 100000) {
            player.experienceLevel = payload.level();
            player.totalExperience = Math.max(0, player.totalExperience);
            player.experienceProgress = finite(player.experienceProgress)
                    ? Math.max(0.0F, Math.min(0.999F, player.experienceProgress)) : 0.0F;
        }

        applyEffects(player, payload.effects());
        ToughAsNailsCompat.apply(player, payload.tanThirst(), payload.tanHydration(),
                payload.tanExhaustion(), payload.tanTemperature());
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    private static boolean finite(float value) {
        return Float.isFinite(value);
    }

    private static void applyEffects(ServerPlayer player, String encoded) {
        if (encoded == null || encoded.isBlank()) {
            player.removeAllEffects();
            return;
        }
        var registry = player.registryAccess().lookupOrThrow(Registries.MOB_EFFECT);
        List<MobEffectInstance> parsed = new ArrayList<>();
        for (String raw : encoded.split(";")) {
            String[] entry = raw.split("=", 2);
            if (entry.length != 2) return;
            Identifier id = Identifier.tryParse(entry[0]);
            Holder<MobEffect> effect = id == null ? null : registry.get(id).orElse(null);
            if (effect == null) return;
            String[] values = entry[1].split(",", -1);
            if (values.length < 2 || values.length > 5) return;
            try {
                int amplifier = Integer.parseInt(values[0]);
                int duration = Integer.parseInt(values[1]);
                boolean ambient = values.length > 2 && Boolean.parseBoolean(values[2]);
                boolean visible = values.length <= 3 || Boolean.parseBoolean(values[3]);
                boolean icon = values.length <= 4 || Boolean.parseBoolean(values[4]);
                if (amplifier < 0 || amplifier > 255 || (duration != -1 && (duration < 1 || duration > 2_147_483_647))) return;
                parsed.add(new MobEffectInstance(effect, duration, amplifier, ambient, visible, icon));
            } catch (NumberFormatException ignored) {
                return;
            }
        }
        player.removeAllEffects();
        parsed.forEach(player::addEffect);
    }

    private static void apply(ServerPlayer player, String target, String encoded, String customName, String lore,
                              int damage, boolean unbreakable,
                              String trimMaterialId, String trimPatternId,
                              int dyeColor, String attributeModifiers,
                              int glintOverride, String rarity,
                              String customModelData, String tooltipDisplay,
                              String canPlaceOn, String canBreak,
                              String bannerBaseColor, String bannerPatterns,
                              String specialData, boolean infiniteUse) {
        ItemStack stack = resolveTarget(player, target);
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

        // Infinite Infinity is component-driven.  The Infinity enchantment is not
        // required and is removed when this mode is enabled, so normal bow Infinity
        // remains untouched unless the player explicitly enables this mode.
        if (infiniteUse) {
            Holder<Enchantment> infinity = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .get(Identifier.fromNamespaceAndPath("minecraft", "infinity")).orElse(null);
            if (infinity != null) {
                EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.removeIf(holder -> holder.equals(infinity)));
            }
            stack.set(INFINITE_USE, true);
        } else {
            stack.remove(INFINITE_USE);
        }

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
        }
        if (unbreakable) {
            stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        } else {
            stack.remove(DataComponents.UNBREAKABLE);
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
        // Only explicitly sent, supported attributes are changed. All other entries stay intact.
        applyAttributeModifiers(player, stack, attributeModifiers);

        // --- Simple Data Components ---
        applyGlintOverride(stack, glintOverride);
        applyRarity(stack, rarity);
        applyCustomModelData(stack, customModelData);
        applyTooltipDisplay(stack, tooltipDisplay);
        applyAdventurePredicate(player, stack, DataComponents.CAN_PLACE_ON, canPlaceOn);
        applyAdventurePredicate(player, stack, DataComponents.CAN_BREAK, canBreak);
        applyBanner(player, stack, bannerBaseColor, bannerPatterns);
        applySpecial(player, stack, specialData);
        TrinketsCompat.markUpdated(player, target);
        AccessoriesCompat.markUpdated(player, target);

        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * -2 is the legacy-payload "not supplied" sentinel.  The network/API
     * contract for new callers is -1 = no override, 0 = false, 1 = true.
     */
    private static void applyGlintOverride(ItemStack stack, int value) {
        if (value == -2) return;
        if (value == -1) {
            stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        } else if (value == 0 || value == 1) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, value == 1);
        }
    }

    private static void applyRarity(ItemStack stack, String encoded) {
        if (encoded == null || encoded.isBlank()) return;
        if (encoded.equalsIgnoreCase("clear")) {
            stack.remove(DataComponents.RARITY);
            return;
        }

        Rarity rarity = null;
        try {
            int id = Integer.parseInt(encoded);
            if (id >= 0 && id < Rarity.values().length) rarity = Rarity.BY_ID.apply(id);
        } catch (NumberFormatException ignored) {
            for (Rarity candidate : Rarity.values()) {
                if (candidate.name().equalsIgnoreCase(encoded)
                        || candidate.getSerializedName().equalsIgnoreCase(encoded)) {
                    rarity = candidate;
                    break;
                }
            }
        }
        if (rarity != null) stack.set(DataComponents.RARITY, rarity);
    }

    /**
     * Format: blank = leave unchanged, clear = remove, int:<0..16777215>,
     * or string:<UTF-8 text up to 256 chars>.  The integer bound avoids
     * precision loss when the value is represented by the component's float
     * list and matches the practical exact-in-float model-data range.
     */
    private static void applyCustomModelData(ItemStack stack, String encoded) {
        if (encoded == null || encoded.isBlank()) return;
        if (encoded.equalsIgnoreCase("clear")) {
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
            return;
        }

        // Current editor format preserves all four CustomModelData lists. Keep
        // legacy single-value formats below for older clients.
        if (encoded.regionMatches(true, 0, "data:", 0, 5)) {
            if (encoded.length() > 8192) return;
            List<Float> floats = new ArrayList<>();
            List<Boolean> flags = new ArrayList<>();
            List<String> strings = new ArrayList<>();
            List<Integer> colors = new ArrayList<>();
            for (String section : encoded.substring(5).split("\\|", -1)) {
                String[] pair = section.split("=", 2);
                if (pair.length != 2) return;
                String[] values = pair[1].isBlank() ? new String[0] : pair[1].split(",", -1);
                switch (pair[0].toLowerCase(java.util.Locale.ROOT)) {
                    case "floats" -> {
                        if (values.length > 64) return;
                        for (String value : values) {
                            Float parsed = boundedFloat(value, -1_000_000.0F, 1_000_000.0F);
                            if (parsed == null) return;
                            floats.add(parsed);
                        }
                    }
                    case "flags" -> {
                        if (values.length > 64) return;
                        for (String value : values) {
                            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) return;
                            flags.add(Boolean.parseBoolean(value));
                        }
                    }
                    case "strings" -> {
                        if (values.length > 64) return;
                        for (String value : values) {
                            if (value.length() > 256) return;
                            strings.add(value);
                        }
                    }
                    case "colors" -> {
                        if (values.length > 64) return;
                        for (String value : values) {
                            Integer parsed = boundedInt(value, 0, 0xFFFFFF);
                            if (parsed == null) return;
                            colors.add(parsed);
                        }
                    }
                    default -> { return; }
                }
            }
            if (floats.isEmpty() && flags.isEmpty() && strings.isEmpty() && colors.isEmpty()) {
                stack.remove(DataComponents.CUSTOM_MODEL_DATA);
            } else {
                stack.set(DataComponents.CUSTOM_MODEL_DATA,
                        new CustomModelData(floats, flags, strings, colors));
            }
            return;
        }

        String[] parts = encoded.split(":", 2);
        if (parts.length != 2 || parts[1].length() > 256) return;

        CustomModelData current = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (parts[0].equalsIgnoreCase("int")) {
            int value;
            try {
                value = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
                return;
            }
            if (value < 0 || value > 16_777_215) return;
            List<Float> floats = new ArrayList<>(current == null ? List.of() : current.floats());
            if (floats.isEmpty()) floats.add((float) value);
            else floats.set(0, (float) value);
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                    List.copyOf(floats),
                    current == null ? List.of() : current.flags(),
                    current == null ? List.of() : current.strings(),
                    current == null ? List.of() : current.colors()));
        } else if (parts[0].equalsIgnoreCase("float")) {
            float value;
            try {
                value = Float.parseFloat(parts[1]);
            } catch (NumberFormatException ignored) {
                return;
            }
            if (!Float.isFinite(value) || Math.abs(value) > 1_000_000.0F) return;
            List<Float> floats = new ArrayList<>(current == null ? List.of() : current.floats());
            if (floats.isEmpty()) floats.add(value);
            else floats.set(0, value);
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                    List.copyOf(floats), current == null ? List.of() : current.flags(),
                    current == null ? List.of() : current.strings(), current == null ? List.of() : current.colors()));
        } else if (parts[0].equalsIgnoreCase("flag")) {
            if (!parts[1].equalsIgnoreCase("true") && !parts[1].equalsIgnoreCase("false")) return;
            List<Boolean> flags = new ArrayList<>(current == null ? List.of() : current.flags());
            boolean value = Boolean.parseBoolean(parts[1]);
            if (flags.isEmpty()) flags.add(value);
            else flags.set(0, value);
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                    current == null ? List.of() : current.floats(), List.copyOf(flags),
                    current == null ? List.of() : current.strings(), current == null ? List.of() : current.colors()));
        } else if (parts[0].equalsIgnoreCase("string")) {
            if (parts[1].isEmpty()) return;
            List<String> strings = new ArrayList<>(current == null ? List.of() : current.strings());
            if (strings.isEmpty()) strings.add(parts[1]);
            else strings.set(0, parts[1]);
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                    current == null ? List.of() : current.floats(),
                    current == null ? List.of() : current.flags(),
                    List.copyOf(strings),
                    current == null ? List.of() : current.colors()));
        }
    }

    /**
     * Format: blank = leave unchanged, reset = remove, otherwise
     * hide=0|1;hidden=custom_name,lore,enchantments,... . Known flags are
     * replaced while unknown component flags already on the item remain.
     */
    private static void applyTooltipDisplay(ItemStack stack, String encoded) {
        if (encoded == null || encoded.isBlank()) return;
        if (encoded.equalsIgnoreCase("reset")) {
            stack.remove(DataComponents.TOOLTIP_DISPLAY);
            return;
        }

        boolean hideTooltip = false;
        Set<String> hidden = new HashSet<>();
        for (String part : encoded.split("\\|")) {
            if (part.startsWith("hide=")) {
                hideTooltip = part.substring("hide=".length()).equals("1");
            } else if (part.startsWith("hidden=")) {
                hidden.addAll(Arrays.stream(part.substring("hidden=".length()).split(","))
                        .map(String::trim).filter(s -> !s.isBlank()).toList());
            }
        }

        TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        display = new TooltipDisplay(hideTooltip, new LinkedHashSet<>(display.hiddenComponents()));
        for (Map.Entry<String, net.minecraft.core.component.DataComponentType<?>> entry : TOOLTIP_FLAGS.entrySet()) {
            display = display.withHidden(entry.getValue(), hidden.contains(entry.getKey()));
        }
        stack.set(DataComponents.TOOLTIP_DISPLAY, display);
    }

    private static final Map<String, net.minecraft.core.component.DataComponentType<?>> TOOLTIP_FLAGS = Map.ofEntries(
            Map.entry("custom_name", DataComponents.CUSTOM_NAME),
            Map.entry("lore", DataComponents.LORE),
            Map.entry("enchantments", DataComponents.ENCHANTMENTS),
            Map.entry("stored_enchantments", DataComponents.STORED_ENCHANTMENTS),
            Map.entry("attributes", DataComponents.ATTRIBUTE_MODIFIERS),
            Map.entry("dyed_color", DataComponents.DYED_COLOR),
            Map.entry("base_color", DataComponents.BASE_COLOR),
            Map.entry("banner_patterns", DataComponents.BANNER_PATTERNS),
            Map.entry("trim", DataComponents.TRIM),
            Map.entry("damage", DataComponents.DAMAGE),
            Map.entry("unbreakable", DataComponents.UNBREAKABLE),
            Map.entry("custom_model_data", DataComponents.CUSTOM_MODEL_DATA),
            Map.entry("rarity", DataComponents.RARITY),
            Map.entry("can_place_on", DataComponents.CAN_PLACE_ON),
            Map.entry("can_break", DataComponents.CAN_BREAK),
            Map.entry("food", DataComponents.FOOD),
            Map.entry("potion_contents", DataComponents.POTION_CONTENTS),
            Map.entry("firework_explosion", DataComponents.FIREWORK_EXPLOSION),
            Map.entry("fireworks", DataComponents.FIREWORKS),
            Map.entry("container", DataComponents.CONTAINER),
            Map.entry("bundle_contents", DataComponents.BUNDLE_CONTENTS));

    private static void applyAdventurePredicate(ServerPlayer player, ItemStack stack,
                                                 net.minecraft.core.component.DataComponentType<AdventureModePredicate> type,
                                                 String encoded) {
        if (encoded == null || encoded.isBlank()) return;
        if (encoded.equalsIgnoreCase("clear")) {
            stack.remove(type);
            return;
        }
        List<BlockPredicate> predicates = new ArrayList<>();
        var blocks = player.registryAccess().lookupOrThrow(Registries.BLOCK);
        for (String part : encoded.split(",")) {
            String value = part.trim();
            if (value.isBlank()) continue;
            HolderSet<net.minecraft.world.level.block.Block> set = null;
            if (value.startsWith("#")) {
                Identifier tagId = Identifier.tryParse(value.substring(1));
                if (tagId != null) {
                    TagKey<net.minecraft.world.level.block.Block> tag = TagKey.create(Registries.BLOCK, tagId);
                    set = blocks.get(tag).orElse(null);
                }
            } else {
                Identifier id = Identifier.tryParse(value);
                Holder<net.minecraft.world.level.block.Block> holder = id == null ? null : blocks.get(id).orElse(null);
                if (holder != null) set = HolderSet.direct(holder);
            }
            if (set == null) continue;
            predicates.add(new BlockPredicate(Optional.of(set), Optional.empty(), Optional.empty(), DataComponentMatchers.ANY));
        }
        if (predicates.isEmpty()) return;
        stack.set(type, new AdventureModePredicate(predicates));
    }

    private static void applyBanner(ServerPlayer player, ItemStack stack, String baseColorEncoded, String patternsEncoded) {
        boolean bannerOrShield = stack.is(ItemTags.BANNERS) || stack.is(Items.SHIELD)
                || stack.has(DataComponents.BANNER_PATTERNS) || stack.has(DataComponents.BASE_COLOR);
        if (!bannerOrShield) return;

        if (baseColorEncoded != null && !baseColorEncoded.isBlank()) {
            if (baseColorEncoded.equalsIgnoreCase("clear")) {
                stack.remove(DataComponents.BASE_COLOR);
            } else {
                DyeColor color = DyeColor.byName(baseColorEncoded.toLowerCase(java.util.Locale.ROOT), null);
                if (color != null) stack.set(DataComponents.BASE_COLOR, color);
            }
        }

        if (patternsEncoded == null || patternsEncoded.isBlank()) return;
        if (patternsEncoded.equalsIgnoreCase("clear")) {
            stack.remove(DataComponents.BANNER_PATTERNS);
            return;
        }

        var registry = player.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        List<BannerPatternLayers.Layer> layers = new ArrayList<>();
        for (String entry : patternsEncoded.split(";")) {
            if (layers.size() >= 20) break;
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) continue;
            Identifier patternId = Identifier.tryParse(parts[0]);
            DyeColor color = DyeColor.byName(parts[1].toLowerCase(java.util.Locale.ROOT), null);
            if (patternId == null || color == null) continue;
            Holder<BannerPattern> pattern = registry.get(patternId).orElse(null);
            if (pattern != null) layers.add(new BannerPatternLayers.Layer(pattern, color));
        }
        if (layers.isEmpty()) return;
        stack.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers(layers));
    }

    private static void applySpecial(ServerPlayer player, ItemStack stack, String encoded) {
        if (encoded == null || encoded.isBlank()) return;
        for (String section : encoded.split(";")) {
            String[] pair = section.split("=", 2);
            if (pair.length != 2) continue;
            String value = pair[1];
            switch (pair[0]) {
                case "potion" -> applyPotion(player, stack, value);
                case "food" -> applyFood(stack, value);
                case "weapon" -> applyWeapon(stack, value);
                case "fireworks" -> applyFireworks(stack, value);
                case "explosion" -> applyExplosion(stack, value);
                case "container" -> applyItemContainer(player, stack, value);
                case "bundle" -> applyBundle(player, stack, value);
                case "charged" -> applyCharged(player, stack, value);
                case "map" -> applyMapColor(stack, value);
                case "max_stack" -> applyMaxStack(stack, value);
                case "max_damage" -> applyMaxDamage(stack, value);
                case "repair_cost" -> applyRepairCost(stack, value);
                case "potion_scale" -> applyPotionScale(stack, value);
                case "potion_effects" -> applyPotionEffects(player, stack, value);
                case "firework_explosions" -> applyFireworkExplosions(stack, value);
                case "tool_defaults" -> applyToolDefaults(stack, value);
                case "attack_range" -> applyAttackRange(stack, value);
                case "consumable" -> applyConsumable(stack, value);
                case "cooldown" -> applyCooldown(stack, value);
                case "glider" -> applyGlider(stack, value);
                case "item_model" -> applyIdentifier(stack, DataComponents.ITEM_MODEL, value);
                case "tooltip_style" -> applyIdentifier(stack, DataComponents.TOOLTIP_STYLE, value);
                case "enchantable" -> applyEnchantable(stack, value);
                case "repairable" -> applyRepairable(player, stack, value);
                case "damage_resistant" -> applyDamageResistant(stack, value);
                case "profile" -> applyProfile(stack, value);
                case "note_sound" -> applyIdentifier(stack, DataComponents.NOTE_BLOCK_SOUND, value);
                case "use_effects" -> applyUseEffects(stack, value);
                case "swing" -> applySwingAnimation(stack, value);
                case "piercing" -> applyPiercing(stack, value);
                case "equippable" -> applyEquippable(stack, value);
                default -> { }
            }
        }
    }

    private static void applyIdentifier(ItemStack stack, net.minecraft.core.component.DataComponentType<Identifier> type,
                                         String value) {
        if (value.isBlank() || value.equalsIgnoreCase("clear")) stack.remove(type);
        else {
            Identifier id = Identifier.tryParse(value);
            if (id != null) stack.set(type, id);
        }
    }

    private static void applyEnchantable(ItemStack stack, String value) {
        if (value.isBlank() || value.equalsIgnoreCase("clear")) {
            stack.remove(DataComponents.ENCHANTABLE);
            return;
        }
        Integer parsed = boundedInt(value, 0, 255);
        if (parsed != null) stack.set(DataComponents.ENCHANTABLE, new Enchantable(parsed));
    }

    private static void applyRepairable(ServerPlayer player, ItemStack stack, String value) {
        if (value.isBlank() || value.equalsIgnoreCase("clear")) {
            stack.remove(DataComponents.REPAIRABLE);
            return;
        }
        List<Holder<net.minecraft.world.item.Item>> items = new ArrayList<>();
        var registry = player.registryAccess().lookupOrThrow(Registries.ITEM);
        for (String part : value.split(",")) {
            if (items.size() >= 64) return;
            Identifier id = Identifier.tryParse(part.trim());
            Holder<net.minecraft.world.item.Item> holder = id == null ? null : registry.get(id).orElse(null);
            if (holder == null) return;
            items.add(holder);
        }
        if (!items.isEmpty()) stack.set(DataComponents.REPAIRABLE, new Repairable(HolderSet.direct(items)));
    }

    private static void applyDamageResistant(ItemStack stack, String value) {
        if (value.isBlank() || value.equalsIgnoreCase("clear")) {
            stack.remove(DataComponents.DAMAGE_RESISTANT);
            return;
        }
        Identifier id = Identifier.tryParse(value);
        if (id != null) stack.set(DataComponents.DAMAGE_RESISTANT,
                new DamageResistant(net.minecraft.tags.TagKey.create(Registries.DAMAGE_TYPE, id)));
    }

    private static void applyProfile(ItemStack stack, String value) {
        if (!stack.is(Items.PLAYER_HEAD)) return;
        if (value.isBlank() || value.equalsIgnoreCase("clear")) stack.remove(DataComponents.PROFILE);
        else if (value.length() <= 64) stack.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(value));
    }

    private static void applyUseEffects(ItemStack stack, String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 3) return;
        Boolean sprint = booleanFlag(parts[0]);
        Boolean vibrations = booleanFlag(parts[1]);
        Float speed = boundedFloat(parts[2], 0.0F, 10.0F);
        if (sprint != null && vibrations != null && speed != null) {
            stack.set(DataComponents.USE_EFFECTS, new UseEffects(sprint, vibrations, speed));
        }
    }

    private static void applySwingAnimation(ItemStack stack, String value) {
        if (value.isBlank() || value.equalsIgnoreCase("clear")) {
            stack.remove(DataComponents.SWING_ANIMATION);
            return;
        }
        String[] parts = value.split(",", -1);
        if (parts.length != 2) return;
        Integer duration = boundedInt(parts[1], 1, 1000);
        SwingAnimationType type = parseSwingType(parts[0]);
        if (duration != null && type != null) stack.set(DataComponents.SWING_ANIMATION, new SwingAnimation(type, duration));
    }

    private static SwingAnimationType parseSwingType(String value) {
        for (SwingAnimationType type : SwingAnimationType.values()) {
            if (type.getSerializedName().equalsIgnoreCase(value.trim()) || type.name().equalsIgnoreCase(value.trim())) return type;
        }
        return null;
    }

    private static void applyPiercing(ItemStack stack, String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 2) return;
        Boolean knockback = booleanFlag(parts[0]);
        Boolean dismount = booleanFlag(parts[1]);
        if (knockback == null || dismount == null) return;
        PiercingWeapon current = stack.get(DataComponents.PIERCING_WEAPON);
        stack.set(DataComponents.PIERCING_WEAPON, new PiercingWeapon(knockback, dismount,
                current == null ? Optional.empty() : current.sound(),
                current == null ? Optional.empty() : current.hitSound()));
    }

    private static void applyEquippable(ItemStack stack, String value) {
        Equippable current = stack.get(DataComponents.EQUIPPABLE);
        if (current == null) return;
        String[] parts = value.split(",", -1);
        if (parts.length != 6) return;
        EquipmentSlot slot = EquipmentSlot.byName(parts[0].trim());
        Boolean dispensable = booleanFlag(parts[1]);
        Boolean swappable = booleanFlag(parts[2]);
        Boolean damageOnHurt = booleanFlag(parts[3]);
        Boolean equipOnInteract = booleanFlag(parts[4]);
        Boolean canBeSheared = booleanFlag(parts[5]);
        if (slot == null || dispensable == null || swappable == null || damageOnHurt == null
                || equipOnInteract == null || canBeSheared == null) return;
        stack.set(DataComponents.EQUIPPABLE, new Equippable(slot, current.equipSound(), current.assetId(),
                current.cameraOverlay(), current.allowedEntities(), dispensable, swappable, damageOnHurt,
                equipOnInteract, canBeSheared, current.shearingSound()));
    }

    private static void applyMaxStack(ItemStack stack, String value) {
        Integer parsed = boundedInt(value, 1, 99);
        if (parsed != null) stack.set(DataComponents.MAX_STACK_SIZE, parsed);
    }

    private static void applyMaxDamage(ItemStack stack, String value) {
        Integer parsed = boundedInt(value, 0, 1_000_000);
        if (parsed != null) stack.set(DataComponents.MAX_DAMAGE, parsed);
    }

    private static void applyRepairCost(ItemStack stack, String value) {
        Integer parsed = boundedInt(value, 0, 100_000);
        if (parsed != null) stack.set(DataComponents.REPAIR_COST, parsed);
    }

    private static void applyPotionScale(ItemStack stack, String value) {
        if (!stack.has(DataComponents.POTION_CONTENTS)) return;
        if (value.isBlank() || value.equalsIgnoreCase("clear")) {
            stack.remove(DataComponents.POTION_DURATION_SCALE);
            return;
        }
        Float parsed = boundedFloat(value, 0.0F, 10.0F);
        if (parsed != null) stack.set(DataComponents.POTION_DURATION_SCALE, parsed);
    }

    private static void applyPotionEffects(ServerPlayer player, ItemStack stack, String value) {
        if (!stack.has(DataComponents.POTION_CONTENTS)) return;
        PotionContents current = stack.get(DataComponents.POTION_CONTENTS);
        if (current == null) return;
        List<MobEffectInstance> effects = parseEffects(player, value);
        if (effects != null) stack.set(DataComponents.POTION_CONTENTS, new PotionContents(current.potion(),
                current.customColor(), effects, current.customName()));
    }

    private static List<MobEffectInstance> parseEffects(ServerPlayer player, String encoded) {
        if (encoded.isBlank() || encoded.equalsIgnoreCase("clear")) return new ArrayList<>();
        List<MobEffectInstance> result = new ArrayList<>();
        var registry = player.registryAccess().lookupOrThrow(Registries.MOB_EFFECT);
        for (String entry : encoded.split("\\^")) {
            if (entry.isBlank() || result.size() >= 32) return null;
            String[] parts = entry.split("\\*", -1);
            if (parts.length != 3 && parts.length != 6) return null;
            Identifier id = Identifier.tryParse(parts[0]);
            Integer duration = parseEffectDuration(parts[1]);
            Integer amplifier = boundedInt(parts[2], 0, 255);
            if (id == null || duration == null || amplifier == null) return null;
            Holder<net.minecraft.world.effect.MobEffect> effect = registry.get(id).orElse(null);
            if (effect == null) return null;
            boolean ambient = parts.length == 6 && booleanFlag(parts[3]) != null && booleanFlag(parts[3]);
            boolean visible = parts.length != 6 || (booleanFlag(parts[4]) != null && booleanFlag(parts[4]));
            boolean icon = parts.length != 6 || (booleanFlag(parts[5]) != null && booleanFlag(parts[5]));
            if (parts.length == 6 && (booleanFlag(parts[3]) == null || booleanFlag(parts[4]) == null
                    || booleanFlag(parts[5]) == null)) return null;
            result.add(new MobEffectInstance(effect, duration, amplifier, ambient, visible, icon));
        }
        return result;
    }

    private static Integer parseEffectDuration(String value) {
        if (value.trim().equals("-1")) return MobEffectInstance.INFINITE_DURATION;
        return boundedInt(value, 1, Integer.MAX_VALUE);
    }

    private static void applyFireworkExplosions(ItemStack stack, String value) {
        if (!stack.has(DataComponents.FIREWORKS)) return;
        if (value.isBlank() || value.equalsIgnoreCase("clear")) {
            Fireworks current = stack.get(DataComponents.FIREWORKS);
            if (current != null) stack.set(DataComponents.FIREWORKS, new Fireworks(current.flightDuration(), List.of()));
            return;
        }
        List<FireworkExplosion> result = new ArrayList<>();
        for (String entry : value.split("\\^")) {
            if (entry.isBlank() || result.size() >= 256) return;
            String[] parts = entry.split("~", -1);
            if (parts.length != 5) return;
            FireworkExplosion.Shape shape = parseShape(parts[0]);
            List<Integer> colors = parseColors(parts[1]);
            List<Integer> fades = parseColors(parts[2]);
            if (shape == null || colors == null || fades == null
                    || (!parts[3].equals("0") && !parts[3].equals("1"))
                    || (!parts[4].equals("0") && !parts[4].equals("1"))) return;
            result.add(new FireworkExplosion(shape, new it.unimi.dsi.fastutil.ints.IntArrayList(colors),
                    new it.unimi.dsi.fastutil.ints.IntArrayList(fades), parts[3].equals("1"), parts[4].equals("1")));
        }
        Fireworks current = stack.get(DataComponents.FIREWORKS);
        if (current != null) stack.set(DataComponents.FIREWORKS, new Fireworks(current.flightDuration(), result));
    }

    private static FireworkExplosion.Shape parseShape(String value) {
        for (FireworkExplosion.Shape shape : FireworkExplosion.Shape.values()) {
            if (shape.getSerializedName().equalsIgnoreCase(value) || shape.name().equalsIgnoreCase(value)) return shape;
        }
        return null;
    }

    private static void applyToolDefaults(ItemStack stack, String value) {
        if (!stack.has(DataComponents.TOOL)) return;
        String[] parts = value.split(",", -1);
        if (parts.length != 3) return;
        Float speed = boundedFloat(parts[0], 0.0F, 10_000.0F);
        Integer damage = boundedInt(parts[1], 0, 1_000_000);
        Boolean creative = booleanFlag(parts[2]);
        Tool current = stack.get(DataComponents.TOOL);
        if (current != null && speed != null && damage != null && creative != null) {
            stack.set(DataComponents.TOOL, new Tool(current.rules(), speed, damage, creative));
        }
    }

    private static void applyAttackRange(ItemStack stack, String value) {
        if (!stack.has(DataComponents.ATTACK_RANGE)) return;
        String[] parts = value.split(",", -1);
        if (parts.length != 6) return;
        float[] values = new float[6];
        for (int i = 0; i < values.length; i++) {
            Float parsed = boundedFloat(parts[i], 0.0F, 128.0F);
            if (parsed == null) return;
            values[i] = parsed;
        }
        if (values[0] > values[1] || values[2] > values[3]) return;
        stack.set(DataComponents.ATTACK_RANGE, new AttackRange(values[0], values[1], values[2], values[3], values[4], values[5]));
    }

    private static void applyConsumable(ItemStack stack, String value) {
        if (!stack.has(DataComponents.CONSUMABLE)) return;
        String[] parts = value.split(",", -1);
        if (parts.length != 3) return;
        Float seconds = boundedFloat(parts[0], 0.05F, 3600.0F);
        Boolean particles = booleanFlag(parts[2]);
        ItemUseAnimation animation = parseUseAnimation(parts[1]);
        Consumable current = stack.get(DataComponents.CONSUMABLE);
        if (current != null && seconds != null && particles != null && animation != null) {
            stack.set(DataComponents.CONSUMABLE, new Consumable(seconds, animation, current.sound(), particles,
                    current.onConsumeEffects()));
        }
    }

    private static ItemUseAnimation parseUseAnimation(String value) {
        for (ItemUseAnimation animation : ItemUseAnimation.values()) {
            if (animation.getSerializedName().equalsIgnoreCase(value.trim()) || animation.name().equalsIgnoreCase(value.trim())) return animation;
        }
        return null;
    }

    private static void applyCooldown(ItemStack stack, String value) {
        if (value.isBlank() || value.equalsIgnoreCase("clear")) {
            stack.remove(DataComponents.USE_COOLDOWN);
            return;
        }
        String[] parts = value.split("\\|", -1);
        if (parts.length > 2) return;
        Float seconds = boundedFloat(parts[0], 0.0F, 3600.0F);
        if (seconds == null) return;
        if (parts.length == 1 || parts[1].isBlank()) {
            stack.set(DataComponents.USE_COOLDOWN, new UseCooldown(seconds));
            return;
        }
        Identifier group = Identifier.tryParse(parts[1].trim());
        if (group != null) stack.set(DataComponents.USE_COOLDOWN, new UseCooldown(seconds, Optional.of(group)));
    }

    private static void applyGlider(ItemStack stack, String value) {
        Boolean enabled = booleanFlag(value);
        if (enabled == null) return;
        if (enabled) stack.set(DataComponents.GLIDER, Unit.INSTANCE);
        else stack.remove(DataComponents.GLIDER);
    }

    private static Boolean booleanFlag(String value) {
        if (value.trim().equals("1") || value.trim().equalsIgnoreCase("true")) return true;
        if (value.trim().equals("0") || value.trim().equalsIgnoreCase("false")) return false;
        return null;
    }

    private static void applyPotion(ServerPlayer player, ItemStack stack, String value) {
        if (!stack.has(DataComponents.POTION_CONTENTS)) return;
        if (value.isBlank() || value.equalsIgnoreCase("clear")) {
            stack.remove(DataComponents.POTION_CONTENTS);
            return;
        }
        Identifier id = Identifier.tryParse(value);
        if (id == null) return;
        Holder<net.minecraft.world.item.alchemy.Potion> potion = player.registryAccess().lookupOrThrow(Registries.POTION).get(id).orElse(null);
        if (potion != null) stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
    }

    private static void applyFood(ItemStack stack, String value) {
        if (!stack.has(DataComponents.FOOD)) return;
        String[] parts = value.split(",", -1);
        if (parts.length != 3) return;
        Integer nutrition = boundedInt(parts[0], 0, 20);
        Float saturation = boundedFloat(parts[1], 0.0F, 100.0F);
        if (nutrition == null || saturation == null || (!parts[2].equals("0") && !parts[2].equals("1"))) return;
        stack.set(DataComponents.FOOD, new FoodProperties(nutrition, saturation, parts[2].equals("1")));
    }

    private static void applyWeapon(ItemStack stack, String value) {
        if (!stack.has(DataComponents.WEAPON)) return;
        String[] parts = value.split(",", -1);
        if (parts.length != 2) return;
        Integer damage = boundedInt(parts[0], 0, 100);
        Float disable = boundedFloat(parts[1], 0.0F, 10.0F);
        if (damage != null && disable != null) stack.set(DataComponents.WEAPON, new Weapon(damage, disable));
    }

    private static void applyFireworks(ItemStack stack, String value) {
        if (!stack.has(DataComponents.FIREWORKS)) return;
        Integer flight = boundedInt(value, 0, 127);
        if (flight == null) return;
        Fireworks current = stack.get(DataComponents.FIREWORKS);
        stack.set(DataComponents.FIREWORKS, new Fireworks(flight, current == null ? List.of() : current.explosions()));
    }

    private static void applyExplosion(ItemStack stack, String value) {
        if (!stack.has(DataComponents.FIREWORK_EXPLOSION)) return;
        String[] parts = value.split("~", -1);
        if (parts.length != 5) return;
        FireworkExplosion.Shape shape = null;
        for (FireworkExplosion.Shape candidate : FireworkExplosion.Shape.values()) {
            if (candidate.getSerializedName().equalsIgnoreCase(parts[0]) || candidate.name().equalsIgnoreCase(parts[0])) {
                shape = candidate;
                break;
            }
        }
        if (shape == null) return;
        List<Integer> colors = parseColors(parts[1]);
        List<Integer> fades = parseColors(parts[2]);
        if (colors == null || fades == null || (!parts[3].equals("0") && !parts[3].equals("1"))
                || (!parts[4].equals("0") && !parts[4].equals("1"))) return;
        stack.set(DataComponents.FIREWORK_EXPLOSION, new FireworkExplosion(shape,
                new it.unimi.dsi.fastutil.ints.IntArrayList(colors),
                new it.unimi.dsi.fastutil.ints.IntArrayList(fades), parts[3].equals("1"), parts[4].equals("1")));
    }

    private static List<Integer> parseColors(String encoded) {
        List<Integer> colors = new ArrayList<>();
        if (encoded.isBlank()) return colors;
        for (String part : encoded.split(",")) {
            Integer color = boundedInt(part, 0, 0xFFFFFF);
            if (color == null || colors.size() >= 16) return null;
            colors.add(color);
        }
        return colors;
    }

    private static void applyItemContainer(ServerPlayer player, ItemStack stack, String value) {
        if (!stack.has(DataComponents.CONTAINER)) return;
        List<ItemStack> items = parseItemList(player, value, false, false);
        if (items != null) stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    private static void applyBundle(ServerPlayer player, ItemStack stack, String value) {
        if (!stack.has(DataComponents.BUNDLE_CONTENTS)) return;
        List<ItemStack> items = parseItemList(player, value, true, false);
        if (items != null) stack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(items));
    }

    private static void applyCharged(ServerPlayer player, ItemStack stack, String value) {
        if (!stack.has(DataComponents.CHARGED_PROJECTILES)) return;
        List<ItemStack> items = parseItemList(player, value, false, true);
        if (items != null) stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(items));
    }

    private static List<ItemStack> parseItemList(ServerPlayer player, String encoded, boolean bundle, boolean charged) {
        if (encoded.equalsIgnoreCase("clear") || encoded.isBlank()) return new ArrayList<>();
        List<ItemStack> result = new ArrayList<>();
        var registry = player.registryAccess().lookupOrThrow(Registries.ITEM);
        for (String part : encoded.split(",")) {
            String[] pieces = part.trim().split("\\*", 2);
            if (pieces.length == 0) return null;
            Identifier id = Identifier.tryParse(pieces[0]);
            Integer count = pieces.length == 1 ? 1 : boundedInt(pieces[1], 1, 99);
            if (id == null || count == null || result.size() >= 64) return null;
            Holder<net.minecraft.world.item.Item> holder = registry.get(id).orElse(null);
            if (holder == null) return null;
            ItemStack item = new ItemStack(holder, count);
            if (bundle && !BundleContents.canItemBeInBundle(item)) return null;
            if (charged && !item.is(ItemTags.ARROWS) && !item.is(Items.FIREWORK_ROCKET)) return null;
            result.add(item);
        }
        return result;
    }

    private static void applyMapColor(ItemStack stack, String value) {
        if (!stack.has(DataComponents.MAP_COLOR)) return;
        Integer color = boundedInt(value, 0, 0xFFFFFF);
        if (color != null) stack.set(DataComponents.MAP_COLOR, new MapItemColor(color));
    }

    private static Integer boundedInt(String value, int min, int max) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= min && parsed <= max ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Float boundedFloat(String value, float min, float max) {
        try {
            float parsed = Float.parseFloat(value.trim());
            return Float.isFinite(parsed) && parsed >= min && parsed <= max ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
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

    private static void applyAttributeModifiers(ServerPlayer player, ItemStack stack, String encoded) {
        if (encoded == null || encoded.isBlank()) return;

        List<AttributeEdit> edits = parseAttributeEdits(player, encoded);
        if (edits == null || edits.isEmpty()) return;

        ItemAttributeModifiers existing = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        Map<Identifier, AttributeEdit> byAttribute = new HashMap<>();
        for (AttributeEdit edit : edits) byAttribute.put(edit.attributeId(), edit);
        Set<Identifier> changed = new HashSet<>();

        if (existing != null) {
            for (ItemAttributeModifiers.Entry entry : existing.modifiers()) {
                Identifier attributeId = entry.attribute().unwrapKey().map(key -> key.identifier()).orElse(null);
                AttributeEdit edit = attributeId == null ? null : byAttribute.get(attributeId);
                if (edit != null && !changed.contains(attributeId)) {
                    AttributeModifier.Operation operation = edit.operation() == null
                            ? entry.modifier().operation() : edit.operation();
                    EquipmentSlotGroup slot = edit.slotGroup() == null ? entry.slot() : edit.slotGroup();
                    builder.add(entry.attribute(), new AttributeModifier(entry.modifier().id(), edit.amount(), operation), slot, entry.display());
                    changed.add(attributeId);
                } else {
                    builder.add(entry.attribute(), entry.modifier(), entry.slot(), entry.display());
                }
            }
        }

        for (AttributeEdit edit : edits) {
            if (changed.contains(edit.attributeId())) continue;
            builder.add(edit.attribute(),
                    new AttributeModifier(edit.modifierId(), edit.amount(), edit.operation() == null
                            ? AttributeModifier.Operation.ADD_VALUE : edit.operation()),
                    edit.slotGroup() == null ? defaultSlot(edit.attributeId()) : edit.slotGroup());
        }

        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
    }

    private static List<AttributeEdit> parseAttributeEdits(ServerPlayer player, String encoded) {
        List<AttributeEdit> edits = new ArrayList<>();
        Set<Identifier> seenAttributes = new HashSet<>();
        var attributes = player.registryAccess().lookupOrThrow(Registries.ATTRIBUTE);

        for (String entry : encoded.split(";")) {
            if (entry.isBlank()) return null;

            String[] parts = entry.split("=", 4);
            if (parts.length != 2 && parts.length != 4) return null;

            Identifier attributeId = Identifier.tryParse(parts[0]);
            if (attributeId == null || !seenAttributes.add(attributeId)) {
                return null;
            }

            double amount;
            try {
                amount = Double.parseDouble(parts.length == 2 ? parts[1] : parts[3]);
            } catch (NumberFormatException ignored) {
                return null;
            }
            EquipmentSlotGroup slotGroup = parts.length == 4 ? parseSlotGroup(parts[1]) : null;
            AttributeModifier.Operation operation = parts.length == 4 ? parseOperation(parts[2]) : null;
            if (parts.length == 4 && (slotGroup == null || operation == null)) return null;

            Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute =
                    attributes.get(attributeId).orElse(null);
            if (attribute == null) return null;
            if (!validAttributeAmount(attribute, attributeId, amount)) return null;

            edits.add(new AttributeEdit(
                    attribute,
                    attributeId,
                    modifierId(attributeId),
                    slotGroup,
                    operation,
                    amount));
        }

        return edits;
    }

    private static Identifier modifierId(Identifier attributeId) {
        return Identifier.fromNamespaceAndPath(MOD_ID, "modifier." + attributeId.getPath());
    }

    private static EquipmentSlotGroup defaultSlot(Identifier attributeId) {
        String path = attributeId.getPath();
        return switch (path) {
            case "attack_damage", "generic.attack_damage", "attack_speed", "generic.attack_speed" -> EquipmentSlotGroup.MAINHAND;
            case "armor", "generic.armor", "armor_toughness", "generic.armor_toughness", "knockback_resistance", "generic.knockback_resistance" -> EquipmentSlotGroup.ARMOR;
            default -> EquipmentSlotGroup.ANY;
        };
    }

    private static boolean validAttributeAmount(Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                                Identifier attributeId, double amount) {
        if (!Double.isFinite(amount) || Math.abs(amount) > MAX_ATTRIBUTE_AMOUNT) return false;
        String path = attributeId.getPath();
        if (path.equals("knockback_resistance") || path.equals("generic.knockback_resistance")) {
            return amount >= 0 && amount <= 1;
        }
        if (path.equals("armor") || path.equals("generic.armor") || path.equals("armor_toughness")
                || path.equals("generic.armor_toughness") || path.equals("movement_speed")
                || path.equals("generic.movement_speed")) {
            return amount >= 0;
        }
        double sanitized = attribute.value().sanitizeValue(amount);
        return Double.isFinite(sanitized) && Math.abs(sanitized) <= MAX_ATTRIBUTE_AMOUNT;
    }

    private record AttributeEdit(
            Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            Identifier attributeId,
            Identifier modifierId,
            EquipmentSlotGroup slotGroup,
            AttributeModifier.Operation operation,
            double amount) {
    }

    private static void duplicate(ServerPlayer player, String target) {
        ItemStack held = TARGET_CURSOR.equals(target)
                ? player.containerMenu.getCarried()
                : resolveTarget(player, target);
        if (held.isEmpty()) return;
        ItemStack copy = held.copy();
        if (!player.getInventory().add(copy)) {
            player.drop(copy, false);
        }
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }

    /** Resolves only top-level player slots. Nested container components are never valid targets. */
    private static ItemStack resolveTarget(ServerPlayer player, String target) {
        if (target == null || target.isBlank() || TARGET_MAIN_HAND.equals(target)) return player.getMainHandItem();
        if (TARGET_OFF_HAND.equals(target)) return player.getOffhandItem();
        if (TARGET_ARMOR_HEAD.equals(target)) return player.getItemBySlot(EquipmentSlot.HEAD);
        if (TARGET_ARMOR_CHEST.equals(target)) return player.getItemBySlot(EquipmentSlot.CHEST);
        if (TARGET_ARMOR_LEGS.equals(target)) return player.getItemBySlot(EquipmentSlot.LEGS);
        if (TARGET_ARMOR_FEET.equals(target)) return player.getItemBySlot(EquipmentSlot.FEET);
        if (target.startsWith(TARGET_INVENTORY_PREFIX)) {
            try {
                int index = Integer.parseInt(target.substring(TARGET_INVENTORY_PREFIX.length()));
                if (index >= 0 && index < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE) {
                    return player.getInventory().getItem(index);
                }
            } catch (NumberFormatException ignored) { }
        }
        if (target.startsWith("trinket:")) return TrinketsCompat.resolve(player, target);
        if (target.startsWith("accessory:")) return AccessoriesCompat.resolve(player, target);
        return ItemStack.EMPTY;
    }

    public record ApplyPayload(String target, String entries, String customName, String lore, int damage,
                               boolean unbreakable, String trimMaterial, String trimPattern,
                               int dyeColor, String attributeModifiers, int glintOverride,
                               String rarity, String customModelData,
                               String tooltipDisplay, String canPlaceOn,
                               String canBreak, String bannerBaseColor,
                               String bannerPatterns, String specialData,
                               boolean infiniteUse) implements CustomPacketPayload {
        /** Compatibility constructor for the existing screen payload. */
        public ApplyPayload(String entries, String customName, String lore, int damage,
                            boolean unbreakable, String trimMaterial, String trimPattern,
                            int dyeColor, String attributeModifiers) {
            this(TARGET_MAIN_HAND, entries, customName, lore, damage, unbreakable, trimMaterial, trimPattern,
                    dyeColor, attributeModifiers, -2, "", "", "", "", "", "", "", "", false);
        }

        private ApplyPayload(String target, String entries, String customName, String lore, int damage,
                             boolean unbreakable, String trimMaterial, String trimPattern,
                             int dyeColor, String attributeModifiers, ComponentPayload components) {
            this(target, entries, customName, lore, damage, unbreakable, trimMaterial, trimPattern,
                    dyeColor, attributeModifiers, components.glintOverride(), components.rarity(),
                    components.customModelData(), components.tooltipDisplay(),
                    components.canPlaceOn(), components.canBreak(),
                    components.bannerBaseColor(), components.bannerPatterns(), components.specialData(),
                    components.infiniteUse());
        }

        public static final Type<ApplyPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "apply"));
        private static final StreamCodec<ByteBuf, ComponentPayload> COMPONENT_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, ComponentPayload::glintOverride,
                        ByteBufCodecs.STRING_UTF8, ComponentPayload::rarity,
                        ByteBufCodecs.STRING_UTF8, ComponentPayload::customModelData,
                        ByteBufCodecs.STRING_UTF8, ComponentPayload::tooltipDisplay,
                        ByteBufCodecs.STRING_UTF8, ComponentPayload::canPlaceOn,
                        ByteBufCodecs.STRING_UTF8, ComponentPayload::canBreak,
                        ByteBufCodecs.STRING_UTF8, ComponentPayload::bannerBaseColor,
                        ByteBufCodecs.STRING_UTF8, ComponentPayload::bannerPatterns,
                        ByteBufCodecs.STRING_UTF8, ComponentPayload::specialData,
                        ByteBufCodecs.BOOL, ComponentPayload::infiniteUse,
                        ByteBufCodecs.STRING_UTF8, ComponentPayload::target,
                        ComponentPayload::new);
        private static final StreamCodec<RegistryFriendlyByteBuf, ApplyPayload> LEGACY_CODEC = StreamCodec.composite(
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
        public static final StreamCodec<RegistryFriendlyByteBuf, ApplyPayload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    LEGACY_CODEC.encode(buf, payload);
                    COMPONENT_CODEC.encode(buf, new ComponentPayload(payload.glintOverride(),
                            payload.rarity(), payload.customModelData(), payload.tooltipDisplay(),
                            payload.canPlaceOn(), payload.canBreak(), payload.bannerBaseColor(),
                            payload.bannerPatterns(), payload.specialData(), payload.infiniteUse(), payload.target()));
                },
                buf -> {
                    ApplyPayload legacy = LEGACY_CODEC.decode(buf);
                    ComponentPayload components = COMPONENT_CODEC.decode(buf);
                    return new ApplyPayload(components.target(), legacy.entries(), legacy.customName(), legacy.lore(),
                            legacy.damage(), legacy.unbreakable(), legacy.trimMaterial(),
                            legacy.trimPattern(), legacy.dyeColor(), legacy.attributeModifiers(),
                            components);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record ComponentPayload(int glintOverride, String rarity,
                                    String customModelData, String tooltipDisplay,
                                    String canPlaceOn, String canBreak,
                                    String bannerBaseColor, String bannerPatterns,
                                    String specialData, boolean infiniteUse, String target) {
    }

    public record DuplicatePayload(String target) implements CustomPacketPayload {
        public static final Type<DuplicatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "duplicate"));
        public DuplicatePayload() {
            this(TARGET_MAIN_HAND);
        }
        public static final StreamCodec<RegistryFriendlyByteBuf, DuplicatePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, DuplicatePayload::target, DuplicatePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DeletePayload(String target) implements CustomPacketPayload {
        public static final Type<DeletePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "delete"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DeletePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, DeletePayload::target, DeletePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PlayerEditPayload(double maxHealth, double health, int food, float saturation,
                                    int level, double movementSpeed, String effects,
                                    int tanThirst, float tanHydration, float tanExhaustion,
                                    int tanTemperature) implements CustomPacketPayload {
        public static final Type<PlayerEditPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "player_edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayerEditPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, PlayerEditPayload::maxHealth,
                ByteBufCodecs.DOUBLE, PlayerEditPayload::health,
                ByteBufCodecs.VAR_INT, PlayerEditPayload::food,
                ByteBufCodecs.FLOAT, PlayerEditPayload::saturation,
                ByteBufCodecs.VAR_INT, PlayerEditPayload::level,
                ByteBufCodecs.DOUBLE, PlayerEditPayload::movementSpeed,
                ByteBufCodecs.STRING_UTF8, PlayerEditPayload::effects,
                ByteBufCodecs.VAR_INT, PlayerEditPayload::tanThirst,
                ByteBufCodecs.FLOAT, PlayerEditPayload::tanHydration,
                ByteBufCodecs.FLOAT, PlayerEditPayload::tanExhaustion,
                ByteBufCodecs.VAR_INT, PlayerEditPayload::tanTemperature,
                PlayerEditPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ItemVariantPayload(String target, String itemId) implements CustomPacketPayload {
        public static final Type<ItemVariantPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "item_variant"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ItemVariantPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ItemVariantPayload::target,
                ByteBufCodecs.STRING_UTF8, ItemVariantPayload::itemId,
                ItemVariantPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
