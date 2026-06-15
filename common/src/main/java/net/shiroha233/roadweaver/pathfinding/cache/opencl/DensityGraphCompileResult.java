package net.shiroha233.roadweaver.pathfinding.cache.opencl;

/**
 * Density graph 编译结果。
 */
public record DensityGraphCompileResult(
        DensityGraphProgram program,
        String unsupportedReason
) {
    public static DensityGraphCompileResult supported(DensityGraphProgram program) {
        return new DensityGraphCompileResult(program, null);
    }

    public static DensityGraphCompileResult unsupported(String reason) {
        return new DensityGraphCompileResult(null, reason == null || reason.isBlank() ? "unknown" : reason);
    }

    public boolean supported() {
        return program != null && !program.isEmpty() && unsupportedReason == null;
    }
}