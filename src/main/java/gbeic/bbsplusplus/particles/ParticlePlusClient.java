package gbeic.bbsplusplus.particles;

import gbeic.bbsplusplus.BBSFSloveCML;
import gbeic.bbsplusplus.particles.components.ParticleComponentCollisionAppearance;
import gbeic.bbsplusplus.particles.components.ParticleComponentCollisionTinting;
import gbeic.bbsplusplus.particles.components.ParticleComponentParticleMorph;
import mchorse.bbs_mod.particles.ParticleMaterial;
import mchorse.bbs_mod.particles.ParticleScheme;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

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
                BBSFSloveCML.LOGGER.info("[Particle+] Additive particle material registered");
            }
            catch (Throwable throwable)
            {
                BBSFSloveCML.LOGGER.error("[Particle+] Failed to register additive particle material", throwable);
            }
        }

        ParticleScheme.PARSER.components.put(MORPH_COMPONENT, ParticleComponentParticleMorph.class);
        ParticleScheme.PARSER.components.put(COLLISION_APPEARANCE_COMPONENT, ParticleComponentCollisionAppearance.class);
        ParticleScheme.PARSER.components.put(COLLISION_TINTING_COMPONENT, ParticleComponentCollisionTinting.class);
        initialized = true;
    }

    public static boolean hasAdditiveMaterial()
    {
        return additiveMaterial != null;
    }

    public static boolean isAdditive(ParticleMaterial material)
    {
        return material != null && ADDITIVE_MATERIAL_ID.equals(material.id);
    }

    private static ParticleMaterial injectAdditiveMaterial() throws Exception
    {
        Unsafe unsafe = getUnsafe();
        ParticleMaterial additive = (ParticleMaterial) unsafe.allocateInstance(ParticleMaterial.class);

        Field nameField = Enum.class.getDeclaredField("name");
        Field ordinalField = Enum.class.getDeclaredField("ordinal");
        Field idField = ParticleMaterial.class.getDeclaredField("id");

        unsafe.putObject(additive, unsafe.objectFieldOffset(nameField), "ADDITIVE");
        unsafe.putInt(additive, unsafe.objectFieldOffset(ordinalField), ParticleMaterial.values().length);
        unsafe.putObject(additive, unsafe.objectFieldOffset(idField), ADDITIVE_MATERIAL_ID);

        Field valuesField = findValuesField();
        ParticleMaterial[] oldValues = ParticleMaterial.values();
        ParticleMaterial[] newValues = new ParticleMaterial[oldValues.length + 1];

        System.arraycopy(oldValues, 0, newValues, 0, oldValues.length);
        newValues[oldValues.length] = additive;

        Object base = unsafe.staticFieldBase(valuesField);
        long offset = unsafe.staticFieldOffset(valuesField);

        unsafe.putObjectVolatile(base, offset, newValues);

        return additive;
    }

    private static Field findValuesField() throws NoSuchFieldException
    {
        for (Field field : ParticleMaterial.class.getDeclaredFields())
        {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == ParticleMaterial[].class)
            {
                return field;
            }
        }

        throw new NoSuchFieldException("ParticleMaterial values field");
    }

    private static Unsafe getUnsafe() throws ReflectiveOperationException
    {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");

        field.setAccessible(true);

        return (Unsafe) field.get(null);
    }
}
