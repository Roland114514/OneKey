package me.easan.onekey.fabric.net;

import io.netty.buffer.ByteBuf;
import java.util.List;
import me.easan.onekey.fabric.OneKeyFabric;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端：同步指定容器类型的锁定槽位列表。
 */
public record SyncSlotLocksPayload(String containerKey, List<Integer> lockedSlots) implements CustomPacketPayload {

    public static final Type<SyncSlotLocksPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OneKeyFabric.MOD_ID, "sync_slot_locks"));

    public static final StreamCodec<ByteBuf, SyncSlotLocksPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SyncSlotLocksPayload::containerKey,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), SyncSlotLocksPayload::lockedSlots,
            SyncSlotLocksPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
