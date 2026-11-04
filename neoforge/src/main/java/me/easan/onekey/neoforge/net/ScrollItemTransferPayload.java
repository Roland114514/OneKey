package me.easan.onekey.neoforge.net;

import io.netty.buffer.ByteBuf;
import me.easan.onekey.neoforge.OneKeyNeoForge;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：鼠标滚轮微调单件物品转移。
 */
public record ScrollItemTransferPayload(int syncId, int slotIndex, boolean scrollUp) implements CustomPacketPayload {

    public static final Type<ScrollItemTransferPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OneKeyNeoForge.MOD_ID, "scroll_item_transfer"));

    public static final StreamCodec<ByteBuf, ScrollItemTransferPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScrollItemTransferPayload::syncId,
            ByteBufCodecs.VAR_INT, ScrollItemTransferPayload::slotIndex,
            ByteBufCodecs.BOOL, ScrollItemTransferPayload::scrollUp,
            ScrollItemTransferPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
