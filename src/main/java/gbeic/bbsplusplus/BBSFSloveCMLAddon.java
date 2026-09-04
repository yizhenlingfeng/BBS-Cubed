package gbeic.bbsplusplus;

import gbeic.bbsplusplus.clips.HotbarClip;
import gbeic.bbsplusplus.clips.ReplayClip;
import gbeic.bbsplusplus.clips.screen.CinematicClip;
import gbeic.bbsplusplus.forms.FluidForm;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.events.BBSAddonMod;
import mchorse.bbs_mod.events.Subscribe;
import mchorse.bbs_mod.events.register.RegisterSettingsEvent;
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
}
