package routu.bbsuirabbit.client.ui.assets;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import net.minecraft.class_332;

import java.lang.reflect.Field;
import java.util.Deque;

/**
 * Exact undo for a preview that throws mid-frame: {@link #capture} snapshots the scissor
 * and model-view stack depths right before the risky call, {@link #restore} pops only what
 * the failed call added — deeper scissor layers go back through {@code disableScissor} (it
 * reapplies the layer underneath), one model-view level via {@link #restoreMatrices} (that
 * also brings the projection and inverse view back), extras pop to the snapshot.
 *
 * The private deques are read by reflection (intermediary names, production runtime) rather
 * than accessor mixins: a mixin on DrawContext that fails to apply kills the game at launch,
 * while a failed lookup here only skips that part of the recovery.
 */
public final class GLStateGuard
{
    private static Field drawContextScissors;
    private static Field scissorDeque;
    private static Field matrixEntries;
    private static boolean reflected;

    private int scissorDepth = -1;
    private int modelViewDepth = -1;

    private GLStateGuard()
    {}

    public static GLStateGuard capture(UIContext context)
    {
        GLStateGuard guard = new GLStateGuard();
        Deque<?> scissors = scissorDeque(context);
        Deque<?> modelView = modelViewDeque();

        guard.scissorDepth = scissors == null ? -1 : scissors.size();
        guard.modelViewDepth = modelView == null ? -1 : modelView.size();

        return guard;
    }

    public void restore(UIContext context)
    {
        try
        {
            Deque<?> scissors = scissorDeque(context);

            if (scissors != null && this.scissorDepth >= 0)
            {
                class_332 drawContext = context.batcher.getContext();

                for (int guard = 0; scissors.size() > this.scissorDepth && guard < 16; guard++)
                {
                    drawContext.method_44380();
                }
            }
        }
        catch (Throwable ignored)
        {}

        try
        {
            Deque<?> modelView = modelViewDeque();

            if (modelView != null && this.modelViewDepth >= 0 && modelView.size() > this.modelViewDepth)
            {
                /* restoreMatrices pops the cacheMatrices frame and brings the projection
                 * and inverse view matrix back in one go */
                MatrixStackUtils.restoreMatrices();

                for (int guard = 0; modelView.size() > this.modelViewDepth && guard < 16; guard++)
                {
                    RenderSystem.getModelViewStack().method_22909();
                }

                RenderSystem.applyModelViewMatrix();
            }
        }
        catch (Throwable ignored)
        {}

        try
        {
            net.minecraft.class_1041 window = net.minecraft.class_310.method_1551().method_22683();

            RenderSystem.viewport(0, 0, window.method_4480(), window.method_4502());
        }
        catch (Throwable ignored)
        {}

        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    private static void reflect()
    {
        if (reflected)
        {
            return;
        }

        reflected = true;

        try
        {
            drawContextScissors = class_332.class.getDeclaredField("field_44659");
            drawContextScissors.setAccessible(true);
            scissorDeque = drawContextScissors.getType().getDeclaredField("field_43099");
            scissorDeque.setAccessible(true);
        }
        catch (Throwable e)
        {
            drawContextScissors = null;
            scissorDeque = null;
        }

        try
        {
            matrixEntries = net.minecraft.class_4587.class.getDeclaredField("field_20898");
            matrixEntries.setAccessible(true);
        }
        catch (Throwable e)
        {
            matrixEntries = null;
        }
    }

    private static Deque<?> scissorDeque(UIContext context)
    {
        reflect();

        try
        {
            return scissorDeque == null ? null : (Deque<?>) scissorDeque.get(drawContextScissors.get(context.batcher.getContext()));
        }
        catch (Throwable e)
        {
            return null;
        }
    }

    private static Deque<?> modelViewDeque()
    {
        reflect();

        try
        {
            return matrixEntries == null ? null : (Deque<?>) matrixEntries.get(RenderSystem.getModelViewStack());
        }
        catch (Throwable e)
        {
            return null;
        }
    }
}
