package me.easan.onekey.common.net;

/**
 * 网络协议版本。客户端与服务端不一致时拒绝协同功能（避免静默错位）。
 */
public final class ProtocolVersion {

    /**
     * <p>版本历史：</p>
     * <ul>
     *     <li>1 —— 初版（整理/堆叠/滚轮）。</li>
     *     <li>2 —— 1.0.2 起 {@link QuickStackRequest} 增加 {@code Optional<TargetPos> targetPos}。</li>
     *     <li>3 —— 1.1.2 起 {@code SortRequest} 增加 {@code optionsBitmask}、
     *     {@code QuickStackRequest} 增加 {@code smartMatching}、{@code ScrollItemTransferPayload}
     *     增加 {@code includeHotbar}、{@code ExcavatorStatePayload} 增加玩家连锁破坏偏好位掩码。</li>
     * </ul>
     * <p>1.1.2 之前的几个版本虽然改了数据包结构却没有升版本号，导致 1.0.x 与 1.1.x 混连时
     * NeoForge 的 {@code versioned(...)} 通道握手不会拦截；从 3 起恢复「改结构必升版本」的约定。</p>
     */
    public static final int CURRENT = 3;

    private ProtocolVersion() {
    }
}
