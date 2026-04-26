/*
 * @file EdTestBlock.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Creative mod testing block
 */
package wile.engineersdecor.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;

import wile.engineersdecor.ModContent;
import wile.engineersdecor.libmc.*;

public class EdTestBlock {

    public static class TestBlock extends StandardBlocks.Directed
            implements StandardEntityBlocks.IStandardEntityBlock<TestTileEntity>, Auxiliaries.IExperimentalFeature {

        public TestBlock(long config, BlockBehaviour.Properties builder, final AABB unrotatedAABB) {
            super(config, builder, unrotatedAABB);
        }

        @Override
        public boolean isBlockEntityTicking(Level world, BlockState state) {
            return true;
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos,
                                            CollisionContext selectionContext) {
            return Shapes.block();
        }

        @Override
        public boolean isSignalSource(BlockState state) {
            return true;
        }

        @Override
        public boolean shouldCheckWeakPower(BlockState state, SignalGetter level, BlockPos pos, Direction side) {
            return false;
        }

        @Override
        public boolean hasDynamicDropList() {
            return true;
        }

        @Override
        public List<ItemStack> dropList(BlockState state, Level world, BlockEntity te, boolean explosion) {
            return Collections.singletonList(new ItemStack(this));
        }

        //@Override
        public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
                                     BlockHitResult hit) {
            if (world.isClientSide()) return InteractionResult.SUCCESS;
            BlockEntity be = world.getBlockEntity(pos);
            if (!(be instanceof TestTileEntity te)) return InteractionResult.FAIL;
            return te.activated(player, hand, hit) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
    }

    // ---------------------------------------------------------------------
    // Tile Entity
    // ---------------------------------------------------------------------
    public static class TestTileEntity extends StandardEntityBlocks.StandardBlockEntity {

        private final RfEnergy.Battery battery_;
        private final Fluidics.Tank tank_;
        private final Inventories.StorageInventory inventory_;

        private BlockCapabilityCache<IEnergyStorage, Direction> capEnergyCache;
        private BlockCapabilityCache<IFluidHandler, Direction> capFluidCache;
        private BlockCapabilityCache<IItemHandler, Direction> capItemCache;

        private int tick_timer = 0;
        private int rf_fed_avg = 0;
        private int rf_fed_total = 0;
        private int rf_fed_acc = 0;
        private int rf_received_avg = 0;
        private int rf_received_total = 0;
        private int liq_filled_avg = 0;
        private int liq_filled_total = 0;
        private int liq_filled_acc = 0;
        private int liq_received_avg = 0;
        private int liq_received_total = 0;
        private int items_inserted_total = 0;
        private int items_received_total = 0;
        private int rf_feed_setting = 4096;
        private FluidStack liq_fill_stack = FluidStack.EMPTY;
        private ItemStack insertion_item = ItemStack.EMPTY;
        private Direction block_facing = Direction.NORTH;
        private boolean paused = false;

        public TestTileEntity(BlockPos pos, BlockState state) {
            super(ModContent.getBlockEntityTypeOfBlock(state.getBlock()), pos, state);
            battery_ = new RfEnergy.Battery((int) 1e9, (int) 1e9, 0, 0);
            tank_ = new Fluidics.Tank((int) 1e9);
            inventory_ = new Inventories.StorageInventory(this, 1);
        }

        @Override
        public void setLevel(Level level) {
            super.setLevel(level);
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                capEnergyCache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, serverLevel, worldPosition, null);
                capFluidCache = BlockCapabilityCache.create(Capabilities.FluidHandler.BLOCK, serverLevel, worldPosition, null);
                capItemCache = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, serverLevel, worldPosition, null);
            }
        }

        // Новая сигнатура load
        @Override
        public void loadAdditional(CompoundTag nbt, HolderLookup.Provider provider) {
            super.loadAdditional(nbt, provider);

            // Исправлено: tank_.load(nbt) → tank_.load(nbt.getCompound("tank"), provider)
            if (nbt.contains("tank", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                tank_.load(nbt.getCompound("tank"), provider);
            } else {
                tank_.clear();
            }

            battery_.load(nbt);
            rf_fed_avg = nbt.getInt("rf_fed_avg");
            rf_fed_total = nbt.getInt("rf_fed_total");
            rf_fed_acc = nbt.getInt("rf_fed_acc");
            rf_received_avg = nbt.getInt("rf_received_avg");
            rf_received_total = nbt.getInt("rf_received_total");
            liq_filled_avg = nbt.getInt("liq_filled_avg");
            liq_filled_total = nbt.getInt("liq_filled_total");
            liq_filled_acc = nbt.getInt("liq_filled_acc");
            liq_received_avg = nbt.getInt("liq_received_avg");
            liq_received_total = nbt.getInt("liq_received_total");
            rf_feed_setting = nbt.getInt("rf_feed_setting");
            items_received_total = nbt.getInt("items_received_total");
            items_inserted_total = nbt.getInt("items_inserted_total");

            liq_fill_stack = FluidStack.parseOptional(provider, nbt.getCompound("liq_fill_stack"));
            insertion_item = ItemStack.parseOptional(provider, nbt.getCompound("insertion_item"));
        }

        @Override
        protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider provider) {
            super.saveAdditional(nbt, provider);

            // Исправлено: tank_.save(nbt) → tank_.save(nbt, provider)
            tank_.save(nbt, provider);

            battery_.save(nbt);
            nbt.putInt("rf_fed_avg", rf_fed_avg);
            nbt.putInt("rf_fed_total", rf_fed_total);
            nbt.putInt("rf_fed_acc", rf_fed_acc);
            nbt.putInt("rf_received_avg", rf_received_avg);
            nbt.putInt("rf_received_total", rf_received_total);
            nbt.putInt("liq_filled_avg", liq_filled_avg);
            nbt.putInt("liq_filled_total", liq_filled_total);
            nbt.putInt("liq_filled_acc", liq_filled_acc);
            nbt.putInt("liq_received_avg", liq_received_avg);
            nbt.putInt("liq_received_total", liq_received_total);
            nbt.putInt("rf_feed_setting", rf_feed_setting);
            nbt.putInt("items_received_total", items_received_total);
            nbt.putInt("items_inserted_total", items_inserted_total);
            nbt.put("liq_fill_stack", liq_fill_stack.saveOptional(provider));
            nbt.put("insertion_item", insertion_item.saveOptional(provider));
        }

        private FluidStack getFillFluid(ItemStack stack) {
            if (stack.is(Items.WATER_BUCKET)) return new FluidStack(Fluids.WATER, 1000);
            if (stack.is(Items.LAVA_BUCKET)) return new FluidStack(Fluids.LAVA, 1000);
            return FluidStack.EMPTY;
        }

        private ItemStack getRandomItemstack() {
            var list = BuiltInRegistries.ITEM.stream().toList();
            ItemStack stack = new ItemStack(list.get((int) (Math.random() * list.size())));
            stack.setCount((int) (Math.random() * stack.getMaxStackSize()));
            return stack;
        }

        public boolean activated(Player player, InteractionHand hand, BlockHitResult hit) {
            final ItemStack held = player.getItemInHand(hand);
            if (held.isEmpty()) {
                ArrayList<String> msgs = new ArrayList<>();
                if (rf_fed_avg > 0) msgs.add("-" + rf_fed_avg + "rf/t");
                if (rf_fed_total > 0) msgs.add("-" + rf_fed_total + "rf");
                if (rf_received_avg > 0) msgs.add("+" + rf_received_avg + "rf/t");
                if (rf_received_total > 0) msgs.add("+" + rf_received_total + "rf");
                if (liq_filled_avg > 0) msgs.add("-" + liq_filled_avg + "mb/t");
                if (liq_filled_total > 0) msgs.add("-" + liq_filled_total + "mb");
                if (liq_received_avg > 0) msgs.add("+" + liq_received_avg + "mb/t");
                if (liq_received_total > 0) msgs.add("+" + liq_received_total + "mb");
                if (items_received_total > 0) msgs.add("+" + items_received_total + "items");
                if (items_inserted_total > 0) msgs.add("-" + items_inserted_total + "items");
                if (msgs.isEmpty()) msgs.add("Nothing transferred yet.");
                Overlay.show(player, Component.literal(String.join(" | ", msgs)), 1000);
                return true;
            }
            return false;
        }

        @Override
        public void tick() {
            if (level.isClientSide()) return;
            block_facing = getBlockState().getValue(TestBlock.FACING);
            paused = level.hasNeighborSignal(getBlockPos());
        }
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModContent.getBlockEntityTypeOfBlock(ModContent.getBlock("test_block")),
                (be, side) -> ((TestTileEntity) be).inventory_ instanceof IItemHandler ih ? ih : null
        );

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModContent.getBlockEntityTypeOfBlock(ModContent.getBlock("test_block")),
                (be, side) -> ((TestTileEntity) be).battery_ instanceof IEnergyStorage es ? es : null
        );

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModContent.getBlockEntityTypeOfBlock(ModContent.getBlock("test_block")),
                (be, side) -> ((TestTileEntity) be).tank_ instanceof IFluidHandler fh ? fh : null
        );
    }
}
