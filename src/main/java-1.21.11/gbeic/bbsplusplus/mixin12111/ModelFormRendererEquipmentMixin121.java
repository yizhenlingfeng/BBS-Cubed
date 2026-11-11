package gbeic.bbsplusplus.mixin12111;

import gbeic.bbsplusplus.keyframes.EquipmentTransformRuntime;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 1.21.11 版：把装备/手持物品关键帧里的附加变换叠加到模型渲染。
 *
 * <p>1.20.x 的同名 mixin（{@code gbeic.bbsplusplus.mixin.ModelFormRendererEquipmentMixin}）
 * 之所以不能直接在 1.21.11 复用，有两个原因：
 * </p>
 * <ul>
 *   <li>它的第一个 {@code @Redirect} 瞄准的是
 *       {@code ModelInstance#render(MatrixStack, Supplier, Color, int, int, StencilMap, ShapeKeys, Function)}
 *       —— 1.21.11 已经没有 {@code Supplier<ShaderProgram>} 这个参数了，描述符
 *       整体变成 {@code (MatrixStack, Color, int, int, StencilMap, ShapeKeys, Function)}；
 *       该 UV 上下文职责已由 {@link ModelInstanceUvContextMixin121} 接管。</li>
 *   <li>{@code ModelTransformationMode} 已重命名为 {@code net.minecraft.item.ItemDisplayContext}。</li>
 * </ul>
 *
 * <p>本 mixin 只保留两处真正需要的注入：盔甲槽与手持槽在应用了模型自带的挂点变换之后，
 * 继续叠加当前关键帧采样出的槽位变换。注入点与 1.20.x 完全同构
 * （{@code MatrixStackUtils#applyTransform} 之后），因此 1.20.1 / 1.21.11 的行为一致。
 * </p>
 */
@Mixin(value = ModelFormRenderer.class, remap = true)
public class ModelFormRendererEquipmentMixin121
{
    private static final String APPLY_TRANSFORM = "Lmchorse/bbs_mod/utils/MatrixStackUtils;applyTransform(Lnet/minecraft/client/util/math/MatrixStack;Lmchorse/bbs_mod/utils/pose/Transform;)V";

    private static final String RENDER_ARMOR = "renderArmor(Lmchorse/bbs_mod/forms/entities/IEntity;Lnet/minecraft/client/util/math/MatrixStack;Lmchorse/bbs_mod/cubic/model/ArmorType;Lmchorse/bbs_mod/cubic/model/ArmorSlot;Lmchorse/bbs_mod/utils/colors/Color;III)V";

    private static final String RENDER_ITEMS = "renderItems(Lmchorse/bbs_mod/forms/entities/IEntity;Lmchorse/bbs_mod/cubic/ModelInstance;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/entity/EquipmentSlot;Lnet/minecraft/item/ItemDisplayContext;Ljava/util/List;Lmchorse/bbs_mod/utils/colors/Color;III)V";

    /**
     * 盔甲槽：模型配置的挂点变换之后，叠加关键帧的槽位变换。
     */
    @Inject(
        method = RENDER_ARMOR,
        at = @At(value = "INVOKE", target = APPLY_TRANSFORM, shift = At.Shift.AFTER)
    )
    private void bbspp$applyArmorKeyframeTransform(IEntity target, MatrixStack stack, ArmorType type, ArmorSlot armorSlot, Color color, int overlay, int light, CallbackInfo ci)
    {
        this.bbspp$applyEquipmentTransform(target, stack, type.slot);
    }

    /**
     * 主手 / 副手：同样是挂点变换之后叠加关键帧的槽位变换。
     *
     * <p>注意 1.21.11 里 {@code renderItems} 的两个旋转与 0.125 位移发生在
     * {@code applyTransform} <b>之前</b>，所以本注入仍在同一局部坐标系里生效。</p>
     */
    @Inject(
        method = RENDER_ITEMS,
        at = @At(value = "INVOKE", target = APPLY_TRANSFORM, shift = At.Shift.AFTER)
    )
    private void bbspp$applyItemKeyframeTransform(IEntity target, ModelInstance model, MatrixStack stack, EquipmentSlot slot, ItemDisplayContext mode, List<ArmorSlot> items, Color color, int overlay, int light, CallbackInfo ci)
    {
        this.bbspp$applyEquipmentTransform(target, stack, slot);
    }

    private void bbspp$applyEquipmentTransform(IEntity target, MatrixStack stack, EquipmentSlot slot)
    {
        Transform transform = EquipmentTransformRuntime.get(target, slot);

        if (transform != null && !transform.isDefault())
        {
            MatrixStackUtils.applyTransform(stack, transform);
        }
    }
}
