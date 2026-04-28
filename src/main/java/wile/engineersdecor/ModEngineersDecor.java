package wile.engineersdecor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import com.mojang.serialization.MapCodec;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import wile.engineersdecor.blocks.EdLadderBlock;
import wile.engineersdecor.libmc.Auxiliaries;
import wile.engineersdecor.libmc.OptionalRecipeCondition;
import wile.engineersdecor.libmc.Overlay;
import wile.engineersdecor.libmc.EDRegistries;

import java.util.function.Supplier;

@Mod(ModEngineersDecor.MODID)
public class ModEngineersDecor {
    public static final String MODID = "engineersdecor";
    public static final String MODNAME = "Engineer's Decor";
    private static final Logger LOGGER = LogUtils.getLogger();

    // Регистрация условий рецептов
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, MODID);
    private static final Supplier<MapCodec<? extends ICondition>> OPTIONAL_RECIPE_CONDITION =
            CONDITION_CODECS.register("optional", () -> OptionalRecipeCondition.CODEC);

    public ModEngineersDecor(IEventBus modEventBus, ModContainer container) {
        Auxiliaries.init(MODID, LOGGER, wile.engineersdecor.ModConfig::getServerConfig);
        Auxiliaries.logGitVersion(MODNAME);

        // Регистрация контента
        EDRegistries.init(MODID, "sign_decor", (reg) -> reg.register(modEventBus));
        ModContent.init(MODID);
        OptionalRecipeCondition.init(MODID, LOGGER);

        // Конфиги
        container.registerConfig(ModConfig.Type.SERVER, wile.engineersdecor.ModConfig.SERVER_CONFIG_SPEC);
        container.registerConfig(ModConfig.Type.COMMON, wile.engineersdecor.ModConfig.COMMON_CONFIG_SPEC);

        // Регистрация кодеков условий
        CONDITION_CODECS.register(modEventBus);
        ModConditions.register(modEventBus);

        // Слушатели инициализации
        modEventBus.addListener(this::onSetup);
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(EDRegistries::addCreative);

        // ✅ Правильная регистрация слушателей конфигов
        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigReload);

        // ✅ Регистрируем только игровые события на общей шине
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(ClientEvents.class);
    }

    private void onSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FlowerPotBlock pot = (FlowerPotBlock) Blocks.FLOWER_POT;
            pot.addPlant(
                    ResourceLocation.fromNamespaceAndPath(MODID, "cycad"),
                    () -> EDRegistries.getBlock("potted_cycad")
            );
        });
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        Overlay.TextOverlayGui.on_config(0.75, 0x00ffaa00, 0x55333333, 0x55333333, 0x55444444);
        wile.engineersdecor.libmc.Networking.OverlayTextMessage.setHandler(Overlay.TextOverlayGui::show);
        ModContent.registerMenuGuis(event);
        ModContent.registerBlockEntityRenderers(event);
        ModContent.processContentClientSide(event);
    }

    // ✅ Эти методы теперь слушаются через modEventBus (не через @SubscribeEvent)
    private void onConfigLoad(final ModConfigEvent.Loading event) {
        wile.engineersdecor.ModConfig.apply();
    }

    private void onConfigReload(final ModConfigEvent.Reloading event) {
        try {
            Auxiliaries.logger().info("Config file changed {}", event.getConfig().getFileName());
            wile.engineersdecor.ModConfig.apply();
        } catch (Throwable e) {
            Auxiliaries.logger().error("Failed reload config: " + e.getMessage());
        }
    }

    // Игровые события
    @SubscribeEvent
    public void onPlayerEvent(final PlayerTickEvent.Post event) {  // Или .Pre, если нужно начало тика
        Player player = event.getEntity();  // Получаем игрока напрямую
        if (player.onClimbable()) {
            EdLadderBlock.onPlayerUpdateEvent(player);
        }
    }

    // Клиентские события
    public static class ClientEvents {
        @SubscribeEvent
        public static void onRenderGui(RenderGuiLayerEvent.Post event) {
            try {
                GuiGraphics gui = event.getGuiGraphics();
                Overlay.TextOverlayGui.INSTANCE.onRenderGui(gui);
            } catch (Throwable t) {
                Auxiliaries.logger().warn("RenderGui error: {}", t.toString());
            }
        }

        @SubscribeEvent
        public static void onRenderWorldOverlay(RenderLevelStageEvent event) {
            try {
                if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
                    Overlay.TextOverlayGui.INSTANCE.onRenderWorldOverlay(
                            event.getPoseStack(),
                            event.getPartialTick().getGameTimeDeltaTicks()
                    );
                }
            } catch (Throwable t) {
                Auxiliaries.logger().warn("RenderLevelStage error: {}", t.toString());
            }
        }
    }
}
