package gbeic.bbsplusplus;

import java.util.HashMap;
import java.util.Map;

/**
 * POV 插件与第三方扩展轨道名、提示文本的中文映射表。
 * 供 {@link gbeic.bbsplusplus.mixin.StringKeyMixin} 使用，处理硬编码的
 * {@code IKey.constant("英文")} 文本，不依赖 BBS L10n 系统。
 */
public final class PovZHTranslator
{
    private static final Map<String, String> STRING_MAP = new HashMap<>();

    static
    {
        // —— HUD 轨道分组 ——
        STRING_MAP.put("Layout", "布局");
        STRING_MAP.put("Slots", "物品槽位");
        STRING_MAP.put("Hearts", "生命值");
        STRING_MAP.put("Food", "饥饿值");
        STRING_MAP.put("XP Bar", "经验条");

        // —— HUD 轨道 ——
        STRING_MAP.put("Visible", "可见");
        STRING_MAP.put("Status Bars", "状态栏");
        STRING_MAP.put("Crosshair", "准星");
        STRING_MAP.put("Cursor Layout", "光标布局");
        STRING_MAP.put("Cursor Visible", "光标可见");
        STRING_MAP.put("Cursor Item", "光标物品");
        STRING_MAP.put("Selected Slot", "选中的格子");
        STRING_MAP.put("Inventory Slots", "物品栏槽位");
        STRING_MAP.put("Offhand", "副手");
        STRING_MAP.put("Health", "生命值");
        STRING_MAP.put("Previous Health", "上次生命值");
        STRING_MAP.put("Health Flash", "生命值闪烁");
        STRING_MAP.put("Health Container", "生命值容器");
        STRING_MAP.put("Absorption", "伤害吸收");
        STRING_MAP.put("Absorption Container", "伤害吸收容器");
        STRING_MAP.put("Heart Type", "心形类型");
        STRING_MAP.put("Hardcore", "极限模式");
        STRING_MAP.put("Heart Regeneration", "生命恢复");
        STRING_MAP.put("Armor", "护甲");
        STRING_MAP.put("Hunger", "饥饿值");
        STRING_MAP.put("Hunger Effect", "饥饿效果");
        STRING_MAP.put("Mount Health", "坐骑生命值");
        STRING_MAP.put("Mount Health Container", "坐骑生命值容器");
        STRING_MAP.put("Air", "氧气");
        STRING_MAP.put("Experience", "经验");
        STRING_MAP.put("Experience Level", "经验等级");
        STRING_MAP.put("Golden Heart Flash", "金心闪烁");

        // —— 手部轨道 ——
        STRING_MAP.put("Active Use Hand", "使用中的手");
        STRING_MAP.put("Active Use Item", "使用中的物品");
        STRING_MAP.put("Bob Phase", "摆动相位");
        STRING_MAP.put("Bob Strength", "摆动幅度");
        STRING_MAP.put("Camera Offset", "摄像机偏移");
        STRING_MAP.put("Color Overlay", "颜色叠加");
        STRING_MAP.put("Item Pose", "物品姿势");
        STRING_MAP.put("Left Hand Visible", "左手可见");
        STRING_MAP.put("Left Swing", "左手挥动");
        STRING_MAP.put("Left-handed Main Arm", "左手为主手");
        STRING_MAP.put("Main Equip", "主手装备");
        STRING_MAP.put("Model", "模型");
        STRING_MAP.put("Off Hand Item", "副手物品");
        STRING_MAP.put("Offhand Equip", "副手装备");
        STRING_MAP.put("Pose", "姿势");
        STRING_MAP.put("Render Pitch", "渲染俯仰");
        STRING_MAP.put("Render Yaw", "渲染偏航");
        STRING_MAP.put("Replay Interaction", "回放交互");
        STRING_MAP.put("Right Hand Visible", "右手可见");
        STRING_MAP.put("Right Swing", "右手挥动");
        STRING_MAP.put("Show Particles", "显示使用粒子");
        STRING_MAP.put("Use Time", "使用时长");
        STRING_MAP.put("World Interaction", "世界交互");

        // —— 第三方插件轨道 ——
        STRING_MAP.put("illusion", "幻影");
        STRING_MAP.put("illusion_transform", "幻影变换");

        // —— 编辑器工具提示 ——
        STRING_MAP.put("Hotbar", "快捷栏");
        STRING_MAP.put("Hand", "手部");
        STRING_MAP.put("BodyPart", "身体附件");
        STRING_MAP.put("POV Actions", "POV 动作");
    }

    private PovZHTranslator()
    {
    }

    /**
     * 查询 POV 文本的中文译文。
     *
     * @param text 英文原文
     * @return 中文译文，未命中时返回 null
     */
    public static String getChinese(String text)
    {
        return text == null ? null : STRING_MAP.get(text);
    }
}
