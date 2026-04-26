/*
 * @file Fluidics.java
 * @author Stefan Wilhelm (wile) - ported fixes
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * General fluid handling functionality.
 */
package wile.engineersdecor.libmc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class Fluidics {

    public static class Tank implements IFluidHandler {
        private Predicate<FluidStack> validator_ = (e) -> true;
        private BiConsumer<Tank, Integer> interaction_notifier_ = (tank, diff) -> {};
        private FluidStack fluid_ = FluidStack.EMPTY;
        private int capacity_;
        private int fill_rate_;
        private int drain_rate_;

        public Tank(int capacity) {
            this(capacity, capacity, capacity);
        }

        public Tank(int capacity, int fill_rate, int drain_rate) {
            this(capacity, fill_rate, drain_rate, e -> true);
        }

        public Tank(int capacity, int fill_rate, int drain_rate, @Nullable Predicate<FluidStack> validator) {
            capacity_ = capacity;
            setMaxFillRate(fill_rate);
            setMaxDrainRate(drain_rate);
            setValidator(validator);
        }

        public Tank load(CompoundTag nbt, HolderLookup.Provider provider) {
            if (nbt != null && nbt.contains("tank", Tag.TAG_COMPOUND)) {
                setFluid(FluidStack.parseOptional(provider, nbt.getCompound("tank")));
            } else {
                clear();
            }
            return this;
        }

        public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
            if (!isEmpty()) {
                nbt.put("tank", fluid_.save(provider));
            }
            return nbt;
        }

        public void reset() {
            clear();
        }

        public Tank clear() {
            setFluid(null);
            return this;
        }

        public int getCapacity() {
            return capacity_;
        }

        public Tank setCapacity(int capacity) {
            capacity_ = capacity;
            return this;
        }

        public int getMaxDrainRate() {
            return drain_rate_;
        }

        public Tank setMaxDrainRate(int rate) {
            drain_rate_ = Mth.clamp(rate, 0, capacity_);
            return this;
        }

        public int getMaxFillRate() {
            return fill_rate_;
        }

        public Tank setMaxFillRate(int rate) {
            fill_rate_ = Mth.clamp(rate, 0, capacity_);
            return this;
        }

        public Tank setValidator(@Nullable Predicate<FluidStack> validator) {
            validator_ = (validator != null) ? validator : (e) -> true;
            return this;
        }

        public Tank setInteractionNotifier(@Nullable BiConsumer<Tank, Integer> notifier) {
            interaction_notifier_ = (notifier != null) ? notifier : (tank, diff) -> {};
            return this;
        }

        // IFluidHandler --------------------------------------------------------------------------------

        @Override
        public int getTanks() {
            return 1;
        }

        @Nonnull
        @Override
        public FluidStack getFluidInTank(int tank) {
            return (tank == 0) ? fluid_ : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return (tank == 0) ? capacity_ : 0;
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            return (tank == 0) && validator_.test(stack);
        }

        @Override
        public int fill(FluidStack fs, FluidAction action) {
            if (fs == null || fs.isEmpty() || (!isFluidValid(0, fs))) {
                return 0;
            }

            if (action.simulate()) {
                if (fluid_.isEmpty()) return Math.min(capacity_, fs.getAmount());
                if (!FluidStack.isSameFluid(fs, fluid_)) return 0;
                return Math.min(capacity_ - fluid_.getAmount(), fs.getAmount());
            }

            if (fluid_.isEmpty()) {
                int toSet = Math.min(capacity_, fs.getAmount());
                fluid_ = fs.copyWithAmount(toSet);
                if (fluid_.getAmount() > 0) interaction_notifier_.accept(this, fluid_.getAmount());
                return fluid_.getAmount();
            } else {
                if (!FluidStack.isSameFluid(fs, fluid_)) {
                    return 0;
                } else {
                    int amount = Math.min(Math.min(capacity_ - fluid_.getAmount(), fs.getAmount()), fill_rate_);
                    if (amount > 0) {
                        fluid_.grow(amount);
                        interaction_notifier_.accept(this, amount);
                    }
                    return amount;
                }
            }
        }

        @Nonnull
        @Override
        public FluidStack drain(FluidStack fs, FluidAction action) {
            if (fs == null || fs.isEmpty()) return FluidStack.EMPTY;
            if (!FluidStack.isSameFluid(fs, fluid_)) return FluidStack.EMPTY;
            return drain(fs.getAmount(), action);
        }

        @Nonnull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            final int amount = Math.min(Math.min(fluid_.getAmount(), maxDrain), drain_rate_);
            final FluidStack stack = fluid_.copyWithAmount(amount);
            if ((amount > 0) && action.execute()) {
                fluid_.shrink(amount);
                if (fluid_.isEmpty()) fluid_ = FluidStack.EMPTY;
                interaction_notifier_.accept(this, -amount);
            }
            return stack;
        }

        // Utility methods ------------------------------------------------------------------------------

        public FluidStack getFluid() {
            return fluid_;
        }

        public void setFluid(@Nullable FluidStack stack) {
            fluid_ = (stack == null) ? FluidStack.EMPTY : stack;
        }

        public int getFluidAmount() {
            return fluid_.getAmount();
        }

        public boolean isEmpty() {
            return fluid_.isEmpty();
        }

        public boolean isFull() {
            return getFluidAmount() >= getCapacity();
        }

        public boolean isFluidEqual(FluidStack stack) {
            if (stack == null) return fluid_.isEmpty();
            return FluidStack.isSameFluid(stack, fluid_);
        }
    }

    // -------------------------------------------------------------------------------------------------------------------

    public static @Nullable IFluidHandler handler(Level world, BlockPos pos, @Nullable Direction side) {
        return world.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
    }

    /**
     * Fills or drains items with fluid handlers from or into tile blocks with fluid handlers.
     */
    public static boolean manualFluidHandlerInteraction(Level world, BlockPos pos, @Nullable Direction side, Player player, InteractionHand hand) {
        if (world == null || world.isClientSide()) return false;
        BlockEntity te = world.getBlockEntity(pos);
        if (te == null) {
            System.out.println("No BlockEntity found at " + pos);
            return false;
        }
        BlockState state = world.getBlockState(pos);
        IFluidHandler fh = world.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, te, side);
        System.out.println("FluidHandler at pos " + pos + ": " + (fh != null ? fh.getClass().getSimpleName() : "null"));
        if (fh == null) return false;
        boolean result = FluidUtil.interactWithFluidHandler(player, hand, fh);
        System.out.println("FluidUtil.interactWithFluidHandler result: " + result + ", pos: " + pos + ", hand: " + hand);
        return result;
    }

    public static boolean manualFluidHandlerInteraction(Player player, InteractionHand hand, IFluidHandler handler) {
        return FluidUtil.interactWithFluidHandler(player, hand, handler);
    }

    /**
     * Fills or drains items with fluid handlers from or into tile blocks with fluid handlers.
     * Returns the fluid and (possibly negative) amount that transferred from the item into the block.
     */
    public static @Nullable Tuple<Fluid, Integer> manualTrackedFluidHandlerInteraction(Level world, BlockPos pos, @Nullable Direction side, Player player, InteractionHand hand) {
        if (world.isClientSide()) return null;
        final ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) return null;
        final IFluidHandler fh = handler(world, pos, side);
        if (fh == null) return null;
        final IItemHandler ih = player.getCapability(Capabilities.ItemHandler.ENTITY, null);
        if (ih == null) return null;

        FluidActionResult far = FluidUtil.tryFillContainerAndStow(held, fh, ih, Integer.MAX_VALUE, player, true);
        if (!far.isSuccess()) far = FluidUtil.tryEmptyContainerAndStow(held, fh, ih, Integer.MAX_VALUE, player, true);
        if (!far.isSuccess()) return null;
        final ItemStack rstack = far.getResult().copy();
        player.setItemInHand(hand, far.getResult());
        final IFluidHandler fh_before = held.getCapability(Capabilities.FluidHandler.ITEM, null);
        final IFluidHandler fh_after = rstack.getCapability(Capabilities.FluidHandler.ITEM, null);
        if ((fh_before == null) || (fh_after == null) || (fh_after.getTanks() != fh_before.getTanks())) return null;
        for (int i = 0; i < fh_before.getTanks(); ++i) {
            final int vol_before = fh_before.getFluidInTank(i).getAmount();
            final int vol_after = fh_after.getFluidInTank(i).getAmount();
            if (vol_before != vol_after) {
                return new Tuple<>(
                        (vol_before > 0) ? fh_before.getFluidInTank(i).getFluid() : fh_after.getFluidInTank(i).getFluid(),
                        (vol_before - vol_after)
                );
            }
        }
        return null;
    }

    public static boolean manualFluidHandlerInteraction(Player player, InteractionHand hand, Level world, BlockPos pos, @Nullable Direction side) {
        return FluidUtil.interactWithFluidHandler(player, hand, world, pos, side);
    }

    public static int fill(Level world, BlockPos pos, Direction side, FluidStack fs, FluidAction action) {
        IFluidHandler fh = world.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        return (fh == null) ? 0 : fh.fill(fs, action);
    }

    public static int fill(Level world, BlockPos pos, Direction side, FluidStack fs) {
        return fill(world, pos, side, fs, FluidAction.EXECUTE);
    }

    /**
     * Fluid tank access when itemized.
     */
    public static class FluidContainerItemCapabilityWrapper implements IFluidHandler {
        private final ItemStack container_;
        private final int capacity_;
        private final int transfer_rate_;
        private final Predicate<FluidStack> validator_;
        private final java.util.function.Function<ItemStack, CompoundTag> nbt_getter_;
        private final BiConsumer<ItemStack, CompoundTag> nbt_setter_;

        public FluidContainerItemCapabilityWrapper(ItemStack container, int capacity, int transfer_rate,
                                                   java.util.function.Function<ItemStack, CompoundTag> nbt_getter,
                                                   BiConsumer<ItemStack, CompoundTag> nbt_setter,
                                                   Predicate<FluidStack> validator) {
            container_ = container;
            capacity_ = capacity;
            transfer_rate_ = transfer_rate;
            nbt_getter_ = nbt_getter;
            nbt_setter_ = nbt_setter;
            validator_ = (validator != null) ? validator : (e -> true);
        }

        protected FluidStack readnbt() {
            final CompoundTag nbt = nbt_getter_.apply(container_);
            if (nbt == null || nbt.isEmpty()) return FluidStack.EMPTY;

            HolderLookup.Provider provider = RegistryAccess.EMPTY;
            return FluidStack.parseOptional(provider, nbt);
        }

        protected void writenbt(FluidStack fs) {
            CompoundTag nbt = new CompoundTag();
            if (!fs.isEmpty()) {
                HolderLookup.Provider provider = RegistryAccess.EMPTY;
                CompoundTag fluidTag = (CompoundTag) fs.save(provider);
                nbt = fluidTag;
            }
            nbt_setter_.accept(container_, nbt);
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return (tank == 0) ? readnbt() : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return (tank == 0) ? capacity_ : 0;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack fs) {
            return (tank == 0) && validator_.test(fs);
        }

        @Override
        public int fill(FluidStack fs, FluidAction action) {
            if ((fs == null) || fs.isEmpty() || (!isFluidValid(0, fs)) || (container_.getCount() != 1)) return 0;
            FluidStack tank = readnbt();
            final int amount = Math.min(Math.min(fs.getAmount(), transfer_rate_), capacity_ - tank.getAmount());
            if (amount <= 0) return 0;
            if (tank.isEmpty()) {
                if (action.execute()) {
                    tank = fs.copyWithAmount(amount);
                    writenbt(tank);
                }
            } else {
                if (!FluidStack.isSameFluid(fs, tank)) {
                    return 0;
                } else if (action.execute()) {
                    tank.grow(amount);
                    writenbt(tank);
                }
            }
            return amount;
        }

        @Override
        public FluidStack drain(FluidStack fs, FluidAction action) {
            if ((fs == null) || fs.isEmpty() || (container_.getCount() != 1)) return FluidStack.EMPTY;
            final FluidStack tank = readnbt();
            if ((!tank.isEmpty()) && (!FluidStack.isSameFluid(fs, tank))) return FluidStack.EMPTY;
            return drain(fs.getAmount(), action);
        }

        @Override
        public FluidStack drain(int max, FluidAction action) {
            if ((max <= 0) || (container_.getCount() != 1)) return FluidStack.EMPTY;
            FluidStack tank = readnbt();
            if (tank.isEmpty()) return FluidStack.EMPTY;
            final int amount = Math.min(Math.min(tank.getAmount(), max), transfer_rate_);
            final FluidStack fs = tank.copyWithAmount(amount);
            if (action.execute()) {
                tank.shrink(amount);
                writenbt(tank);
            }
            return fs;
        }
    }
}