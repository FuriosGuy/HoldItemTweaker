package dev.codex.helditemtweaker;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

/**
 * Optional Tough As Nails bridge.  Kept reflection-only so the base mod still
 * loads when Tough As Nails is not installed.
 */
final class ToughAsNailsCompat {
    private ToughAsNailsCompat() {}

    static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded("toughasnails");
    }

    static void apply(Player player, int thirst, float hydration, float exhaustion, int temperature) {
        if (!isAvailable()) return;
        try {
            if (thirst >= 0) {
                Object data = invokeStatic("toughasnails.api.thirst.ThirstHelper", "getThirst", player);
                invokeSetter(data, "setThirst", int.class, clamp(thirst, 0, 20));
            }
            if (Float.isFinite(hydration) && hydration >= 0.0F) {
                Object data = invokeStatic("toughasnails.api.thirst.ThirstHelper", "getThirst", player);
                invokeSetter(data, "setHydration", float.class, Math.min(hydration, 20.0F));
            }
            if (Float.isFinite(exhaustion) && exhaustion >= 0.0F) {
                Object data = invokeStatic("toughasnails.api.thirst.ThirstHelper", "getThirst", player);
                invokeSetter(data, "setExhaustion", float.class, Math.min(exhaustion, 40.0F));
            }
            if (temperature >= 0 && temperature <= 4) {
                Object data = invokeStatic("toughasnails.api.temperature.TemperatureHelper", "getTemperatureData", player);
                Class<?> level = Class.forName("toughasnails.api.temperature.TemperatureLevel");
                Object value = level.getEnumConstants()[temperature];
                invokeSetter(data, "setLevel", level, value);
                invokeSetter(data, "setTargetLevel", level, value);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Optional API changes must never prevent normal player editing.
        }
    }

    static int thirst(Player player) {
        return number(player, "toughasnails.api.thirst.ThirstHelper", "getThirst", "getThirst", -1).intValue();
    }

    static float hydration(Player player) {
        return number(player, "toughasnails.api.thirst.ThirstHelper", "getThirst", "getHydration", -1.0F).floatValue();
    }

    static float exhaustion(Player player) {
        return number(player, "toughasnails.api.thirst.ThirstHelper", "getThirst", "getExhaustion", -1.0F).floatValue();
    }

    static int temperature(Player player) {
        try {
            Object data = invokeStatic("toughasnails.api.temperature.TemperatureHelper", "getTemperatureData", player);
            Object level = data.getClass().getMethod("getLevel").invoke(data);
            return ((Enum<?>) level).ordinal();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return -1;
        }
    }

    private static Number number(Player player, String helper, String getter, String dataGetter, Number fallback) {
        if (!isAvailable()) return fallback;
        try {
            Object data = invokeStatic(helper, getter, player);
            Object value = data.getClass().getMethod(dataGetter).invoke(data);
            return value instanceof Number number ? number : fallback;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return fallback;
        }
    }

    private static Object invokeStatic(String className, String methodName, Player player)
            throws ReflectiveOperationException {
        Class<?> type = Class.forName(className);
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName) && java.lang.reflect.Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() == 1) {
                return method.invoke(null, player);
            }
        }
        throw new NoSuchMethodException(methodName);
    }

    private static void invokeSetter(Object object, String name, Class<?> type, Object value)
            throws ReflectiveOperationException {
        if (object == null) return;
        object.getClass().getMethod(name, type).invoke(object, value);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
