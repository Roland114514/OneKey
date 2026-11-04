package me.easan.onekey.common.config;

import java.util.ArrayList;
import java.util.List;

import me.easan.onekey.common.sort.SortRule;

/**
 * 配置文件模型，落盘为 {@code config/onekey.json}。
 *
 * <p>使用普通字段（而非 record）以兼容 Minecraft 内置的 Gson 版本；
 * 缺失字段由 {@link #normalized()} 补默认值。
 */
public final class OneKeyConfig {

    /** 配置结构版本，未来做迁移时递增。 */
    public int schema = 1;

    public Hotkeys hotkeys = new Hotkeys();
    public Sort sort = new Sort();
    public QuickStack quickStack = new QuickStack();
    public ClientCompat compat = new ClientCompat();

    /** 热键映射：值形如 {@code key.keyboard.r}。 */
    public static final class Hotkeys {
        public String sort = "key.keyboard.r";
        public String quickStack = "key.keyboard.v";
        public String quickStash = "key.keyboard.b";
        public String settings = "key.keyboard.k";
    }

    /** 一键整理设置。 */
    public static final class Sort {
        public boolean enabled = true;
        public boolean groupByCategory = true;
        public boolean sortByName = true;
        public boolean sortByCountDesc = true;
        /** 连续触发的冷却时间，避免连点导致与服务端不同步。 */
        public int cooldownMs = 250;

        public SortRule toRule() {
            SortRule rule = new SortRule();
            rule.groupByCategory = this.groupByCategory;
            rule.sortByName = this.sortByName;
            rule.sortByCountDesc = this.sortByCountDesc;
            return rule;
        }
    }

    /** 快速堆叠 / 一键存入设置。 */
    public static final class QuickStack {
        /** 扫描附近容器的半径（格）。 */
        public int radius = 6;
        /** true = 全部存入（stash），false = 只补已有物品（quick stack）。 */
        public boolean stashMode = false;
        /** 容器方块白名单（方块 id，如 {@code minecraft:chest}）；空 = 允许所有可识别容器。 */
        public List<String> whitelist = new ArrayList<>();
        /** 容器方块黑名单（方块 id，优先于白名单）。 */
        public List<String> blacklist = new ArrayList<>();
        /** 参与堆叠/存入的物品白名单（物品 id，如 {@code minecraft:diamond}）；空 = 不限制物品。 */
        public List<String> itemWhitelist = new ArrayList<>();
        /** 排除的物品黑名单（物品 id，优先于白名单）。 */
        public List<String> itemBlacklist = new ArrayList<>();
        /** true = 准星瞄准的容器优先成为本次唯一目标（看哪存哪）；false = 忽略准星，仅按距离扫描。 */
        public boolean crosshairPriority = true;
        /** true = 智能分类防污染（空槽只向已有同类物品的容器或焦点容器开放）；false = 恢复旧版「存入全部」。 */
        public boolean smartMatching = true;
    }

    /** 纯客户端点击模拟（用于公共服务器）的开关，默认关闭。 */
    public static final class ClientCompat {
        public boolean enabled = false;
        /** 每个模拟点击之间的间隔，越大越安全。 */
        public int clickDelayMs = 60;
    }

    /** 补全 null 字段，保证下游逻辑不需要判空。 */
    public OneKeyConfig normalized() {
        if (hotkeys == null) {
            hotkeys = new Hotkeys();
        }
        if (sort == null) {
            sort = new Sort();
        }
        if (quickStack == null) {
            quickStack = new QuickStack();
        }
        if (compat == null) {
            compat = new ClientCompat();
        }
        if (quickStack.whitelist == null) {
            quickStack.whitelist = new ArrayList<>();
        }
        if (quickStack.blacklist == null) {
            quickStack.blacklist = new ArrayList<>();
        }
        if (quickStack.itemWhitelist == null) {
            quickStack.itemWhitelist = new ArrayList<>();
        }
        if (quickStack.itemBlacklist == null) {
            quickStack.itemBlacklist = new ArrayList<>();
        }
        // 数值上下限约束，防止异常配置引发计算暴涨或失去冷却保护
        sort.cooldownMs = Math.clamp(sort.cooldownMs, 50, 5000);
        quickStack.radius = Math.clamp(quickStack.radius, 1, 32);
        return this;
    }
}
