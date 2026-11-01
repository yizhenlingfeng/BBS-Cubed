package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.outline.OutlineEmissiveTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ReloadableResourceManagerImpl;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourcePack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.ByteArrayInputStream;
import java.util.Optional;

/**
 * Serves the runtime-generated outline PNGs as if they were files in the active
 * resource pack. Iris's SimplePBRLoader resolves {@code foo_s.png}/{@code foo_n.png}
 * through the resource manager, so the companions must be visible here, not merely
 * generated as a texture.
 */
@Mixin(ReloadableResourceManagerImpl.class)
public abstract class ReloadableResourceManagerImplOutlineMixin
{
    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
    private void bbspp$serveOutlinePng(Identifier location, CallbackInfoReturnable<Optional<Resource>> cir)
    {
        byte[] png = OutlineEmissiveTexture.getPng(location);

        if (png == null)
        {
            return;
        }

        ResourcePack pack = MinecraftClient.getInstance().getResourceManager()
            .streamResourcePacks().findFirst().orElse(null);

        if (pack == null)
        {
            cir.setReturnValue(Optional.empty());
            return;
        }

        cir.setReturnValue(Optional.of(new Resource(pack, () -> new ByteArrayInputStream(png))));
    }
}
