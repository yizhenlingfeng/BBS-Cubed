package gbeic.bbsplusplus.simulation;

public class FluidSimulation
{
    /* 单格注入幅度上限：避免相邻多帧叠加导致 buffer1 振幅无界增长（鬼畜根因） */
    private static final float MAX_CELL_AMPLITUDE = 8.0F;

    /* 固定步长推进：每个 tick 按真实经过时间累积，攒满一步才 update()，
       使耗散/传播频率与客户端 tick 一致、与渲染帧率解耦 */
    private static final float STEPS_PER_SECOND = 60.0F;
    private static final float MAX_STEP_CATCHUP = 4.0F;

    private float[] buffer1;
    private float[] buffer2;
    private int width;
    private int height;
    public float viscosity = 0.96f;

    private float stepAccumulator;

    public FluidSimulation(int width, int height)
    {
        this.width = width;
        this.height = height;
        this.buffer1 = new float[width * height];
        this.buffer2 = new float[width * height];
    }

    public int getWidth()
    {
        return this.width;
    }

    public int getHeight()
    {
        return this.height;
    }

    /**
     * 清空整场（尺寸变更重建后调用），避免残留能量导致画面突跳。
     */
    public void clear()
    {
        java.util.Arrays.fill(this.buffer1, 0F);
        java.util.Arrays.fill(this.buffer2, 0F);
        this.stepAccumulator = 0F;
    }

    /**
     * 按固定步长推进物理。dtSeconds 为自上次调用以来经过的秒数（真实时间），
     * 每积满 1/STEPS_PER_SECOND 秒执行一步 update()，单次最多追赶 MAX_STEP_CATCHUP 步，
     * 防止长时间未渲染后一次性追太多步而爆掉。
     */
    public void advance(float dtSeconds)
    {
        if (dtSeconds <= 0F)
        {
            return;
        }

        this.stepAccumulator += dtSeconds * STEPS_PER_SECOND;

        int steps = 0;

        while (this.stepAccumulator >= 1F && steps < MAX_STEP_CATCHUP)
        {
            this.update();
            this.stepAccumulator -= 1F;
            steps++;
        }

        if (this.stepAccumulator > 1F)
        {
            /* 丢弃过多积压，避免后续帧继续追赶造成抖动 */
            this.stepAccumulator = 1F;
        }
    }

    public void update()
    {
        for (int i = this.width; i < this.width * this.height - this.width; i++)
        {
            /* Check boundaries to avoid wrapping artifacts */
            int x = i % this.width;
            if (x == 0 || x == this.width - 1) continue;

            float val = (this.buffer1[i - 1] +
                         this.buffer1[i + 1] +
                         this.buffer1[i - this.width] +
                         this.buffer1[i + this.width]) / 2.0f - this.buffer2[i];
            
            val *= this.viscosity;
            this.buffer2[i] = val;
        }

        /* Swap buffers */
        float[] temp = this.buffer1;
        this.buffer1 = this.buffer2;
        this.buffer2 = temp;
    }

    public void addForce(int x, int y, float force)
    {
        if (x > 0 && x < this.width - 1 && y > 0 && y < this.height - 1)
        {
            int idx = x + y * this.width;
            float next = this.buffer1[idx] + force;

            /* 夹紧单格振幅，杜绝能量无界累加导致的剧烈抖动 */
            if (next > MAX_CELL_AMPLITUDE) next = MAX_CELL_AMPLITUDE;
            else if (next < -MAX_CELL_AMPLITUDE) next = -MAX_CELL_AMPLITUDE;

            this.buffer1[idx] = next;
        }
    }

    public float getHeight(int x, int y)
    {
        if (x >= 0 && x < this.width && y >= 0 && y < this.height)
        {
            return this.buffer1[x + y * this.width];
        }
        
        return 0.0f;
    }
}
