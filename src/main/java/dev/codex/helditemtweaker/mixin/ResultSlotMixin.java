package dev.codex.helditemtweaker.mixin;

import dev.codex.helditemtweaker.HeldItemTweaker;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
    @Redirect(method = "onTake",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/CraftingContainer;removeItem(II)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack helditemtweaker$keepInfiniteIngredient(CraftingContainer container, int slot, int amount) {
        ItemStack stack = container.getItem(slot);
        if (HeldItemTweaker.isInfiniteUse(stack)) return stack.copyWithCount(amount);
        return container.removeItem(slot, amount);
    }
}
