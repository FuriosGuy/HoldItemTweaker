package dev.codex.helditemtweaker;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.core.component.DataComponents;

import java.util.List;

/** Shared client/server relationship rules for the safe item-family changer. */
final class ItemVariantRules {
    private static final List<TagKey<Item>> FAMILY_TAGS = List.of(
            tag("minecraft:swords"), tag("minecraft:pickaxes"), tag("minecraft:axes"),
            tag("minecraft:shovels"), tag("minecraft:hoes"), tag("minecraft:enchantable/armor"),
            tag("minecraft:enchantable/weapon"), tag("minecraft:enchantable/mining"),
            tag("minecraft:enchantable/mining_loot"), tag("minecraft:planks"),
            tag("minecraft:logs"), tag("minecraft:logs_that_burn"), tag("minecraft:wooden_slabs"),
            tag("minecraft:wooden_stairs"), tag("minecraft:wooden_fences"), tag("minecraft:wooden_doors"),
            tag("minecraft:wooden_trapdoors"), tag("minecraft:ores"), tag("minecraft:stone_ores"),
            tag("minecraft:coal_ores"), tag("minecraft:iron_ores"), tag("minecraft:copper_ores"),
            tag("minecraft:gold_ores"), tag("minecraft:redstone_ores"), tag("minecraft:lapis_ores"),
            tag("minecraft:diamond_ores"), tag("minecraft:emerald_ores"), tag("minecraft:raw_materials"),
            tag("minecraft:food"), tag("minecraft:meat"), tag("minecraft:fishes"),
            tag("minecraft:shulker_boxes"), tag("minecraft:bundles"),
            // Farmer's Delight / common tags. These remain harmless when the mod is absent.
            tag("c:tools/knife"), tag("c:foods"));

    private ItemVariantRules() {}

    static boolean isRelated(ItemStack source, ItemStack candidate) {
        if (source.isEmpty() || candidate.isEmpty()) return false;
        if (ItemStack.isSameItem(source, candidate)) return true;
        Item sourceItem = source.getItem();
        Item candidateItem = candidate.getItem();
        if (!(sourceItem instanceof BlockItem) && !(candidateItem instanceof BlockItem)
                && sourceItem.getClass() == candidateItem.getClass()
                && sourceItem.getClass() != Item.class) return true;
        boolean sourceFood = source.has(DataComponents.FOOD);
        boolean candidateFood = candidate.has(DataComponents.FOOD);
        if (sourceFood && candidateFood) return true;
        for (TagKey<Item> tag : FAMILY_TAGS) {
            if (source.is(tag) && candidate.is(tag)) return true;
        }
        return false;
    }

    static List<TagKey<Item>> familyTags() {
        return FAMILY_TAGS;
    }

    private static TagKey<Item> tag(String id) {
        return TagKey.create(Registries.ITEM, Identifier.parse(id));
    }
}
