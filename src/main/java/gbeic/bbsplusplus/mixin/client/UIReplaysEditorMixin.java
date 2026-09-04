package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.film.replays.overlays.UIAnimationToPoseOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 原版「动画转姿势关键帧」只挂在根回放的 {@code pose} 轨道上
 * ({@code sheet.id.equals("pose")})。身体附件(body part)的 pose 轨道 id 形如
 * {@code parts/0/pose} 或 {@code &lt;path&gt;/pose}，因此菜单不出现。
 *
 * <p>在 {@link UIReplaysEditor#updateChannelsList()} 重建 keyframe 编辑器后叠加
 * context 菜单：对任意非 overlay 的 pose 轨道(根轨原版已有，这里跳过以免重复)
 * 解析 sheet 所属 {@link ModelForm}，调用同一套
 * {@link UIReplaysEditorUtils#animationToPoseKeyframes}。</p>
 *
 * <p>注意:不要在 layoutBottomToggles 上挂转小窗菜单——该方法每次 resize 都会调用,
 * context() 是追加而非替换,会刷出超长菜单;动作时间轴也不是独立 dock 面板。</p>
 */
@Mixin(value = UIReplaysEditor.class, remap = false)
public abstract class UIReplaysEditorMixin
{
    @Shadow
    public UIKeyframeEditor keyframeEditor;

    @Shadow
    private UIFilmPanel filmPanel;

    @Shadow
    private Replay replay;

    @Inject(method = "updateChannelsList", at = @At("TAIL"), remap = false)
    private void bbspp_cml$addBodyPartAnimationToPose(CallbackInfo ci)
    {
        if (this.keyframeEditor == null || this.filmPanel == null || this.replay == null)
        {
            return;
        }

        UIKeyframeEditor editor = this.keyframeEditor;
        UIFilmPanel panel = this.filmPanel;

        editor.view.context((menu) ->
        {
            if (this.replay == null || this.replay.form.get() == null)
            {
                return;
            }

            int mouseY = editor.getContext().mouseY;
            UIKeyframeSheet sheet = editor.view.getGraph().getSheet(mouseY);

            if (sheet == null
                || sheet.channel.getFactory() != KeyframeFactories.POSE
                || sheet.id == null
                || sheet.id.equals("pose")
                || !sheet.id.endsWith(FormUtils.PATH_SEPARATOR + "pose")
                || sheet.id.contains("pose_overlay"))
            {
                return;
            }

            Form sheetForm = sheet.property != null ? FormUtils.getForm(sheet.property) : null;

            if (!(sheetForm instanceof ModelForm modelForm))
            {
                return;
            }

            ModelInstance model = ModelFormRenderer.getModel(modelForm);

            if (model == null)
            {
                return;
            }

            menu.action(Icons.POSE, UIKeys.FILM_REPLAY_CONTEXT_ANIMATION_TO_KEYFRAMES, () ->
            {
                UIOverlay.addOverlay(
                    editor.getContext(),
                    new UIAnimationToPoseOverlayPanel(
                        (animationKey, onlyKeyframes, length, step) ->
                        {
                            int current = panel.getCursor();
                            IEntity entity = panel.getController().getCurrentEntity();

                            UIReplaysEditorUtils.animationToPoseKeyframes(
                                editor, sheet, modelForm, entity, current,
                                animationKey, onlyKeyframes, length, step);
                        },
                        modelForm, sheet),
                    200, 197
                );
            });
        });
    }
}
