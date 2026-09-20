package dev.codex.helditemtweaker.mixin;

import dev.codex.helditemtweaker.HeldItemTweaker;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the selected projectile when the firing weapon has the Infinite Use component. */
@Mixin(ProjectileWeaponItem.class)
public abstract class ProjectileWeaponItemMixin {
    @Inject(method = "useAmmo", at = @At("HEAD"), cancellable = true)
    private static void helditemtweaker$keepProjectile(ItemStack weapon, ItemStack projectile,
                                                        LivingEntity user, boolean ignoreAmmo,
                                                        CallbackInfoReturnable<ItemStack> callback) {
        if (!projectile.isEmpty() && HeldItemTweaker.isInfiniteUse(weapon)) {
            ItemStack shot = projectile.copyWithCount(1);
            shot.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
            callback.setReturnValue(shot);
        }
    }
}
