package me.easan.onekey.neoforge.client;

import java.util.List;

import me.easan.onekey.common.excavator.BlockInspector;
import me.easan.onekey.common.excavator.BlockPos3i;
import me.easan.onekey.common.excavator.Direction3i;
import me.easan.onekey.common.excavator.MiningShape;
import me.easan.onekey.common.excavator.VeinFinder;
import me.easan.onekey.neoforge.OneKeyNeoForge;
import me.easan.onekey.neoforge.net.ExcavatorStatePayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * NeoForge 客户端连锁按键、滚轮模式切换与 HUD 信息渲染中心。
 */
public final class ClientExcavatorHandler {

    private static KeyMapping excavateKey;
    private static boolean wasPressed = false;
    private static MiningShape currentShape = MiningShape.VEIN;
    private static long lastModeSwitchTime = 0L;

    private ClientExcavatorHandler() {
    }

    public static void init(KeyMapping key) {
        excavateKey = key;
    }

    public static boolean isExcavateKeyDown() {
        return excavateKey != null && excavateKey.isDown();
    }

    public static MiningShape getCurrentShape() {
        return currentShape;
    }

    public static void setCurrentShape(MiningShape shape) {
        if (shape != null) {
            currentShape = shape;
        }
    }

    public static void onClientTick(Minecraft client) {
        if (client.player == null) {
            return;
        }

        boolean isDown = isExcavateKeyDown();
        if (isDown != wasPressed) {
            wasPressed = isDown;
            ClientPacketDistributor.sendToServer(new ExcavatorStatePayload(isDown, currentShape.id()));
        }
    }

    public static boolean onMouseScroll(Minecraft client, double vertical) {
        if (client == null || client.player == null || (client.gui != null && client.gui.screen() != null)) {
            return false;
        }
        if (!isExcavateKeyDown()) {
            return false;
        }

        boolean invert = OneKeyNeoForge.config().gestures.invertWheel;
        boolean forward = invert ? vertical < 0 : vertical > 0;
        currentShape = currentShape.cycle(forward);
        lastModeSwitchTime = System.currentTimeMillis();

        ClientPacketDistributor.sendToServer(new ExcavatorStatePayload(true, currentShape.id()));

        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
        return true;
    }

    public static boolean isToolValidForBlock(LocalPlayer player, BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        ItemStack tool = player.getMainHandItem();
        if (OneKeyNeoForge.config().excavator.preventToolBreak && !tool.isEmpty() && tool.isDamageableItem()) {
            if (tool.getMaxDamage() - tool.getDamageValue() <= 2) {
                return false;
            }
        }
        if (OneKeyNeoForge.config().excavator.requireValidTool) {
            if (state.requiresCorrectToolForDrops()) {
                if (!player.hasCorrectToolForDrops(state) || tool.getDestroySpeed(state) <= 1.0F) {
                    return false;
                }
            } else {
                boolean isOrganic = state.is(BlockTags.LEAVES) || state.is(BlockTags.CROPS);
                if (!isOrganic && tool.getDestroySpeed(state) <= 1.0F) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isEquivalentOre(Block origin, Block current) {
        if (origin == current) {
            return true;
        }
        String origId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(origin).getPath();
        String currId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(current).getPath();
        String origBase = origId.startsWith("deepslate_") ? origId.substring("deepslate_".length()) : origId;
        String currBase = currId.startsWith("deepslate_") ? currId.substring("deepslate_".length()) : currId;
        return origBase.equals(currBase);
    }

    public static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) {
            return;
        }
        if (client.gui != null && (client.gui.screen() != null || (client.gui.hud != null && client.gui.hud.isHidden()))) {
            return;
        }
        if (!OneKeyNeoForge.config().excavator.enabled) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean isKeyDown = isExcavateKeyDown();
        boolean showToast = (now - lastModeSwitchTime) < 2500L;

        if (!isKeyDown && !showToast) {
            return;
        }

        Font font = client.font;
        int x = 6;
        int y = 6;

        String modeText = Component.translatable(currentShape.translationKey()).getString();
        String line1 = "§b[连锁破坏] §f模式: §e" + modeText;

        String line2;
        String line3 = null;
        String line4 = "§8[~ + 滚轮] 切换模式 | [松开] 退出";

        HitResult hit = client.hitResult;
        if (hit instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            BlockPos targetPos = bhr.getBlockPos();
            BlockState targetState = client.level.getBlockState(targetPos);
            String blockName = targetState.getBlock().getName().getString();

            boolean validTool = isToolValidForBlock(client.player, targetState);
            if (!validTool) {
                line2 = "§7目标: §f" + blockName + " §c[工具不匹配, 不触发]";
            } else {
                line2 = "§7目标: §e" + blockName + " §a[就绪]";

                // 实时预估方块数
                int maxBlocks = OneKeyNeoForge.config().excavator.maxBlocks;
                boolean includeLeaves = OneKeyNeoForge.config().excavator.includeLeaves;

                Direction playerDir = client.player.getDirection();
                Direction3i playerFacing = Direction3i.valueOf(playerDir.name());
                Direction hitSide = bhr.getDirection();
                Direction3i hitFace = Direction3i.valueOf(hitSide.name());
                boolean isUpperHalf = (bhr.getLocation().y - targetPos.getY()) > 0.5;

                BlockInspector inspector = new BlockInspector() {
                    @Override
                    public boolean matches(BlockPos3i origin, BlockPos3i current, boolean leavesAllowed) {
                        BlockPos cp = new BlockPos(current.x(), current.y(), current.z());
                        BlockState cs = client.level.getBlockState(cp);
                        if (cs.isAir()) {
                            return false;
                        }
                        if (cs.is(targetState.getBlock())) {
                            return true;
                        }
                        if (isEquivalentOre(targetState.getBlock(), cs.getBlock())) {
                            return true;
                        }
                        if ((targetState.is(BlockTags.IRON_ORES) && cs.is(BlockTags.IRON_ORES))
                                || (targetState.is(BlockTags.GOLD_ORES) && cs.is(BlockTags.GOLD_ORES))
                                || (targetState.is(BlockTags.COPPER_ORES) && cs.is(BlockTags.COPPER_ORES))) {
                            return true;
                        }
                        if (targetState.is(BlockTags.LOGS) && cs.is(BlockTags.LOGS)) {
                            return true;
                        }
                        if (leavesAllowed && targetState.is(BlockTags.LOGS) && cs.is(BlockTags.LEAVES)) {
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public boolean isDestructible(BlockPos3i pos) {
                        BlockPos cp = new BlockPos(pos.x(), pos.y(), pos.z());
                        BlockState cs = client.level.getBlockState(cp);
                        return isToolValidForBlock(client.player, cs);
                    }
                };

                BlockPos3i origin3i = new BlockPos3i(targetPos.getX(), targetPos.getY(), targetPos.getZ());
                List<BlockPos3i> previewBlocks = VeinFinder.findBlocks(
                        currentShape,
                        origin3i,
                        hitFace,
                        playerFacing,
                        inspector,
                        maxBlocks,
                        includeLeaves,
                        isUpperHalf
                );

                line3 = "§7预计破坏: §6" + previewBlocks.size() + " §7/ " + maxBlocks + " 块";
            }
        } else {
            line2 = "§7目标: §8无目标方块";
        }

        int width = Math.max(font.width(line1), Math.max(font.width(line2), font.width(line4)));
        if (line3 != null) {
            width = Math.max(width, font.width(line3));
        }

        int height = (line3 != null ? 4 : 3) * (font.lineHeight + 2) + 6;

        // 半透明深色圆角矩形背景与精致外边框
        graphics.fill(x - 4, y - 4, x + width + 4, y + height - 2, 0x88000000);
        graphics.outline(x - 4, y - 4, width + 8, height + 2, 0x33FFFFFF);

        int curY = y;
        graphics.text(font, line1, x, curY, 0xFFFFFFFF);
        curY += font.lineHeight + 2;

        graphics.text(font, line2, x, curY, 0xFFFFFFFF);
        curY += font.lineHeight + 2;

        if (line3 != null) {
            graphics.text(font, line3, x, curY, 0xFFFFFFFF);
            curY += font.lineHeight + 2;
        }

        graphics.text(font, line4, x, curY, 0xFFAAAAAA);
    }
}
