package gbeic.bbsplusplus.client.morph;

import org.junit.jupiter.api.Test;
import mchorse.bbs_mod.forms.forms.ModelForm;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class MorphRenderContextTest
{
    @Test
    void textureSyncPreferenceIsCarriedIntoTheRenderFrame()
    {
        MorphRenderContext.Frame frame = MorphRenderContext.push(null, null, "source", "target", 0.5F, false);

        try
        {
            assertEquals(false, MorphRenderContext.current().textureSync());
        }
        finally
        {
            MorphRenderContext.pop(frame);
        }
    }

    @Test
    void textureCacheKeyIncludesMappedUvRegions()
    {
        MutationCube.UvRegion first = new MutationCube.UvRegion(0F, 0F, 0.25F, 0.25F,
            0.5F, 0F, 0.75F, 0.25F);
        MutationCube.UvRegion second = new MutationCube.UvRegion(0F, 0F, 0.5F, 0.25F,
            0.5F, 0F, 0.75F, 0.25F);

        MorphTextures.Key firstKey = new MorphTextures.Key(null, null, List.of(first));
        MorphTextures.Key sameKey = new MorphTextures.Key(null, null, List.of(first));
        MorphTextures.Key otherMappingKey = new MorphTextures.Key(null, null, List.of(second));

        assertEquals(firstKey, sameKey);
        assertNotEquals(firstKey, otherMappingKey,
            "同一对图集的不同映射区域必须使用不同混合纹理缓存项");
    }

    @Test
    void nestedVanillaRenderDoesNotPopOrUseOuterMorphFrame()
    {
        MorphRenderContext.Frame outerFrame = MorphRenderContext.push(null, null, "outer", "target", 0.5F, true);
        ModelMorphRuntime.RenderScope outerScope = new ModelMorphRuntime.RenderScope(outerFrame);
        MorphRenderContext.beginInvocation(outerScope);

        try
        {
            assertEquals(outerFrame, MorphRenderContext.current());

            MorphRenderContext.beginInvocation(null);
            assertEquals(null, MorphRenderContext.current(), "嵌套普通渲染期间应屏蔽外层形变帧");
            assertEquals(null, MorphRenderContext.endInvocation());
            assertEquals(outerFrame, MorphRenderContext.current(), "嵌套返回后外层形变帧应恢复");

            ModelMorphRuntime.RenderScope popped = MorphRenderContext.endInvocation();
            assertEquals(outerScope, popped);
            assertEquals(outerFrame, MorphRenderContext.current(), "结束 render 调用不应隐式弹出形变帧");
        }
        finally
        {
            MorphRenderContext.pop(outerFrame);
        }
    }

    @Test
    void nestedModelFormsRestoreTheOuterRenderingForm()
    {
        ModelForm outer = new ModelForm();
        ModelForm inner = new ModelForm();

        MorphRenderContext.beginForm(outer);

        try
        {
            assertEquals(outer, MorphRenderContext.currentForm());
            MorphRenderContext.beginForm(inner);

            try
            {
                assertEquals(inner, MorphRenderContext.currentForm());
            }
            finally
            {
                MorphRenderContext.endForm();
            }

            assertEquals(outer, MorphRenderContext.currentForm());
        }
        finally
        {
            MorphRenderContext.endForm();
        }

        assertEquals(false, MorphRenderContext.hasCurrentForm());
    }
}
