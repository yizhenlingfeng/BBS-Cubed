package gbeic.bbsplusplus.api;

/**
 * 由 {@code UITransformMixin}（fillP 与默认空 setP）和
 * {@code UIPropTransformMixin}（setP 覆盖：写回 transform.pivot 并触发
 * pre/postCallback）实现的 duck 接口 —— 中心点编辑行的填充与写回入口。
 */
public interface TransformPivotEditor
{
    void bbspp_cml$fillP(double x, double y, double z);

    void bbspp_cml$setP(double x, double y, double z);
}
