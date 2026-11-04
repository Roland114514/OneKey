package me.easan.onekey.common.net;

/**
 * 网络协议版本。客户端与服务端不一致时拒绝协同功能（避免静默错位）。
 */
public final class ProtocolVersion {

    /**
     * 1.0.2 起 {@link QuickStackRequest} 增加 {@code Optional<TargetPos> targetPos} 字段，
     * 旧版客户端/服务端无法解码新包，必须双端同版本，故升到 2。
     */
    public static final int CURRENT = 2;

    private ProtocolVersion() {
    }
}
