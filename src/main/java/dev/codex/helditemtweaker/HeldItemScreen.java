package dev.codex.helditemtweaker;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;

import java.util.*;

public final class HeldItemScreen extends Screen {

    // -------------------------------------------------------------------------
    // Tabs
    // -------------------------------------------------------------------------
    private enum Tab { GENERAL, ENCHANTMENTS, VISUALS, ATTRIBUTES }
    private Tab currentTab = Tab.GENERAL;

    // -------------------------------------------------------------------------
    // State – enchantments
    // -------------------------------------------------------------------------
    private final Map<Holder<Enchantment>, Integer> selected = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // State – general
    // -------------------------------------------------------------------------
    private EditBox nameBox;
    private EditBox loreBox;
    private EditBox damageBox;
    private Button unbreakableButton;
    private boolean unbreakable;

    // -------------------------------------------------------------------------
    // State – trims
    // -------------------------------------------------------------------------
    private List<TrimEntry> trimMaterials = List.of();
    private List<TrimEntry> trimPatterns = List.of();
    private int trimMaterialIdx = 0; // 0 == none
    private int trimPatternIdx = 0;  // 0 == none
    private boolean trimCapable;

    // -------------------------------------------------------------------------
    // State – dye
    // -------------------------------------------------------------------------
    private boolean dyeCapable;
    private int dyeColor = -1;
    private EditBox hexDyeBox;

    // -------------------------------------------------------------------------
    // State – attributes
    // -------------------------------------------------------------------------
    private boolean hasWeaponAttribs;
    private boolean hasArmorAttribs;
    private EditBox attackDamageBox;
    private EditBox attackSpeedBox;
    private EditBox armorBox;
    private EditBox armorToughnessBox;
    private EditBox knockbackResBox;

    // -------------------------------------------------------------------------
    // Tab buttons
    // -------------------------------------------------------------------------
    private Button tabGeneral;
    private Button tabEnchants;
    private Button tabVisuals;
    private Button tabAttribs;

    // -------------------------------------------------------------------------
    // Enchantment list (only visible in ENCHANTMENTS tab)
    // -------------------------------------------------------------------------
    private EnchantmentList enchantmentList;

    // Vanilla DyeColor palette order matching in-game order
    private static final DyeColor[] DYE_COLORS = DyeColor.values();

    public HeldItemScreen() {
        super(Component.literal("Held Item Tweaker"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private record TrimEntry(String id, Component label) {}

    private String trimMaterialId() {
        if (trimMaterialIdx <= 0 || trimMaterialIdx > trimMaterials.size()) return "";
        return trimMaterials.get(trimMaterialIdx - 1).id();
    }

    private String trimPatternId() {
        if (trimPatternIdx <= 0 || trimPatternIdx > trimPatterns.size()) return "";
        return trimPatterns.get(trimPatternIdx - 1).id();
    }

    // =========================================================================
    // Init
    // =========================================================================

    @Override
    protected void init() {
        this.selected.clear();
        this.currentTab = Tab.GENERAL;

        ItemStack held = mc().player == null ? ItemStack.EMPTY : mc().player.getMainHandItem();

        // --- Capabilities ---
        trimCapable = !held.isEmpty() && (held.is(ItemTags.TRIMMABLE_ARMOR) || held.has(DataComponents.TRIM));
        dyeCapable  = !held.isEmpty() && (held.is(ItemTags.DYEABLE) || held.has(DataComponents.DYED_COLOR));
        hasWeaponAttribs = !held.isEmpty() && (held.has(DataComponents.WEAPON) || held.has(DataComponents.TOOL)
                || held.has(DataComponents.ATTRIBUTE_MODIFIERS));
        hasArmorAttribs  = !held.isEmpty() && held.has(DataComponents.EQUIPPABLE);

        // --- Load enchantments ---
        if (!held.isEmpty() && mc().level != null) {
            var lookup = mc().level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(held);
            lookup.listElements().filter(h -> h.value().canEnchant(held) || current.keySet().contains(h))
                    .forEach(h -> {
                        int lvl = current.getLevel(h);
                        if (lvl > 0) this.selected.put(h, lvl);
                    });
        }

        // --- Load unbreakable ---
        unbreakable = held.has(DataComponents.UNBREAKABLE);

        // --- Load trims ---
        if (trimCapable && mc().level != null) {
            var matReg = mc().level.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL);
            var patReg = mc().level.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN);

            List<TrimEntry> mats = new ArrayList<>();
            matReg.listElements().sorted(Comparator.comparing(h -> h.value().description().getString()))
                    .forEach(h -> h.unwrapKey().ifPresent(k -> {
                        Component label = Component.empty()
                                .append(h.value().description());
                        mats.add(new TrimEntry(k.identifier().toString(), label));
                    }));
            trimMaterials = mats;

            List<TrimEntry> pats = new ArrayList<>();
            patReg.listElements().sorted(Comparator.comparing(h -> h.value().description().getString()))
                    .forEach(h -> h.unwrapKey().ifPresent(k -> {
                        pats.add(new TrimEntry(k.identifier().toString(),
                                Component.literal(friendlyId(k.identifier().toString()))));
                    }));
            trimPatterns = pats;

            // Restore existing trim selection
            ArmorTrim currentTrim = held.get(DataComponents.TRIM);
            if (currentTrim != null) {
                String matId = currentTrim.material().unwrapKey().map(k -> k.identifier().toString()).orElse("");
                String patId = currentTrim.pattern().unwrapKey().map(k -> k.identifier().toString()).orElse("");
                trimMaterialIdx = indexOfId(trimMaterials, matId) + 1;
                trimPatternIdx  = indexOfId(trimPatterns, patId)  + 1;
            }
        }

        // --- Load dye color ---
        if (dyeCapable) {
            DyedItemColor existing = held.get(DataComponents.DYED_COLOR);
            dyeColor = (existing != null) ? existing.rgb() : -1;
        }

        // --- Load attributes ---
        double initAttackDmg = 1.0, initAttackSpd = 4.0, initArmor = 0.0, initToughness = 0.0, initKB = 0.0;
        if (!held.isEmpty()) {
            ItemAttributeModifiers mods = held.get(DataComponents.ATTRIBUTE_MODIFIERS);
            if (mods != null) {
                for (var entry : mods.modifiers()) {
                    String id = entry.attribute().unwrapKey().map(k -> k.identifier().toString()).orElse("");
                    double amt = entry.modifier().amount();
                    switch (id) {
                        case "minecraft:generic.attack_damage"  -> initAttackDmg = amt;
                        case "minecraft:generic.attack_speed"   -> initAttackSpd = amt;
                        case "minecraft:generic.armor"          -> initArmor = amt;
                        case "minecraft:generic.armor_toughness"-> initToughness = amt;
                        case "minecraft:generic.knockback_resistance" -> initKB = amt;
                    }
                }
            }
        }

        // ========================= Build Widgets =========================

        int cx = this.width / 2;

        // --- Tab buttons ---
        int tabY = 4, tabH = 16;
        int tabW = hasArmorAttribs || hasWeaponAttribs ? 64 : 80;
        int tabsTotal = 3 + (trimCapable || dyeCapable ? 1 : 0) + (hasWeaponAttribs || hasArmorAttribs ? 1 : 0);
        int tabsWidth = tabsTotal * (tabW + 2);
        int tabStartX = cx - tabsWidth / 2;
        int tx = tabStartX;

        tabGeneral = tab("General", tx, tabY, tabW, tabH, Tab.GENERAL);
        this.addRenderableWidget(tabGeneral); tx += tabW + 2;

        tabEnchants = tab("Enchants", tx, tabY, tabW, tabH, Tab.ENCHANTMENTS);
        this.addRenderableWidget(tabEnchants); tx += tabW + 2;

        if (trimCapable || dyeCapable) {
            tabVisuals = tab("Visuals", tx, tabY, tabW, tabH, Tab.VISUALS);
            this.addRenderableWidget(tabVisuals); tx += tabW + 2;
        }

        if (hasWeaponAttribs || hasArmorAttribs) {
            tabAttribs = tab("Attributes", tx, tabY, tabW, tabH, Tab.ATTRIBUTES);
            this.addRenderableWidget(tabAttribs);
        }

        // --- General widgets ---
        int contentX = cx - 150;
        int contentW = 300;
        int y = 30;

        nameBox = new EditBox(this.font, contentX, y, contentW, 18, Component.literal("Item name"));
        nameBox.setHint(Component.literal("Leave blank to remove custom name"));
        Component currentName = held.get(DataComponents.CUSTOM_NAME);
        nameBox.setValue(currentName == null ? "" : currentName.getString());
        nameBox.setMaxLength(50);
        this.addRenderableWidget(nameBox);
        y += 24;

        ItemLore currentLore = held.get(DataComponents.LORE);
        loreBox = new EditBox(this.font, contentX, y, contentW, 18, Component.literal("Lore"));
        loreBox.setHint(Component.literal("Lore lines separated by |"));
        loreBox.setValue(currentLore == null ? "" : currentLore.lines().stream().map(Component::getString).reduce((a, b) -> a + "|" + b).orElse(""));
        loreBox.setMaxLength(1024);
        this.addRenderableWidget(loreBox);
        y += 24;

        damageBox = new EditBox(this.font, contentX, y, contentW, 18, Component.literal("Damage"));
        damageBox.setHint(Component.literal("Durability damage (0 = full)"));
        damageBox.setValue(Integer.toString(held.isDamageableItem() ? held.getDamageValue() : 0));
        damageBox.setMaxLength(7);
        damageBox.active = held.isDamageableItem();
        this.addRenderableWidget(damageBox);

        // Quick damage buttons
        if (held.isDamageableItem()) {
            int bw = 72, bx = contentX;
            this.addRenderableWidget(Button.builder(Component.literal("Repair"), b -> { damageBox.setValue("0"); })
                    .pos(bx, y + 22).size(bw, 14).build());
            this.addRenderableWidget(Button.builder(Component.literal("Half"), b -> { damageBox.setValue(String.valueOf(held.getMaxDamage() / 2)); })
                    .pos(bx + bw + 2, y + 22).size(bw, 14).build());
            this.addRenderableWidget(Button.builder(Component.literal("Almost Broken"), b -> { damageBox.setValue(String.valueOf(held.getMaxDamage() - 1)); })
                    .pos(bx + (bw + 2) * 2, y + 22).size(bw + 6, 14).build());
            y += 14;
        }
        y += 26;

        unbreakableButton = Button.builder(Component.empty(), b -> { unbreakable = !unbreakable; refreshUnbreakable(); })
                .pos(contentX, y).size(contentW, 18).build();
        if (!held.isDamageableItem()) unbreakableButton.active = false;
        this.addRenderableWidget(unbreakableButton);
        refreshUnbreakable();
        y += 24;

        // --- Enchantment list (hidden by default) ---
        int listY = 28, listBottom = this.height - 44;
        enchantmentList = new EnchantmentList(contentX, listY, contentW, listBottom - listY);
        this.addRenderableWidget(enchantmentList);

        // --- Visuals: trim ---
        // Trim material/pattern cycle buttons built in refreshVisuals
        buildVisualsWidgets(held, contentX, contentW);

        // --- Attributes ---
        int ay = 30;
        int aw = contentW;
        attackDamageBox = editBox(contentX, ay, aw, "Attack Damage", String.format("%.4f", initAttackDmg));
        attackSpeedBox  = editBox(contentX, ay + 24, aw, "Attack Speed", String.format("%.4f", initAttackSpd));
        armorBox           = editBox(contentX, ay + 48, aw, "Armor", String.format("%.4f", initArmor));
        armorToughnessBox  = editBox(contentX, ay + 72, aw, "Armor Toughness", String.format("%.4f", initToughness));
        knockbackResBox    = editBox(contentX, ay + 96, aw, "Knockback Resistance", String.format("%.4f", initKB));
        this.addRenderableWidget(attackDamageBox);
        this.addRenderableWidget(attackSpeedBox);
        this.addRenderableWidget(armorBox);
        this.addRenderableWidget(armorToughnessBox);
        this.addRenderableWidget(knockbackResBox);

        // --- Bottom buttons ---
        int bby = this.height - 22;
        this.addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply())
                .pos(cx - 116, bby).size(72, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Clear All"), b -> clearEnchants())
                .pos(cx - 36, bby).size(72, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .pos(cx + 44, bby).size(72, 18).build());

        switchTab(Tab.GENERAL);
    }

    // =========================================================================
    // Dynamic widgets for visuals tab (trim + dye)
    // =========================================================================

    // We store trim/dye buttons as instance fields for easy re-labelling
    private Button trimPrevMat, trimNextMat, trimPrevPat, trimNextPat, trimClear;
    private final List<Button> dyeButtons = new ArrayList<>();
    private Button dyeClearButton;

    private void buildVisualsWidgets(ItemStack held, int contentX, int contentW) {
        int y = 30;

        if (trimCapable) {
            // Material row
            trimPrevMat = Button.builder(Component.literal("<"), b -> { trimMaterialIdx = prevIdx(trimMaterialIdx, trimMaterials.size()); })
                    .pos(contentX, y).size(18, 18).build();
            trimNextMat = Button.builder(Component.literal(">"), b -> { trimMaterialIdx = nextIdx(trimMaterialIdx, trimMaterials.size()); })
                    .pos(contentX + contentW - 18, y).size(18, 18).build();
            this.addRenderableWidget(trimPrevMat);
            this.addRenderableWidget(trimNextMat);
            y += 22;

            // Pattern row
            trimPrevPat = Button.builder(Component.literal("<"), b -> { trimPatternIdx = prevIdx(trimPatternIdx, trimPatterns.size()); })
                    .pos(contentX, y).size(18, 18).build();
            trimNextPat = Button.builder(Component.literal(">"), b -> { trimPatternIdx = nextIdx(trimPatternIdx, trimPatterns.size()); })
                    .pos(contentX + contentW - 18, y).size(18, 18).build();
            this.addRenderableWidget(trimPrevPat);
            this.addRenderableWidget(trimNextPat);
            y += 22;

            trimClear = Button.builder(Component.literal("Remove Trim"), b -> { trimMaterialIdx = 0; trimPatternIdx = 0; })
                    .pos(contentX + contentW / 2 - 50, y).size(100, 16).build();
            this.addRenderableWidget(trimClear);
            y += 24;
        }

        if (dyeCapable) {
            // 4-column color grid of the 16 dye colors
            int swatch = 16;
            int cols = 8;
            int gx = contentX;
            int gy = y;
            for (int i = 0; i < DYE_COLORS.length; i++) {
                DyeColor dc = DYE_COLORS[i];
                int bx = gx + (i % cols) * (swatch + 2);
                int by = gy + (i / cols) * (swatch + 2);
                Button btn = Button.builder(Component.empty(), b -> dyeColor = dc.getTextureDiffuseColor())
                        .pos(bx, by).size(swatch, swatch).build();
                dyeButtons.add(btn);
                this.addRenderableWidget(btn);
            }
            y = gy + ((DYE_COLORS.length / cols) + 1) * (swatch + 2) + 2;

            hexDyeBox = new EditBox(this.font, contentX, y, contentW - 72, 18, Component.literal("Hex Color"));
            hexDyeBox.setHint(Component.literal("#RRGGBB"));
            hexDyeBox.setMaxLength(7);
            hexDyeBox.setValue(dyeColor >= 0 ? String.format("#%06X", dyeColor & 0xFFFFFF) : "");
            this.addRenderableWidget(hexDyeBox);

            Button hexApply = Button.builder(Component.literal("Apply Hex"), b -> applyHexDye())
                    .pos(contentX + contentW - 70, y).size(70, 18).build();
            this.addRenderableWidget(hexApply);
            y += 22;

            dyeClearButton = Button.builder(Component.literal("Clear Dye"), b -> { dyeColor = -1; hexDyeBox.setValue(""); })
                    .pos(contentX + contentW / 2 - 40, y).size(80, 16).build();
            this.addRenderableWidget(dyeClearButton);
        }
    }

    private void applyHexDye() {
        String hex = hexDyeBox.getValue().trim().replace("#", "");
        try {
            dyeColor = (int) Long.parseLong(hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {}
    }

    // =========================================================================
    // Tab switching
    // =========================================================================

    private Button tab(String label, int x, int y, int w, int h, Tab tab) {
        return Button.builder(Component.literal(label), b -> switchTab(tab)).pos(x, y).size(w, h).build();
    }

    private void switchTab(Tab tab) {
        currentTab = tab;

        // General
        boolean gen = tab == Tab.GENERAL;
        nameBox.visible = gen;
        loreBox.visible = gen;
        damageBox.visible = gen;
        unbreakableButton.visible = gen;

        // Enchantments
        boolean enc = tab == Tab.ENCHANTMENTS;
        enchantmentList.visible = enc;

        // Visuals
        boolean vis = tab == Tab.VISUALS;
        if (trimPrevMat != null) { trimPrevMat.visible = vis; trimNextMat.visible = vis; }
        if (trimPrevPat != null) { trimPrevPat.visible = vis; trimNextPat.visible = vis; }
        if (trimClear != null) trimClear.visible = vis;
        dyeButtons.forEach(b -> b.visible = vis);
        if (hexDyeBox != null) hexDyeBox.visible = vis;
        if (dyeClearButton != null) dyeClearButton.visible = vis;

        // Attributes
        boolean att = tab == Tab.ATTRIBUTES;
        attackDamageBox.visible = att && hasWeaponAttribs;
        attackSpeedBox.visible = att && hasWeaponAttribs;
        armorBox.visible = att && hasArmorAttribs;
        armorToughnessBox.visible = att && hasArmorAttribs;
        knockbackResBox.visible = att && hasArmorAttribs;
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        super.render(g, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 8, 0xFFFFFFFF);

        // --- Live Preview Panel ---
        ItemStack preview = previewStack();
        if (!preview.isEmpty()) {
            // Panel on far right of screen (if enough room) or below tabs
            int px = Math.min(cx + 170, this.width - 32);
            int py = 28;

            // Slot background
            g.fill(px - 2, py - 2, px + 18, py + 18, 0x88000000);
            g.renderItem(preview, px, py);
            g.renderItemDecorations(this.font, preview, px, py, null);

            // Tooltip on hover
            if (mouseX >= px && mouseX < px + 16 && mouseY >= py && mouseY < py + 16) {
                List<ClientTooltipComponent> tooltip = preview.getTooltipLines(
                                net.minecraft.world.item.Item.TooltipContext.of(mc().level),
                                mc().player, TooltipFlag.Default.NORMAL).stream()
                        .map(line -> ClientTooltipComponent.create(line.getVisualOrderText()))
                        .toList();
                g.renderTooltip(this.font, tooltip, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
            }

            // Persistent tooltip shown below preview
            int tipY = py + 22;
            List<Component> tooltipLines = preview.getTooltipLines(
                    net.minecraft.world.item.Item.TooltipContext.of(mc().level),
                    mc().player, TooltipFlag.Default.NORMAL);
            int maxTipW = Math.max(50, this.width - (px - 4) - 4);
            for (Component line : tooltipLines) {
                List<net.minecraft.util.FormattedCharSequence> wrapped = this.font.split(line, maxTipW);
                for (var seq : wrapped) {
                    g.drawString(this.font, seq, px - 2, tipY, 0xFFFFFFFF);
                    tipY += this.font.lineHeight + 1;
                    if (tipY > this.height - 30) break;
                }
                if (tipY > this.height - 30) break;
            }
        }

        // --- Tab-specific labels ---
        if (currentTab == Tab.GENERAL) {
            int contentX = cx - 150;
            int y = 30;
            g.drawString(this.font, Component.literal("Name"), contentX, y - 10, 0xFFAAAAAA);
            y += 24;
            g.drawString(this.font, Component.literal("Lore (use | for new lines)"), contentX, y - 10, 0xFFAAAAAA);
            y += 24;
            ItemStack held = mc().player == null ? ItemStack.EMPTY : mc().player.getMainHandItem();
            if (held.isDamageableItem()) {
                g.drawString(this.font, Component.literal("Damage  (max " + held.getMaxDamage() + ")"), contentX, y - 10, 0xFFAAAAAA);
            } else {
                g.drawString(this.font, Component.literal("Damage  (item not damageable)"), contentX, y - 10, 0xFF666666);
            }
        }

        if (currentTab == Tab.ENCHANTMENTS && enchantmentList.children().isEmpty()) {
            g.drawCenteredString(this.font, Component.literal("No supported enchantments for this item."),
                    cx, 60, 0xFFFF5555);
        }

        if (currentTab == Tab.VISUALS) {
            int contentX = cx - 150;
            int y = 30;
            if (trimCapable) {
                // Material label
                Component matLabel = trimMaterialIdx <= 0
                        ? Component.literal("Material: None").withStyle(Style.EMPTY.withColor(0xFFBDBDBD))
                        : Component.literal("Material: ").append(trimMaterials.get(trimMaterialIdx - 1).label());
                g.drawCenteredString(this.font, matLabel, cx, y + 4, 0xFFFFFFFF);
                y += 22;

                // Pattern label
                String patName = trimPatternIdx <= 0 ? "None" : friendlyId(trimPatterns.get(trimPatternIdx - 1).id());
                g.drawCenteredString(this.font, Component.literal("Pattern: " + patName), cx, y + 4, 0xFFFFFFFF);
                y += 22 + 24;
            }

            if (dyeCapable) {
                // Draw colored swatches
                int swatch = 16, cols = 8;
                int gx = contentX;
                int gy = y;
                for (int i = 0; i < DYE_COLORS.length; i++) {
                    DyeColor dc = DYE_COLORS[i];
                    int bx = gx + (i % cols) * (swatch + 2);
                    int by = gy + (i / cols) * (swatch + 2);
                    int color = dc.getTextureDiffuseColor() | 0xFF000000;
                    g.fill(bx, by, bx + swatch, by + swatch, color);
                    if (dyeColor == dc.getTextureDiffuseColor()) {
                        // Selection border
                        g.renderOutline(bx - 1, by - 1, swatch + 2, swatch + 2, 0xFFFFFF00);
                    }
                    // Tooltip
                    if (mouseX >= bx && mouseX < bx + swatch && mouseY >= by && mouseY < by + swatch) {
                        List<ClientTooltipComponent> dyeTip = List.of(ClientTooltipComponent.create(
                                Component.literal(dc.getName()).getVisualOrderText()));
                        g.renderTooltip(this.font, dyeTip, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
                    }
                }
                // Current dye preview
                if (dyeColor >= 0) {
                    int previewX = contentX + (DYE_COLORS.length % cols) * (swatch + 2);
                    g.drawString(this.font, Component.literal(String.format("  #%06X", dyeColor & 0xFFFFFF)), contentX, gy - 10, dyeColor | 0xFF000000);
                }
            }
        }

        if (currentTab == Tab.ATTRIBUTES) {
            int contentX = cx - 150;
            int y = 30;
            if (hasWeaponAttribs) {
                g.drawString(this.font, Component.literal("Attack Damage"), contentX, y - 10, 0xFFAAAAAA);
                y += 24;
                g.drawString(this.font, Component.literal("Attack Speed"), contentX, y - 10, 0xFFAAAAAA);
            }
            if (hasArmorAttribs) {
                y += 24;
                g.drawString(this.font, Component.literal("Armor"), contentX, y - 10, 0xFFAAAAAA);
                y += 24;
                g.drawString(this.font, Component.literal("Armor Toughness"), contentX, y - 10, 0xFFAAAAAA);
                y += 24;
                g.drawString(this.font, Component.literal("Knockback Resistance"), contentX, y - 10, 0xFFAAAAAA);
            }
        }
    }

    // =========================================================================
    // Preview Stack
    // =========================================================================

    private ItemStack previewStack() {
        ItemStack preview = mc().player == null ? ItemStack.EMPTY : mc().player.getMainHandItem().copy();
        if (preview.isEmpty()) return preview;

        EnchantmentHelper.updateEnchantments(preview, mutable -> {
            mutable.removeIf(h -> true);
            this.selected.forEach(mutable::set);
        });

        String name = nameBox == null ? "" : nameBox.getValue().trim();
        if (name.isBlank()) preview.remove(DataComponents.CUSTOM_NAME);
        else preview.set(DataComponents.CUSTOM_NAME, Component.literal(name));

        if (loreBox != null && !loreBox.getValue().trim().isBlank()) {
            List<Component> lines = List.of(loreBox.getValue().trim().split("\\|", -1)).stream()
                    .limit(ItemLore.MAX_LINES).map(line -> (Component) Component.literal(line)).toList();
            preview.set(DataComponents.LORE, new ItemLore(lines));
        } else preview.remove(DataComponents.LORE);

        if (preview.isDamageableItem() && damageBox != null) {
            int d = parseInteger(damageBox.getValue());
            if (d >= 0 && d <= preview.getMaxDamage()) preview.setDamageValue(d);
        }

        if (preview.isDamageableItem()) {
            if (unbreakable) preview.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            else preview.remove(DataComponents.UNBREAKABLE);
        }

        if (trimCapable && mc().level != null && trimMaterialIdx > 0 && trimPatternIdx > 0) {
            Identifier matId = Identifier.tryParse(trimMaterialId());
            Identifier patId = Identifier.tryParse(trimPatternId());
            if (matId != null && patId != null) {
                Holder<TrimMaterial> mat = mc().level.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).get(matId).orElse(null);
                Holder<TrimPattern> pat = mc().level.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).get(patId).orElse(null);
                if (mat != null && pat != null) preview.set(DataComponents.TRIM, new ArmorTrim(mat, pat));
            }
        } else if (trimCapable && trimMaterialIdx == 0) {
            preview.remove(DataComponents.TRIM);
        }

        if (dyeCapable) {
            if (dyeColor < 0) preview.remove(DataComponents.DYED_COLOR);
            else preview.set(DataComponents.DYED_COLOR, new DyedItemColor(dyeColor));
        }

        return preview;
    }

    // =========================================================================
    // Apply
    // =========================================================================

    private void apply() {
        if (mc().player == null) return;

        // Enchantments
        List<String> entries = new ArrayList<>();
        for (Map.Entry<Holder<Enchantment>, Integer> e : this.selected.entrySet()) {
            e.getKey().unwrapKey().ifPresent(k -> entries.add(k.identifier() + "=" + e.getValue()));
        }

        // Damage
        int dmg = parseInteger(damageBox == null ? "0" : damageBox.getValue());

        // Attributes string
        String attrString = buildAttributeString();

        ClientPlayNetworking.send(new HeldItemTweaker.ApplyPayload(
                String.join(";", entries),
                nameBox.getValue().trim(),
                loreBox.getValue().trim(),
                dmg,
                unbreakable,
                trimMaterialId(),
                trimPatternId(),
                dyeColor,
                attrString));
        mc().player.displayClientMessage(Component.literal("Applied!"), true);
        onClose();
    }

    private String buildAttributeString() {
        List<String> parts = new ArrayList<>();
        if (hasWeaponAttribs) {
            addAttr(parts, "minecraft:generic.attack_damage", "mainhand", attackDamageBox);
            addAttr(parts, "minecraft:generic.attack_speed", "mainhand", attackSpeedBox);
        }
        if (hasArmorAttribs) {
            addAttr(parts, "minecraft:generic.armor", "armor", armorBox);
            addAttr(parts, "minecraft:generic.armor_toughness", "armor", armorToughnessBox);
            addAttr(parts, "minecraft:generic.knockback_resistance", "armor", knockbackResBox);
        }
        return String.join(";", parts);
    }

    private void addAttr(List<String> out, String id, String slot, EditBox box) {
        if (box == null || !box.visible) return;
        try {
            double val = Double.parseDouble(box.getValue().trim());
            out.add(id + "=" + slot + "=add_value=" + val);
        } catch (NumberFormatException ignored) {}
    }

    private void clearEnchants() {
        this.selected.clear();
        if (enchantmentList != null) enchantmentList.refresh();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Minecraft mc() { return this.minecraft; }

    private int parseInteger(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) { return -1; }
    }

    private static int nextIdx(int current, int size) {
        // 0 = none, 1..size = entry indices
        return (current + 1) % (size + 1);
    }

    private static int prevIdx(int current, int size) {
        return (current - 1 + size + 1) % (size + 1);
    }

    private static int indexOfId(List<TrimEntry> list, String id) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(id)) return i;
        return -1;
    }

    private static String friendlyId(String id) {
        int sep = id.indexOf(':');
        String path = sep < 0 ? id : id.substring(sep + 1);
        String[] words = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) { if (!sb.isEmpty()) sb.append(' '); sb.append(Character.toUpperCase(w.charAt(0))); sb.append(w.substring(1)); }
        return sb.toString();
    }

    private int level(Holder<Enchantment> h) { return selected.getOrDefault(h, 0); }

    private void cycle(Holder<Enchantment> h) {
        Enchantment e = h.value();
        int cur = level(h);
        if (cur == 0) cur = e.getMinLevel();
        else if (cur >= e.getMaxLevel()) cur = 0;
        else cur++;
        if (cur == 0) selected.remove(h); else selected.put(h, cur);
        enchantmentList.refresh();
    }

    private void remove(Holder<Enchantment> h) { selected.remove(h); enchantmentList.refresh(); }

    private void refreshUnbreakable() {
        if (unbreakableButton != null) {
            unbreakableButton.setMessage(Component.literal("Unbreakable: " + (unbreakable ? "§aON" : "§cOFF")));
        }
    }

    private EditBox editBox(int x, int y, int w, String hint, String initial) {
        EditBox b = new EditBox(this.font, x, y, w, 18, Component.literal(hint));
        b.setHint(Component.literal(hint));
        b.setValue(initial);
        b.setMaxLength(20);
        return b;
    }

    // =========================================================================
    // Enchantment List
    // =========================================================================

    private final class EnchantmentList extends ObjectSelectionList<EnchantmentEntry> {
        EnchantmentList(int x, int y, int width, int height) {
            super(HeldItemScreen.this.minecraft, width, height, y, 28);
            this.setX(x);
            refresh();
        }

        void refresh() {
            clearEntries();
            if (HeldItemScreen.this.minecraft.level == null || HeldItemScreen.this.minecraft.player == null) return;
            ItemStack held = HeldItemScreen.this.minecraft.player.getMainHandItem();
            ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(held);
            HeldItemScreen.this.minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements()
                    .filter(h -> h.value().canEnchant(held) || current.keySet().contains(h))
                    .sorted(Comparator.comparing(h -> h.value().description().getString()))
                    .forEach(h -> addEntry(new EnchantmentEntry(h)));
        }

        @Override public int getRowWidth() { return Math.max(0, getWidth() - 8); }
    }

    private final class EnchantmentEntry extends ObjectSelectionList.Entry<EnchantmentEntry> {
        private final Holder<Enchantment> holder;
        EnchantmentEntry(Holder<Enchantment> h) { this.holder = h; }

        @Override
        public void renderContent(GuiGraphics g, int mouseX, int mouseY, boolean hovering, float partialTick) {
            int lv = HeldItemScreen.this.level(holder);
            int color = lv > 0 ? 0xFFFFFF55 : 0xFFFFFFFF;
            if (hovering) g.fill(getContentX(), getContentY(),
                    getContentX() + HeldItemScreen.this.enchantmentList.getRowWidth(), getContentY() + 24, 0x55333333);
            g.drawString(HeldItemScreen.this.font, holder.value().description(),
                    getContentX() + 8, getContentY() + 3, color);
            String range = "Level " + holder.value().getMinLevel() + "-" + holder.value().getMaxLevel();
            String val   = lv > 0 ? "Selected: " + lv : range;
            g.drawString(HeldItemScreen.this.font, Component.literal(val),
                    getContentX() + 8, getContentY() + 14, lv > 0 ? 0xFF55FF55 : 0xFFBDBDBD);
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
            if (event.button() == 0) { cycle(holder); return true; }
            if (event.button() == 1) { remove(holder); return true; }
            return false;
        }

        @Override public Component getNarration() { return holder.value().description(); }
    }
}
