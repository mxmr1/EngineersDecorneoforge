/*
 * @file EdMineralSmelter.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Small highly insulated stone liquification furnace
 * (magmatic phase).
 */
package wile.engineersdecor.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import wile.engineersdecor.ModConfig;
import wile.engineersdecor.ModContent;
import wile.engineersdecor.libmc.*;

import javax.annotation.Nullable;
import java.util.*;

public class EdMineralSmelter
{
    public static void on_config(int consumption, int heatup_per_second)
    { MineralSmelterTileEntity.on_config(consumption, heatup_per_second); }

    //--------------------------------------------------------------------------------------------------------------------
    // Block
    //--------------------------------------------------------------------------------------------------------------------

    public static class MineralSmelterBlock extends StandardBlocks.Horizontal implements StandardEntityBlocks.IStandardEntityBlock<MineralSmelterTileEntity>
    {
        public static final int PHASE_MAX = 3;
        public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, PHASE_MAX);

        public MineralSmelterBlock(long config, BlockBehaviour.Properties builder, final AABB unrotatedAABB)
        { super(config, builder, unrotatedAABB); }

        @Override
        public boolean isBlockEntityTicking(Level world, BlockState state)
        { return true; }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext selectionContext)
        { return Shapes.block(); }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
        { super.createBlockStateDefinition(builder); builder.add(PHASE); }

        @Override
        @Nullable
        public BlockState getStateForPlacement(BlockPlaceContext context)
        { return super.getStateForPlacement(context).setValue(PHASE, 0); }

        @Override
        @SuppressWarnings("deprecation")
        public boolean hasAnalogOutputSignal(BlockState state)
        { return true; }

        @Override
        @SuppressWarnings("deprecation")
        public int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos)
        { return Mth.clamp((state.getValue(PHASE)*5), 0, 15); }

        @Override
        public boolean shouldCheckWeakPower(BlockState state, SignalGetter level, BlockPos pos, Direction side)
        { return false; }

        @Override
        public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack)
        {}

        @Override
        public boolean hasDynamicDropList()
        { return true; }

        @Override
        public List<ItemStack> dropList(BlockState state, Level world, BlockEntity te, boolean explosion)
        {
            final List<ItemStack> stacks = new ArrayList<>();
            if(world.isClientSide) return stacks;
            if(!(te instanceof MineralSmelterTileEntity smelter)) return stacks;
            smelter.reset_process();
            stacks.add(new ItemStack(this, 1));
            return stacks;
        }

        // 新版交互：改用 ItemInteractionResult 和 ItemStack 参数
        @Override
        public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult rayTraceResult)
        {
            if(player.isShiftKeyDown()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if(world.isClientSide()) return ItemInteractionResult.SUCCESS;
            MineralSmelterTileEntity te = getTe(world, pos);
            if(te==null) return ItemInteractionResult.FAIL;
            boolean dirty = false;
            if(te.accepts_lava_container(stack)) {
                if(stack.is(Items.BUCKET)) {
                    if(te.bucket_extraction_possible()) {
                        if(stack.getCount() > 1) {
                            int emptySlot = -1;
                            for(int i=0; i<player.getInventory().getContainerSize(); ++i) {
                                if(player.getInventory().getItem(i).isEmpty()) {
                                    emptySlot = i;
                                    break;
                                }
                            }
                            if(emptySlot >= 0) {
                                te.reset_process();
                                stack.shrink(1);
                                player.setItemInHand(hand, stack);
                                player.getInventory().setItem(emptySlot, new ItemStack(Items.LAVA_BUCKET));
                                world.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1f, 1f);
                                dirty = true;
                            }
                        } else {
                            te.reset_process();
                            player.setItemInHand(hand, new ItemStack(Items.LAVA_BUCKET));
                            world.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1f, 1f);
                            dirty = true;
                        }
                    }
                }
            } else if(stack.isEmpty()) {
                final ItemStack istack = te.extract(true);
                if(te.phase() > MineralSmelterTileEntity.PHASE_WARMUP) player.setRemainingFireTicks(20); // setSecondsOnFire -> setRemainingFireTicks
                if(!istack.isEmpty()) {
                    player.setItemInHand(hand, te.extract(false));
                    dirty = true;
                }
            } else if(te.insert(stack,false)) {
                stack.shrink(1);
                dirty = true;
            }
            if(dirty) player.getInventory().setChanged();
            return ItemInteractionResult.CONSUME;
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource rnd)
        {
            if(state.getBlock()!=this) return;
            ParticleOptions particle = ParticleTypes.SMOKE;
            switch(state.getValue(PHASE)) {
                case MineralSmelterTileEntity.PHASE_WARMUP:
                    return;
                case MineralSmelterTileEntity.PHASE_HOT:
                    if(rnd.nextInt(10) > 4) return;
                    break;
                case MineralSmelterTileEntity.PHASE_MAGMABLOCK:
                    if(rnd.nextInt(10) > 7) return;
                    particle = ParticleTypes.LARGE_SMOKE;
                    break;
                case MineralSmelterTileEntity.PHASE_LAVA:
                    if(rnd.nextInt(10) > 2) return;
                    particle = ParticleTypes.LAVA;
                    break;
                default:
                    return;
            }
            final double x=0.5+pos.getX(), y=0.5+pos.getY(), z=0.5+pos.getZ();
            final double xr=rnd.nextDouble()*0.4-0.2, yr=rnd.nextDouble()*0.5, zr=rnd.nextDouble()*0.4-0.2;
            world.addParticle(particle, x+xr, y+yr, z+zr, 0.0, 0.0, 0.0);
        }

        @Nullable
        private MineralSmelterTileEntity getTe(Level world, BlockPos pos)
        { final BlockEntity te=world.getBlockEntity(pos); return (!(te instanceof MineralSmelterTileEntity)) ? (null) : ((MineralSmelterTileEntity)te); }
    }

    //--------------------------------------------------------------------------------------------------------------------
    // Tile entity
    //--------------------------------------------------------------------------------------------------------------------

    public static class MineralSmelterTileEntity extends StandardEntityBlocks.StandardBlockEntity
    {
        public static final int NUM_OF_SLOTS = 2;
        public static final int TICK_INTERVAL = 20;
        public static final int MAX_FLUID_LEVEL = 2000;
        public static final int MAX_BUCKET_EXTRACT_FLUID_LEVEL = 900;
        public static final int MAX_ENERGY_BUFFER = 32000;
        public static final int MAX_ENERGY_TRANSFER = 8192;
        public static final int DEFAULT_ENERGY_CONSUMPTION = 92;
        public static final int DEFAULT_HEATUP_RATE = 2;
        public static final int PHASE_WARMUP = 0;
        public static final int PHASE_HOT = 1;
        public static final int PHASE_MAGMABLOCK = 2;
        public static final int PHASE_LAVA = 3;

        private static final Set<Item> accepted_minerals = new HashSet<>();
        private static final Set<Item> accepted_lava_containers = new HashSet<>();
        private static int energy_consumption = DEFAULT_ENERGY_CONSUMPTION;
        private static int heatup_rate = DEFAULT_HEATUP_RATE;
        private static int cooldown_rate = 1;
        private int tick_timer_;
        private int progress_;
        private boolean force_block_update_;

        private final RfEnergy.Battery battery_ = new RfEnergy.Battery(MAX_ENERGY_BUFFER, MAX_ENERGY_TRANSFER, 0);
        // 新版 Tank 直接实现 IFluidHandler，无需包装
        private final Fluidics.Tank tank_ = new Fluidics.Tank(MAX_FLUID_LEVEL, 0, 100);
        private final Inventories.StorageInventory main_inventory_;

        // ----- 能力提供者实例（供 EdCapabilities 注册）----------
        public final IEnergyStorage energy_handler_ = battery_;
        public final IFluidHandler fluid_handler_ = tank_;
        public final IItemHandler item_handler_;

        static {
            accepted_lava_containers.add(Items.BUCKET);
        }

        public static void on_config(int consumption, int heatup_per_second)
        {
            energy_consumption = Mth.clamp(consumption, 8, 4096);
            heatup_rate = Mth.clamp(heatup_per_second, 1, 5);
            cooldown_rate = Mth.clamp(heatup_per_second/2, 1, 5);
            ModConfig.log("Config mineal smelter: energy consumption:" + energy_consumption + "rf/t, heat-up rate: " + heatup_rate + "%/s.");
        }

        public MineralSmelterTileEntity(BlockPos pos, BlockState state)
        {
            super(ModContent.getBlockEntityTypeOfBlock(state.getBlock()), pos, state);
            main_inventory_ = new Inventories.StorageInventory(this, NUM_OF_SLOTS, 1);
            main_inventory_.setStackLimit(1);
            // Inventories.MappedItemHandler.createGenericHandler 返回 Optional，取之
            item_handler_ = Inventories.MappedItemHandler.createGenericHandler(
                    main_inventory_,
                    (index,stack) -> (index==1) && (phase()!=PHASE_LAVA),
                    (index,stack) -> (index==0) && (progress_==0) && accepts_input(stack),
                    (index,stack) -> {},
                    (index,stack) -> { if(index!=0) reset_process(); }
            ).orElse(null);
        }

        public int progress()
        { return progress_; }

        public int phase()
        {
            if(progress_ >= 100) return PHASE_LAVA;
            if(progress_ >=  90) return PHASE_MAGMABLOCK;
            if(progress_ >=   5) return PHASE_HOT;
            return PHASE_WARMUP;
        }

        public boolean bucket_extraction_possible()
        { return tank_.getFluidAmount() >= MAX_BUCKET_EXTRACT_FLUID_LEVEL; }

        public int comparator_signal()
        { return phase() * 5; }

        public boolean accepts_lava_container(ItemStack stack)
        { return accepted_lava_containers.contains(stack.getItem()); }

        private boolean accepts_input(ItemStack stack)
        {
            if(!main_inventory_.isEmpty()) {
                return false;
            } else if(bucket_extraction_possible()) {
                return accepts_lava_container(stack);
            } else {
                return accepted_minerals.contains(stack.getItem()) || Auxiliaries.isInItemTag(stack.getItem(), ResourceLocation.fromNamespaceAndPath(Auxiliaries.modid(), "accepted_mineral_smelter_input"));
            }
        }

        public boolean insert(final ItemStack stack, boolean simulate)
        {
            if(stack.isEmpty() || !accepts_input(stack)) return false;
            if(!simulate) {
                ItemStack st = stack.copy();
                st.setCount(1);
                main_inventory_.setItem(0, st);
                if(!accepts_lava_container(stack)) progress_ = 0;
                force_block_update_ = true;
            }
            return true;
        }

        public ItemStack extract(boolean simulate)
        {
            ItemStack stack = main_inventory_.getItem(1).copy();
            if(stack.isEmpty()) return ItemStack.EMPTY;
            if(!simulate) reset_process();
            return stack;
        }

        protected void reset_process()
        {
            main_inventory_.setItem(0, ItemStack.EMPTY);
            main_inventory_.setItem(1, ItemStack.EMPTY);
            tank_.clear();
            force_block_update_ = true;
            tick_timer_ = 0;
            progress_ = 0;
        }

        public void readnbt(CompoundTag nbt, HolderLookup.Provider provider)
        {
            main_inventory_.load(nbt);
            battery_.load(nbt);
            tank_.load(nbt, provider);
            progress_ = nbt.getInt("progress");
        }

        protected void writenbt(CompoundTag nbt, HolderLookup.Provider provider)
        {
            main_inventory_.save(nbt);
            battery_.save(nbt);
            tank_.save(nbt, provider);
            nbt.putInt("progress", Mth.clamp(progress_,0 , 100));
        }

        // BlockEntity ------------------------------------------------------------------------------

        @Override
        public void load(CompoundTag nbt, HolderLookup.Provider provider)
        { super.load(nbt, provider); readnbt(nbt, provider); }

        @Override
        protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider provider)
        { super.saveAdditional(nbt, provider); writenbt(nbt, provider); }

        @Override
        public void setRemoved()
        {
            super.setRemoved();
            // 不再需要 invalidate
        }

        // ITickable ------------------------------------------------------------------------------------

        @Override
        public void tick()
        {
            if(--tick_timer_ > 0) return;
            tick_timer_ = TICK_INTERVAL;
            BlockState state = level.getBlockState(worldPosition);
            if(!(state.getBlock() instanceof MineralSmelterBlock)) return;
            boolean dirty = false;
            final int last_phase = phase();
            final ItemStack istack = main_inventory_.getItem(0);
            if(istack.isEmpty() && (tank_.getFluidAmount() < MAX_BUCKET_EXTRACT_FLUID_LEVEL) && (phase() != PHASE_LAVA)) {
                progress_ = 0;
                tank_.clear();
                main_inventory_.clearContent();
            } else if(battery_.isEmpty() || level.hasNeighborSignal(worldPosition)) {
                progress_ = Mth.clamp(progress_-cooldown_rate, 0, 100);
            } else if(progress_ >= 100) {
                progress_ = 100;
                if(!battery_.draw(energy_consumption * TICK_INTERVAL / 20)) battery_.clear();
            } else if((phase() >= PHASE_LAVA) || (!istack.isEmpty())) {
                if(!battery_.draw(energy_consumption * TICK_INTERVAL)) battery_.clear();
                progress_ = Mth.clamp(progress_+heatup_rate, 0, 100);
            }
            final int new_phase = phase();
            if(accepts_lava_container(istack)) {
                if(istack.is(Items.BUCKET)) {
                    if(!main_inventory_.getItem(1).is(Items.LAVA_BUCKET)) {
                        if(bucket_extraction_possible()) {
                            reset_process();
                            main_inventory_.setItem(1, new ItemStack(Items.LAVA_BUCKET));
                            level.playSound(null, worldPosition, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 0.2f, 1.3f);
                        } else {
                            main_inventory_.setItem(1, istack.copy());
                        }
                        dirty = true;
                    }
                } else {
                    main_inventory_.setItem(1, istack.copy());
                }
            } else if(new_phase > last_phase) {
                switch(new_phase) {
                    case PHASE_LAVA -> {
                        tank_.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
                        main_inventory_.setItem(1, ItemStack.EMPTY);
                        main_inventory_.setItem(0, ItemStack.EMPTY);
                        level.playSound(null, worldPosition, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 0.2f, 1.0f);
                        dirty = true;
                    }
                    case PHASE_MAGMABLOCK -> {
                        main_inventory_.setItem(1, new ItemStack(Blocks.MAGMA_BLOCK));
                        level.playSound(null, worldPosition, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.2f, 0.8f);
                        dirty = true;
                    }
                    case PHASE_HOT -> {
                        level.playSound(null, worldPosition, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.2f, 0.8f);
                    }
                }
            } else if(new_phase < last_phase) {
                switch(new_phase) {
                    case PHASE_MAGMABLOCK -> {
                        if(tank_.getFluidAmount() < MAX_BUCKET_EXTRACT_FLUID_LEVEL) {
                            reset_process();
                        } else {
                            main_inventory_.setItem(0, new ItemStack(Blocks.MAGMA_BLOCK));
                            main_inventory_.setItem(1, new ItemStack(Blocks.MAGMA_BLOCK));
                            tank_.drain(1000, IFluidHandler.FluidAction.EXECUTE);
                        }
                        level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.1f);
                        dirty = true;
                    }
                    case PHASE_HOT -> {
                        if(istack.is(Blocks.MAGMA_BLOCK.asItem())) {
                            main_inventory_.setItem(1, new ItemStack(Blocks.OBSIDIAN));
                        } else {
                            main_inventory_.setItem(1, new ItemStack(Blocks.COBBLESTONE));
                        }
                        level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.3f, 0.9f);
                        dirty = true;
                    }
                    case PHASE_WARMUP -> {
                        level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.3f, 0.7f);
                    }
                }
            } else if(phase() >= PHASE_LAVA) {
                if(tank_.getFluidAmount() <= 0) {
                    reset_process();
                    level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.3f, 0.7f);
                } else {
                    FluidStack fs = tank_.getFluidInTank(0).copy();
                    if(fs.getAmount() > 100) fs.setAmount(100);
                    // 新版 fill 方法需要 FluidAction 参数
                    final int n = Fluidics.fill(level, getBlockPos().below(), Direction.UP, fs, IFluidHandler.FluidAction.EXECUTE);
                    if(n > 0) {
                        tank_.drain(n, IFluidHandler.FluidAction.EXECUTE);
                        if(tank_.isEmpty()) {
                            final ItemStack prev = main_inventory_.getItem(0);
                            reset_process();
                            main_inventory_.setItem(0, prev);
                            level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.3f, 0.7f);
                        }
                    }
                }
            }
            if((force_block_update_ || (state.getValue(MineralSmelterBlock.PHASE) != new_phase))) {
                state = state.setValue(MineralSmelterBlock.PHASE, new_phase);
                level.setBlock(worldPosition, state, 3|16);
                level.updateNeighborsAt(getBlockPos(), state.getBlock());
                force_block_update_ = false;
            }
            if(dirty) setChanged();
        }
    }
}