package me.easan.onekey.fabric.client.gui;

import me.easan.onekey.common.config.OneKeyConfig;
import me.easan.onekey.fabric.OneKeyFabric;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * OneKey 图形设置界面：采用多标签分页布局（一键整理 / 快速堆叠 / 鼠标手势），
 * 彻底消除低分辨率/大 GUI 缩放比例下底部提示文本与设置按钮重叠的问题。
 */
public final class OneKeySettingsScreen extends Screen {

    private final Screen parent;
    private static int activeTab = 0; // 0: 整理设置, 1: 堆叠存入, 2: 鼠标手势

    public OneKeySettingsScreen(Screen parent) {
        super(Component.translatable("onekey.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        OneKeyConfig config = OneKeyFabric.config();

        // 1. 顶部 3 个分类标签切换按钮
        int tabWidth = 98;
        int tabHeight = 20;
        int totalTabsWidth = 3 * tabWidth + 2 * 4;
        int tabStartX = this.width / 2 - totalTabsWidth / 2;
        int tabY = 28;

        Button tabSortBtn = Button.builder(Component.translatable("onekey.settings.tab.sort"), btn -> switchTab(0))
                .bounds(tabStartX, tabY, tabWidth, tabHeight).build();
        tabSortBtn.active = (activeTab != 0);
        this.addRenderableWidget(tabSortBtn);

        Button tabStackBtn = Button.builder(Component.translatable("onekey.settings.tab.stack"), btn -> switchTab(1))
                .bounds(tabStartX + tabWidth + 4, tabY, tabWidth, tabHeight).build();
        tabStackBtn.active = (activeTab != 1);
        this.addRenderableWidget(tabStackBtn);

        Button tabGesturesBtn = Button.builder(Component.translatable("onekey.settings.tab.gestures"), btn -> switchTab(2))
                .bounds(tabStartX + 2 * (tabWidth + 4), tabY, tabWidth, tabHeight).build();
        tabGesturesBtn.active = (activeTab != 2);
        this.addRenderableWidget(tabGesturesBtn);

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
            this.addRenderableWidget(CycleButton.onOffBuilder(config.sort.enabled)
                    .create(leftX, rowStartY, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.enabled"),
                            (button, value) -> config.sort.enabled = value));

            this.addRenderableWidget(CycleButton.onOffBuilder(config.sort.mergeStacks)
                    .create(rightX, rowStartY, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.mergeStacks"),
                            (button, value) -> config.sort.mergeStacks = value));

            // Row 2: 整理包含快捷栏 / 冷却时间
            this.addRenderableWidget(CycleButton.onOffBuilder(config.sort.includeHotbar)
                    .create(leftX, rowStartY + rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.includeHotbar"),
                            (button, value) -> config.sort.includeHotbar = value));

            this.addRenderableWidget(CycleButton.<Integer>builder(val -> Component.literal(val + " ms"), () -> config.sort.cooldownMs)
                    .withValues(100, 150, 200, 250, 300, 500)
                    .create(rightX, rowStartY + rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.cooldown"),
                            (button, value) -> config.sort.cooldownMs = value));

            // Row 3: 分类分组 / 名称排序
            this.addRenderableWidget(CycleButton.onOffBuilder(config.sort.groupByCategory)
                    .create(leftX, rowStartY + 2 * rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.groupByCategory"),
                            (button, value) -> config.sort.groupByCategory = value));

            this.addRenderableWidget(CycleButton.onOffBuilder(config.sort.sortByName)
                    .create(rightX, rowStartY + 2 * rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.sortByName"),
                            (button, value) -> config.sort.sortByName = value));

            // Row 4: 数量降序
            this.addRenderableWidget(CycleButton.onOffBuilder(config.sort.sortByCountDesc)
                    .create(leftX, rowStartY + 3 * rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.sort.sortByCountDesc"),
                            (button, value) -> config.sort.sortByCountDesc = value));

        } else if (activeTab == 1) {
            // Tab 1: 快速堆叠与一键存入
            // Row 1: 准星瞄准优先 / 智能分类防污染
            this.addRenderableWidget(CycleButton.onOffBuilder(config.quickStack.crosshairPriority)
                    .create(leftX, rowStartY, btnWidth, btnHeight, Component.translatable("onekey.settings.quickStack.crosshairPriority"),
                            (button, value) -> config.quickStack.crosshairPriority = value));

            this.addRenderableWidget(CycleButton.onOffBuilder(config.quickStack.smartMatching)
                    .create(rightX, rowStartY, btnWidth, btnHeight, Component.translatable("onekey.settings.quickStack.smartMatching"),
                            (button, value) -> config.quickStack.smartMatching = value));

            // Row 2: 堆叠扫描半径 / 存入模式
            this.addRenderableWidget(CycleButton.<Integer>builder(val -> Component.literal(val + " 格"), () -> config.quickStack.radius)
                    .withValues(2, 4, 6, 8)
                    .create(leftX, rowStartY + rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.quickStack.radius"),
                            (button, value) -> config.quickStack.radius = value));

            this.addRenderableWidget(CycleButton.<Boolean>builder(
                            val -> Component.translatable(val ? "onekey.settings.stashMode.all" : "onekey.settings.stashMode.matching"),
                            () -> config.quickStack.stashMode)
                    .withValues(false, true)
                    .create(rightX, rowStartY + rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.quickStack.stashMode"),
                            (button, value) -> config.quickStack.stashMode = value));

        } else {
            // Tab 2: 鼠标手势
            // Row 1: Shift左键扫掠转移 / 右键划动重复放置
            this.addRenderableWidget(CycleButton.onOffBuilder(config.gestures.lmbDragSweep)
                    .create(leftX, rowStartY, btnWidth, btnHeight, Component.translatable("onekey.settings.gestures.lmbDragSweep"),
                            (button, value) -> config.gestures.lmbDragSweep = value));

            this.addRenderableWidget(CycleButton.onOffBuilder(config.gestures.rmbDragRepeat)
                    .create(rightX, rowStartY, btnWidth, btnHeight, Component.translatable("onekey.settings.gestures.rmbDragRepeat"),
                            (button, value) -> config.gestures.rmbDragRepeat = value));

            // Row 2: 滚轮方向反转 / 持物滚轮微调
            this.addRenderableWidget(CycleButton.onOffBuilder(config.gestures.invertWheel)
                    .create(leftX, rowStartY + rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.gestures.invertWheel"),
                            (button, value) -> config.gestures.invertWheel = value));

            this.addRenderableWidget(CycleButton.onOffBuilder(config.gestures.wheelCarriedHandling)
                    .create(rightX, rowStartY + rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.gestures.wheelCarriedHandling"),
                            (button, value) -> config.gestures.wheelCarriedHandling = value));

            // Row 3: 手势影响快捷栏
            this.addRenderableWidget(CycleButton.onOffBuilder(config.gestures.includeHotbar)
                    .create(leftX, rowStartY + 2 * rowGap, btnWidth, btnHeight, Component.translatable("onekey.settings.gestures.includeHotbar"),
                            (button, value) -> config.gestures.includeHotbar = value));
        }

        // 3. 底部操作按钮：配置原版按键绑定 / 完成
        this.addRenderableWidget(Button.builder(Component.translatable("onekey.settings.openKeybinds"), btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new KeyBindsScreen(this, this.minecraft.options));
            }
        }).bounds(leftX, this.height - 28, btnWidth, btnHeight).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> onClose())
                .bounds(rightX, this.height - 28, btnWidth, btnHeight).build());
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
                extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.locking"), this.width / 2, tipY2, 0xFFAAAAAA);
            }
        } else if (activeTab == 1) {
            int tipY1 = Math.max(115, bottomButtonY - 32);
            int tipY2 = Math.max(129, bottomButtonY - 18);
            if (tipY1 < bottomButtonY - 14) {
                extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.stack_1"), this.width / 2, tipY1, 0xFF55FFFF);
                extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.stack_2"), this.width / 2, tipY2, 0xFFAAAAAA);
            }
        } else {
            int tipY1 = Math.max(135, bottomButtonY - 44);
            int tipY2 = Math.max(149, bottomButtonY - 30);
            int tipY3 = Math.max(163, bottomButtonY - 16);
            if (tipY1 < bottomButtonY - 26) {
                extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.sweep"), this.width / 2, tipY1, 0xFFFFB300);
                extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.repeat"), this.width / 2, tipY2, 0xFF55FFFF);
                extractor.centeredText(this.font, Component.translatable("onekey.settings.tip.wheel"), this.width / 2, tipY3, 0xFFAAAAAA);
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
