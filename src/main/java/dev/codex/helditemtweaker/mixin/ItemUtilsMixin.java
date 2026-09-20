package dev.codex.helditemtweaker.mixin;

import dev.codex.helditemtweaker.HeldItemTweaker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents infinite-use containers from adding a replacement stack after use. */
@Mixin(ItemUtils.class)
public abstract class ItemUtilsMixin {
    @Inject(method = "createFilledResult(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private static void helditemtweaker$keepOriginalStack(ItemStack source, Player player,
                                                            ItemStack replacement, boolean checkInfiniteMaterials,
                                                            CallbackInfoReturnable<ItemStack> callback) {
        if (HeldItemTweaker.isInfiniteUse(source)) callback.setReturnValue(source);
    }
}
