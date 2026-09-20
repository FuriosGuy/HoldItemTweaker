package dev.codex.helditemtweaker;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/** Tracks the ItemStack currently inside an item-use call. */
public final class InfiniteUseGuard {
    private static final ThreadLocal<Deque<ItemStack>> ACTIVE =
            ThreadLocal.withInitial(ArrayDeque::new);

    private InfiniteUseGuard() {
    }

    public static void enter(ItemStack stack) {
        ACTIVE.get().addLast(stack);
    }

    public static void exit(ItemStack stack) {
        Deque<ItemStack> stacks = ACTIVE.get();
        stacks.removeLastOccurrence(stack);
        if (stacks.isEmpty()) ACTIVE.remove();
    }

    public static boolean isActiveFor(ItemStack stack) {
        return ACTIVE.get().contains(stack);
    }
}
