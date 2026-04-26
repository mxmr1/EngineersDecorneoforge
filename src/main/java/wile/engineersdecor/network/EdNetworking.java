/*
 * @file EdNetworking.java
 * @author Stefan
 * @license MIT
 *
 * Networking for Engineer's Decor — NeoForge 1.21.1 (21.1.209)
 */

package wile.engineersdecor.network;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.ChunkPos;

@EventBusSubscriber(modid = "engineersdecor") // 🆕 Удален bus
public class EdNetworking {

    public static final ResourceLocation CONTAINER_SYNC_ID = ResourceLocation.fromNamespaceAndPath("engineersdecor", "container_sync"); // 🆕 Исправлено


    // ======== PAYLOAD: Container Sync ========
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

    // ======== EVENT: Register payloads ========
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("engineersdecor");


        // playToServer — server-bound packets (container updates etc.)
        registrar.playToServer(ContainerSyncPayload.TYPE, ContainerSyncPayload.STREAM_CODEC, EdNetworking::handleContainerSync);
    }


    // ======== HANDLER: Container sync ========
    private static void handleContainerSync(ContainerSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() == null) return;
            if (context.player().containerMenu.containerId == payload.windowId) {
                context.player().containerMenu.broadcastChanges();
            }
        });
    }



    public static void sendContainerSync(int windowId, CompoundTag nbt) {
        ContainerSyncPayload payload = new ContainerSyncPayload(windowId, nbt);
        PacketDistributor.sendToServer(payload);
    }
}
