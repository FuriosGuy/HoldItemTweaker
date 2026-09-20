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

/** Optional reflection-only bridge for Wisp Forest Accessories. */
final class AccessoriesCompat {
    record Slot(String id, String label, ItemStack stack) {}
    private record Access(Object container, Object storage, int index) {}

    private AccessoriesCompat() {}

    static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded("accessories");
    }

    static List<Slot> list(Player player) {
        if (!isAvailable()) return List.of();
        try {
            Object capability = capability(player);
            Object rawContainers = invoke(capability, "getContainers");
            if (!(rawContainers instanceof Map<?, ?> containers)) return List.of();
            List<Slot> result = new ArrayList<>();
            for (Map.Entry<?, ?> entry : containers.entrySet()) {
                if (!(entry.getKey() instanceof String slotName)) continue;
                Object container = entry.getValue();
                Object storage = storage(container);
                Method size = findSizeMethod(storage.getClass());
                Method getter = findGetter(storage.getClass());
                if (size == null || getter == null) continue;
                int count = ((Number) size.invoke(storage)).intValue();
                for (int index = 0; index < count; index++) {
                    Object value = getter.invoke(storage, index);
                    if (value instanceof ItemStack stack && !stack.isEmpty()) {
                        result.add(new Slot(targetId(slotName, index), slotName + " " + (index + 1), stack.copy()));
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
            Method getter = findGetter(access.storage().getClass());
            Object value = getter == null ? null : getter.invoke(access.storage(), access.index());
            return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    static boolean set(Player player, String target, ItemStack stack) {
        Access access = resolveAccess(player, target);
        if (access == null) return false;
        try {
            Method setter = findSetter(access.storage().getClass());
            if (setter == null) return false;
            setter.invoke(access.storage(), access.index(), stack);
            markContainer(access.container());
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    static boolean clear(Player player, String target) {
        return set(player, target, ItemStack.EMPTY);
    }

    static void markUpdated(Player player, String target) {
        Access access = resolveAccess(player, target);
        if (access != null) markContainer(access.container());
    }

    private static Access resolveAccess(Player player, String target) {
        if (!isAvailable() || target == null || !target.startsWith("accessory:")) return null;
        String[] parts = target.substring("accessory:".length()).split("/", -1);
        if (parts.length != 2) return null;
        int index;
        try {
            index = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (index < 0) return null;
        try {
            Object capability = capability(player);
            Object rawContainers = invoke(capability, "getContainers");
            if (!(rawContainers instanceof Map<?, ?> containers)) return null;
            Object container = containers.get(parts[0]);
            if (container == null) return null;
            Object storage = storage(container);
            Method size = findSizeMethod(storage.getClass());
            if (size == null || index >= ((Number) size.invoke(storage)).intValue()) return null;
            return new Access(container, storage, index);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object capability(Player player) throws ReflectiveOperationException {
        for (Method method : player.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && method.getName().equals("accessoriesCapability")) {
                Object value = method.invoke(player);
                if (value != null) return value;
            }
        }
        Class<?> api = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
        for (String name : List.of("get", "getCapability", "getOptionally")) {
            for (Method method : api.getMethods()) {
                if (!method.getName().equals(name) || !Modifier.isStatic(method.getModifiers())
                        || method.getParameterCount() != 1) continue;
                Object value = method.invoke(null, player);
                if (value instanceof Optional<?> optional) value = optional.orElse(null);
                if (value != null) return value;
            }
        }
        throw new NoSuchMethodException("Accessories capability");
    }

    private static Object storage(Object container) throws ReflectiveOperationException {
        for (String name : List.of("getAccessories", "getBase", "getItems", "getContainer")) {
            try {
                Object value = invoke(container, name);
                if (value != null) return value;
            } catch (NoSuchMethodException ignored) { }
        }
        return container;
    }

    private static Object invoke(Object object, String name) throws ReflectiveOperationException {
        return object.getClass().getMethod(name).invoke(object);
    }

    private static Method findSizeMethod(Class<?> type) {
        for (String name : List.of("getContainerSize", "size", "getSlots", "getSlotCount")) {
            try {
                Method method = type.getMethod(name);
                if (method.getParameterCount() == 0 && (method.getReturnType() == int.class
                        || Number.class.isAssignableFrom(method.getReturnType()))) return method;
            } catch (NoSuchMethodException ignored) { }
        }
        return null;
    }

    private static Method findGetter(Class<?> type) {
        for (String name : List.of("getItem", "getStackInSlot", "getStack")) {
            for (Method method : type.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == int.class
                        && ItemStack.class.isAssignableFrom(method.getReturnType())) return method;
            }
        }
        return null;
    }

    private static Method findSetter(Class<?> type) {
        for (String name : List.of("setItem", "setStackInSlot", "setStack")) {
            for (Method method : type.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 2
                        && method.getParameterTypes()[0] == int.class
                        && ItemStack.class.isAssignableFrom(method.getParameterTypes()[1])) return method;
            }
        }
        return null;
    }

    private static void markContainer(Object container) {
        for (String name : List.of("setChanged", "markDirty", "markUpdate", "setChangedAndSync")) {
            try {
                invoke(container, name);
                return;
            } catch (ReflectiveOperationException ignored) { }
        }
    }

    private static String targetId(String slot, int index) {
        return "accessory:" + slot + "/" + index;
    }
}
