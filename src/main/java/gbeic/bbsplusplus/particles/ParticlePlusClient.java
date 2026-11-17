package gbeic.bbsplusplus.particles;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.particles.components.ParticleComponentCollisionAppearance;
import gbeic.bbsplusplus.particles.components.ParticleComponentCollisionTinting;
import gbeic.bbsplusplus.particles.components.ParticleComponentParticleMorph;
import mchorse.bbs_mod.particles.ParticleMaterial;
import mchorse.bbs_mod.particles.ParticleScheme;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * Particle Plus 的客户端注册点：三个自定义组件 + 一个自定义材质。
 *
 * <p>组件走 {@code ParticleScheme.PARSER.components} 公开注册表，跨版本稳定。
 * 材质则没有这种入口 —— {@link ParticleMaterial} 是上游的枚举，加不进去，只能
 * 把自己造出来的常量塞进它的 {@code $VALUES} 静态数组（见
 * {@link #injectAdditiveMaterial()}）。</p>
 *
 * <p>{@code sun.misc.Unsafe} 全程通过反射取用，不在编译期引用：1.21.11 的
 * 编译目标裁掉了 {@code jdk.unsupported} 的文档化视图，直接 import 会编译失败。</p>
 *
 * <p>注入失败不是致命错误：三个组件照常注册，只是下拉里少一个 Additive，
 * 老方案里已存成 {@code particles_add} 的会被 {@code fromString} 静默回落成
 * {@code OPAQUE}。</p>
 */
public final class ParticlePlusClient
{
    public static final String MORPH_COMPONENT = "blockbuster:particle_morph";
    public static final String COLLISION_APPEARANCE_COMPONENT = "blockbuster:particle_collision_appearance";
    public static final String COLLISION_TINTING_COMPONENT = "blockbuster:particle_collision_tinting";
    public static final String ADDITIVE_MATERIAL_ID = "particles_add";

    private static ParticleMaterial additiveMaterial;
    private static boolean initialized;

    private ParticlePlusClient()
    {}

    public static synchronized void initialize()
    {
        if (initialized)
        {
            return;
        }

        initialized = true;

        for (ParticleMaterial material : ParticleMaterial.values())
        {
            if (ADDITIVE_MATERIAL_ID.equals(material.id))
            {
                additiveMaterial = material;
                break;
            }
        }

        if (additiveMaterial == null)
        {
            try
            {
                additiveMaterial = injectAdditiveMaterial();
                BBSPlusPlusMod.LOGGER.info("[Particle+] 已注册 Additive 粒子材质");
            }
            catch (Throwable throwable)
            {
                BBSPlusPlusMod.LOGGER.warn("[Particle+] Additive 粒子材质注册失败，材质下拉将只有原版三项", throwable);
            }
        }

        ParticleScheme.PARSER.components.put(MORPH_COMPONENT, ParticleComponentParticleMorph.class);
        ParticleScheme.PARSER.components.put(COLLISION_APPEARANCE_COMPONENT, ParticleComponentCollisionAppearance.class);
        ParticleScheme.PARSER.components.put(COLLISION_TINTING_COMPONENT, ParticleComponentCollisionTinting.class);
    }

    public static boolean hasAdditiveMaterial()
    {
        return additiveMaterial != null;
    }

    public static boolean isAdditive(ParticleMaterial material)
    {
        return material != null && ADDITIVE_MATERIAL_ID.equals(material.id);
    }

    /**
     * 造一个游离的 {@link ParticleMaterial} 常量并追加到它的 {@code $VALUES}。
     *
     * <p>{@code name}/{@code ordinal} 是 {@code java.lang.Enum} 的 final 字段，Java 17+
     * 已经不允许反射写 {@code java.base} 的字段（{@code InaccessibleObjectException}），
     * 所以必须走 Unsafe 的裸内存写。{@code values()} 每次调用都 clone
     * {@code $VALUES}，替换该静态字段即可让新常量对所有读取方可见。</p>
     */
    private static ParticleMaterial injectAdditiveMaterial() throws Exception
    {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");

        unsafeField.setAccessible(true);

        Object unsafe = unsafeField.get(null);
        Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        Method objectFieldOffset = unsafeClass.getMethod("objectFieldOffset", Field.class);
        Method staticFieldBase = unsafeClass.getMethod("staticFieldBase", Field.class);
        Method staticFieldOffset = unsafeClass.getMethod("staticFieldOffset", Field.class);
        Method putObject = unsafeClass.getMethod("putObject", Object.class, long.class, Object.class);
        Method putInt = unsafeClass.getMethod("putInt", Object.class, long.class, int.class);
        Method putObjectVolatile = unsafeClass.getMethod("putObjectVolatile", Object.class, long.class, Object.class);

        ParticleMaterial additive = (ParticleMaterial) allocateInstance.invoke(unsafe, ParticleMaterial.class);
        ParticleMaterial[] oldValues = ParticleMaterial.values();

        /* 新常量与现有常量不同名不同序：name/ordinal 若不写，name() 会返回 null，
         * 任何一处用它做键或拼字符串的地方都会 NPE。 */
        putObject.invoke(unsafe, additive, (Long) objectFieldOffset.invoke(unsafe, Enum.class.getDeclaredField("name")), "ADDITIVE");
        putInt.invoke(unsafe, additive, (Long) objectFieldOffset.invoke(unsafe, Enum.class.getDeclaredField("ordinal")), oldValues.length);
        putObject.invoke(unsafe, additive, (Long) objectFieldOffset.invoke(unsafe, ParticleMaterial.class.getDeclaredField("id")), ADDITIVE_MATERIAL_ID);

        ParticleMaterial[] newValues = Arrays.copyOf(oldValues, oldValues.length + 1);

        newValues[oldValues.length] = additive;

        Field valuesField = findValuesField();

        putObjectVolatile.invoke(unsafe, staticFieldBase.invoke(unsafe, valuesField), (Long) staticFieldOffset.invoke(unsafe, valuesField), newValues);

        return additive;
    }

    /** javac 生成的常量数组字段叫 {@code $VALUES}，但名字随映射/混淆会变，按类型认。 */
    private static Field findValuesField() throws NoSuchFieldException
    {
        for (Field field : ParticleMaterial.class.getDeclaredFields())
        {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == ParticleMaterial[].class)
            {
                return field;
            }
        }

        throw new NoSuchFieldException("找不到 ParticleMaterial 的 $VALUES 字段");
    }
}
