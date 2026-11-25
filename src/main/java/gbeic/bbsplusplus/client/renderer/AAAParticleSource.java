package gbeic.bbsplusplus.client.renderer;

import java.lang.ref.WeakReference;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;

/**
 * Identifies the model block a source-rendered AAA particle effect is attached to.
 *
 * <p>Stores the world registry key, the block position and the identity hash of the
 * {@link ModelBlockEntity}. The identity hash is kept (rather than a strong reference
 * to the block entity) so that a {@link java.util.WeakHashMap} keyed by entity can
 * still release entries once the entity is gone.</p>
 *
 * @param blockEntityIdentity the identity hash of the bound model block entity
 */
record AAAParticleSource(RegistryKey<World> worldKey, BlockPos pos, int blockEntityIdentity)
{
    AAAParticleSource(World world, BlockPos pos, ModelBlockEntity modelBlock)
    {
        this(world.getRegistryKey(), pos.toImmutable(), System.identityHashCode(modelBlock));
    }

    boolean isAlive(World world)
    {
        if (world == null || !world.getRegistryKey().equals(this.worldKey))
        {
            return false;
        }

        if (world.getBlockEntity(this.pos) instanceof ModelBlockEntity modelBlock)
        {
            return System.identityHashCode(modelBlock) == this.blockEntityIdentity;
        }

        return false;
    }
}
