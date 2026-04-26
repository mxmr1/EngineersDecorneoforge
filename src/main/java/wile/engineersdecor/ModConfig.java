/*
 * @file ModConfig.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Main class for module settings. Handles reading and
 * saving the config file.
 */
package wile.engineersdecor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;
import wile.engineersdecor.blocks.*;
import wile.engineersdecor.detail.TreeCutting;
import wile.engineersdecor.libmc.SlabSliceBlock;
import wile.engineersdecor.libmc.VariantSlabBlock;
import wile.engineersdecor.libmc.Auxiliaries;
import wile.engineersdecor.libmc.OptionalRecipeCondition;

import javax.annotation.Nullable;
import java.util.*;


public class ModConfig
{
  //--------------------------------------------------------------------------------------------------------------------
  private static final String MODID = ModEngineersDecor.MODID;
  public static final CommonConfig COMMON;
  public static final ServerConfig SERVER;
  public static final ModConfigSpec COMMON_CONFIG_SPEC;
  public static final ModConfigSpec SERVER_CONFIG_SPEC;

  static {
    final Pair<CommonConfig, ModConfigSpec> common_ = (new ModConfigSpec.Builder()).configure(CommonConfig::new);
    COMMON_CONFIG_SPEC = common_.getRight();
    COMMON = common_.getLeft();
    final Pair<ServerConfig, ModConfigSpec> server_ = (new ModConfigSpec.Builder()).configure(ServerConfig::new);
    SERVER_CONFIG_SPEC = server_.getRight();
    SERVER = server_.getLeft();
  }

  //--------------------------------------------------------------------------------------------------------------------

  public static class CommonConfig
  {
    // Optout
    public final ModConfigSpec.ConfigValue<String> pattern_excludes;
    public final ModConfigSpec.ConfigValue<String> pattern_includes;
    // MISC
    public final ModConfigSpec.BooleanValue with_creative_mode_device_drops;
    public final ModConfigSpec.BooleanValue with_experimental;
    public final ModConfigSpec.BooleanValue with_config_logging;
    public final ModConfigSpec.BooleanValue with_debug_logging;

    CommonConfig(ModConfigSpec.Builder builder)
    {
      builder.comment("Settings affecting the logical server side.")
        .push("server");
      // --- OPTOUTS ------------------------------------------------------------
      {
        builder.comment("Opt-out settings")
          .push("optout");
        pattern_excludes = builder
          .translation(MODID + ".config.pattern_excludes")
          .comment("Opt-out any block by its registry name ('*' wildcard matching, "
            + "comma separated list, whitespaces ignored. You must match the whole name, "
            + "means maybe add '*' also at the begin and end. Example: '*wood*,*steel*' "
            + "excludes everything that has 'wood' or 'steel' in the registry name. "
            + "The matching result is also traced in the log file. ")
          .define("pattern_excludes", "");
        pattern_includes = builder
          .translation(MODID + ".config.pattern_includes")
          .comment("Prevent blocks from being opt'ed by registry name ('*' wildcard matching, "
            + "comma separated list, whitespaces ignored. Evaluated before all other opt-out checks. "
            + "You must match the whole name, means maybe add '*' also at the begin and end. Example: "
            + "'*wood*,*steel*' includes everything that has 'wood' or 'steel' in the registry name."
            + "The matching result is also traced in the log file.")
          .define("pattern_includes", "");
        builder.pop();
      }
      // --- MISC ---------------------------------------------------------------
      {
        builder.comment("Miscellaneous settings")
          .push("miscellaneous");
        with_experimental = builder
          .translation(MODID + ".config.with_experimental")
          .comment("Enables experimental features. Use at own risk.")
          .define("with_experimental", false);
        with_creative_mode_device_drops = builder
          .translation(MODID + ".config.with_creative_mode_device_drops")
          .comment("Enable that devices are dropped as item also in creative mode, allowing " +
            " to relocate them with contents and settings.")
          .define("with_creative_mode_device_drops", false);
        with_config_logging = builder
          .translation(MODID + ".config.with_debug_logging")
          .comment("Enable detailed logging of the config values and resulting calculations in each mod feature config.")
          .define("with_debug_logging", false);
        with_debug_logging = builder
          .translation(MODID + ".config.with_debug_logging")
          .comment("Enable debug log messages for trouble shooting. Don't activate if not really needed, this can spam the log file.")
          .define("with_debug_logging", false);
        builder.pop();
      }
    }
  }

  //--------------------------------------------------------------------------------------------------------------------

  public static class ServerConfig
  {
    // Optout
    public final ModConfigSpec.BooleanValue without_chair_sitting;
    public final ModConfigSpec.BooleanValue without_mob_chair_sitting;
    public final ModConfigSpec.BooleanValue without_ladder_speed_boost;
    public final ModConfigSpec.BooleanValue without_crafting_table_history;
    // Misc
    public final ModConfigSpec.BooleanValue without_direct_slab_pickup;
    // Tweaks
    public final ModConfigSpec.IntValue furnace_smelting_speed_percent;
    public final ModConfigSpec.IntValue furnace_fuel_efficiency_percent;
    public final ModConfigSpec.IntValue furnace_boost_energy_consumption;
    public final ModConfigSpec.ConfigValue<String> furnace_accepted_heaters;
    public final ModConfigSpec.DoubleValue chair_mob_sitting_probability_percent;
    public final ModConfigSpec.DoubleValue chair_mob_standup_probability_percent;
    public final ModConfigSpec.BooleanValue without_crafting_mouse_scrolling;
    public final ModConfigSpec.IntValue pipevalve_max_flowrate;
    public final ModConfigSpec.IntValue pipevalve_redstone_gain;

    ServerConfig(ModConfigSpec.Builder builder)
    {
      builder.comment("Settings affecting the logical server side.")
        .push("server");
      // --- OPTOUTS ------------------------------------------------------------
      {
        builder.comment("Server dev opt-out settings !WARNING THE OPT-OUTs will be moved to common-config.toml in the next MC version!")
          .push("optout");
        without_chair_sitting = builder
          .translation(MODID + ".config.without_chair_sitting")
          .comment("Disable possibility to sit on stools and chairs.")
          .define("without_chair_sitting", false);
        without_mob_chair_sitting = builder
          .translation(MODID + ".config.without_mob_chair_sitting")
          .comment("Disable that mobs will sit on chairs and stools.")
          .define("without_mob_chair_sitting", false);
        without_ladder_speed_boost = builder
          .translation(MODID + ".config.without_ladder_speed_boost")
          .comment("Disable the speed boost of ladders in this mod.")
          .define("without_ladder_speed_boost", false);
        without_crafting_table_history = builder
          .translation(MODID + ".config.without_crafting_table_history")
          .comment("Disable history refabrication feature of the crafting table.")
          .define("without_crafting_table_history", false);
        builder.pop();
      }
      // --- MISC ---------------------------------------------------------------
      {
        builder.comment("Miscellaneous settings")
          .push("miscellaneous");
        without_direct_slab_pickup = builder
          .translation(MODID + ".config.without_direct_slab_pickup")
          .comment("Disable directly picking up layers from slabs and slab " +
            " slices by left clicking while looking up/down.")
          .define("without_direct_slab_pickup", false);
        builder.pop();
      }
      // --- TWEAKS -------------------------------------------------------------
      {
        builder.comment("Tweaks")
          .push("tweaks");
        furnace_smelting_speed_percent = builder
          .translation(MODID + ".config.furnace_smelting_speed_percent")
          .comment("Defines, in percent, how fast the lab furnace smelts compared to " +
            "a vanilla furnace. 100% means vanilla furnace speed, 150% means the " +
            "lab furnace is faster. The value can be changed on-the-fly for tuning.")
          .defineInRange("furnace_smelting_speed_percent", 130, 50, 800);
        furnace_fuel_efficiency_percent = builder
          .translation(MODID + ".config.furnace_fuel_efficiency_percent")
          .comment("Defines, in percent, how fuel efficient the lab furnace is, compared " +
            "to a vanilla furnace. 100% means vanilla furnace consumiton, 200% means " +
            "the lab furnace needs about half the fuel of a vanilla furnace, " +
            "The value can be changed on-the-fly for tuning.")
          .defineInRange("furnace_fuel_efficiency_percent", 100, 50, 400);
        furnace_boost_energy_consumption = builder
          .translation(MODID + ".config.furnace_boost_energy_consumption")
          .comment("Defines the energy consumption (per tick) for speeding up the smelting process. " +
            "If IE is installed, an external heater has to be inserted into an auxiliary slot " +
            "of the lab furnace. The power source needs to be able to provide at least 4 times " +
            "this consumption (fixed threshold value). The value can be changed on-the-fly for tuning. " +
            "The default value corresponds to the IE heater consumption.")
          .defineInRange("furnace_boost_energy_consumption", 24, 2, 1024);
        furnace_accepted_heaters = builder
          .translation(MODID + ".config.furnace_accepted_heaters")
          .comment("Defines (as comma separated list of full registry names) which items are allowed as external " +
            "heaters in the Aux slot for powered speed boosting.")
          .define("furnace_accepted_heaters", "immersiveengineering:furnace_heater");
        chair_mob_sitting_probability_percent = builder
          .translation(MODID + ".config.chair_mob_sitting_probability_percent")
          .comment("Defines, in percent, how high the probability is that a mob sits on a chair " +
            "when colliding with it. Can be changed on-the-fly for tuning.")
          .defineInRange("chair_mob_sitting_probability_percent", 10.0, 0.0, 80.0);
        chair_mob_standup_probability_percent = builder
          .translation(MODID + ".config.chair_mob_standup_probability_percent")
          .comment("Defines, in percent, probable it is that a mob leaves a chair when sitting " +
            "on it. The 'dice is rolled' about every 20 ticks. There is also a minimum " +
            "Sitting time of about 3s. The config value can be changed on-the-fly for tuning.")
          .defineInRange("chair_mob_standup_probability_percent", 1.0, 1e-3, 10.0);
        without_crafting_mouse_scrolling = builder
          .translation(MODID + ".config.without_crafting_mouse_scrolling")
          .comment("Disables increasing/decreasing the crafting grid items by scrolling over the crafting result slot.")
          .define("without_crafting_mouse_scrolling", false);
        pipevalve_max_flowrate = builder
          .translation(MODID + ".config.pipevalve_max_flowrate")
          .comment("Defines how many millibuckets can be transferred (per tick) through the valves. " +
            "That is technically the 'storage size' specified for blocks that want to fill " +
            "fluids into the valve (the valve has no container and forward that to the output " +
            "block), The value can be changed on-the-fly for tuning. ")
          .defineInRange("pipevalve_max_flowrate", 1000, 1, 32000);
        pipevalve_redstone_gain = builder
          .translation(MODID + ".config.pipevalve_redstone_gain")
          .comment("Defines how many millibuckets per redstone signal strength can be transferred per tick " +
            "through the analog redstone controlled valves. Note: power 0 is always off, power 15 is always " +
            "the max flow rate. Between power 1 and 14 this scaler will result in a flow = 'redstone slope' * 'current redstone power'. " +
            "The value can be changed on-the-fly for tuning. ")
          .defineInRange("pipevalve_redstone_gain", 20, 1, 32000);

      }
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Optout checks
  //--------------------------------------------------------------------------------------------------------------------

  public static boolean isOptedOut(final @Nullable Block block)
  { return (block==null) || isOptedOut(block.asItem()); }

  public static boolean isOptedOut(final @Nullable Item item)
  { return (item!=null) && optouts_.contains(Auxiliaries.getResourceLocation(item).getPath()); }

  public static boolean withExperimental()
  { return with_experimental_features_; }

  public static boolean withoutRecipes()
  { return false; }

  public static boolean withDebug()
  { return with_debug_logs_; }

  public static boolean withDebugLogging()
  { return with_experimental_features_ && with_config_logging_; }

  //--------------------------------------------------------------------------------------------------------------------
  // Cache
  //--------------------------------------------------------------------------------------------------------------------

  private static final CompoundTag server_config_ = new CompoundTag();
  private static HashSet<String> optouts_ = new HashSet<>();
  private static boolean with_experimental_features_ = false;
  private static boolean with_config_logging_ = false;
  private static boolean with_debug_logs_ = false;
  public static boolean immersiveengineering_installed = false;
  public static boolean without_direct_slab_pickup = false;
  public static boolean with_creative_mode_device_drops = false;

  public static CompoundTag getServerConfig() // config that may be synchronized from server to client via net pkg.
  { return server_config_; }

  private static void updateOptouts()
  {
    final ArrayList<String> includes = new ArrayList<>();
    final ArrayList<String> excludes = new ArrayList<>();
    {
      String inc = COMMON.pattern_includes.get().toLowerCase().replaceAll(MODID+":", "").replaceAll("[^*_,a-z\\d]", "");
      if(!COMMON.pattern_includes.get().equals(inc)) COMMON.pattern_includes.set(inc);
      String[] incl = inc.split(",");
      for(int i=0; i< incl.length; ++i) {
        incl[i] = incl[i].replaceAll("[*]", ".*?");
        if(!incl[i].isEmpty()) includes.add(incl[i]);
      }
    }
    {
      String exc = COMMON.pattern_excludes.get().toLowerCase().replaceAll(MODID+":", "").replaceAll("[^*_,a-z\\d]", "");
      String[] excl = exc.split(",");
      for(int i=0; i< excl.length; ++i) {
        excl[i] = excl[i].replaceAll("[*]", ".*?");
        if(!excl[i].isEmpty()) excludes.add(excl[i]);
      }
    }
    if(!excludes.isEmpty()) log("Config pattern excludes: '" + String.join(",", excludes) + "'");
    if(!includes.isEmpty()) log("Config pattern includes: '" + String.join(",", includes) + "'");
    try {
      HashSet<String> optouts = new HashSet<>();
      ModContent.getRegisteredBlocks().stream().filter((Block block) -> {
        if(block==null) return true;
        if(block==ModContent.getBlock("sign_decor")) return true;
        try {
          if(!with_experimental_features_) {
            if(block instanceof Auxiliaries.IExperimentalFeature) return true;
          }
          // Force-include/exclude pattern matching
          final String rn = Auxiliaries.getResourceLocation(block).getPath();
          try {
            for(String e : includes) {
              if(rn.matches(e)) {
                log("Optout force include: "+rn);
                return false;
              }
            }
            for(String e : excludes) {
              if(rn.matches(e)) {
                log("Optout force exclude: "+rn);
                return true;
              }
            }
          } catch(Throwable ex) {
            Auxiliaries.logger().error("optout include pattern failed, disabling.");
            includes.clear();
            excludes.clear();
          }
        } catch(Exception ex) {
          Auxiliaries.logger().error("Exception evaluating the optout config: '"+ex.getMessage()+"'");
        }
        return false;
      }).forEach(
        e -> optouts.add(Auxiliaries.getResourceLocation(e).getPath())
      );
      optouts_ = optouts;
        OptionalRecipeCondition.on_config(
                withExperimental(),
                withoutRecipes(),
                (obj) -> obj instanceof Block && ModConfig.isOptedOut((Block) obj),
                (obj) -> obj instanceof Item && ModConfig.isOptedOut((Item) obj)
        );
    } catch(Throwable ex) {
      Auxiliaries.logger().error("Exception evaluating the optout config: '"+ex.getMessage()+"'"); // Compat issue: config-apply may be called before the registries are all loaded.
    }
  }

  public static void apply()
  {
    with_config_logging_ = COMMON.with_config_logging.get();
    with_experimental_features_ = COMMON.with_experimental.get();
    with_debug_logs_ = COMMON.with_debug_logging.get();
    if(with_experimental_features_) Auxiliaries.logger().info("Config: EXPERIMENTAL FEATURES ENABLED.");
    if(with_debug_logs_) Auxiliaries.logger().info("Config: DEBUG LOGGING ENABLED, WARNING, THIS MAY SPAM THE LOG.");
    immersiveengineering_installed = Auxiliaries.isModLoaded("immersiveengineering");
    updateOptouts();
    if(!SERVER_CONFIG_SPEC.isLoaded()) return;
    without_direct_slab_pickup = SERVER.without_direct_slab_pickup.get();
    // -----------------------------------------------------------------------------------------------------------------
    EdChair.on_config(SERVER.without_chair_sitting.get(), SERVER.without_mob_chair_sitting.get(), SERVER.chair_mob_sitting_probability_percent.get(), SERVER.chair_mob_standup_probability_percent.get());
    EdLadderBlock.on_config(SERVER.without_ladder_speed_boost.get());
    VariantSlabBlock.on_config(!SERVER.without_direct_slab_pickup.get());
    SlabSliceBlock.on_config(!SERVER.without_direct_slab_pickup.get());
    EdFluidBarrel.on_config(12000, 1000);
    EdHopper.on_config();
    // -----------------------------------------------------------------------------------------------------------------


    // -----------------------------------------------------------------------------------------------------------------

  }

  public static void log(String config_message)
  {
    if(!with_config_logging_) return;
    Auxiliaries.logger().info(config_message);
  }

}
