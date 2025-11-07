/*
 * @file EdItem.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Basic item functionality for mod items.
 */
package wile.engineersdecor.items;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import wile.engineersdecor.ModConfig;
import wile.engineersdecor.libmc.Auxiliaries;
import wile.engineersdecor.libmc.EDRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class EdItem extends Item
{
    public EdItem(Item.Properties properties)
    { super(properties); }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag)
    {
        Auxiliaries.Tooltip.addInformation(stack, null, tooltip, flag, true);
    }

    public Collection<CreativeModeTab> getCreativeTabs()
    {
        return ModConfig.isOptedOut(this)
                ? new ArrayList<>()
                : Collections.singletonList(EDRegistries.getCreativeModeTab());
    }
}