package bbslezy.actions;

import mchorse.bbs_mod.actions.types.DamageActionClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.clips.Clip;

import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

public class LezyDamageActionClip extends DamageActionClip
{
    public LezyDamageActionClip()
    {
        super();
        this.damage.set(1.0F);
    }

    @Override
    public boolean isClient()
    {
        return true;
    }

    @Override
    protected void applyClientAction(IEntity entity, Film film, Replay replay, int tick)
    {
        if (entity != null && this.damage.get() > 0F)
        {
            entity.setHurtTimer(10);

            if (entity.getWorld() != null && entity.getWorld().isClient())
            {
                entity.getWorld().playSound(
                    entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.ENTITY_PLAYER_HURT,
                    SoundCategory.PLAYERS,
                    1.0F, 1.0F, false
                );
            }
        }
    }

    @Override
    public Clip create()
    {
        return new LezyDamageActionClip();
    }
}
