package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft density graph 到 OpenCL 节点表的编译器。
 */
public final class DensityGraphCompiler {
    private final Map<DensityFunction, Integer> seen = new IdentityHashMap<>();
    private final List<DensityGraphNode> nodes = new ArrayList<>();
    private final List<Double> constants = new ArrayList<>();
    private final NoiseTableExtractor noiseTables = new NoiseTableExtractor();
    private final List<OpenCLSpline> splines = new ArrayList<>();

    private DensityGraphCompiler() {}

    public static DensityGraphCompileResult compile(DensityFunction root) {
        if (root == null) {
            return DensityGraphCompileResult.unsupported("density root is null");
        }
        DensityGraphCompiler compiler = new DensityGraphCompiler();
        try {
            int rootNode = compiler.compileNode(root);
            return DensityGraphCompileResult.supported(new DensityGraphProgram(
                    rootNode,
                    compiler.nodes,
                    compiler.constants,
                    compiler.noiseTables.toTables(),
                    compiler.splines));
        } catch (UnsupportedDensityGraphException e) {
            return DensityGraphCompileResult.unsupported(e.getMessage());
        } catch (Throwable t) {
            return DensityGraphCompileResult.unsupported("density graph compile failed: " + t.getClass().getSimpleName());
        }
    }

    private int compileNode(DensityFunction function) {
        Integer cached = seen.get(function);
        if (cached != null) {
            return cached;
        }

        int index = compileUncached(function);
        seen.put(function, index);
        return index;
    }

    private int compileUncached(DensityFunction function) {
        String typeName = function.getClass().getSimpleName();
        return switch (typeName) {
            case "Constant" -> constant(function);
            case "BlendAlpha" -> constant(1.0D);
            case "BlendOffset", "BeardifierMarker" -> constant(0.0D);
            case "BlendDensity" -> compileNode(input(function));
            case "Ap2", "MulOrAdd" -> twoArgument(function);
            case "Clamp" -> clamp(function);
            case "Mapped" -> mapped(function);
            case "YClampedGradient" -> yClampedGradient(function);
            case "RangeChoice" -> rangeChoice(function);
            case "Marker" -> marker(function);
            case "HolderHolder" -> holder(function);
            case "Noise" -> noise(function);
            case "ShiftedNoise" -> shiftedNoise(function);
            case "ShiftA" -> shift(function, DensityGraphNodeType.SHIFT_A);
            case "ShiftB" -> shift(function, DensityGraphNodeType.SHIFT_B);
            case "Shift" -> shift(function, DensityGraphNodeType.SHIFT);
            case "Spline" -> spline(function);
            default -> unsupported("unsupported density node: " + function.getClass().getName());
        };
    }

    private int constant(DensityFunction function) {
        double value = DensityGraphReflection.readDouble(function, "value", Double.NaN);
        if (Double.isNaN(value)) {
            throw new UnsupportedDensityGraphException("constant node missing value");
        }
        return constant(value);
    }

    private int constant(double value) {
        constants.add(value);
        return add(new DensityGraphNode(DensityGraphNodeType.CONSTANT, -1, -1, constants.size() - 1, -1, value, 0.0D, 0.0D, 0.0D));
    }

    private int twoArgument(DensityFunction function) {
        Object type = DensityGraphReflection.read(function, "type");
        DensityFunction leftFunction = (DensityFunction) DensityGraphReflection.read(function, "argument1");
        DensityFunction rightFunction = (DensityFunction) DensityGraphReflection.read(function, "argument2");
        Integer constantLeft = null;
        if (type == null) {
            type = DensityGraphReflection.read(function, "specificType");
            Double argument = readBoxedDouble(function, "argument");
            rightFunction = (DensityFunction) DensityGraphReflection.read(function, "input");
            if (argument != null) {
                constantLeft = constant(argument);
            }
        }
        if ((leftFunction == null && constantLeft == null) || rightFunction == null) {
            throw new UnsupportedDensityGraphException("two-argument node missing input");
        }
        int left = constantLeft != null ? constantLeft : compileNode(leftFunction);
        int right = compileNode(rightFunction);
        String op = String.valueOf(type);
        DensityGraphNodeType nodeType = switch (op) {
            case "ADD" -> DensityGraphNodeType.ADD;
            case "MUL" -> DensityGraphNodeType.MUL;
            case "MIN" -> DensityGraphNodeType.MIN;
            case "MAX" -> DensityGraphNodeType.MAX;
            default -> throw new UnsupportedDensityGraphException("unsupported two-argument op: " + op);
        };
        return add(new DensityGraphNode(nodeType, left, right, -1, -1, 0.0D, 0.0D, 0.0D, 0.0D));
    }

    private int clamp(DensityFunction function) {
        DensityFunction input = input(function);
        int child = compileNode(input);
        double min = function.minValue();
        double max = function.maxValue();
        return add(new DensityGraphNode(DensityGraphNodeType.CLAMP, child, -1, -1, -1, min, max, 0.0D, 0.0D));
    }

    private int mapped(DensityFunction function) {
        DensityFunction input = input(function);
        int child = compileNode(input);
        Object type = DensityGraphReflection.read(function, "type");
        DensityGraphNodeType nodeType = switch (String.valueOf(type)) {
            case "ABS" -> DensityGraphNodeType.ABS;
            case "SQUARE" -> DensityGraphNodeType.SQUARE;
            case "CUBE" -> DensityGraphNodeType.CUBE;
            case "HALF_NEGATIVE" -> DensityGraphNodeType.HALF_NEGATIVE;
            case "QUARTER_NEGATIVE" -> DensityGraphNodeType.QUARTER_NEGATIVE;
            case "SQUEEZE" -> DensityGraphNodeType.SQUEEZE;
            default -> throw new UnsupportedDensityGraphException("unsupported mapped op: " + type);
        };
        return add(new DensityGraphNode(nodeType, child, -1, -1, -1, 0.0D, 0.0D, 0.0D, 0.0D));
    }

    private int yClampedGradient(DensityFunction function) {
        int fromY = DensityGraphReflection.readInt(function, "fromY", Integer.MIN_VALUE);
        int toY = DensityGraphReflection.readInt(function, "toY", Integer.MIN_VALUE);
        double fromValue = DensityGraphReflection.readDouble(function, "fromValue", Double.NaN);
        double toValue = DensityGraphReflection.readDouble(function, "toValue", Double.NaN);
        if (fromY == Integer.MIN_VALUE || toY == Integer.MIN_VALUE || Double.isNaN(fromValue) || Double.isNaN(toValue)) {
            throw new UnsupportedDensityGraphException("y_clamped_gradient node missing values");
        }
        return add(new DensityGraphNode(DensityGraphNodeType.Y_CLAMPED_GRADIENT, -1, -1, fromY, toY, fromValue, toValue, 0.0D, 0.0D));
    }

    private int rangeChoice(DensityFunction function) {
        DensityFunction input = (DensityFunction) DensityGraphReflection.read(function, "input");
        DensityFunction whenInRange = (DensityFunction) DensityGraphReflection.read(function, "whenInRange");
        DensityFunction whenOutOfRange = (DensityFunction) DensityGraphReflection.read(function, "whenOutOfRange");
        if (input == null || whenInRange == null || whenOutOfRange == null) {
            throw new UnsupportedDensityGraphException("range_choice node missing input");
        }
        int condition = compileNode(input);
        int inRange = compileNode(whenInRange);
        int outOfRange = compileNode(whenOutOfRange);
        double min = DensityGraphReflection.readDouble(function, "minInclusive", Double.NaN);
        double max = DensityGraphReflection.readDouble(function, "maxExclusive", Double.NaN);
        if (Double.isNaN(min) || Double.isNaN(max)) {
            throw new UnsupportedDensityGraphException("range_choice node missing bounds");
        }
        return add(new DensityGraphNode(DensityGraphNodeType.RANGE_CHOICE, condition, inRange, outOfRange, -1, min, max, 0.0D, 0.0D));
    }

    private int marker(DensityFunction function) {
        DensityFunction wrapped = (DensityFunction) DensityGraphReflection.read(function, "wrapped");
        if (wrapped == null) {
            throw new UnsupportedDensityGraphException("marker node missing wrapped function");
        }
        return compileNode(wrapped);
    }

    private int holder(DensityFunction function) {
        Object holder = DensityGraphReflection.read(function, "function");
        Object value = holder == null ? null : invokeValue(holder);
        if (!(value instanceof DensityFunction nested)) {
            throw new UnsupportedDensityGraphException("holder node is not bound");
        }
        return compileNode(nested);
    }

    private int noise(DensityFunction function) {
        Object holder = DensityGraphReflection.read(function, "noise");
        int noiseIndex = normalNoiseIndex(holder);
        double xzScale = DensityGraphReflection.readDouble(function, "xzScale", Double.NaN);
        double yScale = DensityGraphReflection.readDouble(function, "yScale", Double.NaN);
        if (Double.isNaN(xzScale) || Double.isNaN(yScale)) {
            throw new UnsupportedDensityGraphException("noise node missing scale");
        }
        return add(new DensityGraphNode(DensityGraphNodeType.NOISE, -1, -1, noiseIndex, -1, xzScale, yScale, 0.0D, 0.0D));
    }

    private int shiftedNoise(DensityFunction function) {
        DensityFunction shiftX = (DensityFunction) DensityGraphReflection.read(function, "shiftX");
        DensityFunction shiftY = (DensityFunction) DensityGraphReflection.read(function, "shiftY");
        DensityFunction shiftZ = (DensityFunction) DensityGraphReflection.read(function, "shiftZ");
        Object holder = DensityGraphReflection.read(function, "noise");
        if (shiftX == null || shiftY == null || shiftZ == null) {
            throw new UnsupportedDensityGraphException("shifted_noise node missing shifts");
        }
        int x = compileNode(shiftX);
        int y = compileNode(shiftY);
        int z = compileNode(shiftZ);
        int noiseIndex = normalNoiseIndex(holder);
        double xzScale = DensityGraphReflection.readDouble(function, "xzScale", Double.NaN);
        double yScale = DensityGraphReflection.readDouble(function, "yScale", Double.NaN);
        if (Double.isNaN(xzScale) || Double.isNaN(yScale)) {
            throw new UnsupportedDensityGraphException("shifted_noise node missing scale");
        }
        return add(new DensityGraphNode(DensityGraphNodeType.SHIFTED_NOISE, x, y, z, noiseIndex, xzScale, yScale, 0.0D, 0.0D));
    }

    private int shift(DensityFunction function, DensityGraphNodeType nodeType) {
        Object holder = DensityGraphReflection.read(function, "offsetNoise");
        int noiseIndex = normalNoiseIndex(holder);
        return add(new DensityGraphNode(nodeType, -1, -1, noiseIndex, -1, 0.25D, 4.0D, 0.0D, 0.0D));
    }

    private int spline(DensityFunction function) {
        Object spline = DensityGraphReflection.read(function, "spline");
        return compileSplineValueNode(spline);
    }

    private int compileSplineValueNode(Object spline) {
        if (spline == null) {
            throw new UnsupportedDensityGraphException("spline node missing spline");
        }
        String typeName = spline.getClass().getSimpleName();
        if ("Constant".equals(typeName)) {
            double value = DensityGraphReflection.readDouble(spline, "value", Double.NaN);
            if (Double.isNaN(value)) {
                throw new UnsupportedDensityGraphException("constant spline missing value");
            }
            return constant(value);
        }
        if (!"Multipoint".equals(typeName)) {
            throw new UnsupportedDensityGraphException("unsupported spline type: " + spline.getClass().getName());
        }
        int splineIndex = compileSplineTable(spline);
        return add(new DensityGraphNode(DensityGraphNodeType.SPLINE, -1, -1, splineIndex, -1, 0.0D, 0.0D, 0.0D, 0.0D));
    }

    private int compileSplineTable(Object spline) {
        if (spline == null) {
            throw new UnsupportedDensityGraphException("spline node missing spline");
        }

        Object coordinate = DensityGraphReflection.read(spline, "coordinate");
        Object coordinateHolder = DensityGraphReflection.read(coordinate, "function");
        Object coordinateFunction = coordinateHolder == null ? null : invokeValue(coordinateHolder);
        if (!(coordinateFunction instanceof DensityFunction densityFunction)) {
            throw new UnsupportedDensityGraphException("spline coordinate is not a density function");
        }
        int coordinateNode = compileNode(densityFunction);

        Object locationsObject = DensityGraphReflection.read(spline, "locations");
        Object valuesObject = DensityGraphReflection.read(spline, "values");
        Object derivativesObject = DensityGraphReflection.read(spline, "derivatives");
        if (!(locationsObject instanceof float[] locations) || !(valuesObject instanceof List<?> values) || !(derivativesObject instanceof float[] derivatives)) {
            throw new UnsupportedDensityGraphException("spline table missing arrays");
        }
        if (locations.length != values.size() || locations.length != derivatives.length) {
            throw new UnsupportedDensityGraphException("spline table size mismatch");
        }

        List<Double> locationList = new ArrayList<>(locations.length);
        List<Integer> valueNodes = new ArrayList<>(locations.length);
        List<Double> derivativeList = new ArrayList<>(locations.length);
        for (int i = 0; i < locations.length; i++) {
            locationList.add((double) locations[i]);
            valueNodes.add(compileSplineValueNode(values.get(i)));
            derivativeList.add((double) derivatives[i]);
        }

        int index = splines.size();
        splines.add(new OpenCLSpline(coordinateNode, locationList, valueNodes, derivativeList));
        return index;
    }

    private int normalNoiseIndex(Object noiseHolder) {
        Object noise = DensityGraphReflection.read(noiseHolder, "noise");
        if (!(noise instanceof net.minecraft.world.level.levelgen.synth.NormalNoise normalNoise)) {
            throw new UnsupportedDensityGraphException("noise holder has no NormalNoise instance");
        }
        try {
            return noiseTables.normalNoiseIndex(normalNoise);
        } catch (Throwable t) {
            throw new UnsupportedDensityGraphException("noise table extraction failed: " + t.getClass().getSimpleName());
        }
    }

    private DensityFunction input(DensityFunction function) {
        DensityFunction input = (DensityFunction) DensityGraphReflection.read(function, "input");
        if (input == null) {
            throw new UnsupportedDensityGraphException(function.getClass().getSimpleName() + " node missing input");
        }
        return input;
    }

    private int add(DensityGraphNode node) {
        nodes.add(node);
        return nodes.size() - 1;
    }

    private static Double readBoxedDouble(Object owner, String name) {
        Object value = DensityGraphReflection.read(owner, name);
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static int unsupported(String reason) {
        throw new UnsupportedDensityGraphException(reason);
    }

    private static Object invokeValue(Object holder) {
        try {
            return holder.getClass().getMethod("value").invoke(holder);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class UnsupportedDensityGraphException extends RuntimeException {
        private UnsupportedDensityGraphException(String message) {
            super(message);
        }
    }
}