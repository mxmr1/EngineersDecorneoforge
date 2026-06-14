package wile.engineersdecor;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.common.conditions.ICondition;
import com.mojang.serialization.MapCodec;
import wile.engineersdecor.libmc.OptionalRecipeCondition;

import java.util.function.Supplier;

public class ModConditions {
    // 使用来自 NeoForgeRegistries.Keys 的键
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, "engineersdecor");

    public static final Supplier<MapCodec<OptionalRecipeCondition>> OPTIONAL =
            CONDITIONS.register("optional", () -> OptionalRecipeCondition.CODEC);

    public static void register(IEventBus bus) {
        CONDITIONS.register(bus);
    }
}