package wile.engineersdecor.eapi.jei;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import wile.engineersdecor.ModConfig;
import wile.engineersdecor.ModContent;
import wile.engineersdecor.ModEngineersDecor;
import wile.engineersdecor.libmc.Auxiliaries;
import wile.engineersdecor.libmc.EDRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@mezz.jei.api.JeiPlugin
public class JEIPlugin implements mezz.jei.api.IModPlugin {


    @Override
    public @NotNull ResourceLocation getPluginUid() {
        // Новый способ создания ResourceLocation в 1.21+
        return ResourceLocation.fromNamespaceAndPath(ModEngineersDecor.MODID, "jei_plugin_uid");
    }

    @Override
    public void registerRecipeTransferHandlers(@NotNull IRecipeTransferRegistration registration) {
        // Здесь можно регистрировать transfer-handlers (если нужно)
    }

    @Override
    public void onRuntimeAvailable(@NotNull IJeiRuntime jeiRuntime) {
        HashSet<Item> blacklisted = new HashSet<>();

        // Обрабатываем блоки, отключённые в конфиге
        for (Block block : EDRegistries.getRegisteredBlocks()) {
            if (ModConfig.isOptedOut(block)) {
                Item item = block.asItem();
                ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(item);
                ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(block);

                if (itemKey.getPath().equals(blockKey.getPath())) {
                    blacklisted.add(item);
                }
            }
        }

        // Предметы, отключённые в конфиге, не являющиеся BlockItem
        for (Item item : EDRegistries.getRegisteredItems()) {
            if (ModConfig.isOptedOut(item) && !(item instanceof BlockItem)) {
                blacklisted.add(item);
            }
        }

        // Удаляем отключённые предметы из JEI
        if (!blacklisted.isEmpty()) {
            List<ItemStack> blacklistStacks = blacklisted.stream()
                    .map(ItemStack::new)
                    .collect(Collectors.toList());
            try {
                jeiRuntime.getIngredientManager()
                        .removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, blacklistStacks);
            } catch (Exception e) {
                Auxiliaries.logger().warn(
                        "Exception in JEI opt-out processing: '" + e.getMessage()
                                + "', skipping further JEI opt-out processing."
                );
            }
        }
    }
}
