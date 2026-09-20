package dev.codex.helditemtweaker;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

/** Optional AppleSkin bridge. Kept reflective so AppleSkin never becomes a hard dependency. */
final class AppleSkinCompat {
    private static final String MOD_ID = "appleskin";
    private static final String FOOD_HELPER = "squeek.appleskin.helpers.FoodHelper";

    private AppleSkinCompat() {}

    static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    static Optional<FoodValues> query(ItemStack stack, Player player) {
        if (!isAvailable() || stack == null || stack.isEmpty() || player == null) return Optional.empty();
        try {
            Class<?> helper = Class.forName(FOOD_HELPER);
            Method query = helper.getMethod("query", ItemStack.class, Player.class);
            Object result = query.invoke(null, stack, player);
            if (result == null) return Optional.empty();

            Field foodField = result.getClass().getField("modifiedFoodComponent");
            Object food = foodField.get(result);
            if (!(food instanceof FoodProperties properties)) return Optional.empty();

            // AppleSkin exposes the effective food component after its own event hooks.
            return Optional.of(new FoodValues(properties.nutrition(), properties.saturation()));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    record FoodValues(int nutrition, float saturationModifier) {
        float saturation() {
            return nutrition * saturationModifier * 2.0F;
        }
    }
}
