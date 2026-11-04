package me.easan.onekey.fabric.net;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import me.easan.onekey.common.net.QuickStackRequest;
import me.easan.onekey.common.net.TargetPos;
import me.easan.onekey.fabric.OneKeyFabric;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：请求快速堆叠 / 存入附近容器。
 */
public record QuickStackRequestPayload(QuickStackRequest request) implements CustomPacketPayload {

    public static final Type<QuickStackRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OneKeyFabric.MOD_ID, "quick_stack_request"));

    private static final StreamCodec<ByteBuf, TargetPos> TARGET_POS_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TargetPos::x,
            ByteBufCodecs.VAR_INT, TargetPos::y,
            ByteBufCodecs.VAR_INT, TargetPos::z,
            TargetPos::new
    );

    private static final StreamCodec<ByteBuf, Optional<TargetPos>> OPTIONAL_TARGET_POS_CODEC =
            ByteBufCodecs.optional(TARGET_POS_CODEC);

    public static final StreamCodec<ByteBuf, QuickStackRequestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, r -> r.request().syncId(),
            ByteBufCodecs.VAR_INT, r -> r.request().radius(),
            ByteBufCodecs.BOOL, r -> r.request().stash(),
            OPTIONAL_TARGET_POS_CODEC, r -> r.request().targetPos(),
            (syncId, radius, stash, targetPos) -> new QuickStackRequestPayload(new QuickStackRequest(syncId, radius, stash, targetPos))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
