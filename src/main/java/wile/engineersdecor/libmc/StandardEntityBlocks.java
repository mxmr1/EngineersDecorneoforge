package wile.engineersdecor.libmc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.capabilities.BlockCapability;

import javax.annotation.Nullable;
import java.util.Optional;

public class StandardEntityBlocks
{
    public interface IStandardEntityBlock<ET extends StandardBlockEntity> extends EntityBlock
    {
        default boolean isBlockEntityTicking(Level world, BlockState state)
        { return false; }

        default InteractionResult useOpenGui(BlockState state, Level world, BlockPos pos, Player player)
        {
            if(world.isClientSide()) return InteractionResult.SUCCESS;
            final BlockEntity te = world.getBlockEntity(pos);
            if(!(te instanceof MenuProvider) || (player instanceof FakePlayer)) return InteractionResult.FAIL;
            player.openMenu((MenuProvider)te);
            return InteractionResult.CONSUME;
        }

        @Override
        @Nullable
        default BlockEntity newBlockEntity(BlockPos pos, BlockState state)
        {
            BlockEntityType<?> tet = EDRegistries.getBlockEntityTypeOfBlock(state.getBlock());
            return (tet == null) ? null : tet.create(pos, state);
        }

        @Override
        @Nullable
        default <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> te_type)
        {
            return (world.isClientSide || !isBlockEntityTicking(world, state))
                    ? null
                    : (w, p, s, te) -> ((StandardBlockEntity) te).tick();
        }

        @Override
        @Nullable
        default <T extends BlockEntity> GameEventListener getListener(ServerLevel world, T te)
        { return null; }
    }

    // -----------------------------
    // Updated StandardBlockEntity
    // -----------------------------
    public static abstract class StandardBlockEntity extends BlockEntity
    {
        public StandardBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state)
        {
            super(type, pos, state);
        }

        // Новый формат методов чтения/сохранения
        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider)
        {
            super.loadAdditional(tag, provider);
            readnbt(tag, provider);
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider)
        {
            super.saveAdditional(tag, provider);
            writenbt(tag, provider);
        }

        protected void readnbt(CompoundTag tag, HolderLookup.Provider provider) {}
        protected void writenbt(CompoundTag tag, HolderLookup.Provider provider) {}

        public void tick() {}

        protected void load(CompoundTag nbt, HolderLookup.Provider provider) {}

        // -------------------------------------------------------------------------------------------------
        // ✅ NeoForge capability bridge (внутри класса!)
        // -------------------------------------------------------------------------------------------------
        // 重写 BlockEntity 中的 getCapability（返回 T，可空）
        //@Override
        /*@Nullable
        public <T> T getCapability(BlockCapability<T, Direction> capability, @Nullable Direction side) {
            return provideCapability(capability, side).orElse(null);
        }

        // 留给子类的钩子（返回 Optional，方便处理）
        protected <T> Optional<T> provideCapability(BlockCapability<T, Direction> capability, @Nullable Direction side) {
            return Optional.empty();
        }*/

    }
}
