package gbeic.bbsplusplus.client.outline;

import mchorse.bbs_mod.settings.values.base.BaseValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueList;

/**
 * List of per-bone outline overrides.
 *
 * <p>{@link ValueList#copy(BaseValueGroup)} only recreates the elements by id and drops their
 * contents. Model configs are copied in a few editor paths, so override it to carry the limb
 * settings across.</p>
 */
public class OutlineLimbList extends ValueList<OutlineLimbValue>
{
    public OutlineLimbList(String id)
    {
        super(id);
    }

    @Override
    protected OutlineLimbValue create(String id)
    {
        return new OutlineLimbValue(id);
    }

    @Override
    public void copy(BaseValueGroup group)
    {
        this.list.clear();

        for (var value : group.getAll())
        {
            OutlineLimbValue limb = this.create(value.getId());

            this.add(limb);
            limb.copy(value);
        }
    }
}
