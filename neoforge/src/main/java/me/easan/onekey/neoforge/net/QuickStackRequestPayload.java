package me.easan.onekey.neoforge.net;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import me.easan.onekey.common.net.QuickStackRequest;
import me.easan.onekey.common.net.TargetPos;
import me.easan.onekey.neoforge.OneKeyNeoForge;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：快速堆叠 / 一键存入请求。
 */
public record QuickStackRequestPayload(QuickStackRequest request) implements CustomPacketPayload {

    public static final Type<QuickStackRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OneKeyNeoForge.MOD_ID, "quick_stack_request"));

    private static final StreamCodec<ByteBuf, TargetPos> TARGET_POS_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TargetPos::x,
            ByteBufCodecs.VAR_INT, TargetPos::y,
            ByteBufCodecs.VAR_INT, TargetPos::z,
            TargetPos::new
    );

    private static final StreamCodec<ByteBuf, Optional<TargetPos>> OPTIONAL_TARGET_POS_CODEC =
            ByteBufCodecs.optional(TARGET_POS_CODEC);

    private static final StreamCodec<ByteBuf, QuickStackRequestPayload> RAW_CODEC = new StreamCodec<>() {
        @Override
        public QuickStackRequestPayload decode(ByteBuf buffer) {
            int syncId = ByteBufCodecs.VAR_INT.decode(buffer);
            int radius = ByteBufCodecs.VAR_INT.decode(buffer);
            boolean stash = ByteBufCodecs.BOOL.decode(buffer);
            Optional<TargetPos> targetPos = OPTIONAL_TARGET_POS_CODEC.decode(buffer);
            boolean smartMatching = buffer.isReadable() ? ByteBufCodecs.BOOL.decode(buffer) : true;
            return new QuickStackRequestPayload(new QuickStackRequest(syncId, radius, stash, targetPos, smartMatching));
        }

        @Override
        public void encode(ByteBuf buffer, QuickStackRequestPayload value) {
            ByteBufCodecs.VAR_INT.encode(buffer, value.request().syncId());
            ByteBufCodecs.VAR_INT.encode(buffer, value.request().radius());
            ByteBufCodecs.BOOL.encode(buffer, value.request().stash());
            OPTIONAL_TARGET_POS_CODEC.encode(buffer, value.request().targetPos());
            ByteBufCodecs.BOOL.encode(buffer, value.request().smartMatching());
        }
    };

    /**
     * 容错解码：1.0.1 及更早的客户端数据包不含「目标坐标」字段，直接按新格式解码会抛异常，
     * 进而导致该玩家被服务器以「数据包解码失败」断开。这里把解码失败退化为 syncId = -1 的空请求，
     * 交由服务端 syncId 校验静默丢弃，混版本玩家最多是「按了没反应」，不会被踢下线。
     */
    public static final StreamCodec<ByteBuf, QuickStackRequestPayload> CODEC = new StreamCodec<>() {
        @Override
        public QuickStackRequestPayload decode(ByteBuf buffer) {
            try {
                return RAW_CODEC.decode(buffer);
            } catch (RuntimeException exception) {
                OneKeyNeoForge.LOGGER.warn("[OneKey] 无法解码快速堆叠请求（可能是旧版客户端），已忽略：{}", exception.toString());
                return new QuickStackRequestPayload(new QuickStackRequest(-1, 0, false, Optional.empty()));
            }
        }

        @Override
        public void encode(ByteBuf buffer, QuickStackRequestPayload value) {
            RAW_CODEC.encode(buffer, value);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
