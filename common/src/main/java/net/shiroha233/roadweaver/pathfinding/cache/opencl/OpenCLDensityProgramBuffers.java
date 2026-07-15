/* 文件职责：持有 density graph 静态设备缓冲区并按 kernel 参数顺序提供访问。 */
package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import net.shiroha233.roadweaver.pathfinding.cache.opencl.bridge.OpenCLBridge;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个 density program 在一个 OpenCL runtime 上的常驻缓冲区。
 */
final class OpenCLDensityProgramBuffers implements AutoCloseable {
    private final List<OpenCLBridge.DeviceBuffer> arguments;

    private OpenCLDensityProgramBuffers(List<OpenCLBridge.DeviceBuffer> arguments) {
        this.arguments = List.copyOf(arguments);
    }

    static OpenCLDensityProgramBuffers upload(OpenCLRuntime runtime, OpenCLDensityProgramPayload payload) {
        ArrayList<OpenCLBridge.DeviceBuffer> buffers = new ArrayList<>(14);
        try {
            buffers.add(runtime.upload(payload.nodeInts()));
            buffers.add(runtime.upload(payload.nodeValues()));
            buffers.add(runtime.upload(payload.normalInts()));
            buffers.add(runtime.upload(payload.normalValues()));
            buffers.add(runtime.upload(payload.perlinInts()));
            buffers.add(runtime.upload(payload.perlinValues()));
            buffers.add(runtime.upload(payload.amplitudes()));
            buffers.add(runtime.upload(payload.improvedIndices()));
            buffers.add(runtime.upload(payload.improvedValues()));
            buffers.add(runtime.upload(payload.permutations()));
            buffers.add(runtime.upload(payload.splineInts()));
            buffers.add(runtime.upload(payload.splineLocations()));
            buffers.add(runtime.upload(payload.splineValueNodes()));
            buffers.add(runtime.upload(payload.splineDerivatives()));
            return new OpenCLDensityProgramBuffers(buffers);
        } catch (Throwable failure) {
            buffers.forEach(OpenCLBridge.DeviceBuffer::close);
            throw failure;
        }
    }

    List<OpenCLBridge.DeviceBuffer> arguments() {
        return arguments;
    }

    @Override
    public void close() {
        arguments.forEach(OpenCLBridge.DeviceBuffer::close);
    }
}
