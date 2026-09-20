package dev.codex.helditemtweaker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

/** Renders the vanilla or modded effect icon when that effect provides one. */
public final class MobEffectIconRenderer {
    private MobEffectIconRenderer() {
    }

    public static void render(GuiGraphics graphics, Holder<MobEffect> effect, int x, int y) {
        if (effect == null) return;
        effect.unwrapKey().ifPresent(key -> {
            Identifier icon = Identifier.fromNamespaceAndPath(
                    key.identifier().getNamespace(), "textures/mob_effect/" + key.identifier().getPath() + ".png");
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getResourceManager().getResource(icon).isPresent()) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0, 0,
                        18, 18, 18, 18, 18, 18);
            }
        });
    }
}
