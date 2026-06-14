/*
 * @file EdNetworking.java
 * @author Stefan
 * @license MIT
 *
 * Engineer's Decor 的网络通信 — NeoForge 1.21.1 (21.1.209)
 */

package wile.engineersdecor.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import wile.engineersdecor.libmc.Networking;  // 导入自定义接口

@EventBusSubscriber(modid = "engineersdecor")
public class EdNetworking {

    public static final ResourceLocation CONTAINER_SYNC_ID = ResourceLocation.fromNamespaceAndPath("engineersdecor", "container_sync");

    // ======== PAYLOAD: 容器同步 ========
    public record ContainerSyncPayload(int windowId, CompoundTag nbt) implements CustomPacketPayload {
        public static final Type<ContainerSyncPayload> TYPE = new Type<>(CONTAINER_SYNC_ID);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, ContainerSyncPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeInt(payload.windowId);
                    buf.writeNbt(payload.nbt);
                },
                buf -> new ContainerSyncPayload(buf.readInt(), buf.readNbt())
        );
    }

    // ======== 事件: 注册载荷 ========
    @SubscribeEvent()
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("engineersdecor");
        // playToServer — 发送至服务端的数据包（容器更新等）
        registrar.playToServer(ContainerSyncPayload.TYPE, ContainerSyncPayload.STREAM_CODEC, EdNetworking::handleContainerSync);
    }

    // ======== 处理: 容器同步 ========
    private static void handleContainerSync(ContainerSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) return;
            var menu = player.containerMenu;
            // 检查菜单 ID 匹配，并且菜单实现了我们的网络同步接口
            if (menu.containerId == payload.windowId && menu instanceof Networking.INetworkSynchronisableContainer syncMenu) {
                syncMenu.onClientPacketReceived(payload.windowId, player, payload.nbt);
                menu.broadcastChanges();  // 将更改同步回客户端
            }
        });
    }

    // ======== 客户端发送辅助方法 ========
    public static void sendContainerSync(int windowId, CompoundTag nbt) {
        ContainerSyncPayload payload = new ContainerSyncPayload(windowId, nbt);
        PacketDistributor.sendToServer(payload);
    }
}