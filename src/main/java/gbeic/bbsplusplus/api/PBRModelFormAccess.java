package gbeic.bbsplusplus.api;

import java.util.Map;

/**
 * ModelForm 的逐骨骼 PBR 覆盖数据访问接口（由 ModelFormCMLMixin 实现）。
 * <p>2.6 原生已提供逐材质 PBR，逐材质覆盖方法已移除；逐骨骼 PBR 为本插件独有能力，保留。</p>
 */
public interface PBRModelFormAccess
{
    Map<String, Map<String, Integer>> bbspp_snow$getBonePbrOverrides();
}
