package wile.engineersdecor.libmc;

// 导入
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import wile.engineersdecor.blocks.EdFreezer;
import wile.engineersdecor.blocks.EdHopper;
import wile.engineersdecor.blocks.EdPlacer;

// 在事件注册中使用
//@EventBusSubscriber(modid = "engineersdecor")
public class EdCapabilities {
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // 直接从方块注册表获取工厂漏斗的方块实例
        Block factoryHopperBlock = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("engineersdecor", "factory_hopper")
        );

        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,                     // 要注册的能力
                (level, pos, state, be, side) -> {                 // 提供者 lambda
                    if (be instanceof EdHopper.HopperTileEntity hopper) {
                        return hopper.item_handler_;               // 返回内部的 IItemHandler
                    }
                    return null;
                },
                factoryHopperBlock                                 // 传入方块实例
        );

        // --- 工厂放置器的能力注册 ---
        Block factoryPlacerBlock = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("engineersdecor", "factory_placer")
        );
        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                (level, pos, state, be, side) -> {
                    if (be instanceof EdPlacer.PlacerTileEntity placer) {
                        return placer.item_handler_;
                    }
                    return null;
                },
                factoryPlacerBlock
        );

        Block freezerBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("engineersdecor", "small_freezer"));
        // 物品能力
        event.registerBlock(Capabilities.ItemHandler.BLOCK,
                (level, pos, state, be, side) -> be instanceof EdFreezer.FreezerTileEntity freezer ? freezer.item_handler_ : null,
                freezerBlock);
        // 流体能力
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, be, side) -> be instanceof EdFreezer.FreezerTileEntity freezer ? freezer.fluid_handler_ : null,
                freezerBlock);
        // 能量能力
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, be, side) -> be instanceof EdFreezer.FreezerTileEntity freezer ? freezer : null,
                freezerBlock);

    }
}
