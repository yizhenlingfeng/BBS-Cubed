package gbeic.bbsplusplus.client.grade;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.render.CubicCubeRenderer;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import net.minecraft.client.render.RenderLayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动自检：确认 1.21.11 专属 mixin 真的被应用到了目标类上。
 *
 * <p>为什么需要它：这些注入全部是 {@code require = 0}（静默），而且其中多数依赖
 * 字符串选择器——{@code require = 0} 的代价是「没匹配上也不报错」。实机上唯一的症状是
 * 「功能没反应」，而玩家/开发者没有日志可查。Mixin 会把 handler 方法合并进目标类，所以
 * 在运行时用反射列出目标类的 {@code declaredMethods}，看到 {@code bbspp$<handler>} 就说明
 * 那一份注入确实生效了。</p>
 *
 * <p>本类只存在于 {@code src/main/java-1.21.11}，因此 1.20.x 构建里不存在；共享的
 * {@code BBSPlusPlusModClient} 通过 {@code Class.forName} 反射调用，类找不到就静默跳过。</p>
 */
public final class GradeSelfCheck
{
    private static final String[][] CHECKS = {
        { "FormTranslucentQueue.submit", "bbspp$submitGrade" },
        { "RenderLayer.draw", "bbspp$drawPreviewGrade" },
        { "ModelInstance.render", "bbspp$pushRenderContext" },
        { "ModelInstance.render", "bbspp$popRenderContext" },
        { "CubicCubeRenderer.emit", "bbspp$transformU" },
        { "CubicCubeRenderer.emit", "bbspp$transformV" },
        { "UIFormEditor.pick", "bbspp$directParentPick" },
    };

    private GradeSelfCheck()
    {
    }

    public static void verify()
    {
        List<String> missing = new ArrayList<>();
        int applied = 0;

        for (String[] check : CHECKS)
        {
            if (hasHandler(check[0], check[1]))
            {
                applied++;
            }
            else
            {
                missing.add(check[0] + " → " + check[1]);
            }
        }

        String pipelines = "base=" + safeReady(false) + ", glint=" + safeReady(true);

        if (missing.isEmpty())
        {
            BBSPlusPlusMod.LOGGER.info("[BBS++] 纹理分级注入自检：{}/{} 生效（管线 {}）",
                applied, CHECKS.length, pipelines);
        }
        else
        {
            BBSPlusPlusMod.LOGGER.warn("[BBS++] 纹理分级注入自检：{}/{} 生效，缺失 {} —— 对应功能不会生效；管线 {}",
                applied, CHECKS.length, missing, pipelines);
        }
    }

    private static boolean hasHandler(String target, String handler)
    {
        Class<?> type = switch (target)
        {
            case "FormTranslucentQueue.submit" -> FormTranslucentQueue.class;
            case "RenderLayer.draw" -> RenderLayer.class;
            case "ModelInstance.render" -> ModelInstance.class;
            case "CubicCubeRenderer.emit" -> CubicCubeRenderer.class;
            case "UIFormEditor.pick" -> UIFormEditor.class;
            default -> null;
        };

        if (type == null)
        {
            return false;
        }

        try
        {
            for (Method method : type.getDeclaredMethods())
            {
                /* Mixin 会把 handler 改名成 handler$<hash>$<prefix$><我们的名字>（实机日志里是
                 * handler$zka000$bbsplusplus$bbspp$submitGrade），所以只能 contains，不能 equals/startsWith。 */
                if (method.getName().contains(handler))
                {
                    return true;
                }
            }
        }
        catch (Throwable ignored)
        {
            /* 反射失败当作没生效，让日志把问题暴露出来 */
        }

        return false;
    }

    private static String safeReady(boolean glint)
    {
        try
        {
            return (glint ? GradePass.readyGlint() : GradePass.readyBase()) ? "ok" : "未就绪";
        }
        catch (Throwable e)
        {
            return "异常:" + e.getClass().getSimpleName();
        }
    }
}
