package gbeic.bbsplusplus.client.morph;

import mchorse.bbs_mod.cubic.data.model.ModelCube;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个表单实例的形变采样缓冲区。
 *
 * <p>缓冲区只在本实例内复用，避免每帧分配大数组；同一份 {@link MorphPlan} 被多个
 * 回放实例共享时，各自持有独立的几何结果，互不影响。</p>
 */
public final class MorphGeometry
{
    private static final int VERTEX_COUNT = MutationCube.FACE_COUNT * 4;

    private final MorphPlan plan;
    private final List<Buffer> allocated = new ArrayList<>();
    private final Map<ModelCube, List<Buffer>> buffers = new IdentityHashMap<>();
    private float sampled = Float.NaN;

    public MorphGeometry(MorphPlan plan)
    {
        this.plan = plan;

        for (Map.Entry<ModelCube, List<MorphPlan.Slot>> entry : plan.slots().entrySet())
        {
            List<Buffer> list = new ArrayList<>();

            for (int i = 0; i < entry.getValue().size(); i++)
            {
                Buffer buffer = new Buffer();

                buffer.slot = entry.getValue().get(i);
                buffer.mutation = buffer.slot.mutation();
                list.add(buffer);
                this.allocated.add(buffer);
            }

            this.buffers.put(entry.getKey(), list);
        }
    }

    public MorphPlan plan()
    {
        return this.plan;
    }

    public List<Buffer> get(ModelCube cube)
    {
        return this.buffers.get(cube);
    }

    /**
     * 按进度重新采样；进度未变时直接复用上一次结果。
     */
    public void sample(float progress)
    {
        float value = MorphMath.clamp01(progress);

        if (Float.isFinite(this.sampled) && Float.compare(this.sampled, value) == 0)
        {
            return;
        }

        this.sampled = value;

        for (Buffer buffer : this.allocated)
        {
            buffer.mutation.sample(value, buffer.positions, buffer.normals, buffer.uvs);
            buffer.visible = buffer.slot.visibleAt(value);
        }
    }

    /**
     * 一个过渡体的采样结果。
     */
    public static final class Buffer
    {
        private MorphPlan.Slot slot;
        private MutationCube mutation;
        private boolean visible = true;

        private final float[] positions = new float[VERTEX_COUNT * 3];
        private final float[] normals = new float[VERTEX_COUNT * 3];
        private final float[] uvs = new float[VERTEX_COUNT * 2];

        public float[] positions()
        {
            return this.positions;
        }

        public float[] normals()
        {
            return this.normals;
        }

        public float[] uvs()
        {
            return this.uvs;
        }

        public int faceMask()
        {
            return this.mutation.faceMask();
        }

        public int winding(int face)
        {
            return this.mutation.winding(face);
        }

        public boolean visible()
        {
            return this.visible;
        }

        public MutationCube mutation()
        {
            return this.mutation;
        }
    }
}
