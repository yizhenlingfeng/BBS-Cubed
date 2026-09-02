package gbeic.bbsplusplus.client.ui.presets;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.presets.DataManager;
import net.minecraft.client.MinecraftClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 预设自动保存状态管理。
 * <p>
 * 为模型方块的 IK 链、物理骨骼、骨骼限制和姿势页面提供"修改即保存"能力。
 * 启用后，面板每次提交修改都会经防抖延迟后自动写入当前选中的预设。
 * </p>
 * <p>
 * 状态仅在运行时内存中维护，不持久化到配置文件。
 * 每次打开编辑器默认关闭，避免误覆盖预设。
 * </p>
 */
public class AutoSavePresetState
{
    /** 防抖延迟（毫秒）：连续修改时仅在停止修改后保存一次 */
    private static final long DELAY_MS = 500;

    private static final Map<String, Boolean> enabled = new HashMap<>();
    private static final Map<String, String> selectedPresets = new HashMap<>();
    private static final Map<String, ScheduledFuture<?>> pendingTasks = new HashMap<>();
    private static ScheduledExecutorService executor;

    private AutoSavePresetState() {}

    private static ScheduledExecutorService getExecutor()
    {
        if (executor == null || executor.isShutdown())
        {
            executor = Executors.newSingleThreadScheduledExecutor(r ->
            {
                Thread t = new Thread(r, "bbspp-autosave");
                t.setDaemon(true);
                return t;
            });
        }
        return executor;
    }

    public static boolean isEnabled(String type)
    {
        return enabled.getOrDefault(type, false);
    }

    public static void setEnabled(String type, boolean value)
    {
        enabled.put(type, value);
        if (!value)
        {
            cancelPending(type);
        }
    }

    public static String getSelectedPreset(String type)
    {
        return selectedPresets.get(type);
    }

    public static void setSelectedPreset(String type, String name)
    {
        selectedPresets.put(type, name);
    }

    /**
     * 调度一次自动保存。连续调用会取消上一次未执行的任务，实现防抖。
     * 实际保存操作在 Minecraft 主线程执行，避免 DataManager 的线程安全问题。
     */
    public static void scheduleSave(String type, DataManager manager, String group,
                                    String presetName, MapType data)
    {
        if (type == null || manager == null || group == null || group.isEmpty()
                || presetName == null || presetName.isEmpty() || data == null)
        {
            return;
        }
        cancelPending(type);
        ScheduledFuture<?> future = getExecutor().schedule(() ->
        {
            try
            {
                MinecraftClient.getInstance().execute(() ->
                {
                    try
                    {
                        manager.saveData(group, presetName, data);
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                });
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }, DELAY_MS, TimeUnit.MILLISECONDS);
        pendingTasks.put(type, future);
    }

    private static void cancelPending(String type)
    {
        ScheduledFuture<?> future = pendingTasks.remove(type);
        if (future != null)
        {
            future.cancel(false);
        }
    }

    /**
     * 清理指定类型的所有状态。
     */
    public static void clear(String type)
    {
        cancelPending(type);
        enabled.remove(type);
        selectedPresets.remove(type);
    }

    /**
     * 通过 manager 的完整类名判断面板类型标识。
     * 使用 getName() 而非 getSimpleName()，避免内部类/代理导致的名称差异。
     */
    public static String typeFromManager(DataManager manager)
    {
        if (manager == null)
        {
            return null;
        }
        String name = manager.getClass().getName();
        if (name.contains("ModelIK")) return "ik";
        if (name.contains("ModelPhysics")) return "physics";
        if (name.contains("ModelConstraints")) return "constraints";
        if (name.contains("PoseManager") || name.contains("Pose")) return "pose";
        return null;
    }
}
