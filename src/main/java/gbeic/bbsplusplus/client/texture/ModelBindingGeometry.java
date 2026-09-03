package gbeic.bbsplusplus.client.texture;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.cubic.data.model.ModelQuad;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Intersectionf;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 提取模型绑定姿态下的三角形与包围盒。
 *
 * <p>纹理坐标烘焙和关键帧三维取点器都通过本类遍历同一套方块面、网格与骨骼初始变换，
 * 从源头保证运行时扩散坐标、鼠标拾取坐标和标记显示坐标完全一致。</p>
 */
public final class ModelBindingGeometry
{
    private static final float EPSILON = 0.0001F;

    private final List<Triangle> triangles;
    private final Vector3f min;
    private final Vector3f max;

    private ModelBindingGeometry(List<Triangle> triangles, Vector3f min, Vector3f max)
    {
        this.triangles = Collections.unmodifiableList(triangles);
        this.min = min;
        this.max = max;
    }

    public static ModelBindingGeometry create(ModelForm form)
    {
        ModelInstance instance = ModelFormRenderer.getModel(form);

        if (instance == null || !(instance.model instanceof Model model))
        {
            return null;
        }

        List<Triangle> triangles = new ArrayList<>();

        for (ModelGroup group : model.topGroups)
        {
            collectGroup(triangles, model, group, new Matrix4f());
        }

        if (triangles.isEmpty())
        {
            return null;
        }

        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);

        for (Triangle triangle : triangles)
        {
            include(min, max, triangle.a);
            include(min, max, triangle.b);
            include(min, max, triangle.c);
        }

        return new ModelBindingGeometry(triangles, min, max);
    }

    public List<Triangle> triangles()
    {
        return this.triangles;
    }

    public Vector3f min()
    {
        return new Vector3f(this.min);
    }

    public Vector3f max()
    {
        return new Vector3f(this.max);
    }

    public Vector3f normalize(Vector3f point)
    {
        return new Vector3f(
            normalize(point.x, this.min.x, this.max.x),
            normalize(point.y, this.min.y, this.max.y),
            normalize(point.z, this.min.z, this.max.z)
        );
    }

    public Vector3f denormalize(float x, float y, float z)
    {
        return new Vector3f(
            this.min.x + clamp(x) * (this.max.x - this.min.x),
            this.min.y + clamp(y) * (this.max.y - this.min.y),
            this.min.z + clamp(z) * (this.max.z - this.min.z)
        );
    }

    public Vector3f raycast(Vector3f origin, Vector3f direction)
    {
        float nearest = Float.POSITIVE_INFINITY;
        Vector3f hit = null;

        for (Triangle triangle : this.triangles)
        {
            float distance = Intersectionf.intersectRayTriangle(origin, direction, triangle.a, triangle.b, triangle.c, EPSILON);

            if (distance >= 0F && distance < nearest)
            {
                nearest = distance;
                hit = new Vector3f(direction).mul(distance).add(origin);
            }
        }

        return hit;
    }

    private static void collectGroup(List<Triangle> triangles, Model model, ModelGroup group, Matrix4f parent)
    {
        Matrix4f groupMatrix = applyTransform(parent, group.initial.translate, group.initial.rotate);

        for (ModelCube cube : group.cubes)
        {
            Matrix4f matrix = applyTransform(groupMatrix, cube.pivot, cube.rotate);

            for (ModelQuad quad : cube.quads)
            {
                if (quad.vertices.size() == 4)
                {
                    ModelVertex va = quad.vertices.get(0);
                    ModelVertex vb = quad.vertices.get(1);
                    ModelVertex vc = quad.vertices.get(2);
                    ModelVertex vd = quad.vertices.get(3);
                    Vector3f a = matrix.transformPosition(new Vector3f(va.vertex));
                    Vector3f b = matrix.transformPosition(new Vector3f(vb.vertex));
                    Vector3f c = matrix.transformPosition(new Vector3f(vc.vertex));
                    Vector3f d = matrix.transformPosition(new Vector3f(vd.vertex));

                    triangles.add(new Triangle(va.uv, vb.uv, vc.uv, a, b, c));
                    triangles.add(new Triangle(va.uv, vc.uv, vd.uv, a, c, d));
                }
            }
        }

        for (ModelMesh mesh : group.meshes)
        {
            Matrix4f matrix = applyTransform(groupMatrix, mesh.origin, mesh.rotate);

            for (int i = 0; i + 2 < mesh.baseData.vertices.size(); i += 3)
            {
                Vector3f a = matrix.transformPosition(new Vector3f(mesh.baseData.vertices.get(i)).div(16F));
                Vector3f b = matrix.transformPosition(new Vector3f(mesh.baseData.vertices.get(i + 1)).div(16F));
                Vector3f c = matrix.transformPosition(new Vector3f(mesh.baseData.vertices.get(i + 2)).div(16F));
                Vector2f ua = new Vector2f(mesh.baseData.uvs.get(i)).div(model.textureWidth, model.textureHeight);
                Vector2f ub = new Vector2f(mesh.baseData.uvs.get(i + 1)).div(model.textureWidth, model.textureHeight);
                Vector2f uc = new Vector2f(mesh.baseData.uvs.get(i + 2)).div(model.textureWidth, model.textureHeight);

                triangles.add(new Triangle(ua, ub, uc, a, b, c));
            }
        }

        for (ModelGroup child : group.children)
        {
            collectGroup(triangles, model, child, groupMatrix);
        }
    }

    private static Matrix4f applyTransform(Matrix4f parent, Vector3f pivot, Vector3f rotate)
    {
        float px = pivot.x / 16F;
        float py = pivot.y / 16F;
        float pz = pivot.z / 16F;

        return new Matrix4f(parent)
            .translate(px, py, pz)
            .rotateZ(MathUtils.toRad(rotate.z))
            .rotateY(MathUtils.toRad(rotate.y))
            .rotateX(MathUtils.toRad(rotate.x))
            .translate(-px, -py, -pz);
    }

    private static void include(Vector3f min, Vector3f max, Vector3f point)
    {
        min.min(point);
        max.max(point);
    }

    private static float normalize(float value, float min, float max)
    {
        float range = max - min;

        return Math.abs(range) < EPSILON ? 0.5F : clamp((value - min) / range);
    }

    private static float clamp(float value)
    {
        return Math.max(0F, Math.min(1F, value));
    }

    public record Triangle(Vector2f ua, Vector2f ub, Vector2f uc, Vector3f a, Vector3f b, Vector3f c)
    {}
}
