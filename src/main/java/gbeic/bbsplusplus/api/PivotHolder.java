package gbeic.bbsplusplus.api;

import org.joml.Vector3f;

/**
 * 由 {@code TransformMixin} 实现的 duck 接口 —— 给 bbs-fs 的
 * {@code Transform} 外挂 CML 的"中心点"（pivot）字段。
 *
 * <p>返回的是可变 Vector3f（与 CML 的 {@code public final Vector3f pivot}
 * 语义一致），调用方直接 set/add/lerp 操作。消费点在
 * {@code Transform.setupMatrix}（旋转前 +pivot、缩放后 -pivot），
 * cubic 骨骼 / BOBJ 骨骼 / form 变换等一切经 setupMatrix/createMatrix
 * 构建矩阵的地方自动生效。</p>
 */
public interface PivotHolder
{
    Vector3f bbspp_cml$getPivot();
}
