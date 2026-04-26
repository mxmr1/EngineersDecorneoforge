/*
 * @file Networking.java
 * @author Stefan Wilhelm
 * @license MIT
 *
 * Networking system for Engineer's Decor (NeoForge 1.21.1 port)
 */

package wile.engineersdecor.libmc;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.network.codec.ByteBufCodecs;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Mod("engineersdecor")
public class Networking {

    private static final String PROTOCOL = "1";

    // --------------------------------------------------------------------------------------------
    // Регистрация пакетов
    // --------------------------------------------------------------------------------------------
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("engineersdecor").versioned(PROTOCOL);

        // Tile entity sync
        registrar.playToServer(PacketTileNotifyClientToServer.TYPE, PacketTileNotifyClientToServer.STREAM_CODEC, PacketTileNotifyClientToServer.Handler::handle);
        registrar.playToClient(PacketTileNotifyServerToClient.TYPE, PacketTileNotifyServerToClient.STREAM_CODEC, PacketTileNotifyServerToClient.Handler::handle);

        // Containers
        registrar.playToServer(PacketContainerSyncClientToServer.TYPE, PacketContainerSyncClientToServer.STREAM_CODEC, PacketContainerSyncClientToServer.Handler::handle);
        registrar.playToClient(PacketContainerSyncServerToClient.TYPE, PacketContainerSyncServerToClient.STREAM_CODEC, PacketContainerSyncServerToClient.Handler::handle);

        // Generic NBT packets
        registrar.playToServer(PacketNbtNotifyClientToServer.TYPE, PacketNbtNotifyClientToServer.STREAM_CODEC, PacketNbtNotifyClientToServer.Handler::handle);
        registrar.playToClient(PacketNbtNotifyServerToClient.TYPE, PacketNbtNotifyServerToClient.STREAM_CODEC, PacketNbtNotifyServerToClient.Handler::handle);

        // Overlay messages
        registrar.playToClient(OverlayTextMessage.TYPE, OverlayTextMessage.STREAM_CODEC, OverlayTextMessage.Handler::handle);
    }

    // --------------------------------------------------------------------------------------------
    // Tile entity notifications
    // --------------------------------------------------------------------------------------------
    public interface IPacketTileNotifyReceiver {
        default void onServerPacketReceived(CompoundTag nbt) {}
        default void onClientPacketReceived(Player player, CompoundTag nbt) {}
    }

    public record PacketTileNotifyClientToServer(BlockPos pos, CompoundTag nbt) implements CustomPacketPayload {
        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("engineersdecor", "tile_notify_c2s");
        public static final Type<PacketTileNotifyClientToServer> TYPE = new Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, PacketTileNotifyClientToServer> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, PacketTileNotifyClientToServer::pos,
                        ByteBufCodecs.fromCodecWithRegistries(CompoundTag.CODEC), PacketTileNotifyClientToServer::nbt,
                        PacketTileNotifyClientToServer::new);

        public static void sendToServer(BlockPos pos, CompoundTag nbt) {
            if (pos == null || nbt == null) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null)
                mc.getConnection().send(new PacketTileNotifyClientToServer(pos, nbt));
        }

        public static void sendToServer(BlockEntity te, CompoundTag nbt) {
            if (te != null && nbt != null) sendToServer(te.getBlockPos(), nbt);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static class Handler {
            public static void handle(final PacketTileNotifyClientToServer pkt, final IPayloadContext ctx) {
                ctx.enqueueWork(() -> {
                    Player player = ctx.player();
                    Level level = player.level();
                    BlockEntity be = level.getBlockEntity(pkt.pos());
                    if (be instanceof IPacketTileNotifyReceiver recv)
                        recv.onClientPacketReceived(player, pkt.nbt());
                });
            }
        }
    }

    public record PacketTileNotifyServerToClient(BlockPos pos, CompoundTag nbt) implements CustomPacketPayload {
        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("engineersdecor", "tile_notify_s2c");
        public static final Type<PacketTileNotifyServerToClient> TYPE = new Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, PacketTileNotifyServerToClient> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, PacketTileNotifyServerToClient::pos,
                        ByteBufCodecs.fromCodecWithRegistries(CompoundTag.CODEC), PacketTileNotifyServerToClient::nbt,
                        PacketTileNotifyServerToClient::new);

        public static void sendToPlayer(Player player, BlockEntity te, CompoundTag nbt) {
            if (player instanceof ServerPlayer sp && !(sp instanceof FakePlayer) && te != null && nbt != null)
                sp.connection.send(new PacketTileNotifyServerToClient(te.getBlockPos(), nbt));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static class Handler {
            public static void handle(final PacketTileNotifyServerToClient pkt, final IPayloadContext ctx) {
                ctx.enqueueWork(() ->  {
                    Level world = Minecraft.getInstance().level;
                    if (world == null) return;
                    BlockEntity be = world.getBlockEntity(pkt.pos());
                    if (be instanceof IPacketTileNotifyReceiver recv)
                        recv.onServerPacketReceived(pkt.nbt());
                });
            }
        }
    }

    // --------------------------------------------------------------------------------------------
    // Container synchronization
    // --------------------------------------------------------------------------------------------
    public interface INetworkSynchronisableContainer {
        void onServerPacketReceived(int windowId, CompoundTag nbt);
        void onClientPacketReceived(int windowId, Player player, CompoundTag nbt);
    }

    public record PacketContainerSyncClientToServer(int id, CompoundTag nbt) implements CustomPacketPayload {
        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("engineersdecor", "container_sync_c2s");
        public static final Type<PacketContainerSyncClientToServer> TYPE = new Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, PacketContainerSyncClientToServer> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.INT, PacketContainerSyncClientToServer::id,
                        ByteBufCodecs.fromCodecWithRegistries(CompoundTag.CODEC), PacketContainerSyncClientToServer::nbt,
                        PacketContainerSyncClientToServer::new);

        public static void sendToServer(int windowId, CompoundTag nbt) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null && nbt != null)
                mc.getConnection().send(new PacketContainerSyncClientToServer(windowId, nbt));
        }

        public static void sendToServer(AbstractContainerMenu container, CompoundTag nbt) {
            if (container != null && nbt != null)
                sendToServer(container.containerId, nbt);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static class Handler {
            public static void handle(final PacketContainerSyncClientToServer pkt, final IPayloadContext ctx) {
                ctx.enqueueWork(() ->  {
                    Player player = ctx.player();
                    if (!(player.containerMenu instanceof INetworkSynchronisableContainer menu)) return;
                    if (player.containerMenu.containerId != pkt.id()) return;
                    menu.onClientPacketReceived(pkt.id(), player, pkt.nbt());
                });
            }
        }
    }

    public record PacketContainerSyncServerToClient(int id, CompoundTag nbt) implements CustomPacketPayload {
        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("engineersdecor", "container_sync_s2c");
        public static final Type<PacketContainerSyncServerToClient> TYPE = new Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, PacketContainerSyncServerToClient> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.INT, PacketContainerSyncServerToClient::id,
                        ByteBufCodecs.fromCodecWithRegistries(CompoundTag.CODEC), PacketContainerSyncServerToClient::nbt,
                        PacketContainerSyncServerToClient::new);

        public static void sendToPlayer(Player player, int windowId, CompoundTag nbt) {
            if (player instanceof ServerPlayer sp && !(sp instanceof FakePlayer) && nbt != null)
                sp.connection.send(new PacketContainerSyncServerToClient(windowId, nbt));
        }

        public static void sendToPlayer(Player player, AbstractContainerMenu container, CompoundTag nbt) {
            if (container != null)
                sendToPlayer(player, container.containerId, nbt);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static class Handler {
            public static void handle(final PacketContainerSyncServerToClient pkt, final IPayloadContext ctx) {
                ctx.enqueueWork(() ->  {
                    Player player = Minecraft.getInstance().player;
                    if (player == null) return;
                    if (!(player.containerMenu instanceof INetworkSynchronisableContainer menu)) return;
                    if (player.containerMenu.containerId != pkt.id()) return;
                    menu.onServerPacketReceived(pkt.id(), pkt.nbt());
                });
            }
        }
    }

    // --------------------------------------------------------------------------------------------
    // Generic NBT notifications
    // --------------------------------------------------------------------------------------------
    public record PacketNbtNotifyClientToServer(CompoundTag nbt) implements CustomPacketPayload {
        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("engineersdecor", "nbt_notify_c2s");
        public static final Type<PacketNbtNotifyClientToServer> TYPE = new Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, PacketNbtNotifyClientToServer> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.fromCodecWithRegistries(CompoundTag.CODEC), PacketNbtNotifyClientToServer::nbt, PacketNbtNotifyClientToServer::new);

        public static final Map<String, BiConsumer<Player, CompoundTag>> handlers = new HashMap<>();

        public static void sendToServer(CompoundTag nbt) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null && nbt != null)
                mc.getConnection().send(new PacketNbtNotifyClientToServer(nbt));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static class Handler {
            public static void handle(final PacketNbtNotifyClientToServer pkt, final IPayloadContext ctx) {
                ctx.enqueueWork(() ->  {
                    Player player = ctx.player();
                    String hnd = pkt.nbt().getString("hnd");
                    if (!hnd.isEmpty() && handlers.containsKey(hnd))
                        handlers.get(hnd).accept(player, pkt.nbt());
                });
            }
        }
    }

    public record PacketNbtNotifyServerToClient(CompoundTag nbt) implements CustomPacketPayload {
        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("engineersdecor", "nbt_notify_s2c");
        public static final Type<PacketNbtNotifyServerToClient> TYPE = new Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, PacketNbtNotifyServerToClient> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.fromCodecWithRegistries(CompoundTag.CODEC), PacketNbtNotifyServerToClient::nbt, PacketNbtNotifyServerToClient::new);

        public static final Map<String, Consumer<CompoundTag>> handlers = new HashMap<>();

        public static void sendToPlayer(Player player, CompoundTag nbt) {
            if (player instanceof ServerPlayer sp && !(sp instanceof FakePlayer) && nbt != null)
                sp.connection.send(new PacketNbtNotifyServerToClient(nbt));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static class Handler {
            public static void handle(final PacketNbtNotifyServerToClient pkt, final IPayloadContext ctx) {
                ctx.enqueueWork(() ->  {
                    String hnd = pkt.nbt().getString("hnd");
                    if (!hnd.isEmpty() && handlers.containsKey(hnd))
                        handlers.get(hnd).accept(pkt.nbt());
                });
            }
        }
    }

    // --------------------------------------------------------------------------------------------
    // Overlay text
    // --------------------------------------------------------------------------------------------
    public record OverlayTextMessage(Component data, int delay) implements CustomPacketPayload {
        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("engineersdecor", "overlay_text");
        public static final Type<OverlayTextMessage> TYPE = new Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, OverlayTextMessage> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.fromCodecWithRegistries(ComponentSerialization.CODEC), OverlayTextMessage::data,
                        ByteBufCodecs.INT, OverlayTextMessage::delay,
                        OverlayTextMessage::new);

        public static final int DISPLAY_TIME_MS = 3000;
        private static BiConsumer<Component, Integer> handler_ = null;

        public static void setHandler(BiConsumer<Component, Integer> handler) {
            handler_ = handler;
        }

        public static void sendToPlayer(Player player, Component message, int delay) {
            if (message == null || player instanceof FakePlayer) return;
            if (player instanceof ServerPlayer sp)
                sp.connection.send(new OverlayTextMessage(message, delay));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static class Handler {
            public static void handle(final OverlayTextMessage pkt, final IPayloadContext ctx) {
                if (handler_ != null)
                    ctx.enqueueWork(() ->  handler_.accept(pkt.data(), pkt.delay()));
            }
        }
    }
}
