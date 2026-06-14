/*
 * @file EdPlacer.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Block placer and planter, factory automation suitable.
 */
package wile.engineersdecor.blocks;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;
import wile.engineersdecor.ModContent;
import wile.engineersdecor.libmc.*;
import wile.engineersdecor.network.EdNetworking;

import javax.annotation.Nullable;
import java.util.*;

public class EdPlacer
{
    public static void on_config()
    {}

    //--------------------------------------------------------------------------------------------------------------------
    // Block
    //--------------------------------------------------------------------------------------------------------------------

    public static class PlacerBlock extends StandardBlocks.Directed implements StandardEntityBlocks.IStandardEntityBlock<PlacerTileEntity>
    {
        public PlacerBlock(long config, BlockBehaviour.Properties builder, final AABB[] unrotatedAABB)
        { super(config, builder, unrotatedAABB); }

        @Override
        public boolean isBlockEntityTicking(Level world, BlockState state)
        { return true; }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext selectionContext)
        { return Shapes.block(); }

        @Override
        @SuppressWarnings("deprecation")
        public boolean hasAnalogOutputSignal(BlockState state)
        { return true; }

        @Override
        @SuppressWarnings("deprecation")
        public int getAnalogOutputSignal(BlockState blockState, Level world, BlockPos pos)
        { return (world.getBlockEntity(pos) instanceof EdPlacer.PlacerTileEntity te) ? RsSignals.fromContainer(te.inventory_) : 0; }

        @Override
        public boolean shouldCheckWeakPower(BlockState state, SignalGetter level, BlockPos pos, Direction side)
        { return false; }

        @Override
        public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack)
        {
            if(world.isClientSide) return;
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if(data == null) return;
            CompoundTag tag = data.copyTag();
            if(!tag.contains("tedata")) return;
            CompoundTag te_nbt = tag.getCompound("tedata");
            if(te_nbt.isEmpty()) return;
            if(!(world.getBlockEntity(pos) instanceof final PlacerTileEntity te)) return;
            te.readnbt(te_nbt, false);
            te.reset_rtstate();
            te.setChanged();
        }

        @Override
        public boolean hasDynamicDropList()
        { return true; }

        @Override
        public List<ItemStack> dropList(BlockState state, Level world, final BlockEntity te, boolean explosion)
        {
            final List<ItemStack> stacks = new ArrayList<>();
            if(world.isClientSide) return stacks;
            if(!(te instanceof PlacerTileEntity placer)) return stacks;
            if(!explosion) {
                ItemStack stack = new ItemStack(this);
                CompoundTag te_nbt = placer.clear_getnbt();
                if(!te_nbt.isEmpty()) {
                    CompoundTag nbt = new CompoundTag();
                    nbt.put("tedata", te_nbt);
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                }
                stacks.add(stack);
            } else {
                for(ItemStack s : placer.inventory_) {
                    if(!s.isEmpty()) stacks.add(s);
                }
                placer.reset_rtstate();
            }
            return stacks;
        }

        @Override
        public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
            return useOpenGui(state, world, pos, player);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean unused)
        {
            if(world.isClientSide) return;
            BlockEntity te = world.getBlockEntity(pos);
            if(!(te instanceof PlacerTileEntity)) return;
            ((PlacerTileEntity)te).block_updated();
        }

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
    // Tile entity
    //--------------------------------------------------------------------------------------------------------------------

    public static class PlacerTileEntity extends StandardEntityBlocks.StandardBlockEntity implements MenuProvider, Nameable
    {
        public static final int TICK_INTERVAL = 40;
        public static final int NUM_OF_SLOTS = 18;
        public static final int NUM_OF_FIELDS = 3;
        public static final int LOGIC_NOT_INVERTED = 0x00;
        public static final int LOGIC_INVERTED     = 0x01;
        public static final int LOGIC_CONTINUOUS   = 0x02;
        public static final int LOGIC_IGNORE_EXT   = 0x04;
        ///
        private boolean block_power_signal_ = false;
        private boolean block_power_updated_ = false;
        private int logic_ = LOGIC_IGNORE_EXT|LOGIC_CONTINUOUS;
        private int current_slot_index_ = 0;
        private int tick_timer_ = 0;
        private final Inventories.StorageInventory inventory_ = new Inventories.StorageInventory(this, NUM_OF_SLOTS, 1);
        public final IItemHandler item_handler_;  // exposed via EdCapabilities

        public PlacerTileEntity(BlockPos pos, BlockState state)
        {
            super(ModContent.getBlockEntityTypeOfBlock(state.getBlock()), pos, state);
            item_handler_ = Inventories.MappedItemHandler.createGenericHandler(inventory_,
                    (stack, slot) -> true,
                    (stack, slot) -> true
            ).orElse(null);

        }

        public CompoundTag clear_getnbt()
        {
            CompoundTag nbt = new CompoundTag();
            writenbt(nbt, false);
            inventory_.clearContent();
            reset_rtstate();
            block_power_updated_ = false;
            return nbt;
        }

        public void reset_rtstate()
        {
            block_power_signal_ = false;
            block_power_updated_ = false;
        }

        public void readnbt(CompoundTag nbt, boolean update_packet)
        {
            inventory_.load(nbt);
            block_power_signal_ = nbt.getBoolean("powered");
            current_slot_index_ = nbt.getInt("act_slot_index");
            logic_ = nbt.getInt("logic");
        }

        protected void writenbt(CompoundTag nbt, boolean update_packet)
        {
            inventory_.save(nbt);
            nbt.putBoolean("powered", block_power_signal_);
            nbt.putInt("act_slot_index", current_slot_index_);
            nbt.putInt("logic", logic_);
        }

        public void block_updated()
        {
            boolean powered = level.hasNeighborSignal(worldPosition);
            if(block_power_signal_ != powered) block_power_updated_ = true;
            block_power_signal_ = powered;
            if(block_power_updated_) {
                tick_timer_ = 1;
            } else if(tick_timer_ > 4) {
                tick_timer_ = 4;
            }
        }

        // BlockEntity ------------------------------------------------------------------------------

        @Override
        public void load(CompoundTag nbt, HolderLookup.Provider provider)
        { super.load(nbt, provider); readnbt(nbt, false); }

        @Override
        protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider provider)
        { super.saveAdditional(nbt, provider); writenbt(nbt, false); }

        // Namable -----------------------------------------------------------------------------------------------

        @Override
        public Component getName()
        { return Auxiliaries.localizable(getBlockState().getBlock().getDescriptionId()); }

        @Override
        public boolean hasCustomName()
        { return false; }

        @Override
        public Component getCustomName()
        { return getName(); }

        // INamedContainerProvider ------------------------------------------------------------------------------

        @Override
        public Component getDisplayName()
        { return Nameable.super.getDisplayName(); }

        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player )
        { return new PlacerContainer(id, inventory, inventory_, ContainerLevelAccess.create(level, worldPosition), fields); }

        // Fields -----------------------------------------------------------------------------------------------

        protected final ContainerData fields = new ContainerData()
        {
            @Override
            public int getCount()
            { return PlacerTileEntity.NUM_OF_FIELDS; }

            @Override
            public int get(int id)
            {
                return switch (id) {
                    case 0 -> logic_;
                    case 1 -> block_power_signal_ ? 1 : 0;
                    case 2 -> Math.min(current_slot_index_, NUM_OF_SLOTS - 1);
                    default -> 0;
                };
            }
            @Override
            public void set(int id, int value)
            {
                switch (id) {
                    case 0 -> logic_ = value;
                    case 1 -> block_power_signal_ = (value != 0);
                    case 2 -> current_slot_index_ = Math.min(value, NUM_OF_SLOTS - 1);
                }
            }
        };

        // ITickable and aux methods ----------------------------------------------------------------------------

        private static int next_slot(int i)
        { return (i<NUM_OF_SLOTS-1) ? (i+1) : 0; }

        private boolean spit_out(Direction facing)
        { return spit_out(facing, false); }

        private boolean spit_out(Direction facing, boolean all)
        {
            ItemStack stack = inventory_.getItem(current_slot_index_);
            ItemStack drop = stack.copy();
            if(!all) {
                stack.shrink(1);
                inventory_.setItem(current_slot_index_, stack);
                drop.setCount(1);
            } else {
                inventory_.setItem(current_slot_index_, ItemStack.EMPTY);
            }
            for(int i=0; i<8; ++i) {
                BlockPos p = worldPosition.relative(facing, i);
                if(!level.isEmptyBlock(p)) continue;
                level.addFreshEntity(new ItemEntity(level, (p.getX()+0.5), (p.getY()+0.5), (p.getZ()+0.5), drop));
                level.playSound(null, p, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.7f, 0.8f);
                break;
            }
            return true;
        }

        private boolean try_place(Direction facing, boolean triggered) {
            // 仅服务端执行
            if (level.isClientSide()) return false;

            BlockPos placement_pos = worldPosition.relative(facing);

            // 目标位置已有方块实体则放弃
            if (level.getBlockEntity(placement_pos) != null) return false;

            // 从库存中选择第一个非空物品
            ItemStack current_stack = ItemStack.EMPTY;
            for (int i = 0; i < NUM_OF_SLOTS; ++i) {
                if (current_slot_index_ >= NUM_OF_SLOTS) current_slot_index_ = 0;
                current_stack = inventory_.getItem(current_slot_index_);
                if (!current_stack.isEmpty()) break;
                current_slot_index_ = next_slot(current_slot_index_);
            }

            // 库存为空
            if (current_stack.isEmpty()) {
                current_slot_index_ = 0;
                return false;
            }

            // 非方块物品直接喷出
            if (!(current_stack.getItem() instanceof BlockItem blockItem)) {
                return spit_out(facing);
            }

            // 可替换性检查（不再尝试向上偏移）
            BlockState existingState = level.getBlockState(placement_pos);
            boolean replaceable = existingState.canBeReplaced()
                    || level.isEmptyBlock(placement_pos)
                    || existingState.getFluidState().is(Fluids.WATER);
            if (!replaceable) {
                advanceToNextNonEmptySlot(); // 切换到下一个物品
                return false;
            }

            // 实体碰撞检查
            List<Entity> entities = level.getEntitiesOfClass(Entity.class, new AABB(placement_pos),
                    e -> e.isPickable() && !(e instanceof ItemEntity));
            if (!entities.isEmpty()) {
                advanceToNextNonEmptySlot();
                return false;
            }

            // 获取假玩家用于放置
            FakePlayer placer = FakePlayerFactory.getMinecraft((ServerLevel) level);
            if (placer == null) return false;

            ItemStack held = placer.getMainHandItem();
            ItemStack placementStack = current_stack.copy();
            placementStack.setCount(1);
            placer.setItemInHand(InteractionHand.MAIN_HAND, placementStack);

            boolean success = false;
            try {
                // 多方向尝试获取合法放置状态
                BlockPlaceContext useContext = null;
                Direction[] tryDirections = {
                        facing.getOpposite(), // 优先附着面
                        Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
                };
                for (Direction d : tryDirections) {
                    Vec3 hitVec = Vec3.atCenterOf(placement_pos)
                            .subtract(Vec3.atLowerCornerOf(d.getNormal()).scale(0.5));
                    BlockHitResult hit = new BlockHitResult(hitVec, d, placement_pos, false);
                    BlockPlaceContext ctx = new BlockPlaceContext(
                            new UseOnContext(placer, InteractionHand.MAIN_HAND, hit)
                    );
                    if (blockItem.getBlock().getStateForPlacement(ctx) != null) {
                        useContext = ctx;
                        break;
                    }
                }

                if (useContext != null) {
                    InteractionResult result = blockItem.place(useContext);
                    if (result.consumesAction()) {
                        // 放置成功，消耗库存物品
                        current_stack.shrink(1);
                        success = true;
                    }
                }
            } catch (Throwable e) {
                // 异常回退：移除可能错误放置的方块并喷出物品
                Auxiliaries.logger().error("Placer failed to place block: " + e);
                level.removeBlock(placement_pos, false);
                placer.setItemInHand(InteractionHand.MAIN_HAND, held); // 恢复假玩家物品
                return spit_out(facing, true);
            } finally {
                // 无论成功与否，恢复假玩家主手物品
                placer.setItemInHand(InteractionHand.MAIN_HAND, held);
            }

            if (success) {
                // 放置成功：如果当前物品耗尽则自动切到下一个非空槽位
                if (current_stack.isEmpty()) {
                    advanceToNextNonEmptySlot();
                }
                return true;
            } else {
                // 放置失败：切换到下一个槽位（与原有行为一致）
                advanceToNextNonEmptySlot();
                return false;
            }
        }

        // 辅助方法：将槽位推进到下一个非空物品
        private void advanceToNextNonEmptySlot() {
            for (int i = 0; i < NUM_OF_SLOTS; ++i) {
                current_slot_index_ = next_slot(current_slot_index_);
                if (!inventory_.getItem(current_slot_index_).isEmpty()) break;
            }
        }

        @Override
        public void tick()
        {
            if(level.isClientSide) return;
            if(--tick_timer_ > 0) return;
            tick_timer_ = TICK_INTERVAL;
            final BlockState state = level.getBlockState(worldPosition);
            if(!(state.getBlock() instanceof PlacerBlock)) { block_power_signal_= false; return; }
            final boolean updated = block_power_updated_;
            final boolean rssignal = ((logic_ & LOGIC_IGNORE_EXT)!=0) || ((logic_ & LOGIC_INVERTED)!=0)==(!block_power_signal_);
            final boolean trigger = ((logic_ & LOGIC_IGNORE_EXT)!=0) ||  (rssignal && ((updated) || ((logic_ & LOGIC_CONTINUOUS)!=0)));
            final Direction placer_facing = state.getValue(PlacerBlock.FACING);
            boolean dirty = updated;
            {
                boolean tr = level.hasNeighborSignal(worldPosition);
                block_power_updated_ = (block_power_signal_ != tr);
                block_power_signal_ = tr;
                if(block_power_updated_) dirty = true;
            }
            if(trigger && try_place(placer_facing, rssignal && updated)) dirty = true;
            if(dirty) setChanged();
            if(trigger && (tick_timer_ > TICK_INTERVAL)) tick_timer_ = TICK_INTERVAL;
        }
    }

    //--------------------------------------------------------------------------------------------------------------------
    // Container
    //--------------------------------------------------------------------------------------------------------------------

    public static class PlacerContainer extends AbstractContainerMenu implements Networking.INetworkSynchronisableContainer
    {
        protected static final String QUICK_MOVE_ALL = "quick-move-all";
        private static final int PLAYER_INV_START_SLOTNO = PlacerTileEntity.NUM_OF_SLOTS;
        private final Player player_;
        private final Container inventory_;
        private final ContainerData fields_;
        private final Inventories.InventoryRange player_inventory_range_;
        private final Inventories.InventoryRange block_storage_range_;

        public final int field(int index) { return fields_.get(index); }

        public PlacerContainer(int cid, Inventory player_inventory)
        { this(cid, player_inventory, new SimpleContainer(PlacerTileEntity.NUM_OF_SLOTS), ContainerLevelAccess.NULL, new SimpleContainerData(PlacerTileEntity.NUM_OF_FIELDS)); }

        private PlacerContainer(int cid, Inventory player_inventory, Container block_inventory, ContainerLevelAccess wpc, ContainerData fields)
        {
            super(ModContent.getMenuType("factory_placer"), cid);
            fields_ = fields;
            player_ = player_inventory.player;
            inventory_ = block_inventory;
            block_storage_range_ = new Inventories.InventoryRange(inventory_, 0, PlacerTileEntity.NUM_OF_SLOTS);
            player_inventory_range_ = Inventories.InventoryRange.fromPlayerInventory(player_);
            int i=-1;
            for(int y=0; y<3; ++y) {
                for(int x=0; x<6; ++x) {
                    int xpos = 11+x*18, ypos = 9+y*17;
                    addSlot(new Slot(inventory_, ++i, xpos, ypos));
                }
            }
            for(int x=0; x<9; ++x) {
                addSlot(new Slot(player_inventory, x, 9+x*18, 129));
            }
            for(int y=0; y<3; ++y) {
                for(int x=0; x<9; ++x) {
                    addSlot(new Slot(player_inventory, x+y*9+9, 9+x*18, 71+y*18));
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
                if(!moveItemStackTo(slot_stack, 0, PlacerTileEntity.NUM_OF_SLOTS, false)) return ItemStack.EMPTY;
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
        { EdNetworking.sendContainerSync(containerId, nbt); }

        @OnlyIn(Dist.CLIENT)
        public void onGuiAction(String key, int value)
        {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt(key, value);
            EdNetworking.sendContainerSync(containerId, nbt);
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
            System.out.println("[SERVER] Received packet: " + nbt);
            if(!(inventory_ instanceof Inventories.StorageInventory)) return;
            if(!((((Inventories.StorageInventory)inventory_).getBlockEntity()) instanceof PlacerTileEntity te)) return;
            if(nbt.contains("action")) {
                final int slotId = nbt.contains("slot") ? nbt.getInt("slot") : -1;
                boolean changed = false;
                if(nbt.getString("action").equals(QUICK_MOVE_ALL)) {
                    if ((slotId >= 0) && (slotId < PLAYER_INV_START_SLOTNO) && (getSlot(slotId).hasItem())) {
                        changed = block_storage_range_.move(getSlot(slotId).getSlotIndex(), player_inventory_range_, true, false, true, true);
                    } else if ((slotId >= PLAYER_INV_START_SLOTNO) && (slotId < PLAYER_INV_START_SLOTNO + 36) && (getSlot(slotId).hasItem())) {
                        changed = player_inventory_range_.move(getSlot(slotId).getSlotIndex(), block_storage_range_, true, false, false, true);
                    }
                }
                if(changed) {
                    inventory_.setChanged();
                    player.getInventory().setChanged();
                    broadcastChanges();
                }
            } else {
                if(nbt.contains("logic")) te.logic_ = nbt.getInt("logic");
                if(nbt.contains("manual_trigger") && (nbt.getInt("manual_trigger")!=0)) { te.block_power_signal_=true; te.block_power_updated_=true; te.tick_timer_=1; }
                System.out.println("[SERVER] New logic_ = " + te.logic_);
                te.setChanged();
            }
        }
    }

    //--------------------------------------------------------------------------------------------------------------------
    // GUI
    //--------------------------------------------------------------------------------------------------------------------

    @OnlyIn(Dist.CLIENT)
    public static class PlacerGui extends Guis.ContainerGui<PlacerContainer>
    {
        public PlacerGui(PlacerContainer container, Inventory player_inventory, Component title)
        { super(container, player_inventory, title,"textures/gui/factory_placer_gui.png"); }

        @Override
        public void init()
        {
            super.init();
            {
                final Block block = ModContent.getBlock(Auxiliaries.getResourceLocation(getMenu().getType()).getPath().replaceAll("^ct_",""));
                final String prefix = block.getDescriptionId() + ".tooltips.";
                final int x0 = getGuiLeft(), y0 = getGuiTop();
                tooltip_.init(
                        new TooltipDisplay.TipRange(x0+133, y0+49,  9,  9, Component.translatable(prefix + "rssignal")),
                        new TooltipDisplay.TipRange(x0+145, y0+49,  9,  9, Component.translatable(prefix + "inversion")),
                        new TooltipDisplay.TipRange(x0+159, y0+49,  9,  9, Component.translatable(prefix + "triggermode"))
                );
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int mouseButton)
        {
            tooltip_.resetTimer();
            PlacerContainer container = getMenu();
            if(!isHovering(126, 1, 49, 60, mouseX, mouseY)) {
                return super.mouseClicked(mouseX, mouseY, mouseButton);
            } else if(isHovering(133, 49, 9, 9, mouseX, mouseY)) {
                container.onGuiAction("manual_trigger", 1);
            } else if(isHovering(145, 49, 9, 9, mouseX, mouseY)) {
                final int mask = (PlacerTileEntity.LOGIC_INVERTED|PlacerTileEntity.LOGIC_IGNORE_EXT|PlacerTileEntity.LOGIC_NOT_INVERTED);
                final int logic = switch(container.field(0) & mask) {
                    case PlacerTileEntity.LOGIC_NOT_INVERTED -> PlacerTileEntity.LOGIC_INVERTED;
                    case PlacerTileEntity.LOGIC_INVERTED -> PlacerTileEntity.LOGIC_IGNORE_EXT;
                    case PlacerTileEntity.LOGIC_IGNORE_EXT -> PlacerTileEntity.LOGIC_NOT_INVERTED;
                    default -> PlacerTileEntity.LOGIC_IGNORE_EXT;
                };
                container.onGuiAction("logic", (container.field(0) & (~mask)) | logic);
            } else if(isHovering(159, 49, 7, 9, mouseX, mouseY)) {
                container.onGuiAction("logic", container.field(0) ^ PlacerTileEntity.LOGIC_CONTINUOUS);
            }
            return true;
        }

        @Override
        protected void slotClicked(Slot slot, int slotId, int button, ClickType type)
        {
            tooltip_.resetTimer();
            if((type == ClickType.QUICK_MOVE) && (slot!=null) && slot.hasItem() && Auxiliaries.isShiftDown() && Auxiliaries.isCtrlDown()) {
                CompoundTag nbt = new CompoundTag();
                nbt.putInt("slot", slotId);
                menu.onGuiAction(PlacerContainer.QUICK_MOVE_ALL, nbt);
            } else {
                super.slotClicked(slot, slotId, button, type);
            }
        }

        @Override
        protected void renderBgWidgets(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY)
        {
            final int x0=getGuiLeft(), y0=getGuiTop();
            PlacerContainer container = getMenu();
            ResourceLocation bg = this.background_image_;
            // active slot
            {
                int slot_index = container.field(2);
                if((slot_index < 0) || (slot_index >= PlacerTileEntity.NUM_OF_SLOTS)) slot_index = 0;
                int x = (x0+10+((slot_index % 6) * 18));
                int y = (y0+8+((slot_index / 6) * 17));
                graphics.blit(bg, x, y, 200, 8, 18, 18);
            }
            // redstone input
            if(container.field(1) != 0) {
                graphics.blit(bg, x0+133, y0+49, 217, 49, 9, 9);
            }
            // trigger logic
            {
                int inverter_offset_x = ((container.field(0) & PlacerTileEntity.LOGIC_INVERTED) != 0) ? 11 : 0;
                int inverter_offset_y = ((container.field(0) & PlacerTileEntity.LOGIC_IGNORE_EXT) != 0) ? 10 : 0;
                graphics.blit(bg, x0+145, y0+49, 177+inverter_offset_x, 49+inverter_offset_y, 9, 9);
                int pulse_mode_offset  = ((container.field(0) & PlacerTileEntity.LOGIC_CONTINUOUS) != 0) ? 9 : 0;
                graphics.blit(bg, x0+159, y0+49, 199+pulse_mode_offset, 49, 9, 9);
            }
        }
    }
}