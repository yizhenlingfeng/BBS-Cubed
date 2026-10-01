package bbslezy.camera.clips.screen;

public class GrainEffect implements LayeredEffect
{
    public int layer;
    public int renderOrder;

    @Override
    public int layer()
    {
        return this.layer;
    }

    @Override
    public int renderOrder()
    {
        return this.renderOrder;
    }

    public float strength;
    public float size;
}
