package dev.codex.helditemtweaker;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Registry-backed related-item selector. */
public final class ItemVariantScreen extends Screen {
    private final String target;
    private final ItemStack source;
    private EditBox searchBox;
    private VariantList list;
    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;
    private ItemStack hoveredStack = ItemStack.EMPTY;

    public ItemVariantScreen(String target, ItemStack source) {
        super(Component.translatable("helditemtweaker.variant.title"));
        this.target = target;
        this.source = source.copy();
    }

    @Override
    protected void init() {
        int margin = Math.max(12, this.width / 10);
        this.searchBox = new EditBox(this.font, margin, 30, this.width - margin * 2, 20,
                Component.translatable("helditemtweaker.variant.search"));
        this.searchBox.setHint(Component.translatable("helditemtweaker.variant.search"));
        this.searchBox.setTooltip(Tooltip.create(Component.literal("Filter related items by name or registry ID.")));
        this.searchBox.setMaxLength(128);
        this.searchBox.setResponder(ignored -> refreshList());
        this.addRenderableWidget(this.searchBox);
        this.list = new VariantList(this.minecraft, this.width - margin * 2, Math.max(30, this.height - 100), 56, 24);
        this.list.setX(margin);
        this.addRenderableWidget(this.list);
        refreshList();

        int buttonY = this.height - 28;
        int buttonWidth = Math.max(90, (this.width - margin * 2 - 8) / 2);
        Button close = this.addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> returnToMainTweaker())
                .pos(margin, buttonY).size(buttonWidth, 20).build());
        close.setTooltip(Tooltip.create(Component.literal("Return to the Universal Tweaker without changing the item.")));
        Button apply = this.addRenderableWidget(Button.builder(Component.translatable("helditemtweaker.variant.apply"), ignored -> apply())
                .pos(margin + buttonWidth + 8, buttonY).size(buttonWidth, 20).build());
        apply.setTooltip(Tooltip.create(Component.literal("Apply the selected related item.")));
    }

    private void refreshList() {
        if (this.list == null) return;
        String filter = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        List<ItemStack> variants = BuiltInRegistries.ITEM.entrySet().stream()
                .map(entry -> new ItemStack(entry.getValue()))
                .filter(stack -> ItemVariantRules.isRelated(this.source, stack))
                .filter(stack -> filter.isBlank()
                        || stack.getItem().getName(stack).getString().toLowerCase(Locale.ROOT).contains(filter)
                        || BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains(filter))
                .sorted(Comparator.comparing(stack -> stack.getItem().getName(stack).getString()))
                .toList();
        this.list.refresh(variants);
    }

    private void apply() {
        VariantEntry selected = this.list == null ? null : this.list.selected;
        if (selected == null) {
            this.status = Component.literal("Select a related item first.");
            this.statusColor = 0xFFFF5555;
            return;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(selected.stack.getItem());
        ClientPlayNetworking.send(new HeldItemTweaker.ItemVariantPayload(this.target, id.toString()));
        this.status = Component.literal("Applied");
        this.statusColor = 0xFF55FF55;
    }

    private void returnToMainTweaker() {
        this.minecraft.setScreen(new HeldItemScreen(this.target));
    }

    @Override
    public void onClose() {
        returnToMainTweaker();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.hoveredStack = ItemStack.EMPTY;
        this.renderTransparentBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
        graphics.drawString(this.font, Component.literal("Current: " + this.source.getHoverName().getString()),
                12, this.height - 48, 0xFFBDBDBD);
        if (!this.status.getString().isBlank()) {
            graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height - 64, this.statusColor);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!this.hoveredStack.isEmpty()) {
            List<ClientTooltipComponent> tooltip = this.hoveredStack.getTooltipLines(
                            Item.TooltipContext.of(this.minecraft.level), this.minecraft.player, TooltipFlag.Default.NORMAL)
                    .stream().map(line -> ClientTooltipComponent.create(line.getVisualOrderText())).toList();
            graphics.renderTooltip(this.font, tooltip, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class VariantList extends ObjectSelectionList<VariantEntry> {
        private VariantEntry selected;

        private VariantList(Minecraft minecraft, int width, int height, int top, int itemHeight) {
            super(minecraft, width, height, top, itemHeight);
        }

        private void refresh(List<ItemStack> variants) {
            this.clearEntries();
            for (ItemStack variant : variants) {
                VariantEntry entry = new VariantEntry(this, variant);
                this.addEntry(entry);
                if (ItemStack.isSameItem(ItemVariantScreen.this.source, variant)) this.selected = entry;
            }
        }

        @Override
        public int getRowWidth() {
            return Math.max(0, this.getWidth() - 8);
        }

        @Override
        protected void renderListBackground(GuiGraphics graphics) {
            graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), 0xEE151515);
            graphics.renderOutline(this.getX(), this.getY(), this.getWidth(), this.getHeight(), 0xFF8A8A8A);
        }
    }

    private final class VariantEntry extends ObjectSelectionList.Entry<VariantEntry> {
        private final VariantList owner;
        private final ItemStack stack;

        private VariantEntry(VariantList owner, ItemStack stack) {
            this.owner = owner;
            this.stack = stack;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovering, float partialTick) {
            if (hovering || this == this.owner.selected) {
                graphics.fill(this.getContentX(), this.getContentY(),
                        this.getContentX() + this.owner.getRowWidth(), this.getContentY() + 24, 0x77555555);
            }
            graphics.renderItem(this.stack, this.getContentX() + 2, this.getContentY() + 4);
            graphics.drawString(ItemVariantScreen.this.font, this.stack.getHoverName(),
                    this.getContentX() + 26, this.getContentY() + 7, 0xFFFFFFFF);
            if (hovering) ItemVariantScreen.this.hoveredStack = this.stack;
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
            if (event.button() != 0) return false;
            this.owner.selected = this;
            return true;
        }

        @Override
        public Component getNarration() {
            return this.stack.getHoverName();
        }
    }
}
