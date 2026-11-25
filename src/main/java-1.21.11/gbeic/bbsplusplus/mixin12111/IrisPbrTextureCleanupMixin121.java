package gbeic.bbsplusplus.mixin12111;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pbr.texture.PBRTextureManager", remap = false)
public class IrisPbrTextureCleanupMixin121
{
    @WrapOperation(
        method = "clear",
        at = @At(
            value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;"
        ),
        require = 0
    )
    private ObjectCollection<?> bbspp$detachHoldersBeforeClosing(Int2ObjectMap<?> holders,
                                                                 Operation<ObjectCollection<?>> original)
    {
        // Closing textures triggers callbacks that remove entries from this map.
        ObjectCollection<?> snapshot = new ObjectArrayList<>(original.call(holders));
        holders.clear();

        return snapshot;
    }
}
