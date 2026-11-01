package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.cubic.ModelInstance;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 1.20.x placeholder for the 1.21.11-only outline hook.
 *
 * <p>The shared mixin config lists {@code ModelInstanceOutlineMixin121} for every build. On 1.20.x
 * the real class lives in {@code java-1.21.11} and is not compiled, so this no-op mixin keeps the
 * config valid without pulling the 1.21.11 RenderPipeline types into the 1.20.x classpath. The
 * matching class in {@code java-1.21.11} replaces it on that branch.</p>
 */
@Mixin(value = ModelInstance.class, remap = false)
public abstract class ModelInstanceOutlineMixin121
{
}
