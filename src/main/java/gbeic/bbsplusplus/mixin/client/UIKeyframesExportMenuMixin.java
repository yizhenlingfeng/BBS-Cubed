package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.film.replays.ExportUIKeys;
import gbeic.bbsplusplus.ui.film.replays.overlays.UIExportAnimationOverlayPanel;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 给 pose 轨道的右键菜单追加"导出为动画"入口:构造 TAIL 再调一次
 * {@code context(...)} —— {@code UIElement.contextOptions} 是叠加列表,
 * 原版菜单项不受影响。挂 {@link UIKeyframes} 级别(而非 UIReplaysEditor)
 * 使所有 keyframe 编辑器场景通用,所需上下文(选中关键帧、所属 form)
 * 全部能从 sheet 自身取到。
 *
 * <p>两种可导出目标,优先鼠标悬停的轨道:整体 pose 轨道(factory 为 POSE,
 * {@code Keyframe<Pose>});或展开后的肢体轨道(isBoneTrack + POSE_TRANSFORM,
 * id 形如 {@code pose.bones.<bone>})——肢体模式会收集同一 form 下所有
 * 有选中关键帧的骨骼轨道一起导出。form 须是已加载模型的 {@link ModelForm}。</p>
 */
@Mixin(value = UIKeyframes.class, remap = false)
public abstract class UIKeyframesExportMenuMixin
{
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$registerExportMenu(Consumer<Keyframe> callback, CallbackInfo ci)
    {
        UIKeyframes self = (UIKeyframes) (Object) this;

        self.context((menu) ->
        {
            IUIKeyframeGraph graph = self.getGraph();

            if (graph == null)
            {
                return;
            }

            UIKeyframeSheet hovered = graph == self.getDopeSheet()
                ? self.getDopeSheet().getSheet(self.getContext().mouseY)
                : null;

            /* 整体 pose 轨道:鼠标优先,其次任一有选中的 */
            UIKeyframeSheet poseSheet = this.bbspp_cml$isPoseSheet(hovered) ? hovered : null;

            if (poseSheet == null)
            {
                for (UIKeyframeSheet sheet : graph.getSheets())
                {
                    if (this.bbspp_cml$isPoseSheet(sheet))
                    {
                        poseSheet = sheet;

                        break;
                    }
                }
            }

            if (poseSheet != null)
            {
                Form form = this.bbspp_cml$resolveForm(poseSheet);

                if (form instanceof ModelForm modelForm && ModelFormRenderer.getModel(modelForm) != null)
                {
                    final UIKeyframeSheet sheet = poseSheet;

                    menu.action(Icons.UPLOAD, ExportUIKeys.CONTEXT_EXPORT, () ->
                    {
                        UIOverlay.addOverlay(self.getContext(), new UIExportAnimationOverlayPanel(modelForm, sheet,
                            this.bbspp_cml$getReplayProperties(self)), 240, 250);
                    });
                }

                return;
            }

            /* 肢体轨道:锚定鼠标下轨道(否则首条命中)的 form,收集同 form 的全部选中骨骼轨道 */
            UIKeyframeSheet anchor = this.bbspp_cml$isBoneSheet(hovered) ? hovered : null;

            if (anchor == null)
            {
                for (UIKeyframeSheet sheet : graph.getSheets())
                {
                    if (this.bbspp_cml$isBoneSheet(sheet))
                    {
                        anchor = sheet;

                        break;
                    }
                }
            }

            if (anchor == null)
            {
                return;
            }

            TrackId anchorPath = TrackId.parse(anchor.id, TrackKind.BONE);
            Form form = this.bbspp_cml$resolveForm(anchor);

            if (anchorPath == null || !(form instanceof ModelForm modelForm) || ModelFormRenderer.getModel(modelForm) == null)
            {
                return;
            }

            List<UIKeyframeSheet> boneSheets = new ArrayList<>();

            for (UIKeyframeSheet sheet : graph.getSheets())
            {
                if (!this.bbspp_cml$isBoneSheet(sheet))
                {
                    continue;
                }

                TrackId path = TrackId.parse(sheet.id, TrackKind.BONE);

                if (path != null && Objects.equals(path.formPath(), anchorPath.formPath()))
                {
                    boneSheets.add(sheet);
                }
            }

            if (!boneSheets.isEmpty())
            {
                menu.action(Icons.UPLOAD, ExportUIKeys.CONTEXT_EXPORT, () ->
                {
                    UIOverlay.addOverlay(self.getContext(), new UIExportAnimationOverlayPanel(modelForm, boneSheets,
                        this.bbspp_cml$getReplayProperties(self)), 240, 250);
                });
            }
        });
    }

    @Unique
    private Form bbspp_cml$resolveForm(UIKeyframeSheet sheet)
    {
        Form form = sheet.property != null ? FormUtils.getForm(sheet.property) : null;

        return form != null ? form : sheet.form;
    }

    @Unique
    private boolean bbspp_cml$isPoseSheet(UIKeyframeSheet sheet)
    {
        return sheet != null
            && !sheet.isBoneTrack
            && sheet.channel.getFactory() == KeyframeFactories.POSE
            && sheet.selection.hasAny();
    }

    @Unique
    private boolean bbspp_cml$isBoneSheet(UIKeyframeSheet sheet)
    {
        return sheet != null
            && sheet.isBoneTrack
            && sheet.channel.getFactory() == KeyframeFactories.POSE_TRANSFORM
            && TrackId.kindOf(sheet.id) == TrackKind.BONE
            && sheet.selection.hasAny();
    }

    @Unique
    private FormProperties bbspp_cml$getReplayProperties(UIKeyframes self)
    {
        UIReplaysEditor editor = self.getParent(UIReplaysEditor.class);
        Replay replay = editor == null ? null : editor.getReplay();

        return replay == null ? null : replay.properties;
    }
}
