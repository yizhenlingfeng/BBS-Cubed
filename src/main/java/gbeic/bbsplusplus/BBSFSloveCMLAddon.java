package gbeic.bbsplusplus;

import gbeic.bbsplusplus.api.BBSPPFormValues;
import gbeic.bbsplusplus.clips.HotbarClip;
import gbeic.bbsplusplus.clips.ReplayClip;
import gbeic.bbsplusplus.clips.screen.CinematicClip;
import gbeic.bbsplusplus.forms.FluidForm;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.events.RegisterSettingsEvent;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.MapFactory;

/**
 * BBS FSloveCML Addon - 添加 CML 功能到 BBS FS
 *
 * <p>BBS 在 <code>bbs-addon</code> 入口加载本类，并在自身初始化完成后派发
 * {@link RegisterSettingsEvent}——这是唯一能安全调用 BBSMod 静态工厂的时机
 * （Fabric 入口顺序下 BBSFSloveCML.onInitialize 先于 BBSMod.onInitialize 执行，
 * 直接注册会让 getForms()/getFactoryCameraClips() 返回 null）。</p>
 */
public class BBSFSloveCMLAddon implements BBSAddonMod {

    @Subscribe
    public void onRegisterSettings(RegisterSettingsEvent event) {
        registerCameraClips();
        registerFluidForm();
    }

    private void registerCameraClips() {
        try {
            MapFactory<Clip, ClipFactoryData> factory = BBSMod.getFactoryCameraClips();
            if (factory == null) {
                BBSFSloveCML.LOGGER.warn("[FSloveCML] 剪辑工厂为空，无法注册剪辑");
                return;
            }

            /* 快捷栏剪辑 */
            factory.register(Link.bbs("hotbar"), HotbarClip.class, new ClipFactoryData(Icons.BLOCK, 0x55aaff));
            BBSFSloveCML.LOGGER.info("[FSloveCML] 快捷栏剪辑 (HotbarClip) 注册成功!");

            /* 电影效果剪辑 */
            factory.register(Link.bbs("cinematic"), CinematicClip.class, new ClipFactoryData(Icons.VIDEO_CAMERA, 0xffaa00));
            BBSFSloveCML.LOGGER.info("[FSloveCML] 电影效果剪辑 (CinematicClip) 注册成功!");

            /* 重播是按所在图层读取下方摄像机剪辑的时间修饰剪辑 */
            factory.register(Link.bbs("replay"), ReplayClip.class, new ClipFactoryData(Icons.REFRESH, 0x4f8cff));
            BBSFSloveCML.LOGGER.info("[FSloveCML] 重播剪辑 (ReplayClip) 注册成功!");
        } catch (Exception e) {
            BBSFSloveCML.LOGGER.error("[FSloveCML] 注册剪辑失败: {}", e.getMessage(), e);
        }
    }

    private void registerFluidForm() {
        try {
            BBSMod.getForms().register(Link.bbs("fluid"), FluidForm.class, null);
            BBSFSloveCML.LOGGER.info("[FSloveCML] 流体伪装 (FluidForm) 注册成功!");
        } catch (Exception e) {
            BBSFSloveCML.LOGGER.error("[FSloveCML] 注册流体伪装失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 注册 AAA 粒子与物品喷射表单（原 {@code BBSModMixin} 的官方事件版）。
     *
     * <p>{@code RegisterFormsEvent} 在 {@code BBSMod.onInitialize} 中
     * {@code forms = new FormArchitect()} 并注册完原生表单之后派发，
     * 因此 {@code event.forms} 必然可用——旧注释"此时尚未初始化"针对的是
     * Fabric main 入口的时序，对 bbs-addon 事件不成立。</p>
     */
    @Subscribe
    public void onRegisterForms(mchorse.bbs_mod.api.events.RegisterFormsEvent event) {
        try {
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("aaa_particles")) {
                event.forms.register(Link.bbs("aaa_particle"), gbeic.bbsplusplus.forms.AAAParticleForm.class, null);
                BBSFSloveCML.LOGGER.info("[FSloveCML] 已注册 AAAParticleForm 到 FormArchitect");
            }

            event.forms.register(Link.bbs("item_spray"), gbeic.bbsplusplus.forms.ItemSprayForm.class, null);
            BBSFSloveCML.LOGGER.info("[FSloveCML] 已注册 ItemSprayForm 到 FormArchitect");
        } catch (Exception e) {
            BBSFSloveCML.LOGGER.error("[FSloveCML] 注册 AAA 粒子 / 物品喷射表单失败", e);
        }
    }

    /**
     * 注册 aaa_effect 关键帧工厂（原 {@code BBSPlusPlusKeyframeFactories.register()}
     * 直接往静态 map put 的官方事件版——事件在 {@code KeyframeFactories.setup()}
     * 之后派发，消除"未注册就被读取"的时序隐患）。
     */
    @Subscribe
    public void onRegisterKeyframeFactories(mchorse.bbs_mod.api.events.RegisterKeyframeFactoriesEvent event) {
        event.register("aaa_effect", gbeic.bbsplusplus.keyframes.BBSPlusPlusKeyframeFactories.AAA_EFFECT);
        BBSFSloveCML.LOGGER.info("[FSloveCML] 已注册 aaa_effect 关键帧工厂");
    }

    /**
     * 给 BBS 原生表单统一追加 BBS++ 扩展属性（原 6 个 Form 构造器 Mixin 的官方事件版）：
     * <ul>
     *   <li>{@code BillboardFormMixin}    → billboard 的 bbspp_uv_scale（插在旋转前）</li>
     *   <li>{@code ExtrudedFormMixin}     → extruded 的 bbspp_uv_transform / bbspp_uv_rotation（插在颜色前）</li>
     *   <li>{@code ModelFormMixin}        → model 的 bbspp_uv_transform</li>
     *   <li>{@code ModelFormCMLMixin}     → model 的 actions_overlay 系列 / molangShared / texture_tint / texture_whiten</li>
     *   <li>{@code MiscFormTextureGradeMixin} → 7 个杂项表单的 texture_tint / texture_whiten</li>
     *   <li>{@code TrailFormColorMixin}   → trail / vanilla_particles 的 color</li>
     * </ul>
     * modifier 在表单构造完成后执行，remove/add 重排与原 Mixin 的插入顺序等价。
     * 读取端见 {@code BBSPPFormValues}（按属性 ID 取值，duck 接口已移除）。
     * 注意：BBS 调色板里的模板表单不经 modifier（官方设计），读取端已对 null 做了默认分支。
     */
    @Subscribe
    public void onRegisterFormModifiers(mchorse.bbs_mod.api.events.RegisterFormModifiersEvent event) {
        event.register(BBSFSloveCMLAddon::bbspp$modifyForm);
    }

    private static void bbspp$modifyForm(mchorse.bbs_mod.forms.forms.Form form) {
        /* 广告牌：U/V 缩放插在原生旋转之前 */
        if (form instanceof mchorse.bbs_mod.forms.forms.BillboardForm billboard) {
            billboard.remove(billboard.rotation);
            billboard.remove(billboard.shading);
            billboard.add(new mchorse.bbs_mod.settings.values.misc.ValueVector4f(BBSPPFormValues.UV_SCALE, new org.joml.Vector4f(1F)));
            billboard.add(billboard.rotation);
            billboard.add(billboard.shading);
        }

        /* 挤出：UV 变换 + 旋转插在颜色/广告牌/着色之前 */
        if (form instanceof mchorse.bbs_mod.forms.forms.ExtrudedForm extruded) {
            extruded.remove(extruded.color);
            extruded.remove(extruded.billboard);
            extruded.remove(extruded.shading);
            extruded.add(new mchorse.bbs_mod.settings.values.misc.ValueVector4f(BBSPPFormValues.UV_TRANSFORM, new org.joml.Vector4f(0F, 0F, 1F, 1F)));
            extruded.add(new mchorse.bbs_mod.settings.values.numeric.ValueFloat(BBSPPFormValues.UV_ROTATION, 0F));
            extruded.add(extruded.color);
            extruded.add(extruded.billboard);
            extruded.add(extruded.shading);
        }

        /* 模型：UV 变换 + CML 系列 */
        if (form instanceof mchorse.bbs_mod.forms.forms.ModelForm modelForm) {
            modelForm.add(new mchorse.bbs_mod.settings.values.misc.ValueVector4f(BBSPPFormValues.UV_TRANSFORM, new org.joml.Vector4f(0F, 0F, 1F, 1F)));

            if (gbeic.bbsplusplus.settings.CMLSettings.isSnowActionsEnabled()) {
                modelForm.add(new mchorse.bbs_mod.forms.values.ValueActionsConfig(
                    BBSPPFormValues.ACTIONS_OVERLAY, bbspp$createEmptyActionsOverlay()));

                /* 2.7 起 pose_transform_overlays 拆为 pose_overlays 与 transform_overlays 两个计数；
                 * CML 的 actions overlay 作为并行覆盖集，数量与二者之和保持一致。 */
                int overlayCount = (mchorse.bbs_mod.BBSSettings.recordingPoseOverlays == null ? 0 : mchorse.bbs_mod.BBSSettings.recordingPoseOverlays.get())
                    + (mchorse.bbs_mod.BBSSettings.recordingTransformOverlays == null ? 0 : mchorse.bbs_mod.BBSSettings.recordingTransformOverlays.get());

                for (int i = 0; i < overlayCount; i++) {
                    modelForm.add(new mchorse.bbs_mod.forms.values.ValueActionsConfig(
                        BBSPPFormValues.ACTIONS_OVERLAY + i, bbspp$createEmptyActionsOverlay()));
                }
            }

            modelForm.add(new mchorse.bbs_mod.settings.values.numeric.ValueBoolean(BBSPPFormValues.MOLANG_SHARED, false));
            modelForm.add(new mchorse.bbs_mod.settings.values.core.ValueColor(BBSPPFormValues.TEXTURE_TINT, new mchorse.bbs_mod.utils.colors.Color(1F, 1F, 1F, 0F)));
            modelForm.add(new mchorse.bbs_mod.settings.values.numeric.ValueFloat(BBSPPFormValues.TEXTURE_WHITEN, 0F, 0F, 1F));
        }

        /* 杂项可渲染表单：纹理调色（ModelForm 的在上方已加；FluidForm 自带同名值） */
        if (form instanceof mchorse.bbs_mod.forms.forms.BillboardForm
            || form instanceof mchorse.bbs_mod.forms.forms.BlockForm
            || form instanceof mchorse.bbs_mod.forms.forms.ExtrudedForm
            || form instanceof mchorse.bbs_mod.forms.forms.ItemForm
            || form instanceof mchorse.bbs_mod.forms.forms.LabelForm
            || form instanceof mchorse.bbs_mod.forms.forms.TrailForm
            || form instanceof mchorse.bbs_mod.forms.forms.VanillaParticleForm) {
            form.add(new mchorse.bbs_mod.settings.values.core.ValueColor(BBSPPFormValues.TEXTURE_TINT, new mchorse.bbs_mod.utils.colors.Color(1F, 1F, 1F, 0F)));
            form.add(new mchorse.bbs_mod.settings.values.numeric.ValueFloat(BBSPPFormValues.TEXTURE_WHITEN, 0F, 0F, 1F));
        }

        /* 拖尾与原版粒子：补 color 关键帧 */
        if (form instanceof mchorse.bbs_mod.forms.forms.TrailForm
            || form instanceof mchorse.bbs_mod.forms.forms.VanillaParticleForm) {
            form.add(new mchorse.bbs_mod.settings.values.core.ValueColor(BBSPPFormValues.COLOR, mchorse.bbs_mod.utils.colors.Color.white()));
        }
    }

    private static final String[] bbspp_ACTION_SLOTS = {
        "idle", "running", "sprinting", "crouching", "crouching_idle", "dying", "falling",
        "swimming", "swimming_idle", "riding", "riding_idle", "flying", "flying_idle",
        "swipe", "jump", "jump_alt", "hurt", "land", "shoot", "consume", "base_pre", "base_post"
    };

    private static mchorse.bbs_mod.cubic.animation.ActionsConfig bbspp$createEmptyActionsOverlay() {
        mchorse.bbs_mod.cubic.animation.ActionsConfig configs = new mchorse.bbs_mod.cubic.animation.ActionsConfig();

        for (String key : bbspp_ACTION_SLOTS) {
            configs.actions.put(key, new mchorse.bbs_mod.cubic.animation.ActionConfig(""));
        }

        return configs;
    }
}
