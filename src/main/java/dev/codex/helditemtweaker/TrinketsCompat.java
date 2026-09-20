package dev.codex.helditemtweaker;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Optional reflection-only bridge. HeldItemTweaker does not require Trinkets at runtime. */
final class TrinketsCompat {
    record Slot(String id, String label, ItemStack stack) {}
    private record Access(Object inventory, int index) {}

    private TrinketsCompat() {}

    static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded("trinkets")
                || FabricLoader.getInstance().isModLoaded("trinkets_updated");
    }

    static List<Slot> list(Player player) {
        if (!isAvailable()) return List.of();
        try {
            Object component = component(player);
            if (component == null) return List.of();
            Object rawGroups = invokeNamed(component, "getInventory");
            if (!(rawGroups instanceof Map<?, ?> groups)) return List.of();

            List<Slot> result = new ArrayList<>();
            for (Map.Entry<?, ?> groupEntry : groups.entrySet()) {
                if (!(groupEntry.getKey() instanceof String group)
                        || !(groupEntry.getValue() instanceof Map<?, ?> slots)) continue;
                for (Map.Entry<?, ?> slotEntry : slots.entrySet()) {
                    if (!(slotEntry.getKey() instanceof String slot)) continue;
                    Object inventory = slotEntry.getValue();
                    Method sizeMethod = findSizeMethod(inventory.getClass());
                    Method getMethod = findStackGetter(inventory.getClass());
                    if (sizeMethod == null || getMethod == null) continue;
                    int size = ((Number) sizeMethod.invoke(inventory)).intValue();
                    for (int index = 0; index < size; index++) {
                        Object value = getMethod.invoke(inventory, index);
                        if (value instanceof ItemStack stack && !stack.isEmpty()) {
                            result.add(new Slot(targetId(group, slot, index),
                                    group + ": " + slot + " " + (index + 1), stack));
                        }
                    }
                }
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return List.of();
        }
    }

    static ItemStack resolve(Player player, String target) {
        Access access = resolveAccess(player, target);
        if (access == null) return ItemStack.EMPTY;
        try {
            Method getMethod = findStackGetter(access.inventory().getClass());
            if (getMethod == null) return ItemStack.EMPTY;
            Object value = getMethod.invoke(access.inventory(), access.index());
            return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    static void markUpdated(ServerPlayer player, String target) {
        Access access = resolveAccess(player, target);
        if (access == null) return;
        try {
            Method markUpdate = access.inventory().getClass().getMethod("markUpdate");
            markUpdate.invoke(access.inventory());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // A failed optional integration must never break normal item editing.
        }
    }

    static boolean clear(Player player, String target) {
        Access access = resolveAccess(player, target);
        if (access == null) return false;
        try {
            Method getter = findStackGetter(access.inventory().getClass());
            if (getter == null || !(getter.invoke(access.inventory(), access.index()) instanceof ItemStack stack)
                    || stack.isEmpty()) return false;
            Method setter = findStackSetter(access.inventory().getClass());
            if (setter == null) return false;
            setter.invoke(access.inventory(), access.index(), ItemStack.EMPTY);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    static boolean set(Player player, String target, ItemStack stack) {
        Access access = resolveAccess(player, target);
        if (access == null) return false;
        try {
            Method setter = findStackSetter(access.inventory().getClass());
            if (setter == null) return false;
            setter.invoke(access.inventory(), access.index(), stack);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static Access resolveAccess(Player player, String target) {
        if (!isAvailable() || target == null || !target.startsWith("trinket:")) return null;
        String[] parts = target.substring("trinket:".length()).split("/", -1);
        if (parts.length != 3) return null;
        int index;
        try {
            index = Integer.parseInt(parts[2]);
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (index < 0) return null;

        try {
            Object component = component(player);
            if (component == null) return null;
            Object rawGroups = invokeNamed(component, "getInventory");
            if (!(rawGroups instanceof Map<?, ?> groups)) return null;
            Object rawSlots = groups.get(parts[0]);
            if (!(rawSlots instanceof Map<?, ?> slots)) return null;
            Object inventory = slots.get(parts[1]);
            if (inventory == null) return null;
            Method sizeMethod = findSizeMethod(inventory.getClass());
            if (sizeMethod == null || index >= ((Number) sizeMethod.invoke(inventory)).intValue()) return null;
            return new Access(inventory, index);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object component(Player player) throws ReflectiveOperationException {
        Class<?> api = Class.forName("dev.emi.trinkets.api.TrinketsApi");
        for (Method method : api.getMethods()) {
            if (!method.getName().equals("getTrinketComponent") || !Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != 1) continue;
            Object value = method.invoke(null, player);
            return value instanceof Optional<?> optional ? optional.orElse(null) : value;
        }
        return null;
    }

    private static Object invokeNamed(Object object, String name) throws ReflectiveOperationException {
        Method method = object.getClass().getMethod(name);
        return method.invoke(object);
    }

    private static Method findSizeMethod(Class<?> type) {
        for (String name : List.of("size", "method_5439")) {
            try {
                Method method = type.getMethod(name);
                if (method.getParameterCount() == 0 && method.getReturnType() == int.class) return method;
            } catch (NoSuchMethodException ignored) { }
        }
        return null;
    }

    private static Method findStackGetter(Class<?> type) {
        for (Method method : type.getMethods()) {
            if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == int.class
                    && ItemStack.class.isAssignableFrom(method.getReturnType())) return method;
        }
        return null;
    }

    private static Method findStackSetter(Class<?> type) {
        for (Method method : type.getMethods()) {
            if (method.getParameterCount() == 2
                    && method.getParameterTypes()[0] == int.class
                    && ItemStack.class.isAssignableFrom(method.getParameterTypes()[1])) {
                return method;
            }
        }
        return null;
    }

    private static String targetId(String group, String slot, int index) {
        return "trinket:" + group + "/" + slot + "/" + index;
    }
}
