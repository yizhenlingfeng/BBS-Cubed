package bbslezy.camera.clips.screen;

public class LetterboxEffect implements LayeredEffect
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

    public float size;
    public float smoothness;
    public int color;
    public float rotation;
    public float zoom = 1F;
    public float offsetX;
    public float offsetY;
    public float width = 1F;
}
