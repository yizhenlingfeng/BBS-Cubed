package gbeic.bbsplusplus.client.structure;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * BBS VFX 破坏魔杖的选区增强。
 * <p>
 * VFX 原版魔杖只在方块交互回调里用潜行 / 非潜行区分两个点，既没有拖拽预览也没有
 * 清晰的端点反馈。这里沿用结构棒的交互思路，在 BBS++ 客户端侧接管
 * 新版 {@code bbsvfx:destruction_wand} 与旧版 {@code xavin:destruction_wand}：长按使用键拖拽选区，短按使用键移动起点，短按攻击键
 * 移动终点，长按攻击键清除选区，并把最终选区同步回 VFX 的
 * {@code DestructionSelection} 静态字段，供原插件的“捕获破坏盒”按钮读取。
 * </p>
 */
public final class VFXDestructionWandSelection
{
    private static final Identifier BBSVFX_DESTRUCTION_WAND = Identifier.of("bbsvfx", "destruction_wand");
    private static final Identifier XAVIN_DESTRUCTION_WAND = Identifier.of("xavin", "destruction_wand");
    private static final String[] VFX_SELECTION_CLASSES = {
        "com.bbsvfx.bbsvfx.DestructionSelection",
        "org.xavin.xavin.DestructionSelection"
    };

    /** 按住使用键超过这么多 tick 才算拖拽，否则视为单击 */
    private static final int HOLD_THRESHOLD_TICKS = 8;

    /** 拖拽音效的最小间隔 */
    private static final int DRAG_SOUND_COOLDOWN_TICKS = 1;

    /** 长按攻击键达到这么多 tick 清除选区 */
    private static final int ATTACK_CLEAR_TICKS = 40;

    /** 准星没有指向方块时，取视线前方这么远的位置 */
    private static final double FALLBACK_TARGET_DISTANCE = 4.0D;

    /** 射线检测距离 */
    private static final double RAYCAST_DISTANCE = 5.0D;

    private static final float CUSTOM_SOUND_VOLUME = 1.5F;
    private static final float LINE_WIDTH = 3.0F;

    /**
     * 1.21.11 起没有 {@code RenderSystem.setShader} / {@code BufferRenderer} / {@code lineWidth} 这套
     * 立即模式接口，线段必须走 RenderLayer。
     * <p>
     * 直接复用原版 {@code RenderPipelines.LINES}（顶点格式 {@code POSITION_COLOR_NORMAL_LINE_WIDTH}）：
     * 只有带 LineWidth 元素，{@code VertexRendering.drawOutline(...)} 写进去的 3px 线宽才会生效；
     * 纯 POSITION_COLOR 的线恒为 1px，与原实现不符。
     */
    private static final RenderLayer LINE_LAYER = RenderLayer.of("bbsplusplus_vfx_wand_lines",
        RenderSetup.builder(RenderPipelines.LINES).translucent().build());

    /** 拖拽选区提示音（沿用原结构棒音效资源，结构棒移除后在此自持 SoundEvent）。 */
    private static final SoundEvent DRAG_SOUND = SoundEvent.of(Identifier.of("bbspp", "structure_stick_drag"));

    private static BlockPos start;
    private static BlockPos end;

    private static BlockPos dragStart;
    private static BlockPos dragEnd;

    private static boolean using;
    private static int useTicks;
    private static boolean longAction;

    private static int attackTicks;
    private static boolean attackLongHandled;
    private static boolean attackSessionActive;

    private static long lastDragMeasure;
    private static int soundCooldown;
    private static float dragPitch = 1.0F;

    private static final List<Field[]> VFX_SELECTION_FIELDS = new ArrayList<>();
    private static boolean reflectionResolved;
    private static boolean reflectionAvailable;

    private VFXDestructionWandSelection()
    {}

    public static void register()
    {
        ClientTickEvents.END_CLIENT_TICK.register(VFXDestructionWandSelection::tick);
        /* 1.21.11 的 WorldRenderEvents 没有 LAST，END_MAIN 是等价的“主通道渲染收尾”时机。 */
        WorldRenderEvents.END_MAIN.register(VFXDestructionWandSelection::render);
    }

    /** 手持破坏魔杖且没打开界面时，屏蔽 VFX 原本的方块交互回调。 */
    public static boolean shouldBlockUse(MinecraftClient client)
    {
        return isHoldingWand(client) && client.currentScreen == null;
    }

    /** 手持破坏魔杖且没打开界面时，屏蔽原版挖掘与攻击。 */
    public static boolean shouldBlockAttack(MinecraftClient client)
    {
        return isHoldingWand(client) && client.currentScreen == null;
    }

    /** 只有手持破坏魔杖时才显示破坏盒预览边框。 */
    public static boolean shouldRenderSelection(MinecraftClient client)
    {
        return isHoldingWand(client);
    }

    public static boolean isDestructionWand(ItemStack stack)
    {
        if (stack == null)
        {
            return false;
        }

        Identifier itemId = Registries.ITEM.getId(stack.getItem());

        return BBSVFX_DESTRUCTION_WAND.equals(itemId) || XAVIN_DESTRUCTION_WAND.equals(itemId);
    }

    private static void tick(MinecraftClient client)
    {
        if (!isHoldingWand(client))
        {
            resetInputState();

            return;
        }

        if (client.currentScreen != null)
        {
            resetActiveInputState();
            syncSelection();

            return;
        }

        if (soundCooldown > 0)
        {
            soundCooldown--;
        }

        boolean usePressed = client.options.useKey.isPressed();
        boolean attackPressed = isAttackPressed(client);

        if (usePressed && !using)
        {
            beginUse(client);
        }
        else if (usePressed)
        {
            continueUse(client);
        }
        else if (using)
        {
            finishUse(client);
        }

        if (attackPressed)
        {
            handleAttackHold(client);
        }
        else if (attackSessionActive)
        {
            finishAttack(client);
        }

        syncSelection();
    }

    private static void beginUse(MinecraftClient client)
    {
        using = true;
        useTicks = 0;
        longAction = false;
        dragStart = targetBlock(client);
        dragEnd = dragStart;
        lastDragMeasure = getDragMeasure(dragStart, dragEnd);
        soundCooldown = 0;
        dragPitch = 1.0F;

        playSound(client, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
    }

    private static void continueUse(MinecraftClient client)
    {
        dragEnd = targetBlock(client);

        updateDragSound(client);

        if (++useTicks >= HOLD_THRESHOLD_TICKS)
        {
            longAction = true;
        }
    }

    private static void finishUse(MinecraftClient client)
    {
        boolean completedDrag = false;

        if (longAction)
        {
            if (dragStart != null && dragEnd != null)
            {
                start = dragStart;
                end = dragEnd;
                completedDrag = true;

                notify(client, Text.translatable("bbsplusplus.vfx_wand.selected"));
            }
        }
        else if (start != null && end != null)
        {
            start = targetBlock(client);

            notify(client, Text.translatable("bbsplusplus.vfx_wand.start_moved"));
        }
        else
        {
            start = targetBlock(client);
            end = start;

            notify(client, Text.translatable("bbsplusplus.vfx_wand.selected"));
        }

        if (completedDrag)
        {
            playSound(client, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.5F);
        }

        using = false;
        useTicks = 0;
        longAction = false;
        dragStart = null;
        dragEnd = null;
        lastDragMeasure = 0L;
    }

    /**
     * 拖拽时按选区体积变化播放提示音，越拖越大音高上升，反之下降。
     */
    private static void updateDragSound(MinecraftClient client)
    {
        if (dragStart == null || dragEnd == null || soundCooldown > 0)
        {
            return;
        }

        long measure = getDragMeasure(dragStart, dragEnd);

        if (measure == lastDragMeasure)
        {
            return;
        }

        dragPitch = measure > lastDragMeasure
            ? Math.min(2.0F, dragPitch + 0.1F)
            : Math.max(0.5F, dragPitch - 0.1F);
        lastDragMeasure = measure;
        soundCooldown = DRAG_SOUND_COOLDOWN_TICKS;

        playSound(client, DRAG_SOUND, CUSTOM_SOUND_VOLUME, dragPitch);
    }

    private static void handleAttackHold(MinecraftClient client)
    {
        if (using)
        {
            return;
        }

        if (!attackSessionActive)
        {
            attackSessionActive = true;
            attackTicks = 0;
            attackLongHandled = false;
        }

        if (attackLongHandled)
        {
            return;
        }

        if (start != null && end != null && ++attackTicks >= ATTACK_CLEAR_TICKS)
        {
            clearSelection(client);

            attackLongHandled = true;
        }
    }

    private static void finishAttack(MinecraftClient client)
    {
        if (!attackLongHandled && attackTicks >= ATTACK_CLEAR_TICKS && start != null && end != null)
        {
            clearSelection(client);

            attackLongHandled = true;
        }

        if (!attackLongHandled)
        {
            moveEnd(client);
        }

        attackTicks = 0;
        attackLongHandled = false;
        attackSessionActive = false;
    }

    private static void moveEnd(MinecraftClient client)
    {
        if (start == null)
        {
            start = targetBlock(client);
        }

        end = targetBlock(client);

        notify(client, Text.translatable("bbsplusplus.vfx_wand.end_moved"));
    }

    private static void clearSelection(MinecraftClient client)
    {
        start = null;
        end = null;
        dragStart = null;
        dragEnd = null;

        syncSelection();
        notify(client, Text.translatable("bbsplusplus.vfx_wand.cleared"));
    }

    private static void syncSelection()
    {
        if (!resolveReflection())
        {
            return;
        }

        boolean synchronizedAny = false;

        for (Field[] fields : VFX_SELECTION_FIELDS)
        {
            try
            {
                fields[0].set(null, start);
                fields[1].set(null, end);
                synchronizedAny = true;
            }
            catch (Exception ignored)
            {
                // 某个兼容版本同步失败时，继续尝试另一个已加载版本。
            }
        }

        reflectionAvailable = synchronizedAny;
    }

    private static boolean resolveReflection()
    {
        if (reflectionResolved)
        {
            return reflectionAvailable;
        }

        reflectionResolved = true;

        for (String className : VFX_SELECTION_CLASSES)
        {
            try
            {
                Class<?> selection = Class.forName(className);

                VFX_SELECTION_FIELDS.add(new Field[] {
                    selection.getField("pos1"),
                    selection.getField("pos2")
                });
            }
            catch (Exception ignored)
            {
                // 未安装对应版本时继续尝试另一个包名。
            }
        }

        reflectionAvailable = !VFX_SELECTION_FIELDS.isEmpty();

        return reflectionAvailable;
    }

    /** 取准星指向的方块，没打到方块时取视线前方固定距离的位置。 */
    private static BlockPos targetBlock(MinecraftClient client)
    {
        if (client.player == null || client.world == null)
        {
            return BlockPos.ORIGIN;
        }

        Vec3d camera = client.player.getCameraPosVec(1.0F);
        Vec3d direction = client.player.getRotationVec(1.0F);
        Vec3d reach = camera.add(direction.multiply(RAYCAST_DISTANCE));
        BlockHitResult hit = client.world.raycast(new RaycastContext(
            camera, reach, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, client.player
        ));

        if (hit.getType() == HitResult.Type.BLOCK)
        {
            return hit.getBlockPos();
        }

        return BlockPos.ofFloored(camera.add(direction.multiply(FALLBACK_TARGET_DISTANCE)));
    }

    /**
     * 绘制选区线框：整体框、起点（绿）、终点（红），以及准星当前指向的方块（蓝）。
     */
    private static void render(WorldRenderContext context)
    {
        MinecraftClient client = MinecraftClient.getInstance();

        if (!shouldRenderSelection(client))
        {
            return;
        }

        BlockPos a = using ? dragStart : start;
        BlockPos b = using ? dragEnd : end;
        BlockPos preview = using ? null : targetBlock(client);

        if ((a == null || b == null) && preview == null)
        {
            return;
        }

        MatrixStack matrices = context.matrices();
        Vec3d cam = client.gameRenderer.getCamera().getCameraPos();

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);

        if (a != null && b != null)
        {
            drawBox(builder, matrices, bounds(a, b), 1.0F, 0.65F, using ? 0.0F : 0.2F, 1.0F);
            drawBox(builder, matrices, new Box(a), 0.0F, 1.0F, 0.0F, 1.0F);
            drawBox(builder, matrices, new Box(b), 1.0F, 0.0F, 0.0F, 1.0F);
        }

        if (preview != null)
        {
            drawBox(builder, matrices, new Box(preview), 0.1F, 0.45F, 1.0F, 1.0F);
        }

        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            LINE_LAYER.draw(built);
        }

        matrices.pop();
    }

    private static Box bounds(BlockPos a, BlockPos b)
    {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX()) + 1;
        int maxY = Math.max(a.getY(), b.getY()) + 1;
        int maxZ = Math.max(a.getZ(), b.getZ()) + 1;

        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** 选区体积，用来判断拖拽是变大还是变小。 */
    private static long getDragMeasure(BlockPos a, BlockPos b)
    {
        if (a == null || b == null)
        {
            return 0L;
        }

        long x = Math.abs(a.getX() - b.getX()) + 1L;
        long y = Math.abs(a.getY() - b.getY()) + 1L;
        long z = Math.abs(a.getZ() - b.getZ()) + 1L;

        return x * y * z;
    }

    /**
     * 1.21.11 的线段必须带 normal 与 LineWidth 两个元素才能画出粗线，自己拼这两项容易写错方向
     * （原版 {@code rendertype_lines.vsh} 用 {@code Position + Normal} 求屏幕方向），所以直接复用
     * {@link VertexRendering#drawOutline} —— 它内部按 {@code VoxelShape.forEachEdge} 把 12 条棱
     * 连同 normal 与 lineWidth 一起写进 builder。
     */
    private static void drawBox(BufferBuilder builder, MatrixStack matrices, Box box, float r, float g, float b, float a)
    {
        int color = ColorHelper.fromFloats(a, r, g, b);

        VertexRendering.drawOutline(matrices, builder, VoxelShapes.cuboid(box), 0.0D, 0.0D, 0.0D, color, LINE_WIDTH);
    }

    /**
     * 1.21.11 删除了 {@code PositionedSoundInstance.master(...)}，且现存工厂方法都固定了
     * {@code SoundCategory}（ui / music / record / ambient）。这里要保留原来的 MASTER 通道，
     * 所以直接用公开构造器（{@code SoundEvent, SoundCategory, volume, pitch, Random, BlockPos}）。
     */
    private static void playSound(MinecraftClient client, SoundEvent event, float volume, float pitch)
    {
        client.getSoundManager().play(new PositionedSoundInstance(event, SoundCategory.MASTER, volume, pitch,
            SoundInstance.createRandom(), BlockPos.ORIGIN));
    }

    private static boolean isHoldingWand(MinecraftClient client)
    {
        return client != null
            && client.player != null
            && isDestructionWand(client.player.getMainHandStack());
    }

    /**
     * 直接查询攻击键的物理按下状态，避免和原版按键消费逻辑互相影响。
     */
    private static boolean isAttackPressed(MinecraftClient client)
    {
        if (client == null || client.getWindow() == null)
        {
            return false;
        }

        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(client.options.attackKey);
        Window window = client.getWindow();
        long handle = window.getHandle();

        if (key.getCategory() == InputUtil.Type.MOUSE)
        {
            return GLFW.glfwGetMouseButton(handle, key.getCode()) == GLFW.GLFW_PRESS;
        }

        if (key.getCategory() == InputUtil.Type.KEYSYM)
        {
            /* 1.21.11 的 InputUtil.isKeyPressed 收 Window 而不是 GLFW 句柄。 */
            return InputUtil.isKeyPressed(window, key.getCode());
        }

        if (key.getCategory() == InputUtil.Type.SCANCODE)
        {
            return GLFW.glfwGetKeyScancode(key.getCode()) != -1 && InputUtil.isKeyPressed(window, key.getCode());
        }

        return client.options.attackKey.isPressed();
    }

    private static void resetInputState()
    {
        resetActiveInputState();
    }

    private static void resetActiveInputState()
    {
        using = false;
        useTicks = 0;
        longAction = false;
        dragStart = null;
        dragEnd = null;
        attackTicks = 0;
        attackLongHandled = false;
        attackSessionActive = false;
        lastDragMeasure = 0L;
        soundCooldown = 0;
        dragPitch = 1.0F;
    }

    private static void notify(MinecraftClient client, Text message)
    {
        if (client.player != null)
        {
            client.player.sendMessage(message, true);
        }
    }
}
