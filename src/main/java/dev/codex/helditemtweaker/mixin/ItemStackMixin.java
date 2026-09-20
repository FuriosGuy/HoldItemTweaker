package dev.codex.helditemtweaker.mixin;

import dev.codex.helditemtweaker.HeldItemTweaker;
import dev.codex.helditemtweaker.InfiniteUseGuard;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "useOn", at = @At("HEAD"))
    private void helditemtweaker$enterUseOn(UseOnContext context,
                                             CallbackInfoReturnable<InteractionResult> callback) {
        InfiniteUseGuard.enter((ItemStack) (Object) this);
    }

    @Inject(method = "useOn", at = @At("RETURN"))
    private void helditemtweaker$exitUseOn(UseOnContext context,
                                            CallbackInfoReturnable<InteractionResult> callback) {
        InfiniteUseGuard.exit((ItemStack) (Object) this);
    }

    @Inject(method = "use", at = @At("HEAD"))
    private void helditemtweaker$enterUse(Level level, Player player, InteractionHand hand,
                                           CallbackInfoReturnable<?> callback) {
        InfiniteUseGuard.enter((ItemStack) (Object) this);
    }

    @Inject(method = "use", at = @At("RETURN"))
    private void helditemtweaker$exitUse(Level level, Player player, InteractionHand hand,
                                          CallbackInfoReturnable<?> callback) {
        InfiniteUseGuard.exit((ItemStack) (Object) this);
    }

    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void helditemtweaker$enterFinish(Level level, LivingEntity entity,
                                               CallbackInfoReturnable<ItemStack> callback) {
        InfiniteUseGuard.enter((ItemStack) (Object) this);
    }

    @Inject(method = "finishUsingItem", at = @At("RETURN"))
    private void helditemtweaker$exitFinish(Level level, LivingEntity entity,
                                              CallbackInfoReturnable<ItemStack> callback) {
        InfiniteUseGuard.exit((ItemStack) (Object) this);
    }

    @Inject(method = "interactLivingEntity", at = @At("HEAD"))
    private void helditemtweaker$enterEntityInteract(Player player, LivingEntity entity, InteractionHand hand,
                                                      CallbackInfoReturnable<InteractionResult> callback) {
        InfiniteUseGuard.enter((ItemStack) (Object) this);
    }

    @Inject(method = "interactLivingEntity", at = @At("RETURN"))
    private void helditemtweaker$exitEntityInteract(Player player, LivingEntity entity, InteractionHand hand,
                                                     CallbackInfoReturnable<InteractionResult> callback) {
        InfiniteUseGuard.exit((ItemStack) (Object) this);
    }

    @Inject(method = "shrink", at = @At("HEAD"), cancellable = true)
    private void helditemtweaker$keepDuringUse(int amount, CallbackInfo callback) {
        ItemStack stack = (ItemStack) (Object) this;
        if (HeldItemTweaker.isInfiniteUse(stack) && InfiniteUseGuard.isActiveFor(stack)) {
            callback.cancel();
        }
    }

    @Inject(method = "consume", at = @At("HEAD"), cancellable = true)
    private void helditemtweaker$keepConsumedItem(int amount, LivingEntity entity, CallbackInfo callback) {
        if (HeldItemTweaker.isInfiniteUse((ItemStack) (Object) this)) callback.cancel();
    }

    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true)
    private void helditemtweaker$keepDurability(int amount, ServerLevel level, ServerPlayer entity,
                                                  Consumer<Item> breakCallback, CallbackInfo callback) {
        if (HeldItemTweaker.isInfiniteUse((ItemStack) (Object) this)) callback.cancel();
    }

    @Inject(method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V",
            at = @At("HEAD"), cancellable = true)
    private void helditemtweaker$keepDurabilityForUse(int amount, LivingEntity entity, EquipmentSlot slot,
                                                       CallbackInfo callback) {
        if (HeldItemTweaker.isInfiniteUse((ItemStack) (Object) this)) callback.cancel();
    }
}
