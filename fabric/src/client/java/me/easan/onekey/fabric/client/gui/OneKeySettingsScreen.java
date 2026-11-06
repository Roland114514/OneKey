package me.easan.onekey.fabric.client.gui;

import me.easan.onekey.common.config.OneKeyConfig;
import me.easan.onekey.fabric.client.ClientExcavatorHandler;
import me.easan.onekey.fabric.OneKeyFabric;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * OneKey 图形设置界面：采用多标签分页布局（一键整理 / 快速堆叠 / 鼠标手势 / 连锁破坏），
 * 彻底消除低分辨率/大 GUI 缩放比例下底部提示文本与设置按钮重叠的问题，
 * 全面支持按钮原生悬浮气泡说明（Tooltip）与一键恢复出厂设置。
 */
public final class OneKeySettingsScreen extends Screen {

    private final Screen parent;
    private static int activeTab = 0; // 0: 整理设置, 1: 堆叠存入, 2: 鼠标手势, 3: 连锁破坏

    public OneKeySettingsScreen(Screen parent) {
        super(Component.translatable("onekey.settings.title"));
        this.parent = parent;
    }

    private static <T extends AbstractWidget> T withTooltip(T widget, String tooltipKey) {
        widget.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        return widget;
    }

    @Override
    protected void init() {
        OneKeyConfig config = OneKeyFabric.config();

        // 1. 顶部 4 个分类标签切换按钮
        int tabWidth = 74;
        int tabHeight = 20;
        int totalTabsWidth = 4 * tabWidth + 3 * 4;
        int tabStartX = this.width / 2 - totalTabsWidth / 2;
        int tabY = 28;

        Button tabSortBtn = withTooltip(Button.builder(Component.translatable("onekey.settings.tab.sort"), btn -> switchTab(0))
                .bounds(tabStartX, tabY, tabWidth, tabHeight).build(), "onekey.settings.tab.sort.tooltip");
        tabSortBtn.active = (activeTab != 0);
        this.addRenderableWidget(tabSortBtn);

        Button tabStackBtn = withTooltip(Button.builder(Component.translatable("onekey.settings.tab.stack"), btn -> switchTab(1))
                .bounds(tabStartX + tabWidth + 4, tabY, tabWidth, tabHeight).build(), "onekey.settings.tab.stack.tooltip");
        tabStackBtn.active = (activeTab != 1);
        this.addRenderableWidget(tabStackBtn);

        Button tabGesturesBtn = withTooltip(Button.builder(Component.translatable("onekey.settings.tab.gestures"), btn -> switchTab(2))
                .bounds(tabStartX + 2 * (tabWidth + 4), tabY, tabWidth, tabHeight).build(), "onekey.settings.tab.gestures.tooltip");
        tabGesturesBtn.active = (activeTab != 2);
        this.addRenderableWidget(tabGesturesBtn);

        Button tabExcavatorBtn = withTooltip(Button.builder(Component.translatable("onekey.settings.tab.excavator"), btn -> switchTab(3))
                .bounds(tabStartX + 3 * (tabWidth + 4), tabY, tabWidth, tabHeight).build(), "onekey.settings.tab.excavator.tooltip");
        tabExcavatorBtn.active = (activeTab != 3);
        this.addRenderableWidget(tabExcavatorBtn);

        // 2. 居中两列选项布局
        int leftX = this.width / 2 - 155;
        int rightX = this.width / 2 + 5;
        int btnWidth = 150;
        int btnHeight = 20;
        int rowStartY = 56;
        int rowGap = 24;

        if (activeTab == 0) {
            // Tab 0: 一键整理
            // Row 1: 整理总开关 / 合并同类堆叠
            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.sort.enabled)
                    .create(leftX, rowStartY, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.enabled"),
                            (button, value) -> config.sort.enabled = value), "onekey.settings.sort.enabled.tooltip"));

            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.sort.mergeStacks)
                    .create(rightX, rowStartY, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.mergeStacks"),
                            (button, value) -> config.sort.mergeStacks = value), "onekey.settings.sort.mergeStacks.tooltip"));

            // Row 2: 整理包含快捷栏 / 冷却时间
            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.sort.includeHotbar)
                    .create(leftX, rowStartY + rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.includeHotbar"),
                            (button, value) -> config.sort.includeHotbar = value), "onekey.settings.sort.includeHotbar.tooltip"));

            this.addRenderableWidget(withTooltip(CycleButton.<Integer>builder(val -> Component.literal(val + " ms"), () -> config.sort.cooldownMs)
                    .withValues(100, 150, 200, 250, 300, 500)
                    .create(rightX, rowStartY + rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.cooldown"),
                            (button, value) -> config.sort.cooldownMs = value), "onekey.settings.sort.cooldown.tooltip"));

            // Row 3: 分类分组 / 名称排序
            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.sort.groupByCategory)
                    .create(leftX, rowStartY + 2 * rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.groupByCategory"),
                            (button, value) -> config.sort.groupByCategory = value), "onekey.settings.sort.groupByCategory.tooltip"));

            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.sort.sortByName)
                    .create(rightX, rowStartY + 2 * rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.sortByName"),
                            (button, value) -> config.sort.sortByName = value), "onekey.settings.sort.sortByName.tooltip"));

            // Row 4: 数量降序 / 显示整理按键
            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.sort.sortByCountDesc)
                    .create(leftX, rowStartY + 3 * rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.sortByCountDesc"),
                            (button, value) -> config.sort.sortByCountDesc = value), "onekey.settings.sort.sortByCountDesc.tooltip"));

            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.sort.showSortButton)
                    .create(rightX, rowStartY + 3 * rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.showSortButton"),
                            (button, value) -> config.sort.showSortButton = value), "onekey.settings.sort.showSortButton.tooltip"));

        } else if (activeTab == 1) {
            // Tab 1: 快速堆叠与一键存入
            // Row 1: 准星瞄准优先 / 智能分类防污染
            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.quickStack.crosshairPriority)
                    .create(leftX, rowStartY, btnWidth, btnHeight, Component.translatable("onekey.settings.quickStack.crosshairPriority"),
                            (button, value) -> config.quickStack.crosshairPriority = value), "onekey.settings.quickStack.crosshairPriority.tooltip"));

            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.quickStack.smartMatching)
                    .create(rightX, rowStartY, btnWidth, btnHeight, Component.translatable("onekey.settings.quickStack.smartMatching"),
                            (button, value) -> config.quickStack.smartMatching = value), "onekey.settings.quickStack.smartMatching.tooltip"));

            // Row 2: 堆叠扫描半径 / 存入模式
            this.addRenderableWidget(withTooltip(CycleButton.<Integer>builder(val -> Component.literal(val + " 格"), () -> config.quickStack.radius)
                    .withValues(2, 4, 6, 8)
                    .create(leftX, rowStartY + rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.quickStack.radius"),
                            (button, value) -> config.quickStack.radius = value), "onekey.settings.quickStack.radius.tooltip"));

            this.addRenderableWidget(withTooltip(CycleButton.<Boolean>builder(
                            val -> Component.translatable(val ? "onekey.settings.stashMode.all" : "onekey.settings.stashMode.matching"),
                            () -> config.quickStack.stashMode)
                    .withValues(false, true)
                    .create(rightX, rowStartY + rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.quickStack.stashMode"),
                            (button, value) -> config.quickStack.stashMode = value), "onekey.settings.quickStack.stashMode.tooltip"));

        } else if (activeTab == 2) {
            // Tab 2: 鼠标手势
            // Row 1: Shift左键扫掠转移 / 右键划动重复放置
            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.gestures.lmbDragSweep)
                    .create(leftX, rowStartY, btnWidth, btnHeight, Component.translatable("onekey.settings.gestures.lmbDragSweep"),
                            (button, value) -> config.gestures.lmbDragSweep = value), "onekey.settings.gestures.lmbDragSweep.tooltip"));

            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.gestures.rmbDragRepeat)
                    .create(rightX, rowStartY, btnWidth, btnHeight, Component.translatable("onekey.settings.gestures.rmbDragRepeat"),
                            (button, value) -> config.gestures.rmbDragRepeat = value), "onekey.settings.gestures.rmbDragRepeat.tooltip"));

            // Row 2: 滚轮方向反转 / 持物滚轮微调
            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.gestures.invertWheel)
                    .create(leftX, rowStartY + rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.gestures.invertWheel"),
                            (button, value) -> config.gestures.invertWheel = value), "onekey.settings.gestures.invertWheel.tooltip"));

            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.gestures.wheelCarriedHandling)
                    .create(rightX, rowStartY + rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.gestures.wheelCarriedHandling"),
                            (button, value) -> config.gestures.wheelCarriedHandling = value), "onekey.settings.gestures.wheelCarriedHandling.tooltip"));

            // Row 3: 手势影响快捷栏
            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.gestures.includeHotbar)
                    .create(leftX, rowStartY + 2 * rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.gestures.includeHotbar"),
                            (button, value) -> config.gestures.includeHotbar = value), "onekey.settings.gestures.includeHotbar.tooltip"));

        } else {
            // Tab 3: 连锁破坏
            // Row 1: 连锁总开关 / 最大破坏数量
            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.excavator.enabled)
                    .create(leftX, rowStartY, btnWidth, btnHeight, Component.translatable("onekey.settings.excavator.enabled"),
                            (button, value) -> config.excavator.enabled = value), "onekey.settings.excavator.enabled.tooltip"));

            this.addRenderableWidget(withTooltip(CycleButton.<Integer>builder(val -> Component.literal(val + " 块"), () -> config.excavator.maxBlocks)
                    .withValues(16, 32, 64, 128)
                    .create(rightX, rowStartY, btnWidth, btnHeight, Component.translatable("onekey.settings.excavator.maxBlocks"),
                            (button, value) -> config.excavator.maxBlocks = value), "onekey.settings.excavator.maxBlocks.tooltip"));

            // Row 2: 包含相连树叶 / 工具防爆保护
            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.excavator.includeLeaves)
                    .create(leftX, rowStartY + rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.excavator.includeLeaves"),
                            (button, value) -> config.excavator.includeLeaves = value), "onekey.settings.excavator.includeLeaves.tooltip"));

            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.excavator.preventToolBreak)
                    .create(rightX, rowStartY + rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.excavator.preventToolBreak"),
                            (button, value) -> config.excavator.preventToolBreak = value), "onekey.settings.excavator.preventToolBreak.tooltip"));

            // Row 3: 严格工具匹配 / 掉落物与经验聚合
            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.excavator.requireValidTool)
                    .create(leftX, rowStartY + 2 * rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.excavator.requireValidTool"),
                            (button, value) -> config.excavator.requireValidTool = value), "onekey.settings.excavator.requireValidTool.tooltip"));

            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.excavator.consolidateDrops)
                    .create(rightX, rowStartY + 2 * rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.excavator.consolidateDrops"),
                            (button, value) -> config.excavator.consolidateDrops = value), "onekey.settings.excavator.consolidateDrops.tooltip"));

            // Row 4: 边框高亮显示
            this.addRenderableWidget(withTooltip(CycleButton.onOffBuilder(config.excavator.highlightOutlines)
                    .create(leftX, rowStartY + 3 * rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.excavator.highlightOutlines"),
                            (button, value) -> config.excavator.highlightOutlines = value), "onekey.settings.excavator.highlightOutlines.tooltip"));
        }

        // 3. 底部操作按钮：配置原版按键绑定 / 恢复默认 / 完成
        int bottomBtnWidth = 98;
        int bottomGap = 6;
        int bottomTotalWidth = 3 * bottomBtnWidth + 2 * bottomGap;
        int bottomStartX = this.width / 2 - bottomTotalWidth / 2;
        int bottomY = this.height - 28;

        this.addRenderableWidget(withTooltip(Button.builder(Component.translatable("onekey.settings.openKeybinds"), btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new KeyBindsScreen(this, this.minecraft.options));
            }
        }).bounds(bottomStartX, bottomY, bottomBtnWidth, btnHeight).build(), "onekey.settings.openKeybinds.tooltip"));

        this.addRenderableWidget(withTooltip(Button.builder(Component.translatable("onekey.settings.resetDefaults"), btn -> resetDefaults())
                .bounds(bottomStartX + bottomBtnWidth + bottomGap, bottomY, bottomBtnWidth, btnHeight).build(), "onekey.settings.resetDefaults.tooltip"));

        this.addRenderableWidget(withTooltip(Button.builder(CommonComponents.GUI_DONE, btn -> onClose())
                .bounds(bottomStartX + 2 * (bottomBtnWidth + bottomGap), bottomY, bottomBtnWidth, btnHeight).build(), "onekey.settings.done.tooltip"));
    }

    private void resetDefaults() {
        OneKeyConfig defaults = new OneKeyConfig();
        OneKeyConfig current = OneKeyFabric.config();
        current.sort = defaults.sort;
        current.quickStack = defaults.quickStack;
        current.gestures = defaults.gestures;
        current.excavator = defaults.excavator;
        OneKeyFabric.saveConfig();
        ClientExcavatorHandler.syncState();
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
        }
        this.clearWidgets();
        this.init();
    }

    private void switchTab(int tab) {
        activeTab = tab;
        this.clearWidgets();
        this.init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);

        // 顶部标题
        extractor.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);

        // 根据不同标签页绘制专属规则与操作提示（位置自适应，杜绝重叠）
        int bottomButtonY = this.height - 28;
        if (activeTab == 0) {
            int tipY1 = Math.max(160, bottomButtonY - 32);
            int tipY2 = Math.max(174, bottomButtonY - 18);
            if (tipY1 < bottomButtonY - 14) {
                extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.sort_1"), this.width / 2, tipY1, 0xFFFFB300);
                extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.locking",
                        me.easan.onekey.fabric.client.OneKeyFabricClient.getLockKeyComboComponent()), this.width / 2, tipY2, 0xFFAAAAAA);
            }
        } else if (activeTab == 1) {
            int tipY1 = Math.max(115, bottomButtonY - 32);
            int tipY2 = Math.max(129, bottomButtonY - 18);
            if (tipY1 < bottomButtonY - 14) {
                extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.stack_1"), this.width / 2, tipY1, 0xFF55FFFF);
                extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.stack_2"), this.width / 2, tipY2, 0xFFAAAAAA);
            }
        } else if (activeTab == 2) {
            int tipY1 = Math.max(135, bottomButtonY - 44);
            int tipY2 = Math.max(149, bottomButtonY - 30);
            int tipY3 = Math.max(163, bottomButtonY - 16);
            if (tipY1 < bottomButtonY - 26) {
                extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.sweep"), this.width / 2, tipY1, 0xFFFFB300);
                extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.repeat"), this.width / 2, tipY2, 0xFF55FFFF);
                extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.wheel"), this.width / 2, tipY3, 0xFFAAAAAA);
            }
        } else {
            int tipY1 = Math.max(135, bottomButtonY - 44);
            int tipY2 = Math.max(149, bottomButtonY - 30);
            int tipY3 = Math.max(163, bottomButtonY - 16);
            if (tipY1 < bottomButtonY - 26) {
                extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.excavator_1"), this.width / 2, tipY1, 0xFFFFB300);
                extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.excavator_2"), this.width / 2, tipY2, 0xFF55FFFF);
                extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.excavator_3"), this.width / 2, tipY3, 0xFFAAAAAA);
            }
        }
    }

    @Override
    public void removed() {
        // 窗口关闭 / 直接切换界面不一定走 onClose，这里再落一次盘避免设置丢失
        OneKeyFabric.saveConfig();
        super.removed();
    }

    @Override
    public void onClose() {
        OneKeyFabric.saveConfig();
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
