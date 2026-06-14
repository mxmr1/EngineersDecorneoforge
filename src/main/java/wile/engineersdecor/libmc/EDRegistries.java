/*
 * @file EDRegistries.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (https://opensource.org/licenses/MIT)
 *
 * 通用游戏注册表处理（迁移至 NeoForge 1.21.1 API）
 */
package wile.engineersdecor.libmc;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.util.List;

import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;

import wile.engineersdecor.ModEngineersDecor;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 将原始 EDRegistries.java 轻量迁移至 NeoForge 1.21.x
 * 在切换 RegistryObject -> DeferredHolder 的同时保留原始 mod 逻辑，
 * 并替换旧的 FMLJavaModLoadingContext 用法。
 */
public class EDRegistries
{
    private static String modid = null;
    private static String creative_tab_icon = "";
    private static CreativeModeTab creative_tab = null;
    private static final Map<String, TagKey<Block>> registered_block_tag_keys = new HashMap<>();
    private static final Map<String, TagKey<Item>> registered_item_tag_keys = new HashMap<>();

    /*
     * 注册表映射现在存储 DeferredHolder 实例（替代 RegistryObject）。
     * 第二个泛型保留为通配符以接受子类（例如 SlabBlock 继承自 Block）。
     */
    private static final Map<String, DeferredHolder<Block, ? extends Block>> registered_blocks = new HashMap<>();
    private static final Map<String, DeferredHolder<Item, ? extends Item>> registered_items = new HashMap<>();
    private static final Map<String, DeferredHolder<BlockEntityType<?>, ? extends BlockEntityType<?>>> registered_block_entity_types = new HashMap<>();
    private static final Map<String, DeferredHolder<EntityType<?>, ? extends EntityType<?>>> registered_entity_types = new HashMap<>();
    private static final Map<String, DeferredHolder<MenuType<?>, ? extends MenuType<?>>> registered_menu_types = new HashMap<>();
    private static final Map<String, DeferredHolder<RecipeSerializer<?>, ? extends RecipeSerializer<?>>> recipe_serializers = new HashMap<>();

    private static DeferredRegister<Block> BLOCKS;
    private static DeferredRegister<Item> ITEMS;
    private static DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES;
    private static DeferredRegister<MenuType<?>> MENUS;
    private static DeferredRegister<EntityType<?>> ENTITIES;
    private static DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS;
    private static List<DeferredRegister<?>> MOD_REGISTRIES;

    /*
     * 创造模式标签页仍然是一个注册项；保留原有的创建方式，但将其注册到 mod 事件总线。
     */
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TAB =
            DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, ModEngineersDecor.MODID);

    /**
     * 初始化注册表辅助类。调用方需提供一个注册器消费者，
     * 通常会调用 `r -> r.register(modEventBus)` 来注册每个 DeferredRegister。
     */
    public static void init(String mod_id, String creative_tab_icon_item_name, Consumer<DeferredRegister<?>> registrar)
    {
        modid = mod_id;
        creative_tab_icon = creative_tab_icon_item_name;

        // 为每个使用的原版注册表创建 DeferredRegister
        BLOCKS = DeferredRegister.create(Registries.BLOCK, modid);
        ITEMS = DeferredRegister.create(Registries.ITEM, modid);
        BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, modid);
        MENUS = DeferredRegister.create(Registries.MENU, modid);
        ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, modid);
        RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, modid);

        // 立即将创造模式标签页注册到 mod 事件总线
        // （ModLoadingContext 是 NeoForge 中用于获取上下文帮助的入口）
        CREATIVE_TAB.register(ModLoadingContext.get().getActiveContainer().getEventBus());

        // 允许调用方注册其余部分（保持原始控制流不变）
        List.of(BLOCKS, ITEMS, BLOCK_ENTITIES, MENUS, ENTITIES, RECIPE_SERIALIZERS).forEach(registrar);
    }

    /*
     * 创造模式标签页的持有者 — 保留与原版相同的名称以保持行为一致。
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ENGINEERS_DECOR_TAB = CREATIVE_TAB.register(ModEngineersDecor.MODID,
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(registered_items.get(creative_tab_icon).get()))
                    .title(Component.literal(ModEngineersDecor.MODNAME))
                    .build());

    public static void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        if(event.getTab() == EDRegistries.ENGINEERS_DECOR_TAB.get())
        {
            List<Item> items = EDRegistries.getRegisteredItems();
            items.forEach(item -> event.accept(new ItemStack(item)));
        }
    }

    public static CreativeModeTab getCreativeModeTab()
    {
        return ENGINEERS_DECOR_TAB.get();
    }

    // -------------------------------------------------------------------------------------------------------------

    public static Block getBlock(String block_name)
    { return registered_blocks.get(block_name).get(); }

    public static void addBlockNoItem(String name, Supplier<Block> supplier) {
        // 注册方块并将 DeferredHolder 存入 registered_blocks，
        // 但不为它创建 BlockItem 存入 registered_items
        registered_blocks.put(name, BLOCKS.register(name, supplier));
    }

    public static Item getItem(String name)
    { return registered_items.get(name).get(); }

    public static EntityType<?> getEntityType(String name)
    { return registered_entity_types.get(name).get(); }

    public static BlockEntityType<?> getBlockEntityType(String block_name)
    { return registered_block_entity_types.get(block_name).get(); }

    public static MenuType<?> getMenuType(String name)
    { return registered_menu_types.get(name).get(); }

    public static RecipeSerializer<?> getRecipeSerializer(String name)
    { return recipe_serializers.get(name).get(); }

    public static BlockEntityType<?> getBlockEntityTypeOfBlock(String block_name)
    { return getBlockEntityType("tet_"+block_name); }

    public static BlockEntityType<?> getBlockEntityTypeOfBlock(Block block)
    {
        // 为了兼容性，继续使用 NeoForgeRegistries 获取注册键/路径
        return getBlockEntityTypeOfBlock(BuiltInRegistries.BLOCK.getKey(block).getPath());
    }

    public static MenuType<?> getMenuTypeOfBlock(String name)
    { return getMenuType("ct_"+name); }

    public static MenuType<?> getMenuTypeOfBlock(Block block)
    { return getMenuTypeOfBlock(BuiltInRegistries.BLOCK.getKey(block).getPath()); }

    public static TagKey<Block> getBlockTagKey(String name)
    { return registered_block_tag_keys.get(name); }

    public static TagKey<Item> getItemTagKey(String name)
    { return registered_item_tag_keys.get(name); }

    // -------------------------------------------------------------------------------------------------------------

    @Nonnull
    public static List<Block> getRegisteredBlocks()
    { return Collections.unmodifiableList(registered_blocks.values().stream().map(DeferredHolder::get).collect(Collectors.toList())); }

    @Nonnull
    public static List<Item> getRegisteredItems()
    { return Collections.unmodifiableList(registered_items.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> e.getValue().get())
            .collect(Collectors.toList())); }

    @Nonnull
    public static List<BlockEntityType<?>> getRegisteredBlockEntityTypes()
    { return Collections.unmodifiableList(registered_block_entity_types.values().stream().map(DeferredHolder::get).collect(Collectors.toList())); }

    @Nonnull
    public static List<EntityType<?>> getRegisteredEntityTypes()
    { return Collections.unmodifiableList(registered_entity_types.values().stream().map(DeferredHolder::get).collect(Collectors.toList())); }

    // -------------------------------------------------------------------------------------------------------------

    public static <T extends Item> void addItem(String registry_name, Supplier<T> supplier)
    { registered_items.put(registry_name, ITEMS.register(registry_name, supplier)); }

    public static <T extends Block> void addBlock(String registry_name, Supplier<T> block_supplier)
    {
        registered_blocks.put(registry_name, BLOCKS.register(registry_name, block_supplier));
        registered_items.put(registry_name, ITEMS.register(registry_name, ()->new BlockItem(registered_blocks.get(registry_name).get(), (new Item.Properties()))));
    }

    public static <TB extends Block, TI extends Item> void addBlock(String registry_name, Supplier<TB> block_supplier, Supplier<TI> item_supplier)
    {
        registered_blocks.put(registry_name, BLOCKS.register(registry_name, block_supplier));
        registered_items.put(registry_name, ITEMS.register(registry_name, item_supplier));
    }

    public static <T extends BlockEntity> void addBlockEntityType(String registry_name, BlockEntityType.BlockEntitySupplier<T> ctor, String... block_names)
    {
        registered_block_entity_types.put(registry_name, BLOCK_ENTITIES.register(registry_name, ()->{
            final Block[] blocks = Arrays.stream(block_names).map(s->{
                final DeferredHolder<Block, ? extends Block> holder = registered_blocks.get(s);
                final Block b = holder == null ? null : holder.get();
                if(b==null) Auxiliaries.logError("registered_blocks 中不包含 '" + s + "'");
                return b;
            }).filter(Objects::nonNull).toArray(Block[]::new);
            return BlockEntityType.Builder.of(ctor, blocks).build(null);
        }));
    }

    public static <T extends EntityType<?>> void addEntityType(String registry_name, Supplier<EntityType<?>> supplier)
    { registered_entity_types.put(registry_name, ENTITIES.register(registry_name, supplier)); }

    public static <T extends MenuType<?>> void addMenuType(String registry_name, MenuType.MenuSupplier<?> supplier)
    { registered_menu_types.put(registry_name, MENUS.register(registry_name, ()->new MenuType<>(supplier, FeatureFlags.DEFAULT_FLAGS))); }

    public static void addRecipeSerializer(String registry_name, Supplier<? extends RecipeSerializer<?>> serializer_supplier)
    { recipe_serializers.put(registry_name, RECIPE_SERIALIZERS.register(registry_name, serializer_supplier)); }

    public static void addOptionalBlockTag(String tag_name, ResourceLocation... default_blocks)
    {
        final TagKey<Block> key = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(modid, tag_name));
        registered_block_tag_keys.put(tag_name, key);
    }

    public static void addOptionalItemTag(String tag_name, ResourceLocation... default_items)
    {
        final TagKey<Item> key = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(modid, tag_name));
        registered_item_tag_keys.put(tag_name, key);
    }

    // -------------------------------------------------------------------------------------------------------------

    public static <TB extends Block, TI extends Item> void addBlock(String registry_name, Supplier<TB> block_supplier, BiFunction<Block, Item.Properties, Item> item_builder)
    {
        addBlock(registry_name, block_supplier, ()->item_builder.apply(registered_blocks.get(registry_name).get(), (new Item.Properties())));
    }

    public static void addBlock(String registry_name, Supplier<? extends Block> block_supplier, BlockEntityType.BlockEntitySupplier<?> block_entity_ctor)
    {
        addBlock(registry_name, block_supplier);
        addBlockEntityType("tet_"+registry_name, block_entity_ctor, registry_name);
    }

    public static void addBlock(String registry_name, Supplier<? extends Block> block_supplier, BiFunction<Block, Item.Properties, Item> item_builder, BlockEntityType.BlockEntitySupplier<?> block_entity_ctor)
    {
        addBlock(registry_name, block_supplier, item_builder);
        addBlockEntityType("tet_"+registry_name, block_entity_ctor, registry_name);
    }

    public static void addBlock(String registry_name, Supplier<? extends Block> block_supplier, BiFunction<Block, Item.Properties, Item> item_builder, BlockEntityType.BlockEntitySupplier<?> block_entity_ctor, MenuType.MenuSupplier<?> menu_type_supplier)
    {
        addBlock(registry_name, block_supplier, item_builder);
        addBlockEntityType("tet_"+registry_name, block_entity_ctor, registry_name);
        addMenuType("ct_"+registry_name, menu_type_supplier);
    }

    public static void addBlock(String registry_name, Supplier<? extends Block> block_supplier, BlockEntityType.BlockEntitySupplier<?> block_entity_ctor, MenuType.MenuSupplier<?> menu_type_supplier)
    {
        addBlock(registry_name, block_supplier, block_entity_ctor);
        addMenuType("ct_"+registry_name, menu_type_supplier);
    }
}