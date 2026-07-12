package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

/**
 * OpenCL 运行时。
 */
public final class OpenCLRuntime implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final String KERNEL_RESOURCE = "/assets/roadweaver/opencl/OpenCLCoarseHeightKernel.cl";
    private static final String KERNEL_NAME = "roadweaver_coarse_height_sample";
    private static final Object CL_INIT_LOCK = new Object();
    private static final Object RUNTIME_LOCK = new Object();
    private static boolean clCreated;
    private static OpenCLRuntime sharedRuntime;

    private final Object operationLock = new Object();
    private final OpenCLDevicePreference devicePreference;
    private final long platform;
    private final long device;
    private final long context;
    private final long queue;
    private final long program;
    private final long kernel;

    private OpenCLRuntime(OpenCLDevicePreference devicePreference,
                          long platform,
                          long device,
                          long context,
                          long queue,
                          long program,
                          long kernel) {
        this.devicePreference = devicePreference;
        this.platform = platform;
        this.device = device;
        this.context = context;
        this.queue = queue;
        this.program = program;
        this.kernel = kernel;
    }

    public static OpenCLRuntime tryCreate(OpenCLDevicePreference devicePreference) {
        if (!OpenCLAvailability.isAvailable()) {
            return null;
        }
        OpenCLDevicePreference safePreference = devicePreference == null ? OpenCLDevicePreference.AUTO : devicePreference;
        synchronized (RUNTIME_LOCK) {
            if (!OpenCLAvailability.isAvailable()) {
                return null;
            }
            if (sharedRuntime != null) {
                return sharedRuntime;
            }
            try {
                ensureCLCreated();
                DeviceSelection selection = selectDevice(safePreference);
                if (selection == null) {
                    OpenCLAvailability.disable("未发现匹配的 OpenCL 设备");
                    OpenCLAvailability.logDisabledOnce(null);
                    return null;
                }
                sharedRuntime = createRuntime(safePreference, selection);
                return sharedRuntime;
            } catch (Throwable t) {
                OpenCLAvailability.disable(runtimeFailureReason(t), t);
                return null;
            }
        }
    }

    private static String runtimeFailureReason(Throwable failure) {
        String message = failure == null ? "" : String.valueOf(failure.getMessage());
        if (message.startsWith("clBuildProgram failed")) {
            return "OpenCL kernel 编译失败";
        }
        if (message.startsWith("clCreateKernel failed")) {
            return "OpenCL kernel 创建失败";
        }
        return "OpenCL 初始化失败";
    }

    private static void ensureCLCreated() {
        if (clCreated) {
            return;
        }
        synchronized (CL_INIT_LOCK) {
            if (clCreated) {
                return;
            }
            try {
                CL.create();
            } catch (IllegalStateException alreadyCreated) {
                String message = alreadyCreated.getMessage();
                if (message == null || !message.contains("already been created")) {
                    throw alreadyCreated;
                }
            }
            clCreated = true;
        }
    }

    public OpenCLDevicePreference devicePreference() {
        return devicePreference;
    }

    public String deviceName() {
        return deviceName(device);
    }

    public long context() {
        return context;
    }

    public long queue() {
        return queue;
    }

    public long kernel() {
        return kernel;
    }

    public Object operationLock() {
        return operationLock;
    }

    @Override
    public void close() {
        // 共享 OpenCL runtime 不在瓦片采样器生命周期内释放，避免驱动在并发任务中释放 context 崩溃。
    }

    public static void closeSharedRuntimeForTests() {
        synchronized (RUNTIME_LOCK) {
            OpenCLCoarseHeightBatchSampler.clearProgramCache();
            OpenCLRuntime runtime = sharedRuntime;
            sharedRuntime = null;
            if (runtime != null) {
                runtime.releaseNativeResources();
            }
        }
    }

    private void releaseNativeResources() {
        release(kernel, CL10::clReleaseKernel);
        release(program, CL10::clReleaseProgram);
        release(queue, CL10::clReleaseCommandQueue);
        release(context, CL10::clReleaseContext);
    }

    private static OpenCLRuntime createRuntime(OpenCLDevicePreference preference, DeviceSelection selection) throws IOException {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer err = stack.mallocInt(1);
            PointerBuffer properties = stack.mallocPointer(3);
            properties.put(CL10.CL_CONTEXT_PLATFORM).put(selection.platform()).put(0).flip();
            PointerBuffer devices = stack.mallocPointer(1);
            devices.put(selection.device()).flip();

            long context = CL10.clCreateContext(properties, devices, (org.lwjgl.opencl.CLContextCallbackI) null, 0L, err);
            check(err.get(0), "clCreateContext");

            long queue = CL10.clCreateCommandQueue(context, selection.device(), 0L, err);
            check(err.get(0), "clCreateCommandQueue");

            long program = CL10.clCreateProgramWithSource(context, readKernelSource(), err);
            check(err.get(0), "clCreateProgramWithSource");

            PointerBuffer buildDevices = stack.mallocPointer(1);
            buildDevices.put(selection.device()).flip();
            int buildResult = CL10.clBuildProgram(program, buildDevices, "", (org.lwjgl.opencl.CLProgramCallbackI) null, 0L);
            if (buildResult != CL10.CL_SUCCESS) {
                String log = buildLog(program, selection.device());
                CL10.clReleaseProgram(program);
                CL10.clReleaseCommandQueue(queue);
                CL10.clReleaseContext(context);
                throw new IllegalStateException("clBuildProgram failed: " + buildResult + (log.isBlank() ? "" : "\n" + log));
            }

            long kernel = CL10.clCreateKernel(program, KERNEL_NAME, err);
            check(err.get(0), "clCreateKernel");

            String deviceName = deviceName(selection.device());
            LOGGER.info("OpenCL 粗采样运行时已初始化 preference={} device={} handle=0x{}", preference, deviceName, Long.toHexString(selection.device()));
            return new OpenCLRuntime(preference, selection.platform(), selection.device(), context, queue, program, kernel);
        }
    }

    private static DeviceSelection selectDevice(OpenCLDevicePreference preference) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            int platformsResult = CL10.clGetPlatformIDs((PointerBuffer) null, count);
            if (platformsResult != CL10.CL_SUCCESS || count.get(0) <= 0) {
                return null;
            }

            PointerBuffer platforms = stack.mallocPointer(count.get(0));
            if (CL10.clGetPlatformIDs(platforms, count) != CL10.CL_SUCCESS) {
                return null;
            }

            for (long deviceType : deviceTypes(preference)) {
                for (int i = 0; i < platforms.capacity(); i++) {
                    long platform = platforms.get(i);
                    count.put(0, 0);
                    int result = CL10.clGetDeviceIDs(platform, deviceType, (PointerBuffer) null, count);
                    if (result != CL10.CL_SUCCESS || count.get(0) <= 0) {
                        continue;
                    }
                    PointerBuffer devices = stack.mallocPointer(count.get(0));
                    if (CL10.clGetDeviceIDs(platform, deviceType, devices, (IntBuffer) null) == CL10.CL_SUCCESS) {
                        return new DeviceSelection(platform, devices.get(0));
                    }
                }
            }
            return null;
        }
    }

    private static long[] deviceTypes(OpenCLDevicePreference preference) {
        return switch (preference) {
            case GPU -> new long[]{CL10.CL_DEVICE_TYPE_GPU};
            case CPU -> new long[]{CL10.CL_DEVICE_TYPE_CPU};
            case AUTO -> new long[]{CL10.CL_DEVICE_TYPE_GPU, CL10.CL_DEVICE_TYPE_CPU};
        };
    }

    private static String readKernelSource() throws IOException {
        try (InputStream stream = OpenCLRuntime.class.getResourceAsStream(KERNEL_RESOURCE)) {
            if (stream == null) {
                throw new IOException("missing kernel resource: " + KERNEL_RESOURCE);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String buildLog(long program, long device) {
        return infoString((sizeBuffer, valueBuffer) -> CL10.clGetProgramBuildInfo(
                program,
                device,
                CL10.CL_PROGRAM_BUILD_LOG,
                valueBuffer,
                sizeBuffer));
    }

    private static String deviceName(long device) {
        String name = infoString((sizeBuffer, valueBuffer) -> CL10.clGetDeviceInfo(
                device,
                CL10.CL_DEVICE_NAME,
                valueBuffer,
                sizeBuffer));
        return name == null || name.isBlank() ? "unknown" : name.trim();
    }

    private static String infoString(OpenCLInfoQuery query) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer size = stack.mallocPointer(1);
            int sizeResult = query.query(size, null);
            if (sizeResult != CL10.CL_SUCCESS || size.get(0) <= 0L) {
                return "";
            }
            ByteBuffer value = MemoryUtil.memAlloc((int) size.get(0));
            try {
                int valueResult = query.query(null, value);
                if (valueResult != CL10.CL_SUCCESS) {
                    return "";
                }
                int length = value.remaining();
                if (length > 0 && value.get(length - 1) == 0) {
                    length--;
                }
                byte[] bytes = new byte[length];
                value.get(bytes);
                return new String(bytes, StandardCharsets.UTF_8);
            } finally {
                MemoryUtil.memFree(value);
            }
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void check(int result, String operation) {
        if (result != CL10.CL_SUCCESS) {
            throw new IllegalStateException(operation + " failed: " + result);
        }
    }

    private static void release(long handle, ReleaseOperation operation) {
        if (handle != 0L) {
            try {
                operation.release(handle);
            } catch (Throwable ignored) {}
        }
    }

    private record DeviceSelection(long platform, long device) {}

    @FunctionalInterface
    private interface ReleaseOperation {
        int release(long handle);
    }

    @FunctionalInterface
    private interface OpenCLInfoQuery {
        int query(PointerBuffer sizeBuffer, ByteBuffer valueBuffer);
    }
}
