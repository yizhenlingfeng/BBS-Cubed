package gbeic.bbsplusplus.client.morph;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一键自动生成 Cube 映射建议。
 *
 * <p>先保留稳定 key 完全相同的配对，再对剩余 Cube 以模型自身包围范围归一化位置，
 * 综合空间位置、相对尺寸、长宽比例和骨骼/名称相似度做全局最小代价分配。骨骼名不再
 * 是硬限制，因此不同命名、不同层级的模型也能按几何位置匹配。</p>
 *
 * <p>两个模型 Cube 数量不同时，数量较多一侧的剩余 Cube 会并入最近的 1:N 或 N:1 组。
 * 自动匹配不会生成 N:N，因为当前几何管线没有定义多源同时分叉到多个目标的过渡语义。</p>
 */
public final class MorphAutoMatch
{
    private static final float EPSILON = 1.0E-5F;
    private static final double LOG_SIZE_RANGE = Math.log(8D);
    private static final double POSITION_WEIGHT = 0.60D;
    private static final double SIZE_WEIGHT = 0.20D;
    private static final double SHAPE_WEIGHT = 0.12D;
    private static final double SEMANTIC_WEIGHT = 0.08D;

    private MorphAutoMatch()
    {
    }

    /**
     * Best-effort automatic mapping. Every Cube on the smaller side gets one match; any surplus Cube on the
     * larger side is attached to its nearest counterpart, producing only supported 1:1, 1:N, or N:1 groups.
     */
    public static List<MappingGroup> suggest(ModelMorphSkeleton source, ModelMorphSkeleton target)
    {
        if (source == null || target == null || source.isEmpty() || target.isEmpty())
        {
            return List.of();
        }

        List<ModelMorphSkeleton.CubeSpec> sourceCubes = new ArrayList<>(source.cubesByKey().values());
        List<ModelMorphSkeleton.CubeSpec> targetCubes = new ArrayList<>(target.cubesByKey().values());
        Map<String, ModelMorphSkeleton.CubeSpec> targetsByKey = target.cubesByKey();
        Set<String> usedSources = new HashSet<>();
        Set<String> usedTargets = new HashSet<>();
        Map<String, List<ModelMorphSkeleton.CubeSpec>> targetsBySource = new LinkedHashMap<>();
        Map<String, List<ModelMorphSkeleton.CubeSpec>> sourcesByTarget = new LinkedHashMap<>();

        /* Identical full keys are stronger evidence than a geometric estimate. */
        for (ModelMorphSkeleton.CubeSpec sourceCube : sourceCubes)
        {
            ModelMorphSkeleton.CubeSpec targetCube = targetsByKey.get(sourceCube.key());

            if (targetCube != null)
            {
                addPair(sourceCube, targetCube, targetsBySource, sourcesByTarget);
                usedSources.add(sourceCube.key());
                usedTargets.add(targetCube.key());
            }
        }

        List<ModelMorphSkeleton.CubeSpec> remainingSources = sourceCubes.stream()
            .filter(cube -> !usedSources.contains(cube.key())).toList();
        List<ModelMorphSkeleton.CubeSpec> remainingTargets = targetCubes.stream()
            .filter(cube -> !usedTargets.contains(cube.key())).toList();
        Map<String, Feature> sourceFeatures = features(sourceCubes);
        Map<String, Feature> targetFeatures = features(targetCubes);

        for (Pair pair : minimumCostPairs(remainingSources, remainingTargets, sourceFeatures, targetFeatures))
        {
            addPair(pair.source(), pair.target(), targetsBySource, sourcesByTarget);
            usedSources.add(pair.source().key());
            usedTargets.add(pair.target().key());
        }

        if (sourceCubes.size() <= targetCubes.size())
        {
            /* Every source already has a 1:1 anchor; attach extra target pieces to the closest source anchor. */
            for (ModelMorphSkeleton.CubeSpec extra : targetCubes)
            {
                if (usedTargets.contains(extra.key()))
                {
                    continue;
                }

                ModelMorphSkeleton.CubeSpec nearest = nearestSource(extra, targetsBySource.keySet(), source.cubesByKey(),
                    sourceFeatures, targetFeatures);

                if (nearest != null)
                {
                    targetsBySource.get(nearest.key()).add(extra);
                    usedTargets.add(extra.key());
                }
            }

            List<MappingGroup> groups = new ArrayList<>();

            for (ModelMorphSkeleton.CubeSpec sourceCube : sourceCubes)
            {
                List<ModelMorphSkeleton.CubeSpec> mappedTargets = targetsBySource.get(sourceCube.key());

                if (mappedTargets != null && !mappedTargets.isEmpty())
                {
                    groups.add(group(List.of(sourceCube), mappedTargets));
                }
            }

            return groups;
        }

        /* Every target has a 1:1 anchor; attach extra source pieces to the closest target anchor. */
        for (ModelMorphSkeleton.CubeSpec extra : sourceCubes)
        {
            if (usedSources.contains(extra.key()))
            {
                continue;
            }

            ModelMorphSkeleton.CubeSpec nearest = nearestTarget(extra, sourcesByTarget.keySet(), target.cubesByKey(),
                sourceFeatures, targetFeatures);

            if (nearest != null)
            {
                sourcesByTarget.get(nearest.key()).add(extra);
                usedSources.add(extra.key());
            }
        }

        List<MappingGroup> groups = new ArrayList<>();

        for (ModelMorphSkeleton.CubeSpec targetCube : targetCubes)
        {
            List<ModelMorphSkeleton.CubeSpec> mappedSources = sourcesByTarget.get(targetCube.key());

            if (mappedSources != null && !mappedSources.isEmpty())
            {
                groups.add(group(mappedSources, List.of(targetCube)));
            }
        }

        return groups;
    }

    private static void addPair(ModelMorphSkeleton.CubeSpec source, ModelMorphSkeleton.CubeSpec target,
                                Map<String, List<ModelMorphSkeleton.CubeSpec>> targetsBySource,
                                Map<String, List<ModelMorphSkeleton.CubeSpec>> sourcesByTarget)
    {
        targetsBySource.computeIfAbsent(source.key(), key -> new ArrayList<>()).add(target);
        sourcesByTarget.computeIfAbsent(target.key(), key -> new ArrayList<>()).add(source);
    }

    private static MappingGroup group(List<ModelMorphSkeleton.CubeSpec> sources,
                                      List<ModelMorphSkeleton.CubeSpec> targets)
    {
        MappingGroup group = new MappingGroup();
        List<MappingGroup.CubeRef> sourceRefs = new ArrayList<>(sources.size());
        List<MappingGroup.CubeRef> targetRefs = new ArrayList<>(targets.size());

        for (ModelMorphSkeleton.CubeSpec cube : sources)
        {
            sourceRefs.add(new MappingGroup.CubeRef(cube.key(), cube.signature()));
        }

        for (ModelMorphSkeleton.CubeSpec cube : targets)
        {
            targetRefs.add(new MappingGroup.CubeRef(cube.key(), cube.signature()));
        }

        group.setSources(sourceRefs);
        group.setTargets(targetRefs);

        return group;
    }

    private static ModelMorphSkeleton.CubeSpec nearestSource(
        ModelMorphSkeleton.CubeSpec target,
        Set<String> sourceKeys,
        Map<String, ModelMorphSkeleton.CubeSpec> sources,
        Map<String, Feature> sourceFeatures,
        Map<String, Feature> targetFeatures)
    {
        ModelMorphSkeleton.CubeSpec best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        Feature targetFeature = targetFeatures.get(target.key());

        for (String sourceKey : sourceKeys)
        {
            ModelMorphSkeleton.CubeSpec candidate = sources.get(sourceKey);
            double cost = cost(sourceFeatures.get(sourceKey), targetFeature);

            if (cost < bestCost)
            {
                bestCost = cost;
                best = candidate;
            }
        }

        return best;
    }

    private static ModelMorphSkeleton.CubeSpec nearestTarget(
        ModelMorphSkeleton.CubeSpec source,
        Set<String> targetKeys,
        Map<String, ModelMorphSkeleton.CubeSpec> targets,
        Map<String, Feature> sourceFeatures,
        Map<String, Feature> targetFeatures)
    {
        ModelMorphSkeleton.CubeSpec best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        Feature sourceFeature = sourceFeatures.get(source.key());

        for (String targetKey : targetKeys)
        {
            ModelMorphSkeleton.CubeSpec candidate = targets.get(targetKey);
            double cost = cost(sourceFeature, targetFeatures.get(targetKey));

            if (cost < bestCost)
            {
                bestCost = cost;
                best = candidate;
            }
        }

        return best;
    }

    private static List<Pair> minimumCostPairs(List<ModelMorphSkeleton.CubeSpec> sources,
                                               List<ModelMorphSkeleton.CubeSpec> targets,
                                               Map<String, Feature> sourceFeatures,
                                               Map<String, Feature> targetFeatures)
    {
        if (sources.isEmpty() || targets.isEmpty())
        {
            return List.of();
        }

        double[][] costs;
        int[] assignment;
        List<Pair> pairs = new ArrayList<>(Math.min(sources.size(), targets.size()));

        if (sources.size() <= targets.size())
        {
            costs = new double[sources.size()][targets.size()];

            for (int row = 0; row < sources.size(); row++)
            {
                for (int column = 0; column < targets.size(); column++)
                {
                    costs[row][column] = cost(sourceFeatures.get(sources.get(row).key()),
                        targetFeatures.get(targets.get(column).key()));
                }
            }

            assignment = minimumCostAssignment(costs);

            for (int row = 0; row < assignment.length; row++)
            {
                pairs.add(new Pair(sources.get(row), targets.get(assignment[row])));
            }
        }
        else
        {
            costs = new double[targets.size()][sources.size()];

            for (int row = 0; row < targets.size(); row++)
            {
                for (int column = 0; column < sources.size(); column++)
                {
                    costs[row][column] = cost(sourceFeatures.get(sources.get(column).key()),
                        targetFeatures.get(targets.get(row).key()));
                }
            }

            assignment = minimumCostAssignment(costs);

            for (int row = 0; row < assignment.length; row++)
            {
                pairs.add(new Pair(sources.get(assignment[row]), targets.get(row)));
            }
        }

        return pairs;
    }

    /** Hungarian assignment for a rectangular matrix with rows <= columns. */
    static int[] minimumCostAssignment(double[][] costs)
    {
        int rows = costs.length;

        if (rows == 0)
        {
            return new int[0];
        }

        int columns = costs[0].length;

        if (columns < rows)
        {
            throw new IllegalArgumentException("assignment matrix must have at least as many columns as rows");
        }

        double[] rowPotential = new double[rows + 1];
        double[] columnPotential = new double[columns + 1];
        int[] matchedRow = new int[columns + 1];
        int[] previousColumn = new int[columns + 1];

        for (int row = 1; row <= rows; row++)
        {
            matchedRow[0] = row;
            double[] minimum = new double[columns + 1];
            Arrays.fill(minimum, Double.POSITIVE_INFINITY);
            boolean[] used = new boolean[columns + 1];
            int currentColumn = 0;

            do
            {
                used[currentColumn] = true;
                int currentRow = matchedRow[currentColumn];
                double delta = Double.POSITIVE_INFINITY;
                int nextColumn = 0;

                for (int column = 1; column <= columns; column++)
                {
                    if (used[column])
                    {
                        continue;
                    }

                    double reducedCost = costs[currentRow - 1][column - 1]
                        - rowPotential[currentRow] - columnPotential[column];

                    if (reducedCost < minimum[column])
                    {
                        minimum[column] = reducedCost;
                        previousColumn[column] = currentColumn;
                    }

                    if (minimum[column] < delta)
                    {
                        delta = minimum[column];
                        nextColumn = column;
                    }
                }

                for (int column = 0; column <= columns; column++)
                {
                    if (used[column])
                    {
                        rowPotential[matchedRow[column]] += delta;
                        columnPotential[column] -= delta;
                    }
                    else
                    {
                        minimum[column] -= delta;
                    }
                }

                currentColumn = nextColumn;
            }
            while (matchedRow[currentColumn] != 0);

            do
            {
                int nextColumn = previousColumn[currentColumn];
                matchedRow[currentColumn] = matchedRow[nextColumn];
                currentColumn = nextColumn;
            }
            while (currentColumn != 0);
        }

        int[] assignment = new int[rows];

        for (int column = 1; column <= columns; column++)
        {
            if (matchedRow[column] != 0)
            {
                assignment[matchedRow[column] - 1] = column - 1;
            }
        }

        return assignment;
    }

    private static double cost(Feature source, Feature target)
    {
        double dx = source.x() - target.x();
        double dy = source.y() - target.y();
        double dz = source.z() - target.z();
        double position = Math.min(1D, Math.sqrt(dx * dx + dy * dy + dz * dz));
        double sizeRatio = (source.relativeSize() + EPSILON) / (target.relativeSize() + EPSILON);
        double relativeSize = Math.min(1D, Math.abs(Math.log(sizeRatio)) / LOG_SIZE_RANGE);
        double shape = (Math.abs(source.shapeX() - target.shapeX())
            + Math.abs(source.shapeY() - target.shapeY())
            + Math.abs(source.shapeZ() - target.shapeZ())) * 0.5D;
        double semantic = (1D - similarity(source.bone(), target.bone())) * 0.65D
            + (1D - similarity(source.name(), target.name())) * 0.35D;

        return POSITION_WEIGHT * position + SIZE_WEIGHT * relativeSize
            + SHAPE_WEIGHT * Math.min(1D, shape) + SEMANTIC_WEIGHT * semantic;
    }

    private static Map<String, Feature> features(List<ModelMorphSkeleton.CubeSpec> cubes)
    {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (ModelMorphSkeleton.CubeSpec cube : cubes)
        {
            Vector3f center = cube.worldCenter();
            float radius = Math.max(cube.size().length() * ModelMorphAdapter.MODEL_UNIT * 0.5F,
                EPSILON) + Math.abs(cube.inflate()) * ModelMorphAdapter.MODEL_UNIT;
            minX = Math.min(minX, center.x - radius);
            minY = Math.min(minY, center.y - radius);
            minZ = Math.min(minZ, center.z - radius);
            maxX = Math.max(maxX, center.x + radius);
            maxY = Math.max(maxY, center.y + radius);
            maxZ = Math.max(maxZ, center.z + radius);
        }

        float centerX = (minX + maxX) * 0.5F;
        float centerY = (minY + maxY) * 0.5F;
        float centerZ = (minZ + maxZ) * 0.5F;
        float diagonal = Math.max(new Vector3f(maxX - minX, maxY - minY, maxZ - minZ).length(), EPSILON);
        Map<String, Feature> result = new HashMap<>();

        for (ModelMorphSkeleton.CubeSpec cube : cubes)
        {
            Vector3f world = cube.worldCenter();
            Vector3f dimensions = new Vector3f(
                Math.abs(cube.size().x), Math.abs(cube.size().y), Math.abs(cube.size().z));
            float size = dimensions.length() * ModelMorphAdapter.MODEL_UNIT;
            float dimensionSum = Math.max(dimensions.x + dimensions.y + dimensions.z, EPSILON);
            float[] sorted = {dimensions.x / dimensionSum, dimensions.y / dimensionSum, dimensions.z / dimensionSum};

            Arrays.sort(sorted);
            result.put(cube.key(), new Feature(
                (world.x - centerX) / diagonal,
                (world.y - centerY) / diagonal,
                (world.z - centerZ) / diagonal,
                size / diagonal,
                sorted[0], sorted[1], sorted[2],
                leaf(cube.groupPath()), cube.name().toLowerCase(java.util.Locale.ROOT)));
        }

        return result;
    }

    private static String leaf(String path)
    {
        int separator = path.lastIndexOf('/');

        return path.substring(separator + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private static double similarity(String first, String second)
    {
        if (first.isEmpty() || second.isEmpty())
        {
            return 0.5D;
        }

        if (first.equals(second))
        {
            return 1D;
        }

        int longest = Math.max(first.length(), second.length());
        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];

        for (int column = 0; column <= second.length(); column++)
        {
            previous[column] = column;
        }

        for (int row = 1; row <= first.length(); row++)
        {
            current[0] = row;

            for (int column = 1; column <= second.length(); column++)
            {
                int substitution = previous[column - 1] + (first.charAt(row - 1) == second.charAt(column - 1) ? 0 : 1);
                current[column] = Math.min(Math.min(previous[column] + 1, current[column - 1] + 1), substitution);
            }

            int[] swap = previous;
            previous = current;
            current = swap;
        }

        return 1D - previous[second.length()] / (double) longest;
    }

    private record Pair(ModelMorphSkeleton.CubeSpec source, ModelMorphSkeleton.CubeSpec target)
    {
    }

    private record Feature(float x, float y, float z, float relativeSize,
                           float shapeX, float shapeY, float shapeZ, String bone, String name)
    {
    }
}
