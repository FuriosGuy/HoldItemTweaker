package dev.codex.helditemtweaker;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.Optional;

/** Optional bridge for Enchantment Descriptions. No hard dependency. */
public final class EnchantmentDescriptionsCompat {
    private static final String MOD_ID = "enchdesc";

    private EnchantmentDescriptionsCompat() {
    }

    public static Optional<Component> description(Holder<Enchantment> holder) {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return Optional.empty();
        return holder.unwrapKey().map(key -> {
            String translationKey = "enchantment."
                    + key.identifier().toString().replace(':', '.') + ".desc";
            Component description = Component.translatable(translationKey);
            return description.getString().equals(translationKey) ? null : description;
        }).filter(java.util.Objects::nonNull);
    }
}
