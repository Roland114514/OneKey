package me.easan.onekey.fabric.net;

import io.netty.buffer.ByteBuf;
import me.easan.onekey.fabric.OneKeyFabric;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：鼠标滚轮微调单件物品转移。
 *
 * @param syncId    当前容器同步 ID
 * @param slotIndex 触发滚轮的槽位菜单下标
 * @param scrollUp  true = 向上滚动（从对侧拿取 1 件放入本槽位）；false = 向下滚动（将本槽位 1 件送往对侧）
 */
public record ScrollItemTransferPayload(int syncId, int slotIndex, boolean scrollUp) implements CustomPacketPayload {

    public static final Type<ScrollItemTransferPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OneKeyFabric.MOD_ID, "scroll_item_transfer"));

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
