package wile.engineersdecor;

import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import wile.engineersdecor.libmc.EDRegistries;

// Импорты блоков
import wile.engineersdecor.blocks.EdHopper;

@EventBusSubscriber(modid = ModEngineersDecor.MODID, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class EdScreens {

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {


        /*event.register(
                (MenuType<EdHopper.HopperContainer>) EDRegistries.getMenuTypeOfBlock("factory_hopper"),
                EdHopper.HopperGui::new
        );*/

    }
}
