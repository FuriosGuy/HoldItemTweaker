package dev.codex.helditemtweaker.mixin;

import dev.codex.helditemtweaker.HeldItemTweaker;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.world.entity.LivingEntity;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Redirect(method = "checkTotemDeathProtection",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
    private void helditemtweaker$keepInfiniteTotem(ItemStack stack, int amount) {
        if (!HeldItemTweaker.isInfiniteUse(stack)) stack.shrink(amount);
    }
}
