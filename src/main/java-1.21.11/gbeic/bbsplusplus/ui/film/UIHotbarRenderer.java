package gbeic.bbsplusplus.ui.film;

import gbeic.bbsplusplus.clips.HotbarState;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Random;

/**
 * 1.21.11 的热键栏 HUD 渲染。
 *
 * <p>与 1.20 版相比改了两层：</p>
 *
 * <ul>
 *   <li><b>纹理来源</b>：1.21.11 删掉了 {@code textures/gui/icons.png} 与
 *       {@code textures/gui/widgets.png} 这两张整图集，改成
 *       {@code textures/gui/sprites/hud/...} 下一个元素一张图（见 {@code InGameHud} 与
 *       {@code ExperienceBar} 的反编译结果）。继续按旧的 256×256 图集 UV 采样会直接
 *       命中缺失资源，画面表现为纯黑底框 + 品红条，所以这里全部换成
 *       {@code DrawContext.drawGuiTexture(...)} 按 sprite id 绘制。</li>
 *   <li><b>提交方式</b>：平移/缩放改走 {@code context.getMatrices()}
 *       （{@code Matrix3x2fStack}，2D 栈）；深度/混合开关全部删掉（1.21.11 的 GUI
 *       两阶段合成自带固定状态）；整体 alpha 逐个图元塞进各自 {@code color} 参数。</li>
 * </ul>
 *
 * <p>布局、血量/饥饿/氧气/经验的分格算法、低血量抖动、再生心跳位与 1.20 版逐字一致。</p>
 *
 * <p>已知取舍：物品图标本身没有着色入口（{@code DrawContext.drawItem} 只收 seed），
 * 所以 {@code hotbar.alpha} 淡出时格内物品保持原样 —— 面板、血条、文字、经验条都会淡出。</p>
 */
public class UIHotbarRenderer
{
    private static final float REFERENCE_WIDTH = 1920F;
    private static final float REFERENCE_HEIGHT = 1080F;
    private static final int HUD_GREEN = 8453920;
    private static final int BAR_ICON_Y = -17;
    private static final int EXPERIENCE_BAR_Y = -7;
    private static final int EXPERIENCE_TEXT_Y = -12;
    private static final int EXPERIENCE_BAR_WIDTH = 182;
    private static final int EXPERIENCE_BAR_HEIGHT = 5;
    private static final float SCALE_PIVOT_X = 91F;
    private static final float SCALE_PIVOT_Y = 0.5F;
    private static final int MAX_HEALTH_ROWS = 60;
    private static final float MAX_HEALTH_CONTAINER = MAX_HEALTH_ROWS * 10F * 2F;

    private static final Identifier HOTBAR = sprite("hud/hotbar");
    private static final Identifier HOTBAR_SELECTION = sprite("hud/hotbar_selection");
    /* 副手框固定用左臂版：HotbarState 没有 Arm 字段，1.20 版同样硬编码左臂（UV 24,22）。 */
    private static final Identifier HOTBAR_OFFHAND_LEFT = sprite("hud/hotbar_offhand_left");

    private static final Identifier EXPERIENCE_BACKGROUND = sprite("hud/experience_bar_background");
    private static final Identifier EXPERIENCE_PROGRESS = sprite("hud/experience_bar_progress");

    private static final Identifier ARMOR_EMPTY = sprite("hud/armor_empty");
    private static final Identifier ARMOR_HALF = sprite("hud/armor_half");
    private static final Identifier ARMOR_FULL = sprite("hud/armor_full");

    private static final Identifier FOOD_EMPTY = sprite("hud/food_empty");
    private static final Identifier FOOD_HALF = sprite("hud/food_half");
    private static final Identifier FOOD_FULL = sprite("hud/food_full");
    private static final Identifier FOOD_EMPTY_HUNGER = sprite("hud/food_empty_hunger");
    private static final Identifier FOOD_HALF_HUNGER = sprite("hud/food_half_hunger");
    private static final Identifier FOOD_FULL_HUNGER = sprite("hud/food_full_hunger");

    private static final Identifier AIR = sprite("hud/air");
    private static final Identifier AIR_BURSTING = sprite("hud/air_bursting");

    private static final Identifier HEART_CONTAINER = sprite("hud/heart/container");
    private static final Identifier HEART_CONTAINER_HARDCORE = sprite("hud/heart/container_hardcore");

    private static boolean wasHeartRegenerationEnabled;
    private static long heartRegenerationStartTick;

    private static Identifier sprite(String path)
    {
        return Identifier.of("minecraft", path);
    }

    public static void renderHotbars(MatrixStack stack, Batcher2D batcher, List<HotbarState> hotbars)
    {
        if (hotbars == null || hotbars.isEmpty())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();

        renderHotbars(stack, batcher, hotbars, 0, 0, width, height);
    }

    public static void renderHotbars(MatrixStack stack, Batcher2D batcher, List<HotbarState> hotbars, int originX, int originY, int width, int height)
    {
        if (hotbars == null || hotbars.isEmpty())
        {
            return;
        }

        for (HotbarState hotbar : hotbars)
        {
            renderHotbar(stack, batcher, hotbar, originX, originY, width, height);
        }
    }

    public static void renderHotbar(MatrixStack stack, Batcher2D batcher, HotbarState hotbar, int originX, int originY, int width, int height)
    {
        float alpha = MathHelper.clamp(hotbar.alpha, 0F, 1F);

        if (alpha <= 0F)
        {
            return;
        }

        float resolutionScale = getResolutionScale(width, height);
        float scale = Math.max(0.05F, hotbar.scale) * resolutionScale;
        int hotbarWidth = 182;
        int x = originX + Math.round(width / 2F + hotbar.x * resolutionScale - hotbarWidth / 2F);
        int y = originY + Math.round(height - (22 + 9) * resolutionScale + hotbar.y * resolutionScale);

        int tint = applyAlpha(0xFFFFFFFF, alpha);
        var matrices = batcher.getContext().getMatrices();

        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.translate(SCALE_PIVOT_X, SCALE_PIVOT_Y);
        matrices.scale(scale, scale);
        matrices.translate(-SCALE_PIVOT_X, -SCALE_PIVOT_Y);

        drawSprite(batcher, HOTBAR, 0, 0, 182, 22, tint);

        boolean hasOffhandItem = hotbar.offhandItem != null && !hotbar.offhandItem.isEmpty();
        if (hasOffhandItem)
        {
            drawSprite(batcher, HOTBAR_OFFHAND_LEFT, -29, -1, 29, 24, tint);
        }

        int selectedSlot = MathHelper.clamp(hotbar.selectedSlot, 0, 8);
        drawSprite(batcher, HOTBAR_SELECTION, selectedSlot * 20 - 1, -1, 24, 23, tint);

        int barsY = BAR_ICON_Y;

        int healthSlots = MathHelper.ceil(MathHelper.clamp(hotbar.healthContainer, 0F, MAX_HEALTH_CONTAINER) / 2F);
        healthSlots = MathHelper.clamp(healthSlots, 0, MAX_HEALTH_ROWS * 10);
        int healthRows = Math.max(1, Math.min(MAX_HEALTH_ROWS, (healthSlots + 9) / 10));
        int absorptionSlots = MathHelper.ceil(MathHelper.clamp(hotbar.absorptionContainer, 0F, MAX_HEALTH_CONTAINER) / 2F);
        absorptionSlots = MathHelper.clamp(absorptionSlots, 0, MAX_HEALTH_ROWS * 10);
        int absorptionRows = absorptionSlots <= 0 ? 0 : Math.max(1, Math.min(MAX_HEALTH_ROWS, (absorptionSlots + 9) / 10));
        Random heartShakeRandom = hotbar.health <= 4F ? new Random(thisTickSeed()) : null;
        Random hungerShakeRandom = hotbar.hunger <= 6F ? new Random(thisTickSeed() + 17L) : null;

        int regenerationHeartIndex = -1;
        long hudTick = MinecraftClient.getInstance().world != null ? MinecraftClient.getInstance().world.getTime() : System.currentTimeMillis() / 50L;

        if (hotbar.heartRegeneration && healthSlots > 0 && hotbar.health > 0F)
        {
            if (!wasHeartRegenerationEnabled)
            {
                heartRegenerationStartTick = hudTick;
            }
            wasHeartRegenerationEnabled = true;
            int cycleLength = healthSlots + 5;
            int cycleIndex = cycleLength <= 0 ? 0 : (int) Math.floorMod(hudTick - heartRegenerationStartTick, cycleLength);
            regenerationHeartIndex = cycleIndex < healthSlots ? cycleIndex : -1;
        }
        else if (wasHeartRegenerationEnabled)
        {
            wasHeartRegenerationEnabled = false;
        }

        boolean hardcore = hotbar.hardcore;
        Identifier container = hardcore ? HEART_CONTAINER_HARDCORE : HEART_CONTAINER;
        Identifier heartHalf = heartSprite(hotbar.heartType, hardcore, true);
        Identifier heartFull = heartSprite(hotbar.heartType, hardcore, false);

        renderBar(batcher, hotbar.health, container, heartHalf, heartFull, 0, barsY, healthSlots, heartShakeRandom, regenerationHeartIndex, tint);
        if (absorptionSlots > 0)
        {
            Identifier absorptionHalf = heartSprite(HotbarState.HEART_ABSORBING, hardcore, true);
            Identifier absorptionFull = heartSprite(HotbarState.HEART_ABSORBING, hardcore, false);

            renderBar(batcher, hotbar.absorption, container, absorptionHalf, absorptionFull, 0, barsY - healthRows * 10, absorptionSlots, heartShakeRandom, -1, tint);
        }
        if (hotbar.armor > 0F)
        {
            renderBar(batcher, hotbar.armor, ARMOR_EMPTY, ARMOR_HALF, ARMOR_FULL, 0, barsY - (healthRows + absorptionRows) * 10, 10, null, -1, tint);
        }

        Identifier foodEmpty = hotbar.hungerEffect ? FOOD_EMPTY_HUNGER : FOOD_EMPTY;
        Identifier foodHalf = hotbar.hungerEffect ? FOOD_HALF_HUNGER : FOOD_HALF;
        Identifier foodFull = hotbar.hungerEffect ? FOOD_FULL_HUNGER : FOOD_FULL;

        renderBarReverse(batcher, hotbar.hunger, foodEmpty, foodHalf, foodFull, 182 - 9, barsY, 10, hungerShakeRandom, tint);

        renderAirBar(batcher, hotbar.air, 182 - 9, barsY - 10, tint);

        float experience = MathHelper.clamp(hotbar.experience, 0F, 1F);
        int xpPixels = MathHelper.ceil(experience * EXPERIENCE_BAR_WIDTH);
        drawSprite(batcher, EXPERIENCE_BACKGROUND, 0, EXPERIENCE_BAR_Y, EXPERIENCE_BAR_WIDTH, EXPERIENCE_BAR_HEIGHT, tint);
        if (xpPixels > 0)
        {
            drawSpriteRegion(batcher, EXPERIENCE_PROGRESS, EXPERIENCE_BAR_WIDTH, EXPERIENCE_BAR_HEIGHT, 0, 0, 0, EXPERIENCE_BAR_Y, xpPixels, EXPERIENCE_BAR_HEIGHT, tint);
        }

        if (hotbar.experienceLevel > 0)
        {
            String level = Integer.toString(hotbar.experienceLevel);
            int levelX = (182 - batcher.getFont().getWidth(level)) / 2;

            batcher.textShadow(level, levelX, EXPERIENCE_TEXT_Y, applyAlpha(HUD_GREEN, alpha));
        }

        for (int i = 0; i < 9; i++)
        {
            ItemStack stackItem = hotbar.items[i];

            if (stackItem == null || stackItem.isEmpty())
            {
                continue;
            }

            int itemX = 3 + i * 20;
            int itemY = 3;

            batcher.getContext().drawItem(stackItem, itemX, itemY);
            batcher.getContext().drawStackOverlay(batcher.getFont().getRenderer(), stackItem, itemX, itemY);
        }

        if (hasOffhandItem)
        {
            int offhandX = -26;
            int offhandY = 3;

            batcher.getContext().drawItem(hotbar.offhandItem, offhandX, offhandY);
            batcher.getContext().drawStackOverlay(batcher.getFont().getRenderer(), hotbar.offhandItem, offhandX, offhandY);
        }

        matrices.popMatrix();
    }

    private static float getResolutionScale(int width, int height)
    {
        if (width <= 0 || height <= 0)
        {
            return 1F;
        }

        return Math.max(0.05F, Math.min(width / REFERENCE_WIDTH, height / REFERENCE_HEIGHT));
    }

    private static void renderBar(Batcher2D batcher, float value, Identifier empty, Identifier half, Identifier full, int x, int y, int slots, Random lowHealthShakeRandom, int regenerationHeartIndex, int tint)
    {
        if (slots <= 0)
        {
            return;
        }

        float normalized = MathHelper.clamp(value, 0F, slots * 2F) / 2F;

        for (int i = 0; i < slots; i++)
        {
            int row = i / 10;
            int col = i % 10;
            int iconX = x + col * 8;
            int iconY = y - row * 10;

            if (lowHealthShakeRandom != null)
            {
                iconY += lowHealthShakeRandom.nextInt(2);
            }

            if (i == regenerationHeartIndex)
            {
                iconY -= 2;
            }

            drawSprite(batcher, empty, iconX, iconY, 9, 9, tint);

            float current = normalized - i;

            if (current >= 1F)
            {
                drawSprite(batcher, full, iconX, iconY, 9, 9, tint);
            }
            else if (current >= 0.5F)
            {
                drawSprite(batcher, half, iconX, iconY, 9, 9, tint);
            }
        }
    }

    private static long thisTickSeed()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        long tick = mc.world != null ? mc.world.getTime() : System.currentTimeMillis() / 50L;

        return tick * 312871L;
    }

    private static void renderBarReverse(Batcher2D batcher, float value, Identifier empty, Identifier half, Identifier full, int x, int y, int slots, Random lowHungerShakeRandom, int tint)
    {
        if (slots <= 0)
        {
            return;
        }

        float normalized = MathHelper.clamp(value, 0F, slots * 2F) / 2F;

        for (int i = 0; i < slots; i++)
        {
            int row = i / 10;
            int col = i % 10;
            int iconX = x - col * 8;
            int iconY = y - row * 10;

            if (lowHungerShakeRandom != null)
            {
                iconY += lowHungerShakeRandom.nextInt(2);
            }

            drawSprite(batcher, empty, iconX, iconY, 9, 9, tint);

            float current = normalized - i;

            if (current >= 1F)
            {
                drawSprite(batcher, full, iconX, iconY, 9, 9, tint);
            }
            else if (current >= 0.5F)
            {
                drawSprite(batcher, half, iconX, iconY, 9, 9, tint);
            }
        }
    }

    private static int applyAlpha(int color, float alpha)
    {
        int a = MathHelper.clamp(Math.round(MathHelper.clamp(alpha, 0F, 1F) * 255F), 0, 255);

        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static void renderAirBar(Batcher2D batcher, float air, int x, int y, int tint)
    {
        if (air >= 300F)
        {
            return;
        }

        int full = MathHelper.ceil((air - 2F) * 10F / 300F);
        int popping = MathHelper.ceil(air * 10F / 300F) - full;

        full = MathHelper.clamp(full, 0, 10);
        popping = MathHelper.clamp(popping, 0, 10 - full);

        for (int i = 0; i < full + popping; i++)
        {
            int iconX = x - i * 8;

            drawSprite(batcher, i < full ? AIR : AIR_BURSTING, iconX, y, 9, 9, tint);
        }
    }

    private static Identifier heartSprite(int heartType, boolean hardcore, boolean half)
    {
        String prefix = switch (heartType)
        {
            case HotbarState.HEART_POISONED -> "poisoned";
            case HotbarState.HEART_WITHERED -> "withered";
            case HotbarState.HEART_ABSORBING -> "absorbing";
            case HotbarState.HEART_FROZEN -> "frozen";
            default -> "";
        };

        String hardcorePart = hardcore ? "hardcore_" : "";
        String name = prefix.isEmpty()
            ? "hud/heart/" + hardcorePart + (half ? "half" : "full")
            : "hud/heart/" + prefix + "_" + hardcorePart + (half ? "half" : "full");

        return sprite(name);
    }

    private static void drawSprite(Batcher2D batcher, Identifier texture, int x, int y, int w, int h, int tint)
    {
        batcher.getContext().drawGuiTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, w, h, tint);
    }

    private static void drawSpriteRegion(Batcher2D batcher, Identifier texture, int regionW, int regionH, int u, int v, int x, int y, int w, int h, int tint)
    {
        batcher.getContext().drawGuiTexture(RenderPipelines.GUI_TEXTURED, texture, regionW, regionH, u, v, x, y, w, h, tint);
    }
}
