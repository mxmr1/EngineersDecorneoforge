/*
 * @file Crafting.java
 * @author Stefan Wilhelm (wile)
 * @license MIT (https://opensource.org/licenses/MIT)
 *
 * Ported and fixed for NeoForge 1.21.1 (21.1.209) by ChatGPT (2025)
 */

package wile.engineersdecor.libmc;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Crafting {

    // ------------------------------------------------------------------------------------------------------------
    // Crafting grid helper
    // ------------------------------------------------------------------------------------------------------------

    public static final class CraftingGrid implements CraftingContainer {
        protected static final CraftingGrid instance3x3 = new CraftingGrid(3, 3);
        private final int width;
        private final int height;
        private final NonNullList<ItemStack> items;

        protected CraftingGrid(int width, int height) {
            this.width = width;
            this.height = height;
            this.items = NonNullList.withSize(width * height, ItemStack.EMPTY);
        }

        protected void fill(Container grid) {
            for (int i = 0; i < this.items.size(); ++i) this.items.set(i, ItemStack.EMPTY);
            for (int i = 0; i < Math.min(grid.getContainerSize(), this.items.size()); ++i) {
                ItemStack s = grid.getItem(i);
                this.items.set(i, s == null ? ItemStack.EMPTY : s.copy());
            }
        }

        protected CraftingInput toCraftingInput() {
            return CraftingInput.of(this.width, this.height, this.items);
        }

        public List<CraftingRecipe> getRecipes(Level world, Container grid) {
            fill(grid);
            CraftingInput input = toCraftingInput();
            return world.getRecipeManager().getRecipesFor(RecipeType.CRAFTING, input, world)
                    .stream()
                    .map(holder -> holder.value())
                    .filter(r -> r instanceof CraftingRecipe)
                    .map(r -> (CraftingRecipe) r)
                    .collect(Collectors.toList());
        }

        public List<ItemStack> getRemainingItems(Level world, Container grid, CraftingRecipe recipe) {
            fill(grid);
            CraftingInput input = toCraftingInput();
            NonNullList<ItemStack> ret = recipe.getRemainingItems(input);
            return new ArrayList<>(ret);
        }

        public ItemStack getCraftingResult(Level world, Container grid, CraftingRecipe recipe) {
            fill(grid);
            CraftingInput input = toCraftingInput();
            try {
                return recipe.assemble(input, RegistryAccess.EMPTY);
            } catch (Throwable t) {
                return ItemStack.EMPTY;
            }
        }

        // CraftingContainer implementation -------------------------------------------------------------

        @Override public int getWidth() { return this.width; }
        @Override public int getHeight() { return this.height; }
        @Override public List<ItemStack> getItems() { return this.items; }
        @Override public int getContainerSize() { return this.items.size(); }
        @Override public boolean isEmpty() { return this.items.stream().allMatch(ItemStack::isEmpty); }
        @Override public ItemStack getItem(int i) { return (i < 0 || i >= this.items.size()) ? ItemStack.EMPTY : this.items.get(i); }
        @Override public ItemStack removeItem(int i, int count) {
            if (i < 0 || i >= this.items.size()) return ItemStack.EMPTY;
            ItemStack slot = this.items.get(i);
            if (slot.isEmpty()) return ItemStack.EMPTY;
            if (slot.getCount() <= count) {
                ItemStack copy = slot.copy();
                this.items.set(i, ItemStack.EMPTY);
                setChanged();
                return copy;
            } else {
                ItemStack result = slot.split(count);
                if (slot.getCount() == 0) this.items.set(i, ItemStack.EMPTY);
                setChanged();
                return result;
            }
        }
        @Override public ItemStack removeItemNoUpdate(int i) {
            if (i < 0 || i >= this.items.size()) return ItemStack.EMPTY;
            ItemStack res = this.items.get(i);
            this.items.set(i, ItemStack.EMPTY);
            return res;
        }
        @Override public void setItem(int i, ItemStack itemStack) {
            if (i >= 0 && i < this.items.size()) {
                this.items.set(i, itemStack);
                setChanged();
            }
        }
        @Override public void setChanged() {}
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() { for (int i = 0; i < this.items.size(); ++i) this.items.set(i, ItemStack.EMPTY); }
        @Override public void fillStackedContents(StackedContents stackedContents) {
            for (ItemStack is : this.items) if (!is.isEmpty()) stackedContents.accountSimpleStack(is);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Recipe lookups
    // ------------------------------------------------------------------------------------------------------------

    public static Optional<CraftingRecipe> getCraftingRecipe(Level world, ResourceLocation recipe_id) {
        return world.getRecipeManager().byKey(recipe_id)
                .map(holder -> holder.value())
                .filter(r -> r instanceof CraftingRecipe)
                .map(r -> (CraftingRecipe) r);
    }

    public static List<CraftingRecipe> get3x3CraftingRecipes(Level world, Container crafting_grid_slots) {
        return CraftingGrid.instance3x3.getRecipes(world, crafting_grid_slots);
    }

    public static Optional<CraftingRecipe> get3x3CraftingRecipe(Level world, Container crafting_grid_slots) {
        return get3x3CraftingRecipes(world, crafting_grid_slots).stream().findFirst();
    }

    public static ItemStack get3x3CraftingResult(Level world, Container grid, CraftingRecipe recipe) {
        return CraftingGrid.instance3x3.getCraftingResult(world, grid, recipe);
    }

    public static List<ItemStack> get3x3RemainingItems(Level world, Container grid, CraftingRecipe recipe) {
        return CraftingGrid.instance3x3.getRemainingItems(world, grid, recipe);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Furnace recipes / fuel
    // ------------------------------------------------------------------------------------------------------------

    public static <T extends Recipe<?>> Optional<AbstractCookingRecipe> getFurnaceRecipe(RecipeType<T> recipe_type, Level world, ItemStack input_stack) {
        if (input_stack.isEmpty()) return Optional.empty();
        SingleRecipeInput input = new SingleRecipeInput(input_stack);

        if (recipe_type == RecipeType.SMELTING) {
            return world.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, world)
                    .map(r -> (AbstractCookingRecipe) r.value());
        } else if (recipe_type == RecipeType.BLASTING) {
            return world.getRecipeManager().getRecipeFor(RecipeType.BLASTING, input, world)
                    .map(r -> (AbstractCookingRecipe) r.value());
        } else if (recipe_type == RecipeType.SMOKING) {
            return world.getRecipeManager().getRecipeFor(RecipeType.SMOKING, input, world)
                    .map(r -> (AbstractCookingRecipe) r.value());
        }
        return Optional.empty();
    }

    public static int getSmeltingTimeNeeded(RecipeType<?> recipe_type, Level world, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return getFurnaceRecipe(recipe_type, world, stack)
                .map(AbstractCookingRecipe::getCookingTime)
                .orElse(200);
    }

    public static int getFuelBurntime(Level world, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        int t = stack.getBurnTime(RecipeType.SMELTING);
        return Math.max(t, 0);
    }

    public static boolean isFuel(Level world, ItemStack stack) {
        return (getFuelBurntime(world, stack) > 0) || (stack.getItem() == Items.LAVA_BUCKET);
    }

    public static Tuple<Integer, ItemStack> consumeFuel(Level world, ItemStack stack) {
        if (stack.isEmpty()) return new Tuple<>(0, stack);
        int burnime = getFuelBurntime(world, stack);
        if (stack.getItem() == Items.LAVA_BUCKET) {
            if (burnime <= 0) burnime = 1000 * 20;
            return new Tuple<>(burnime, new ItemStack(Items.BUCKET));
        } else if (burnime <= 0) {
            return new Tuple<>(0, stack);
        } else {
            ItemStack left_over = stack.copy();
            left_over.shrink(1);
            return new Tuple<>(burnime, left_over);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Brewing (fixed for 1.21.1)
    // ------------------------------------------------------------------------------------------------------------

    private static PotionBrewing getBrewing(Level world) {
        return PotionBrewing.bootstrap(world.enabledFeatures(), world.registryAccess());
    }

    public static boolean isBrewingFuel(Level world, ItemStack stack) {
        return (stack.getItem() == Items.BLAZE_POWDER) || (stack.getItem() == Items.BLAZE_ROD);
    }

    public static boolean isBrewingIngredient(Level world, ItemStack stack) {
        return getBrewing(world).isIngredient(stack);
    }

    public static boolean isBrewingInput(Level world, ItemStack stack) {
        return getBrewing(world).isInput(stack);
    }

    public static int getBrewingFuelBurntime(Level world, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.getItem() == Items.BLAZE_POWDER) return 400 * 20;
        if (stack.getItem() == Items.BLAZE_ROD) return 400 * 40;
        return 0;
    }

    public static Tuple<Integer, ItemStack> consumeBrewingFuel(Level world, ItemStack stack) {
        int burntime = getBrewingFuelBurntime(world, stack);
        if (burntime <= 0) return new Tuple<>(0, stack.copy());
        stack = stack.copy();
        stack.shrink(1);
        return new Tuple<>(burntime, stack.isEmpty() ? ItemStack.EMPTY : stack);
    }

    public static final class BrewingOutput {
        public static final int DEFAULT_BREWING_TIME = 400;
        public static final BrewingOutput EMPTY = new BrewingOutput(ItemStack.EMPTY, new SimpleContainer(1), new SimpleContainer(1), 0, 0, DEFAULT_BREWING_TIME);

        public final ItemStack item;
        public final Container potionInventory;
        public final Container ingredientInventory;
        public final int potionSlot;
        public final int ingredientSlot;
        public final int brewTime;

        public BrewingOutput(ItemStack output_potion, Container potion_inventory, Container ingredient_inventory, int potion_slot, int ingredient_slot, int time_needed) {
            item = output_potion;
            potionInventory = potion_inventory;
            ingredientInventory = ingredient_inventory;
            potionSlot = potion_slot;
            ingredientSlot = ingredient_slot;
            brewTime = time_needed;
        }

        public static BrewingOutput find(Level world, Container potion_inventory, Container ingredient_inventory) {
            PotionBrewing brewing = getBrewing(world);
            for (int potion_slot = 0; potion_slot < potion_inventory.getContainerSize(); ++potion_slot) {
                final ItemStack pstack = potion_inventory.getItem(potion_slot);
                if (!brewing.isInput(pstack)) continue;
                for (int ingredient_slot = 0; ingredient_slot < ingredient_inventory.getContainerSize(); ++ingredient_slot) {
                    final ItemStack istack = ingredient_inventory.getItem(ingredient_slot);
                    if (!brewing.isIngredient(istack) || ingredient_slot == potion_slot || isBrewingFuel(world, istack))
                        continue;
                    final ItemStack result = brewing.mix(istack, pstack);
                    if (result.isEmpty()) continue;
                    return new BrewingOutput(result, potion_inventory, ingredient_inventory, potion_slot, ingredient_slot, DEFAULT_BREWING_TIME);
                }
            }
            return BrewingOutput.EMPTY;
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Misc (composting, enchantments)
    // ------------------------------------------------------------------------------------------------------------

    public static double getCompostingChance(ItemStack stack) {
        return ComposterBlock.COMPOSTABLES.getOrDefault(stack.getItem(), 0f);
    }

    public static Map<Enchantment, Integer> getEnchantmentsOnItem(Level world, ItemStack stack) {
        if (stack.isEmpty() || !stack.has(DataComponents.ENCHANTMENTS)) return Collections.emptyMap();
        ItemEnchantments ench = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        Map<Enchantment, Integer> result = new HashMap<>();
        ench.entrySet().forEach(e -> result.put(e.getKey().value(), e.getIntValue()));
        return result;
    }

    public static ItemStack getEnchantmentBook(Level world, Enchantment enchantment, int level) {
        if (level <= 0) return ItemStack.EMPTY;
        Holder<Enchantment> h = world.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .wrapAsHolder(enchantment);
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(h, level));
    }

    public static int getEnchantmentRepairCost(Level world, Map<Enchantment, Integer> enchantments) {
        int repair_cost = 0;
        for (Map.Entry<Enchantment, Integer> e : enchantments.entrySet())
            repair_cost = repair_cost * 2 + 1;
        return repair_cost;
    }

    public static boolean addEnchantmentOnItem(Level world, ItemStack stack, Enchantment enchantment, int level) {
        if (stack.isEmpty() || level <= 0 || !stack.isEnchantable() || level > enchantment.getMaxLevel())
            return false;

        final Map<Enchantment, Integer> on_item = getEnchantmentsOnItem(world, stack);

        if (on_item.keySet().stream().anyMatch(e -> {
            Holder<Enchantment> he = world.registryAccess().registryOrThrow(Registries.ENCHANTMENT).wrapAsHolder(e);
            Holder<Enchantment> hnew = world.registryAccess().registryOrThrow(Registries.ENCHANTMENT).wrapAsHolder(enchantment);
            return !Enchantment.areCompatible(he, hnew);
        })) return false;

        if (!enchantment.canEnchant(stack))
            return false;

        int existing_level = on_item.getOrDefault(enchantment, 0);
        if (existing_level > 0)
            level = Mth.clamp(level + existing_level, 1, enchantment.getMaxLevel());

        on_item.put(enchantment, level);

        applyEnchantments(world, stack, on_item);
        stack.set(DataComponents.REPAIR_COST, getEnchantmentRepairCost(world, on_item));
        return true;
    }

    public static Map<Enchantment, Integer> removeEnchantmentsOnItem(Level world, ItemStack stack, BiPredicate<Enchantment, Integer> filter) {
        if (stack.isEmpty()) return Collections.emptyMap();
        final Map<Enchantment, Integer> on_item = getEnchantmentsOnItem(world, stack);
        final Map<Enchantment, Integer> removed = new HashMap<>();
        for (Map.Entry<Enchantment, Integer> e : on_item.entrySet()) {
            if (filter.test(e.getKey(), e.getValue()))
                removed.put(e.getKey(), e.getValue());
        }
        removed.keySet().forEach(on_item::remove);
        applyEnchantments(world, stack, on_item);
        stack.set(DataComponents.REPAIR_COST, getEnchantmentRepairCost(world, on_item));
        return removed;
    }

    private static void applyEnchantments(Level world, ItemStack stack, Map<Enchantment, Integer> map) {
        stack.update(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY, enchants -> {
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchants);
            var registry = world.registryAccess().registryOrThrow(Registries.ENCHANTMENT);

            for (Map.Entry<Enchantment, Integer> e : map.entrySet()) {
                Holder<Enchantment> holder = registry.wrapAsHolder(e.getKey());
                mutable.set(holder, e.getValue());
            }

            return mutable.toImmutable();
        });
    }
}
