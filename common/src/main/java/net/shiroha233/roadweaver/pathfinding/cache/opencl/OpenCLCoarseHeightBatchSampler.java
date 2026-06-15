package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.pathfinding.cache.CoarseHeightBatchRequest;
import net.shiroha233.roadweaver.pathfinding.cache.CoarseHeightBatchSampler;
import net.shiroha233.roadweaver.pathfinding.cache.CpuCoarseHeightBatchSampler;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.Buffer;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenCL 粗高度批量采样器。
 */
public final class OpenCLCoarseHeightBatchSampler implements CoarseHeightBatchSampler {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final AtomicBoolean ENABLED_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean KERNEL_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean DISABLED_FALLBACK_LOGGED = new AtomicBoolean();

    private final ServerLevel level;
    private final OpenCLRuntime runtime;
    private final DensityGraphProgram program;
    private final CpuCoarseHeightBatchSampler fallback;
    private final int minY;
    private final int maxY;
    private final int cellHeight;

    private OpenCLCoarseHeightBatchSampler(ServerLevel level,
                                           OpenCLRuntime runtime,
                                           DensityGraphProgram program,
                                           CpuCoarseHeightBatchSampler fallback,
                                           NoiseSettings settings) {
        this.level = level;
        this.runtime = runtime;
        this.program = program;
        this.fallback = fallback;
        this.minY = settings.minY();
        this.maxY = settings.minY() + settings.height();
        this.cellHeight = settings.getCellHeight();
    }

    public static CoarseHeightBatchSampler tryCreate(ServerLevel level,
                                                     OpenCLDevicePreference devicePreference) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) {
            return null;
        }
        if (OpenCLWorldSupport.isUnsupported(level.dimension().location())) {
            LOGGER.info("OpenCL 粗采样跳过维度 {}，回退到 CPU: {}",
                    level.dimension().location(),
                    OpenCLWorldSupport.unsupportedReason(level.dimension().location()));
            return null;
        }
        DensityGraphCompileResult graph = compileInitialDensity(level);
        if (!graph.supported()) {
            OpenCLWorldSupport.markUnsupported(level.dimension().location(), graph.unsupportedReason());
            LOGGER.info("OpenCL 粗采样暂不支持维度 {}，回退到 CPU: {}", level.dimension().location(), graph.unsupportedReason());
            return null;
        }
        OpenCLRuntime runtime = OpenCLRuntime.tryCreate(devicePreference);
        if (runtime == null) {
            return null;
        }
        try {
            OpenCLCoarseHeightBatchSampler sampler = new OpenCLCoarseHeightBatchSampler(
                    level,
                    runtime,
                    graph.program(),
                    CpuCoarseHeightBatchSampler.create(level),
                    getNoiseSettings(level));
            if (ENABLED_LOGGED.compareAndSet(false, true)) {
                LOGGER.info("OpenCL 粗采样已启用 preference={} nodes={} normalNoises={} splines={}",
                        runtime.devicePreference(),
                        graph.program().nodes().size(),
                        graph.program().noiseTables().normalNoises().size(),
                        graph.program().splines().size());
            }
            return sampler;
        } catch (Throwable t) {
            OpenCLAvailability.disable("OpenCL 粗采样器创建失败", t);
            return null;
        }
    }

    private static DensityGraphCompileResult compileInitialDensity(ServerLevel level) {
        try {
            var randomState = level.getChunkSource().getGeneratorState().randomState();
            var router = randomState.router();
            return DensityGraphCompiler.compile(router.initialDensityWithoutJaggedness());
        } catch (Throwable t) {
            return DensityGraphCompileResult.unsupported("initial density graph unavailable: " + t.getClass().getSimpleName());
        }
    }

    @Override
    public int[] sampleHeights(CoarseHeightBatchRequest request) {
        if (request == null) {
            return null;
        }
        if (program.isEmpty()) {
            return fallback.sampleHeights(request);
        }
        if (!OpenCLAvailability.isAvailable()) {
            if (DISABLED_FALLBACK_LOGGED.compareAndSet(false, true)) {
                LOGGER.info("OpenCL 粗采样已禁用，当前请求直接使用 CPU: {}", OpenCLAvailability.disabledReason());
            }
            return fallback.sampleHeights(request);
        }
        try {
            int[] heights = sampleHeightsOpenCL(request);
            if (shouldValidateSamples()) {
                int[] expected = fallback.sampleHeights(request);
                if (expected == null) {
                    return null;
                }
                if (!Arrays.equals(expected, heights)) {
                    OpenCLAvailability.disable("OpenCL 粗采样结果校验失败");
                    LOGGER.info("OpenCL 粗采样结果校验失败，回退到 CPU: {}", firstMismatch(expected, heights));
                    return expected;
                }
            }
            if (KERNEL_LOGGED.compareAndSet(false, true)) {
                LOGGER.info("OpenCL 粗采样 kernel 已执行 samples={} nodes={}", request.sampleCount(), program.nodes().size());
            }
            return heights;
        } catch (Throwable t) {
            OpenCLAvailability.disable("OpenCL kernel 执行失败", t);
            return fallback.sampleHeights(request);
        }
    }

    @Override
    public boolean isAccelerated() {
        return OpenCLAvailability.isAvailable();
    }

    @Override
    public void close() {
        fallback.close();
    }

    private int[] sampleHeightsOpenCL(CoarseHeightBatchRequest request) {
        synchronized (runtime.operationLock()) {
            return sampleHeightsOpenCLLocked(request);
        }
    }

    private int[] sampleHeightsOpenCLLocked(CoarseHeightBatchRequest request) {
        KernelPayload payload = KernelPayload.from(program, request, minY, maxY, cellHeight);
        List<Long> memObjects = new ArrayList<>();
        List<Buffer> hostBuffers = new ArrayList<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer err = stack.mallocInt(1);

            IntBuffer params = directInt(payload.params(), hostBuffers);
            IntBuffer nodeInts = directInt(payload.nodeInts(), hostBuffers);
            DoubleBuffer nodeValues = directDouble(payload.nodeValues(), hostBuffers);
            IntBuffer normalInts = directInt(payload.normalInts(), hostBuffers);
            DoubleBuffer normalValues = directDouble(payload.normalValues(), hostBuffers);
            IntBuffer perlinInts = directInt(payload.perlinInts(), hostBuffers);
            DoubleBuffer perlinValues = directDouble(payload.perlinValues(), hostBuffers);
            DoubleBuffer amplitudes = directDouble(payload.amplitudes(), hostBuffers);
            IntBuffer improvedIndices = directInt(payload.improvedIndices(), hostBuffers);
            DoubleBuffer improvedValues = directDouble(payload.improvedValues(), hostBuffers);
            IntBuffer permutations = directInt(payload.permutations(), hostBuffers);
            IntBuffer splineInts = directInt(payload.splineInts(), hostBuffers);
            DoubleBuffer splineLocations = directDouble(payload.splineLocations(), hostBuffers);
            IntBuffer splineValueNodes = directInt(payload.splineValueNodes(), hostBuffers);
            DoubleBuffer splineDerivatives = directDouble(payload.splineDerivatives(), hostBuffers);

            long paramsMem = readOnly(params, err, memObjects);
            long nodeIntsMem = readOnly(nodeInts, err, memObjects);
            long nodeValuesMem = readOnly(nodeValues, err, memObjects);
            long normalIntsMem = readOnly(normalInts, err, memObjects);
            long normalValuesMem = readOnly(normalValues, err, memObjects);
            long perlinIntsMem = readOnly(perlinInts, err, memObjects);
            long perlinValuesMem = readOnly(perlinValues, err, memObjects);
            long amplitudesMem = readOnly(amplitudes, err, memObjects);
            long improvedIndicesMem = readOnly(improvedIndices, err, memObjects);
            long improvedValuesMem = readOnly(improvedValues, err, memObjects);
            long permutationsMem = readOnly(permutations, err, memObjects);
            long splineIntsMem = readOnly(splineInts, err, memObjects);
            long splineLocationsMem = readOnly(splineLocations, err, memObjects);
            long splineValueNodesMem = readOnly(splineValueNodes, err, memObjects);
            long splineDerivativesMem = readOnly(splineDerivatives, err, memObjects);
            long scratchMem = buffer(CL10.CL_MEM_READ_WRITE, payload.scratchBytes(), err, memObjects);
            long heightsMem = buffer(CL10.CL_MEM_WRITE_ONLY, intBytes(request.sampleCount()), err, memObjects);

            int arg = 0;
            setKernelArg(arg++, paramsMem);
            setKernelArg(arg++, nodeIntsMem);
            setKernelArg(arg++, nodeValuesMem);
            setKernelArg(arg++, normalIntsMem);
            setKernelArg(arg++, normalValuesMem);
            setKernelArg(arg++, perlinIntsMem);
            setKernelArg(arg++, perlinValuesMem);
            setKernelArg(arg++, amplitudesMem);
            setKernelArg(arg++, improvedIndicesMem);
            setKernelArg(arg++, improvedValuesMem);
            setKernelArg(arg++, permutationsMem);
            setKernelArg(arg++, splineIntsMem);
            setKernelArg(arg++, splineLocationsMem);
            setKernelArg(arg++, splineValueNodesMem);
            setKernelArg(arg++, splineDerivativesMem);
            setKernelArg(arg++, scratchMem);
            setKernelArg(arg, heightsMem);

            var globalWorkSize = stack.mallocPointer(1);
            globalWorkSize.put(0, request.sampleCount());
            check(CL10.clEnqueueNDRangeKernel(runtime.queue(), runtime.kernel(), 1, (org.lwjgl.PointerBuffer) null, globalWorkSize, (org.lwjgl.PointerBuffer) null, (org.lwjgl.PointerBuffer) null, (org.lwjgl.PointerBuffer) null), "clEnqueueNDRangeKernel");

            IntBuffer heights = MemoryUtil.memAllocInt(request.sampleCount());
            hostBuffers.add(heights);
            check(CL10.clEnqueueReadBuffer(runtime.queue(), heightsMem, true, 0L, heights, (org.lwjgl.PointerBuffer) null, (org.lwjgl.PointerBuffer) null), "clEnqueueReadBuffer");
            int[] result = new int[request.sampleCount()];
            heights.get(result);
            return result;
        } finally {
            for (long memObject : memObjects) {
                if (memObject != 0L) {
                    try {
                        CL10.clReleaseMemObject(memObject);
                    } catch (Throwable ignored) {}
                }
            }
            for (Buffer hostBuffer : hostBuffers) {
                MemoryUtil.memFree(hostBuffer);
            }
        }
    }

    private long readOnly(IntBuffer host, IntBuffer err, List<Long> memObjects) {
        long mem = buffer(CL10.CL_MEM_READ_ONLY, intBytes(host.remaining()), err, memObjects);
        check(CL10.clEnqueueWriteBuffer(runtime.queue(), mem, true, 0L, host, (org.lwjgl.PointerBuffer) null, (org.lwjgl.PointerBuffer) null), "clEnqueueWriteBuffer(int)");
        return mem;
    }

    private long readOnly(DoubleBuffer host, IntBuffer err, List<Long> memObjects) {
        long mem = buffer(CL10.CL_MEM_READ_ONLY, doubleBytes(host.remaining()), err, memObjects);
        check(CL10.clEnqueueWriteBuffer(runtime.queue(), mem, true, 0L, host, (org.lwjgl.PointerBuffer) null, (org.lwjgl.PointerBuffer) null), "clEnqueueWriteBuffer(double)");
        return mem;
    }

    private long buffer(long flags, long bytes, IntBuffer err, List<Long> memObjects) {
        long mem = CL10.clCreateBuffer(runtime.context(), flags, Math.max(1L, bytes), err);
        check(err.get(0), "clCreateBuffer");
        memObjects.add(mem);
        return mem;
    }

    private void setKernelArg(int index, long memObject) {
        check(CL10.clSetKernelArg1p(runtime.kernel(), index, memObject), "clSetKernelArg(" + index + ")");
    }

    private static IntBuffer directInt(int[] values, List<Buffer> hostBuffers) {
        IntBuffer buffer = MemoryUtil.memAllocInt(Math.max(1, values.length));
        if (values.length == 0) {
            buffer.put(0);
        } else {
            buffer.put(values);
        }
        buffer.flip();
        hostBuffers.add(buffer);
        return buffer;
    }

    private static DoubleBuffer directDouble(double[] values, List<Buffer> hostBuffers) {
        DoubleBuffer buffer = MemoryUtil.memAllocDouble(Math.max(1, values.length));
        if (values.length == 0) {
            buffer.put(0.0D);
        } else {
            buffer.put(values);
        }
        buffer.flip();
        hostBuffers.add(buffer);
        return buffer;
    }

    private static long intBytes(int count) {
        return Math.multiplyExact((long) Math.max(1, count), (long) Integer.BYTES);
    }

    private static long doubleBytes(int count) {
        return Math.multiplyExact((long) Math.max(1, count), (long) Double.BYTES);
    }

    private static void check(int result, String operation) {
        if (result != CL10.CL_SUCCESS) {
            throw new IllegalStateException(operation + " failed: " + result);
        }
    }

    private static boolean shouldValidateSamples() {
        try {
            return ConfigService.get().performance().openclValidateSamples();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String firstMismatch(int[] expected, int[] actual) {
        int limit = Math.min(expected.length, actual.length);
        for (int i = 0; i < limit; i++) {
            if (expected[i] != actual[i]) {
                return "index=" + i + " cpu=" + expected[i] + " gpu=" + actual[i];
            }
        }
        return "length cpu=" + expected.length + " gpu=" + actual.length;
    }

    private static NoiseSettings getNoiseSettings(ServerLevel level) {
        var generator = level.getChunkSource().getGenerator();
        if (generator instanceof NoiseBasedChunkGenerator noiseGen) {
            return noiseGen.generatorSettings().value().noiseSettings();
        }
        return NoiseSettings.create(-64, 384, 1, 2);
    }

    private record KernelPayload(
            int[] params,
            int[] nodeInts,
            double[] nodeValues,
            int[] normalInts,
            double[] normalValues,
            int[] perlinInts,
            double[] perlinValues,
            double[] amplitudes,
            int[] improvedIndices,
            double[] improvedValues,
            int[] permutations,
            int[] splineInts,
            double[] splineLocations,
            int[] splineValueNodes,
            double[] splineDerivatives,
            long scratchBytes
    ) {
        private static KernelPayload from(DensityGraphProgram program,
                                          CoarseHeightBatchRequest request,
                                          int minY,
                                          int maxY,
                                          int cellHeight) {
            int sampleCount = request.sampleCount();
            int nodeCount = program.nodes().size();
            int[] params = {
                    sampleCount,
                    minY,
                    maxY,
                    cellHeight,
                    request.minBlockX(),
                    request.minBlockZ(),
                    request.step(),
                    request.sampleWidth(),
                    program.rootNode(),
                    nodeCount,
                    program.noiseTables().normalNoises().size(),
                    program.noiseTables().perlinNoises().size(),
                    program.noiseTables().improvedNoises().size(),
                    program.splines().size()
            };

            int[] nodeInts = new int[Math.multiplyExact(nodeCount, 5)];
            double[] nodeValues = new double[Math.multiplyExact(nodeCount, 4)];
            for (int i = 0; i < nodeCount; i++) {
                DensityGraphNode node = program.nodes().get(i);
                int intBase = i * 5;
                nodeInts[intBase] = node.type().ordinal();
                nodeInts[intBase + 1] = node.left();
                nodeInts[intBase + 2] = node.right();
                nodeInts[intBase + 3] = node.extraA();
                nodeInts[intBase + 4] = node.extraB();
                int valueBase = i * 4;
                nodeValues[valueBase] = node.valueA();
                nodeValues[valueBase + 1] = node.valueB();
                nodeValues[valueBase + 2] = node.valueC();
                nodeValues[valueBase + 3] = node.valueD();
            }

            OpenCLNoiseTables noiseTables = program.noiseTables();
            int[] normalInts = new int[Math.multiplyExact(noiseTables.normalNoises().size(), 2)];
            double[] normalValues = new double[Math.multiplyExact(noiseTables.normalNoises().size(), 2)];
            for (int i = 0; i < noiseTables.normalNoises().size(); i++) {
                OpenCLNormalNoise noise = noiseTables.normalNoises().get(i);
                normalInts[i * 2] = noise.firstPerlinIndex();
                normalInts[i * 2 + 1] = noise.secondPerlinIndex();
                normalValues[i * 2] = noise.valueFactor();
                normalValues[i * 2 + 1] = noise.maxValue();
            }

            List<Double> amplitudeValues = new ArrayList<>();
            List<Integer> improvedIndexValues = new ArrayList<>();
            int[] perlinInts = new int[Math.multiplyExact(noiseTables.perlinNoises().size(), 4)];
            double[] perlinValues = new double[Math.multiplyExact(noiseTables.perlinNoises().size(), 3)];
            for (int i = 0; i < noiseTables.perlinNoises().size(); i++) {
                OpenCLPerlinNoise noise = noiseTables.perlinNoises().get(i);
                int amplitudeOffset = amplitudeValues.size();
                amplitudeValues.addAll(noise.amplitudes());
                int improvedOffset = improvedIndexValues.size();
                improvedIndexValues.addAll(noise.improvedNoiseIndices());
                int intBase = i * 4;
                perlinInts[intBase] = noise.firstOctave();
                perlinInts[intBase + 1] = noise.amplitudes().size();
                perlinInts[intBase + 2] = amplitudeOffset;
                perlinInts[intBase + 3] = improvedOffset;
                int valueBase = i * 3;
                perlinValues[valueBase] = noise.lowestFreqInputFactor();
                perlinValues[valueBase + 1] = noise.lowestFreqValueFactor();
                perlinValues[valueBase + 2] = noise.maxValue();
            }

            int[] improvedIndices = toIntArray(improvedIndexValues);
            double[] amplitudes = toDoubleArray(amplitudeValues);
            int[] permutations = new int[Math.multiplyExact(noiseTables.improvedNoises().size(), 256)];
            double[] improvedValues = new double[Math.multiplyExact(noiseTables.improvedNoises().size(), 3)];
            for (int i = 0; i < noiseTables.improvedNoises().size(); i++) {
                OpenCLImprovedNoise noise = noiseTables.improvedNoises().get(i);
                improvedValues[i * 3] = noise.xo();
                improvedValues[i * 3 + 1] = noise.yo();
                improvedValues[i * 3 + 2] = noise.zo();
                byte[] permutation = noise.permutation();
                if (permutation.length != 256) {
                    throw new IllegalStateException("ImprovedNoise permutation size must be 256");
                }
                for (int p = 0; p < 256; p++) {
                    permutations[i * 256 + p] = permutation[p] & 0xFF;
                }
            }

            List<Double> splineLocationValues = new ArrayList<>();
            List<Integer> splineValueNodeValues = new ArrayList<>();
            List<Double> splineDerivativeValues = new ArrayList<>();
            int[] splineInts = new int[Math.multiplyExact(program.splines().size(), 3)];
            for (int i = 0; i < program.splines().size(); i++) {
                OpenCLSpline spline = program.splines().get(i);
                int pointOffset = splineLocationValues.size();
                splineLocationValues.addAll(spline.locations());
                splineValueNodeValues.addAll(spline.valueNodes());
                splineDerivativeValues.addAll(spline.derivatives());
                int intBase = i * 3;
                splineInts[intBase] = spline.coordinateNode();
                splineInts[intBase + 1] = pointOffset;
                splineInts[intBase + 2] = spline.pointCount();
            }

            long scratchBytes = Math.multiplyExact(
                    Math.multiplyExact((long) sampleCount, (long) Math.max(1, nodeCount)),
                    (long) Double.BYTES);
            return new KernelPayload(
                    params,
                    nodeInts,
                    nodeValues,
                    normalInts,
                    normalValues,
                    perlinInts,
                    perlinValues,
                    amplitudes,
                    improvedIndices,
                    improvedValues,
                    permutations,
                    splineInts,
                    toDoubleArray(splineLocationValues),
                    toIntArray(splineValueNodeValues),
                    toDoubleArray(splineDerivativeValues),
                    scratchBytes);
        }

        private static double[] toDoubleArray(List<Double> values) {
            double[] result = new double[values.size()];
            for (int i = 0; i < values.size(); i++) {
                result[i] = values.get(i);
            }
            return result;
        }

        private static int[] toIntArray(List<Integer> values) {
            int[] result = new int[values.size()];
            for (int i = 0; i < values.size(); i++) {
                result[i] = values.get(i);
            }
            return result;
        }
    }
}