package gbeic.bbsplusplus.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 特征测试：固定流体模拟网格的边界规则与单步传播，避免整理时静默改动数值行为。
 */
class FluidSimulationTest
{
    @Test
    void gridDimensionsAndInitialStateAreFlat()
    {
        FluidSimulation sim = new FluidSimulation(8, 8);

        assertEquals(8, sim.getWidth());
        assertEquals(8, sim.getHeight());

        for (int y = 0; y < 8; y++)
        {
            for (int x = 0; x < 8; x++)
            {
                assertEquals(0F, sim.getHeight(x, y), 1.0E-6F);
            }
        }
    }

    @Test
    void outOfBoundsReadsReturnZero()
    {
        FluidSimulation sim = new FluidSimulation(8, 8);

        assertEquals(0F, sim.getHeight(-1, 0), 1.0E-6F);
        assertEquals(0F, sim.getHeight(0, -1), 1.0E-6F);
        assertEquals(0F, sim.getHeight(8, 0), 1.0E-6F);
        assertEquals(0F, sim.getHeight(0, 8), 1.0E-6F);
    }

    @Test
    void forceOnBorderCellIsIgnored()
    {
        FluidSimulation sim = new FluidSimulation(8, 8);

        sim.addForce(0, 4, 5F);
        sim.addForce(7, 4, 5F);
        sim.addForce(4, 0, 5F);
        sim.addForce(4, 7, 5F);

        assertEquals(0F, sim.getHeight(0, 4), 1.0E-6F);
        assertEquals(0F, sim.getHeight(7, 4), 1.0E-6F);
        assertEquals(0F, sim.getHeight(4, 0), 1.0E-6F);
        assertEquals(0F, sim.getHeight(4, 7), 1.0E-6F);
    }

    @Test
    void forceOutsideGridIsIgnored()
    {
        FluidSimulation sim = new FluidSimulation(8, 8);

        sim.addForce(-1, 4, 5F);
        sim.addForce(8, 4, 5F);
        sim.addForce(4, 100, 5F);

        assertEquals(0F, sim.getHeight(4, 4), 1.0E-6F);
    }

    @Test
    void singleUpdatePropagatesToNeighborsAndClearsSource()
    {
        FluidSimulation sim = new FluidSimulation(8, 8);

        sim.addForce(4, 4, 1F);
        assertEquals(1F, sim.getHeight(4, 4), 1.0E-6F);

        sim.update();

        /* 邻居拿到 (1)/2 * viscosity，源点自身被清成 0 */
        assertEquals(0F, sim.getHeight(4, 4), 1.0E-6F);
        assertEquals(0.5F * 0.96F, sim.getHeight(3, 4), 1.0E-6F);
        assertEquals(0.5F * 0.96F, sim.getHeight(5, 4), 1.0E-6F);
        assertEquals(0.5F * 0.96F, sim.getHeight(4, 3), 1.0E-6F);
        assertEquals(0.5F * 0.96F, sim.getHeight(4, 5), 1.0E-6F);
    }

    @Test
    void viscosityScalesPropagatedAmplitude()
    {
        FluidSimulation sim = new FluidSimulation(8, 8);

        sim.viscosity = 0.5F;
        sim.addForce(4, 4, 2F);
        sim.update();

        assertEquals(0.5F, sim.getHeight(3, 4), 1.0E-6F);
    }
}
