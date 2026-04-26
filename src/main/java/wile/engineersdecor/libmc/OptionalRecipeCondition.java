/*
 * @file OptionalRecipeCondition.java
 * @author Stefan Wilhelm
 * @license MIT
 *
 * Updated for NeoForge 21.1.209 (Codec-based conditions)
 */
package wile.engineersdecor.libmc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.Optional;

public class OptionalRecipeCondition implements ICondition {
    private static ResourceLocation NAME;

    private final List<ResourceLocation> all_required;
    private final List<ResourceLocation> any_missing;
    private final List<ResourceLocation> all_required_tags;
    private final List<ResourceLocation> any_missing_tags;
    private final @Nullable ResourceLocation result;
    private final boolean result_is_tag;
    private final boolean experimental;

    private static boolean with_experimental = false;
    private static boolean without_recipes = false;
    private static Predicate<Object> block_optouts = b -> false;
    private static Predicate<Object> item_optouts = i -> false;

    // ---------- NeoForge Codec Registration ----------
    public static final MapCodec<OptionalRecipeCondition> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.optionalFieldOf("result").forGetter(c ->
                            c.result == null
                                    ? Optional.empty()
                                    : Optional.of((c.result_is_tag ? "#" : "") + c.result.toString())
                    ),
                    Codec.STRING.listOf().optionalFieldOf("required", List.of())
                            .forGetter(c -> c.all_required.stream().map(ResourceLocation::toString).collect(Collectors.toList())),
                    Codec.STRING.listOf().optionalFieldOf("missing", List.of())
                            .forGetter(c -> c.any_missing.stream().map(ResourceLocation::toString).collect(Collectors.toList())),
                    Codec.STRING.listOf().optionalFieldOf("required_tags", List.of())
                            .forGetter(c -> c.all_required_tags.stream().map(ResourceLocation::toString).collect(Collectors.toList())),
                    Codec.STRING.listOf().optionalFieldOf("missing_tags", List.of())
                            .forGetter(c -> c.any_missing_tags.stream().map(ResourceLocation::toString).collect(Collectors.toList())),
                    Codec.BOOL.optionalFieldOf("experimental", false).forGetter(c -> c.experimental)
            ).apply(instance, (resultStrOpt, req, miss, reqTags, missTags, exp) -> {
                ResourceLocation res = null;
                boolean tag = false;
                if (resultStrOpt.isPresent()) {
                    String s = resultStrOpt.get();
                    if (s.startsWith("#")) {
                        tag = true;
                        s = s.substring(1);
                    }
                    res = ResourceLocation.parse(s);
                }
                List<ResourceLocation> reqRL = req.stream().map(ResourceLocation::parse).collect(Collectors.toList());
                List<ResourceLocation> missRL = miss.stream().map(ResourceLocation::parse).collect(Collectors.toList());
                List<ResourceLocation> reqTagRL = reqTags.stream().map(ResourceLocation::parse).collect(Collectors.toList());
                List<ResourceLocation> missTagRL = missTags.stream().map(ResourceLocation::parse).collect(Collectors.toList());
                return new OptionalRecipeCondition(res, reqRL, missRL, reqTagRL, missTagRL, exp, tag);
            })
    );

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    // ---------- Existing Logic ----------
    public static void init(String modid, Logger logger) {
        NAME = ResourceLocation.fromNamespaceAndPath(modid, "optional");
    }

    public static void on_config(boolean enable_experimental, boolean disable_all_recipes,
                                 Predicate<Object> block_optout_provider,
                                 Predicate<Object> item_optout_provider) {
        with_experimental = enable_experimental;
        without_recipes = disable_all_recipes;
        block_optouts = block_optout_provider;
        item_optouts = item_optout_provider;
    }

    public OptionalRecipeCondition(ResourceLocation result, List<ResourceLocation> required,
                                   List<ResourceLocation> missing,
                                   List<ResourceLocation> required_tags,
                                   List<ResourceLocation> missing_tags,
                                   boolean isexperimental, boolean result_is_tag) {
        all_required = required;
        any_missing = missing;
        all_required_tags = required_tags;
        any_missing_tags = missing_tags;
        this.result = result;
        this.result_is_tag = result_is_tag;
        experimental = isexperimental;
    }

    @Override
    public boolean test(IContext context) {
        if (without_recipes) return false;
        if (experimental && !with_experimental) return false;

        if (result != null) {
            if (!BuiltInRegistries.ITEM.containsKey(result) && !BuiltInRegistries.BLOCK.containsKey(result))
                return false;
        }

        for (ResourceLocation rl : all_required)
            if (!BuiltInRegistries.ITEM.containsKey(rl)) return false;

        for (ResourceLocation rl : all_required_tags)
            if (BuiltInRegistries.ITEM.getTagNames().noneMatch(tk -> tk.location().equals(rl))) return false;

        for (ResourceLocation rl : any_missing)
            if (!BuiltInRegistries.ITEM.containsKey(rl)) return true;

        for (ResourceLocation rl : any_missing_tags)
            if (BuiltInRegistries.ITEM.getTagNames().noneMatch(tk -> tk.location().equals(rl))) return true;

        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Optional recipe: ");
        sb.append("required=").append(all_required).append(", missing=").append(any_missing);
        if (experimental) sb.append(" EXPERIMENTAL");
        return sb.toString();
    }
}
