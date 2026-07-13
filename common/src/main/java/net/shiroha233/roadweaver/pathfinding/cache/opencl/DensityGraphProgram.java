package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import java.util.List;

/**
 * OpenCL density graph 编译产物。
 */
public record DensityGraphProgram(
        int rootNode,
        List<DensityGraphNode> nodes,
        List<Double> constants,
        OpenCLNoiseTables noiseTables,
        List<OpenCLSpline> splines
) {
    public DensityGraphProgram {
        nodes = List.copyOf(nodes == null ? List.of() : nodes);
        constants = List.copyOf(constants == null ? List.of() : constants);
        noiseTables = noiseTables == null ? new OpenCLNoiseTables(List.of(), List.of(), List.of()) : noiseTables;
        splines = List.copyOf(splines == null ? List.of() : splines);
    }

    public boolean isEmpty() {
        return rootNode < 0 || nodes.isEmpty();
    }
}