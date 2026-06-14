package wile.engineersdecor.blocks;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.extensions.IBlockExtension;
import net.neoforged.neoforge.items.IItemHandler;
import wile.engineersdecor.ModContent;
import wile.engineersdecor.libmc.*;
import wile.engineersdecor.network.EdNetworking;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class EdHopper
{
    public static void on_config()
    {}

    //--------------------------------------------------------------------------------------------------------------------
    // 方块
    //--------------------------------------------------------------------------------------------------------------------

    public static class HopperBlock extends StandardBlocks.Directed implements StandardEntityBlocks.IStandardEntityBlock<EdHopper.HopperTileEntity>, IBlockExtension
    {
        public HopperBlock(long config, BlockBehaviour.Properties builder, final Supplier<ArrayList<VoxelShape>> shape_supplier)
        { super(config, builder, shape_supplier); }

        @Override
        public boolean isBlockEntityTicking(Level world, BlockState state)
        { return true; }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
        { return Shapes.block(); }

        @Override
        @SuppressWarnings("deprecation")
        public boolean hasAnalogOutputSignal(BlockState state)
        { return true; }

        @Override
        @SuppressWarnings("deprecation")
        public int getAnalogOutputSignal(BlockState blockState, Level world, BlockPos pos)
        { return (world.getBlockEntity(pos) instanceof EdHopper.HopperTileEntity te) ? RsSignals.fromContainer(te.storage_slot_range_) : 0; }

        @Override
        public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack)
        {
            if (world.isClientSide) return;

            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) return;

            CompoundTag tag = data.copyTag();
            if (!tag.contains("tedata")) return;

            CompoundTag te_nbt = tag.getCompound("tedata");
            if (te_nbt.isEmpty()) return;

            BlockEntity te = world.getBlockEntity(pos);
            if (!(te instanceof EdHopper.HopperTileEntity hopper)) return;

            hopper.readnbt(te_nbt, false);
            hopper.reset_rtstate();
            te.setChanged();
        }

        // 提取公共方法：生成带方块实体数据的物品栈，同时清空库存
        private ItemStack createHopperItemWithData(HopperTileEntity hopper) {
            ItemStack stack = new ItemStack(this);
            CompoundTag teData = hopper.clear_getnbt(); // 获取完整 NBT 并清空库存
            if (!teData.isEmpty()) {
                CompoundTag wrapper = new CompoundTag();
                wrapper.put("tedata", teData);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(wrapper));
            }
            return stack;
        }

        @Override
        public boolean hasDynamicDropList()
        { return true; }

        @Override
        public List<ItemStack> dropList(BlockState state, Level world, final BlockEntity te, boolean explosion)
        {
            final List<ItemStack> stacks = new ArrayList<>();
            if(world.isClientSide) return stacks;
            if(!(te instanceof HopperTileEntity hopper)) return stacks;
            stacks.add(createHopperItemWithData(hopper));
            return stacks;
        }

        @Override
        protected void onExplosionHit(BlockState state, Level world, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof  HopperTileEntity hopper && !world.isClientSide()) {
                hopper.exploded = true;
            }
            super.onExplosionHit(state, world, pos, explosion, dropConsumer);
        }

        @Override
        public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
            if (!world.isClientSide) {
                BlockEntity be = world.getBlockEntity(pos);
                if (be instanceof EdHopper.HopperTileEntity hopper) {
                    hopper.destroyedByCreative = player.isCreative();
                }
            }
            super.playerWillDestroy(world, pos, state, player);
            return state;
        }



        @Override
        public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
            if (!world.isClientSide && !isMoving && state.getBlock() != newState.getBlock()) {
                BlockEntity be = world.getBlockEntity(pos);
                if (be instanceof EdHopper.HopperTileEntity hopper) {
                    if(!hopper.destroyedByCreative){
                        if (!hopper.exploded){
                            // 生成带完整 NBT 的漏斗物品（内部物品和设置均保留），同时清空内部库存
                            ItemStack hopperStack = createHopperItemWithData(hopper);
                            Block.popResource(world, pos, hopperStack);   // 安全生成 ItemEntity
                        }
                        // 库存已清空，避免 NeoForge 能力系统再次掉落\
                    }
                    hopper.main_inventory_.clearContent();
                    hopper.setChanged();
                }

            }
            super.onRemove(state, world, pos, newState, isMoving);
        }

        @Nonnull
        @Override
        public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
            return useOpenGui(state, world, pos, player);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean unused)
        {
            if(!(world instanceof Level) || (world.isClientSide)) return;
            BlockEntity te = world.getBlockEntity(pos);
            if(!(te instanceof HopperTileEntity)) return;
            ((HopperTileEntity)te).block_updated();
        }

        @Override
        public void fallOn(Level world, BlockState state, BlockPos pos, Entity entity, float fallDistance)
        {
            super.fallOn(world, state, pos, entity, fallDistance);
            if(!(entity instanceof ItemEntity)) return;
            if(!(world.getBlockEntity(pos) instanceof HopperTileEntity te)) return;
            te.collection_timer_ = 0;
        }

        @Override
        public boolean shouldCheckWeakPower(BlockState state, SignalGetter level, BlockPos pos, Direction side)
        { return false; }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isSignalSource(BlockState state)
        { return true; }

        @Override
        @SuppressWarnings("deprecation")
        public int getSignal(BlockState blockState, BlockGetter blockAccess, BlockPos pos, Direction side)
        { return 0; }

        @Override
        @SuppressWarnings("deprecation")
        public int getDirectSignal(BlockState blockState, BlockGetter blockAccess, BlockPos pos, Direction side)
        { return 0; }
    }

    //--------------------------------------------------------------------------------------------------------------------
    // 方块实体
    //--------------------------------------------------------------------------------------------------------------------

    public static class HopperTileEntity extends StandardEntityBlocks.StandardBlockEntity implements MenuProvider, Nameable
    {
        public static final int NUM_OF_FIELDS = 7;
        public static final int TICK_INTERVAL = 10;
        public static final int COLLECTION_INTERVAL = 50;
        public static final int NUM_OF_SLOTS = 18;
        public static final int NUM_OF_STORAGE_SLOTS = NUM_OF_SLOTS;
        public static final int MAX_TRANSFER_COUNT = 32;
        public static final int MAX_COLLECTION_RANGE = 4;
        public static final int PERIOD_OFFSET = 10;
        ///
        public static final int LOGIC_NOT_INVERTED = 0x00;
        public static final int LOGIC_INVERTED     = 0x01;
        public static final int LOGIC_CONTINUOUS   = 0x02;
        public static final int LOGIC_IGNORE_EXT   = 0x04;
        ///
        public boolean exploded = false;
        public boolean destroyedByCreative = false;
        ///
        private boolean block_power_signal_ = false;
        private boolean block_power_updated_ = false;
        private int collection_timer_ = 0;
        private int delay_timer_ = 0;
        private int transfer_count_ = 1;
        private int logic_ = LOGIC_INVERTED|LOGIC_CONTINUOUS;
        private int transfer_period_ = 0;
        private int collection_range_ = 0;
        private int current_slot_index_ = 0;
        private int tick_timer_ = 0;
        protected final Inventories.StorageInventory main_inventory_ = new Inventories.StorageInventory(this, NUM_OF_SLOTS, 1);
        protected final Inventories.InventoryRange storage_slot_range_ = new Inventories.InventoryRange(main_inventory_, 0, NUM_OF_STORAGE_SLOTS);
        public final IItemHandler item_handler_ = new HopperItemHandler();

        public HopperTileEntity(BlockPos pos, BlockState state)
        {
            super(ModContent.getBlockEntityTypeOfBlock(state.getBlock()), pos, state);
            main_inventory_.setSlotChangeAction((slot,stack)->tick_timer_ = Math.min(tick_timer_, 8));
        }

        public void reset_rtstate()
        {
            block_power_signal_ = false;
            block_power_updated_ = false;
        }

        public CompoundTag clear_getnbt()
        {
            CompoundTag nbt = new CompoundTag();
            block_power_signal_ = false;
            writenbt(nbt, false);
            boolean is_empty = main_inventory_.isEmpty();
            main_inventory_.clearContent(); // 清空库存
            reset_rtstate();
            block_power_updated_ = false;
            if(is_empty) nbt = new CompoundTag();
            return nbt;
        }

        public void readnbt(CompoundTag nbt, boolean update_packet)
        {
            main_inventory_.load(nbt);
            block_power_signal_ = nbt.getBoolean("powered");
            current_slot_index_ = nbt.getInt("act_slot_index");
            transfer_count_ = Mth.clamp(nbt.getInt("xsize"), 1, MAX_TRANSFER_COUNT);
            logic_ = nbt.getInt("logic");
            transfer_period_ = nbt.getInt("period");
            collection_range_ = nbt.getInt("range");
        }

        protected void writenbt(CompoundTag nbt, boolean update_packet)
        {
            main_inventory_.save(nbt);
            nbt.putBoolean("powered", block_power_signal_);
            nbt.putInt("act_slot_index", current_slot_index_);
            nbt.putInt("xsize", transfer_count_);
            nbt.putInt("logic", logic_);
            nbt.putInt("period", transfer_period_);
            nbt.putInt("range", collection_range_);
        }

        public void block_updated()
        {
            boolean powered = level.hasNeighborSignal(worldPosition);
            if(block_power_signal_ != powered) block_power_updated_ = true;
            block_power_signal_ = powered;
            tick_timer_ = 1;
        }

        // BlockEntity --------------------------------------------------------------------------------------------

        @Override
        public void loadAdditional(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider provider) {
            super.loadAdditional(nbt, provider);
            readnbt(nbt, false);
        }

        @Override
        protected void saveAdditional(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider provider) {
            super.saveAdditional(nbt, provider);
            writenbt(nbt, false);
        }

        @Override
        public void setRemoved() {
            super.setRemoved(); }

        // Nameable ----------------------------------------------------------------------------------------------

        @Override
        public Component getName()
        { return Auxiliaries.localizable(getBlockState().getBlock().getDescriptionId()); }

        @Override
        public boolean hasCustomName()
        { return false; }

        @Override
        public Component getCustomName()
        { return getName(); }

        // MenuProvider ----------------------------------------------------------------------------------------------

        @Override
        public Component getDisplayName()
        { return Nameable.super.getDisplayName(); }

        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player)
        { return new HopperContainer(id, inventory, main_inventory_, ContainerLevelAccess.create(level, worldPosition), fields); }

        // Fields -----------------------------------------------------------------------------------------------

        protected final ContainerData fields = new ContainerData()
        {
            @Override
            public int getCount()
            { return HopperTileEntity.NUM_OF_FIELDS; }

            @Override
            public int get(int id)
            {
                return switch(id) {
                    case 0 -> collection_range_;
                    case 1 -> transfer_count_;
                    case 2 -> logic_;
                    case 3 -> transfer_period_;
                    case 4 -> delay_timer_;
                    case 5 -> block_power_signal_ ? 1 : 0;
                    case 6 -> current_slot_index_;
                    default -> 0;
                };
            }

            @Override
            public void set(int id, int value)
            {
                switch (id) {
                    case 0 -> collection_range_ = Mth.clamp(value, 0, MAX_COLLECTION_RANGE);
                    case 1 -> transfer_count_ = Mth.clamp(value, 1, MAX_TRANSFER_COUNT);
                    case 2 -> logic_ = value;
                    case 3 -> transfer_period_ = Mth.clamp(value, 0, 100);
                    case 4 -> delay_timer_ = Mth.clamp(value, 0, 400);
                    case 5 -> block_power_signal_ = (value != 0);
                    case 6 -> current_slot_index_ = Mth.clamp(value, 0, NUM_OF_STORAGE_SLOTS - 1);
                }
            }
        };

        // 物品处理器 --------------------------------------------------------------------------------------------

        private class HopperItemHandler implements IItemHandler
        {
            @Override
            public int getSlots()
            { return storage_slot_range_.size(); }

            @Override
            public ItemStack getStackInSlot(int slot)
            { return storage_slot_range_.get(slot); }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate)
            {
                if(stack.isEmpty()) return ItemStack.EMPTY;
                ItemStack current = storage_slot_range_.get(slot);
                if(!current.isEmpty() && Inventories.areItemStacksDifferent(stack, current)) return stack;
                int maxCanInsert = Math.min(stack.getCount(), current.getMaxStackSize() - current.getCount());
                if(maxCanInsert <= 0) return stack;
                if(!simulate) {
                    ItemStack newStack = stack.copy();
                    newStack.setCount(current.getCount() + maxCanInsert);
                    storage_slot_range_.set(slot, newStack);
                    setChanged();
                }
                ItemStack remainder = stack.copy();
                remainder.shrink(maxCanInsert);
                return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate)
            {
                ItemStack current = storage_slot_range_.get(slot);
                if(current.isEmpty() || amount <= 0) return ItemStack.EMPTY;
                int toExtract = Math.min(amount, current.getCount());
                ItemStack extracted = current.copy();
                extracted.setCount(toExtract);
                if(!simulate) {
                    current.shrink(toExtract);
                    storage_slot_range_.set(slot, current.isEmpty() ? ItemStack.EMPTY : current);
                    setChanged();
                }
                return extracted;
            }

            @Override
            public int getSlotLimit(int slot)
            { return storage_slot_range_.get(slot).getMaxStackSize(); }

            @Override
            public boolean isItemValid(int slot, ItemStack stack)
            { return true; }
        }

        // ITickable 及辅助方法 ---------------------------------------------------------------------

        private IItemHandler inventory_entity_handler(BlockPos where)
        {
            final List<Entity> entities = level.getEntities((Entity)null, new AABB(where), EntitySelector.ENTITY_STILL_ALIVE);
            for (Entity entity : entities) {
                if (entity instanceof Player) continue;  // 跳过玩家
                IItemHandler handler = entity.getCapability(Capabilities.ItemHandler.ENTITY);
                if (handler != null) return handler;
            }
            return null;
        }

        private static int next_slot(int i)
        { return (i<NUM_OF_STORAGE_SLOTS-1) ? (i+1) : 0; }

        private int try_insert_into_hopper(final ItemStack stack)
        {
            final int max_to_insert = stack.getCount();
            int n_to_insert = max_to_insert;
            int first_empty_slot = -1;
            for(int i=0; i<storage_slot_range_.size(); ++i) {
                final ItemStack slotstack = storage_slot_range_.get(i);
                if((first_empty_slot < 0) && slotstack.isEmpty()) { first_empty_slot=i; continue; }
                if(Inventories.areItemStacksDifferent(stack, slotstack)) continue;
                int nspace = slotstack.getMaxStackSize() - slotstack.getCount();
                if(nspace <= 0) {
                    continue;
                } else if(nspace >= n_to_insert) {
                    slotstack.grow(n_to_insert);
                    n_to_insert = 0;
                    break;
                } else {
                    slotstack.grow(nspace);
                    n_to_insert -= nspace;
                }
            }
            if((n_to_insert > 0) && (first_empty_slot >= 0)) {
                ItemStack new_stack = stack.copy();
                new_stack.setCount(n_to_insert);
                storage_slot_range_.set(first_empty_slot, new_stack);
                n_to_insert = 0;
            }
            return max_to_insert - n_to_insert;
        }

        private boolean try_insert(Direction facing)
        {
            ItemStack current_stack = ItemStack.EMPTY;
            for(int i=0; i<NUM_OF_STORAGE_SLOTS; ++i) {
                if(current_slot_index_ >= NUM_OF_STORAGE_SLOTS) current_slot_index_ = 0;
                current_stack = storage_slot_range_.get(current_slot_index_);
                if(!current_stack.isEmpty()) break;
                current_slot_index_ = next_slot(current_slot_index_);
            }
            if(current_stack.isEmpty()) {
                current_slot_index_ = 0;
                return false;
            }
            final BlockPos facing_pos = worldPosition.relative(facing);
            IItemHandler ih = null;
            // 方块实体插入检查
            {
                ih = level.getCapability(Capabilities.ItemHandler.BLOCK, facing_pos, facing.getOpposite());
                if(ih == null) { delay_timer_ = TICK_INTERVAL+2; return false; }
                final BlockState target_state = level.getBlockState(facing_pos);
                if(target_state.getBlock() instanceof net.minecraft.world.level.block.HopperBlock) {
                    Direction f = target_state.getValue(net.minecraft.world.level.block.HopperBlock.FACING);
                    if(f==facing.getOpposite()) return false; // 无反向传输
                } else if(target_state.getBlock() instanceof EdHopper.HopperBlock) {
                    Direction f = target_state.getValue(EdHopper.HopperBlock.FACING);
                    if(f==facing.getOpposite()) return false;
                }
            }
            // 实体插入检查
            if(ih == null) ih = inventory_entity_handler(facing_pos);
            if(ih == null) { delay_timer_ = TICK_INTERVAL+2; return false; }
            // 处理器插入
            {
                ItemStack insert_stack = current_stack.copy();
                if(insert_stack.getCount() > transfer_count_) insert_stack.setCount(transfer_count_);
                final int initial_insert_stack_size = insert_stack.getCount();
                if((ih == null) || ih.getSlots() <= 0) return false;
                // 首先尝试补满已存在物品的槽位
                for(int i=0; i<ih.getSlots(); ++i) {
                    final ItemStack target_stack = ih.getStackInSlot(i);
                    if(Inventories.areItemStacksDifferent(target_stack, insert_stack)) continue;
                    insert_stack = ih.insertItem(i, insert_stack.copy(), false);
                    if(insert_stack.isEmpty()) break;
                }
                // 然后尝试插入首个可用槽位
                if(!insert_stack.isEmpty()) {
                    for(int i=0; i<ih.getSlots(); ++i) {
                        insert_stack = ih.insertItem(i, insert_stack.copy(), false);
                        if(insert_stack.isEmpty()) break;
                    }
                }
                final int num_inserted = initial_insert_stack_size-insert_stack.getCount();
                if(num_inserted > 0) {
                    current_stack.shrink(num_inserted);
                    storage_slot_range_.set(current_slot_index_, current_stack);
                }
                if(!insert_stack.isEmpty()) current_slot_index_ = next_slot(current_slot_index_);
                return (num_inserted > 0);
            }
        }

        private boolean try_item_handler_extract(final IItemHandler ih)
        {
            final int end = ih.getSlots();
            int n_to_extract = transfer_count_;
            for(int i=0; i<end; ++i) {
                if(ih.getStackInSlot(i).isEmpty()) continue;
                ItemStack stack = ih.extractItem(i, n_to_extract, true);
                if(stack.isEmpty()) continue;
                int n_accepted = try_insert_into_hopper(stack);
                if(n_accepted > 0) {
                    ItemStack test = ih.extractItem(i, n_accepted, false);
                    n_to_extract -= n_accepted;
                    if(n_to_extract <= 0) break;
                }
            }
            return (n_to_extract < transfer_count_);
        }

        private boolean try_inventory_extract(final Container inv)
        {
            final int end = inv.getContainerSize();
            int n_to_extract = transfer_count_;
            for(int i=0; i<end; ++i) {
                ItemStack stack = inv.getItem(i).copy();
                if(stack.isEmpty()) continue;
                int n_accepted = try_insert_into_hopper(stack);
                if(n_accepted > 0) {
                    stack.shrink(n_accepted);
                    n_to_extract -= n_accepted;
                    if(stack.isEmpty()) stack = ItemStack.EMPTY;
                    inv.setItem(i, stack);
                    if(n_to_extract <= 0) break;
                }
            }
            if(n_to_extract < transfer_count_) {
                inv.setChanged();
                return true;
            } else {
                return false;
            }
        }

        private boolean try_collect(Direction facing)
        {
            AABB collection_volume;
            Vec3 rpos;
            if(facing==Direction.UP)  {
                rpos = new Vec3(0.5+worldPosition.getX(),1.5+worldPosition.getY(),0.5+worldPosition.getZ());
                collection_volume = (new AABB(worldPosition.above())).inflate(0.1+collection_range_, 0.6, 0.1+collection_range_);
            } else {
                rpos = new Vec3(0.5+worldPosition.getX(),-1.5+worldPosition.getY(),0.5+worldPosition.getZ());
                collection_volume = (new AABB(worldPosition.below(2))).inflate(0.1+collection_range_, 1, 0.1+collection_range_);
            }
            final List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, collection_volume, e->(e.isAlive() && e.onGround()));
            if(items.size() <= 0) return false;
            final int max_to_collect = 3;
            int n_collected = 0;
            for(ItemEntity ie:items) {
                boolean is_direct_collection_tange = ie.distanceToSqr(rpos)<0.7;
                if(!is_direct_collection_tange && (ie.hasPickUpDelay())) continue;
                ItemStack stack = ie.getItem();
                if(stack.isEmpty()) continue;
                int n_accepted = try_insert_into_hopper(stack);
                if(n_accepted <= 0) continue;
                if(n_accepted >= stack.getCount()) {
                    stack.setCount(0);
                    ie.setItem(stack);
                    ie.remove(Entity.RemovalReason.DISCARDED);
                } else {
                    stack.shrink(n_accepted);
                    ie.setItem(stack);
                }
                if((!is_direct_collection_tange) && (++n_collected >= max_to_collect)) break;
            }
            return (n_collected > 0);
        }

        @Override
        public void tick()
        {
            if(level.isClientSide) return;
            if((delay_timer_ > 0) && ((--delay_timer_) == 0)) setChanged();
            if(--tick_timer_ > 0) return;
            tick_timer_ = TICK_INTERVAL;
            boolean dirty = block_power_updated_;
            final boolean rssignal = ((logic_ & LOGIC_IGNORE_EXT)!=0) || ((logic_ & LOGIC_INVERTED)!=0)==(!block_power_signal_);
            final boolean pulse_mode = ((logic_ & (LOGIC_CONTINUOUS|LOGIC_IGNORE_EXT))==0);
            boolean trigger = ((logic_ & LOGIC_IGNORE_EXT)!=0) || (rssignal && ((block_power_updated_) || (!pulse_mode)));
            final BlockState state = level.getBlockState(worldPosition);
            if(!(state.getBlock() instanceof HopperBlock)) { block_power_signal_= false; return; }
            final Direction hopper_facing = state.getValue(HopperBlock.FACING);
            {
                boolean tr = level.hasNeighborSignal(worldPosition);
                block_power_updated_ = (block_power_signal_ != tr);
                block_power_signal_ = tr;
                if(block_power_updated_) dirty = true;
            }
            if(rssignal || pulse_mode) {
                Direction hopper_input_facing = (hopper_facing==Direction.UP) ? Direction.DOWN : Direction.UP;
                BlockEntity te = level.getBlockEntity(worldPosition.relative(hopper_input_facing));
                IItemHandler ih = (te==null) ? null : level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(hopper_input_facing), hopper_input_facing.getOpposite());
                if((ih != null) || (te instanceof WorldlyContainer)) {
                    if((ih != null)) {
                        if(try_item_handler_extract(ih)) dirty = true;
                    } else {
                        if(try_inventory_extract((WorldlyContainer)te)) dirty = true;
                    }
                }
                if(ih==null) {
                    ih = inventory_entity_handler(worldPosition.relative(hopper_input_facing));
                    if((ih!=null) && (try_item_handler_extract(ih))) dirty = true;
                }
                if((ih==null) && (collection_timer_ -= TICK_INTERVAL) <= 0) {
                    collection_timer_ = COLLECTION_INTERVAL;
                    if(try_collect(hopper_input_facing)) dirty = true;
                }
            }
            if(trigger && (delay_timer_ <= 0)) {
                delay_timer_ = PERIOD_OFFSET + transfer_period_ * 2;
                if(try_insert(hopper_facing)) dirty = true;
            }
            if(dirty) setChanged();
            if(trigger && (tick_timer_ > TICK_INTERVAL)) tick_timer_ = TICK_INTERVAL;
        }
    }

    //--------------------------------------------------------------------------------------------------------------------
    // 容器
    //--------------------------------------------------------------------------------------------------------------------

    public static class HopperContainer extends AbstractContainerMenu implements Networking.INetworkSynchronisableContainer
    {
        protected static final String QUICK_MOVE_ALL = "quick-move-all";
        private static final int PLAYER_INV_START_SLOTNO = HopperTileEntity.NUM_OF_SLOTS;
        private static final int NUM_OF_CONTAINER_SLOTS = HopperTileEntity.NUM_OF_SLOTS + 36;
        protected static final int STORAGE_SLOT_BEGIN = 0;
        protected static final int STORAGE_SLOT_END = HopperTileEntity.NUM_OF_SLOTS;
        protected static final int PLAYER_SLOT_BEGIN = HopperTileEntity.NUM_OF_SLOTS;
        protected static final int PLAYER_SLOT_END = HopperTileEntity.NUM_OF_SLOTS+36;
        private final Inventories.InventoryRange player_inventory_range_;
        private final Inventories.InventoryRange block_storage_range_;
        private final Player player_;
        private final Container inventory_;
        private final ContainerLevelAccess wpc_;
        private final ContainerData fields_;

        public final int field(int index) { return fields_.get(index); }

        public HopperContainer(int cid, Inventory player_inventory)
        { this(cid, player_inventory, new SimpleContainer(HopperTileEntity.NUM_OF_SLOTS), ContainerLevelAccess.NULL, new SimpleContainerData(HopperTileEntity.NUM_OF_FIELDS)); }

        private HopperContainer(int cid, Inventory player_inventory, Container block_inventory, ContainerLevelAccess wpc, ContainerData fields)
        {
            super(ModContent.getMenuType("factory_hopper"), cid);
            fields_ = fields;
            wpc_ = wpc;
            player_ = player_inventory.player;
            inventory_ = block_inventory;
            block_storage_range_ = new Inventories.InventoryRange(inventory_, 0, HopperTileEntity.NUM_OF_SLOTS);
            player_inventory_range_ = Inventories.InventoryRange.fromPlayerInventory(player_);
            int i=-1;
            for(int y=0; y<3; ++y) {
                for(int x=0; x<6; ++x) {
                    int xpos = 11+x*18, ypos = 9+y*17;
                    addSlot(new Slot(inventory_, ++i, xpos, ypos));
                }
            }
            for(int x=0; x<9; ++x) {
                addSlot(new Slot(player_inventory, x, 8+x*18, 129));
            }
            for(int y=0; y<3; ++y) {
                for(int x=0; x<9; ++x) {
                    addSlot(new Slot(player_inventory, x+y*9+9, 8+x*18, 71+y*18));
                }
            }
            this.addDataSlots(fields_);
        }

        @Override
        public boolean stillValid(Player player)
        { return inventory_.stillValid(player); }

        @Override
        public ItemStack quickMoveStack(Player player, int index)
        {
            Slot slot = getSlot(index);
            if((slot==null) || (!slot.hasItem())) return ItemStack.EMPTY;
            ItemStack slot_stack = slot.getItem();
            ItemStack transferred = slot_stack.copy();
            if((index>=0) && (index<PLAYER_INV_START_SLOTNO)) {
                if(!moveItemStackTo(slot_stack, PLAYER_INV_START_SLOTNO, PLAYER_INV_START_SLOTNO+36, false)) return ItemStack.EMPTY;
            } else if((index >= PLAYER_INV_START_SLOTNO) && (index <= PLAYER_INV_START_SLOTNO+36)) {
                if(!moveItemStackTo(slot_stack, 0, HopperTileEntity.NUM_OF_SLOTS, false)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }
            if(slot_stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if(slot_stack.getCount() == transferred.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, slot_stack);
            return transferred;
        }

        // INetworkSynchronisableContainer ---------------------------------------------------------

        @OnlyIn(Dist.CLIENT)
        public void onGuiAction(CompoundTag nbt)
        { EdNetworking.sendContainerSync(containerId, nbt);; }

        @OnlyIn(Dist.CLIENT)
        public void onGuiAction(String key, int value)
        {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt(key, value);
            EdNetworking.sendContainerSync(containerId, nbt);;
        }

        @OnlyIn(Dist.CLIENT)
        public void onGuiAction(String message, CompoundTag nbt)
        {
            nbt.putString("action", message);
            EdNetworking.sendContainerSync(containerId, nbt);
        }

        @Override
        public void onServerPacketReceived(int windowId, CompoundTag nbt)
        {}

        @Override
        public void onClientPacketReceived(int windowId, Player player, CompoundTag nbt)
        {
            if(!(inventory_ instanceof Inventories.StorageInventory)) return;
            if(!((((((Inventories.StorageInventory)inventory_).getBlockEntity())) instanceof final EdHopper.HopperTileEntity te))) return;
            if(nbt.contains("xsize")) te.transfer_count_  = Mth.clamp(nbt.getInt("xsize"), 1, HopperTileEntity.MAX_TRANSFER_COUNT);
            if(nbt.contains("period")) te.transfer_period_ = Mth.clamp(nbt.getInt("period"),   0,  100);
            if(nbt.contains("range")) te.collection_range_ = Mth.clamp(nbt.getInt("range"),   0,  HopperTileEntity.MAX_COLLECTION_RANGE);
            if(nbt.contains("logic")) te.logic_  = nbt.getInt("logic");
            if(nbt.contains("manual_trigger") && (nbt.getInt("manual_trigger")!=0)) { te.block_power_signal_=true; te.block_power_updated_=true; te.tick_timer_=1; }
            if(nbt.contains("action")) {
                boolean changed = false;
                final int slotId = nbt.contains("slot") ? nbt.getInt("slot") : -1;
                switch (nbt.getString("action")) {
                    case QUICK_MOVE_ALL -> {
                        if ((slotId >= STORAGE_SLOT_BEGIN) && (slotId < STORAGE_SLOT_END) && (getSlot(slotId).hasItem())) {
                            changed = block_storage_range_.move(getSlot(slotId).getSlotIndex(), player_inventory_range_, true, false, true, true);
                        } else if ((slotId >= PLAYER_SLOT_BEGIN) && (slotId < PLAYER_SLOT_END) && (getSlot(slotId).hasItem())) {
                            changed = player_inventory_range_.move(getSlot(slotId).getSlotIndex(), block_storage_range_, true, false, false, true);
                        }
                    }
                }
                if(changed) {
                    inventory_.setChanged();
                    player.getInventory().setChanged();
                    broadcastChanges();
                }
            }
            te.setChanged();
        }
    }

    //--------------------------------------------------------------------------------------------------------------------
    // GUI
    //--------------------------------------------------------------------------------------------------------------------

    @OnlyIn(Dist.CLIENT)
    public static class HopperGui extends Guis.ContainerGui<HopperContainer>
    {
        public HopperGui(HopperContainer container, Inventory player_inventory, Component title)
        { super(container, player_inventory, title, "textures/gui/factory_hopper_gui.png"); }

        @Override
        public void init()
        {
            super.init();
            {
                final Block block = ModContent.getBlock(Auxiliaries.getResourceLocation(getMenu().getType()).getPath().replaceAll("^ct_",""));
                final String prefix = block.getDescriptionId() + ".tooltips.";
                final int x0 = getGuiLeft(), y0 = getGuiTop();
                tooltip_.init(
                        new TooltipDisplay.TipRange(x0+148, y0+22,  3,  3, Component.translatable(prefix + "delayindicator")),
                        new TooltipDisplay.TipRange(x0+130, y0+ 9, 40, 10, Component.translatable(prefix + "range")),
                        new TooltipDisplay.TipRange(x0+130, y0+22, 40, 10, Component.translatable(prefix + "period")),
                        new TooltipDisplay.TipRange(x0+130, y0+35, 40, 10, Component.translatable(prefix + "count")),
                        new TooltipDisplay.TipRange(x0+133, y0+49,  9,  9, Component.translatable(prefix + "rssignal")),
                        new TooltipDisplay.TipRange(x0+145, y0+49,  9,  9, Component.translatable(prefix + "inversion")),
                        new TooltipDisplay.TipRange(x0+159, y0+49,  9,  9, Component.translatable(prefix + "triggermode"))
                );
            }
        }

        @Override
        protected void renderBgWidgets(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY)
        {
            final int x0=getGuiLeft(), y0=getGuiTop(), w=getXSize(), h=getYSize();
            final ResourceLocation bg = this.background_image_;
            HopperContainer container = getMenu();
            // 当前活跃槽位
            {
                int slot_index = container.field(6);
                if((slot_index < 0) || (slot_index >= HopperTileEntity.NUM_OF_SLOTS)) slot_index = 0;
                int x = (x0+10+((slot_index % 6) * 18));
                int y = (y0+8+((slot_index / 6) * 17));
                graphics.blit(bg, x, y, 200, 8, 18, 18);
            }
            // 收集范围
            {
                int[] lut = { 133, 141, 149, 157, 166 };
                int px = lut[Mth.clamp(container.field(0), 0, HopperTileEntity.MAX_COLLECTION_RANGE)];
                int x = x0 + px - 2;
                int y = y0 + 14;
                graphics.blit(bg, x, y, 179, 40, 5, 5);
            }
            // 传输周期
            {
                int px = (int)Math.round(((33.5 * container.field(3)) / 100) + 1);
                int x = x0 + 132 - 2 + Mth.clamp(px, 0, 34);
                int y = y0 + 27;
                graphics.blit(bg, x, y, 179, 40, 5, 5);
            }
            // 传输数量
            {
                int x = x0 + 133 - 2 + (container.field(1));
                int y = y0 + 40;
                graphics.blit(bg, x, y, 179, 40, 5, 5);
            }
            // 红石输入
            {
                if(container.field(5) != 0) {
                    graphics.blit(bg, x0+133, y0+49, 217, 49, 9, 9);
                }
            }
            // 触发逻辑
            {
                int inverter_offset_x = ((container.field(2) & HopperTileEntity.LOGIC_INVERTED) != 0) ? 11 : 0;
                int inverter_offset_y = ((container.field(2) & HopperTileEntity.LOGIC_IGNORE_EXT) != 0) ? 10 : 0;
                graphics.blit(bg, x0+145, y0+49, 177+inverter_offset_x, 49+inverter_offset_y, 9, 9);
                int pulse_mode_offset  = ((container.field(2) & HopperTileEntity.LOGIC_CONTINUOUS    ) != 0) ? 9 : 0;
                graphics.blit(bg, x0+159, y0+49, 199+pulse_mode_offset, 49, 9, 9);
            }
            // 延迟计时器运行指示器
            {
                if((container.field(4) > HopperTileEntity.PERIOD_OFFSET) && ((System.currentTimeMillis() % 1000) < 500)) {
                    graphics.blit(bg, x0+148, y0+22, 187, 22, 3, 3);
                }
            }
        }

        @Override
        protected void slotClicked(Slot slot, int slotId, int button, ClickType type)
        {
            tooltip_.resetTimer();
            if((type == ClickType.QUICK_MOVE) && (slot!=null) && slot.hasItem() && Auxiliaries.isShiftDown() && Auxiliaries.isCtrlDown()) {
                CompoundTag nbt = new CompoundTag();
                nbt.putInt("slot", slotId);
                menu.onGuiAction(HopperContainer.QUICK_MOVE_ALL, nbt);
            } else {
                super.slotClicked(slot, slotId, button, type);
            }
        }

        @Override
        protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
            int i = getGuiLeft();   // 替代 this.leftPos
            int j = getGuiTop();    // 替代 this.topPos
            mouseX -= i;
            mouseY -= j;
            return mouseX >= (x - 1) && mouseX < (x + width + 1) && mouseY >= (y - 1) && mouseY < (y + height + 1);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int mouseButton)
        {
            tooltip_.resetTimer();
            HopperContainer container = getMenu();
            int mx = (int)(mouseX - getGuiLeft() + .5);
            int my = (int)(mouseY - getGuiTop() + .5);
            if((!isHovering(126, 1, 49, 60, mouseX, mouseY))) {
                return super.mouseClicked(mouseX, mouseY, mouseButton);
            }
            else if(isHovering(128, 9, 44, 10, mouseX, mouseY)) {
                int range = (mx-133);
                if(range < -1) {
                    range = container.field(0) - 1; // 减小
                } else if(range >= 34) {
                    range = container.field(0) + 1; // 增大
                } else {
                    range = (int)(0.5 + ((((double)HopperTileEntity.MAX_COLLECTION_RANGE) * range)/34));
                    range = Mth.clamp(range, 0, HopperTileEntity.MAX_COLLECTION_RANGE);
                }
                container.onGuiAction("range", range);
            }
            else if(isHovering(128, 21, 44, 10, mouseX, mouseY)) {
                int period = (mx-133);
                if(period < -1) {
                    period = container.field(3) - 3; // 减小
                } else if(period >= 35) {
                    period = container.field(3) + 3; // 增大
                } else {
                    period = (int)(0.5 + ((100.0 * period)/34));
                }
                period = Mth.clamp(period, 0, 100);
                container.onGuiAction("period", period);
            }
            else if(isHovering(128, 34, 44, 10, mouseX, mouseY)) {
                int ndrop = (mx-134);
                if(ndrop < -1) {
                    ndrop = container.field(1) - 1; // 减小
                } else if(ndrop >= 34) {
                    ndrop = container.field(1) + 1; // 增大
                } else {
                    ndrop = Mth.clamp(1+ndrop, 1, HopperTileEntity.MAX_TRANSFER_COUNT);
                }
                container.onGuiAction("xsize", ndrop);
            }
            else if(isHovering(133, 49, 9, 9, mouseX, mouseY)) {
                container.onGuiAction("manual_trigger", 1);
            }
            else if(isHovering(145, 49, 9, 9, mouseX, mouseY)) {
                final int mask = (HopperTileEntity.LOGIC_INVERTED|HopperTileEntity.LOGIC_IGNORE_EXT|HopperTileEntity.LOGIC_NOT_INVERTED);
                final int logic = switch (container.field(2) & mask) {
                    case HopperTileEntity.LOGIC_NOT_INVERTED -> HopperTileEntity.LOGIC_INVERTED;
                    case HopperTileEntity.LOGIC_INVERTED -> HopperTileEntity.LOGIC_IGNORE_EXT;
                    case HopperTileEntity.LOGIC_IGNORE_EXT -> HopperTileEntity.LOGIC_NOT_INVERTED;
                    default -> HopperTileEntity.LOGIC_IGNORE_EXT;
                };
                container.onGuiAction("logic", (container.field(2) & (~mask)) | logic);
            }
            else if(isHovering(159, 49, 7, 9, mouseX, mouseY)) {
                container.onGuiAction("logic", container.field(2) ^ HopperTileEntity.LOGIC_CONTINUOUS);
            }
            return true;
        }
    }
}