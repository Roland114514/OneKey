package me.easan.onekey.neoforge.net;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.easan.onekey.neoforge.OneKeyNeoForge;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端：同步槽位锁定集合。
 */
public record SyncSlotLocksPayload(String containerKey, Set<Integer> lockedSlots) implements CustomPacketPayload {

    public static final Type<SyncSlotLocksPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OneKeyNeoForge.MOD_ID, "sync_slot_locks"));

    public static final StreamCodec<ByteBuf, SyncSlotLocksPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SyncSlotLocksPayload::containerKey,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), p -> new ArrayList<>(p.lockedSlots()),
            (key, list) -> new SyncSlotLocksPayload(key, new HashSet<>(list))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
