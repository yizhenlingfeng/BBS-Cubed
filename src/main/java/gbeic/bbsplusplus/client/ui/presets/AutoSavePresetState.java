package gbeic.bbsplusplus.client.ui.presets;

import gbeic.bbsplusplus.mixin.DataManagerAccessor;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.presets.DataManager;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 预设自动保存状态管理。
 * <p>
 * 为模型方块的 IK 链、物理骨骼、骨骼限制和姿势页面提供"修改即保存"能力。
 * 启用后，面板每次提交修改都会经防抖延迟后自动写入当前选中的预设。
 * </p>
 * <p>
 * 性能设计：
 * <ul>
 *   <li>面板修改时只重置防抖定时器，<b>不</b>立即构建数据快照；
 *       快照（toPresetData/toData）延迟到防抖触发后才在主线程构建一次，
 *       避免拖动滑块时每帧全量序列化造成的 GC 压力。</li>
 *   <li>内存缓存更新（DataManager 的 MapType）在主线程完成，保持 BBS 原有线程模型；
 *       整组预设的序列化与文件写入（含 fsync）在后台守护线程执行，不阻塞渲染线程。</li>
 *   <li>写盘前做内容深比较，快照与上次已保存内容一致时直接跳过，避免无效 IO。</li>
 * </ul>
 * </p>
 * <p>
 * 状态仅在运行时内存中维护，不持久化到配置文件。
 * 开关状态（enabled）独立于 clear()：切换编辑目标/面板刷新时只重置目标预设与脏检查基准，
 * 不会关闭自动保存，避免 startEdit/setPose 等内部刷新导致按钮意外熄灭。
 * 开关只能由用户手动点击按钮切换。
 * </p>
 */
public class AutoSavePresetState
{
    /** 防抖延迟（毫秒）：连续修改时仅在停止修改后保存一次 */
    private static final long DELAY_MS = 500;

    private static final Map<String, Boolean> enabled = new ConcurrentHashMap<>();
    private static final Map<String, String> selectedPresets = new ConcurrentHashMap<>();
    private static final Map<String, ScheduledFuture<?>> pendingTasks = new ConcurrentHashMap<>();
    /** 各类型上次已写入磁盘的预设快照，用于脏检查（仅主线程读写） */
    private static final Map<String, MapType> lastSaved = new ConcurrentHashMap<>();
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
            lastSaved.remove(type);
        }
    }

    public static String getSelectedPreset(String type)
    {
        return selectedPresets.get(type);
    }

    public static void setSelectedPreset(String type, String name)
    {
        selectedPresets.put(type, name);
        /* 目标预设变化后必须清除脏检查基准，否则新目标的首次保存可能被误判为"内容未变"而跳过 */
        lastSaved.remove(type);
    }

    /**
     * 调度一次自动保存。连续调用会取消上一次未执行的任务，实现防抖。
     * <p>
     * dataSupplier 仅在防抖触发后（主线程）被调用一次，拖动期间不产生快照分配。
     * </p>
     */
    public static void scheduleSave(String type, DataManager manager, String group,
                                    String presetName, Supplier<MapType> dataSupplier)
    {
        if (type == null || manager == null || group == null || group.isEmpty()
                || presetName == null || presetName.isEmpty() || dataSupplier == null)
        {
            return;
        }
        cancelPending(type);
        ScheduledFuture<?> future = getExecutor().schedule(() ->
        {
            try
            {
                MinecraftClient.getInstance().execute(() -> saveOnMainThread(
                        type, manager, group, presetName, dataSupplier));
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }, DELAY_MS, TimeUnit.MILLISECONDS);
        pendingTasks.put(type, future);
    }

    /**
     * 防抖触发后在主线程执行：构建快照 → 脏检查 → 更新内存缓存 → 浅拷贝整组数据交后台写盘。
     */
    private static void saveOnMainThread(String type, DataManager manager, String group,
                                         String presetName, Supplier<MapType> dataSupplier)
    {
        /* 任务可能在开关关闭或目标失效后才触发，这里重新校验 */
        if (!isEnabled(type))
        {
            return;
        }
        if (!presetName.equals(getSelectedPreset(type)))
        {
            return;
        }

        final MapType data;
        try
        {
            data = dataSupplier.get();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return;
        }
        if (data == null)
        {
            return;
        }

        /* 脏检查：内容与上次已保存快照一致则跳过（BaseType.equals 为深比较） */
        if (BaseType.equals(lastSaved.get(type), data))
        {
            return;
        }

        /* 内存缓存更新与 BBS 原生 DataManager.saveData 完全一致，且仍在主线程，无线程安全问题 */
        MapType root = ((DataManagerAccessor) manager).bbspp$getData();
        if (root == null)
        {
            return;
        }
        MapType groupMap = root.getMap(group);
        groupMap.put(presetName, data);
        lastSaved.put(type, data);

        final File file;
        try
        {
            file = BBSMod.getProvider().getFile(((DataManagerAccessor) manager).bbspp$getFile(group));
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return;
        }
        if (file == null)
        {
            return;
        }

        /* 浅拷贝整组预设容器（只复制 entry 引用）：预设放入缓存后以整体替换方式更新、
           不会原地修改，后台线程遍历这份快照与主线程并发安全 */
        MapType snapshot = new MapType();
        for (Map.Entry<String, BaseType> entry : groupMap.elements.entrySet())
        {
            snapshot.put(entry.getKey(), entry.getValue());
        }

        /* 序列化 + 写临时文件 + fsync + 原子改名全部在后台线程执行，复用 BBS 原生写盘逻辑 */
        getExecutor().execute(() ->
        {
            try
            {
                File parent = file.getParentFile();
                if (parent != null)
                {
                    parent.mkdirs();
                }
                DataToString.writeSilently(file, snapshot, true);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });
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
     * 清理指定类型的运行时状态（面板切换编辑目标/刷新时调用）。
     * <p>
     * 仅取消挂起的保存任务、清除目标预设与脏检查基准；<b>不</b>关闭自动保存开关。
     * 开关状态只能由用户手动点击按钮切换，避免 startEdit/setPose 等内部刷新
     * （如点击肢体、数据回载）导致按钮意外熄灭。
     * </p>
     */
    public static void clear(String type)
    {
        cancelPending(type);
        selectedPresets.remove(type);
        lastSaved.remove(type);
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
