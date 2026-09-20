package dev.codex.helditemtweaker;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.advancements.criterion.BlockPredicate;
import net.minecraft.advancements.criterion.DataComponentMatchers;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.model.geom.ModelLayers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.function.IntConsumer;

public final class HeldItemScreen extends Screen {
    private enum Tab { GENERAL, PLAYER, ENCHANTMENTS, VISUALS, ATTRIBUTES, COMPONENTS, SPECIAL, ADVANCED, DATA, BANNER }
    private record Layout(int sidebarX, int sidebarWidth, int contentX, int contentWidth, int contentTop, int contentBottom, int footerY,
                          int rowHeight, int fieldHeight, int tabY, int tabHeight, int tabColumns, int tabRows) {}
    private record FieldLabel(String text, int x, int y) {}
    private record TrimEntry(String id, Component label) {}
    private record AttributeSpec(String id, String label, double fallback, double min, double max,
                                 EquipmentSlotGroup group) {}
    private record BannerLayerChoice(String patternId, DyeColor color) {}
    private record TargetChoice(String id, String label, ItemStack stack) {}
    private record ChoiceValue(String value, Component label) {}
    private static final class EffectEdit {
        private Holder<MobEffect> effect;
        private int duration;
        private int amplifier;
        private boolean enabled;
        private boolean particles;
        private boolean icon;
        private boolean ambient;

        private EffectEdit(Holder<MobEffect> effect, int duration, int amplifier, boolean enabled,
                           boolean particles, boolean icon, boolean ambient) {
            this.effect = effect;
            this.duration = duration;
            this.amplifier = amplifier;
            this.enabled = enabled;
            this.particles = particles;
            this.icon = icon;
            this.ambient = ambient;
        }
    }

    private final EnumMap<Tab, List<AbstractWidget>> tabWidgets = new EnumMap<>(Tab.class);
    private final EnumMap<Tab, List<FieldLabel>> labels = new EnumMap<>(Tab.class);
    private final EnumMap<Tab, Integer> tabScrollOffsets = new EnumMap<>(Tab.class);
    private final EnumMap<Tab, Integer> tabScrollMaximums = new EnumMap<>(Tab.class);
    private final Map<AbstractWidget, Integer> tabWidgetBaseY = new IdentityHashMap<>();
    private final Map<EditBox, String> suggestionFields = new IdentityHashMap<>();
    private final Map<EditBox, List<String>> suggestionOptions = new IdentityHashMap<>();
    private EditBox suggestionOwner;
    private List<String> activeSuggestions = List.of();
    private String suggestionToken = "";
    private int suggestionTokenStart;
    private int suggestionCursor;
    private int selectedSuggestion;
    private final List<AbstractWidget> footerWidgets = new ArrayList<>();
    private final Map<Holder<Enchantment>, Integer> selected = new LinkedHashMap<>();
    private final Map<String, EditBox> attributeBoxes = new LinkedHashMap<>();
    private final Set<String> dirtyAttributes = new HashSet<>();
    private final List<AttributeSpec> attributeSpecs = new ArrayList<>();
    private final Map<String, Button> attributeOperationButtons = new LinkedHashMap<>();
    private final Map<String, Button> attributeSlotButtons = new LinkedHashMap<>();
    private final Map<String, ChoiceList> attributeOperationLists = new LinkedHashMap<>();
    private final Map<String, ChoiceList> attributeSlotLists = new LinkedHashMap<>();
    private final List<Button> tabButtons = new ArrayList<>();
    private final List<ChoiceList> choiceLists = new ArrayList<>();
    private ChoiceList openChoiceList;
    private List<TargetChoice> targetChoices = List.of();
    // Lists are rendered in a second pass after Screen.render(), so native
    // setTooltipForNextFrame() calls from their entries arrive too late. Keep
    // the hovered stack and paint its normal Minecraft tooltip at the end.
    private ItemStack hoveredListStack = ItemStack.EMPTY;
    private Component hoveredListHeader = Component.empty();
    private final Map<String, AttributeModifier.Operation> attributeOperations = new LinkedHashMap<>();
    private final Map<String, EquipmentSlotGroup> attributeSlots = new LinkedHashMap<>();

    private Layout layout;
    private Tab currentTab = Tab.GENERAL;
    private final String targetId;
    private TargetList targetList;
    private EnchantmentList enchantmentList;
    private EditBox nameBox;
    private EditBox loreBox;
    private EditBox damageBox;
    private Button unbreakableButton;
    private boolean unbreakable;
    private Button infiniteUseButton;
    private boolean infiniteUse;
    private Button itemVariantButton;

    private EditBox playerMaxHealthBox;
    private EditBox playerHealthBox;
    private EditBox playerFoodBox;
    private EditBox playerSaturationBox;
    private EditBox playerLevelBox;
    private EditBox playerSpeedBox;
    private EditBox playerThirstBox;
    private EditBox playerHydrationBox;
    private EditBox playerExhaustionBox;
    private EditBox playerTemperatureBox;
    private EditBox playerEffectDurationBox;
    private EditBox playerEffectLevelBox;
    private EditBox playerEffectSearchBox;
    private PlayerEffectList playerEffectList;
    private PlayerEffectChoiceList playerEffectChoiceList;
    private Button playerEffectInfiniteButton;
    private Button playerEffectEnabledButton;
    private Button playerEffectParticlesButton;
    private Button playerEffectIconButton;
    private Button playerEffectAmbientButton;
    private Button playerEffectRemoveButton;
    private Button playerEffectClearButton;
    private final List<EffectEdit> playerEffectEdits = new ArrayList<>();
    private int selectedPlayerEffect = -1;

    private boolean trimCapable;
    private List<TrimEntry> trimMaterials = List.of();
    private List<TrimEntry> trimPatterns = List.of();
    private int trimMaterialIndex;
    private int trimPatternIndex;
    private Button trimMaterialButton;
    private Button trimPatternButton;
    private Button trimClearButton;
    private ChoiceList trimMaterialList;
    private ChoiceList trimPatternList;

    private boolean dyeCapable;
    private int dyeColor = -1;
    private int dyeGridY;
    private int dyeButtonWidth;
    private EditBox hexDyeBox;

    private boolean attributeCapable;
    private boolean bannerCapable;
    private boolean shieldCapable;
    private boolean specialCapable;
    private boolean potionCapable;
    private boolean foodCapable;
    private boolean fireworksCapable;
    private boolean weaponCapable;
    private boolean containerCapable;
    private boolean bundleCapable;
    private boolean chargedProjectilesCapable;
    private boolean mapColorCapable;
    private EditBox potionBox;
    private ChoiceList potionList;
    private EditBox foodNutritionBox;
    private EditBox foodSaturationBox;
    private EditBox fireworkFlightBox;
    private EditBox fireworkShapeBox;
    private EditBox fireworkColorsBox;
    private EditBox fireworkFadeColorsBox;
    private EditBox weaponDamageBox;
    private EditBox weaponDisableBox;
    private EditBox containerItemsBox;
    private EditBox bundleItemsBox;
    private EditBox chargedProjectilesBox;
    private EditBox mapColorBox;
    private Button foodAlwaysEatButton;
    private Button fireworkTrailButton;
    private Button fireworkTwinkleButton;
    private ChoiceList fireworkShapeList;
    private boolean foodAlwaysEat;
    private boolean fireworkTrail;
    private boolean fireworkTwinkle;
    private boolean specialDirty;
    private DyeColor bannerBaseColor = DyeColor.WHITE;
    private final List<BannerLayerChoice> bannerLayers = new ArrayList<>();
    private List<TrimEntry> bannerPatterns = List.of();
    private int bannerPatternChoice;
    private DyeColor bannerLayerColorChoice = DyeColor.WHITE;
    private int selectedBannerLayer = -1;
    private Button bannerBaseColorButton;
    private Button bannerPatternChoiceButton;
    private Button bannerLayerColorButton;
    private Button bannerClearButton;
    private Button bannerAddLayerButton;
    private Button bannerEditLayerButton;
    private BannerLayerList bannerLayerList;
    private ChoiceList bannerBaseColorList;
    private ChoiceList bannerPatternList;
    private ChoiceList bannerLayerColorList;
    private BannerFlagModel bannerPreviewFlag;

    private int glintOverride = -1;
    private Rarity rarity = Rarity.COMMON;
    private boolean hideTooltip;
    private boolean hideEnchantments;
    private EditBox modelFloatsBox;
    private EditBox modelFlagsBox;
    private EditBox modelStringsBox;
    private EditBox modelColorsBox;
    private EditBox canPlaceBox;
    private EditBox canBreakBox;
    private boolean canPlaceDirty;
    private boolean canBreakDirty;
    private boolean modelDirty;
    private Button glintButton;
    private Button rarityButton;
    private Button hideTooltipButton;
    private Button hideEnchantmentsButton;
    private ChoiceList glintList;
    private ChoiceList rarityList;
    private ArmorStand previewStand;

    private EditBox maxStackBox;
    private EditBox maxDamageBox;
    private EditBox repairCostBox;
    private EditBox potionDurationScaleBox;
    private EditBox potionEffectDurationBox;
    private EditBox potionEffectAmplifierBox;
    private EffectList potionEffectList;
    private ChoiceList potionEffectChoiceList;
    private Button potionEffectChoiceButton;
    private Button potionEffectEnabledButton;
    private Button potionEffectInfiniteButton;
    private Button potionEffectParticlesButton;
    private Button potionEffectIconButton;
    private Button potionEffectAmbientButton;
    private Button potionEffectApplyButton;
    private Button potionEffectRemoveButton;
    private Button potionEffectClearButton;
    private final List<EffectEdit> potionEffectEdits = new ArrayList<>();
    private int selectedPotionEffect = -1;
    private Holder<MobEffect> selectedPotionEffectType;
    private EditBox fireworkExplosionsBox;
    private EditBox toolDefaultsBox;
    private EditBox attackRangeBox;
    private EditBox consumableBox;
    private ChoiceList consumableAnimationList;
    private EditBox cooldownBox;
    private Button gliderButton;
    private boolean glider;
    private boolean gliderDirty;
    private boolean advancedDirty;
    private String potionDurationScaleValue = "";
    private String potionEffectsValue = "";
    private String fireworkExplosionsValue = "";
    private String toolDefaultsValue = "";
    private String attackRangeValue = "";
    private String consumableValue = "";
    private String cooldownValue = "";
    private EditBox itemModelBox;
    private EditBox tooltipStyleBox;
    private EditBox enchantableBox;
    private EditBox repairableBox;
    private EditBox damageResistantBox;
    private EditBox profileBox;
    private EditBox noteBlockSoundBox;
    private EditBox useEffectsBox;
    private EditBox swingAnimationBox;
    private ChoiceList swingAnimationList;
    private EditBox piercingBox;
    private EditBox equippableBox;
    private ChoiceList equippableSlotList;
    private boolean dataDirty;
    private String itemModelValue = "";
    private String tooltipStyleValue = "";
    private String enchantableValue = "";
    private String repairableValue = "";
    private String damageResistantValue = "";
    private String profileValue = "";
    private String noteBlockSoundValue = "";
    private String useEffectsValue = "";
    private String swingAnimationValue = "";
    private String piercingValue = "";
    private String equippableValue = "";
    private Button applyFooterButton;
    private Button duplicateFooterButton;
    private Button clearEnchantsFooterButton;
    private Button deleteFooterButton;
    private Button closeFooterButton;
    private final boolean openPlayerTab;

    public HeldItemScreen() {
        this(HeldItemTweaker.TARGET_MAIN_HAND);
    }

    public HeldItemScreen(String targetId) {
        this(targetId, false);
    }

    public HeldItemScreen(String targetId, boolean openPlayerTab) {
        super(Component.literal("Universal Tweaker"));
        this.targetId = targetId == null || targetId.isBlank() ? HeldItemTweaker.TARGET_MAIN_HAND : targetId;
        this.openPlayerTab = openPlayerTab;
    }

    @Override
    protected void init() {
        this.tabWidgets.clear();
        this.labels.clear();
        this.footerWidgets.clear();
        this.tabButtons.clear();
        this.choiceLists.clear();
        this.tabScrollOffsets.clear();
        this.tabScrollMaximums.clear();
        this.tabWidgetBaseY.clear();
        this.suggestionFields.clear();
        this.suggestionOptions.clear();
        clearSuggestionPopup();
        this.attributeBoxes.clear();
        this.dirtyAttributes.clear();
        this.attributeOperationButtons.clear();
        this.attributeSlotButtons.clear();
        this.attributeOperationLists.clear();
        this.attributeSlotLists.clear();
        this.attributeOperations.clear();
        this.attributeSlots.clear();
        this.playerEffectEdits.clear();
        this.selectedPlayerEffect = -1;
        for (Tab tab : Tab.values()) {
            this.tabWidgets.put(tab, new ArrayList<>());
            this.labels.put(tab, new ArrayList<>());
        }
        this.currentTab = Tab.GENERAL;
        this.previewStand = null;
        this.bannerPreviewFlag = null;

        ItemStack held = targetStack();
        loadCapabilities(held);
        loadEnchantments(held);
        loadVisualState(held);
        loadAttributeSpecs(held);
        loadBannerState(held);
        loadComponentState(held);
        loadSpecialState(held);
        loadAdvancedState(held);
        loadDataState(held);
        loadPlayerEffects();
        this.layout = calculateLayout();
        buildTargetSidebar();
        buildTabs();
        buildGeneral(held);
        buildPlayer();
        buildEnchantments();
        buildVisuals();
        buildAttributes(held);
        buildComponents(held);
        buildSpecial(held);
        buildAdvanced(held);
        buildData(held);
        buildBanner();
        buildFooter();
        captureTabLayout();
        switchTab(this.openPlayerTab ? Tab.PLAYER : Tab.GENERAL);
    }

    private Layout calculateLayout() {
        int footerY = Math.max(0, this.height - 24);
        int widthLimit = Math.max(1, this.width - 16);
        int tabCount = 3 + (this.trimCapable || this.dyeCapable ? 1 : 0)
                + (this.attributeCapable ? 1 : 0) + 1 + (this.specialCapable ? 1 : 0)
                + 1 + 1 + (this.bannerCapable ? 1 : 0);
        int tabGap = 3;
        int tabColumns = Math.max(1, Math.min(tabCount, (widthLimit + tabGap) / 81));
        int tabRows = (tabCount + tabColumns - 1) / tabColumns;
        int tabY = 28;
        int tabHeight = 20;
        // Leave a full line between the tab row and the content header. This keeps
        // the target-slot label below the last tab at small resolutions.
        int contentTop = tabY + tabRows * (tabHeight + 2) + this.font.lineHeight + 8;
        int contentBottom = Math.max(contentTop + 1, footerY - 10);
        int sidebarWidth = Math.min(150, Math.max(96, widthLimit / 4));
        int gap = 12;
        // Reserve preview space only when screen can display it. Keep editor wide on
        // large screens, while retaining usable width on small GUI scales.
        int previewSpace = this.width >= 620 ? 180 : 0;
        int contentWidth = Math.min(720, Math.max(1, widthLimit - sidebarWidth - gap - previewSpace));
        int totalWidth = sidebarWidth + gap + contentWidth;
        int sidebarX = Math.max(0, (this.width - totalWidth) / 2);
        int contentX = sidebarX + sidebarWidth + gap;
        int contentHeight = Math.max(1, contentBottom - contentTop);
        // Keep label, gap, and input separated at every GUI scale. Long tabs scroll instead
        // of compressing rows until their labels overlap the previous input.
        int rowHeight = Math.max(32, Math.min(36, Math.max(32, contentHeight / 8)));
        int fieldHeight = Math.max(12, Math.min(18, rowHeight - this.font.lineHeight - 7));
        return new Layout(sidebarX, sidebarWidth, contentX, contentWidth, contentTop, contentBottom, footerY,
                rowHeight, fieldHeight, tabY, tabHeight, tabColumns, tabRows);
    }

    private void buildTargetSidebar() {
        if (this.minecraft.player == null) return;
        this.targetChoices = collectTargetChoices(this.minecraft.player);
        int height = Math.max(40, this.layout.contentBottom() - this.layout.contentTop());
        this.targetList = new TargetList(this.layout.sidebarX(), this.layout.contentTop(),
                this.layout.sidebarWidth(), height);
        this.addRenderableWidget(this.targetList);
    }

    private List<TargetChoice> collectTargetChoices(Player player) {
        List<TargetChoice> result = new ArrayList<>();
        addTarget(result, HeldItemTweaker.TARGET_ARMOR_HEAD, "Armor: Head", player.getItemBySlot(EquipmentSlot.HEAD));
        addTarget(result, HeldItemTweaker.TARGET_ARMOR_CHEST, "Armor: Chest", player.getItemBySlot(EquipmentSlot.CHEST));
        addTarget(result, HeldItemTweaker.TARGET_ARMOR_LEGS, "Armor: Legs", player.getItemBySlot(EquipmentSlot.LEGS));
        addTarget(result, HeldItemTweaker.TARGET_ARMOR_FEET, "Armor: Feet", player.getItemBySlot(EquipmentSlot.FEET));
        addTarget(result, HeldItemTweaker.TARGET_MAIN_HAND, "Main-hand slot", player.getMainHandItem());

        int selectedSlot = player.getInventory().getSelectedSlot();
        for (int index = 0; index < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE; index++) {
            if (index == selectedSlot) continue;
            addTarget(result, HeldItemTweaker.inventoryTarget(index), "Inventory slot " + (index + 1),
                    player.getInventory().getItem(index));
        }
        addTarget(result, HeldItemTweaker.TARGET_OFF_HAND, "Offhand", player.getOffhandItem());
        for (TrinketsCompat.Slot slot : TrinketsCompat.list(player)) {
            addTarget(result, slot.id(), "Trinket: " + slot.label(), slot.stack());
        }
        for (AccessoriesCompat.Slot slot : AccessoriesCompat.list(player)) {
            addTarget(result, slot.id(), "Accessory: " + slot.label(), slot.stack());
        }
        // Keep the editor usable when the player has no item anywhere. The empty
        // target is still validated server-side and becomes editable once an item
        // is placed in the selected slot.
        if (result.isEmpty()) {
            result.add(new TargetChoice(HeldItemTweaker.TARGET_MAIN_HAND, "Main-hand slot", ItemStack.EMPTY));
        }
        return List.copyOf(result);
    }

    private void addTarget(List<TargetChoice> result, String id, String label, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) result.add(new TargetChoice(id, label, stack.copy()));
    }

    private ItemStack targetStack() {
        if (this.minecraft.player == null) return ItemStack.EMPTY;
        Player player = this.minecraft.player;
        if (HeldItemTweaker.TARGET_MAIN_HAND.equals(this.targetId)) return player.getMainHandItem();
        if (HeldItemTweaker.TARGET_OFF_HAND.equals(this.targetId)) return player.getOffhandItem();
        if (HeldItemTweaker.TARGET_ARMOR_HEAD.equals(this.targetId)) return player.getItemBySlot(EquipmentSlot.HEAD);
        if (HeldItemTweaker.TARGET_ARMOR_CHEST.equals(this.targetId)) return player.getItemBySlot(EquipmentSlot.CHEST);
        if (HeldItemTweaker.TARGET_ARMOR_LEGS.equals(this.targetId)) return player.getItemBySlot(EquipmentSlot.LEGS);
        if (HeldItemTweaker.TARGET_ARMOR_FEET.equals(this.targetId)) return player.getItemBySlot(EquipmentSlot.FEET);
        if (this.targetId.startsWith("inventory:")) {
            try {
                int index = Integer.parseInt(this.targetId.substring("inventory:".length()));
                if (index >= 0 && index < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE) {
                    return player.getInventory().getItem(index);
                }
            } catch (NumberFormatException ignored) { }
        }
        if (this.targetId.startsWith("trinket:")) return TrinketsCompat.resolve(player, this.targetId);
        if (this.targetId.startsWith("accessory:")) return AccessoriesCompat.resolve(player, this.targetId);
        return ItemStack.EMPTY;
    }

    private void loadCapabilities(ItemStack held) {
        this.trimCapable = !held.isEmpty() && (held.is(ItemTags.TRIMMABLE_ARMOR) || held.has(DataComponents.TRIM));
        this.dyeCapable = !held.isEmpty() && (held.is(ItemTags.DYEABLE) || held.has(DataComponents.DYED_COLOR));
        this.attributeCapable = !held.isEmpty() && (held.has(DataComponents.ATTRIBUTE_MODIFIERS)
                || held.has(DataComponents.WEAPON) || held.has(DataComponents.TOOL)
                || held.has(DataComponents.EQUIPPABLE));
        this.bannerCapable = !held.isEmpty() && (held.is(ItemTags.BANNERS) || held.is(Items.SHIELD)
                || held.has(DataComponents.BANNER_PATTERNS) || held.has(DataComponents.BASE_COLOR));
        this.shieldCapable = !held.isEmpty() && held.is(Items.SHIELD);
        this.unbreakable = held.has(DataComponents.UNBREAKABLE);
        this.infiniteUse = held.getOrDefault(HeldItemTweaker.INFINITE_USE, false);
        this.potionCapable = held.has(DataComponents.POTION_CONTENTS);
        this.foodCapable = held.has(DataComponents.FOOD);
        this.fireworksCapable = held.has(DataComponents.FIREWORKS) || held.has(DataComponents.FIREWORK_EXPLOSION);
        this.weaponCapable = held.has(DataComponents.WEAPON);
        this.containerCapable = held.has(DataComponents.CONTAINER);
        this.bundleCapable = held.has(DataComponents.BUNDLE_CONTENTS);
        this.chargedProjectilesCapable = held.has(DataComponents.CHARGED_PROJECTILES);
        this.mapColorCapable = held.has(DataComponents.MAP_COLOR);
        this.specialCapable = this.potionCapable || this.foodCapable || this.fireworksCapable
                || this.weaponCapable || this.containerCapable || this.bundleCapable
                || this.chargedProjectilesCapable || this.mapColorCapable;
    }

    private void loadSpecialState(ItemStack held) {
        this.specialDirty = false;
        PotionContents potion = held.get(DataComponents.POTION_CONTENTS);
        this.potionBoxValue = potion == null ? "" : potion.potion().flatMap(value -> value.unwrapKey()
                .map(key -> key.identifier().toString())).orElse("");
        FoodProperties food = held.get(DataComponents.FOOD);
        this.foodNutritionValue = food == null ? "0" : Integer.toString(food.nutrition());
        this.foodSaturationValue = food == null ? "0" : Float.toString(food.saturation());
        this.foodAlwaysEat = food != null && food.canAlwaysEat();
        Fireworks fireworks = held.get(DataComponents.FIREWORKS);
        this.fireworkFlightValue = fireworks == null ? "1" : Integer.toString(fireworks.flightDuration());
        FireworkExplosion explosion = held.get(DataComponents.FIREWORK_EXPLOSION);
        this.fireworkShapeValue = explosion == null ? FireworkExplosion.Shape.SMALL_BALL.getSerializedName() : explosion.shape().getSerializedName();
        this.fireworkColorsValue = explosion == null ? "" : joinIntValues(explosion.colors());
        this.fireworkFadeColorsValue = explosion == null ? "" : joinIntValues(explosion.fadeColors());
        this.fireworkTrail = explosion != null && explosion.hasTrail();
        this.fireworkTwinkle = explosion != null && explosion.hasTwinkle();
        Weapon weapon = held.get(DataComponents.WEAPON);
        this.weaponDamageValue = weapon == null ? "1" : Integer.toString(weapon.itemDamagePerAttack());
        this.weaponDisableValue = weapon == null ? "0" : Float.toString(weapon.disableBlockingForSeconds());
        this.containerItemsValue = held.get(DataComponents.CONTAINER) == null ? "" : encodeItems(held.get(DataComponents.CONTAINER).nonEmptyStream().toList());
        BundleContents bundle = held.get(DataComponents.BUNDLE_CONTENTS);
        this.bundleItemsValue = bundle == null ? "" : encodeItems(streamItems(bundle.itemsCopy()));
        ChargedProjectiles charged = held.get(DataComponents.CHARGED_PROJECTILES);
        this.chargedProjectilesValue = charged == null ? "" : encodeItems(charged.getItems());
        MapItemColor mapColor = held.get(DataComponents.MAP_COLOR);
        this.mapColorValue = mapColor == null ? "" : Integer.toString(mapColor.rgb());
    }

    private void loadAdvancedState(ItemStack held) {
        this.advancedDirty = false;
        this.gliderDirty = false;
        this.glider = held.has(DataComponents.GLIDER);
        this.potionEffectEdits.clear();
        this.selectedPotionEffect = -1;
        this.selectedPotionEffectType = null;
        Float durationScale = held.get(DataComponents.POTION_DURATION_SCALE);
        this.potionDurationScaleValue = durationScale == null ? "" : Float.toString(durationScale);
        PotionContents potion = held.get(DataComponents.POTION_CONTENTS);
        if (potion != null) {
            for (MobEffectInstance effect : potion.customEffects()) {
                this.potionEffectEdits.add(new EffectEdit(effect.getEffect(), effect.getDuration(),
                        effect.getAmplifier(), true, effect.isVisible(), effect.showIcon(), effect.isAmbient()));
            }
        }
        if (!this.potionEffectEdits.isEmpty()) {
            this.selectedPotionEffect = 0;
            this.selectedPotionEffectType = this.potionEffectEdits.get(0).effect;
        }
        this.potionEffectsValue = encodePotionEffects();
        Fireworks fireworks = held.get(DataComponents.FIREWORKS);
        this.fireworkExplosionsValue = fireworks == null ? "" : fireworks.explosions().stream()
                .map(this::encodeExplosion).collect(Collectors.joining("^"));
        Tool tool = held.get(DataComponents.TOOL);
        this.toolDefaultsValue = tool == null ? "" : tool.defaultMiningSpeed() + "," + tool.damagePerBlock()
                + "," + (tool.canDestroyBlocksInCreative() ? "1" : "0");
        AttackRange range = held.get(DataComponents.ATTACK_RANGE);
        this.attackRangeValue = range == null ? "" : range.minRange() + "," + range.maxRange() + ","
                + range.minCreativeRange() + "," + range.maxCreativeRange() + ","
                + range.hitboxMargin() + "," + range.mobFactor();
        Consumable consumable = held.get(DataComponents.CONSUMABLE);
        this.consumableValue = consumable == null ? "" : consumable.consumeSeconds() + ","
                + consumable.animation().getSerializedName() + "," + (consumable.hasConsumeParticles() ? "1" : "0");
        UseCooldown cooldown = held.get(DataComponents.USE_COOLDOWN);
        this.cooldownValue = cooldown == null ? "" : cooldown.seconds() + "|"
                + cooldown.cooldownGroup().map(Identifier::toString).orElse("");
    }

    private String encodeEffect(MobEffectInstance effect) {
        return effect.getEffect().unwrapKey().map(key -> key.identifier().toString()).orElse("")
                + "*" + effect.getDuration() + "*" + effect.getAmplifier()
                + "*" + effect.isAmbient() + "*" + effect.isVisible() + "*" + effect.showIcon();
    }

    private String encodePotionEffects() {
        return this.potionEffectEdits.stream().filter(edit -> edit.enabled && edit.effect != null)
                .map(edit -> edit.effect.unwrapKey().map(key -> key.identifier().toString()).orElse("")
                        + "*" + edit.duration + "*" + edit.amplifier
                        + "*" + edit.ambient + "*" + edit.particles + "*" + edit.icon)
                .filter(value -> !value.startsWith("*"))
                .collect(Collectors.joining("^"));
    }

    private String encodeExplosion(FireworkExplosion explosion) {
        return explosion.shape().getSerializedName() + "~" + joinIntValues(explosion.colors()) + "~"
                + joinIntValues(explosion.fadeColors()) + "~" + (explosion.hasTrail() ? "1" : "0") + "~"
                + (explosion.hasTwinkle() ? "1" : "0");
    }

    private void loadDataState(ItemStack held) {
        this.dataDirty = false;
        this.itemModelValue = held.get(DataComponents.ITEM_MODEL) == null ? "" : held.get(DataComponents.ITEM_MODEL).toString();
        this.tooltipStyleValue = held.get(DataComponents.TOOLTIP_STYLE) == null ? "" : held.get(DataComponents.TOOLTIP_STYLE).toString();
        Enchantable enchantable = held.get(DataComponents.ENCHANTABLE);
        this.enchantableValue = enchantable == null ? "" : Integer.toString(enchantable.value());
        Repairable repairable = held.get(DataComponents.REPAIRABLE);
        this.repairableValue = repairable == null ? "" : repairable.items().stream()
                .map(holder -> holder.unwrapKey().map(key -> key.identifier().toString()).orElse(""))
                .filter(value -> !value.isBlank()).collect(Collectors.joining(","));
        DamageResistant resistant = held.get(DataComponents.DAMAGE_RESISTANT);
        this.damageResistantValue = resistant == null ? "" : resistant.types().location().toString();
        ResolvableProfile profile = held.get(DataComponents.PROFILE);
        this.profileValue = profile == null ? "" : profile.name().orElse("");
        this.noteBlockSoundValue = held.get(DataComponents.NOTE_BLOCK_SOUND) == null ? ""
                : held.get(DataComponents.NOTE_BLOCK_SOUND).toString();
        UseEffects useEffects = held.get(DataComponents.USE_EFFECTS);
        this.useEffectsValue = useEffects == null ? "" : (useEffects.canSprint() ? "1" : "0") + ","
                + (useEffects.interactVibrations() ? "1" : "0") + "," + useEffects.speedMultiplier();
        SwingAnimation swing = held.get(DataComponents.SWING_ANIMATION);
        this.swingAnimationValue = swing == null ? "" : swing.type().getSerializedName() + "," + swing.duration();
        PiercingWeapon piercing = held.get(DataComponents.PIERCING_WEAPON);
        this.piercingValue = piercing == null ? "" : (piercing.dealsKnockback() ? "1" : "0") + ","
                + (piercing.dismounts() ? "1" : "0");
        Equippable equippable = held.get(DataComponents.EQUIPPABLE);
        this.equippableValue = equippable == null ? "" : equippable.slot().getSerializedName() + ","
                + (equippable.dispensable() ? "1" : "0") + "," + (equippable.swappable() ? "1" : "0") + ","
                + (equippable.damageOnHurt() ? "1" : "0") + "," + (equippable.equipOnInteract() ? "1" : "0") + ","
                + (equippable.canBeSheared() ? "1" : "0");
    }

    private String potionBoxValue = "";
    private String foodNutritionValue = "0";
    private String foodSaturationValue = "0";
    private String fireworkFlightValue = "1";
    private String fireworkShapeValue = "small_ball";
    private String fireworkColorsValue = "";
    private String fireworkFadeColorsValue = "";
    private String weaponDamageValue = "1";
    private String weaponDisableValue = "0";
    private String containerItemsValue = "";
    private String bundleItemsValue = "";
    private String chargedProjectilesValue = "";
    private String mapColorValue = "";

    private String encodeItems(List<ItemStack> items) {
        return items.stream().map(item -> item.getItemHolder().unwrapKey()
                        .map(key -> key.identifier().toString() + "*" + item.getCount()).orElse(""))
                .filter(value -> !value.isBlank()).collect(Collectors.joining(","));
    }

    private List<ItemStack> streamItems(Iterable<ItemStack> items) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : items) result.add(item);
        return result;
    }

    private String joinIntValues(it.unimi.dsi.fastutil.ints.IntList values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) result.append(',');
            result.append(values.getInt(i));
        }
        return result.toString();
    }

    private void loadBannerState(ItemStack held) {
        this.bannerLayers.clear();
        this.bannerBaseColor = held.getOrDefault(DataComponents.BASE_COLOR, DyeColor.WHITE);
        this.bannerPatterns = List.of();
        this.bannerPatternChoice = 0;
        this.selectedBannerLayer = -1;
        this.bannerLayerColorChoice = DyeColor.WHITE;
        if (!this.bannerCapable || this.minecraft.level == null) return;
        List<TrimEntry> patterns = new ArrayList<>();
        this.minecraft.level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).listElements()
                .sorted(Comparator.comparing(holder -> holder.value().translationKey()))
                .forEach(holder -> holder.unwrapKey().ifPresent(key -> patterns.add(
                        new TrimEntry(key.identifier().toString(), Component.translatable(holder.value().translationKey())))));
        this.bannerPatterns = List.copyOf(patterns);
        BannerPatternLayers current = held.get(DataComponents.BANNER_PATTERNS);
        if (current != null) {
            for (BannerPatternLayers.Layer layer : current.layers()) {
                String id = layer.pattern().unwrapKey().map(key -> key.identifier().toString()).orElse("");
                if (!id.isBlank()) this.bannerLayers.add(new BannerLayerChoice(id, layer.color()));
            }
        }
    }

    private void loadComponentState(ItemStack held) {
        Boolean override = held.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        this.glintOverride = override == null ? -1 : (override ? 1 : 0);
        Rarity stored = held.get(DataComponents.RARITY);
        this.rarity = stored == null ? held.getRarity() : stored;
        TooltipDisplay tooltip = held.get(DataComponents.TOOLTIP_DISPLAY);
        this.hideTooltip = tooltip != null && tooltip.hideTooltip();
        this.hideEnchantments = tooltip != null && !tooltip.shows(DataComponents.ENCHANTMENTS);
    }

    private void loadEnchantments(ItemStack held) {
        this.selected.clear();
        if (held.isEmpty() || this.minecraft.level == null) return;
        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(held);
        this.minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements()
                .filter(holder -> holder.value().canEnchant(held) || current.keySet().contains(holder))
                .forEach(holder -> {
                    if (this.infiniteUse && holder.unwrapKey().map(key ->
                            key.identifier().equals(Identifier.fromNamespaceAndPath("minecraft", "infinity"))).orElse(false)) {
                        return;
                    }
                    int level = current.getLevel(holder);
                    if (level > 0) this.selected.put(holder, level);
                });
    }

    private void loadVisualState(ItemStack held) {
        this.trimMaterials = new ArrayList<>();
        this.trimPatterns = new ArrayList<>();
        this.trimMaterialIndex = 0;
        this.trimPatternIndex = 0;
        if (this.trimCapable && this.minecraft.level != null) {
            this.minecraft.level.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).listElements()
                    .sorted(Comparator.comparing(holder -> holder.value().description().getString()))
                    .forEach(holder -> holder.unwrapKey().ifPresent(key -> this.trimMaterials = add(this.trimMaterials,
                            new TrimEntry(key.identifier().toString(), holder.value().description()))));
            this.minecraft.level.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).listElements()
                    .sorted(Comparator.comparing(holder -> holder.value().description().getString()))
                    .forEach(holder -> holder.unwrapKey().ifPresent(key -> this.trimPatterns = add(this.trimPatterns,
                            new TrimEntry(key.identifier().toString(), holder.value().description()))));
            ArmorTrim current = held.get(DataComponents.TRIM);
            if (current != null) {
                this.trimMaterialIndex = indexOf(this.trimMaterials,
                        current.material().unwrapKey().map(key -> key.identifier().toString()).orElse("")) + 1;
                this.trimPatternIndex = indexOf(this.trimPatterns,
                        current.pattern().unwrapKey().map(key -> key.identifier().toString()).orElse("")) + 1;
            }
        }
        DyedItemColor currentDye = held.get(DataComponents.DYED_COLOR);
        this.dyeColor = currentDye == null ? -1 : currentDye.rgb();
    }

    private <T> List<T> add(List<T> source, T value) {
        List<T> result = new ArrayList<>(source);
        result.add(value);
        return result;
    }

    private void loadAttributeSpecs(ItemStack held) {
        this.attributeSpecs.clear();
        if (!this.attributeCapable) return;
        if (this.minecraft == null || this.minecraft.level == null) return;

        // Attributes are registry data, not a hardcoded vanilla-only list. This keeps the
        // editor usable with modded attributes while still showing every current vanilla
        // attribute as a selectable field.
        this.minecraft.level.registryAccess().lookupOrThrow(Registries.ATTRIBUTE).listElements()
                .map(holder -> {
                    String id = attributeId(holder);
                    String label = Component.translatable(holder.value().getDescriptionId()).getString();
                    if (label.equals(holder.value().getDescriptionId())) {
                        label = id;
                    }
                    double[] bounds = attributeBounds(id);
                    return new AttributeSpec(id, label + " modifier amount", 0,
                            bounds[0], bounds[1], defaultAttributeSlot(id));
                })
                .sorted(Comparator.comparing(AttributeSpec::label))
                .forEach(this.attributeSpecs::add);

        for (AttributeSpec spec : this.attributeSpecs) {
            this.attributeOperations.put(spec.id(), AttributeModifier.Operation.ADD_VALUE);
            this.attributeSlots.put(spec.id(), spec.group());
        }
        ItemAttributeModifiers current = held.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (current != null) {
            for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
                String id = attributeId(entry.attribute());
                if (!this.attributeOperations.containsKey(id)) continue;
                this.attributeOperations.put(id, entry.modifier().operation());
                this.attributeSlots.put(id, entry.slot());
            }
        }
    }

    private double[] attributeBounds(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        if (path.endsWith("knockback_resistance")) return new double[]{0, 1};
        if (path.endsWith("movement_speed")) return new double[]{0, 10};
        if (path.endsWith("armor") || path.endsWith("armor_toughness")) return new double[]{0, 1024};
        return new double[]{-1024, 1024};
    }

    private EquipmentSlotGroup defaultAttributeSlot(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        if (path.endsWith("attack_damage") || path.endsWith("attack_speed")) return EquipmentSlotGroup.MAINHAND;
        if (path.endsWith("armor") || path.endsWith("armor_toughness") || path.endsWith("knockback_resistance")) {
            return EquipmentSlotGroup.ARMOR;
        }
        return EquipmentSlotGroup.ANY;
    }

    private String attributeId(Holder<Attribute> attribute) {
        return attribute.unwrapKey().map(key -> key.identifier().toString()).orElse("");
    }

    private boolean containsAttribute(ItemStack stack, Holder<Attribute> wanted) {
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        return modifiers != null && modifiers.modifiers().stream().anyMatch(entry -> entry.attribute().equals(wanted));
    }

    private <T extends AbstractWidget> T addTabWidget(Tab tab, T widget) {
        this.tabWidgets.get(tab).add(widget);
        if (widget instanceof EditBox editBox && !editBox.getMessage().getString().isBlank()) {
            editBox.setTooltip(fieldTooltip(editBox.getMessage().getString(), "Use the hint shown in this input."));
        } else if (widget instanceof Button button) {
            String text = button.getMessage().getString();
            button.setTooltip(buttonTooltip(text));
        }
        this.addRenderableWidget(widget);
        return widget;
    }

    private Tooltip fieldTooltip(String label, String hint) {
        String explanation = switch (label) {
            case "Name" -> "Custom display name shown in inventories and item tooltips. Leave blank for the normal name.";
            case "Lore" -> "Extra tooltip text. Use | between lines. Leave blank to remove custom lore.";
            case "Damage", "Damage (unsupported)" -> "Durability damage: 0 is fully repaired; maximum damage minus 1 is almost broken.";
            case "Max health" -> "Player maximum health. Health is clamped by this value. Valid range: 1-1024.";
            case "Health" -> "Player's current health. It cannot be below 0 or above maximum health.";
            case "Food" -> "Player hunger points. Valid range: 0-20.";
            case "Saturation" -> "Extra hunger saturation. Valid range: 0-20; higher values delay hunger loss.";
            case "XP level" -> "Player experience level. Valid range: 0 or higher.";
            case "Movement speed" -> "Player movement speed attribute value. Valid range: 0-10.";
            case "Thirst" -> "Tough As Nails thirst value. Valid range: 0-20.";
            case "Hydration" -> "Tough As Nails hydration value. Valid range: 0-20.";
            case "Exhaustion" -> "Tough As Nails exhaustion value. Valid range: 0-40.";
            case "Temperature" -> "Tough As Nails temperature tier. 0 is icy and 4 is hot.";
            case "Effect duration (seconds)" -> "Duration of the selected player effect in seconds. Infinite effects leave this blank.";
            case "Effect level" -> "Selected player effect strength. Level 1 is the normal first level; valid range: 1-256.";
            case "Max stack size" -> "Maximum copies allowed in one inventory slot. Valid range: 1-99.";
            case "Max durability" -> "Maximum durability points. Higher values give more uses; 0 removes durability behavior.";
            case "Anvil repair cost" -> "XP level cost added by anvil repairs and combinations. Valid range: 0-100000.";
            case "Potion duration scale" -> "Multiplier for potion-effect duration. Blank removes the override; 1 keeps normal duration.";
            case "Rocket explosions" -> "Advanced rocket explosion list. Format: shape~colors~fade~trail~twinkle; separate explosions with ^.";
            case "Tool defaults" -> "Mining defaults in order: speed, damage per block, and works in Creative (0 or 1).";
            case "Attack range" -> "Reach values in order: normal min, normal max, Creative min, Creative max, hitbox margin, mob factor.";
            case "Consumable behavior" -> "Use duration, hand animation, and consume particles. Use the Animation button for a valid animation.";
            case "Use cooldown" -> "Cooldown after use. Enter seconds, or seconds|cooldown-group. Blank or clear removes it.";
            case "Custom model floats" -> "Custom model float values in order, separated by commas. Leave blank if unused.";
            case "Custom model flags" -> "Custom model boolean values in order. Enter true or false, separated by commas.";
            case "Custom model strings" -> "Custom model text values in order, separated by commas.";
            case "Custom model colors" -> "Custom model decimal RGB colors from 0 to 16777215, separated by commas.";
            case "Can place on" -> "Adventure placement rules. Enter block IDs separated by commas; #tag IDs are supported.";
            case "Can break" -> "Adventure breaking rules. Enter block IDs separated by commas; #tag IDs are supported.";
            case "Container contents" -> "Stored item list. Format: item_id*count, separated by commas.";
            case "Bundle contents" -> "Bundle item list. Format: item_id*count, separated by commas.";
            case "Charged projectiles" -> "Loaded projectiles. Format: arrow or firework*count, separated by commas.";
            case "Map color" -> "Map display color as decimal RGB from 0 to 16777215.";
            case "Food nutrition" -> "Hunger points restored. Valid range: 0-20.";
            case "Food saturation" -> "Saturation restored when eaten. Higher values keep hunger full longer.";
            case "Weapon damage per attack" -> "Durability damage dealt by each attack. Valid range: 0-100.";
            case "Weapon blocking disable seconds" -> "Seconds the target cannot block after being hit. Valid range: 0-10.";
            case "Firework flight duration" -> "Rocket flight duration. Valid range: 0-127; higher values fly farther.";
            case "Explosion colors", "Explosion fade colors" -> "Decimal RGB colors separated by commas. Example: 16711680,255.";
            case "Item model" -> "Item model registry ID. Leave blank to remove the custom model.";
            case "Tooltip style" -> "Tooltip style registry ID. Leave blank for the normal Minecraft style.";
            case "Enchantability" -> "How easily this item accepts enchantments. Valid range: 0-255.";
            case "Repair items" -> "Valid repair item IDs separated by commas.";
            case "Damage-resistant tag" -> "Damage-type tag resisted by this item. Leave blank to remove it.";
            case "Player-head profile" -> "Player name used by a player head. Leave blank to remove the profile.";
            case "Note-block sound" -> "Optional sound registry ID used when this item is placed on a note block.";
            case "Use effects" -> "Use values in order: sprint particles, vibrations, movement speed.";
            case "Swing animation" -> "Attack animation and duration. Use Type to choose a valid animation.";
            case "Piercing weapon" -> "Weapon flags in order: knockback and dismount, using 0 or 1.";
            case "Equippable" -> "Equipment behavior. Use Slot to choose the equipment slot; other values control interaction.";
            default -> "Controls " + label + ". Enter a value matching the format shown below.";
        };
        return Tooltip.create(CommonComponents.joinLines(
                Component.literal(label).withStyle(ChatFormatting.GOLD),
                CommonComponents.EMPTY,
                Component.literal(explanation).withStyle(ChatFormatting.WHITE),
                CommonComponents.EMPTY,
                Component.literal("Format: " + hint).withStyle(ChatFormatting.AQUA)));
    }

    private Tooltip buttonTooltip(String text) {
        String explanation = switch (text) {
            case "Repair" -> "Set durability damage to 0 and fully repair the item.";
            case "Half" -> "Set durability damage to half of the maximum.";
            case "Almost Broken" -> "Set durability to one use before breaking.";
            case "Remove Trim" -> "Remove the armor trim from this item.";
            case "Apply", "Apply Player" -> "Send the current values to the server. The server validates the values before applying them.";
            case "Duplicate" -> "Create a copy with the edited name, lore, enchantments, components, and attributes.";
            case "Clear Enchants" -> "Remove every enchantment from this item.";
            case "Delete" -> "Delete this item stack. This cannot be undone.";
            case "Close" -> "Close the editor without applying unsent changes.";
            case "Clear Dye" -> "Remove the custom dye color.";
            case "Add layer" -> "Add a new banner or shield pattern layer.";
            case "Update selected" -> "Replace the selected banner or shield layer.";
            case "Clear banner patterns" -> "Remove all banner or shield pattern layers.";
            case "Add / update" -> "Add the selected effect or update its existing entry.";
            case "Remove" -> "Remove the selected effect.";
            case "Clear effects" -> "Remove every configured effect.";
            case "Infinite: ON", "Infinite: OFF" -> "Toggle whether the selected effect lasts indefinitely.";
            case "Enabled: ON", "Enabled: OFF" -> "Toggle whether the selected effect is applied when changes are sent.";
            case "Particles: ON", "Particles: OFF" -> "Toggle the selected effect's world particles.";
            case "Icon: ON", "Icon: OFF" -> "Toggle the selected effect's HUD icon.";
            case "Ambient: ON", "Ambient: OFF" -> "Toggle Minecraft's ambient effect rendering style.";
            case "Change item variant" -> "Open the related-item selector without using a separate hotkey.";
            default -> text.isBlank() ? "Change the setting shown on this control." : "Open or change " + text + ".";
        };
        String title = text.isBlank() ? "Setting" : text;
        ChatFormatting titleColor = title.equals("Delete") || title.startsWith("Reset")
                ? ChatFormatting.RED : ChatFormatting.GREEN;
        return Tooltip.create(CommonComponents.joinLines(
                Component.literal(title).withStyle(titleColor),
                CommonComponents.EMPTY,
                Component.literal(explanation).withStyle(ChatFormatting.WHITE)));
    }

    private void setButtonTooltip(Button button, String text) {
        if (button != null) {
            button.setTooltip(Tooltip.create(CommonComponents.joinLines(
                    Component.literal("Details").withStyle(ChatFormatting.GOLD),
                    CommonComponents.EMPTY,
                    Component.literal(text).withStyle(ChatFormatting.WHITE))));
        }
    }

    private void registerSuggestions(EditBox box, String label) {
        List<String> options = switch (label) {
            case "Repair items", "Container contents", "Bundle contents", "Charged projectiles" -> registrySuggestions(Registries.ITEM);
            case "Can place on", "Can break" -> blockSuggestions();
            case "Potion" -> registrySuggestions(Registries.POTION);
            default -> List.of();
        };
        if (!options.isEmpty()) {
            this.suggestionFields.put(box, label);
            this.suggestionOptions.put(box, options);
        }
    }

    private List<String> blockSuggestions() {
        List<String> options = new ArrayList<>(registrySuggestions(Registries.BLOCK));
        options.addAll(registryTagSuggestions(Registries.BLOCK));
        return options.stream().sorted().toList();
    }

    private <T> List<String> registrySuggestions(net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<T>> key) {
        if (this.minecraft == null || this.minecraft.level == null) return List.of();
        return this.minecraft.level.registryAccess().lookupOrThrow(key).listElements()
                .flatMap(holder -> holder.unwrapKey().stream())
                .map(registryKey -> registryKey.identifier().toString())
                .sorted()
                .toList();
    }

    private <T> List<String> registryTagSuggestions(net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<T>> key) {
        if (this.minecraft == null || this.minecraft.level == null) return List.of();
        return this.minecraft.level.registryAccess().lookupOrThrow(key).listTagIds()
                .map(tag -> "#" + tag.location())
                .sorted()
                .toList();
    }

    private boolean suggestionMatches(String candidate, String token) {
        String candidateLower = candidate.toLowerCase(java.util.Locale.ROOT);
        String tokenLower = token.toLowerCase(java.util.Locale.ROOT);
        if (candidateLower.startsWith(tokenLower)) return true;
        boolean candidateTag = candidateLower.startsWith("#");
        boolean tokenTag = tokenLower.startsWith("#");
        if (candidateTag != tokenTag) return false;
        String candidateId = candidateTag ? candidateLower.substring(1) : candidateLower;
        String tokenId = tokenTag ? tokenLower.substring(1) : tokenLower;
        return !tokenId.contains(":") && candidateId.endsWith(":" + tokenId);
    }

    private void updateSuggestions() {
        EditBox nextOwner = null;
        List<String> nextSuggestions = List.of();
        String nextToken = "";
        int nextTokenStart = 0;
        int nextCursor = 0;
        for (Map.Entry<EditBox, String> entry : this.suggestionFields.entrySet()) {
            EditBox box = entry.getKey();
            if (!box.isFocused()) {
                box.setSuggestion("");
                continue;
            }
            String value = box.getValue();
            int cursor = Math.max(0, Math.min(value.length(), box.getCursorPosition()));
            int tokenStart = Math.max(value.lastIndexOf(',', Math.max(0, cursor - 1)),
                    value.lastIndexOf(' ', Math.max(0, cursor - 1))) + 1;
            String token = value.substring(tokenStart, cursor).trim();
            int countSeparator = token.indexOf('*');
            if (countSeparator >= 0) token = token.substring(0, countSeparator);
            if (token.isBlank() || token.startsWith("#")) {
                box.setSuggestion("");
                continue;
            }
            String lower = token.toLowerCase(java.util.Locale.ROOT);
            List<String> matches = this.suggestionOptions.getOrDefault(box, List.of()).stream()
                    .filter(id -> suggestionMatches(id, lower))
                    .limit(8)
                    .toList();
            if (matches.isEmpty() || (matches.size() == 1 && matches.get(0).equalsIgnoreCase(token))) {
                box.setSuggestion("");
            } else {
                String match = matches.get(0);
                box.setSuggestion(match.substring(Math.min(token.length(), match.length())));
                nextOwner = box;
                nextSuggestions = matches;
                nextToken = token;
                nextTokenStart = tokenStart;
                nextCursor = cursor;
                break;
            }
        }
        if (nextOwner == null) {
            clearSuggestionPopup();
            return;
        }
        if (this.suggestionOwner != nextOwner || !this.suggestionToken.equals(nextToken)
                || !this.activeSuggestions.equals(nextSuggestions)) {
            this.selectedSuggestion = 0;
        } else {
            this.selectedSuggestion = Math.min(this.selectedSuggestion, nextSuggestions.size() - 1);
        }
        this.suggestionOwner = nextOwner;
        this.activeSuggestions = nextSuggestions;
        this.suggestionToken = nextToken;
        this.suggestionTokenStart = nextTokenStart;
        this.suggestionCursor = nextCursor;
    }

    private void clearSuggestionPopup() {
        this.suggestionOwner = null;
        this.activeSuggestions = List.of();
        this.suggestionToken = "";
        this.suggestionTokenStart = 0;
        this.suggestionCursor = 0;
        this.selectedSuggestion = 0;
    }

    private int suggestionRowHeight() {
        return 20;
    }

    private int suggestionPopupHeight() {
        return this.activeSuggestions.size() * suggestionRowHeight();
    }

    private int suggestionPopupY() {
        if (this.suggestionOwner == null || this.layout == null) return 0;
        int height = suggestionPopupHeight();
        int below = this.suggestionOwner.getY() + this.suggestionOwner.getHeight() + 2;
        int above = this.suggestionOwner.getY() - height - 2;
        if (below + height <= this.layout.contentBottom()) return below;
        if (above >= this.layout.contentTop()) return above;
        return Math.max(this.layout.contentTop(), Math.min(this.layout.contentBottom() - height, below));
    }

    private boolean isOverSuggestionPopup(double mouseX, double mouseY) {
        if (this.suggestionOwner == null || this.activeSuggestions.isEmpty()) return false;
        int x = this.suggestionOwner.getX();
        int y = suggestionPopupY();
        return mouseX >= x && mouseX < x + this.suggestionOwner.getWidth()
                && mouseY >= y && mouseY < y + suggestionPopupHeight();
    }

    private void renderSuggestionPopup(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.suggestionOwner == null || this.activeSuggestions.isEmpty()) return;
        int x = this.suggestionOwner.getX();
        int y = suggestionPopupY();
        int width = this.suggestionOwner.getWidth();
        int rowHeight = suggestionRowHeight();
        graphics.fill(x, y, x + width, y + suggestionPopupHeight(), 0xF0181818);
        graphics.renderOutline(x, y, width, suggestionPopupHeight(), 0xFF000000);
        for (int i = 0; i < this.activeSuggestions.size(); i++) {
            int rowY = y + i * rowHeight;
            if (i == this.selectedSuggestion || (mouseX >= x && mouseX < x + width
                    && mouseY >= rowY && mouseY < rowY + rowHeight)) {
                graphics.fill(x + 1, rowY + 1, x + width - 1, rowY + rowHeight - 1,
                        i == this.selectedSuggestion ? 0xFF3B4F68 : 0xFF303030);
            }
            graphics.drawString(this.font, Component.literal(this.activeSuggestions.get(i)),
                    x + 8, rowY + 6, 0xFFFFFFFF);
        }
    }

    private boolean acceptSuggestion(int index) {
        if (this.suggestionOwner == null || index < 0 || index >= this.activeSuggestions.size()) return false;
        String value = this.suggestionOwner.getValue();
        int cursor = Math.max(0, Math.min(value.length(), this.suggestionCursor));
        int start = Math.max(0, Math.min(value.length(), this.suggestionTokenStart));
        String replacement = this.activeSuggestions.get(index);
        String next = value.substring(0, start) + replacement + value.substring(cursor);
        this.suggestionOwner.setValue(next);
        this.suggestionOwner.setCursorPosition(start + replacement.length());
        this.suggestionOwner.setSuggestion("");
        clearSuggestionPopup();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.suggestionOwner != null && !this.activeSuggestions.isEmpty()) {
            int keyCode = event.key();
            if (keyCode == 264) {
                this.selectedSuggestion = (this.selectedSuggestion + this.activeSuggestions.size() - 1)
                        % this.activeSuggestions.size();
                return true;
            }
            if (keyCode == 265) {
                this.selectedSuggestion = (this.selectedSuggestion + 1) % this.activeSuggestions.size();
                return true;
            }
            if (keyCode == 257 || keyCode == 335 || keyCode == 258) {
                return acceptSuggestion(this.selectedSuggestion);
            }
            if (keyCode == 256) {
                clearSuggestionPopup();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void tick() {
        super.tick();
        updateSuggestions();
    }

    private ChoiceList addChoiceList(Tab tab, int x, int y, List<ChoiceValue> choices,
                                     int selectedIndex, IntConsumer onSelect) {
        // Choice lists are popups. Give them a useful scrollable viewport and fit them to the
        // content area when opened, instead of shrinking late-tab lists to a few pixels.
        int height = Math.min(192, Math.max(24,
                this.layout.contentBottom() - this.layout.contentTop()));
        ChoiceList list = addTabWidget(tab, new ChoiceList(x, y, this.layout.contentWidth(), height,
                choices, selectedIndex, onSelect));
        this.choiceLists.add(list);
        return list;
    }

    private void captureTabLayout() {
        for (Tab tab : Tab.values()) {
            for (AbstractWidget widget : this.tabWidgets.get(tab)) {
                this.tabWidgetBaseY.put(widget, widget.getY());
            }
            this.tabScrollOffsets.put(tab, 0);
        }
        refreshTabScrollLayout();
    }

    private int choiceExpansion(Tab tab) {
        if (this.openChoiceList == null || !this.tabWidgets.get(tab).contains(this.openChoiceList)) return 0;
        return this.openChoiceList.opensAbove ? 0 : this.openChoiceList.getHeight() + 6;
    }

    private int choiceAnchorBaseY(ChoiceList list) {
        return list.anchorBaseY;
    }

    private int choiceAnchorHeight(ChoiceList list) {
        return this.layout.fieldHeight();
    }

    private boolean belowOpenChoice(Tab tab, int baseY) {
        if (this.openChoiceList == null || !this.tabWidgets.get(tab).contains(this.openChoiceList)) return false;
        return baseY > choiceAnchorBaseY(this.openChoiceList);
    }

    private int shiftedY(Tab tab, int baseY) {
        return baseY + (belowOpenChoice(tab, baseY) ? choiceExpansion(tab) : 0);
    }

    private int tabDocumentBottom(Tab tab) {
        int maxBottom = this.layout.contentTop();
        for (AbstractWidget widget : this.tabWidgets.get(tab)) {
            if (widget instanceof ChoiceList) continue;
            Integer baseY = this.tabWidgetBaseY.get(widget);
            if (baseY != null) maxBottom = Math.max(maxBottom, shiftedY(tab, baseY) + widget.getHeight());
        }
        for (FieldLabel label : this.labels.get(tab)) {
            maxBottom = Math.max(maxBottom, shiftedY(tab, label.y()) + this.font.lineHeight);
        }
        if (this.openChoiceList != null && !this.openChoiceList.opensAbove
                && this.tabWidgets.get(tab).contains(this.openChoiceList)) {
            int anchorY = choiceAnchorBaseY(this.openChoiceList);
            maxBottom = Math.max(maxBottom, anchorY + choiceAnchorHeight(this.openChoiceList) + 2
                    + this.openChoiceList.getHeight());
        }
        return maxBottom;
    }

    private void refreshTabScrollLayout() {
        for (Tab tab : Tab.values()) {
            int maximum = Math.max(0, tabDocumentBottom(tab) - this.layout.contentBottom() + 6);
            this.tabScrollMaximums.put(tab, maximum);
            this.tabScrollOffsets.put(tab,
                    Math.min(this.tabScrollOffsets.getOrDefault(tab, 0), maximum));
        }
        if (this.currentTab != null) applyTabScrollPositions(this.currentTab);
    }

    private int tabScrollOffset(Tab tab) {
        return this.tabScrollOffsets.getOrDefault(tab, 0);
    }

    private void applyTabScrollPositions(Tab tab) {
        int offset = tabScrollOffset(tab);
        for (AbstractWidget widget : this.tabWidgets.get(tab)) {
            Integer baseY = this.tabWidgetBaseY.get(widget);
            if (baseY != null) {
                int y = widget instanceof ChoiceList ? baseY : shiftedY(tab, baseY);
                widget.setY(y - offset);
            }
        }
        for (ChoiceList list : this.choiceLists) {
            if (this.tabWidgets.get(tab).contains(list) && list.visible) fitChoiceList(list);
        }
    }

    private void setTabScrollOffset(Tab tab, int offset) {
        int maximum = this.tabScrollMaximums.getOrDefault(tab, 0);
        this.tabScrollOffsets.put(tab, Math.max(0, Math.min(maximum, offset)));
        applyTabScrollPositions(tab);
    }

    private void fitChoiceList(ChoiceList list) {
        int anchorY = choiceAnchorBaseY(list) - tabScrollOffset(this.currentTab);
        int belowY = anchorY + choiceAnchorHeight(list) + 2;
        int aboveY = anchorY - list.getHeight() - 2;
        int contentTop = this.layout.contentTop();
        int contentBottom = this.layout.contentBottom();
        boolean fitsBelow = belowY + list.getHeight() <= contentBottom;
        boolean fitsAbove = aboveY >= contentTop;
        list.opensAbove = !fitsBelow && fitsAbove;
        int popupY = list.opensAbove ? aboveY : belowY;
        popupY = Math.max(contentTop, Math.min(contentBottom - list.getHeight(), popupY));
        list.setY(popupY);
    }

    private void scrollChoiceBelowButton(ChoiceList list) {
        if (list.opensAbove) return;
        int baseY = choiceAnchorBaseY(list);
        int popupBottom = baseY - tabScrollOffset(this.currentTab)
                + choiceAnchorHeight(list) + 2 + list.getHeight();
        int needed = popupBottom - this.layout.contentBottom();
        if (needed > 0) {
            setTabScrollOffset(this.currentTab, tabScrollOffset(this.currentTab) + needed);
        }
    }

    private void openChoiceList(ChoiceList list) {
        if (list != null && this.openChoiceList == list && list.visible) {
            closeChoiceLists();
            return;
        }
        for (ChoiceList other : this.choiceLists) {
            other.visible = false;
            other.active = false;
        }
        this.openChoiceList = list;
        if (list == null) {
            refreshTabScrollLayout();
            return;
        }
        if (list == this.consumableAnimationList) list.setSelectedIndex(consumableAnimationIndex());
        if (list == this.swingAnimationList) list.setSelectedIndex(swingAnimationIndex());
        if (list == this.equippableSlotList) list.setSelectedIndex(equippableSlotIndex());
        list.visible = true;
        list.active = true;
        list.refresh();
        refreshTabScrollLayout();
        scrollChoiceBelowButton(list);
        fitChoiceList(list);
    }

    private void closeChoiceLists() {
        this.openChoiceList = null;
        for (ChoiceList list : this.choiceLists) {
            list.visible = false;
            list.active = false;
        }
        refreshTabScrollLayout();
    }

    private <T extends AbstractWidget> T addFooterWidget(T widget) {
        this.footerWidgets.add(widget);
        if (widget instanceof Button button) {
            button.setTooltip(buttonTooltip(button.getMessage().getString()));
        }
        this.addRenderableWidget(widget);
        return widget;
    }

    private void label(Tab tab, String text, int y) {
        labelAt(tab, text, this.layout.contentX(), y);
    }

    private void labelAt(Tab tab, String text, int x, int y) {
        this.labels.get(tab).add(new FieldLabel(text, x, y));
    }

    private EditBox field(Tab tab, String label, String hint, int y, String value, int maxLength) {
        label(tab, label, y);
        int inputY = y + this.font.lineHeight + 2;
        EditBox box = new EditBox(this.font, this.layout.contentX(), inputY,
                this.layout.contentWidth(), this.layout.fieldHeight(), Component.literal(label));
        box.setHint(Component.literal(hint));
        box.setTooltip(fieldTooltip(label, hint));
        box.setValue(value);
        box.setMaxLength(maxLength);
        EditBox result = addTabWidget(tab, box);
        registerSuggestions(result, label);
        return result;
    }

    private EditBox fieldAt(Tab tab, String label, String hint, int x, int y, int width,
                            String value, int maxLength) {
        labelAt(tab, label, x, y);
        int inputY = y + this.font.lineHeight + 2;
        EditBox box = new EditBox(this.font, x, inputY, width, this.layout.fieldHeight(), Component.literal(label));
        box.setHint(Component.literal(hint));
        box.setTooltip(fieldTooltip(label, hint));
        box.setValue(value == null ? "" : value);
        box.setMaxLength(maxLength);
        EditBox result = addTabWidget(tab, box);
        registerSuggestions(result, label);
        return result;
    }

    private EditBox fieldWithChoiceButton(Tab tab, String label, String hint, int y, String value,
                                           int maxLength, String buttonText, Runnable openChoices) {
        label(tab, label, y);
        int inputY = y + this.font.lineHeight + 2;
        int buttonWidth = Math.min(92, Math.max(64, this.layout.contentWidth() / 4));
        int inputWidth = Math.max(1, this.layout.contentWidth() - buttonWidth - 4);
        EditBox box = new EditBox(this.font, this.layout.contentX(), inputY, inputWidth,
                this.layout.fieldHeight(), Component.literal(label));
        box.setHint(Component.literal(hint));
        box.setTooltip(fieldTooltip(label, hint));
        box.setValue(value);
        box.setMaxLength(maxLength);
        addTabWidget(tab, box);
        registerSuggestions(box, label);
        Button choiceButton = addTabWidget(tab, Button.builder(Component.literal(buttonText), ignored -> openChoices.run())
                .pos(this.layout.contentX() + inputWidth + 4, inputY)
                .size(buttonWidth, this.layout.fieldHeight()).build());
        choiceButton.setTooltip(Tooltip.create(Component.literal("Choose a value for " + label + ".")));
        return box;
    }

    private int nextRow(int y) {
        return y + this.layout.rowHeight();
    }

    private void buildTabs() {
        List<TabSpec> tabs = new ArrayList<>();
        tabs.add(new TabSpec("General", Tab.GENERAL));
        tabs.add(new TabSpec("Player", Tab.PLAYER));
        tabs.add(new TabSpec("Enchants", Tab.ENCHANTMENTS));
        if (this.trimCapable || this.dyeCapable) tabs.add(new TabSpec("Visuals", Tab.VISUALS));
        if (this.attributeCapable) tabs.add(new TabSpec("Attributes", Tab.ATTRIBUTES));
        tabs.add(new TabSpec("Components", Tab.COMPONENTS));
        if (this.specialCapable) tabs.add(new TabSpec("Special", Tab.SPECIAL));
        tabs.add(new TabSpec("Advanced", Tab.ADVANCED));
        tabs.add(new TabSpec("Data", Tab.DATA));
        if (this.bannerCapable) tabs.add(new TabSpec(this.shieldCapable ? "Shield" : "Banner", Tab.BANNER));

        int gap = 3;
        int available = Math.max(1, this.width - 16);
        int columns = Math.max(1, this.layout.tabColumns());
        int width = Math.max(1, (available - gap * (columns - 1)) / columns);
        for (int index = 0; index < tabs.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            int totalWidth = columns * width + (columns - 1) * gap;
            int x = Math.max(0, (this.width - totalWidth) / 2) + column * (width + gap);
            int y = this.layout.tabY() + row * (this.layout.tabHeight() + 2);
            addTabButton(tabs.get(index).label(), x, y, tabs.get(index).tab(), width);
        }
    }

    private record TabSpec(String label, Tab tab) {}

    private void addTabButton(String text, int x, int y, Tab tab, int width) {
        Button button = Button.builder(Component.literal(text), ignored -> switchTab(tab))
                .pos(x, y).size(width, this.layout.tabHeight()).build();
        button.setTooltip(Tooltip.create(CommonComponents.joinLines(Component.literal(text + " tab"),
                Component.literal("Open this editor section."))));
        this.tabButtons.add(button);
        this.addRenderableWidget(button);
    }

    private void buildGeneral(ItemStack held) {
        int y = this.layout.contentTop();
        Component name = held.get(DataComponents.CUSTOM_NAME);
        this.nameBox = field(Tab.GENERAL, "Name", "Leave blank to remove custom name", y,
                name == null ? "" : name.getString(), 50);
        y = nextRow(y);
        ItemLore lore = held.get(DataComponents.LORE);
        String loreValue = lore == null ? "" : String.join("|", lore.lines().stream().map(Component::getString).toList());
        this.loreBox = field(Tab.GENERAL, "Lore", "Use | for separate lines", y, loreValue, 1024);
        y = nextRow(y);
        this.damageBox = field(Tab.GENERAL, held.isDamageableItem() ? "Damage" : "Damage (unsupported)",
                held.isDamageableItem() ? "0 = fully repaired" : "Item cannot take damage", y,
                Integer.toString(held.isDamageableItem() ? held.getDamageValue() : 0), 7);
        this.damageBox.active = held.isDamageableItem();
        y = nextRow(y);
        label(Tab.GENERAL, "Durability shortcuts", y);
        int quickY = y + 10;
        int gap = 3;
        int width = (this.layout.contentWidth() - gap * 2) / 3;
        Button repair = addTabWidget(Tab.GENERAL, Button.builder(Component.literal("Repair"), ignored -> this.damageBox.setValue("0"))
                .pos(this.layout.contentX(), quickY).size(width, this.layout.fieldHeight()).build());
        Button half = addTabWidget(Tab.GENERAL, Button.builder(Component.literal("Half"), ignored -> this.damageBox.setValue(Integer.toString(held.isDamageableItem() ? held.getMaxDamage() / 2 : 0)))
                .pos(this.layout.contentX() + width + gap, quickY).size(width, this.layout.fieldHeight()).build());
        Button almostBroken = addTabWidget(Tab.GENERAL, Button.builder(Component.literal("Almost Broken"), ignored -> this.damageBox.setValue(Integer.toString(held.isDamageableItem() ? Math.max(0, held.getMaxDamage() - 1) : 0)))
                .pos(this.layout.contentX() + (width + gap) * 2, quickY).size(width, this.layout.fieldHeight()).build());
        repair.active = held.isDamageableItem();
        half.active = held.isDamageableItem();
        almostBroken.active = held.isDamageableItem();
        y = nextRow(y);
        label(Tab.GENERAL, "Item protection", y);
        this.unbreakableButton = addTabWidget(Tab.GENERAL, Button.builder(Component.empty(), ignored -> {
            this.unbreakable = !this.unbreakable;
            refreshUnbreakable();
        }).pos(this.layout.contentX(), y + 10).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
        refreshUnbreakable();
        y = nextRow(y);
        label(Tab.GENERAL, "Infinite item use", y);
        this.infiniteUseButton = addTabWidget(Tab.GENERAL, Button.builder(Component.empty(), ignored -> {
            this.infiniteUse = !this.infiniteUse;
            if (this.infiniteUse) removeInfinitySelection();
            refreshInfiniteUse();
        }).pos(this.layout.contentX(), y + 10).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
        refreshInfiniteUse();
        y = nextRow(y);
        label(Tab.GENERAL, "Item variant", y);
        this.itemVariantButton = addTabWidget(Tab.GENERAL,
                Button.builder(Component.literal("Change item variant"), ignored -> openVariantEditor())
                        .pos(this.layout.contentX(), y + 10)
                        .size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
        setButtonTooltip(this.itemVariantButton,
                "Open the item variant selector. Choose related forms such as wood, stone, cooked, or metal variants.");
    }

    private void buildPlayer() {
        if (this.minecraft.player == null) return;
        int y = this.layout.contentTop();
        Player player = this.minecraft.player;
        int columnGap = 8;
        int columnWidth = Math.max(1, (this.layout.contentWidth() - columnGap) / 2);
        int rightX = this.layout.contentX() + columnWidth + columnGap;

        // Keep related player values on the same row. This cuts the vertical document
        // height in half and leaves both effect lists a useful scrollable area.
        this.playerMaxHealthBox = fieldAt(Tab.PLAYER, "Max health", "1-1024", this.layout.contentX(), y,
                columnWidth, format(player.getMaxHealth()), 12);
        this.playerHealthBox = fieldAt(Tab.PLAYER, "Health", "0 to max health", rightX, y,
                columnWidth, format(player.getHealth()), 12);
        y = nextRow(y);
        this.playerFoodBox = fieldAt(Tab.PLAYER, "Food", "0-20", this.layout.contentX(), y,
                columnWidth, Integer.toString(player.getFoodData().getFoodLevel()), 4);
        this.playerSaturationBox = fieldAt(Tab.PLAYER, "Saturation", "0-20", rightX, y,
                columnWidth, format(player.getFoodData().getSaturationLevel()), 12);
        y = nextRow(y);
        this.playerLevelBox = fieldAt(Tab.PLAYER, "XP level", "0 or higher", this.layout.contentX(), y,
                columnWidth, Integer.toString(player.experienceLevel), 8);
        this.playerSpeedBox = fieldAt(Tab.PLAYER, "Movement speed", "0-10", rightX, y,
                columnWidth, format(player.getAttributeValue(Attributes.MOVEMENT_SPEED)), 12);
        y = nextRow(y);

        if (ToughAsNailsCompat.isAvailable()) {
            this.playerThirstBox = fieldAt(Tab.PLAYER, "Thirst", "0-20", this.layout.contentX(), y,
                    columnWidth, Integer.toString(ToughAsNailsCompat.thirst(player)), 4);
            this.playerHydrationBox = fieldAt(Tab.PLAYER, "Hydration", "0-20", rightX, y,
                    columnWidth, format(ToughAsNailsCompat.hydration(player)), 12);
            y = nextRow(y);
            this.playerExhaustionBox = fieldAt(Tab.PLAYER, "Exhaustion", "0-40", this.layout.contentX(), y,
                    columnWidth, format(ToughAsNailsCompat.exhaustion(player)), 12);
            this.playerTemperatureBox = fieldAt(Tab.PLAYER, "Temperature", "0 icy - 4 hot", rightX, y,
                    columnWidth, Integer.toString(ToughAsNailsCompat.temperature(player)), 4);
            y = nextRow(y);
        }

        label(Tab.PLAYER, "Active effects", y);
        int listTop = y + this.font.lineHeight + 2;
        int gap = 6;
        int listWidth = Math.max(80, (this.layout.contentWidth() - gap) / 2);
        int editorReserve = this.layout.rowHeight() * 3 + 16;
        int listHeight = Math.max(72, this.layout.contentBottom() - listTop - editorReserve);
        this.playerEffectList = addTabWidget(Tab.PLAYER,
                new PlayerEffectList(this.layout.contentX(), listTop, listWidth, listHeight));
        int choiceX = this.layout.contentX() + listWidth + gap;
        this.playerEffectSearchBox = fieldAt(Tab.PLAYER, "Search effects", "name or registry ID", choiceX,
                y, listWidth, "", 96);
        this.playerEffectSearchBox.setResponder(ignored -> {
            if (this.playerEffectChoiceList != null) this.playerEffectChoiceList.refresh();
        });
        int choiceTop = listTop + this.layout.fieldHeight() + 4;
        int choiceHeight = Math.max(48, listHeight - this.layout.fieldHeight() - 4);
        this.playerEffectChoiceList = addTabWidget(Tab.PLAYER,
                new PlayerEffectChoiceList(choiceX, choiceTop, listWidth, choiceHeight));

        int editorY = listTop + listHeight + 6;
        this.playerEffectDurationBox = fieldAt(Tab.PLAYER, "Effect duration (seconds)",
                "1-107374182; blank only for infinite", this.layout.contentX(), editorY, listWidth,
                selectedPlayerEffectDuration(), 16);
        this.playerEffectLevelBox = fieldAt(Tab.PLAYER, "Effect level", "1-256",
                this.layout.contentX() + listWidth + gap, editorY, listWidth,
                selectedPlayerEffectLevel(), 8);
        editorY += this.layout.rowHeight();
        int buttonGap = 4;
        int buttonWidth = Math.max(1, (this.layout.contentWidth() - buttonGap * 4) / 5);
        this.playerEffectInfiniteButton = addTabWidget(Tab.PLAYER, Button.builder(Component.empty(), ignored -> togglePlayerEffectInfinite())
                .pos(this.layout.contentX(), editorY).size(buttonWidth, this.layout.fieldHeight()).build());
        this.playerEffectEnabledButton = addTabWidget(Tab.PLAYER, Button.builder(Component.empty(), ignored -> togglePlayerEffectEnabled())
                .pos(this.layout.contentX() + buttonWidth + buttonGap, editorY).size(buttonWidth, this.layout.fieldHeight()).build());
        this.playerEffectParticlesButton = addTabWidget(Tab.PLAYER, Button.builder(Component.empty(), ignored -> togglePlayerEffectParticles())
                .pos(this.layout.contentX() + (buttonWidth + buttonGap) * 2, editorY).size(buttonWidth, this.layout.fieldHeight()).build());
        this.playerEffectIconButton = addTabWidget(Tab.PLAYER, Button.builder(Component.empty(), ignored -> togglePlayerEffectIcon())
                .pos(this.layout.contentX() + (buttonWidth + buttonGap) * 3, editorY).size(buttonWidth, this.layout.fieldHeight()).build());
        this.playerEffectAmbientButton = addTabWidget(Tab.PLAYER, Button.builder(Component.empty(), ignored -> togglePlayerEffectAmbient())
                .pos(this.layout.contentX() + (buttonWidth + buttonGap) * 4, editorY).size(buttonWidth, this.layout.fieldHeight()).build());
        editorY += nextRow(0);
        int actionWidth = Math.max(1, (this.layout.contentWidth() - buttonGap) / 2);
        this.playerEffectRemoveButton = addTabWidget(Tab.PLAYER,
                Button.builder(Component.literal("Remove effect"), ignored -> removePlayerEffect())
                        .pos(this.layout.contentX(), editorY).size(actionWidth, this.layout.fieldHeight()).build());
        this.playerEffectClearButton = addTabWidget(Tab.PLAYER,
                Button.builder(Component.literal("Clear effects"), ignored -> clearPlayerEffects())
                        .pos(this.layout.contentX() + actionWidth + buttonGap, editorY)
                        .size(actionWidth, this.layout.fieldHeight()).build());
        refreshPlayerEffectEditor();
    }

    private void loadPlayerEffects() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        for (MobEffectInstance instance : this.minecraft.player.getActiveEffects()) {
            this.playerEffectEdits.add(new EffectEdit(instance.getEffect(), instance.getDuration(),
                    instance.getAmplifier(), true, instance.isVisible(), instance.showIcon(), instance.isAmbient()));
        }
        if (!this.playerEffectEdits.isEmpty()) this.selectedPlayerEffect = 0;
    }

    private EffectEdit selectedPlayerEffectEdit() {
        return this.selectedPlayerEffect >= 0 && this.selectedPlayerEffect < this.playerEffectEdits.size()
                ? this.playerEffectEdits.get(this.selectedPlayerEffect) : null;
    }

    private String selectedPlayerEffectDuration() {
        EffectEdit edit = selectedPlayerEffectEdit();
        return edit == null || edit.duration < 0 ? "" : Integer.toString(Math.max(1, edit.duration / 20));
    }

    private String selectedPlayerEffectLevel() {
        EffectEdit edit = selectedPlayerEffectEdit();
        return edit == null ? "1" : Integer.toString(edit.amplifier + 1);
    }

    private void selectPlayerEffect(int index) {
        if (index < 0 || index >= this.playerEffectEdits.size()) return;
        this.selectedPlayerEffect = index;
        refreshPlayerEffectEditor();
    }

    private void addPlayerEffect(Holder<MobEffect> holder) {
        for (int index = 0; index < this.playerEffectEdits.size(); index++) {
            if (this.playerEffectEdits.get(index).effect.equals(holder)) {
                this.selectedPlayerEffect = index;
                refreshPlayerEffectEditor();
                return;
            }
        }
        this.playerEffectEdits.add(new EffectEdit(holder, 200, 0, true, true, true, false));
        this.selectedPlayerEffect = this.playerEffectEdits.size() - 1;
        if (this.playerEffectList != null) this.playerEffectList.refresh();
        refreshPlayerEffectEditor();
    }

    private void refreshPlayerEffectEditor() {
        EffectEdit edit = selectedPlayerEffectEdit();
        if (this.playerEffectDurationBox != null) this.playerEffectDurationBox.setValue(selectedPlayerEffectDuration());
        if (this.playerEffectLevelBox != null) this.playerEffectLevelBox.setValue(selectedPlayerEffectLevel());
        if (this.playerEffectInfiniteButton != null) {
            this.playerEffectInfiniteButton.setMessage(Component.literal("Infinite: " + (edit != null && edit.duration < 0 ? "ON" : "OFF")));
            this.playerEffectInfiniteButton.active = edit != null;
        }
        if (this.playerEffectEnabledButton != null) {
            this.playerEffectEnabledButton.setMessage(Component.literal("Enabled: " + (edit != null && edit.enabled ? "ON" : "OFF")));
            this.playerEffectEnabledButton.active = edit != null;
        }
        if (this.playerEffectParticlesButton != null) {
            this.playerEffectParticlesButton.setMessage(Component.literal("Particles: " + (edit != null && edit.particles ? "ON" : "OFF")));
            this.playerEffectParticlesButton.active = edit != null;
        }
        if (this.playerEffectIconButton != null) {
            this.playerEffectIconButton.setMessage(Component.literal("Icon: " + (edit != null && edit.icon ? "ON" : "OFF")));
            this.playerEffectIconButton.active = edit != null;
        }
        if (this.playerEffectAmbientButton != null) {
            this.playerEffectAmbientButton.setMessage(Component.literal("Ambient: " + (edit != null && edit.ambient ? "ON" : "OFF")));
            this.playerEffectAmbientButton.active = edit != null;
        }
        if (this.playerEffectDurationBox != null) this.playerEffectDurationBox.active = edit == null || edit.duration >= 0;
        if (this.playerEffectRemoveButton != null) this.playerEffectRemoveButton.active = edit != null;
        if (this.playerEffectList != null) this.playerEffectList.refresh();
    }

    private void togglePlayerEffectInfinite() {
        EffectEdit edit = selectedPlayerEffectEdit();
        if (edit == null) return;
        edit.duration = edit.duration < 0 ? 200 : MobEffectInstance.INFINITE_DURATION;
        refreshPlayerEffectEditor();
    }

    private void togglePlayerEffectEnabled() {
        EffectEdit edit = selectedPlayerEffectEdit();
        if (edit == null) return;
        edit.enabled = !edit.enabled;
        refreshPlayerEffectEditor();
    }

    private void togglePlayerEffectParticles() {
        EffectEdit edit = selectedPlayerEffectEdit();
        if (edit == null) return;
        edit.particles = !edit.particles;
        refreshPlayerEffectEditor();
    }

    private void togglePlayerEffectIcon() {
        EffectEdit edit = selectedPlayerEffectEdit();
        if (edit == null) return;
        edit.icon = !edit.icon;
        refreshPlayerEffectEditor();
    }

    private void togglePlayerEffectAmbient() {
        EffectEdit edit = selectedPlayerEffectEdit();
        if (edit == null) return;
        edit.ambient = !edit.ambient;
        refreshPlayerEffectEditor();
    }

    private void removePlayerEffect() {
        if (selectedPlayerEffectEdit() == null) return;
        this.playerEffectEdits.remove(this.selectedPlayerEffect);
        this.selectedPlayerEffect = Math.min(this.selectedPlayerEffect, this.playerEffectEdits.size() - 1);
        refreshPlayerEffectEditor();
    }

    private void clearPlayerEffects() {
        this.playerEffectEdits.clear();
        this.selectedPlayerEffect = -1;
        refreshPlayerEffectEditor();
    }

    private void updateSelectedPlayerEffect() {
        EffectEdit edit = selectedPlayerEffectEdit();
        if (edit == null) return;
        Integer level = parseBoundedInt(this.playerEffectLevelBox.getValue(), 1, 256);
        if (level == null) throw new NumberFormatException();
        edit.amplifier = level - 1;
        if (edit.duration >= 0) {
            Integer seconds = parseBoundedInt(this.playerEffectDurationBox.getValue(), 1, Integer.MAX_VALUE / 20);
            if (seconds == null) throw new NumberFormatException();
            edit.duration = seconds * 20;
        }
    }

    private String encodePlayerEffects() {
        List<String> values = new ArrayList<>();
        for (EffectEdit edit : this.playerEffectEdits) {
            if (!edit.enabled) continue;
            String id = edit.effect.unwrapKey().map(key -> key.identifier().toString()).orElse("");
            if (id.isBlank()) continue;
            values.add(id + "=" + edit.amplifier + "," + edit.duration + ","
                    + edit.ambient + "," + edit.particles + "," + edit.icon);
        }
        return String.join(";", values);
    }

    private void applyPlayerEdits() {
        if (this.minecraft.player == null) return;
        try {
            double maxHealth = Double.parseDouble(this.playerMaxHealthBox.getValue().trim());
            double health = Double.parseDouble(this.playerHealthBox.getValue().trim());
            int food = Integer.parseInt(this.playerFoodBox.getValue().trim());
            float saturation = Float.parseFloat(this.playerSaturationBox.getValue().trim());
            int level = Integer.parseInt(this.playerLevelBox.getValue().trim());
            double speed = Double.parseDouble(this.playerSpeedBox.getValue().trim());
            int thirst = optionalPlayerInt(this.playerThirstBox, -1);
            float hydration = optionalPlayerFloat(this.playerHydrationBox, -1.0F);
            float exhaustion = optionalPlayerFloat(this.playerExhaustionBox, -1.0F);
            int temperature = optionalPlayerInt(this.playerTemperatureBox, -1);
            if (!Double.isFinite(maxHealth) || !Double.isFinite(health) || !Double.isFinite(speed)
                    || !Float.isFinite(saturation) || food < 0 || food > 20 || level < 0
                    || maxHealth < 1.0 || maxHealth > 1024.0 || health < 0.0 || health > maxHealth
                    || speed < 0.0 || speed > 10.0 || saturation < 0.0F || saturation > 20.0F
                    || (thirst >= 0 && thirst > 20) || (hydration >= 0 && (hydration > 20 || !Float.isFinite(hydration)))
                    || (exhaustion >= 0 && (exhaustion > 40 || !Float.isFinite(exhaustion)))
                    || (temperature >= 0 && temperature > 4)) {
                throw new NumberFormatException();
            }
            updateSelectedPlayerEffect();
            ClientPlayNetworking.send(new HeldItemTweaker.PlayerEditPayload(maxHealth, health, food, saturation,
                    level, speed, encodePlayerEffects(), thirst, hydration, exhaustion, temperature));
            this.minecraft.player.displayClientMessage(Component.literal("Player changes applied."), true);
        } catch (NumberFormatException ignored) {
            this.minecraft.player.displayClientMessage(Component.literal("Invalid player value."), true);
        }
    }

    private static int optionalPlayerInt(EditBox box, int fallback) {
        if (box == null || box.getValue().isBlank()) return fallback;
        return Integer.parseInt(box.getValue().trim());
    }

    private static float optionalPlayerFloat(EditBox box, float fallback) {
        if (box == null || box.getValue().isBlank()) return fallback;
        return Float.parseFloat(box.getValue().trim());
    }

    private void openVariantEditor() {
        ItemStack source = targetStack();
        if (source.isEmpty()) return;
        this.minecraft.setScreen(new ItemVariantScreen(this.targetId, source));
    }

    private void buildEnchantments() {
        int height = Math.max(40, this.layout.contentBottom() - this.layout.contentTop());
        this.enchantmentList = addTabWidget(Tab.ENCHANTMENTS, new EnchantmentList(
                this.layout.contentX(), this.layout.contentTop(), this.layout.contentWidth(), height));
    }

    private void buildVisuals() {
        int y = this.layout.contentTop();
        if (this.trimCapable) {
            label(Tab.VISUALS, "Trim material (ore/gem color)", y);
            this.trimMaterialButton = addTabWidget(Tab.VISUALS, Button.builder(Component.empty(), ignored -> {
                openChoiceList(this.trimMaterialList);
            }).pos(this.layout.contentX(), y + 10).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
            this.trimMaterialList = addChoiceList(Tab.VISUALS, this.layout.contentX(), y + 10,
                    trimMaterialOptions(), this.trimMaterialIndex, index -> {
                        this.trimMaterialIndex = index;
                        refreshTrimButtons();
                    });
            y = nextRow(y);
            label(Tab.VISUALS, "Trim pattern", y);
            this.trimPatternButton = addTabWidget(Tab.VISUALS, Button.builder(Component.empty(), ignored -> {
                openChoiceList(this.trimPatternList);
            }).pos(this.layout.contentX(), y + 10).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
            this.trimPatternList = addChoiceList(Tab.VISUALS, this.layout.contentX(), y + 10,
                    trimPatternOptions(), this.trimPatternIndex, index -> {
                        this.trimPatternIndex = index;
                        refreshTrimButtons();
                    });
            y = nextRow(y);
            this.trimClearButton = addTabWidget(Tab.VISUALS, Button.builder(Component.literal("Remove Trim"), ignored -> {
                this.trimMaterialIndex = 0;
                this.trimPatternIndex = 0;
                refreshTrimButtons();
            }).pos(this.layout.contentX(), y + 4).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
            y += this.layout.rowHeight();
            refreshTrimButtons();
        }
        if (this.dyeCapable) {
            label(Tab.VISUALS, "Dyed item color", y);
            this.dyeGridY = y + 11;
            int gap = 2;
            this.dyeButtonWidth = Math.max(20, (this.layout.contentWidth() - gap * 7) / 8);
            for (int i = 0; i < DyeColor.values().length; i++) {
                DyeColor color = DyeColor.values()[i];
                int column = i % 8;
                int row = i / 8;
                addTabWidget(Tab.VISUALS, Button.builder(Component.empty(), ignored -> {
                    this.dyeColor = color.getTextureDiffuseColor();
                    refreshDyeFields();
                }).pos(this.layout.contentX() + column * (this.dyeButtonWidth + gap), this.dyeGridY + row * 22)
                        .size(this.dyeButtonWidth, 18).build());
            }
            int dyeRows = (DyeColor.values().length + 7) / 8;
            y = this.dyeGridY + dyeRows * 22 + 4;
            this.hexDyeBox = addTabWidget(Tab.VISUALS, new EditBox(this.font, this.layout.contentX(), y,
                    this.layout.contentWidth() - 74, this.layout.fieldHeight(), Component.literal("Hex color")));
            this.hexDyeBox.setHint(Component.literal("#RRGGBB"));
            this.hexDyeBox.setMaxLength(7);
            refreshDyeFields();
            addTabWidget(Tab.VISUALS, Button.builder(Component.literal("Apply"), ignored -> applyHexDye())
                    .pos(this.layout.contentX() + this.layout.contentWidth() - 70, y).size(70, this.layout.fieldHeight()).build());
            addTabWidget(Tab.VISUALS, Button.builder(Component.literal("Clear Dye"), ignored -> {
                this.dyeColor = -1;
                refreshDyeFields();
            }).pos(this.layout.contentX(), y + this.layout.fieldHeight() + 6).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
        }
    }

    private void buildAttributes(ItemStack held) {
        if (!this.attributeCapable) return;
        int y = this.layout.contentTop();
        label(Tab.ATTRIBUTES, "Item bonus amount; choose operation and equipment slot", y);
        y += 28;
        for (AttributeSpec spec : this.attributeSpecs) {
            EditBox box = field(Tab.ATTRIBUTES, readableAttributeLabel(spec.label()),
                    spec.min() + " to " + spec.max(), y,
                    format(attributeAmount(held, spec.id(), spec.fallback())), 24);
            this.attributeBoxes.put(spec.id(), box);
            box.setResponder(ignored -> this.dirtyAttributes.add(spec.id()));
            y = nextRow(y);

            int gap = 4;
            int selectorWidth = Math.max(1, (this.layout.contentWidth() - gap) / 2);
            Button operation = addTabWidget(Tab.ATTRIBUTES, Button.builder(Component.empty(), ignored ->
                            openChoiceList(this.attributeOperationLists.get(spec.id())))
                    .pos(this.layout.contentX(), y + 4).size(selectorWidth, this.layout.fieldHeight()).build());
            Button slot = addTabWidget(Tab.ATTRIBUTES, Button.builder(Component.empty(), ignored ->
                            openChoiceList(this.attributeSlotLists.get(spec.id())))
                    .pos(this.layout.contentX() + selectorWidth + gap, y + 4)
                    .size(selectorWidth, this.layout.fieldHeight()).build());
            this.attributeOperationButtons.put(spec.id(), operation);
            this.attributeSlotButtons.put(spec.id(), slot);
            ChoiceList operationList = addChoiceList(Tab.ATTRIBUTES, this.layout.contentX(), y + 4,
                    attributeOperationOptions(), attributeOperationIndex(spec.id()), index -> {
                        List<ChoiceValue> options = attributeOperationOptions();
                        if (index >= 0 && index < options.size()) {
                            this.attributeOperations.put(spec.id(), parseOperation(options.get(index).value()));
                            this.dirtyAttributes.add(spec.id());
                            refreshAttributeButtons();
                        }
                    });
            ChoiceList slotList = addChoiceList(Tab.ATTRIBUTES, this.layout.contentX() + selectorWidth + gap, y + 4,
                    attributeSlotOptions(), attributeSlotIndex(spec.id()), index -> {
                        List<ChoiceValue> options = attributeSlotOptions();
                        if (index >= 0 && index < options.size()) {
                            this.attributeSlots.put(spec.id(), parseSlotGroup(options.get(index).value()));
                            this.dirtyAttributes.add(spec.id());
                            refreshAttributeButtons();
                        }
                    });
            this.attributeOperationLists.put(spec.id(), operationList);
            this.attributeSlotLists.put(spec.id(), slotList);
            y = nextRow(y);
        }
        refreshAttributeButtons();
    }

    private String readableAttributeLabel(String value) {
        return value.replace(" modifier amount", "");
    }

    private List<ChoiceValue> attributeOperationOptions() {
        return List.of(
                new ChoiceValue("add_value", Component.literal("Add value")),
                new ChoiceValue("add_multiplied_base", Component.literal("Add % of base")),
                new ChoiceValue("add_multiplied_total", Component.literal("Add % of total")));
    }

    private List<ChoiceValue> attributeSlotOptions() {
        return List.of(
                new ChoiceValue("any", Component.literal("Any slot")),
                new ChoiceValue("mainhand", Component.literal("Main hand")),
                new ChoiceValue("offhand", Component.literal("Offhand")),
                new ChoiceValue("hand", Component.literal("Both hands")),
                new ChoiceValue("head", Component.literal("Head")),
                new ChoiceValue("chest", Component.literal("Chest")),
                new ChoiceValue("legs", Component.literal("Legs")),
                new ChoiceValue("feet", Component.literal("Feet")),
                new ChoiceValue("armor", Component.literal("Armor")));
    }

    private int attributeOperationIndex(String id) {
        String current = attributeOperationId(this.attributeOperations.getOrDefault(id,
                AttributeModifier.Operation.ADD_VALUE));
        List<ChoiceValue> options = attributeOperationOptions();
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).value().equals(current)) return index;
        }
        return 0;
    }

    private int attributeSlotIndex(String id) {
        String current = attributeSlotId(this.attributeSlots.getOrDefault(id, EquipmentSlotGroup.ANY));
        List<ChoiceValue> options = attributeSlotOptions();
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).value().equals(current)) return index;
        }
        return 0;
    }

    private String attributeOperationId(AttributeModifier.Operation operation) {
        return switch (operation) {
            case ADD_VALUE -> "add_value";
            case ADD_MULTIPLIED_BASE -> "add_multiplied_base";
            case ADD_MULTIPLIED_TOTAL -> "add_multiplied_total";
        };
    }

    private AttributeModifier.Operation parseOperation(String value) {
        return switch (value) {
            case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE;
        };
    }

    private String attributeSlotId(EquipmentSlotGroup slot) {
        return switch (slot) {
            case MAINHAND -> "mainhand";
            case OFFHAND -> "offhand";
            case HAND -> "hand";
            case FEET -> "feet";
            case LEGS -> "legs";
            case CHEST -> "chest";
            case HEAD -> "head";
            case ARMOR -> "armor";
            default -> "any";
        };
    }

    private EquipmentSlotGroup parseSlotGroup(String value) {
        return switch (value) {
            case "mainhand" -> EquipmentSlotGroup.MAINHAND;
            case "offhand" -> EquipmentSlotGroup.OFFHAND;
            case "hand" -> EquipmentSlotGroup.HAND;
            case "feet" -> EquipmentSlotGroup.FEET;
            case "legs" -> EquipmentSlotGroup.LEGS;
            case "chest" -> EquipmentSlotGroup.CHEST;
            case "head" -> EquipmentSlotGroup.HEAD;
            case "armor" -> EquipmentSlotGroup.ARMOR;
            default -> EquipmentSlotGroup.ANY;
        };
    }

    private void refreshAttributeButtons() {
        for (AttributeSpec spec : this.attributeSpecs) {
            Button operation = this.attributeOperationButtons.get(spec.id());
            if (operation != null) {
                operation.setMessage(Component.literal("Operation: " + switch (this.attributeOperations.getOrDefault(
                        spec.id(), AttributeModifier.Operation.ADD_VALUE)) {
                    case ADD_VALUE -> "Add value";
                    case ADD_MULTIPLIED_BASE -> "Add % of base";
                    case ADD_MULTIPLIED_TOTAL -> "Add % of total";
                }));
                setButtonTooltip(operation, "Choose how this attribute amount modifies the item's stat: add value, add a percentage of base, or add a percentage of total.");
            }
            Button slot = this.attributeSlotButtons.get(spec.id());
            if (slot != null) slot.setMessage(Component.literal("Slot: " + switch (this.attributeSlots.getOrDefault(
                    spec.id(), EquipmentSlotGroup.ANY)) {
                case MAINHAND -> "Main hand";
                case OFFHAND -> "Offhand";
                case HAND -> "Both hands";
                case FEET -> "Feet";
                case LEGS -> "Legs";
                case CHEST -> "Chest";
                case HEAD -> "Head";
                case ARMOR -> "Armor";
                default -> "Any slot";
            }));
            setButtonTooltip(slot, "Choose the equipment slot group that receives this attribute bonus.");
        }
    }

    private void buildComponents(ItemStack held) {
        int y = this.layout.contentTop();
        label(Tab.COMPONENTS, "Enchantment glint", y);
        this.glintButton = addTabWidget(Tab.COMPONENTS, Button.builder(Component.empty(), ignored -> {
            openChoiceList(this.glintList);
        }).pos(this.layout.contentX(), y + 10).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
        this.glintList = addChoiceList(Tab.COMPONENTS, this.layout.contentX(), y + 10,
                glintOptions(), glintOptionIndex(), index -> {
                    this.glintOverride = switch (index) {
                        case 1 -> 1;
                        case 2 -> 0;
                        default -> -1;
                    };
                    refreshComponentButtons();
                });
        y = nextRow(y);

        label(Tab.COMPONENTS, "Item rarity", y);
        this.rarityButton = addTabWidget(Tab.COMPONENTS, Button.builder(Component.empty(), ignored -> {
            openChoiceList(this.rarityList);
        }).pos(this.layout.contentX(), y + 10).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
        this.rarityList = addChoiceList(Tab.COMPONENTS, this.layout.contentX(), y + 10,
                rarityOptions(), this.rarity.ordinal(), index -> {
                    Rarity[] values = Rarity.values();
                    if (index >= 0 && index < values.length) this.rarity = values[index];
                    refreshComponentButtons();
                });
        y = nextRow(y);

        label(Tab.COMPONENTS, "Tooltip visibility", y);
        this.hideTooltipButton = addTabWidget(Tab.COMPONENTS, Button.builder(Component.empty(), ignored -> {
            this.hideTooltip = !this.hideTooltip;
            refreshComponentButtons();
        }).pos(this.layout.contentX(), y + 10).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
        y = nextRow(y);
        this.hideEnchantmentsButton = addTabWidget(Tab.COMPONENTS, Button.builder(Component.empty(), ignored -> {
            this.hideEnchantments = !this.hideEnchantments;
            refreshComponentButtons();
        }).pos(this.layout.contentX(), y + 4).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
        y = nextRow(y);

        CustomModelData model = held.get(DataComponents.CUSTOM_MODEL_DATA);
        this.modelFloatsBox = field(Tab.COMPONENTS, "Custom model floats", "comma-separated floats", y,
                model == null ? "" : joinValues(model.floats()), 256);
        this.modelFloatsBox.setResponder(ignored -> this.modelDirty = true);
        y = nextRow(y);
        this.modelFlagsBox = field(Tab.COMPONENTS, "Custom model flags", "comma-separated true/false", y,
                model == null ? "" : joinValues(model.flags()), 256);
        this.modelFlagsBox.setResponder(ignored -> this.modelDirty = true);
        y = nextRow(y);
        this.modelStringsBox = field(Tab.COMPONENTS, "Custom model strings", "comma-separated values", y,
                model == null ? "" : String.join(",", model.strings()), 512);
        this.modelStringsBox.setResponder(ignored -> this.modelDirty = true);
        y = nextRow(y);
        this.modelColorsBox = field(Tab.COMPONENTS, "Custom model colors", "comma-separated RGB integers", y,
                model == null ? "" : joinValues(model.colors()), 256);
        this.modelColorsBox.setResponder(ignored -> this.modelDirty = true);
        y = nextRow(y);

        this.canPlaceBox = field(Tab.COMPONENTS, "Can place on", "block IDs, comma-separated", y, "", 1024);
        this.canPlaceBox.setResponder(ignored -> this.canPlaceDirty = true);
        y = nextRow(y);
        this.canBreakBox = field(Tab.COMPONENTS, "Can break", "block IDs, comma-separated", y, "", 1024);
        this.canBreakBox.setResponder(ignored -> this.canBreakDirty = true);
        refreshComponentButtons();
    }

    private String joinValues(List<?> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private void refreshComponentButtons() {
        if (this.glintButton != null) {
            this.glintButton.setMessage(Component.literal("Glint: " + switch (this.glintOverride) {
                case 1 -> "ON";
                case 0 -> "OFF";
                default -> "Vanilla";
            }));
            setButtonTooltip(this.glintButton, "Choose Vanilla behavior, force the enchantment glint on, or force it off.");
        }
        if (this.rarityButton != null) this.rarityButton.setMessage(Component.literal("Rarity: " + this.rarity.getSerializedName()));
        setButtonTooltip(this.rarityButton, "Choose the rarity color and tooltip style used for this item.");
        if (this.hideTooltipButton != null) this.hideTooltipButton.setMessage(Component.literal("Hide all tooltip: " + (this.hideTooltip ? "ON" : "OFF")));
        setButtonTooltip(this.hideTooltipButton, "Hide the entire item tooltip when the item is hovered.");
        if (this.hideEnchantmentsButton != null) this.hideEnchantmentsButton.setMessage(Component.literal("Hide enchantments: " + (this.hideEnchantments ? "ON" : "OFF")));
        setButtonTooltip(this.hideEnchantmentsButton, "Hide enchantment lines while leaving the rest of the item tooltip visible.");
        if (this.glintList != null) this.glintList.setSelectedIndex(glintOptionIndex());
        if (this.rarityList != null) this.rarityList.setSelectedIndex(this.rarity.ordinal());
    }

    private int glintOptionIndex() {
        return this.glintOverride == 1 ? 1 : this.glintOverride == 0 ? 2 : 0;
    }

    private List<ChoiceValue> glintOptions() {
        return List.of(new ChoiceValue("vanilla", Component.literal("Vanilla")),
                new ChoiceValue("on", Component.literal("On")),
                new ChoiceValue("off", Component.literal("Off")));
    }

    private List<ChoiceValue> rarityOptions() {
        return Arrays.stream(Rarity.values())
                .map(value -> new ChoiceValue(value.getSerializedName(), Component.literal(value.getSerializedName())))
                .toList();
    }

    private void buildBanner() {
        if (!this.bannerCapable) return;
        int y = this.layout.contentTop();
        label(Tab.BANNER, "Base color", y);
        this.bannerBaseColorButton = addTabWidget(Tab.BANNER, Button.builder(Component.empty(), ignored -> {
            openChoiceList(this.bannerBaseColorList);
        }).pos(this.layout.contentX(), y + 10).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
        this.bannerBaseColorList = addChoiceList(Tab.BANNER, this.layout.contentX(), y + 10,
                dyeColorOptions(), this.bannerBaseColor.getId(), index -> {
                    this.bannerBaseColor = dyeColor(index);
                    refreshBannerButtons();
                });
        y = nextRow(y);

        label(Tab.BANNER, "Selected layer pattern", y);
        this.bannerPatternChoiceButton = addTabWidget(Tab.BANNER, Button.builder(Component.empty(), ignored -> openChoiceList(this.bannerPatternList))
                .pos(this.layout.contentX(), y + 10).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
        this.bannerPatternList = addChoiceList(Tab.BANNER, this.layout.contentX(), y + 10,
                bannerPatternOptions(), this.bannerPatternChoice, this::selectBannerPattern);
        y = nextRow(y);

        label(Tab.BANNER, "Selected layer color", y);
        this.bannerLayerColorButton = addTabWidget(Tab.BANNER, Button.builder(Component.empty(), ignored -> openChoiceList(this.bannerLayerColorList))
                .pos(this.layout.contentX(), y + 10).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
        this.bannerLayerColorList = addChoiceList(Tab.BANNER, this.layout.contentX(), y + 10,
                dyeColorOptions(), this.bannerLayerColorChoice.getId(), this::selectBannerLayerColor);
        y = nextRow(y);

        int layerButtonGap = 4;
        int layerButtonWidth = Math.max(1, (this.layout.contentWidth() - layerButtonGap) / 2);
        this.bannerAddLayerButton = addTabWidget(Tab.BANNER,
                Button.builder(Component.literal("Add layer"), ignored -> addBannerLayer())
                        .pos(this.layout.contentX(), y + 4)
                        .size(layerButtonWidth, this.layout.fieldHeight()).build());
        this.bannerEditLayerButton = addTabWidget(Tab.BANNER,
                Button.builder(Component.literal("Update selected"), ignored -> editBannerLayer())
                        .pos(this.layout.contentX() + layerButtonWidth + layerButtonGap, y + 4)
                        .size(layerButtonWidth, this.layout.fieldHeight()).build());
        y += this.layout.rowHeight();

        int clearY = Math.max(y + 24, this.layout.contentBottom() - this.layout.fieldHeight());
        int listHeight = Math.max(24, clearY - y - 4);
        this.bannerLayerList = addTabWidget(Tab.BANNER, new BannerLayerList(
                this.layout.contentX(), y, this.layout.contentWidth(), listHeight));
        this.bannerClearButton = addTabWidget(Tab.BANNER, Button.builder(Component.literal("Clear banner patterns"), ignored -> {
            this.bannerLayers.clear();
            this.selectedBannerLayer = -1;
            this.bannerLayerList.refresh();
            refreshBannerButtons();
        }).pos(this.layout.contentX(), clearY).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
        refreshBannerButtons();
    }

    private void buildSpecial(ItemStack held) {
        if (!this.specialCapable) return;
        int y = this.layout.contentTop();
        if (this.potionCapable) {
            this.potionBox = fieldWithChoiceButton(Tab.SPECIAL, "Potion", "Choose a potion or clear", y,
                    this.potionBoxValue, 256, "Select", () -> openChoiceList(this.potionList));
            this.potionBox.setResponder(ignored -> this.specialDirty = true);
            this.potionList = addChoiceList(Tab.SPECIAL, this.layout.contentX(),
                    y + this.font.lineHeight + 2, potionOptions(), potionIndex(), index -> {
                        List<ChoiceValue> options = potionOptions();
                        if (index >= 0 && index < options.size()) {
                            this.potionBox.setValue(options.get(index).value());
                            this.specialDirty = true;
                        }
                    });
            y = nextRow(y);
        }
        if (this.foodCapable) {
            this.foodNutritionBox = field(Tab.SPECIAL, "Food nutrition", "0-20", y, this.foodNutritionValue, 8);
            this.foodNutritionBox.setResponder(ignored -> this.specialDirty = true);
            y = nextRow(y);
            this.foodSaturationBox = field(Tab.SPECIAL, "Food saturation", "0-100", y, this.foodSaturationValue, 12);
            this.foodSaturationBox.setResponder(ignored -> this.specialDirty = true);
            y = nextRow(y);
            if (this.minecraft.player != null) {
                Optional<AppleSkinCompat.FoodValues> appleSkinValues = AppleSkinCompat.query(held, this.minecraft.player);
                if (appleSkinValues.isPresent()) {
                    AppleSkinCompat.FoodValues values = appleSkinValues.get();
                    label(Tab.SPECIAL, "AppleSkin preview: +" + values.nutrition()
                            + " hunger, +" + format(values.saturation()) + " saturation", y);
                    y = nextRow(y);
                }
            }
            this.foodAlwaysEatButton = addTabWidget(Tab.SPECIAL, Button.builder(Component.empty(), ignored -> {
                this.foodAlwaysEat = !this.foodAlwaysEat;
                this.specialDirty = true;
                refreshSpecialButtons();
            }).pos(this.layout.contentX(), y + 4).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
            y = nextRow(y);
        }
        if (this.weaponCapable) {
            this.weaponDamageBox = field(Tab.SPECIAL, "Weapon damage per attack", "0-100", y, this.weaponDamageValue, 8);
            this.weaponDamageBox.setResponder(ignored -> this.specialDirty = true);
            y = nextRow(y);
            this.weaponDisableBox = field(Tab.SPECIAL, "Weapon blocking disable seconds", "0-10", y, this.weaponDisableValue, 12);
            this.weaponDisableBox.setResponder(ignored -> this.specialDirty = true);
            y = nextRow(y);
        }
        if (this.fireworksCapable) {
            this.fireworkFlightBox = field(Tab.SPECIAL, "Firework flight duration", "0-127", y, this.fireworkFlightValue, 8);
            this.fireworkFlightBox.setResponder(ignored -> this.specialDirty = true);
            y = nextRow(y);
            this.fireworkShapeBox = fieldWithChoiceButton(Tab.SPECIAL, "Firework explosion shape",
                    "small_ball, large_ball, star, creeper, burst", y, this.fireworkShapeValue, 32,
                    "Select", () -> openChoiceList(this.fireworkShapeList));
            this.fireworkShapeBox.setResponder(ignored -> this.specialDirty = true);
            this.fireworkShapeList = addChoiceList(Tab.SPECIAL, this.layout.contentX(),
                    y + this.font.lineHeight + 2, fireworkShapeOptions(), fireworkShapeIndex(), index -> {
                        if (index >= 0 && index < FireworkExplosion.Shape.values().length) {
                            this.fireworkShapeValue = FireworkExplosion.Shape.values()[index].getSerializedName();
                            this.fireworkShapeBox.setValue(this.fireworkShapeValue);
                            this.specialDirty = true;
                        }
                    });
            y = nextRow(y);
            this.fireworkColorsBox = field(Tab.SPECIAL, "Explosion colors", "RGB integers, comma-separated", y, this.fireworkColorsValue, 256);
            this.fireworkColorsBox.setResponder(ignored -> this.specialDirty = true);
            y = nextRow(y);
            this.fireworkFadeColorsBox = field(Tab.SPECIAL, "Explosion fade colors", "RGB integers, comma-separated", y, this.fireworkFadeColorsValue, 256);
            this.fireworkFadeColorsBox.setResponder(ignored -> this.specialDirty = true);
            y = nextRow(y);
            this.fireworkTrailButton = addTabWidget(Tab.SPECIAL, Button.builder(Component.empty(), ignored -> {
                this.fireworkTrail = !this.fireworkTrail;
                this.specialDirty = true;
                refreshSpecialButtons();
            }).pos(this.layout.contentX(), y + 4).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
            y = nextRow(y);
            this.fireworkTwinkleButton = addTabWidget(Tab.SPECIAL, Button.builder(Component.empty(), ignored -> {
                this.fireworkTwinkle = !this.fireworkTwinkle;
                this.specialDirty = true;
                refreshSpecialButtons();
            }).pos(this.layout.contentX(), y + 4).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
            y = nextRow(y);
        }
        if (this.containerCapable) {
            this.containerItemsBox = field(Tab.SPECIAL, "Container contents", "item_id*count, comma-separated", y, this.containerItemsValue, 2048);
            this.containerItemsBox.setResponder(ignored -> this.specialDirty = true);
            y = nextRow(y);
        }
        if (this.bundleCapable) {
            this.bundleItemsBox = field(Tab.SPECIAL, "Bundle contents", "item_id*count, comma-separated", y, this.bundleItemsValue, 2048);
            this.bundleItemsBox.setResponder(ignored -> this.specialDirty = true);
            y = nextRow(y);
        }
        if (this.chargedProjectilesCapable) {
            this.chargedProjectilesBox = field(Tab.SPECIAL, "Charged projectiles", "arrow/firework*count, comma-separated", y, this.chargedProjectilesValue, 512);
            this.chargedProjectilesBox.setResponder(ignored -> this.specialDirty = true);
            y = nextRow(y);
        }
        if (this.mapColorCapable) {
            this.mapColorBox = field(Tab.SPECIAL, "Map color", "RGB integer 0-16777215", y, this.mapColorValue, 12);
            this.mapColorBox.setResponder(ignored -> this.specialDirty = true);
        }
        refreshSpecialButtons();
    }

    private void buildAdvanced(ItemStack held) {
        int y = this.layout.contentTop();
        this.maxStackBox = field(Tab.ADVANCED, "Max stack size", "1-99", y,
                Integer.toString(held.getMaxStackSize()), 4);
        this.maxStackBox.setResponder(ignored -> this.advancedDirty = true);
        y = nextRow(y);
        this.maxDamageBox = field(Tab.ADVANCED, "Max durability", "0-1000000", y,
                Integer.toString(held.getMaxDamage()), 10);
        this.maxDamageBox.setResponder(ignored -> this.advancedDirty = true);
        y = nextRow(y);
        this.repairCostBox = field(Tab.ADVANCED, "Anvil repair cost", "0-100000", y,
                Integer.toString(held.getOrDefault(DataComponents.REPAIR_COST, 0)), 8);
        this.repairCostBox.setResponder(ignored -> this.advancedDirty = true);
        y = nextRow(y);
        this.potionDurationScaleBox = field(Tab.ADVANCED, "Potion duration scale", "blank or 0-10", y,
                this.potionDurationScaleValue, 12);
        this.potionDurationScaleBox.setResponder(ignored -> this.advancedDirty = true);
        y = nextRow(y);
        if (this.potionCapable) y = buildPotionEffectsEditor(y);
        this.fireworkExplosionsBox = field(Tab.ADVANCED, "Rocket explosions", "shape~colors~fade~trail~twinkle^...", y,
                this.fireworkExplosionsValue, 4096);
        this.fireworkExplosionsBox.setResponder(ignored -> this.advancedDirty = true);
        y = nextRow(y);
        this.toolDefaultsBox = field(Tab.ADVANCED, "Tool defaults", "speed,damage-per-block,creative(0/1)", y,
                this.toolDefaultsValue, 64);
        this.toolDefaultsBox.setResponder(ignored -> this.advancedDirty = true);
        y = nextRow(y);
        this.attackRangeBox = field(Tab.ADVANCED, "Attack range", "min,max,creative-min,creative-max,margin,mob-factor", y,
                this.attackRangeValue, 128);
        this.attackRangeBox.setResponder(ignored -> this.advancedDirty = true);
        y = nextRow(y);
        this.consumableBox = fieldWithChoiceButton(Tab.ADVANCED, "Consumable behavior",
                "seconds,animation,particles(0/1)", y, this.consumableValue, 64, "Animation",
                () -> openChoiceList(this.consumableAnimationList));
        this.consumableBox.setResponder(ignored -> this.advancedDirty = true);
        this.consumableAnimationList = addChoiceList(Tab.ADVANCED, this.layout.contentX(),
                y + this.font.lineHeight + 2, consumableAnimationOptions(), consumableAnimationIndex(), index -> {
                    ItemUseAnimation[] values = ItemUseAnimation.values();
                    if (index >= 0 && index < values.length) {
                        setPackedValue(this.consumableBox, 1, values[index].getSerializedName(), "1,drink,1");
                        this.advancedDirty = true;
                    }
                });
        y = nextRow(y);
        this.cooldownBox = field(Tab.ADVANCED, "Use cooldown", "seconds|optional cooldown group", y,
                this.cooldownValue, 256);
        this.cooldownBox.setResponder(ignored -> this.advancedDirty = true);
        y = nextRow(y);
        this.gliderButton = addTabWidget(Tab.ADVANCED, Button.builder(Component.empty(), ignored -> {
            this.glider = !this.glider;
            this.gliderDirty = true;
            refreshAdvancedButtons();
        }).pos(this.layout.contentX(), y + 4).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
        refreshAdvancedButtons();
        this.maxStackBox.active = true;
        this.maxDamageBox.active = true;
        this.repairCostBox.active = true;
        this.potionDurationScaleBox.active = held.has(DataComponents.POTION_CONTENTS);
        this.fireworkExplosionsBox.active = held.has(DataComponents.FIREWORKS);
        this.toolDefaultsBox.active = held.has(DataComponents.TOOL);
        this.attackRangeBox.active = held.has(DataComponents.ATTACK_RANGE);
        this.consumableBox.active = held.has(DataComponents.CONSUMABLE);
        if (this.consumableAnimationList != null) this.consumableAnimationList.active = this.consumableBox.active;
        this.cooldownBox.active = held.has(DataComponents.USE_COOLDOWN);
    }

    private int buildPotionEffectsEditor(int y) {
        label(Tab.ADVANCED, "Custom potion effects", y);
        int listY = y + this.font.lineHeight + 2;
        int listHeight = Math.max(56, Math.min(112, this.layout.contentBottom() - listY - 180));
        this.potionEffectList = addTabWidget(Tab.ADVANCED,
                new EffectList(this.layout.contentX(), listY, this.layout.contentWidth(), listHeight));
        y = listY + listHeight + 6;

        this.potionEffectChoiceButton = addTabWidget(Tab.ADVANCED,
                Button.builder(Component.empty(), ignored -> openChoiceList(this.potionEffectChoiceList))
                        .pos(this.layout.contentX(), y).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
        this.potionEffectChoiceList = addChoiceList(Tab.ADVANCED, this.layout.contentX(), y,
                potionEffectOptions(), selectedPotionEffectOption(), this::selectPotionEffectOption);
        this.potionEffectChoiceList.setAnchorBaseY(y);
        y += this.layout.fieldHeight() + 4;

        this.potionEffectDurationBox = field(Tab.ADVANCED, "Effect duration (seconds)", "1-107374182", y,
                selectedPotionEffectDuration(), 12);
        this.potionEffectDurationBox.setResponder(ignored -> this.advancedDirty = true);
        y = nextRow(y);
        this.potionEffectAmplifierBox = field(Tab.ADVANCED, "Effect level", "1-256", y,
                selectedPotionEffectAmplifier(), 4);
        this.potionEffectAmplifierBox.setResponder(ignored -> this.advancedDirty = true);
        y = nextRow(y);

        int gap = 4;
        int flagWidth = Math.max(1, (this.layout.contentWidth() - gap * 3) / 4);
        this.potionEffectInfiniteButton = addTabWidget(Tab.ADVANCED,
                Button.builder(Component.empty(), ignored -> togglePotionEffectInfinite())
                        .pos(this.layout.contentX(), y).size(flagWidth, this.layout.fieldHeight()).build());
        this.potionEffectParticlesButton = addTabWidget(Tab.ADVANCED,
                Button.builder(Component.empty(), ignored -> togglePotionEffectParticles())
                        .pos(this.layout.contentX() + flagWidth + gap, y).size(flagWidth, this.layout.fieldHeight()).build());
        this.potionEffectIconButton = addTabWidget(Tab.ADVANCED,
                Button.builder(Component.empty(), ignored -> togglePotionEffectIcon())
                        .pos(this.layout.contentX() + (flagWidth + gap) * 2, y).size(flagWidth, this.layout.fieldHeight()).build());
        this.potionEffectAmbientButton = addTabWidget(Tab.ADVANCED,
                Button.builder(Component.empty(), ignored -> togglePotionEffectAmbient())
                        .pos(this.layout.contentX() + (flagWidth + gap) * 3, y).size(flagWidth, this.layout.fieldHeight()).build());
        y = nextRow(y);

        int buttonWidth = Math.max(1, (this.layout.contentWidth() - gap * 2) / 3);
        this.potionEffectEnabledButton = addTabWidget(Tab.ADVANCED,
                Button.builder(Component.empty(), ignored -> toggleSelectedPotionEffect())
                        .pos(this.layout.contentX(), y).size(buttonWidth, this.layout.fieldHeight()).build());
        this.potionEffectApplyButton = addTabWidget(Tab.ADVANCED,
                Button.builder(Component.literal("Add / update"), ignored -> applyPotionEffectEdit())
                        .pos(this.layout.contentX() + buttonWidth + gap, y)
                        .size(buttonWidth, this.layout.fieldHeight()).build());
        this.potionEffectRemoveButton = addTabWidget(Tab.ADVANCED,
                Button.builder(Component.literal("Remove"), ignored -> removeSelectedPotionEffect())
                        .pos(this.layout.contentX() + (buttonWidth + gap) * 2, y)
                        .size(buttonWidth, this.layout.fieldHeight()).build());
        y = nextRow(y);
        this.potionEffectClearButton = addTabWidget(Tab.ADVANCED,
                Button.builder(Component.literal("Clear effects"), ignored -> clearPotionEffects())
                        .pos(this.layout.contentX(), y).size(this.layout.contentWidth(), this.layout.fieldHeight()).build());
        refreshPotionEffectEditor();
        return nextRow(y);
    }

    private void refreshAdvancedButtons() {
        if (this.gliderButton != null) this.gliderButton.setMessage(Component.literal("Glider: " + (this.glider ? "ON" : "OFF")));
        setButtonTooltip(this.gliderButton, "Allow this item to glide like an elytra when equipped.");
        if (this.consumableAnimationList != null) this.consumableAnimationList.setSelectedIndex(consumableAnimationIndex());
        refreshPotionEffectEditor();
    }

    private List<ChoiceValue> potionEffectOptions() {
        if (this.minecraft.level == null) return List.of();
        return this.minecraft.level.registryAccess().lookupOrThrow(Registries.MOB_EFFECT).listElements()
                .sorted(Comparator.comparing(holder -> holder.value().getDisplayName().getString()))
                .map(holder -> new ChoiceValue(
                        holder.unwrapKey().map(key -> key.identifier().toString()).orElse(""),
                        holder.value().getDisplayName()))
                .filter(choice -> !choice.value().isBlank())
                .toList();
    }

    private int selectedPotionEffectOption() {
        if (this.selectedPotionEffectType == null) return 0;
        String id = this.selectedPotionEffectType.unwrapKey()
                .map(key -> key.identifier().toString()).orElse("");
        List<ChoiceValue> options = potionEffectOptions();
        for (int i = 0; i < options.size(); i++) if (options.get(i).value().equals(id)) return i;
        return 0;
    }

    private String selectedPotionEffectDuration() {
        if (this.selectedPotionEffect < 0 || this.selectedPotionEffect >= this.potionEffectEdits.size()) return "200";
        int duration = this.potionEffectEdits.get(this.selectedPotionEffect).duration;
        return duration < 0 ? "" : Integer.toString(Math.max(1, duration / 20));
    }

    private String selectedPotionEffectAmplifier() {
        return this.selectedPotionEffect >= 0 && this.selectedPotionEffect < this.potionEffectEdits.size()
                ? Integer.toString(this.potionEffectEdits.get(this.selectedPotionEffect).amplifier + 1) : "1";
    }

    private void selectPotionEffectOption(int index) {
        List<ChoiceValue> options = potionEffectOptions();
        if (index < 0 || index >= options.size() || this.minecraft.level == null) return;
        Identifier id = Identifier.tryParse(options.get(index).value());
        this.selectedPotionEffectType = id == null ? null : this.minecraft.level.registryAccess()
                .lookupOrThrow(Registries.MOB_EFFECT).get(id).orElse(null);
        refreshPotionEffectEditor();
    }

    private void selectPotionEffectRow(int index) {
        if (index < 0 || index >= this.potionEffectEdits.size()) return;
        this.selectedPotionEffect = index;
        this.selectedPotionEffectType = this.potionEffectEdits.get(index).effect;
        if (this.potionEffectDurationBox != null) this.potionEffectDurationBox.setValue(selectedPotionEffectDuration());
        if (this.potionEffectAmplifierBox != null) this.potionEffectAmplifierBox.setValue(selectedPotionEffectAmplifier());
        refreshPotionEffectEditor();
    }

    private void refreshPotionEffectEditor() {
        if (this.potionEffectChoiceButton != null) {
            Component label = this.selectedPotionEffectType == null
                    ? Component.literal("Choose an effect") : this.selectedPotionEffectType.value().getDisplayName();
            this.potionEffectChoiceButton.setMessage(Component.literal("Effect: ").append(label));
            setButtonTooltip(this.potionEffectChoiceButton, "Choose a potion effect to add or edit.");
        }
        if (this.potionEffectChoiceList != null) {
            this.potionEffectChoiceList.setSelectedIndex(selectedPotionEffectOption());
        }
        if (this.potionEffectEnabledButton != null) {
            boolean enabled = this.selectedPotionEffect >= 0 && this.selectedPotionEffect < this.potionEffectEdits.size()
                    && this.potionEffectEdits.get(this.selectedPotionEffect).enabled;
            this.potionEffectEnabledButton.setMessage(Component.literal("Enabled: " + (enabled ? "ON" : "OFF")));
            setButtonTooltip(this.potionEffectEnabledButton, "Include or exclude the selected effect when the item is applied.");
            this.potionEffectEnabledButton.active = this.selectedPotionEffect >= 0;
        }
        if (this.potionEffectRemoveButton != null) {
            this.potionEffectRemoveButton.active = this.selectedPotionEffect >= 0
                    && this.selectedPotionEffect < this.potionEffectEdits.size();
        }
        EffectEdit edit = this.selectedPotionEffect >= 0 && this.selectedPotionEffect < this.potionEffectEdits.size()
                ? this.potionEffectEdits.get(this.selectedPotionEffect) : null;
        if (this.potionEffectInfiniteButton != null) {
            this.potionEffectInfiniteButton.setMessage(Component.literal("Infinite: " + (edit != null && edit.duration < 0 ? "ON" : "OFF")));
            setButtonTooltip(this.potionEffectInfiniteButton, "Use an infinite duration instead of a timed duration.");
            this.potionEffectInfiniteButton.active = edit != null;
        }
        if (this.potionEffectParticlesButton != null) {
            this.potionEffectParticlesButton.setMessage(Component.literal("Particles: " + (edit != null && edit.particles ? "ON" : "OFF")));
            setButtonTooltip(this.potionEffectParticlesButton, "Show or hide the effect particles.");
            this.potionEffectParticlesButton.active = edit != null;
        }
        if (this.potionEffectIconButton != null) {
            this.potionEffectIconButton.setMessage(Component.literal("Icon: " + (edit != null && edit.icon ? "ON" : "OFF")));
            setButtonTooltip(this.potionEffectIconButton, "Show or hide the effect icon in the HUD.");
            this.potionEffectIconButton.active = edit != null;
        }
        if (this.potionEffectAmbientButton != null) {
            this.potionEffectAmbientButton.setMessage(Component.literal("Ambient: " + (edit != null && edit.ambient ? "ON" : "OFF")));
            setButtonTooltip(this.potionEffectAmbientButton, "Use Minecraft's ambient effect rendering style.");
            this.potionEffectAmbientButton.active = edit != null;
        }
        if (this.potionEffectDurationBox != null) {
            this.potionEffectDurationBox.active = edit == null || edit.duration >= 0;
        }
        if (this.potionEffectList != null) this.potionEffectList.refresh();
    }

    private void toggleSelectedPotionEffect() {
        if (this.selectedPotionEffect < 0 || this.selectedPotionEffect >= this.potionEffectEdits.size()) return;
        this.potionEffectEdits.get(this.selectedPotionEffect).enabled =
                !this.potionEffectEdits.get(this.selectedPotionEffect).enabled;
        this.advancedDirty = true;
        refreshPotionEffectEditor();
    }

    private void togglePotionEffectInfinite() {
        EffectEdit edit = selectedPotionEffectEdit();
        if (edit == null) return;
        edit.duration = edit.duration < 0 ? 200 : MobEffectInstance.INFINITE_DURATION;
        this.advancedDirty = true;
        refreshPotionEffectEditor();
    }

    private void togglePotionEffectParticles() {
        EffectEdit edit = selectedPotionEffectEdit();
        if (edit == null) return;
        edit.particles = !edit.particles;
        this.advancedDirty = true;
        refreshPotionEffectEditor();
    }

    private void togglePotionEffectIcon() {
        EffectEdit edit = selectedPotionEffectEdit();
        if (edit == null) return;
        edit.icon = !edit.icon;
        this.advancedDirty = true;
        refreshPotionEffectEditor();
    }

    private void togglePotionEffectAmbient() {
        EffectEdit edit = selectedPotionEffectEdit();
        if (edit == null) return;
        edit.ambient = !edit.ambient;
        this.advancedDirty = true;
        refreshPotionEffectEditor();
    }

    private EffectEdit selectedPotionEffectEdit() {
        return this.selectedPotionEffect >= 0 && this.selectedPotionEffect < this.potionEffectEdits.size()
                ? this.potionEffectEdits.get(this.selectedPotionEffect) : null;
    }

    private void applyPotionEffectEdit() {
        if (this.selectedPotionEffectType == null) return;
        EffectEdit current = selectedPotionEffectEdit();
        Integer durationSeconds = current != null && current.duration < 0 ? null
                : parseBoundedInt(this.potionEffectDurationBox == null ? "" : this.potionEffectDurationBox.getValue(),
                1, Integer.MAX_VALUE / 20);
        Integer duration = current != null && current.duration < 0 ? MobEffectInstance.INFINITE_DURATION
                : durationSeconds == null ? null : durationSeconds * 20;
        Integer amplifier = parseBoundedInt(this.potionEffectAmplifierBox == null ? "" : this.potionEffectAmplifierBox.getValue(), 1, 256);
        if (duration == null || amplifier == null) return;
        int zeroBasedAmplifier = amplifier - 1;
        int existing = -1;
        for (int i = 0; i < this.potionEffectEdits.size(); i++) {
            if (this.potionEffectEdits.get(i).effect.equals(this.selectedPotionEffectType)) {
                existing = i;
                break;
            }
        }
        if (existing < 0) {
            this.potionEffectEdits.add(new EffectEdit(this.selectedPotionEffectType, duration,
                    zeroBasedAmplifier, true, true, true, false));
            this.selectedPotionEffect = this.potionEffectEdits.size() - 1;
        } else {
            this.selectedPotionEffect = existing;
            EffectEdit edit = this.potionEffectEdits.get(existing);
            edit.effect = this.selectedPotionEffectType;
            edit.duration = duration;
            edit.amplifier = zeroBasedAmplifier;
        }
        this.advancedDirty = true;
        refreshPotionEffectEditor();
    }

    private void removeSelectedPotionEffect() {
        if (this.selectedPotionEffect < 0 || this.selectedPotionEffect >= this.potionEffectEdits.size()) return;
        this.potionEffectEdits.remove(this.selectedPotionEffect);
        this.selectedPotionEffect = Math.min(this.selectedPotionEffect, this.potionEffectEdits.size() - 1);
        this.selectedPotionEffectType = this.selectedPotionEffect >= 0
                ? this.potionEffectEdits.get(this.selectedPotionEffect).effect : null;
        this.advancedDirty = true;
        refreshPotionEffectEditor();
        if (this.selectedPotionEffect >= 0) selectPotionEffectRow(this.selectedPotionEffect);
    }

    private void clearPotionEffects() {
        this.potionEffectEdits.clear();
        this.selectedPotionEffect = -1;
        this.selectedPotionEffectType = null;
        this.advancedDirty = true;
        refreshPotionEffectEditor();
    }

    private int consumableAnimationIndex() {
        String source = this.consumableBox == null ? this.consumableValue : this.consumableBox.getValue();
        String[] parts = source.split(",", -1);
        String current = parts.length > 1 ? parts[1] : "";
        ItemUseAnimation[] values = ItemUseAnimation.values();
        for (int index = 0; index < values.length; index++) {
            if (values[index].getSerializedName().equals(current)) return index;
        }
        return 0;
    }

    private List<ChoiceValue> consumableAnimationOptions() {
        return Arrays.stream(ItemUseAnimation.values())
                .map(animation -> new ChoiceValue(animation.getSerializedName(),
                        Component.literal(animation.getSerializedName())))
                .toList();
    }

    private void setPackedValue(EditBox box, int index, String replacement, String fallback) {
        String value = box == null ? "" : box.getValue().trim();
        String[] parts = value.isBlank() ? fallback.split(",", -1) : value.split(",", -1);
        if (parts.length <= index) parts = fallback.split(",", -1);
        parts[index] = replacement;
        box.setValue(String.join(",", parts));
    }

    private void buildData(ItemStack held) {
        int y = this.layout.contentTop();
        this.itemModelBox = field(Tab.DATA, "Item model", "registry ID or blank to clear", y, this.itemModelValue, 256);
        this.itemModelBox.setResponder(ignored -> this.dataDirty = true);
        y = nextRow(y);
        this.tooltipStyleBox = field(Tab.DATA, "Tooltip style", "registry ID or blank to clear", y, this.tooltipStyleValue, 256);
        this.tooltipStyleBox.setResponder(ignored -> this.dataDirty = true);
        y = nextRow(y);
        this.enchantableBox = field(Tab.DATA, "Enchantability", "0-255", y, this.enchantableValue, 4);
        this.enchantableBox.setResponder(ignored -> this.dataDirty = true);
        y = nextRow(y);
        this.repairableBox = field(Tab.DATA, "Repair items", "item IDs, comma-separated", y, this.repairableValue, 1024);
        this.repairableBox.setResponder(ignored -> this.dataDirty = true);
        y = nextRow(y);
        this.damageResistantBox = field(Tab.DATA, "Damage-resistant tag", "tag ID or blank to clear", y, this.damageResistantValue, 256);
        this.damageResistantBox.setResponder(ignored -> this.dataDirty = true);
        y = nextRow(y);
        this.profileBox = field(Tab.DATA, "Player-head profile", "name or blank to clear", y, this.profileValue, 64);
        this.profileBox.setResponder(ignored -> this.dataDirty = true);
        y = nextRow(y);
        this.noteBlockSoundBox = field(Tab.DATA, "Note-block sound", "sound ID or blank to clear", y, this.noteBlockSoundValue, 256);
        this.noteBlockSoundBox.setResponder(ignored -> this.dataDirty = true);
        y = nextRow(y);
        this.useEffectsBox = field(Tab.DATA, "Use effects", "sprint, vibrations, speed", y, this.useEffectsValue, 64);
        this.useEffectsBox.setResponder(ignored -> this.dataDirty = true);
        y = nextRow(y);
        this.swingAnimationBox = fieldWithChoiceButton(Tab.DATA, "Swing animation",
                "none/whack/stab,duration", y, this.swingAnimationValue, 64, "Type",
                () -> openChoiceList(this.swingAnimationList));
        this.swingAnimationBox.setResponder(ignored -> this.dataDirty = true);
        this.swingAnimationList = addChoiceList(Tab.DATA, this.layout.contentX(),
                y + this.font.lineHeight + 2, swingAnimationOptions(), swingAnimationIndex(), index -> {
                    SwingAnimationType[] values = SwingAnimationType.values();
                    if (index >= 0 && index < values.length) {
                        setPackedValue(this.swingAnimationBox, 0, values[index].getSerializedName(), "none,1");
                        this.dataDirty = true;
                    }
                });
        y = nextRow(y);
        this.piercingBox = field(Tab.DATA, "Piercing weapon", "knockback,dismount (0/1)", y, this.piercingValue, 16);
        this.piercingBox.setResponder(ignored -> this.dataDirty = true);
        y = nextRow(y);
        this.equippableBox = fieldWithChoiceButton(Tab.DATA, "Equippable",
                "slot,dispensable,swappable,damage,equip,shear", y, this.equippableValue, 128, "Slot",
                () -> openChoiceList(this.equippableSlotList));
        this.equippableBox.setResponder(ignored -> this.dataDirty = true);
        this.equippableSlotList = addChoiceList(Tab.DATA, this.layout.contentX(),
                y + this.font.lineHeight + 2, equippableSlotOptions(), equippableSlotIndex(), index -> {
                    EquipmentSlot[] values = EquipmentSlot.values();
                    if (index >= 0 && index < values.length) {
                        setPackedValue(this.equippableBox, 0, values[index].getSerializedName(), "head,0,1,1,1,0");
                        this.dataDirty = true;
                    }
                });
        refreshDataChoiceLists();
    }

    private int swingAnimationIndex() {
        String source = this.swingAnimationBox == null ? this.swingAnimationValue : this.swingAnimationBox.getValue();
        String[] parts = source.split(",", -1);
        String current = parts.length > 0 ? parts[0] : "";
        SwingAnimationType[] values = SwingAnimationType.values();
        for (int index = 0; index < values.length; index++) {
            if (values[index].getSerializedName().equalsIgnoreCase(current)
                    || values[index].name().equalsIgnoreCase(current)) return index;
        }
        return 0;
    }

    private List<ChoiceValue> swingAnimationOptions() {
        return Arrays.stream(SwingAnimationType.values())
                .map(type -> new ChoiceValue(type.getSerializedName(), Component.literal(type.getSerializedName())))
                .toList();
    }

    private int equippableSlotIndex() {
        String source = this.equippableBox == null ? this.equippableValue : this.equippableBox.getValue();
        String[] parts = source.split(",", -1);
        String current = parts.length > 0 ? parts[0] : "";
        EquipmentSlot[] values = EquipmentSlot.values();
        for (int index = 0; index < values.length; index++) {
            if (values[index].getSerializedName().equalsIgnoreCase(current)) return index;
        }
        return 0;
    }

    private List<ChoiceValue> equippableSlotOptions() {
        return Arrays.stream(EquipmentSlot.values())
                .map(slot -> new ChoiceValue(slot.getSerializedName(), Component.literal(slot.getSerializedName())))
                .toList();
    }

    private void refreshDataChoiceLists() {
        if (this.swingAnimationList != null) this.swingAnimationList.setSelectedIndex(swingAnimationIndex());
        if (this.equippableSlotList != null) this.equippableSlotList.setSelectedIndex(equippableSlotIndex());
    }

    private void refreshSpecialButtons() {
        if (this.foodAlwaysEatButton != null) this.foodAlwaysEatButton.setMessage(Component.literal("Food can always eat: " + (this.foodAlwaysEat ? "ON" : "OFF")));
        setButtonTooltip(this.foodAlwaysEatButton, "Allow the food to be eaten even when the hunger bar is full.");
        if (this.fireworkTrailButton != null) this.fireworkTrailButton.setMessage(Component.literal("Explosion trail: " + (this.fireworkTrail ? "ON" : "OFF")));
        setButtonTooltip(this.fireworkTrailButton, "Add a trail behind each firework explosion.");
        if (this.fireworkTwinkleButton != null) this.fireworkTwinkleButton.setMessage(Component.literal("Explosion twinkle: " + (this.fireworkTwinkle ? "ON" : "OFF")));
        setButtonTooltip(this.fireworkTwinkleButton, "Make each firework explosion twinkle.");
        if (this.fireworkShapeList != null) this.fireworkShapeList.setSelectedIndex(fireworkShapeIndex());
        if (this.potionList != null) this.potionList.setSelectedIndex(potionIndex());
    }

    private List<ChoiceValue> potionOptions() {
        List<ChoiceValue> result = new ArrayList<>();
        result.add(new ChoiceValue("", Component.literal("Clear potion")));
        if (this.minecraft.level == null) return result;
        this.minecraft.level.registryAccess().lookupOrThrow(Registries.POTION).listElements()
                .sorted(Comparator.comparing(holder -> readableIdentifier(holder.unwrapKey()
                        .map(key -> key.identifier().toString()).orElse(""))))
                .forEach(holder -> holder.unwrapKey().ifPresent(key -> result.add(new ChoiceValue(
                        key.identifier().toString(), Component.literal(readableIdentifier(key.identifier().toString()))))));
        return result;
    }

    private int potionIndex() {
        String current = this.potionBox == null ? this.potionBoxValue : this.potionBox.getValue().trim();
        List<ChoiceValue> options = potionOptions();
        for (int i = 0; i < options.size(); i++) if (options.get(i).value().equals(current)) return i;
        return 0;
    }

    private String readableIdentifier(String value) {
        String path = value == null ? "" : value.substring(value.indexOf(':') + 1);
        return Arrays.stream(path.split("[_-]"))
                .filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    private int fireworkShapeIndex() {
        FireworkExplosion.Shape[] values = FireworkExplosion.Shape.values();
        for (int index = 0; index < values.length; index++) {
            if (values[index].getSerializedName().equals(this.fireworkShapeValue)) return index;
        }
        return 0;
    }

    private List<ChoiceValue> fireworkShapeOptions() {
        return Arrays.stream(FireworkExplosion.Shape.values())
                .map(shape -> new ChoiceValue(shape.getSerializedName(), Component.literal(shape.getSerializedName())))
                .toList();
    }

    private void selectBannerPattern(int index) {
        if (index < 0 || index >= this.bannerPatterns.size()) return;
        this.bannerPatternChoice = index;
        if (this.selectedBannerLayer >= 0 && this.selectedBannerLayer < this.bannerLayers.size()) {
            BannerLayerChoice current = this.bannerLayers.get(this.selectedBannerLayer);
            this.bannerLayers.set(this.selectedBannerLayer,
                    new BannerLayerChoice(this.bannerPatterns.get(index).id(), current.color()));
            this.bannerLayerList.refresh();
        }
        refreshBannerButtons();
    }

    private void selectBannerLayerColor(int index) {
        DyeColor color = dyeColor(index);
        if (this.selectedBannerLayer >= 0 && this.selectedBannerLayer < this.bannerLayers.size()) {
            BannerLayerChoice current = this.bannerLayers.get(this.selectedBannerLayer);
            this.bannerLayers.set(this.selectedBannerLayer, new BannerLayerChoice(current.patternId(), color));
            this.bannerLayerList.refresh();
        } else {
            this.bannerLayerColorChoice = color;
        }
        refreshBannerButtons();
    }

    private void addBannerLayer() {
        if (this.bannerPatterns.isEmpty()) return;
        if (this.bannerLayers.size() >= 20) return;
        this.bannerLayers.add(new BannerLayerChoice(this.bannerPatterns.get(this.bannerPatternChoice).id(),
                this.bannerLayerColorChoice));
        this.selectedBannerLayer = this.bannerLayers.size() - 1;
        this.bannerLayerList.refresh();
        refreshBannerButtons();
    }

    private void editBannerLayer() {
        if (this.bannerPatterns.isEmpty()
                || this.selectedBannerLayer < 0
                || this.selectedBannerLayer >= this.bannerLayers.size()) return;
        this.bannerLayers.set(this.selectedBannerLayer, new BannerLayerChoice(
                this.bannerPatterns.get(this.bannerPatternChoice).id(), this.bannerLayerColorChoice));
        this.bannerLayerList.refresh();
        refreshBannerButtons();
    }

    private int indexOfBannerPattern(String id) {
        for (int i = 0; i < this.bannerPatterns.size(); i++) {
            if (this.bannerPatterns.get(i).id().equals(id)) return i;
        }
        return 0;
    }

    private String bannerPatternId() {
        if (this.bannerPatterns.isEmpty()) return "";
        int index = this.selectedBannerLayer >= 0 && this.selectedBannerLayer < this.bannerLayers.size()
                ? indexOfBannerPattern(this.bannerLayers.get(this.selectedBannerLayer).patternId()) : this.bannerPatternChoice;
        return this.bannerPatterns.get(Math.max(0, Math.min(index, this.bannerPatterns.size() - 1))).id();
    }

    private String bannerPatternName(String id) {
        return this.bannerPatterns.stream().filter(entry -> entry.id().equals(id)).findFirst()
                .map(entry -> entry.label().getString()).orElse(id);
    }

    private void refreshBannerButtons() {
        if (this.bannerAddLayerButton != null) this.bannerAddLayerButton.active = this.bannerLayers.size() < 20;
        if (this.bannerEditLayerButton != null) {
            this.bannerEditLayerButton.active = this.selectedBannerLayer >= 0
                    && this.selectedBannerLayer < this.bannerLayers.size();
        }
        if (this.bannerBaseColorButton != null) this.bannerBaseColorButton.setMessage(Component.literal("Base: " + this.bannerBaseColor.getName()));
        setButtonTooltip(this.bannerBaseColorButton, "Choose the base dye color for the banner or shield.");
        if (this.bannerPatternChoiceButton != null) this.bannerPatternChoiceButton.setMessage(Component.literal(
                "Pattern: " + bannerPatternName(bannerPatternId())));
        setButtonTooltip(this.bannerPatternChoiceButton, "Choose the pattern for the selected layer.");
        if (this.bannerLayerColorButton != null) {
            DyeColor color = this.selectedBannerLayer >= 0 && this.selectedBannerLayer < this.bannerLayers.size()
                    ? this.bannerLayers.get(this.selectedBannerLayer).color() : this.bannerLayerColorChoice;
            this.bannerLayerColorButton.setMessage(Component.literal("Layer dye: " + color.getName()));
            setButtonTooltip(this.bannerLayerColorButton, "Choose the dye color for the selected pattern layer.");
        }
        if (this.bannerBaseColorList != null) this.bannerBaseColorList.setSelectedIndex(this.bannerBaseColor.getId());
        if (this.bannerPatternList != null) this.bannerPatternList.setSelectedIndex(this.bannerPatternChoice);
        if (this.bannerLayerColorList != null) {
            DyeColor color = this.selectedBannerLayer >= 0 && this.selectedBannerLayer < this.bannerLayers.size()
                    ? this.bannerLayers.get(this.selectedBannerLayer).color() : this.bannerLayerColorChoice;
            this.bannerLayerColorList.setSelectedIndex(color.getId());
        }
    }

    private List<ChoiceValue> dyeColorOptions() {
        return Arrays.stream(DyeColor.values())
                .map(color -> new ChoiceValue(Integer.toString(color.getId()), Component.literal(color.getName())))
                .toList();
    }

    private DyeColor dyeColor(int index) {
        DyeColor[] values = DyeColor.values();
        return values[Math.max(0, Math.min(index, values.length - 1))];
    }

    private List<ChoiceValue> bannerPatternOptions() {
        return this.bannerPatterns.stream()
                .map(entry -> new ChoiceValue(entry.id(), entry.label()))
                .toList();
    }

    private double attributeAmount(ItemStack stack, String id, double fallback) {
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) return fallback;
        return modifiers.modifiers().stream()
                .filter(entry -> entry.attribute().unwrapKey().map(key -> key.identifier().toString().equals(id)).orElse(false))
                .findFirst().map(entry -> entry.modifier().amount()).orElse(fallback);
    }

    private void buildFooter() {
        int gap = 4;
        int width = Math.max(1, Math.min(104, (this.width - 16 - gap * 4) / 5));
        int total = width * 5 + gap * 4;
        int x = Math.max(0, (this.width - total) / 2);
        this.applyFooterButton = addFooterWidget(Button.builder(Component.literal("Apply"), ignored -> applyCurrentTab())
                .pos(x, this.layout.footerY()).size(width, 20).build());
        this.duplicateFooterButton = addFooterWidget(Button.builder(Component.literal("Duplicate"), ignored -> duplicateTarget())
                .pos(x + width + gap, this.layout.footerY()).size(width, 20).build());
        this.clearEnchantsFooterButton = addFooterWidget(Button.builder(Component.literal("Clear Enchants"), ignored -> clearEnchants())
                .pos(x + (width + gap) * 2, this.layout.footerY()).size(width, 20).build());
        this.deleteFooterButton = addFooterWidget(Button.builder(Component.literal("Delete"), ignored -> deleteTarget())
                .pos(x + (width + gap) * 3, this.layout.footerY()).size(width, 20).build());
        this.deleteFooterButton.active = !targetStack().isEmpty();
        this.closeFooterButton = addFooterWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .pos(x + (width + gap) * 4, this.layout.footerY()).size(width, 20).build());
    }

    private void applyCurrentTab() {
        if (this.currentTab == Tab.PLAYER) applyPlayerEdits();
        else apply();
    }

    private void deleteTarget() {
        if (this.minecraft.player == null || targetStack().isEmpty()) return;
        ClientPlayNetworking.send(new HeldItemTweaker.DeletePayload(this.targetId));
        this.minecraft.player.displayClientMessage(Component.literal("Deleted."), true);
        onClose();
    }

    private void switchTab(Tab tab) {
        closeChoiceLists();
        this.currentTab = tab;
        for (List<AbstractWidget> widgets : this.tabWidgets.values()) {
            for (AbstractWidget widget : widgets) {
                widget.visible = false;
                if (widget instanceof ChoiceList list) list.active = false;
            }
        }
        for (AbstractWidget widget : this.tabWidgets.get(tab)) widget.visible = true;
        for (AbstractWidget widget : this.footerWidgets) widget.visible = true;
        for (Button button : this.tabButtons) button.visible = true;
        boolean playerTab = tab == Tab.PLAYER;
        if (this.targetList != null) {
            this.targetList.visible = !playerTab;
            this.targetList.active = !playerTab;
        }
        if (this.applyFooterButton != null) this.applyFooterButton.setMessage(Component.literal(playerTab ? "Apply Player" : "Apply"));
        if (this.duplicateFooterButton != null) this.duplicateFooterButton.active = !playerTab && !targetStack().isEmpty();
        if (this.clearEnchantsFooterButton != null) this.clearEnchantsFooterButton.active = !playerTab;
        if (this.deleteFooterButton != null) this.deleteFooterButton.active = !playerTab && !targetStack().isEmpty();
        applyTabScrollPositions(tab);
    }

    private void refreshUnbreakable() {
        if (this.unbreakableButton != null) {
            this.unbreakableButton.setMessage(Component.literal("Unbreakable: " + (this.unbreakable ? "ON" : "OFF")));
            setButtonTooltip(this.unbreakableButton, "Prevent this item from losing durability when used.");
        }
    }

    private void refreshInfiniteUse() {
        if (this.infiniteUseButton != null) {
            this.infiniteUseButton.setMessage(Component.literal("Infinite use: " + (this.infiniteUse ? "ON" : "OFF")));
            setButtonTooltip(this.infiniteUseButton, "Keep the original item after use. Applies to bows, arrows, buckets, food, potions, and other usable items.");
        }
    }

    private void removeInfinitySelection() {
        this.selected.entrySet().removeIf(entry -> entry.getKey().unwrapKey()
                .map(key -> key.identifier().equals(Identifier.fromNamespaceAndPath("minecraft", "infinity")))
                .orElse(false));
        if (this.enchantmentList != null) this.enchantmentList.refresh();
    }

    private void refreshTrimButtons() {
        if (this.trimMaterialButton != null) {
            String value = this.trimMaterialIndex == 0 ? "None" : this.trimMaterials.get(this.trimMaterialIndex - 1).label().getString();
            this.trimMaterialButton.setMessage(Component.literal("Material: " + value));
            setButtonTooltip(this.trimMaterialButton, "Choose the metal or gem color used by the armor trim.");
        }
        if (this.trimPatternButton != null) {
            String value = this.trimPatternIndex == 0 ? "None" : this.trimPatterns.get(this.trimPatternIndex - 1).label().getString();
            this.trimPatternButton.setMessage(Component.literal("Pattern: " + value));
            setButtonTooltip(this.trimPatternButton, "Choose the armor trim pattern applied to the item.");
        }
        if (this.trimMaterialList != null) this.trimMaterialList.setSelectedIndex(this.trimMaterialIndex);
        if (this.trimPatternList != null) this.trimPatternList.setSelectedIndex(this.trimPatternIndex);
    }

    private List<ChoiceValue> trimMaterialOptions() {
        List<ChoiceValue> result = new ArrayList<>();
        result.add(new ChoiceValue("", Component.literal("None")));
        for (TrimEntry entry : this.trimMaterials) result.add(new ChoiceValue(entry.id(), entry.label()));
        return result;
    }

    private List<ChoiceValue> trimPatternOptions() {
        List<ChoiceValue> result = new ArrayList<>();
        result.add(new ChoiceValue("", Component.literal("None")));
        for (TrimEntry entry : this.trimPatterns) result.add(new ChoiceValue(entry.id(), entry.label()));
        return result;
    }

    private void refreshDyeFields() {
        if (this.hexDyeBox != null) this.hexDyeBox.setValue(this.dyeColor < 0 ? "" : String.format("#%06X", this.dyeColor & 0xFFFFFF));
    }

    private void applyHexDye() {
        String value = this.hexDyeBox == null ? "" : this.hexDyeBox.getValue().trim().replace("#", "");
        if (value.length() != 6) return;
        try {
            this.dyeColor = Integer.parseInt(value, 16) & 0xFFFFFF;
            refreshDyeFields();
        } catch (NumberFormatException ignored) {
        }
    }

    private int indexOf(List<TrimEntry> entries, String id) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id().equals(id)) return i;
        return -1;
    }

    private String trimMaterialId() {
        return this.trimMaterialIndex == 0 ? "" : this.trimMaterials.get(this.trimMaterialIndex - 1).id();
    }

    private String trimPatternId() {
        return this.trimPatternIndex == 0 ? "" : this.trimPatterns.get(this.trimPatternIndex - 1).id();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.hoveredListStack = ItemStack.EMPTY;
        this.hoveredListHeader = Component.empty();
        this.renderTransparentBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);

        // Render global controls first. The active tab is rendered in its own clipped viewport
        // afterward so scrolled fields cannot leak across tabs, into the footer, or over the title.
        List<AbstractWidget> activeWidgets = this.tabWidgets.get(this.currentTab);
        for (AbstractWidget widget : activeWidgets) widget.visible = false;
        super.render(graphics, mouseX, mouseY, partialTick);

        for (AbstractWidget widget : activeWidgets) widget.visible = true;
        if (this.currentTab != Tab.PLAYER) {
            graphics.drawString(this.font, Component.literal("Target slot"), this.layout.sidebarX(),
                    this.layout.contentTop() - this.font.lineHeight - 6, 0xFFFFFFFF);
        }
        graphics.enableScissor(this.layout.contentX(), this.layout.contentTop(),
                this.layout.contentX() + this.layout.contentWidth(), this.layout.contentBottom());
        int scroll = tabScrollOffset(this.currentTab);
        for (FieldLabel label : this.labels.get(this.currentTab)) {
            graphics.drawString(this.font, Component.literal(label.text()), label.x(),
                    shiftedY(this.currentTab, label.y()) - scroll, 0xFFBDBDBD);
        }
        // Render normal controls first and the open dropdown last. Otherwise later
        // controls paint over the dropdown and its rows cannot receive clicks.
        for (AbstractWidget widget : activeWidgets) {
            if (!(widget instanceof ChoiceList)) widget.render(graphics, mouseX, mouseY, partialTick);
        }
        for (ChoiceList list : this.choiceLists) {
            if (list.visible && list.active && activeWidgets.contains(list)) {
                list.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        renderSuggestionPopup(graphics, mouseX, mouseY);
        renderVisualColorSwatches(graphics);
        graphics.disableScissor();
        renderTabScrollbar(graphics);
        renderPreview(graphics, mouseX, mouseY);
        renderListTooltip(graphics, mouseX, mouseY);
        if (this.currentTab == Tab.ENCHANTMENTS && this.enchantmentList.children().isEmpty()) {
            graphics.drawCenteredString(this.font, Component.literal("No supported enchantments for this item."),
                    this.width / 2, this.layout.contentTop() + 12, 0xFFFF5555);
        }
    }

    private void renderListTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.hoveredListStack.isEmpty() && this.hoveredListHeader.getString().isBlank()) return;
        List<Component> lines = new ArrayList<>();
        if (!this.hoveredListHeader.getString().isBlank()) lines.add(this.hoveredListHeader.copy().withStyle(ChatFormatting.GRAY));
        if (!this.hoveredListStack.isEmpty()) {
            lines.addAll(this.hoveredListStack.getTooltipLines(
                    Item.TooltipContext.of(this.minecraft.level), this.minecraft.player, TooltipFlag.Default.NORMAL));
        }
        List<ClientTooltipComponent> tooltip = lines.stream()
                .map(line -> ClientTooltipComponent.create(line.getVisualOrderText())).toList();
        if (!tooltip.isEmpty()) {
            graphics.renderTooltip(this.font, tooltip, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        }
    }

    private void renderTabScrollbar(GuiGraphics graphics) {
        int maximum = this.tabScrollMaximums.getOrDefault(this.currentTab, 0);
        if (maximum <= 0) return;
        int top = this.layout.contentTop();
        int bottom = this.layout.contentBottom();
        int height = bottom - top;
        int x = Math.min(this.width - 7, this.layout.contentX() + this.layout.contentWidth() + 4);
        int thumbHeight = Math.max(18, height * height / (height + maximum));
        int thumbTravel = Math.max(1, height - thumbHeight);
        int thumbY = top + (thumbTravel * tabScrollOffset(this.currentTab)) / maximum;
        graphics.fill(x, top, x + 6, bottom, 0x66101010);
        graphics.fill(x, thumbY, x + 6, thumbY + thumbHeight, 0xFFBDBDBD);
        graphics.renderOutline(x, thumbY, 6, thumbHeight, 0xFF333333);
    }

    private boolean isOverTabScrollbar(double mouseX, double mouseY) {
        int maximum = this.tabScrollMaximums.getOrDefault(this.currentTab, 0);
        int x = Math.min(this.width - 7, this.layout.contentX() + this.layout.contentWidth() + 4);
        return maximum > 0 && mouseX >= x && mouseX < x + 6
                && mouseY >= this.layout.contentTop() && mouseY < this.layout.contentBottom();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0 && isOverSuggestionPopup(event.x(), event.y())) {
            int row = (int) ((event.y() - suggestionPopupY()) / suggestionRowHeight());
            return acceptSuggestion(row);
        }
        if (event.button() == 0 && isOverTabScrollbar(event.x(), event.y())) {
            int maximum = this.tabScrollMaximums.getOrDefault(this.currentTab, 0);
            int height = this.layout.contentBottom() - this.layout.contentTop();
            int thumbHeight = Math.max(18, height * height / (height + maximum));
            int travel = Math.max(1, height - thumbHeight);
            int target = (int) ((event.y() - this.layout.contentTop() - thumbHeight / 2.0)
                    * maximum / travel);
            setTabScrollOffset(this.currentTab, target);
            return true;
        }
        // Route the open popup directly. Fixed-position controls are also screen
        // children and can otherwise receive the click before the dropdown row.
        if (event.button() == 0 && this.openChoiceList != null
                && this.openChoiceList.visible && this.openChoiceList.active
                && this.openChoiceList.isMouseOver(event.x(), event.y())) {
            return this.openChoiceList.mouseClicked(event, doubled);
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Let the native lists consume the wheel first. Their own scrolling must not also move
        // the surrounding tab document.
        if (this.targetList != null && this.targetList.visible && this.targetList.isMouseOver(mouseX, mouseY)) {
            return this.targetList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (this.currentTab == Tab.ENCHANTMENTS && this.enchantmentList != null
                && this.enchantmentList.isMouseOver(mouseX, mouseY)) {
            return this.enchantmentList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (this.currentTab == Tab.BANNER && this.bannerLayerList != null
                && this.bannerLayerList.isMouseOver(mouseX, mouseY)) {
            return this.bannerLayerList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (this.currentTab == Tab.ADVANCED && this.potionEffectList != null
                && this.potionEffectList.isMouseOver(mouseX, mouseY)) {
            return this.potionEffectList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (this.currentTab == Tab.PLAYER) {
            if (this.playerEffectList != null && this.playerEffectList.isMouseOver(mouseX, mouseY)) {
                return this.playerEffectList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            }
            if (this.playerEffectChoiceList != null && this.playerEffectChoiceList.isMouseOver(mouseX, mouseY)) {
                return this.playerEffectChoiceList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            }
        }
        for (ChoiceList list : this.choiceLists) {
            if (list.visible && list.isMouseOver(mouseX, mouseY)) {
                return list.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            }
        }
        if (mouseX >= this.layout.contentX() && mouseX < this.layout.contentX() + this.layout.contentWidth()
                && mouseY >= this.layout.contentTop() && mouseY < this.layout.contentBottom()) {
            int maximum = this.tabScrollMaximums.getOrDefault(this.currentTab, 0);
            if (maximum > 0 && verticalAmount != 0) {
                setTabScrollOffset(this.currentTab,
                        tabScrollOffset(this.currentTab) - (int) Math.round(verticalAmount * 24.0));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void renderVisualColorSwatches(GuiGraphics graphics) {
        if (this.currentTab != Tab.VISUALS || !this.dyeCapable) return;
        int gap = 2;
        int scroll = tabScrollOffset(Tab.VISUALS);
        for (int i = 0; i < DyeColor.values().length; i++) {
            int x = this.layout.contentX() + (i % 8) * (this.dyeButtonWidth + gap);
            int y = shiftedY(Tab.VISUALS, this.dyeGridY + (i / 8) * 22) - scroll;
            int color = DyeColor.values()[i].getTextureDiffuseColor() | 0xFF000000;
            graphics.fill(x + 4, y + 4, x + this.dyeButtonWidth - 4, y + 14, color);
            if (this.dyeColor == DyeColor.values()[i].getTextureDiffuseColor()) {
                graphics.renderOutline(x, y, this.dyeButtonWidth, 18, 0xFFFFFF55);
            }
        }
    }

    private void renderListPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        // Match vanilla list chrome: opaque dark body, black outside edge, subtle
        // gray top/bottom separators. Avoid bright custom white outlines.
        graphics.fill(x, y, x + width, y + height, 0xF0101010);
        graphics.renderOutline(x, y, width, height, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, 0xFF555555);
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, 0xFF555555);
    }

    private void renderPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.currentTab == Tab.PLAYER || this.width < 620) return;
        ItemStack preview = previewStack();
        if (preview.isEmpty()) return;
        int x = this.layout.contentX() + this.layout.contentWidth() + 24;
        int y = this.layout.contentTop();
        if (this.bannerCapable) {
            renderBannerOrShieldPreview(graphics, preview, x, y, mouseX, mouseY);
            return;
        }
        boolean entityPreview = this.trimCapable && this.minecraft.level != null && this.minecraft.player != null;
        if (entityPreview) {
            if (this.previewStand == null) {
                this.previewStand = new ArmorStand(this.minecraft.level, 0.0D, 0.0D, 0.0D);
                this.previewStand.setNoGravity(true);
                this.previewStand.setShowArms(true);
            }
            for (EquipmentSlot slot : EquipmentSlot.values()) this.previewStand.setItemSlot(slot, ItemStack.EMPTY);
            EquipmentSlot slot = this.minecraft.player.getEquipmentSlotForItem(preview);
            this.previewStand.setItemSlot(slot, preview);
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x - 42, y, x + 78, y + 150,
                    65, 0.0F, mouseX, mouseY, this.previewStand);
        } else {
            graphics.renderItem(preview, x, y);
            graphics.renderItemDecorations(this.font, preview, x, y, null);
        }
        int hitWidth = entityPreview ? 120 : 16;
        int hitHeight = entityPreview ? 150 : 16;
        if (mouseX >= x - (entityPreview ? 42 : 0) && mouseX < x + hitWidth
                && mouseY >= y && mouseY < y + hitHeight) {
            List<ClientTooltipComponent> tooltip = preview.getTooltipLines(
                            Item.TooltipContext.of(this.minecraft.level), this.minecraft.player, TooltipFlag.Default.NORMAL)
                    .stream().map(line -> ClientTooltipComponent.create(line.getVisualOrderText())).toList();
            graphics.renderTooltip(this.font, tooltip, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        }
    }

    private void renderBannerOrShieldPreview(GuiGraphics graphics, ItemStack preview, int x, int y,
                                             int mouseX, int mouseY) {
        int previewWidth = Math.min(220, Math.max(132, this.width - x - 16));
        int previewHeight = Math.min(260, Math.max(180, this.layout.contentBottom() - y));
        if (previewWidth < 100 || previewHeight < 100) return;
        int previewX = Math.max(x, this.width - previewWidth - 12);
        int previewY = Math.max(y, this.layout.contentTop());

        if (this.shieldCapable) {
            renderLargeShield(graphics, preview, previewX, previewY, previewWidth, previewHeight);
        } else {
            ensureBannerPreviewModel();
            if (this.bannerPreviewFlag == null) return;
            DyeColor base = preview.getOrDefault(DataComponents.BASE_COLOR, DyeColor.WHITE);
            BannerPatternLayers layers = preview.getOrDefault(DataComponents.BANNER_PATTERNS,
                    BannerPatternLayers.EMPTY);
            graphics.submitBannerPatternRenderState(this.bannerPreviewFlag, base, layers,
                    previewX + 18, previewY + 8, previewX + previewWidth - 18, previewY + previewHeight - 8);
        }

        if (mouseX >= previewX && mouseX < previewX + previewWidth
                && mouseY >= previewY && mouseY < previewY + previewHeight) {
            List<ClientTooltipComponent> tooltip = preview.getTooltipLines(
                            Item.TooltipContext.of(this.minecraft.level), this.minecraft.player, TooltipFlag.Default.NORMAL)
                    .stream().map(line -> ClientTooltipComponent.create(line.getVisualOrderText())).toList();
            graphics.renderTooltip(this.font, tooltip, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        }
    }

    private void ensureBannerPreviewModel() {
        if (this.bannerPreviewFlag != null || this.minecraft == null) return;
        this.bannerPreviewFlag = new BannerFlagModel(this.minecraft.getEntityModels()
                .bakeLayer(ModelLayers.STANDING_BANNER_FLAG));
    }

    private void renderLargeShield(GuiGraphics graphics, ItemStack preview, int x, int y, int width, int height) {
        float scale = Math.min(10.0F, Math.min((width - 24) / 16.0F, (height - 24) / 16.0F));
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(scale, scale);
        graphics.renderItem(preview, -8, -8);
        graphics.pose().popMatrix();
    }

    private ItemStack previewStack() {
        ItemStack preview = targetStack().copy();
        if (preview.isEmpty()) return preview;
        EnchantmentHelper.updateEnchantments(preview, mutable -> {
            mutable.removeIf(ignored -> true);
            this.selected.forEach(mutable::set);
        });
        String name = this.nameBox == null ? "" : this.nameBox.getValue().trim();
        if (name.isBlank()) preview.remove(DataComponents.CUSTOM_NAME);
        else preview.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        String lore = this.loreBox == null ? "" : this.loreBox.getValue().trim();
        if (lore.isBlank()) preview.remove(DataComponents.LORE);
        else preview.set(DataComponents.LORE, new ItemLore(List.of(lore.split("\\|", -1)).stream()
                .limit(ItemLore.MAX_LINES).map(line -> (Component) Component.literal(line)).toList()));
        if (preview.isDamageableItem() && this.damageBox != null) {
            int damage = parseInteger(this.damageBox.getValue());
            if (damage >= 0 && damage <= preview.getMaxDamage()) preview.setDamageValue(damage);
        }
        if (this.unbreakable) preview.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        else preview.remove(DataComponents.UNBREAKABLE);
        if (this.infiniteUse) preview.set(HeldItemTweaker.INFINITE_USE, true);
        else preview.remove(HeldItemTweaker.INFINITE_USE);
        applyTrim(preview);
        if (this.dyeCapable) {
            if (this.dyeColor < 0) preview.remove(DataComponents.DYED_COLOR);
            else preview.set(DataComponents.DYED_COLOR, new DyedItemColor(this.dyeColor));
        }
        applyBanner(preview);
        applyComponentEdits(preview);
        applySpecialPreview(preview);
        applyAdvancedPreview(preview);
        applyDataPreview(preview);
        applyAttributeEdits(preview, this.dirtyAttributes, this.attributeBoxes);
        return preview;
    }

    private void applyBanner(ItemStack stack) {
        if (!this.bannerCapable || this.minecraft.level == null) return;
        stack.set(DataComponents.BASE_COLOR, this.bannerBaseColor);
        List<BannerPatternLayers.Layer> layers = new ArrayList<>();
        var registry = this.minecraft.level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        for (BannerLayerChoice choice : this.bannerLayers) {
            Identifier id = Identifier.tryParse(choice.patternId());
            if (id == null) continue;
            Holder<BannerPattern> holder = registry.get(id).orElse(null);
            if (holder != null) layers.add(new BannerPatternLayers.Layer(holder, choice.color()));
        }
        if (layers.isEmpty()) stack.remove(DataComponents.BANNER_PATTERNS);
        else stack.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers(layers));
    }

    private void applySpecialPreview(ItemStack stack) {
        if (!this.specialCapable || !this.specialDirty || this.minecraft.level == null) return;
        if (this.potionCapable && this.potionBox != null) {
            String value = this.potionBox.getValue().trim();
            if (value.isBlank() || value.equalsIgnoreCase("clear")) stack.remove(DataComponents.POTION_CONTENTS);
            else {
                Identifier id = Identifier.tryParse(value);
                if (id != null) {
                    Holder<net.minecraft.world.item.alchemy.Potion> potion = this.minecraft.level.registryAccess()
                            .lookupOrThrow(Registries.POTION).get(id).orElse(null);
                    if (potion != null) stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
                }
            }
        }
        if (this.foodCapable && this.foodNutritionBox != null && this.foodSaturationBox != null) {
            Integer nutrition = parseBoundedInt(this.foodNutritionBox.getValue(), 0, 20);
            Float saturation = parseBoundedFloat(this.foodSaturationBox.getValue(), 0.0F, 100.0F);
            if (nutrition != null && saturation != null) stack.set(DataComponents.FOOD,
                    new FoodProperties(nutrition, saturation, this.foodAlwaysEat));
        }
        if (this.weaponCapable && this.weaponDamageBox != null && this.weaponDisableBox != null) {
            Integer damage = parseBoundedInt(this.weaponDamageBox.getValue(), 0, 100);
            Float disable = parseBoundedFloat(this.weaponDisableBox.getValue(), 0.0F, 10.0F);
            if (damage != null && disable != null) stack.set(DataComponents.WEAPON, new Weapon(damage, disable));
        }
        if (this.fireworksCapable && this.fireworkFlightBox != null) {
            Integer flight = parseBoundedInt(this.fireworkFlightBox.getValue(), 0, 127);
            Fireworks current = stack.get(DataComponents.FIREWORKS);
            if (flight != null) stack.set(DataComponents.FIREWORKS, new Fireworks(flight,
                    current == null ? List.of() : current.explosions()));
            if (this.fireworkShapeBox != null) {
                FireworkExplosion.Shape shape = parseShape(this.fireworkShapeBox.getValue());
                List<Integer> colors = parseColors(this.fireworkColorsBox == null ? "" : this.fireworkColorsBox.getValue());
                List<Integer> fades = parseColors(this.fireworkFadeColorsBox == null ? "" : this.fireworkFadeColorsBox.getValue());
                if (shape != null) stack.set(DataComponents.FIREWORK_EXPLOSION, new FireworkExplosion(shape,
                        new it.unimi.dsi.fastutil.ints.IntArrayList(colors),
                        new it.unimi.dsi.fastutil.ints.IntArrayList(fades), this.fireworkTrail, this.fireworkTwinkle));
            }
        }
        if (this.containerCapable && this.containerItemsBox != null) {
            List<ItemStack> items = parseClientItems(this.containerItemsBox.getValue(), false);
            if (items != null) stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        }
        if (this.bundleCapable && this.bundleItemsBox != null) {
            List<ItemStack> items = parseClientItems(this.bundleItemsBox.getValue(), true);
            if (items != null) stack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(items));
        }
        if (this.chargedProjectilesCapable && this.chargedProjectilesBox != null) {
            List<ItemStack> items = parseClientItems(this.chargedProjectilesBox.getValue(), false);
            if (items != null) stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(items));
        }
        if (this.mapColorCapable && this.mapColorBox != null) {
            Integer color = parseBoundedInt(this.mapColorBox.getValue(), 0, 0xFFFFFF);
            if (color != null) stack.set(DataComponents.MAP_COLOR, new MapItemColor(color));
        }
    }

    private void applyAdvancedPreview(ItemStack stack) {
        if (!this.advancedDirty && !this.gliderDirty) return;
        Integer maxStack = parseBoundedInt(this.maxStackBox == null ? "" : this.maxStackBox.getValue(), 1, 99);
        Integer maxDamage = parseBoundedInt(this.maxDamageBox == null ? "" : this.maxDamageBox.getValue(), 0, 1_000_000);
        Integer repairCost = parseBoundedInt(this.repairCostBox == null ? "" : this.repairCostBox.getValue(), 0, 100_000);
        if (maxStack != null) stack.set(DataComponents.MAX_STACK_SIZE, maxStack);
        if (maxDamage != null) stack.set(DataComponents.MAX_DAMAGE, maxDamage);
        if (repairCost != null) stack.set(DataComponents.REPAIR_COST, repairCost);

        if (this.potionDurationScaleBox != null && stack.has(DataComponents.POTION_CONTENTS)) {
            String value = this.potionDurationScaleBox.getValue().trim();
            if (value.equalsIgnoreCase("clear") || value.isBlank()) stack.remove(DataComponents.POTION_DURATION_SCALE);
            else {
                Float scale = parseBoundedFloat(value, 0.0F, 10.0F);
                if (scale != null) stack.set(DataComponents.POTION_DURATION_SCALE, scale);
            }
            PotionContents current = stack.get(DataComponents.POTION_CONTENTS);
            List<MobEffectInstance> effects = clientPotionEffects();
            if (current != null) stack.set(DataComponents.POTION_CONTENTS, new PotionContents(current.potion(),
                    current.customColor(), effects, current.customName()));
        }
        if (stack.has(DataComponents.FIREWORKS) && this.fireworkExplosionsBox != null) {
            List<FireworkExplosion> explosions = parseClientExplosions(this.fireworkExplosionsBox.getValue());
            if (explosions != null && explosions.size() <= 256) {
                Fireworks current = stack.get(DataComponents.FIREWORKS);
                if (current != null) stack.set(DataComponents.FIREWORKS, new Fireworks(current.flightDuration(), explosions));
            }
        }
        if (stack.has(DataComponents.TOOL) && this.toolDefaultsBox != null) {
            Tool current = stack.get(DataComponents.TOOL);
            String[] parts = this.toolDefaultsBox.getValue().trim().split(",", -1);
            if (current != null && parts.length == 3) {
                Float speed = parseBoundedFloat(parts[0], 0.0F, 10_000.0F);
                Integer damage = parseBoundedInt(parts[1], 0, 1_000_000);
                Boolean creative = parseBooleanFlag(parts[2]);
                if (speed != null && damage != null && creative != null) {
                    stack.set(DataComponents.TOOL, new Tool(current.rules(), speed, damage, creative));
                }
            }
        }
        if (stack.has(DataComponents.ATTACK_RANGE) && this.attackRangeBox != null) {
            AttackRange range = parseClientAttackRange(this.attackRangeBox.getValue());
            if (range != null) stack.set(DataComponents.ATTACK_RANGE, range);
        }
        if (stack.has(DataComponents.CONSUMABLE) && this.consumableBox != null) {
            Consumable current = stack.get(DataComponents.CONSUMABLE);
            Consumable updated = parseClientConsumable(current, this.consumableBox.getValue());
            if (updated != null) stack.set(DataComponents.CONSUMABLE, updated);
        }
        if (this.cooldownBox != null) {
            String value = this.cooldownBox.getValue().trim();
            if (value.equalsIgnoreCase("clear")) stack.remove(DataComponents.USE_COOLDOWN);
            else if (!value.isBlank()) {
                UseCooldown cooldown = parseClientCooldown(value);
                if (cooldown != null) stack.set(DataComponents.USE_COOLDOWN, cooldown);
            }
        }
        if (this.gliderDirty) {
            if (this.glider) stack.set(DataComponents.GLIDER, Unit.INSTANCE);
            else stack.remove(DataComponents.GLIDER);
        }
    }

    private void applyDataPreview(ItemStack stack) {
        if (!this.dataDirty || this.minecraft.level == null) return;
        applyIdentifierComponent(stack, DataComponents.ITEM_MODEL, this.itemModelBox);
        applyIdentifierComponent(stack, DataComponents.TOOLTIP_STYLE, this.tooltipStyleBox);
        Integer enchantable = parseBoundedInt(this.enchantableBox == null ? "" : this.enchantableBox.getValue(), 0, 255);
        if (enchantable != null) stack.set(DataComponents.ENCHANTABLE, new Enchantable(enchantable));
        applyRepairablePreview(stack);
        String resistant = this.damageResistantBox == null ? "" : this.damageResistantBox.getValue().trim();
        if (resistant.isBlank()) stack.remove(DataComponents.DAMAGE_RESISTANT);
        else {
            Identifier id = Identifier.tryParse(resistant);
            if (id != null) stack.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(TagKey.create(Registries.DAMAGE_TYPE, id)));
        }
        String profile = this.profileBox == null ? "" : this.profileBox.getValue().trim();
        if (profile.isBlank()) stack.remove(DataComponents.PROFILE);
        else if (stack.is(Items.PLAYER_HEAD)) stack.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(profile));
        applyIdentifierComponent(stack, DataComponents.NOTE_BLOCK_SOUND, this.noteBlockSoundBox);

        String[] use = (this.useEffectsBox == null ? "" : this.useEffectsBox.getValue()).trim().split(",", -1);
        if (use.length == 3) {
            Boolean sprint = parseBooleanFlag(use[0]);
            Boolean vibrations = parseBooleanFlag(use[1]);
            Float speed = parseBoundedFloat(use[2], 0.0F, 10.0F);
            if (sprint != null && vibrations != null && speed != null) stack.set(DataComponents.USE_EFFECTS,
                    new UseEffects(sprint, vibrations, speed));
        }
        SwingAnimation swing = parseSwingAnimation(this.swingAnimationBox == null ? "" : this.swingAnimationBox.getValue());
        if (swing == null) stack.remove(DataComponents.SWING_ANIMATION);
        else stack.set(DataComponents.SWING_ANIMATION, swing);

        String[] piercing = (this.piercingBox == null ? "" : this.piercingBox.getValue()).trim().split(",", -1);
        if (piercing.length == 2) {
            Boolean knockback = parseBooleanFlag(piercing[0]);
            Boolean dismount = parseBooleanFlag(piercing[1]);
            if (knockback != null && dismount != null) {
                PiercingWeapon current = stack.get(DataComponents.PIERCING_WEAPON);
                stack.set(DataComponents.PIERCING_WEAPON, new PiercingWeapon(knockback, dismount,
                        current == null ? Optional.empty() : current.sound(),
                        current == null ? Optional.empty() : current.hitSound()));
            }
        }
        applyEquippablePreview(stack);
    }

    private void applyIdentifierComponent(ItemStack stack, net.minecraft.core.component.DataComponentType<Identifier> type,
                                           EditBox box) {
        if (box == null) return;
        String value = box.getValue().trim();
        if (value.isBlank() || value.equalsIgnoreCase("clear")) stack.remove(type);
        else {
            Identifier id = Identifier.tryParse(value);
            if (id != null) stack.set(type, id);
        }
    }

    private void applyRepairablePreview(ItemStack stack) {
        String value = this.repairableBox == null ? "" : this.repairableBox.getValue().trim();
        if (value.isBlank() || value.equalsIgnoreCase("clear")) {
            stack.remove(DataComponents.REPAIRABLE);
            return;
        }
        List<Holder<Item>> holders = new ArrayList<>();
        for (String part : value.split(",")) {
            Identifier id = Identifier.tryParse(part.trim());
            if (id == null) return;
            Holder<Item> holder = this.minecraft.level.registryAccess().lookupOrThrow(Registries.ITEM).get(id).orElse(null);
            if (holder == null || holders.size() >= 64) return;
            holders.add(holder);
        }
        if (!holders.isEmpty()) stack.set(DataComponents.REPAIRABLE, new Repairable(HolderSet.direct(holders)));
    }

    private SwingAnimation parseSwingAnimation(String value) {
        String[] parts = value.trim().split(",", -1);
        if (parts.length != 2) return null;
        Integer duration = parseBoundedInt(parts[1], 1, 1000);
        if (duration == null) return null;
        for (SwingAnimationType type : SwingAnimationType.values()) {
            if (type.getSerializedName().equalsIgnoreCase(parts[0].trim()) || type.name().equalsIgnoreCase(parts[0].trim())) {
                return new SwingAnimation(type, duration);
            }
        }
        return null;
    }

    private void applyEquippablePreview(ItemStack stack) {
        Equippable current = stack.get(DataComponents.EQUIPPABLE);
        if (current == null || this.equippableBox == null) return;
        String[] parts = this.equippableBox.getValue().trim().split(",", -1);
        if (parts.length != 6) return;
        EquipmentSlot slot = EquipmentSlot.byName(parts[0].trim());
        Boolean dispensable = parseBooleanFlag(parts[1]);
        Boolean swappable = parseBooleanFlag(parts[2]);
        Boolean damageOnHurt = parseBooleanFlag(parts[3]);
        Boolean equipOnInteract = parseBooleanFlag(parts[4]);
        Boolean canBeSheared = parseBooleanFlag(parts[5]);
        if (slot == null || dispensable == null || swappable == null || damageOnHurt == null
                || equipOnInteract == null || canBeSheared == null) return;
        stack.set(DataComponents.EQUIPPABLE, new Equippable(slot, current.equipSound(), current.assetId(),
                current.cameraOverlay(), current.allowedEntities(), dispensable, swappable, damageOnHurt,
                equipOnInteract, canBeSheared, current.shearingSound()));
    }

    private Integer parseBoundedInt(String value, int min, int max) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= min && parsed <= max ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Float parseBoundedFloat(String value, float min, float max) {
        try {
            float parsed = Float.parseFloat(value.trim());
            return Float.isFinite(parsed) && parsed >= min && parsed <= max ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private FireworkExplosion.Shape parseShape(String value) {
        for (FireworkExplosion.Shape shape : FireworkExplosion.Shape.values()) {
            if (shape.getSerializedName().equalsIgnoreCase(value.trim()) || shape.name().equalsIgnoreCase(value.trim())) return shape;
        }
        return null;
    }

    private List<Integer> parseColors(String value) {
        List<Integer> result = new ArrayList<>();
        for (String part : value.split(",")) {
            if (part.isBlank()) continue;
            Integer parsed = parseBoundedInt(part, 0, 0xFFFFFF);
            if (parsed != null && result.size() < 16) result.add(parsed);
        }
        return result;
    }

    private Boolean parseBooleanFlag(String value) {
        if (value.trim().equals("1") || value.trim().equalsIgnoreCase("true")) return true;
        if (value.trim().equals("0") || value.trim().equalsIgnoreCase("false")) return false;
        return null;
    }

    private List<MobEffectInstance> parseClientEffects(String value) {
        if (this.minecraft.level == null) return List.of();
        List<MobEffectInstance> result = new ArrayList<>();
        for (String entry : value.split("\\^")) {
            if (entry.isBlank()) continue;
            String[] parts = entry.trim().split("\\*", -1);
            if (parts.length != 3 || result.size() >= 32) continue;
            Identifier id = Identifier.tryParse(parts[0]);
            Integer duration = parseBoundedInt(parts[1], 1, MobEffectInstance.INFINITE_DURATION);
            Integer amplifier = parseBoundedInt(parts[2], 0, 255);
            if (id == null || duration == null || amplifier == null) continue;
            Holder<net.minecraft.world.effect.MobEffect> effect = this.minecraft.level.registryAccess()
                    .lookupOrThrow(Registries.MOB_EFFECT).get(id).orElse(null);
            if (effect != null) result.add(new MobEffectInstance(effect, duration, amplifier));
        }
        return result;
    }

    private List<MobEffectInstance> clientPotionEffects() {
        List<MobEffectInstance> result = new ArrayList<>();
        for (EffectEdit edit : this.potionEffectEdits) {
            if (!edit.enabled || edit.effect == null) continue;
            result.add(new MobEffectInstance(edit.effect, edit.duration, edit.amplifier,
                    edit.ambient, edit.particles, edit.icon));
            if (result.size() >= 32) break;
        }
        return result;
    }

    private List<FireworkExplosion> parseClientExplosions(String value) {
        List<FireworkExplosion> result = new ArrayList<>();
        for (String entry : value.split("\\^")) {
            if (entry.isBlank()) continue;
            String[] parts = entry.trim().split("~", -1);
            if (parts.length != 5 || result.size() >= 256) return null;
            FireworkExplosion.Shape shape = parseShape(parts[0]);
            Boolean trail = parseBooleanFlag(parts[3]);
            Boolean twinkle = parseBooleanFlag(parts[4]);
            if (shape == null || trail == null || twinkle == null) return null;
            result.add(new FireworkExplosion(shape, new it.unimi.dsi.fastutil.ints.IntArrayList(parseColors(parts[1])),
                    new it.unimi.dsi.fastutil.ints.IntArrayList(parseColors(parts[2])), trail, twinkle));
        }
        return result;
    }

    private AttackRange parseClientAttackRange(String value) {
        String[] parts = value.trim().split(",", -1);
        if (parts.length != 6) return null;
        float[] values = new float[6];
        for (int i = 0; i < values.length; i++) {
            Float parsed = parseBoundedFloat(parts[i], 0.0F, 128.0F);
            if (parsed == null) return null;
            values[i] = parsed;
        }
        if (values[0] > values[1] || values[2] > values[3]) return null;
        return new AttackRange(values[0], values[1], values[2], values[3], values[4], values[5]);
    }

    private Consumable parseClientConsumable(Consumable current, String value) {
        String[] parts = value.trim().split(",", -1);
        if (parts.length != 3) return null;
        Float seconds = parseBoundedFloat(parts[0], 0.05F, 3600.0F);
        Boolean particles = parseBooleanFlag(parts[2]);
        if (seconds == null || particles == null) return null;
        ItemUseAnimation animation = null;
        for (ItemUseAnimation candidate : ItemUseAnimation.values()) {
            if (candidate.getSerializedName().equalsIgnoreCase(parts[1].trim())
                    || candidate.name().equalsIgnoreCase(parts[1].trim())) {
                animation = candidate;
                break;
            }
        }
        return animation == null ? null : new Consumable(seconds, animation, current.sound(), particles,
                current.onConsumeEffects());
    }

    private UseCooldown parseClientCooldown(String value) {
        String[] parts = value.split("\\|", -1);
        if (parts.length > 2) return null;
        Float seconds = parseBoundedFloat(parts[0], 0.0F, 3600.0F);
        if (seconds == null) return null;
        if (parts.length == 1 || parts[1].isBlank()) return new UseCooldown(seconds);
        Identifier id = Identifier.tryParse(parts[1].trim());
        return id == null ? null : new UseCooldown(seconds, Optional.of(id));
    }

    private List<ItemStack> parseClientItems(String value, boolean bundle) {
        if (this.minecraft.level == null) return null;
        List<ItemStack> result = new ArrayList<>();
        for (String part : value.split(",")) {
            if (part.isBlank()) continue;
            String[] pieces = part.trim().split("\\*", 2);
            Identifier id = Identifier.tryParse(pieces[0]);
            int count = pieces.length == 1 ? 1 : parseBoundedInt(pieces[1], 1, 99) == null ? -1 : Integer.parseInt(pieces[1]);
            if (id == null || count < 1) return null;
            Holder<Item> holder = this.minecraft.level.registryAccess().lookupOrThrow(Registries.ITEM).get(id).orElse(null);
            if (holder == null) return null;
            ItemStack item = new ItemStack(holder, count);
            if (bundle && !BundleContents.canItemBeInBundle(item)) return null;
            if (result.size() >= 64) return null;
            result.add(item);
        }
        return result;
    }

    private void applyComponentEdits(ItemStack stack) {
        if (this.glintOverride < 0) stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        else stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, this.glintOverride == 1);
        stack.set(DataComponents.RARITY, this.rarity);

        TooltipDisplay originalTooltip = stack.get(DataComponents.TOOLTIP_DISPLAY);
        TooltipDisplay tooltip = originalTooltip == null ? TooltipDisplay.DEFAULT : originalTooltip;
        tooltip = new TooltipDisplay(this.hideTooltip, tooltip.hiddenComponents())
                .withHidden(DataComponents.ENCHANTMENTS, this.hideEnchantments);
        if (this.hideTooltip || this.hideEnchantments || originalTooltip != null) stack.set(DataComponents.TOOLTIP_DISPLAY, tooltip);
        else stack.remove(DataComponents.TOOLTIP_DISPLAY);

        CustomModelData model = customModelData();
        if (model == null) stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        else stack.set(DataComponents.CUSTOM_MODEL_DATA, model);

        if (this.canPlaceDirty) applyAdventurePredicate(stack, DataComponents.CAN_PLACE_ON, this.canPlaceBox);
        if (this.canBreakDirty) applyAdventurePredicate(stack, DataComponents.CAN_BREAK, this.canBreakBox);
    }

    private CustomModelData customModelData() {
        List<Float> floats = parseFloats(this.modelFloatsBox == null ? "" : this.modelFloatsBox.getValue());
        List<Boolean> flags = parseBooleans(this.modelFlagsBox == null ? "" : this.modelFlagsBox.getValue());
        List<String> strings = parseStrings(this.modelStringsBox == null ? "" : this.modelStringsBox.getValue());
        List<Integer> colors = parseIntegers(this.modelColorsBox == null ? "" : this.modelColorsBox.getValue());
        return floats.isEmpty() && flags.isEmpty() && strings.isEmpty() && colors.isEmpty()
                ? null : new CustomModelData(floats, flags, strings, colors);
    }

    private List<Float> parseFloats(String value) {
        List<Float> result = new ArrayList<>();
        for (String part : value.split(",")) {
            if (part.isBlank()) continue;
            try {
                float parsed = Float.parseFloat(part.trim());
                if (Float.isFinite(parsed)) result.add(parsed);
            } catch (NumberFormatException ignored) { }
        }
        return result;
    }

    private List<Boolean> parseBooleans(String value) {
        List<Boolean> result = new ArrayList<>();
        for (String part : value.split(",")) {
            if (part.equalsIgnoreCase("true")) result.add(true);
            else if (part.equalsIgnoreCase("false")) result.add(false);
        }
        return result;
    }

    private List<String> parseStrings(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim).filter(part -> !part.isBlank()).limit(64).toList();
    }

    private List<Integer> parseIntegers(String value) {
        List<Integer> result = new ArrayList<>();
        for (String part : value.split(",")) {
            if (part.isBlank()) continue;
            try {
                long parsed = Long.parseLong(part.trim());
                if (parsed >= 0 && parsed <= 0xFFFFFF) result.add((int) parsed);
            } catch (NumberFormatException ignored) { }
        }
        return result;
    }

    private void applyAdventurePredicate(ItemStack stack, net.minecraft.core.component.DataComponentType<AdventureModePredicate> type,
                                         EditBox box) {
        if (this.minecraft.level == null || box == null) return;
        List<BlockPredicate> predicates = new ArrayList<>();
        for (String part : box.getValue().split(",")) {
            String value = part.trim();
            if (value.isBlank()) continue;
            HolderSet<net.minecraft.world.level.block.Block> set = null;
            var blocks = this.minecraft.level.registryAccess().lookupOrThrow(Registries.BLOCK);
            if (value.startsWith("#")) {
                Identifier tagId = Identifier.tryParse(value.substring(1));
                if (tagId != null) set = blocks.get(TagKey.create(Registries.BLOCK, tagId)).orElse(null);
            } else {
                Identifier id = Identifier.tryParse(value);
                Holder<net.minecraft.world.level.block.Block> holder = id == null ? null : blocks.get(id).orElse(null);
                if (holder != null) set = HolderSet.direct(holder);
            }
            if (set != null) {
                predicates.add(new BlockPredicate(Optional.of(set), Optional.empty(), Optional.empty(), DataComponentMatchers.ANY));
            }
        }
        if (predicates.isEmpty()) stack.remove(type);
        else stack.set(type, new AdventureModePredicate(predicates));
    }

    private void applyTrim(ItemStack stack) {
        if (!this.trimCapable || this.minecraft.level == null) return;
        if (this.trimMaterialIndex == 0 || this.trimPatternIndex == 0) {
            stack.remove(DataComponents.TRIM);
            return;
        }
        Identifier materialId = Identifier.tryParse(trimMaterialId());
        Identifier patternId = Identifier.tryParse(trimPatternId());
        if (materialId == null || patternId == null) return;
        Holder<TrimMaterial> material = this.minecraft.level.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).get(materialId).orElse(null);
        Holder<TrimPattern> pattern = this.minecraft.level.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).get(patternId).orElse(null);
        if (material != null && pattern != null) stack.set(DataComponents.TRIM, new ArmorTrim(material, pattern));
    }

    private void applyAttributeEdits(ItemStack stack, Set<String> edited, Map<String, EditBox> boxes) {
        if (edited.isEmpty() || this.minecraft.level == null) return;
        ItemAttributeModifiers current = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        Set<String> changed = new HashSet<>();
        if (current != null) {
            for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
                String id = entry.attribute().unwrapKey().map(key -> key.identifier().toString()).orElse("");
                AttributeSpec spec = spec(id);
                if (spec != null && edited.contains(id) && !changed.contains(id)) {
                    Double amount = validAttributeValue(spec, boxes.get(id));
                    if (amount != null) {
                        builder.add(entry.attribute(), new AttributeModifier(entry.modifier().id(), amount,
                                this.attributeOperations.getOrDefault(id, entry.modifier().operation())),
                                this.attributeSlots.getOrDefault(id, entry.slot()), entry.display());
                        changed.add(id);
                        continue;
                    }
                }
                builder.add(entry.attribute(), entry.modifier(), entry.slot(), entry.display());
            }
        }
        for (String id : edited) {
            if (changed.contains(id)) continue;
            AttributeSpec spec = spec(id);
            Double amount = spec == null ? null : validAttributeValue(spec, boxes.get(id));
            if (amount == null) continue;
            Holder<Attribute> holder = this.minecraft.level.registryAccess().lookupOrThrow(Registries.ATTRIBUTE)
                    .get(Identifier.tryParse(id)).orElse(null);
            if (holder != null) {
                Identifier modifierId = Identifier.fromNamespaceAndPath(HeldItemTweaker.MOD_ID, "modifier." + holder.unwrapKey().map(key -> key.identifier().getPath()).orElse("attribute"));
                builder.add(holder, new AttributeModifier(modifierId, amount,
                        this.attributeOperations.getOrDefault(id, AttributeModifier.Operation.ADD_VALUE)),
                        this.attributeSlots.getOrDefault(id, spec.group()));
            }
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
    }

    private AttributeSpec spec(String id) {
        return this.attributeSpecs.stream().filter(spec -> spec.id().equals(id)).findFirst().orElse(null);
    }

    private Double validAttributeValue(AttributeSpec spec, EditBox box) {
        if (box == null) return null;
        try {
            double value = Double.parseDouble(box.getValue().trim());
            return Double.isFinite(value) && value >= spec.min() && value <= spec.max() ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void apply() {
        if (this.minecraft.player == null) return;
        List<String> enchantments = new ArrayList<>();
        for (Map.Entry<Holder<Enchantment>, Integer> entry : this.selected.entrySet()) {
            entry.getKey().unwrapKey().ifPresent(key -> enchantments.add(key.identifier() + "=" + entry.getValue()));
        }
        StringBuilder attributes = new StringBuilder();
        for (String id : this.dirtyAttributes) {
            AttributeSpec spec = spec(id);
            Double amount = spec == null ? null : validAttributeValue(spec, this.attributeBoxes.get(id));
            if (amount != null) {
                if (attributes.length() > 0) attributes.append(';');
                attributes.append(id).append('=').append(attributeSlotId(this.attributeSlots.getOrDefault(
                        id, spec.group()))).append('=').append(attributeOperationId(this.attributeOperations.getOrDefault(
                        id, AttributeModifier.Operation.ADD_VALUE))).append('=').append(amount);
            }
        }
        ClientPlayNetworking.send(new HeldItemTweaker.ApplyPayload(
                this.targetId, String.join(";", enchantments), this.nameBox.getValue().trim(), this.loreBox.getValue().trim(),
                parseInteger(this.damageBox.getValue()), this.unbreakable, trimMaterialId(), trimPatternId(),
                this.dyeColor, attributes.toString(), this.glintOverride, rarityPayload(),
                modelPayload(), tooltipPayload(), predicatePayload(this.canPlaceBox),
                predicatePayload(this.canBreakBox), bannerBaseColorPayload(), bannerPatternsPayload(),
                specialPayload(), this.infiniteUse));
        this.minecraft.player.displayClientMessage(Component.literal("Applied!"), true);
        this.onClose();
    }

    private String rarityPayload() {
        return this.rarity == null ? "" : this.rarity.getSerializedName();
    }

    private String modelPayload() {
        if (!this.modelDirty) return "";
        List<Float> floats = parseFloats(this.modelFloatsBox == null ? "" : this.modelFloatsBox.getValue());
        List<Boolean> flags = parseBooleans(this.modelFlagsBox == null ? "" : this.modelFlagsBox.getValue());
        List<String> strings = parseStrings(this.modelStringsBox == null ? "" : this.modelStringsBox.getValue());
        List<Integer> colors = parseIntegers(this.modelColorsBox == null ? "" : this.modelColorsBox.getValue());
        if (floats.isEmpty() && flags.isEmpty() && strings.isEmpty() && colors.isEmpty()) return "clear";
        return "data:floats=" + joinValues(floats)
                + "|flags=" + joinValues(flags)
                + "|strings=" + String.join(",", strings)
                + "|colors=" + joinValues(colors);
    }

    private String tooltipPayload() {
        List<String> hidden = new ArrayList<>();
        if (this.hideEnchantments) hidden.add("enchantments");
        return "hide=" + (this.hideTooltip ? "1" : "0") + "|hidden=" + String.join(",", hidden);
    }

    private String predicatePayload(EditBox box) {
        if (box == null) return "";
        if (box == this.canPlaceBox && !this.canPlaceDirty) return "";
        if (box == this.canBreakBox && !this.canBreakDirty) return "";
        if (box.getValue().trim().isBlank()) return "clear";
        return box.getValue().trim();
    }

    private String bannerBaseColorPayload() {
        return this.bannerCapable ? this.bannerBaseColor.getName() : "";
    }

    private String bannerPatternsPayload() {
        if (!this.bannerCapable) return "";
        if (this.bannerLayers.isEmpty()) return "clear";
        return this.bannerLayers.stream().map(layer -> layer.patternId() + "=" + layer.color().getName())
                .collect(Collectors.joining(";"));
    }

    private String specialPayload() {
        if ((!this.specialCapable || !this.specialDirty) && !this.advancedDirty && !this.gliderDirty) return "";
        List<String> sections = new ArrayList<>();
        if (this.potionCapable && this.potionBox != null) sections.add("potion=" + this.potionBox.getValue().trim());
        if (this.foodCapable && this.foodNutritionBox != null && this.foodSaturationBox != null) {
            sections.add("food=" + this.foodNutritionBox.getValue().trim() + "," + this.foodSaturationBox.getValue().trim()
                    + "," + (this.foodAlwaysEat ? "1" : "0"));
        }
        if (this.weaponCapable && this.weaponDamageBox != null && this.weaponDisableBox != null) {
            sections.add("weapon=" + this.weaponDamageBox.getValue().trim() + "," + this.weaponDisableBox.getValue().trim());
        }
        if (this.fireworksCapable && this.fireworkFlightBox != null) {
            sections.add("fireworks=" + this.fireworkFlightBox.getValue().trim());
            if (this.fireworkShapeBox != null) sections.add("explosion=" + this.fireworkShapeBox.getValue().trim() + "~"
                    + (this.fireworkColorsBox == null ? "" : this.fireworkColorsBox.getValue().trim()) + "~"
                    + (this.fireworkFadeColorsBox == null ? "" : this.fireworkFadeColorsBox.getValue().trim()) + "~"
                    + (this.fireworkTrail ? "1" : "0") + "~" + (this.fireworkTwinkle ? "1" : "0"));
        }
        if (this.containerCapable && this.containerItemsBox != null) sections.add("container=" + this.containerItemsBox.getValue().trim());
        if (this.bundleCapable && this.bundleItemsBox != null) sections.add("bundle=" + this.bundleItemsBox.getValue().trim());
        if (this.chargedProjectilesCapable && this.chargedProjectilesBox != null) sections.add("charged=" + this.chargedProjectilesBox.getValue().trim());
        if (this.mapColorCapable && this.mapColorBox != null) sections.add("map=" + this.mapColorBox.getValue().trim());
        if (this.advancedDirty || this.gliderDirty) {
            sections.add("max_stack=" + (this.maxStackBox == null ? "" : this.maxStackBox.getValue().trim()));
            sections.add("max_damage=" + (this.maxDamageBox == null ? "" : this.maxDamageBox.getValue().trim()));
            sections.add("repair_cost=" + (this.repairCostBox == null ? "" : this.repairCostBox.getValue().trim()));
            sections.add("potion_scale=" + (this.potionDurationScaleBox == null ? "" : this.potionDurationScaleBox.getValue().trim()));
            sections.add("potion_effects=" + encodePotionEffects());
            sections.add("firework_explosions=" + (this.fireworkExplosionsBox == null ? "" : this.fireworkExplosionsBox.getValue().trim()));
            sections.add("tool_defaults=" + (this.toolDefaultsBox == null ? "" : this.toolDefaultsBox.getValue().trim()));
            sections.add("attack_range=" + (this.attackRangeBox == null ? "" : this.attackRangeBox.getValue().trim()));
            sections.add("consumable=" + (this.consumableBox == null ? "" : this.consumableBox.getValue().trim()));
            sections.add("cooldown=" + (this.cooldownBox == null ? "" : this.cooldownBox.getValue().trim()));
            sections.add("glider=" + (this.glider ? "1" : "0"));
        }
        if (this.dataDirty) {
            sections.add("item_model=" + value(this.itemModelBox));
            sections.add("tooltip_style=" + value(this.tooltipStyleBox));
            sections.add("enchantable=" + value(this.enchantableBox));
            sections.add("repairable=" + value(this.repairableBox));
            sections.add("damage_resistant=" + value(this.damageResistantBox));
            sections.add("profile=" + value(this.profileBox));
            sections.add("note_sound=" + value(this.noteBlockSoundBox));
            sections.add("use_effects=" + value(this.useEffectsBox));
            sections.add("swing=" + value(this.swingAnimationBox));
            sections.add("piercing=" + value(this.piercingBox));
            sections.add("equippable=" + value(this.equippableBox));
        }
        return String.join(";", sections);
    }

    private String value(EditBox box) {
        return box == null ? "" : box.getValue().trim();
    }

    private void clearEnchants() {
        this.selected.clear();
        if (this.enchantmentList != null) this.enchantmentList.refresh();
    }

    private void cycleEnchantmentLevel(Holder<Enchantment> holder) {
        Enchantment enchantment = holder.value();
        int current = level(holder);
        if (current <= 0) {
            this.selected.put(holder, enchantment.getMinLevel());
        } else if (current < enchantment.getMaxLevel()) {
            this.selected.put(holder, current + 1);
        } else {
            this.selected.remove(holder);
        }
        if (this.enchantmentList != null) this.enchantmentList.refresh();
    }

    private void duplicateTarget() {
        if (targetStack().isEmpty()) return;
        ClientPlayNetworking.send(new HeldItemTweaker.DuplicatePayload(this.targetId));
        if (this.minecraft.player != null) this.minecraft.player.displayClientMessage(Component.literal("Duplicated!"), true);
    }

    private int parseInteger(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private Component fitText(Component value, int maxWidth) {
        String text = value.getString();
        if (maxWidth <= 8 || this.font.width(text) <= maxWidth) return value;
        String ellipsis = "...";
        return Component.literal(this.font.plainSubstrByWidth(text,
                Math.max(1, maxWidth - this.font.width(ellipsis))) + ellipsis);
    }

    private int level(Holder<Enchantment> holder) {
        return this.selected.getOrDefault(holder, 0);
    }

    private void remove(Holder<Enchantment> holder) {
        this.selected.remove(holder);
        this.enchantmentList.refresh();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }

    private final class ChoiceList extends ObjectSelectionList<ChoiceEntry> {
        private List<ChoiceValue> choices;
        private final IntConsumer onSelect;
        private int selectedIndex;
        private int anchorBaseY;
        private boolean opensAbove;

        private ChoiceList(int x, int y, int width, int height, List<ChoiceValue> choices,
                           int selectedIndex, IntConsumer onSelect) {
            super(HeldItemScreen.this.minecraft, width, height, y, 24);
            this.setX(x);
            this.anchorBaseY = y;
            this.choices = List.copyOf(choices);
            this.selectedIndex = Math.max(0, Math.min(selectedIndex, Math.max(0, choices.size() - 1)));
            this.onSelect = onSelect;
            this.visible = false;
            this.active = false;
            this.refresh();
        }

        private void refresh() {
            this.clearEntries();
            for (int index = 0; index < this.choices.size(); index++) {
                this.addEntry(new ChoiceEntry(this, index, this.choices.get(index)));
            }
        }

        private void setChoices(List<ChoiceValue> choices, int selectedIndex) {
            this.choices = List.copyOf(choices);
            this.selectedIndex = Math.max(0, Math.min(selectedIndex, Math.max(0, this.choices.size() - 1)));
            this.refresh();
        }

        private void setSelectedIndex(int index) {
            this.selectedIndex = Math.max(0, Math.min(index, Math.max(0, this.choices.size() - 1)));
        }

        private void setAnchorBaseY(int y) {
            this.anchorBaseY = y;
        }

        @Override
        public int getRowWidth() {
            return Math.max(0, this.getWidth() - 8);
        }

        @Override
        protected void renderListBackground(GuiGraphics graphics) {
            HeldItemScreen.this.renderListPanel(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight());
        }
    }

    private final class ChoiceEntry extends ObjectSelectionList.Entry<ChoiceEntry> {
        private final ChoiceList owner;
        private final int index;
        private final ChoiceValue choice;

        private ChoiceEntry(ChoiceList owner, int index, ChoiceValue choice) {
            this.owner = owner;
            this.index = index;
            this.choice = choice;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovering, float partialTick) {
            if (hovering || this.index == this.owner.selectedIndex) {
                int color = this.index == this.owner.selectedIndex
                        ? 0x77555555 : 0x55333333;
                graphics.fill(this.getContentX(), this.getContentY(),
                        this.getContentX() + this.owner.getRowWidth(),
                        this.getContentY() + 24, color);
            }
            int textX = renderChoicePreview(graphics, this.owner, this.choice,
                    this.getContentX(), this.getContentY());
            graphics.drawString(HeldItemScreen.this.font, this.choice.label(),
                    this.getContentX() + textX, this.getContentY() + 7, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
            if (event.button() != 0) return false;
            this.owner.selectedIndex = this.index;
            this.owner.onSelect.accept(this.index);
            HeldItemScreen.this.closeChoiceLists();
            return true;
        }

        @Override
        public Component getNarration() {
            return this.choice.label();
        }
    }

    private int renderChoicePreview(GuiGraphics graphics, ChoiceList owner, ChoiceValue choice, int x, int y) {
        if (owner == this.bannerBaseColorList || owner == this.bannerLayerColorList) {
            try {
                int id = Integer.parseInt(choice.value());
                int color = dyeColor(id).getTextureDiffuseColor() | 0xFF000000;
                graphics.fill(x + 4, y + 4, x + 24, y + 20, color);
                graphics.renderOutline(x + 3, y + 3, 22, 18, 0xFF222222);
                return 32;
            } catch (NumberFormatException ignored) {
                return 8;
            }
        }
        if (owner == this.bannerPatternList && !choice.value().isBlank()) {
            ensureBannerPreviewModel();
            if (this.bannerPreviewFlag != null && this.minecraft.level != null) {
                Identifier id = Identifier.tryParse(choice.value());
                if (id != null) {
                    Holder<BannerPattern> holder = this.minecraft.level.registryAccess()
                            .lookupOrThrow(Registries.BANNER_PATTERN).get(id).orElse(null);
                    if (holder != null) {
                        DyeColor color = this.selectedBannerLayer >= 0 && this.selectedBannerLayer < this.bannerLayers.size()
                                ? this.bannerLayers.get(this.selectedBannerLayer).color() : this.bannerLayerColorChoice;
                        BannerPatternLayers layers = new BannerPatternLayers(List.of(
                                new BannerPatternLayers.Layer(holder, color)));
                        graphics.submitBannerPatternRenderState(this.bannerPreviewFlag, this.bannerBaseColor,
                                layers, x + 2, y + 1, x + 26, y + 23);
                        return 34;
                    }
                }
            }
        }
        return 8;
    }

    private final class TargetList extends ObjectSelectionList<TargetEntry> {
        private TargetList(int x, int y, int width, int height) {
            super(HeldItemScreen.this.minecraft, width, height, y, 30);
            this.setX(x);
            for (TargetChoice choice : HeldItemScreen.this.targetChoices) this.addEntry(new TargetEntry(choice));
        }

        @Override
        public int getRowWidth() {
            return Math.max(0, this.getWidth() - 8);
        }

        @Override
        protected void renderListBackground(GuiGraphics graphics) {
            HeldItemScreen.this.renderListPanel(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight());
        }

    }

    private final class TargetEntry extends ObjectSelectionList.Entry<TargetEntry> {
        private final TargetChoice choice;

        private TargetEntry(TargetChoice choice) {
            this.choice = choice;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovering, float partialTick) {
            boolean selected = this.choice.id().equals(HeldItemScreen.this.targetId);
            if (hovering || selected) {
                graphics.fill(this.getContentX(), this.getContentY(),
                        this.getContentX() + HeldItemScreen.this.targetList.getRowWidth(),
                        this.getContentY() + 28, selected ? 0x77555555 : 0x55333333);
            }
            graphics.renderItem(this.choice.stack(), this.getContentX() + 2, this.getContentY() + 5);
            int maxTextWidth = Math.max(1, HeldItemScreen.this.targetList.getRowWidth() - 28);
            graphics.drawString(HeldItemScreen.this.font,
                    HeldItemScreen.this.fitText(Component.literal(this.choice.label()), maxTextWidth),
                    this.getContentX() + 24, this.getContentY() + 3, 0xFFFFFFFF);
            graphics.drawString(HeldItemScreen.this.font,
                    HeldItemScreen.this.fitText(this.choice.stack().getHoverName(), maxTextWidth),
                    this.getContentX() + 24, this.getContentY() + 15, 0xFFBDBDBD);
            if (hovering) {
                HeldItemScreen.this.hoveredListStack = this.choice.stack();
                HeldItemScreen.this.hoveredListHeader = Component.literal(this.choice.label());
            }
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
            if (event.button() != 0 || this.choice.id().equals(HeldItemScreen.this.targetId)) return true;
            HeldItemScreen.this.minecraft.setScreen(new HeldItemScreen(this.choice.id()));
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(this.choice.label() + ": " + this.choice.stack().getHoverName().getString());
        }
    }

    private final class BannerLayerList extends ObjectSelectionList<BannerLayerEntry> {
        private BannerLayerList(int x, int y, int width, int height) {
            super(HeldItemScreen.this.minecraft, width, height, y, 28);
            this.setX(x);
            refresh();
        }

        private void refresh() {
            this.clearEntries();
            for (int i = 0; i < HeldItemScreen.this.bannerLayers.size(); i++) {
                this.addEntry(new BannerLayerEntry(i));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.max(0, this.getWidth() - 8);
        }

        @Override
        protected void renderListBackground(GuiGraphics graphics) {
            HeldItemScreen.this.renderListPanel(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight());
        }
    }

    private final class BannerLayerEntry extends ObjectSelectionList.Entry<BannerLayerEntry> {
        private final int index;

        private BannerLayerEntry(int index) {
            this.index = index;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovering, float partialTick) {
            if (this.index >= HeldItemScreen.this.bannerLayers.size()) return;
            BannerLayerChoice layer = HeldItemScreen.this.bannerLayers.get(this.index);
            int color = this.index == HeldItemScreen.this.selectedBannerLayer ? 0x66336666 : 0x55222222;
            if (hovering || this.index == HeldItemScreen.this.selectedBannerLayer) {
                graphics.fill(this.getContentX(), this.getContentY(),
                        this.getContentX() + HeldItemScreen.this.bannerLayerList.getRowWidth(),
                        this.getContentY() + 24, color);
            }
            graphics.drawString(HeldItemScreen.this.font,
                    Component.literal("Layer " + (this.index + 1) + ": " + HeldItemScreen.this.bannerPatternName(layer.patternId())),
                    this.getContentX() + 8, this.getContentY() + 3, 0xFFFFFFFF);
            graphics.drawString(HeldItemScreen.this.font,
                    Component.literal("Color: " + layer.color().getName() + "  (right-click removes)"),
                    this.getContentX() + 8, this.getContentY() + 14, 0xFFBDBDBD);
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
            if (this.index >= HeldItemScreen.this.bannerLayers.size()) return false;
            if (event.button() == 1) {
                HeldItemScreen.this.bannerLayers.remove(this.index);
                HeldItemScreen.this.selectedBannerLayer = Math.min(HeldItemScreen.this.selectedBannerLayer,
                        HeldItemScreen.this.bannerLayers.size() - 1);
                HeldItemScreen.this.bannerLayerList.refresh();
                HeldItemScreen.this.refreshBannerButtons();
                return true;
            }
            if (event.button() == 0) {
                HeldItemScreen.this.selectedBannerLayer = this.index;
                BannerLayerChoice layer = HeldItemScreen.this.bannerLayers.get(this.index);
                HeldItemScreen.this.bannerPatternChoice = HeldItemScreen.this.indexOfBannerPattern(layer.patternId());
                HeldItemScreen.this.bannerLayerColorChoice = layer.color();
                HeldItemScreen.this.refreshBannerButtons();
                return true;
            }
            return false;
        }

        @Override
        public Component getNarration() {
            if (this.index >= HeldItemScreen.this.bannerLayers.size()) return Component.empty();
            BannerLayerChoice layer = HeldItemScreen.this.bannerLayers.get(this.index);
            return Component.literal("Layer " + (this.index + 1) + ": " + HeldItemScreen.this.bannerPatternName(layer.patternId()));
        }
    }

    private final class PlayerEffectList extends ObjectSelectionList<PlayerEffectEntry> {
        private PlayerEffectList(int x, int y, int width, int height) {
            super(HeldItemScreen.this.minecraft, width, height, y, 30);
            this.setX(x);
            refresh();
        }

        private void refresh() {
            this.clearEntries();
            for (int index = 0; index < HeldItemScreen.this.playerEffectEdits.size(); index++) {
                this.addEntry(new PlayerEffectEntry(index));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.max(0, this.getWidth() - 8);
        }

        @Override
        protected void renderListBackground(GuiGraphics graphics) {
            HeldItemScreen.this.renderListPanel(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight());
        }
    }

    private final class PlayerEffectEntry extends ObjectSelectionList.Entry<PlayerEffectEntry> {
        private final int index;

        private PlayerEffectEntry(int index) {
            this.index = index;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovering, float partialTick) {
            if (this.index >= HeldItemScreen.this.playerEffectEdits.size()) return;
            EffectEdit edit = HeldItemScreen.this.playerEffectEdits.get(this.index);
            boolean selected = this.index == HeldItemScreen.this.selectedPlayerEffect;
            if (hovering || selected) {
                graphics.fill(this.getContentX(), this.getContentY(),
                        this.getContentX() + HeldItemScreen.this.playerEffectList.getRowWidth(),
                        this.getContentY() + 26, selected ? 0x77555555 : 0x55333333);
            }
            MobEffectIconRenderer.render(graphics, edit.effect, this.getContentX() + 3, this.getContentY() + 4);
            Component name = edit.effect == null ? Component.literal("Unknown effect") : edit.effect.value().getDisplayName();
            graphics.drawString(HeldItemScreen.this.font, name, this.getContentX() + 26, this.getContentY() + 3,
                    edit.enabled ? 0xFFFFFFFF : 0xFF888888);
            String duration = edit.duration < 0 ? "Infinite" : Math.max(0, edit.duration / 20) + "s";
            graphics.drawString(HeldItemScreen.this.font,
                    Component.literal((edit.enabled ? "ON" : "OFF") + "  Level " + (edit.amplifier + 1) + "  " + duration),
                    this.getContentX() + 26, this.getContentY() + 15,
                    edit.enabled ? 0xFF55FF55 : 0xFFAAAAAA);
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
            if (event.button() != 0 || this.index >= HeldItemScreen.this.playerEffectEdits.size()) return false;
            HeldItemScreen.this.selectPlayerEffect(this.index);
            return true;
        }

        @Override
        public Component getNarration() {
            if (this.index >= HeldItemScreen.this.playerEffectEdits.size()) return Component.empty();
            return HeldItemScreen.this.playerEffectEdits.get(this.index).effect.value().getDisplayName();
        }
    }

    private final class PlayerEffectChoiceList extends ObjectSelectionList<PlayerEffectChoiceEntry> {
        private PlayerEffectChoiceList(int x, int y, int width, int height) {
            super(HeldItemScreen.this.minecraft, width, height, y, 24);
            this.setX(x);
            refresh();
        }

        private void refresh() {
            this.clearEntries();
            if (HeldItemScreen.this.minecraft.level == null) return;
            String filter = HeldItemScreen.this.playerEffectSearchBox == null
                    ? "" : HeldItemScreen.this.playerEffectSearchBox.getValue().trim().toLowerCase(java.util.Locale.ROOT);
            HeldItemScreen.this.minecraft.level.registryAccess().lookupOrThrow(Registries.MOB_EFFECT).listElements()
                    .sorted(Comparator.comparing(holder -> holder.value().getDisplayName().getString()))
                    .filter(holder -> filter.isBlank()
                            || holder.value().getDisplayName().getString().toLowerCase(java.util.Locale.ROOT).contains(filter)
                            || holder.unwrapKey().map(key -> key.identifier().toString().toLowerCase(java.util.Locale.ROOT)
                            .contains(filter)).orElse(false))
                    .forEach(holder -> this.addEntry(new PlayerEffectChoiceEntry(holder)));
        }

        @Override
        public int getRowWidth() {
            return Math.max(0, this.getWidth() - 8);
        }

        @Override
        protected void renderListBackground(GuiGraphics graphics) {
            HeldItemScreen.this.renderListPanel(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight());
        }
    }

    private final class PlayerEffectChoiceEntry extends ObjectSelectionList.Entry<PlayerEffectChoiceEntry> {
        private final Holder<MobEffect> effect;

        private PlayerEffectChoiceEntry(Holder<MobEffect> effect) {
            this.effect = effect;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovering, float partialTick) {
            if (hovering) {
                graphics.fill(this.getContentX(), this.getContentY(),
                        this.getContentX() + HeldItemScreen.this.playerEffectChoiceList.getRowWidth(),
                        this.getContentY() + 20, 0x55333333);
            }
            MobEffectIconRenderer.render(graphics, this.effect, this.getContentX() + 2, this.getContentY() + 2);
            graphics.drawString(HeldItemScreen.this.font, this.effect.value().getDisplayName(),
                    this.getContentX() + 26, this.getContentY() + 5, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
            if (event.button() != 0) return false;
            HeldItemScreen.this.addPlayerEffect(this.effect);
            return true;
        }

        @Override
        public Component getNarration() {
            return this.effect.value().getDisplayName();
        }
    }

    private final class EffectList extends ObjectSelectionList<EffectEntry> {
        private EffectList(int x, int y, int width, int height) {
            super(HeldItemScreen.this.minecraft, width, height, y, 34);
            this.setX(x);
            refresh();
        }

        private void refresh() {
            this.clearEntries();
            for (int i = 0; i < HeldItemScreen.this.potionEffectEdits.size(); i++) {
                this.addEntry(new EffectEntry(i));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.max(0, this.getWidth() - 8);
        }

        @Override
        protected void renderListBackground(GuiGraphics graphics) {
            HeldItemScreen.this.renderListPanel(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight());
        }
    }

    private final class EffectEntry extends ObjectSelectionList.Entry<EffectEntry> {
        private final int index;

        private EffectEntry(int index) {
            this.index = index;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovering, float partialTick) {
            if (this.index >= HeldItemScreen.this.potionEffectEdits.size()) return;
            EffectEdit edit = HeldItemScreen.this.potionEffectEdits.get(this.index);
            boolean selected = this.index == HeldItemScreen.this.selectedPotionEffect;
            if (hovering || selected) {
                graphics.fill(this.getContentX(), this.getContentY(),
                        this.getContentX() + HeldItemScreen.this.potionEffectList.getRowWidth(),
                        this.getContentY() + 30, selected ? 0x77555555 : 0x55333333);
            }
            Component name = edit.effect == null ? Component.literal("Unknown effect") : edit.effect.value().getDisplayName();
            int color = edit.enabled ? 0xFFFFFFFF : 0xFF888888;
            MobEffectIconRenderer.render(graphics, edit.effect, this.getContentX() + 5, this.getContentY() + 6);
            graphics.drawString(HeldItemScreen.this.font, name,
                    this.getContentX() + 30, this.getContentY() + 2, color);
            String duration = edit.duration < 0 ? "Infinite" : formatEffectDuration(edit.duration);
            String details = (edit.enabled ? "ON" : "OFF") + "  Level " + (edit.amplifier + 1)
                    + "  " + duration + "  Particles " + (edit.particles ? "ON" : "OFF")
                    + "  Icon " + (edit.icon ? "ON" : "OFF");
            graphics.drawString(HeldItemScreen.this.font, Component.literal(details),
                    this.getContentX() + 30, this.getContentY() + 16, edit.enabled ? 0xFF55FF55 : 0xFFAAAAAA);
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
            if (event.button() != 0 || this.index >= HeldItemScreen.this.potionEffectEdits.size()) return false;
            HeldItemScreen.this.selectPotionEffectRow(this.index);
            return true;
        }

        @Override
        public Component getNarration() {
            if (this.index >= HeldItemScreen.this.potionEffectEdits.size()) return Component.empty();
            EffectEdit edit = HeldItemScreen.this.potionEffectEdits.get(this.index);
            return edit.effect == null ? Component.literal("Unknown effect") : edit.effect.value().getDisplayName();
        }
    }

    private String formatEffectDuration(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        if (seconds >= 60) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }

    private final class EnchantmentList extends ObjectSelectionList<EnchantmentEntry> {
        private EnchantmentList(int x, int y, int width, int height) {
            super(HeldItemScreen.this.minecraft, width, height, y, 28);
            this.setX(x);
            refresh();
        }

        private void refresh() {
            this.clearEntries();
            if (HeldItemScreen.this.minecraft.level == null || HeldItemScreen.this.minecraft.player == null) return;
            ItemStack held = HeldItemScreen.this.targetStack();
            ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(held);
            HeldItemScreen.this.minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements()
                    .filter(holder -> holder.value().canEnchant(held) || current.keySet().contains(holder))
                    .sorted(Comparator.comparing(holder -> holder.value().description().getString()))
                    .forEach(holder -> this.addEntry(new EnchantmentEntry(holder)));
        }

        @Override
        public int getRowWidth() {
            return Math.max(0, this.getWidth() - 8);
        }

        @Override
        protected void renderListBackground(GuiGraphics graphics) {
            HeldItemScreen.this.renderListPanel(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight());
        }

        private int entryHeight() {
            return 28;
        }
    }

    private final class EnchantmentEntry extends ObjectSelectionList.Entry<EnchantmentEntry> {
        private final Holder<Enchantment> holder;

        private EnchantmentEntry(Holder<Enchantment> holder) {
            this.holder = holder;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovering, float partialTick) {
            int level = HeldItemScreen.this.level(this.holder);
            int color = level > 0 ? 0xFFFFFF55 : 0xFFFFFFFF;
            if (hovering) graphics.fill(this.getContentX(), this.getContentY(),
                    this.getContentX() + HeldItemScreen.this.enchantmentList.getRowWidth(), this.getContentY() + 24, 0x55333333);
            graphics.drawString(HeldItemScreen.this.font, this.holder.value().description(),
                    this.getContentX() + 8, this.getContentY() + 3, color);
            String text = level > 0 ? "Selected: " + level
                    : "Level " + this.holder.value().getMinLevel() + "-" + this.holder.value().getMaxLevel();
            graphics.drawString(HeldItemScreen.this.font, Component.literal(text),
                    this.getContentX() + 8, this.getContentY() + 14, level > 0 ? 0xFF55FF55 : 0xFFBDBDBD);
            if (hovering) {
                EnchantmentDescriptionsCompat.description(this.holder).ifPresent(description ->
                        graphics.setTooltipForNextFrame(HeldItemScreen.this.font,
                                CommonComponents.joinLines(
                                        this.holder.value().description().copy().withStyle(ChatFormatting.GOLD),
                                        CommonComponents.EMPTY,
                                        description.copy().withStyle(ChatFormatting.WHITE)),
                                mouseX, mouseY));
            }
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
            if (event.button() == 0) {
                cycleEnchantmentLevel(this.holder);
                return true;
            }
            if (event.button() == 1) {
                remove(this.holder);
                return true;
            }
            return false;
        }

        @Override
        public Component getNarration() {
            return this.holder.value().description();
        }
    }
}
