package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.BoneOutlineHolder;
import gbeic.bbsplusplus.api.BonePbrHolder;
import gbeic.bbsplusplus.api.BoneTextureHolder;
import gbeic.bbsplusplus.api.GlintHolder;
import gbeic.bbsplusplus.api.GlintSettingsHolder;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.resources.LinkUtils;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

/**
 * 为 {@link PoseTransform} 挂载逐骨骼的纹理、附魔光效、PBR 与描边参数，并把它们接入
 * 序列化（{@code toData/fromData}）、拷贝（{@code copy}）、相等比较（{@code equals}）、
 * 重置（{@code identity}）、插值（{@code lerp/autoLerp}）与叠加（{@code add}）。
 *
 * <p>这些钩子必须成组维护：漏掉 {@code equals} 会让关键帧误判相等，漏掉
 * {@code lerp/autoLerp} 会让回放时的参数丢失或闪烁。</p>
 */
@Mixin(value = PoseTransform.class, remap = false)
public class PoseTransformMixin implements BoneTextureHolder, GlintHolder, GlintSettingsHolder, BonePbrHolder, BoneOutlineHolder
{
    @Unique
    private Link bbspp_cml$texture;

    @Unique
    private boolean bbspp_cml$glint;

    /** 光效颜色，默认白色 = 原版观感。alpha 通道会一并乘进最终亮度。 */
    @Unique
    private final Color bbspp_cml$glintColor = new Color(1F, 1F, 1F, 1F);

    /** 光效流动速度倍率，1 = 原速，0 = 静止。 */
    @Unique
    private float bbspp_cml$glintSpeed = GlintSettingsHolder.DEFAULT_SPEED;

    /** 光效图层自发光强度，1 = 原始亮度。 */
    @Unique
    private float bbspp_cml$glintBrightness = GlintSettingsHolder.DEFAULT_BRIGHTNESS;

    /* PBR 五值（LabPBR specular channels），0~1，全 0 = 无 PBR。 */
    @Unique
    private float bbspp_cml$pbrSmoothness;

    @Unique
    private float bbspp_cml$pbrMetallic;

    @Unique
    private float bbspp_cml$pbrSss;

    @Unique
    private float bbspp_cml$pbrEmission;

    @Unique
    private float bbspp_cml$pbrRelief;

    /* 逐骨骼描边设置：跟随姿势/关键帧保存，不再写入 model config。 */
    @Unique
    private boolean bbspp_cml$outline;

    @Unique
    private final Color bbspp_cml$outlineColor = new Color(1F, 1F, 1F, 1F);

    @Unique
    private float bbspp_cml$outlineWidth = BoneOutlineHolder.DEFAULT_WIDTH;

    @Unique
    private float bbspp_cml$outlineGlow = BoneOutlineHolder.DEFAULT_GLOW;

    @Unique
    private float bbspp_cml$outlineVanillaGlow = BoneOutlineHolder.DEFAULT_VANILLA_GLOW;

    @Unique
    private float bbspp_cml$outlineDecalSize = BoneOutlineHolder.DEFAULT_DECAL;

    @Unique
    private boolean bbspp_cml$outlineFaceCull = BoneOutlineHolder.DEFAULT_FACE_CULL;

    /* 主描边壳的逐面颜色，索引与 OutlineGeometry.FACE_* 一致；0 = 跟随主颜色。 */
    @Unique
    private final int[] bbspp_cml$outlineFaces = new int[BoneOutlineHolder.FACE_COUNT];

    /* 第二层描边（"叠加描边"）：独立颜色、独立高级选项，只有开关打开时才绘制。 */
    @Unique
    private boolean bbspp_cml$outlineOverlay = BoneOutlineHolder.DEFAULT_OVERLAY;

    @Unique
    private final Color bbspp_cml$overlayColor = new Color(1F, 1F, 1F, 1F);

    /** 相对主描边再往外扩的厚度；主描边关闭时它就是整层厚度。 */
    @Unique
    private float bbspp_cml$overlayWidth = BoneOutlineHolder.DEFAULT_OVERLAY_WIDTH;

    @Unique
    private float bbspp_cml$overlayGlow = BoneOutlineHolder.DEFAULT_GLOW;

    @Unique
    private float bbspp_cml$overlayVanillaGlow = BoneOutlineHolder.DEFAULT_VANILLA_GLOW;

    @Unique
    private boolean bbspp_cml$overlayFaceCull = BoneOutlineHolder.DEFAULT_FACE_CULL;

    @Unique
    private final int[] bbspp_cml$overlayFaces = new int[BoneOutlineHolder.FACE_COUNT];

    @Override
    public Link bbspp_cml$getTexture()
    {
        return this.bbspp_cml$texture;
    }

    @Override
    public void bbspp_cml$setTexture(Link texture)
    {
        this.bbspp_cml$texture = texture;
    }

    @Override
    public boolean bbspp_cml$getGlint()
    {
        return this.bbspp_cml$glint;
    }

    @Override
    public void bbspp_cml$setGlint(boolean glint)
    {
        this.bbspp_cml$glint = glint;
    }

    @Override
    public Color bbspp_cml$getGlintColor()
    {
        return this.bbspp_cml$glintColor;
    }

    @Override
    public void bbspp_cml$setGlintColor(Color color)
    {
        if (color != null)
        {
            this.bbspp_cml$glintColor.copy(color);
        }
    }

    @Override
    public float bbspp_cml$getGlintSpeed()
    {
        return this.bbspp_cml$glintSpeed;
    }

    @Override
    public void bbspp_cml$setGlintSpeed(float speed)
    {
        this.bbspp_cml$glintSpeed = clampGlintSetting(speed, GlintSettingsHolder.MAX_SPEED);
    }

    @Override
    public float bbspp_cml$getGlintBrightness()
    {
        return this.bbspp_cml$glintBrightness;
    }

    @Override
    public void bbspp_cml$setGlintBrightness(float brightness)
    {
        this.bbspp_cml$glintBrightness = clampGlintSetting(brightness, GlintSettingsHolder.MAX_BRIGHTNESS);
    }

    @Unique
    private static float clampGlintSetting(float value, float max)
    {
        return Math.max(0F, Math.min(max, value));
    }

    /* ---- PBR getters / setters ---- */

    @Override
    public float bbspp_cml$getSmoothness()
    {
        return this.bbspp_cml$pbrSmoothness;
    }

    @Override
    public void bbspp_cml$setSmoothness(float value)
    {
        this.bbspp_cml$pbrSmoothness = value;
    }

    @Override
    public float bbspp_cml$getMetallic()
    {
        return this.bbspp_cml$pbrMetallic;
    }

    @Override
    public void bbspp_cml$setMetallic(float value)
    {
        this.bbspp_cml$pbrMetallic = value;
    }

    @Override
    public float bbspp_cml$getSss()
    {
        return this.bbspp_cml$pbrSss;
    }

    @Override
    public void bbspp_cml$setSss(float value)
    {
        this.bbspp_cml$pbrSss = value;
    }

    @Override
    public float bbspp_cml$getEmission()
    {
        return this.bbspp_cml$pbrEmission;
    }

    @Override
    public void bbspp_cml$setEmission(float value)
    {
        this.bbspp_cml$pbrEmission = value;
    }

    @Override
    public float bbspp_cml$getRelief()
    {
        return this.bbspp_cml$pbrRelief;
    }

    @Override
    public void bbspp_cml$setRelief(float value)
    {
        this.bbspp_cml$pbrRelief = value;
    }

    /* ---- outline getters / setters ---- */

    @Override
    public boolean bbspp_cml$getOutline()
    {
        return this.bbspp_cml$outline;
    }

    @Override
    public void bbspp_cml$setOutline(boolean outline)
    {
        this.bbspp_cml$outline = outline;
    }

    @Override
    public Color bbspp_cml$getOutlineColor()
    {
        return this.bbspp_cml$outlineColor;
    }

    @Override
    public void bbspp_cml$setOutlineColor(Color color)
    {
        if (color != null)
        {
            this.bbspp_cml$outlineColor.copy(color);
        }
    }

    @Override
    public float bbspp_cml$getOutlineWidth()
    {
        return this.bbspp_cml$outlineWidth;
    }

    @Override
    public void bbspp_cml$setOutlineWidth(float width)
    {
        this.bbspp_cml$outlineWidth = width;
    }

    @Override
    public float bbspp_cml$getOutlineGlow()
    {
        return this.bbspp_cml$outlineGlow;
    }

    @Override
    public void bbspp_cml$setOutlineGlow(float glow)
    {
        this.bbspp_cml$outlineGlow = glow;
    }

    @Override
    public float bbspp_cml$getOutlineVanillaGlow()
    {
        return this.bbspp_cml$outlineVanillaGlow;
    }

    @Override
    public void bbspp_cml$setOutlineVanillaGlow(float glow)
    {
        this.bbspp_cml$outlineVanillaGlow = glow;
    }

    @Override
    public float bbspp_cml$getOutlineDecalSize()
    {
        return this.bbspp_cml$outlineDecalSize;
    }

    @Override
    public void bbspp_cml$setOutlineDecalSize(float size)
    {
        this.bbspp_cml$outlineDecalSize = size;
    }

    @Override
    public boolean bbspp_cml$getOutlineFaceCull()
    {
        return this.bbspp_cml$outlineFaceCull;
    }

    @Override
    public void bbspp_cml$setOutlineFaceCull(boolean faceCull)
    {
        this.bbspp_cml$outlineFaceCull = faceCull;
    }

    @Override
    public int bbspp_cml$getOutlineFaceColor(int face)
    {
        return face >= 0 && face < BoneOutlineHolder.FACE_COUNT
            ? this.bbspp_cml$outlineFaces[face] : BoneOutlineHolder.DEFAULT_FACE_COLOR;
    }

    @Override
    public void bbspp_cml$setOutlineFaceColor(int face, int argb)
    {
        if (face >= 0 && face < BoneOutlineHolder.FACE_COUNT)
        {
            this.bbspp_cml$outlineFaces[face] = argb;
        }
    }

    /* ---- overlay (second shell) getters / setters ---- */

    @Override
    public boolean bbspp_cml$getOutlineOverlay()
    {
        return this.bbspp_cml$outlineOverlay;
    }

    @Override
    public void bbspp_cml$setOutlineOverlay(boolean overlay)
    {
        this.bbspp_cml$outlineOverlay = overlay;
    }

    @Override
    public Color bbspp_cml$getOverlayColor()
    {
        return this.bbspp_cml$overlayColor;
    }

    @Override
    public void bbspp_cml$setOverlayColor(Color color)
    {
        if (color != null)
        {
            this.bbspp_cml$overlayColor.copy(color);
        }
    }

    @Override
    public float bbspp_cml$getOverlayWidth()
    {
        return this.bbspp_cml$overlayWidth;
    }

    @Override
    public void bbspp_cml$setOverlayWidth(float width)
    {
        this.bbspp_cml$overlayWidth = width;
    }

    @Override
    public float bbspp_cml$getOverlayGlow()
    {
        return this.bbspp_cml$overlayGlow;
    }

    @Override
    public void bbspp_cml$setOverlayGlow(float glow)
    {
        this.bbspp_cml$overlayGlow = glow;
    }

    @Override
    public float bbspp_cml$getOverlayVanillaGlow()
    {
        return this.bbspp_cml$overlayVanillaGlow;
    }

    @Override
    public void bbspp_cml$setOverlayVanillaGlow(float glow)
    {
        this.bbspp_cml$overlayVanillaGlow = glow;
    }

    @Override
    public boolean bbspp_cml$getOverlayFaceCull()
    {
        return this.bbspp_cml$overlayFaceCull;
    }

    @Override
    public void bbspp_cml$setOverlayFaceCull(boolean faceCull)
    {
        this.bbspp_cml$overlayFaceCull = faceCull;
    }

    @Override
    public int bbspp_cml$getOverlayFaceColor(int face)
    {
        return face >= 0 && face < BoneOutlineHolder.FACE_COUNT
            ? this.bbspp_cml$overlayFaces[face] : BoneOutlineHolder.DEFAULT_FACE_COLOR;
    }

    @Override
    public void bbspp_cml$setOverlayFaceColor(int face, int argb)
    {
        if (face >= 0 && face < BoneOutlineHolder.FACE_COUNT)
        {
            this.bbspp_cml$overlayFaces[face] = argb;
        }
    }

    @Inject(method = "toData", at = @At("TAIL"), remap = false)
    private void bbspp_cml$writeTexture(MapType data, CallbackInfo ci)
    {
        if (this.bbspp_cml$texture != null)
        {
            data.put("texture", LinkUtils.toData(this.bbspp_cml$texture));
        }

        if (this.bbspp_cml$glint)
        {
            data.putBool("glint", true);
        }
        if (this.bbspp_cml$glintColor.getARGBColor() != 0xFFFFFFFF)
        {
            data.putInt("glint_color", this.bbspp_cml$glintColor.getARGBColor());
        }
        if (this.bbspp_cml$glintSpeed != GlintSettingsHolder.DEFAULT_SPEED)
        {
            data.putFloat("glint_speed", this.bbspp_cml$glintSpeed);
        }
        if (this.bbspp_cml$glintBrightness != GlintSettingsHolder.DEFAULT_BRIGHTNESS)
        {
            data.putFloat("glint_brightness", this.bbspp_cml$glintBrightness);
        }

        if (this.bbspp_cml$pbrSmoothness != 0F) data.putFloat("pbr_smoothness", this.bbspp_cml$pbrSmoothness);
        if (this.bbspp_cml$pbrMetallic != 0F)  data.putFloat("pbr_metallic", this.bbspp_cml$pbrMetallic);
        if (this.bbspp_cml$pbrSss != 0F)       data.putFloat("pbr_sss", this.bbspp_cml$pbrSss);
        if (this.bbspp_cml$pbrEmission != 0F)  data.putFloat("pbr_emission", this.bbspp_cml$pbrEmission);
        if (this.bbspp_cml$pbrRelief != 0F)    data.putFloat("pbr_relief", this.bbspp_cml$pbrRelief);

        boolean outlineData = this.bbspp_cml$outline
            || this.bbspp_cml$outlineColor.getARGBColor() != 0xFFFFFFFF
            || this.bbspp_cml$outlineWidth != BoneOutlineHolder.DEFAULT_WIDTH
            || this.bbspp_cml$outlineGlow != BoneOutlineHolder.DEFAULT_GLOW
            || this.bbspp_cml$outlineVanillaGlow != BoneOutlineHolder.DEFAULT_VANILLA_GLOW
            || this.bbspp_cml$outlineDecalSize != BoneOutlineHolder.DEFAULT_DECAL
            || this.bbspp_cml$outlineFaceCull != BoneOutlineHolder.DEFAULT_FACE_CULL;

        if (outlineData)
        {
            if (this.bbspp_cml$outline)
            {
                data.putBool("outline", true);
            }

            data.putInt("outline_color", this.bbspp_cml$outlineColor.getARGBColor());
            data.putFloat("outline_width", this.bbspp_cml$outlineWidth);
            data.putFloat("outline_glow", this.bbspp_cml$outlineGlow);
            data.putFloat("outline_vanilla_glow", this.bbspp_cml$outlineVanillaGlow);
            data.putFloat("outline_decal", this.bbspp_cml$outlineDecalSize);

            /* Only written when it differs from the default: an old save can then keep reading
             * "no key = culling on" without a migration step. */
            if (!this.bbspp_cml$outlineFaceCull)
            {
                data.putBool("outline_face_cull", false);
            }

            bbspp_cml$writeFaces(data, "outline_face_", this.bbspp_cml$outlineFaces);
        }

        boolean overlayData = this.bbspp_cml$outlineOverlay
            || this.bbspp_cml$overlayColor.getARGBColor() != 0xFFFFFFFF
            || this.bbspp_cml$overlayWidth != BoneOutlineHolder.DEFAULT_OVERLAY_WIDTH
            || this.bbspp_cml$overlayGlow != BoneOutlineHolder.DEFAULT_GLOW
            || this.bbspp_cml$overlayVanillaGlow != BoneOutlineHolder.DEFAULT_VANILLA_GLOW
            || this.bbspp_cml$overlayFaceCull != BoneOutlineHolder.DEFAULT_FACE_CULL
            || bbspp_cml$hasFaces(this.bbspp_cml$overlayFaces);

        if (overlayData)
        {
            if (this.bbspp_cml$outlineOverlay)
            {
                data.putBool("outline_overlay", true);
            }

            data.putInt("overlay_color", this.bbspp_cml$overlayColor.getARGBColor());
            data.putFloat("overlay_width", this.bbspp_cml$overlayWidth);
            data.putFloat("overlay_glow", this.bbspp_cml$overlayGlow);
            data.putFloat("overlay_vanilla_glow", this.bbspp_cml$overlayVanillaGlow);

            if (!this.bbspp_cml$overlayFaceCull)
            {
                data.putBool("overlay_face_cull", false);
            }

            bbspp_cml$writeFaces(data, "overlay_face_", this.bbspp_cml$overlayFaces);
        }
    }

    /**
     * Face colours are written one key per face ({@code outline_face_0} .. {@code _5}) instead of as
     * an int array, because that keeps a save readable in a text editor and lets a single face be
     * removed without renumbering the rest.
     */
    @Unique
    private static void bbspp_cml$writeFaces(MapType data, String prefix, int[] faces)
    {
        for (int i = 0; i < faces.length; i++)
        {
            if (faces[i] != BoneOutlineHolder.DEFAULT_FACE_COLOR)
            {
                data.putInt(prefix + i, faces[i]);
            }
        }
    }

    @Unique
    private static boolean bbspp_cml$hasFaces(int[] faces)
    {
        for (int face : faces)
        {
            if (face != BoneOutlineHolder.DEFAULT_FACE_COLOR)
            {
                return true;
            }
        }

        return false;
    }

    @Unique
    private static void bbspp_cml$readFaces(MapType data, String prefix, int[] faces)
    {
        for (int i = 0; i < faces.length; i++)
        {
            faces[i] = data.getInt(prefix + i, BoneOutlineHolder.DEFAULT_FACE_COLOR);
        }
    }

    @Inject(method = "fromData", at = @At("TAIL"), remap = false)
    private void bbspp_cml$readTexture(MapType data, CallbackInfo ci)
    {
        if (data.has("texture"))
        {
            this.bbspp_cml$texture = LinkUtils.create(data.get("texture"));
        }
        else
        {
            this.bbspp_cml$texture = null;
        }

        this.bbspp_cml$glint = data.getBool("glint");
        this.bbspp_cml$glintColor.set(data.getInt("glint_color", 0xFFFFFFFF));
        this.bbspp_cml$glintSpeed = data.has("glint_speed")
            ? data.getFloat("glint_speed") : GlintSettingsHolder.DEFAULT_SPEED;
        this.bbspp_cml$glintBrightness = data.has("glint_brightness")
            ? data.getFloat("glint_brightness") : GlintSettingsHolder.DEFAULT_BRIGHTNESS;

        this.bbspp_cml$pbrSmoothness = data.getFloat("pbr_smoothness");
        this.bbspp_cml$pbrMetallic = data.getFloat("pbr_metallic");
        this.bbspp_cml$pbrSss = data.getFloat("pbr_sss");
        this.bbspp_cml$pbrEmission = data.getFloat("pbr_emission");
        this.bbspp_cml$pbrRelief = data.getFloat("pbr_relief");

        this.bbspp_cml$outline = data.getBool("outline");
        this.bbspp_cml$outlineColor.set(data.getInt("outline_color", 0xFFFFFFFF));
        this.bbspp_cml$outlineWidth = data.has("outline_width")
            ? data.getFloat("outline_width") : BoneOutlineHolder.DEFAULT_WIDTH;
        this.bbspp_cml$outlineGlow = data.has("outline_glow")
            ? data.getFloat("outline_glow") : BoneOutlineHolder.DEFAULT_GLOW;
        /* 老存档没有这个键：旧版"发光"其实是一条全亮叠加层，所以按旧发光值是否开启来回填，
         * 让升级前的观感原样保留。 */
        this.bbspp_cml$outlineVanillaGlow = data.has("outline_vanilla_glow")
            ? data.getFloat("outline_vanilla_glow")
            : (this.bbspp_cml$outlineGlow > 0F
                ? BoneOutlineHolder.LEGACY_VANILLA_GLOW
                : BoneOutlineHolder.DEFAULT_VANILLA_GLOW);
        this.bbspp_cml$outlineDecalSize = data.has("outline_decal")
            ? data.getFloat("outline_decal") : BoneOutlineHolder.DEFAULT_DECAL;
        this.bbspp_cml$outlineFaceCull = data.has("outline_face_cull")
            ? data.getBool("outline_face_cull") : BoneOutlineHolder.DEFAULT_FACE_CULL;
        bbspp_cml$readFaces(data, "outline_face_", this.bbspp_cml$outlineFaces);

        this.bbspp_cml$outlineOverlay = data.getBool("outline_overlay");
        this.bbspp_cml$overlayColor.set(data.getInt("overlay_color", 0xFFFFFFFF));
        /* Read as-is: the width key shipped with the overlay itself, so a missing key already falls
         * back to the default. Rewriting a stored 1F to 4F quietly clamped a width the user picked. */
        this.bbspp_cml$overlayWidth = data.has("overlay_width")
            ? data.getFloat("overlay_width") : BoneOutlineHolder.DEFAULT_OVERLAY_WIDTH;
        this.bbspp_cml$overlayGlow = data.has("overlay_glow")
            ? data.getFloat("overlay_glow") : BoneOutlineHolder.DEFAULT_GLOW;
        this.bbspp_cml$overlayVanillaGlow = data.has("overlay_vanilla_glow")
            ? data.getFloat("overlay_vanilla_glow") : BoneOutlineHolder.DEFAULT_VANILLA_GLOW;
        this.bbspp_cml$overlayFaceCull = data.has("overlay_face_cull")
            ? data.getBool("overlay_face_cull") : BoneOutlineHolder.DEFAULT_FACE_CULL;
        bbspp_cml$readFaces(data, "overlay_face_", this.bbspp_cml$overlayFaces);
    }

    @Inject(method = "copy(Lmchorse/bbs_mod/utils/pose/Transform;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$copyTexture(mchorse.bbs_mod.utils.pose.Transform transform, CallbackInfo ci)
    {
        if (transform instanceof BoneTextureHolder holder)
        {
            this.bbspp_cml$texture = LinkUtils.copy(holder.bbspp_cml$getTexture());
        }

        if (transform instanceof GlintHolder holder)
        {
            this.bbspp_cml$glint = holder.bbspp_cml$getGlint();
            this.bbspp_cml$glintColor.copy(holder.bbspp_cml$getGlintColor());
        }

        if (transform instanceof GlintSettingsHolder holder)
        {
            this.bbspp_cml$glintSpeed = holder.bbspp_cml$getGlintSpeed();
            this.bbspp_cml$glintBrightness = holder.bbspp_cml$getGlintBrightness();
        }

        if (transform instanceof BonePbrHolder holder)
        {
            this.bbspp_cml$pbrSmoothness = holder.bbspp_cml$getSmoothness();
            this.bbspp_cml$pbrMetallic = holder.bbspp_cml$getMetallic();
            this.bbspp_cml$pbrSss = holder.bbspp_cml$getSss();
            this.bbspp_cml$pbrEmission = holder.bbspp_cml$getEmission();
            this.bbspp_cml$pbrRelief = holder.bbspp_cml$getRelief();
        }

        if (transform instanceof BoneOutlineHolder holder)
        {
            this.bbspp_cml$outline = holder.bbspp_cml$getOutline();
            this.bbspp_cml$outlineColor.copy(holder.bbspp_cml$getOutlineColor());
            this.bbspp_cml$outlineWidth = holder.bbspp_cml$getOutlineWidth();
            this.bbspp_cml$outlineGlow = holder.bbspp_cml$getOutlineGlow();
            this.bbspp_cml$outlineVanillaGlow = holder.bbspp_cml$getOutlineVanillaGlow();
            this.bbspp_cml$outlineDecalSize = holder.bbspp_cml$getOutlineDecalSize();
            this.bbspp_cml$outlineFaceCull = holder.bbspp_cml$getOutlineFaceCull();

            for (int i = 0; i < BoneOutlineHolder.FACE_COUNT; i++)
            {
                this.bbspp_cml$outlineFaces[i] = holder.bbspp_cml$getOutlineFaceColor(i);
                this.bbspp_cml$overlayFaces[i] = holder.bbspp_cml$getOverlayFaceColor(i);
            }

            this.bbspp_cml$outlineOverlay = holder.bbspp_cml$getOutlineOverlay();
            this.bbspp_cml$overlayColor.copy(holder.bbspp_cml$getOverlayColor());
            this.bbspp_cml$overlayWidth = holder.bbspp_cml$getOverlayWidth();
            this.bbspp_cml$overlayGlow = holder.bbspp_cml$getOverlayGlow();
            this.bbspp_cml$overlayVanillaGlow = holder.bbspp_cml$getOverlayVanillaGlow();
            this.bbspp_cml$overlayFaceCull = holder.bbspp_cml$getOverlayFaceCull();
        }
    }

    /**
     * 关键帧缓存/脏标记会用到 {@code contentHash()}。BBS 原版只哈希姿势可见字段，
     * 这里把光效与速度/亮度也算进去，避免"只改光效"被误判为同一姿势。
     */
    @Inject(method = "contentHash", at = @At("RETURN"), cancellable = true, remap = false)
    private void bbspp_cml$hashGlint(CallbackInfoReturnable<Integer> cir)
    {
        int hash = cir.getReturnValue();

        hash = 31 * hash + Boolean.hashCode(this.bbspp_cml$glint);
        hash = 31 * hash + this.bbspp_cml$glintColor.getARGBColor();
        hash = 31 * hash + Float.floatToIntBits(this.bbspp_cml$glintSpeed);
        hash = 31 * hash + Float.floatToIntBits(this.bbspp_cml$glintBrightness);
        /* The outline shell cache keys off this hash, so a slider-only edit has to change it —
         * otherwise the baked vanilla glow overlay keeps the strength it was built with. */
        hash = 31 * hash + Float.floatToIntBits(this.bbspp_cml$outlineVanillaGlow);
        hash = 31 * hash + Float.floatToIntBits(this.bbspp_cml$outlineGlow);
        hash = 31 * hash + Boolean.hashCode(this.bbspp_cml$outlineFaceCull);
        hash = 31 * hash + this.bbspp_cml$outlineColor.getARGBColor();
        hash = 31 * hash + Float.floatToIntBits(this.bbspp_cml$outlineWidth);
        hash = 31 * hash + Float.floatToIntBits(this.bbspp_cml$outlineDecalSize);

        /* 逐面颜色与叠加层同样进 hash：外壳顶点里烘焙的是最终颜色，这两组值一变就必须重建 VBO，
         * 否则改色后画面会停在旧颜色上。 */
        for (int face : this.bbspp_cml$outlineFaces)
        {
            hash = 31 * hash + face;
        }

        hash = 31 * hash + Boolean.hashCode(this.bbspp_cml$outlineOverlay);
        hash = 31 * hash + this.bbspp_cml$overlayColor.getARGBColor();
        hash = 31 * hash + Float.floatToIntBits(this.bbspp_cml$overlayWidth);
        hash = 31 * hash + Float.floatToIntBits(this.bbspp_cml$overlayGlow);
        hash = 31 * hash + Float.floatToIntBits(this.bbspp_cml$overlayVanillaGlow);
        hash = 31 * hash + Boolean.hashCode(this.bbspp_cml$overlayFaceCull);

        for (int face : this.bbspp_cml$overlayFaces)
        {
            hash = 31 * hash + face;
        }

        cir.setReturnValue(hash);
    }

    @Inject(method = "equals(Ljava/lang/Object;)Z", at = @At("RETURN"), cancellable = true, remap = false)
    private void bbspp_cml$equalsTexture(Object obj, CallbackInfoReturnable<Boolean> cir)
    {
        if (cir.getReturnValueZ() && obj instanceof BoneTextureHolder holder)
        {
            Link mine = this.bbspp_cml$texture;
            Link other = holder.bbspp_cml$getTexture();

            if (mine == null ? other != null : !mine.equals(other))
            {
                cir.setReturnValue(false);
            }
        }

        /* 布尔量同样必须参与判等：否则"只有光效不同"的两个 Pose 会被判等，
         * 关键帧去重/值比较场景会把开了光效的帧错误地合并掉。 */
        if (cir.getReturnValueZ() && obj instanceof GlintHolder holder)
        {
            if (this.bbspp_cml$glint != holder.bbspp_cml$getGlint()
                || this.bbspp_cml$glintColor.getARGBColor() != holder.bbspp_cml$getGlintColor().getARGBColor())
            {
                cir.setReturnValue(false);
            }
        }

        if (cir.getReturnValueZ() && obj instanceof GlintSettingsHolder holder)
        {
            if (Float.compare(this.bbspp_cml$glintSpeed, holder.bbspp_cml$getGlintSpeed()) != 0
                || Float.compare(this.bbspp_cml$glintBrightness, holder.bbspp_cml$getGlintBrightness()) != 0)
            {
                cir.setReturnValue(false);
            }
        }

        if (cir.getReturnValueZ() && obj instanceof BonePbrHolder holder)
        {
            if (this.bbspp_cml$pbrSmoothness != holder.bbspp_cml$getSmoothness()
                || this.bbspp_cml$pbrMetallic != holder.bbspp_cml$getMetallic()
                || this.bbspp_cml$pbrSss != holder.bbspp_cml$getSss()
                || this.bbspp_cml$pbrEmission != holder.bbspp_cml$getEmission()
                || this.bbspp_cml$pbrRelief != holder.bbspp_cml$getRelief())
            {
                cir.setReturnValue(false);
            }
        }

        if (cir.getReturnValueZ() && obj instanceof BoneOutlineHolder holder)
        {
            if (this.bbspp_cml$outline != holder.bbspp_cml$getOutline()
                || this.bbspp_cml$outlineColor.getARGBColor() != holder.bbspp_cml$getOutlineColor().getARGBColor()
                || this.bbspp_cml$outlineWidth != holder.bbspp_cml$getOutlineWidth()
                || this.bbspp_cml$outlineGlow != holder.bbspp_cml$getOutlineGlow()
                || this.bbspp_cml$outlineVanillaGlow != holder.bbspp_cml$getOutlineVanillaGlow()
                || this.bbspp_cml$outlineDecalSize != holder.bbspp_cml$getOutlineDecalSize()
                || this.bbspp_cml$outlineFaceCull != holder.bbspp_cml$getOutlineFaceCull()
                || this.bbspp_cml$outlineOverlay != holder.bbspp_cml$getOutlineOverlay())
            {
                cir.setReturnValue(false);
            }
            else
            {
                for (int i = 0; i < BoneOutlineHolder.FACE_COUNT; i++)
                {
                    if (this.bbspp_cml$outlineFaces[i] != holder.bbspp_cml$getOutlineFaceColor(i)
                        || this.bbspp_cml$overlayFaces[i] != holder.bbspp_cml$getOverlayFaceColor(i))
                    {
                        cir.setReturnValue(false);

                        break;
                    }
                }
            }
        }

        /* Always compared: gating these behind the overlay switch made equality asymmetric
         * (DEFAULT.equals(x) skipped them) and let an overlay-only transform count as default,
         * after which Pose.copy()/toData()/fromData() dropped it. */
        if (cir.getReturnValueZ() && obj instanceof BoneOutlineHolder holder)
        {
            if (this.bbspp_cml$overlayColor.getARGBColor() != holder.bbspp_cml$getOverlayColor().getARGBColor()
                || this.bbspp_cml$overlayWidth != holder.bbspp_cml$getOverlayWidth()
                || this.bbspp_cml$overlayGlow != holder.bbspp_cml$getOverlayGlow()
                || this.bbspp_cml$overlayVanillaGlow != holder.bbspp_cml$getOverlayVanillaGlow()
                || this.bbspp_cml$overlayFaceCull != holder.bbspp_cml$getOverlayFaceCull())
            {
                cir.setReturnValue(false);
            }
        }
    }

    /**
     * {@code Pose.copy()}, {@code Pose.toData()} and {@code Pose.fromData()} drop every transform
     * whose {@code isDefault()} is true, and {@code isDefault()} is just {@code equals(DEFAULT)}.
     * The equals hook only runs while the vanilla fields already compare equal and has to be kept
     * in sync by hand, so a forgotten field disappears on the next copy or save. Treating "carries
     * any extended data" as "not default" keeps those entries alive.
     */
    @Inject(method = "isDefault", at = @At("RETURN"), cancellable = true, remap = false)
    private void bbspp_cml$isDefaultWithExtras(CallbackInfoReturnable<Boolean> cir)
    {
        if (cir.getReturnValueZ() && this.bbspp_cml$hasExtraData())
        {
            cir.setReturnValue(false);
        }
    }

    @Unique
    private boolean bbspp_cml$hasExtraData()
    {
        if (this.bbspp_cml$texture != null || this.bbspp_cml$glint
            || this.bbspp_cml$glintColor.getARGBColor() != 0xFFFFFFFF
            || this.bbspp_cml$glintSpeed != GlintSettingsHolder.DEFAULT_SPEED
            || this.bbspp_cml$glintBrightness != GlintSettingsHolder.DEFAULT_BRIGHTNESS)
        {
            return true;
        }

        if (this.bbspp_cml$pbrSmoothness != 0F || this.bbspp_cml$pbrMetallic != 0F
            || this.bbspp_cml$pbrSss != 0F || this.bbspp_cml$pbrEmission != 0F
            || this.bbspp_cml$pbrRelief != 0F)
        {
            return true;
        }

        if (this.bbspp_cml$outline || this.bbspp_cml$outlineColor.getARGBColor() != 0xFFFFFFFF
            || this.bbspp_cml$outlineWidth != BoneOutlineHolder.DEFAULT_WIDTH
            || this.bbspp_cml$outlineGlow != BoneOutlineHolder.DEFAULT_GLOW
            || this.bbspp_cml$outlineVanillaGlow != BoneOutlineHolder.DEFAULT_VANILLA_GLOW
            || this.bbspp_cml$outlineDecalSize != BoneOutlineHolder.DEFAULT_DECAL
            || this.bbspp_cml$outlineFaceCull != BoneOutlineHolder.DEFAULT_FACE_CULL
            || this.bbspp_cml$outlineOverlay)
        {
            return true;
        }

        if (this.bbspp_cml$overlayColor.getARGBColor() != 0xFFFFFFFF
            || this.bbspp_cml$overlayWidth != BoneOutlineHolder.DEFAULT_OVERLAY_WIDTH
            || this.bbspp_cml$overlayGlow != BoneOutlineHolder.DEFAULT_GLOW
            || this.bbspp_cml$overlayVanillaGlow != BoneOutlineHolder.DEFAULT_VANILLA_GLOW
            || this.bbspp_cml$overlayFaceCull != BoneOutlineHolder.DEFAULT_FACE_CULL)
        {
            return true;
        }

        for (int i = 0; i < BoneOutlineHolder.FACE_COUNT; i++)
        {
            if (this.bbspp_cml$outlineFaces[i] != BoneOutlineHolder.DEFAULT_FACE_COLOR
                || this.bbspp_cml$overlayFaces[i] != BoneOutlineHolder.DEFAULT_FACE_COLOR)
            {
                return true;
            }
        }

        return false;
    }

    @Inject(method = "identity()V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$identityTexture(CallbackInfo ci)
    {
        this.bbspp_cml$texture = null;
        this.bbspp_cml$glint = false;
        this.bbspp_cml$glintColor.set(0xFFFFFFFF);
        this.bbspp_cml$glintSpeed = GlintSettingsHolder.DEFAULT_SPEED;
        this.bbspp_cml$glintBrightness = GlintSettingsHolder.DEFAULT_BRIGHTNESS;
        this.bbspp_cml$pbrSmoothness = 0F;
        this.bbspp_cml$pbrMetallic = 0F;
        this.bbspp_cml$pbrSss = 0F;
        this.bbspp_cml$pbrEmission = 0F;
        this.bbspp_cml$pbrRelief = 0F;

        this.bbspp_cml$outline = false;
        this.bbspp_cml$outlineColor.set(0xFFFFFFFF);
        this.bbspp_cml$outlineWidth = BoneOutlineHolder.DEFAULT_WIDTH;
        this.bbspp_cml$outlineGlow = BoneOutlineHolder.DEFAULT_GLOW;
        this.bbspp_cml$outlineVanillaGlow = BoneOutlineHolder.DEFAULT_VANILLA_GLOW;
        this.bbspp_cml$outlineDecalSize = BoneOutlineHolder.DEFAULT_DECAL;
        this.bbspp_cml$outlineFaceCull = BoneOutlineHolder.DEFAULT_FACE_CULL;
        Arrays.fill(this.bbspp_cml$outlineFaces, BoneOutlineHolder.DEFAULT_FACE_COLOR);

        this.bbspp_cml$outlineOverlay = BoneOutlineHolder.DEFAULT_OVERLAY;
        this.bbspp_cml$overlayColor.set(0xFFFFFFFF);
        this.bbspp_cml$overlayWidth = BoneOutlineHolder.DEFAULT_OVERLAY_WIDTH;
        this.bbspp_cml$overlayGlow = BoneOutlineHolder.DEFAULT_GLOW;
        this.bbspp_cml$overlayVanillaGlow = BoneOutlineHolder.DEFAULT_VANILLA_GLOW;
        this.bbspp_cml$overlayFaceCull = BoneOutlineHolder.DEFAULT_FACE_CULL;
        Arrays.fill(this.bbspp_cml$overlayFaces, BoneOutlineHolder.DEFAULT_FACE_COLOR);
    }

    @Inject(
        method = "lerp(Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/interps/IInterp;F)V",
        at = @At("TAIL"),
        remap = false
    )
    private void bbspp_cml$lerpTexture(mchorse.bbs_mod.utils.pose.Transform preA, mchorse.bbs_mod.utils.pose.Transform a, mchorse.bbs_mod.utils.pose.Transform b, mchorse.bbs_mod.utils.pose.Transform postB, mchorse.bbs_mod.utils.interps.IInterp interp, float x, CallbackInfo ci)
    {
        this.bbspp_cml$texture = LinkUtils.copy(bbspp_cml$closestTexture(a, b, x));
        this.bbspp_cml$glint = bbspp_cml$closestGlint(a, b, x);

        /* 光效颜色与调色一样可插值（照抄 textureTint 的实现）。 */
        if (preA instanceof GlintHolder preG && a instanceof GlintHolder curG
            && b instanceof GlintHolder nextG && postB instanceof GlintHolder postG)
        {
            Color preC = preG.bbspp_cml$getGlintColor();
            Color curC = curG.bbspp_cml$getGlintColor();
            Color nextC = nextG.bbspp_cml$getGlintColor();
            Color postC = postG.bbspp_cml$getGlintColor();

            this.bbspp_cml$glintColor.set(
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.r, curC.r, nextC.r, postC.r, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.g, curC.g, nextC.g, postC.g, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.b, curC.b, nextC.b, postC.b, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.a, curC.a, nextC.a, postC.a, x)), 0F, 1F)
            );
        }

        /* 光效速度/亮度是连续浮点，随关键帧插值。 */
        if (preA instanceof GlintSettingsHolder preS && a instanceof GlintSettingsHolder curS
            && b instanceof GlintSettingsHolder nextS && postB instanceof GlintSettingsHolder postS)
        {
            this.bbspp_cml$glintSpeed = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(
                preS.bbspp_cml$getGlintSpeed(), curS.bbspp_cml$getGlintSpeed(),
                nextS.bbspp_cml$getGlintSpeed(), postS.bbspp_cml$getGlintSpeed(), x)), 0F, GlintSettingsHolder.MAX_SPEED);
            this.bbspp_cml$glintBrightness = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(
                preS.bbspp_cml$getGlintBrightness(), curS.bbspp_cml$getGlintBrightness(),
                nextS.bbspp_cml$getGlintBrightness(), postS.bbspp_cml$getGlintBrightness(), x)), 0F, GlintSettingsHolder.MAX_BRIGHTNESS);
        }

        /* PBR 五值是连续浮点，可插值（与 glintColor 同构）。 */
        if (preA instanceof BonePbrHolder preP && a instanceof BonePbrHolder curP
            && b instanceof BonePbrHolder nextP && postB instanceof BonePbrHolder postP)
        {
            this.bbspp_cml$pbrSmoothness = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preP.bbspp_cml$getSmoothness(), curP.bbspp_cml$getSmoothness(), nextP.bbspp_cml$getSmoothness(), postP.bbspp_cml$getSmoothness(), x)), 0F, 1F);
            this.bbspp_cml$pbrMetallic = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preP.bbspp_cml$getMetallic(), curP.bbspp_cml$getMetallic(), nextP.bbspp_cml$getMetallic(), postP.bbspp_cml$getMetallic(), x)), 0F, 1F);
            this.bbspp_cml$pbrSss = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preP.bbspp_cml$getSss(), curP.bbspp_cml$getSss(), nextP.bbspp_cml$getSss(), postP.bbspp_cml$getSss(), x)), 0F, 1F);
            this.bbspp_cml$pbrEmission = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preP.bbspp_cml$getEmission(), curP.bbspp_cml$getEmission(), nextP.bbspp_cml$getEmission(), postP.bbspp_cml$getEmission(), x)), 0F, 1F);
            this.bbspp_cml$pbrRelief = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preP.bbspp_cml$getRelief(), curP.bbspp_cml$getRelief(), nextP.bbspp_cml$getRelief(), postP.bbspp_cml$getRelief(), x)), 0F, 1F);
        }

        /* 描边开关按最近关键帧，颜色/宽度/辉光/细化可插值。 */
        this.bbspp_cml$outline = bbspp_cml$closestOutline(a, b, x);
        this.bbspp_cml$outlineFaceCull = bbspp_cml$closestOutlineFaceCull(a, b, x);

        if (preA instanceof BoneOutlineHolder preO && a instanceof BoneOutlineHolder curO
            && b instanceof BoneOutlineHolder nextO && postB instanceof BoneOutlineHolder postO)
        {
            Color preC = preO.bbspp_cml$getOutlineColor();
            Color curC = curO.bbspp_cml$getOutlineColor();
            Color nextC = nextO.bbspp_cml$getOutlineColor();
            Color postC = postO.bbspp_cml$getOutlineColor();

            this.bbspp_cml$outlineColor.set(
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.r, curC.r, nextC.r, postC.r, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.g, curC.g, nextC.g, postC.g, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.b, curC.b, nextC.b, postC.b, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.a, curC.a, nextC.a, postC.a, x)), 0F, 1F)
            );

            this.bbspp_cml$outlineWidth = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(
                preO.bbspp_cml$getOutlineWidth(), curO.bbspp_cml$getOutlineWidth(),
                nextO.bbspp_cml$getOutlineWidth(), postO.bbspp_cml$getOutlineWidth(), x)), 0F, 10F);
            this.bbspp_cml$outlineGlow = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(
                preO.bbspp_cml$getOutlineGlow(), curO.bbspp_cml$getOutlineGlow(),
                nextO.bbspp_cml$getOutlineGlow(), postO.bbspp_cml$getOutlineGlow(), x)), 0F, 255F);
            this.bbspp_cml$outlineVanillaGlow = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(
                preO.bbspp_cml$getOutlineVanillaGlow(), curO.bbspp_cml$getOutlineVanillaGlow(),
                nextO.bbspp_cml$getOutlineVanillaGlow(), postO.bbspp_cml$getOutlineVanillaGlow(), x)), 0F, 255F);
            this.bbspp_cml$outlineDecalSize = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(
                preO.bbspp_cml$getOutlineDecalSize(), curO.bbspp_cml$getOutlineDecalSize(),
                nextO.bbspp_cml$getOutlineDecalSize(), postO.bbspp_cml$getOutlineDecalSize(), x)), 0F, 16F);

            bbspp_cml$lerpOverlay(interp, x, preO, curO, nextO, postO);
            bbspp_cml$closestFaces(a, b, x, this.bbspp_cml$outlineFaces, this.bbspp_cml$overlayFaces);
        }
    }

    /**
     * Shared overlay interpolation. Kept out of the two hooks so the lerp and autoLerp bodies stay
     * readable; {@code x} and the four poses are all either hook has to offer.
     */
    @Unique
    private void bbspp_cml$lerpOverlay(IInterp interp, float x,
        BoneOutlineHolder preO, BoneOutlineHolder curO, BoneOutlineHolder nextO, BoneOutlineHolder postO)
    {
        this.bbspp_cml$outlineOverlay = bbspp_cml$closestOverlay(curO, nextO, x);

        Color preC = preO.bbspp_cml$getOverlayColor();
        Color curC = curO.bbspp_cml$getOverlayColor();
        Color nextC = nextO.bbspp_cml$getOverlayColor();
        Color postC = postO.bbspp_cml$getOverlayColor();

        this.bbspp_cml$overlayColor.set(
            (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.r, curC.r, nextC.r, postC.r, x)), 0F, 1F),
            (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.g, curC.g, nextC.g, postC.g, x)), 0F, 1F),
            (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.b, curC.b, nextC.b, postC.b, x)), 0F, 1F),
            (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.a, curC.a, nextC.a, postC.a, x)), 0F, 1F)
        );

        this.bbspp_cml$overlayWidth = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(
            preO.bbspp_cml$getOverlayWidth(), curO.bbspp_cml$getOverlayWidth(),
            nextO.bbspp_cml$getOverlayWidth(), postO.bbspp_cml$getOverlayWidth(), x)), 0F, 10F);
        this.bbspp_cml$overlayGlow = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(
            preO.bbspp_cml$getOverlayGlow(), curO.bbspp_cml$getOverlayGlow(),
            nextO.bbspp_cml$getOverlayGlow(), postO.bbspp_cml$getOverlayGlow(), x)), 0F, 255F);
        this.bbspp_cml$overlayVanillaGlow = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(
            preO.bbspp_cml$getOverlayVanillaGlow(), curO.bbspp_cml$getOverlayVanillaGlow(),
            nextO.bbspp_cml$getOverlayVanillaGlow(), postO.bbspp_cml$getOverlayVanillaGlow(), x)), 0F, 255F);
    }

    /** AutoBezier twin of {@link #bbspp_cml$lerpOverlay}. */
    @Unique
    private void bbspp_cml$autoLerpOverlay(float pt, float at, float bt, float qt, boolean clamped, float x,
        BoneOutlineHolder preO, BoneOutlineHolder curO, BoneOutlineHolder nextO, BoneOutlineHolder postO)
    {
        this.bbspp_cml$outlineOverlay = bbspp_cml$closestOverlay(curO, nextO, x);

        Color preC = preO.bbspp_cml$getOverlayColor();
        Color curC = curO.bbspp_cml$getOverlayColor();
        Color nextC = nextO.bbspp_cml$getOverlayColor();
        Color postC = postO.bbspp_cml$getOverlayColor();

        this.bbspp_cml$overlayColor.set(
            (float) MathUtils.clamp(AutoBezier.get(preC.r, curC.r, nextC.r, postC.r, pt, at, bt, qt, clamped, x), 0F, 1F),
            (float) MathUtils.clamp(AutoBezier.get(preC.g, curC.g, nextC.g, postC.g, pt, at, bt, qt, clamped, x), 0F, 1F),
            (float) MathUtils.clamp(AutoBezier.get(preC.b, curC.b, nextC.b, postC.b, pt, at, bt, qt, clamped, x), 0F, 1F),
            (float) MathUtils.clamp(AutoBezier.get(preC.a, curC.a, nextC.a, postC.a, pt, at, bt, qt, clamped, x), 0F, 1F)
        );

        this.bbspp_cml$overlayWidth = (float) MathUtils.clamp(AutoBezier.get(
            preO.bbspp_cml$getOverlayWidth(), curO.bbspp_cml$getOverlayWidth(),
            nextO.bbspp_cml$getOverlayWidth(), postO.bbspp_cml$getOverlayWidth(),
            pt, at, bt, qt, clamped, x), 0F, 10F);
        this.bbspp_cml$overlayGlow = (float) MathUtils.clamp(AutoBezier.get(
            preO.bbspp_cml$getOverlayGlow(), curO.bbspp_cml$getOverlayGlow(),
            nextO.bbspp_cml$getOverlayGlow(), postO.bbspp_cml$getOverlayGlow(),
            pt, at, bt, qt, clamped, x), 0F, 255F);
        this.bbspp_cml$overlayVanillaGlow = (float) MathUtils.clamp(AutoBezier.get(
            preO.bbspp_cml$getOverlayVanillaGlow(), curO.bbspp_cml$getOverlayVanillaGlow(),
            nextO.bbspp_cml$getOverlayVanillaGlow(), postO.bbspp_cml$getOverlayVanillaGlow(),
            pt, at, bt, qt, clamped, x), 0F, 255F);
    }

    @Inject(
        method = "autoLerp(Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;FFFFZF)V",
        at = @At("TAIL"),
        remap = false
    )
    private void bbspp_cml$autoLerpTexture(mchorse.bbs_mod.utils.pose.Transform preA, mchorse.bbs_mod.utils.pose.Transform a, mchorse.bbs_mod.utils.pose.Transform b, mchorse.bbs_mod.utils.pose.Transform postB, float pt, float at, float bt, float qt, boolean clamped, float x, CallbackInfo ci)
    {
        this.bbspp_cml$texture = LinkUtils.copy(bbspp_cml$closestTexture(a, b, x));
        this.bbspp_cml$glint = bbspp_cml$closestGlint(a, b, x);

        /* 光效颜色与调色一样可插值（照抄 textureTint 的 AutoBezier 实现）。 */
        if (preA instanceof GlintHolder preG && a instanceof GlintHolder curG
            && b instanceof GlintHolder nextG && postB instanceof GlintHolder postG)
        {
            Color preC = preG.bbspp_cml$getGlintColor();
            Color curC = curG.bbspp_cml$getGlintColor();
            Color nextC = nextG.bbspp_cml$getGlintColor();
            Color postC = postG.bbspp_cml$getGlintColor();

            this.bbspp_cml$glintColor.set(
                (float) MathUtils.clamp(AutoBezier.get(preC.r, curC.r, nextC.r, postC.r, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preC.g, curC.g, nextC.g, postC.g, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preC.b, curC.b, nextC.b, postC.b, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preC.a, curC.a, nextC.a, postC.a, pt, at, bt, qt, clamped, x), 0F, 1F)
            );
        }

        /* 光效速度/亮度的 AutoBezier 插值。 */
        if (preA instanceof GlintSettingsHolder preS && a instanceof GlintSettingsHolder curS
            && b instanceof GlintSettingsHolder nextS && postB instanceof GlintSettingsHolder postS)
        {
            this.bbspp_cml$glintSpeed = (float) MathUtils.clamp(AutoBezier.get(
                preS.bbspp_cml$getGlintSpeed(), curS.bbspp_cml$getGlintSpeed(),
                nextS.bbspp_cml$getGlintSpeed(), postS.bbspp_cml$getGlintSpeed(),
                pt, at, bt, qt, clamped, x), 0F, GlintSettingsHolder.MAX_SPEED);
            this.bbspp_cml$glintBrightness = (float) MathUtils.clamp(AutoBezier.get(
                preS.bbspp_cml$getGlintBrightness(), curS.bbspp_cml$getGlintBrightness(),
                nextS.bbspp_cml$getGlintBrightness(), postS.bbspp_cml$getGlintBrightness(),
                pt, at, bt, qt, clamped, x), 0F, GlintSettingsHolder.MAX_BRIGHTNESS);
        }

        /* PBR 五值的 AutoBezier 插值。 */
        if (preA instanceof BonePbrHolder preP && a instanceof BonePbrHolder curP
            && b instanceof BonePbrHolder nextP && postB instanceof BonePbrHolder postP)
        {
            this.bbspp_cml$pbrSmoothness = (float) MathUtils.clamp(AutoBezier.get(preP.bbspp_cml$getSmoothness(), curP.bbspp_cml$getSmoothness(), nextP.bbspp_cml$getSmoothness(), postP.bbspp_cml$getSmoothness(), pt, at, bt, qt, clamped, x), 0F, 1F);
            this.bbspp_cml$pbrMetallic = (float) MathUtils.clamp(AutoBezier.get(preP.bbspp_cml$getMetallic(), curP.bbspp_cml$getMetallic(), nextP.bbspp_cml$getMetallic(), postP.bbspp_cml$getMetallic(), pt, at, bt, qt, clamped, x), 0F, 1F);
            this.bbspp_cml$pbrSss = (float) MathUtils.clamp(AutoBezier.get(preP.bbspp_cml$getSss(), curP.bbspp_cml$getSss(), nextP.bbspp_cml$getSss(), postP.bbspp_cml$getSss(), pt, at, bt, qt, clamped, x), 0F, 1F);
            this.bbspp_cml$pbrEmission = (float) MathUtils.clamp(AutoBezier.get(preP.bbspp_cml$getEmission(), curP.bbspp_cml$getEmission(), nextP.bbspp_cml$getEmission(), postP.bbspp_cml$getEmission(), pt, at, bt, qt, clamped, x), 0F, 1F);
            this.bbspp_cml$pbrRelief = (float) MathUtils.clamp(AutoBezier.get(preP.bbspp_cml$getRelief(), curP.bbspp_cml$getRelief(), nextP.bbspp_cml$getRelief(), postP.bbspp_cml$getRelief(), pt, at, bt, qt, clamped, x), 0F, 1F);
        }

        /* 描边开关按最近关键帧，颜色/宽度/辉光/细化可插值。 */
        this.bbspp_cml$outline = bbspp_cml$closestOutline(a, b, x);
        this.bbspp_cml$outlineFaceCull = bbspp_cml$closestOutlineFaceCull(a, b, x);

        if (preA instanceof BoneOutlineHolder preO && a instanceof BoneOutlineHolder curO
            && b instanceof BoneOutlineHolder nextO && postB instanceof BoneOutlineHolder postO)
        {
            Color preC = preO.bbspp_cml$getOutlineColor();
            Color curC = curO.bbspp_cml$getOutlineColor();
            Color nextC = nextO.bbspp_cml$getOutlineColor();
            Color postC = postO.bbspp_cml$getOutlineColor();

            this.bbspp_cml$outlineColor.set(
                (float) MathUtils.clamp(AutoBezier.get(preC.r, curC.r, nextC.r, postC.r, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preC.g, curC.g, nextC.g, postC.g, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preC.b, curC.b, nextC.b, postC.b, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preC.a, curC.a, nextC.a, postC.a, pt, at, bt, qt, clamped, x), 0F, 1F)
            );

            this.bbspp_cml$outlineWidth = (float) MathUtils.clamp(AutoBezier.get(
                preO.bbspp_cml$getOutlineWidth(), curO.bbspp_cml$getOutlineWidth(),
                nextO.bbspp_cml$getOutlineWidth(), postO.bbspp_cml$getOutlineWidth(),
                pt, at, bt, qt, clamped, x), 0F, 10F);
            this.bbspp_cml$outlineGlow = (float) MathUtils.clamp(AutoBezier.get(
                preO.bbspp_cml$getOutlineGlow(), curO.bbspp_cml$getOutlineGlow(),
                nextO.bbspp_cml$getOutlineGlow(), postO.bbspp_cml$getOutlineGlow(),
                pt, at, bt, qt, clamped, x), 0F, 255F);
            this.bbspp_cml$outlineVanillaGlow = (float) MathUtils.clamp(AutoBezier.get(
                preO.bbspp_cml$getOutlineVanillaGlow(), curO.bbspp_cml$getOutlineVanillaGlow(),
                nextO.bbspp_cml$getOutlineVanillaGlow(), postO.bbspp_cml$getOutlineVanillaGlow(),
                pt, at, bt, qt, clamped, x), 0F, 255F);
            this.bbspp_cml$outlineDecalSize = (float) MathUtils.clamp(AutoBezier.get(
                preO.bbspp_cml$getOutlineDecalSize(), curO.bbspp_cml$getOutlineDecalSize(),
                nextO.bbspp_cml$getOutlineDecalSize(), postO.bbspp_cml$getOutlineDecalSize(),
                pt, at, bt, qt, clamped, x), 0F, 16F);

            bbspp_cml$autoLerpOverlay(pt, at, bt, qt, clamped, x, preO, curO, nextO, postO);
            bbspp_cml$closestFaces(a, b, x, this.bbspp_cml$outlineFaces, this.bbspp_cml$overlayFaces);
        }
    }

    @Inject(method = "add(Lmchorse/bbs_mod/utils/pose/Transform;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$addTexture(mchorse.bbs_mod.utils.pose.Transform transform, CallbackInfo ci)
    {
        if (transform instanceof BoneTextureHolder holder)
        {
            Link texture = holder.bbspp_cml$getTexture();

            if (texture != null)
            {
                this.bbspp_cml$texture = LinkUtils.copy(texture);
            }
        }

        /* 叠加语义（对齐 texture 的"源有则覆盖"）：源开了光效就打开，
         * 源的颜色非默认白则覆盖 —— 只开过颜色但没开开关的源不会污染这里。 */
        if (transform instanceof GlintHolder holder)
        {
            if (holder.bbspp_cml$getGlint())
            {
                this.bbspp_cml$glint = true;
            }

            Color glintColor = holder.bbspp_cml$getGlintColor();

            if (glintColor != null && glintColor.getARGBColor() != 0xFFFFFFFF)
            {
                this.bbspp_cml$glintColor.copy(glintColor);
            }
        }

        if (transform instanceof GlintSettingsHolder holder)
        {
            if (holder.bbspp_cml$getGlintSpeed() != GlintSettingsHolder.DEFAULT_SPEED)
            {
                this.bbspp_cml$glintSpeed = holder.bbspp_cml$getGlintSpeed();
            }
            if (holder.bbspp_cml$getGlintBrightness() != GlintSettingsHolder.DEFAULT_BRIGHTNESS)
            {
                this.bbspp_cml$glintBrightness = holder.bbspp_cml$getGlintBrightness();
            }
        }

        /* PBR 叠加：源任一值 > 0 则覆盖（与材质级 hasPbr 语义一致）。 */
        if (transform instanceof BonePbrHolder holder)
        {
            if (holder.bbspp_cml$getSmoothness() > 0F) this.bbspp_cml$pbrSmoothness = holder.bbspp_cml$getSmoothness();
            if (holder.bbspp_cml$getMetallic() > 0F)  this.bbspp_cml$pbrMetallic = holder.bbspp_cml$getMetallic();
            if (holder.bbspp_cml$getSss() > 0F)       this.bbspp_cml$pbrSss = holder.bbspp_cml$getSss();
            if (holder.bbspp_cml$getEmission() > 0F)  this.bbspp_cml$pbrEmission = holder.bbspp_cml$getEmission();
            if (holder.bbspp_cml$getRelief() > 0F)    this.bbspp_cml$pbrRelief = holder.bbspp_cml$getRelief();
        }

        /* 描边叠加：源开启则连同参数一起覆盖。 */
        if (transform instanceof BoneOutlineHolder holder && holder.bbspp_cml$getOutline())
        {
            this.bbspp_cml$outline = true;
            this.bbspp_cml$outlineColor.copy(holder.bbspp_cml$getOutlineColor());
            this.bbspp_cml$outlineWidth = holder.bbspp_cml$getOutlineWidth();
            this.bbspp_cml$outlineGlow = holder.bbspp_cml$getOutlineGlow();
            this.bbspp_cml$outlineVanillaGlow = holder.bbspp_cml$getOutlineVanillaGlow();
            this.bbspp_cml$outlineDecalSize = holder.bbspp_cml$getOutlineDecalSize();
            this.bbspp_cml$outlineFaceCull = holder.bbspp_cml$getOutlineFaceCull();

            for (int i = 0; i < BoneOutlineHolder.FACE_COUNT; i++)
            {
                this.bbspp_cml$outlineFaces[i] = holder.bbspp_cml$getOutlineFaceColor(i);
            }
        }

        /* 叠加描边层同理：源打开了第二层才覆盖，且第二层可以独立于第一层存续。 */
        if (transform instanceof BoneOutlineHolder holder && holder.bbspp_cml$getOutlineOverlay())
        {
            this.bbspp_cml$outlineOverlay = true;
            this.bbspp_cml$overlayColor.copy(holder.bbspp_cml$getOverlayColor());
            this.bbspp_cml$overlayWidth = holder.bbspp_cml$getOverlayWidth();
            this.bbspp_cml$overlayGlow = holder.bbspp_cml$getOverlayGlow();
            this.bbspp_cml$overlayVanillaGlow = holder.bbspp_cml$getOverlayVanillaGlow();
            this.bbspp_cml$overlayFaceCull = holder.bbspp_cml$getOverlayFaceCull();

            for (int i = 0; i < BoneOutlineHolder.FACE_COUNT; i++)
            {
                this.bbspp_cml$overlayFaces[i] = holder.bbspp_cml$getOverlayFaceColor(i);
            }
        }
    }

    @Unique
    private static Link bbspp_cml$closestTexture(mchorse.bbs_mod.utils.pose.Transform a, mchorse.bbs_mod.utils.pose.Transform b, float x)
    {
        mchorse.bbs_mod.utils.pose.Transform closest = x < 0.5F ? a : b;

        return closest instanceof BoneTextureHolder holder ? holder.bbspp_cml$getTexture() : null;
    }

    /** 布尔量不可插值，与纹理同样取"最近关键帧"的值。 */
    @Unique
    private static boolean bbspp_cml$closestGlint(mchorse.bbs_mod.utils.pose.Transform a, mchorse.bbs_mod.utils.pose.Transform b, float x)
    {
        mchorse.bbs_mod.utils.pose.Transform closest = x < 0.5F ? a : b;

        return closest instanceof GlintHolder holder && holder.bbspp_cml$getGlint();
    }

    /** 描边开关同样取"最近关键帧"的值。 */
    @Unique
    private static boolean bbspp_cml$closestOutline(mchorse.bbs_mod.utils.pose.Transform a, mchorse.bbs_mod.utils.pose.Transform b, float x)
    {
        mchorse.bbs_mod.utils.pose.Transform closest = x < 0.5F ? a : b;

        return closest instanceof BoneOutlineHolder holder && holder.bbspp_cml$getOutline();
    }

    /** 面剔除开关也是布尔量，与描边开关一同取"最近关键帧"的值。 */
    @Unique
    private static boolean bbspp_cml$closestOutlineFaceCull(mchorse.bbs_mod.utils.pose.Transform a, mchorse.bbs_mod.utils.pose.Transform b, float x)
    {
        mchorse.bbs_mod.utils.pose.Transform closest = x < 0.5F ? a : b;

        return !(closest instanceof BoneOutlineHolder holder) || holder.bbspp_cml$getOutlineFaceCull();
    }

    /** 叠加描边开关同样是布尔量，取"最近关键帧"的值。 */
    @Unique
    private static boolean bbspp_cml$closestOverlay(BoneOutlineHolder a, BoneOutlineHolder b, float x)
    {
        return x < 0.5F ? a.bbspp_cml$getOutlineOverlay() : b.bbspp_cml$getOutlineOverlay();
    }

    /**
     * 逐面颜色是离散的调色板索引，插值只会插出一堆"两色之间没人选过的颜色"，所以两张面表都
     * 按"最近关键帧"整表取过来，与 {@link #bbspp_cml$closestOutline} 的规则一致。
     */
    @Unique
    private static void bbspp_cml$closestFaces(mchorse.bbs_mod.utils.pose.Transform a, mchorse.bbs_mod.utils.pose.Transform b,
        float x, int[] outlineFaces, int[] overlayFaces)
    {
        mchorse.bbs_mod.utils.pose.Transform closest = x < 0.5F ? a : b;

        if (!(closest instanceof BoneOutlineHolder holder))
        {
            Arrays.fill(outlineFaces, BoneOutlineHolder.DEFAULT_FACE_COLOR);
            Arrays.fill(overlayFaces, BoneOutlineHolder.DEFAULT_FACE_COLOR);

            return;
        }

        for (int i = 0; i < BoneOutlineHolder.FACE_COUNT; i++)
        {
            outlineFaces[i] = holder.bbspp_cml$getOutlineFaceColor(i);
            overlayFaces[i] = holder.bbspp_cml$getOverlayFaceColor(i);
        }
    }
}
