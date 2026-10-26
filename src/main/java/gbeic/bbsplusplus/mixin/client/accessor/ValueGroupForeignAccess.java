package gbeic.bbsplusplus.mixin.client.accessor;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Gives access to {@code ValueGroup}'s private bag of unknown ("foreign") data keys.
 *
 * <p>BBS only round-trips unknown keys when they contain a {@code :} (namespaced keys),
 * which is why the lock state is stored as {@code bbspp:editor_locks} instead of a
 * plain child value.</p>
 */
@Mixin(value = ValueGroup.class, remap = false)
public interface ValueGroupForeignAccess
{
    @Accessor(value = "foreign", remap = false)
    Map<String, BaseType> bbspp$getForeign();

    @Accessor(value = "foreign", remap = false)
    void bbspp$setForeign(Map<String, BaseType> foreign);
}
