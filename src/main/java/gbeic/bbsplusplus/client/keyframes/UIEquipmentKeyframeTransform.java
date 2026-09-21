package gbeic.bbsplusplus.client.keyframes;

import gbeic.bbsplusplus.keyframes.EquipmentKeyframeTransforms;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframePropTransform;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.item.ItemStack;

import java.util.function.Consumer;

/**
 * 六个装备槽位 {@code ItemStack} 关键帧的内嵌变换编辑器。
 *
 * <p>解决的问题：轨道仍然只显示“主手/副手/头盔...”等六条，但右侧属性栏需要像
 * 原生 Transform 关键帧一样编辑坐标、缩放和旋转。实现思路是复用原生关键帧变换
 * 控件，把写入目标改为关键帧上的 BBS++ 附加 {@link Transform}。</p>
 *
 * <p>BBS 2.6 起，录制期不再使用 {@code applyDuringRecording/getRecordedTransform} 两个钩子，
 * 改为 {@link #getKeyframes()} 提供时间线、{@link #getAutoKeyTransform(int)} 返回播放头处
 * （缺则新建）的变换；写入仍统一走 {@link #applyToSelection(Consumer)}，自动关键帧时把增量
 * 写到每个被选中装备通道在播放头处的关键帧上。</p>
 */
public class UIEquipmentKeyframeTransform extends UIKeyframePropTransform
{
    private final UIKeyframes editor;
    private final Keyframe<?> keyframe;

    public UIEquipmentKeyframeTransform(UIKeyframes editor, Keyframe<ItemStack> keyframe)
    {
        this.editor = editor;
        this.keyframe = keyframe;

        this.enableHotkeys();
        this.setTransform(EquipmentKeyframeTransforms.getOrCreate(keyframe));
    }

    @Override
    protected UIKeyframes getKeyframes()
    {
        return this.editor;
    }

    @Override
    protected void applyToSelection(Consumer<Transform> consumer)
    {
        Float autoTick = this.editor == null ? null : this.editor.getAutoKeyframeTick();

        if (autoTick != null)
        {
            /* 自动关键帧/录制路径：在每个被选中的装备通道播放头处补帧并写入增量。 */
            for (UIKeyframeSheet sheet : this.editor.getGraph().getSheets())
            {
                if (!EquipmentKeyframeTransforms.isEquipmentChannel(sheet.channel.getId())
                    || sheet.selection.getSelected().isEmpty())
                {
                    continue;
                }

                Keyframe<?> recorded = this.ensureEquipmentKeyframe(sheet, autoTick);

                if (recorded != null)
                {
                    Transform transform = EquipmentKeyframeTransforms.getOrCreate(recorded);

                    recorded.preNotify();
                    consumer.accept(transform);
                    recorded.postNotify();
                }
            }

            return;
        }

        this.forEachSelected((selected) ->
        {
            Transform transform = EquipmentKeyframeTransforms.getOrCreate(selected);

            selected.preNotify();
            consumer.accept(transform);
            selected.postNotify();
        });
    }

    @Override
    protected Transform getAutoKeyTransform(float tick)
    {
        UIKeyframeSheet sheet = this.editor.getGraph().getSheet(this.keyframe);
        Keyframe<?> recorded = this.ensureEquipmentKeyframe(sheet, tick);

        return recorded == null ? null : EquipmentKeyframeTransforms.getOrCreate(recorded);
    }

    private void forEachSelected(Consumer<Keyframe<?>> consumer)
    {
        if (this.editor == null || this.keyframe == null)
        {
            return;
        }

        for (UIKeyframeSheet sheet : this.editor.getGraph().getSheets())
        {
            if (!EquipmentKeyframeTransforms.isEquipmentChannel(sheet.channel.getId()))
            {
                continue;
            }

            for (Object selected : sheet.selection.getSelected())
            {
                if (selected instanceof Keyframe<?> keyframe)
                {
                    consumer.accept(keyframe);
                }
            }
        }
    }

    private Keyframe<?> ensureEquipmentKeyframe(UIKeyframeSheet sheet, float tick)
    {
        if (sheet == null || !EquipmentKeyframeTransforms.isEquipmentChannel(sheet.channel.getId()))
        {
            return null;
        }

        for (Object candidate : sheet.channel.getKeyframes())
        {
            if (candidate instanceof Keyframe<?> keyframe && keyframe.getTick() == tick)
            {
                return keyframe;
            }
        }

        Transform interpolated = EquipmentKeyframeTransforms.interpolate(sheet.channel, tick).copy();
        Keyframe<?> keyframe = sheet.ensureKeyframe(tick);

        if (keyframe != null)
        {
            EquipmentKeyframeTransforms.getOrCreate(keyframe).copy(interpolated);
        }

        return keyframe;
    }
}
