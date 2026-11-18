package gbeic.bbsplusplus.compat;

import mchorse.bbs_mod.utils.clips.Clip;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * bbs_together 剪辑锁的只读桥。
 * <p>
 * 锁本身由 Together 模组维护（数据存在剪辑自己的 ValueGroup 数据里），BBS++ 只在双击打开剪辑时
 * 问一句"这个剪辑对本地玩家是否可编辑"。Together 是可选依赖：没装时全部视为可编辑，
 * 装了但反射失败时同样放行，绝不因为另一个模组的内部变化而卡住本地功能。
 * </p>
 */
public final class TogetherClipLockCompat
{
    public static final String MOD_ID = "bbs_together";

    private static final String CLIP_LOCK_CLIENT = "mchorse.bbs_together.locks.ClipLockClient";

    private static Boolean loaded;

    private static Method canEdit;

    private static boolean resolved;

    private TogetherClipLockCompat()
    {}

    public static boolean isLoaded()
    {
        if (loaded == null)
        {
            loaded = FabricLoader.getInstance().isModLoaded(MOD_ID);
        }

        return loaded;
    }

    /** 没有 Together、没有锁、或者锁允许本地玩家时都返回 true。 */
    public static boolean canEdit(Clip clip)
    {
        Method method = canEdit();

        if (method == null || clip == null)
        {
            return true;
        }

        try
        {
            return Boolean.TRUE.equals(method.invoke(null, clip));
        }
        catch (Throwable e)
        {
            return true;
        }
    }

    private static Method canEdit()
    {
        if (resolved)
        {
            return canEdit;
        }

        resolved = true;

        if (!isLoaded())
        {
            return null;
        }

        try
        {
            canEdit = Class.forName(CLIP_LOCK_CLIENT).getMethod("canEdit", Clip.class);
        }
        catch (Throwable e)
        {
            canEdit = null;
        }

        return canEdit;
    }
}
