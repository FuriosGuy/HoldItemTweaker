package dev.codex.helditemtweaker.mixin;

import dev.codex.helditemtweaker.HeldItemTweaker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents Infinite Use filled buckets from transforming into a second empty bucket. */
@Mixin(BucketItem.class)
public abstract class BucketItemMixin {
    @Inject(method = "getEmptySuccessItem", at = @At("HEAD"), cancellable = true)
    private static void helditemtweaker$keepFilledBucket(ItemStack source, Player player,
                                                         CallbackInfoReturnable<ItemStack> callback) {
        if (HeldItemTweaker.isInfiniteUse(source)) callback.setReturnValue(source.copy());
    }
}
