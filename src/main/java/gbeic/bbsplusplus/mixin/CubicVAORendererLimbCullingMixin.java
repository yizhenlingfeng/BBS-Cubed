package gbeic.bbsplusplus.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import gbeic.bbsplusplus.api.LimbCullingConfigHolder;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.CubicVAORenderer;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "mchorse.bbs_mod.cubic.render.CubicVAORenderer", remap = true)
public abstract class CubicVAORendererLimbCullingMixin
{
    @Shadow
    private ModelInstance model;

    @Unique
    private boolean bbspp$forcedCull;

    @Inject(method = "renderGroup", at = @At("HEAD"))
    private void bbspp$forceCullHead(BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model, CallbackInfoReturnable<Boolean> cir)
    {
        this.bbspp$forcedCull = false;
        if (group != null && group.id != null && this.model != null
            && this.model.config instanceof LimbCullingConfigHolder holder
            && holder.bbspp$getLimbCullingBones().contains(group.id))
        {
            this.bbspp$forcedCull = true;
            RenderSystem.enableCull();
        }
    }

    @Inject(method = "renderGroup", at = @At("RETURN"))
    private void bbspp$forceCullReturn(BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model, CallbackInfoReturnable<Boolean> cir)
    {
        if (!this.bbspp$forcedCull) return;
        if (this.model != null && this.model.isCulling())
        {
            RenderSystem.enableCull();
        }
        else
        {
            RenderSystem.disableCull();
        }
        this.bbspp$forcedCull = false;
    }
}
