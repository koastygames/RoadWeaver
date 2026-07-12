package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import net.shiroha233.roadweaver.generation.progress.InitialGenerationProgressTracker;
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
import java.util.IdentityHashMap;
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
    private static final Object SESSION_VALIDATION_LOCK = new Object();
    private static final Object PROGRAM_CACHE_LOCK = new Object();
    private static final IdentityHashMap<Object, CachedDensityProgram> PROGRAM_CACHE = new IdentityHashMap<>();
    private static boolean sessionValidationStarted;
    private static boolean sessionValidationFinished;

    private final ServerLevel level;
    private final OpenCLRuntime runtime;
    private final CachedDensityProgram cachedProgram;
    private final DensityGraphProgram program;
    private final int minY;
    private final int maxY;
    private final int cellHeight;
    private CpuCoarseHeightBatchSampler fallback;

    private OpenCLCoarseHeightBatchSampler(ServerLevel level,
                                            OpenCLRuntime runtime,
                                            CachedDensityProgram cachedProgram,
                                            NoiseSettings settings) {
        this.level = level;
        this.runtime = runtime;
        this.cachedProgram = cachedProgram;
        this.program = cachedProgram.program();
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
        CachedCompileResult graph = compileInitialDensity(level);
        if (!graph.supported()) {
            if (graph.retryable()) {
                LOGGER.info("OpenCL 粗采样暂时不可用，维度 {} 本次回退到 CPU，后续继续重试: {}",
                        level.dimension().location(),
                        graph.unsupportedReason());
                return null;
            }
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
                    graph.cachedProgram(),
                    getNoiseSettings(level));
            InitialGenerationProgressTracker.setBackend("OPENCL", runtime.deviceName(), null);
            if (ENABLED_LOGGED.compareAndSet(false, true)) {
                LOGGER.info("OpenCL 粗采样已启用 preference={} nodes={} normalNoises={} splines={}",
                        runtime.devicePreference(),
                        graph.cachedProgram().program().nodes().size(),
                        graph.cachedProgram().program().noiseTables().normalNoises().size(),
                        graph.cachedProgram().program().splines().size());
            }
            return sampler;
        } catch (Throwable t) {
            OpenCLWorldSupport.markUnsupported(level.dimension().location(), "OpenCL 粗采样器创建失败: " + t.getClass().getSimpleName());
            LOGGER.info("OpenCL 粗采样器创建失败，维度 {} 回退到 CPU: {}", level.dimension().location(), t.getMessage(), t);
            return null;
        }
    }

    public static void clearProgramCache() {
        synchronized (PROGRAM_CACHE_LOCK) {
            for (CachedDensityProgram program : PROGRAM_CACHE.values()) {
                program.close();
            }
            PROGRAM_CACHE.clear();
        }
    }

    private static CachedCompileResult compileInitialDensity(ServerLevel level) {
        try {
            var randomState = level.getChunkSource().getGeneratorState().randomState();
            synchronized (PROGRAM_CACHE_LOCK) {
                CachedDensityProgram cached = PROGRAM_CACHE.get(randomState);
                if (cached != null) {
                    return CachedCompileResult.supported(cached);
                }
                var router = randomState.router();
                DensityGraphCompileResult result = DensityGraphCompiler.compile(router.initialDensityWithoutJaggedness());
                if (!result.supported()) {
                    return CachedCompileResult.from(result);
                }
                cached = new CachedDensityProgram(result.program());
                PROGRAM_CACHE.put(randomState, cached);
                return CachedCompileResult.supported(cached);
            }
        } catch (Throwable t) {
            LOGGER.info("OpenCL 初始 density graph 提取失败: dimension={} error={} message={}",
                    level == null ? "null" : level.dimension().location(),
                    t.getClass().getName(),
                    t.getMessage(),
                    t);
            return CachedCompileResult.retryable("initial density graph unavailable: " + t.getClass().getSimpleName());
        }
    }

    @Override
    public int[] sampleHeights(CoarseHeightBatchRequest request) {
        if (request == null) {
            return null;
        }
        if (OpenCLWorldSupport.isUnsupported(level.dimension().location())) {
            InitialGenerationProgressTracker.setBackend("CPU", "CPU",
                    OpenCLWorldSupport.unsupportedReason(level.dimension().location()));
            return fallback().sampleHeights(request);
        }
        if (program.isEmpty()) {
            InitialGenerationProgressTracker.setBackend("CPU", "CPU", "opencl_program_empty");
            return fallback().sampleHeights(request);
        }
        if (!OpenCLAvailability.isAvailable()) {
            if (DISABLED_FALLBACK_LOGGED.compareAndSet(false, true)) {
                LOGGER.info("OpenCL 粗采样已禁用，当前请求直接使用 CPU: {}", OpenCLAvailability.disabledReason());
            }
            InitialGenerationProgressTracker.setBackend("CPU", "CPU", OpenCLAvailability.disabledReason());
            return fallback().sampleHeights(request);
        }
        long startedAt = System.currentTimeMillis();
        try {
            ValidationMode validationMode = validationMode();
            if (validationMode == ValidationMode.NONE && OpenCLWorldSupport.isUnsupported(level.dimension().location())) {
                InitialGenerationProgressTracker.setBackend("CPU", "CPU",
                        OpenCLWorldSupport.unsupportedReason(level.dimension().location()));
                return fallback().sampleHeights(request);
            }
            int[] heights = sampleHeightsOpenCL(request);
            try {
                if (validationMode.shouldValidate()) {
                    int[] expected = fallback().sampleHeights(request);
                    if (expected == null) {
                        return null;
                    }
                    if (!Arrays.equals(expected, heights)) {
                        String mismatch = firstMismatch(expected, heights);
                        OpenCLWorldSupport.markUnsupported(level.dimension().location(), "OpenCL 粗采样结果校验失败: " + mismatch);
                        LOGGER.info("OpenCL 粗采样结果校验失败，维度 {} 回退到 CPU: {} {}",
                                level.dimension().location(),
                                mismatch,
                                heightSummary(expected, heights));
                        InitialGenerationProgressTracker.setBackend("CPU", "CPU", mismatch);
                        return expected;
                    }
                    if (validationMode == ValidationMode.SESSION) {
                        LOGGER.info("OpenCL 粗采样会话首批校验通过: samples={} {}", request.sampleCount(), heightSummary(expected, heights));
                    }
                }
            } finally {
                finishSessionValidation(validationMode);
            }
            if (KERNEL_LOGGED.compareAndSet(false, true)) {
                LOGGER.info("OpenCL 粗采样 kernel 已执行 samples={} nodes={}", request.sampleCount(), program.nodes().size());
            }
            InitialGenerationProgressTracker.recordSampleBatch("OPENCL", request.sampleCount(),
                    System.currentTimeMillis() - startedAt, runtime.deviceName(), null);
            return heights;
        } catch (Throwable t) {
            OpenCLAvailability.disable("OpenCL kernel 执行失败", t);
            InitialGenerationProgressTracker.setBackend("CPU", "CPU", OpenCLAvailability.disabledReason());
            return fallback().sampleHeights(request);
        }
    }

    @Override
    public boolean isAccelerated() {
        return OpenCLAvailability.isAvailable() && !OpenCLWorldSupport.isUnsupported(level.dimension().location());
    }

    @Override
    public void close() {
        if (fallback != null) {
            fallback.close();
        }
    }

    private CpuCoarseHeightBatchSampler fallback() {
        if (fallback == null) {
            fallback = CpuCoarseHeightBatchSampler.create(level);
        }
        return fallback;
    }

    private int[] sampleHeightsOpenCL(CoarseHeightBatchRequest request) {
        synchronized (runtime.operationLock()) {
            return sampleHeightsOpenCLLocked(request);
        }
    }

    private int[] sampleHeightsOpenCLLocked(CoarseHeightBatchRequest request) {
        synchronized (cachedProgram) {
            return sampleHeightsOpenCLWithProgram(request);
        }
    }

    private int[] sampleHeightsOpenCLWithProgram(CoarseHeightBatchRequest request) {
        RequestKernelPayload payload = RequestKernelPayload.from(program, request, minY, maxY, cellHeight);
        OpenCLDeviceBuffers staticBuffers = cachedProgram.buffers(runtime);
        List<Long> memObjects = new ArrayList<>();
        List<Buffer> hostBuffers = new ArrayList<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer err = stack.mallocInt(1);

            IntBuffer params = directInt(payload.params(), hostBuffers);

            long paramsMem = readOnly(params, err, memObjects);
            long scratchMem = buffer(CL10.CL_MEM_READ_WRITE, payload.scratchBytes(), err, memObjects);
            long heightsMem = buffer(CL10.CL_MEM_WRITE_ONLY, intBytes(request.sampleCount()), err, memObjects);

            int arg = 0;
            setKernelArg(arg++, paramsMem);
            setKernelArg(arg++, staticBuffers.nodeIntsMem());
            setKernelArg(arg++, staticBuffers.nodeValuesMem());
            setKernelArg(arg++, staticBuffers.normalIntsMem());
            setKernelArg(arg++, staticBuffers.normalValuesMem());
            setKernelArg(arg++, staticBuffers.perlinIntsMem());
            setKernelArg(arg++, staticBuffers.perlinValuesMem());
            setKernelArg(arg++, staticBuffers.amplitudesMem());
            setKernelArg(arg++, staticBuffers.improvedIndicesMem());
            setKernelArg(arg++, staticBuffers.improvedValuesMem());
            setKernelArg(arg++, staticBuffers.permutationsMem());
            setKernelArg(arg++, staticBuffers.splineIntsMem());
            setKernelArg(arg++, staticBuffers.splineLocationsMem());
            setKernelArg(arg++, staticBuffers.splineValueNodesMem());
            setKernelArg(arg++, staticBuffers.splineDerivativesMem());
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

    private static ValidationMode validationMode() {
        if (shouldValidateSamples()) {
            return ValidationMode.CONFIG;
        }
        synchronized (SESSION_VALIDATION_LOCK) {
            while (sessionValidationStarted && !sessionValidationFinished) {
                try {
                    SESSION_VALIDATION_LOCK.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ValidationMode.NONE;
                }
            }
            if (sessionValidationFinished) {
                return ValidationMode.NONE;
            }
            sessionValidationStarted = true;
            return ValidationMode.SESSION;
        }
    }

    private static void finishSessionValidation(ValidationMode mode) {
        if (mode != ValidationMode.SESSION) {
            return;
        }
        synchronized (SESSION_VALIDATION_LOCK) {
            sessionValidationFinished = true;
            SESSION_VALIDATION_LOCK.notifyAll();
        }
    }

    private static boolean shouldValidateSamples() {
        try {
            return ConfigService.get().performance().openclValidateSamples();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private enum ValidationMode {
        NONE,
        SESSION,
        CONFIG;

        private boolean shouldValidate() {
            return this != NONE;
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

    private static String heightSummary(int[] expected, int[] actual) {
        return "cpuRange=" + range(expected) + " gpuRange=" + range(actual);
    }

    private static String range(int[] values) {
        if (values == null || values.length == 0) {
            return "empty";
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return min + ".." + max;
    }

    private static NoiseSettings getNoiseSettings(ServerLevel level) {
        var generator = level.getChunkSource().getGenerator();
        if (generator instanceof NoiseBasedChunkGenerator noiseGen) {
            return noiseGen.generatorSettings().value().noiseSettings();
        }
        return NoiseSettings.create(-64, 384, 1, 2);
    }

    private record CachedCompileResult(
            CachedDensityProgram cachedProgram,
            String unsupportedReason,
            boolean retryable
    ) {
        private static CachedCompileResult supported(CachedDensityProgram program) {
            return new CachedCompileResult(program, null, false);
        }

        private static CachedCompileResult unsupported(String reason) {
            return new CachedCompileResult(null, reason == null || reason.isBlank() ? "unknown" : reason, false);
        }

        private static CachedCompileResult retryable(String reason) {
            return new CachedCompileResult(null, reason == null || reason.isBlank() ? "unknown" : reason, true);
        }

        private static CachedCompileResult from(DensityGraphCompileResult result) {
            if (result.retryable()) {
                return retryable(result.unsupportedReason());
            }
            return unsupported(result.unsupportedReason());
        }

        private boolean supported() {
            return cachedProgram != null && cachedProgram.program() != null && !cachedProgram.program().isEmpty() && unsupportedReason == null;
        }
    }

    private static final class CachedDensityProgram {
        private final DensityGraphProgram program;
        private final StaticKernelPayload payload;
        private OpenCLDeviceBuffers buffers;
        private long bufferContext;

        private CachedDensityProgram(DensityGraphProgram program) {
            this.program = program;
            this.payload = StaticKernelPayload.from(program);
        }

        private DensityGraphProgram program() {
            return program;
        }

        private synchronized OpenCLDeviceBuffers buffers(OpenCLRuntime runtime) {
            if (buffers != null && bufferContext == runtime.context()) {
                return buffers;
            }
            closeBuffers();
            buffers = OpenCLDeviceBuffers.create(runtime, payload);
            bufferContext = runtime.context();
            return buffers;
        }

        private synchronized void close() {
            closeBuffers();
        }

        private void closeBuffers() {
            if (buffers != null) {
                buffers.close();
                buffers = null;
                bufferContext = 0L;
            }
        }
    }

    private record RequestKernelPayload(
            int[] params,
            long scratchBytes
    ) {
        private static RequestKernelPayload from(DensityGraphProgram program,
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
            long scratchBytes = Math.multiplyExact(
                    Math.multiplyExact((long) sampleCount, (long) Math.max(1, nodeCount)),
                    (long) Double.BYTES);
            return new RequestKernelPayload(params, scratchBytes);
        }
    }

    private record StaticKernelPayload(
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
            double[] splineDerivatives
    ) {
        private static StaticKernelPayload from(DensityGraphProgram program) {
            int nodeCount = program.nodes().size();
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

            return new StaticKernelPayload(
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
                    toDoubleArray(splineDerivativeValues));
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

    private record OpenCLDeviceBuffers(
            long nodeIntsMem,
            long nodeValuesMem,
            long normalIntsMem,
            long normalValuesMem,
            long perlinIntsMem,
            long perlinValuesMem,
            long amplitudesMem,
            long improvedIndicesMem,
            long improvedValuesMem,
            long permutationsMem,
            long splineIntsMem,
            long splineLocationsMem,
            long splineValueNodesMem,
            long splineDerivativesMem,
            long[] handles
    ) implements AutoCloseable {
        private static OpenCLDeviceBuffers create(OpenCLRuntime runtime, StaticKernelPayload payload) {
            List<Long> memObjects = new ArrayList<>();
            List<Buffer> hostBuffers = new ArrayList<>();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer err = stack.mallocInt(1);
                long nodeIntsMem = uploadReadOnly(runtime, directInt(payload.nodeInts(), hostBuffers), err, memObjects);
                long nodeValuesMem = uploadReadOnly(runtime, directDouble(payload.nodeValues(), hostBuffers), err, memObjects);
                long normalIntsMem = uploadReadOnly(runtime, directInt(payload.normalInts(), hostBuffers), err, memObjects);
                long normalValuesMem = uploadReadOnly(runtime, directDouble(payload.normalValues(), hostBuffers), err, memObjects);
                long perlinIntsMem = uploadReadOnly(runtime, directInt(payload.perlinInts(), hostBuffers), err, memObjects);
                long perlinValuesMem = uploadReadOnly(runtime, directDouble(payload.perlinValues(), hostBuffers), err, memObjects);
                long amplitudesMem = uploadReadOnly(runtime, directDouble(payload.amplitudes(), hostBuffers), err, memObjects);
                long improvedIndicesMem = uploadReadOnly(runtime, directInt(payload.improvedIndices(), hostBuffers), err, memObjects);
                long improvedValuesMem = uploadReadOnly(runtime, directDouble(payload.improvedValues(), hostBuffers), err, memObjects);
                long permutationsMem = uploadReadOnly(runtime, directInt(payload.permutations(), hostBuffers), err, memObjects);
                long splineIntsMem = uploadReadOnly(runtime, directInt(payload.splineInts(), hostBuffers), err, memObjects);
                long splineLocationsMem = uploadReadOnly(runtime, directDouble(payload.splineLocations(), hostBuffers), err, memObjects);
                long splineValueNodesMem = uploadReadOnly(runtime, directInt(payload.splineValueNodes(), hostBuffers), err, memObjects);
                long splineDerivativesMem = uploadReadOnly(runtime, directDouble(payload.splineDerivatives(), hostBuffers), err, memObjects);
                long[] handles = toLongArray(memObjects);
                return new OpenCLDeviceBuffers(
                        nodeIntsMem,
                        nodeValuesMem,
                        normalIntsMem,
                        normalValuesMem,
                        perlinIntsMem,
                        perlinValuesMem,
                        amplitudesMem,
                        improvedIndicesMem,
                        improvedValuesMem,
                        permutationsMem,
                        splineIntsMem,
                        splineLocationsMem,
                        splineValueNodesMem,
                        splineDerivativesMem,
                        handles);
            } catch (Throwable t) {
                releaseAll(memObjects);
                throw t;
            } finally {
                for (Buffer hostBuffer : hostBuffers) {
                    MemoryUtil.memFree(hostBuffer);
                }
            }
        }

        @Override
        public void close() {
            releaseAll(Arrays.asList(toBoxed(handles)));
        }

        private static long uploadReadOnly(OpenCLRuntime runtime, IntBuffer host, IntBuffer err, List<Long> memObjects) {
            long mem = createDeviceBuffer(runtime, CL10.CL_MEM_READ_ONLY, intBytes(host.remaining()), err, memObjects);
            check(CL10.clEnqueueWriteBuffer(runtime.queue(), mem, true, 0L, host, (org.lwjgl.PointerBuffer) null, (org.lwjgl.PointerBuffer) null), "clEnqueueWriteBuffer(int/static)");
            return mem;
        }

        private static long uploadReadOnly(OpenCLRuntime runtime, DoubleBuffer host, IntBuffer err, List<Long> memObjects) {
            long mem = createDeviceBuffer(runtime, CL10.CL_MEM_READ_ONLY, doubleBytes(host.remaining()), err, memObjects);
            check(CL10.clEnqueueWriteBuffer(runtime.queue(), mem, true, 0L, host, (org.lwjgl.PointerBuffer) null, (org.lwjgl.PointerBuffer) null), "clEnqueueWriteBuffer(double/static)");
            return mem;
        }

        private static long createDeviceBuffer(OpenCLRuntime runtime, long flags, long bytes, IntBuffer err, List<Long> memObjects) {
            long mem = CL10.clCreateBuffer(runtime.context(), flags, Math.max(1L, bytes), err);
            check(err.get(0), "clCreateBuffer(static)");
            memObjects.add(mem);
            return mem;
        }

        private static long[] toLongArray(List<Long> values) {
            long[] result = new long[values.size()];
            for (int i = 0; i < values.size(); i++) {
                result[i] = values.get(i);
            }
            return result;
        }

        private static Long[] toBoxed(long[] values) {
            Long[] result = new Long[values == null ? 0 : values.length];
            for (int i = 0; i < result.length; i++) {
                result[i] = values[i];
            }
            return result;
        }

        private static void releaseAll(List<Long> memObjects) {
            for (long memObject : memObjects) {
                if (memObject != 0L) {
                    try {
                        CL10.clReleaseMemObject(memObject);
                    } catch (Throwable ignored) {}
                }
            }
        }
    }
}
