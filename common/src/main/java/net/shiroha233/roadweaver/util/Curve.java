package net.shiroha233.roadweaver.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 连续贝塞尔曲线类
 */
public class Curve {
    private final List<BezierSegment> segments = new ArrayList<>();

    /**
     * 添加一个三阶贝塞尔曲线段
     * @param p0 起点(P1)
     * @param p1 控制点(P2)
     * @param p2 控制点(P3)
     * @param p3 终点(P4)
     */
    public void addSegment(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
        segments.add(new BezierSegment(p0, p1, p2, p3));
    }

    /**
     * 添加一个三阶贝塞尔曲线段
     * @param startPos 起点(P1)
     * @param endPos 终点(P4)
     * @param startAxis 起点切向量(控制点P1指向P2)
     * @param endAxis 终点切向量(控制点P4指向P3)
     */
    public void addSegment0(Vec3 startPos, Vec3 endPos, Vec3 startAxis, Vec3 endAxis) {
        segments.add(BezierSegment.getBezierSegment(startPos, endPos, startAxis, endAxis));
    }

    /**
     * 添加一条线段
     * @param p0 起点
     * @param p1 终点
     */
    public void addLineSegment(Vec3 p0, Vec3 p1) {
        segments.add(new BezierSegment(p0, p0.add(p1.subtract(p0).scale(1/3f)), p0.add(p1.subtract(p0).scale(2/3f)), p1));
    }

    /**
     * 获取曲线总长度
     * @return 总长度
     */
    public double getTotalLength() {
        double totalLength = 0;
        for (BezierSegment segment : segments) {
            totalLength += segment.getLength(100);
        }
        return totalLength;
    }

    /**
     * 获取曲线上一点的标架信息
     */
    public static class Frame {
        public Vec3 closestPoint;  // 最近点
        public Vec3 tangent;  // 切线
        public double globalT;  // 全局t

        public Vec3 tangent0;  // 水平切线
        public Vec3 normal0;  // 竖直法线
        public Vec3 binormal0;  // 水平副法线

        @Override
        public String toString() {
            return String.format("Closet: %s\nTangent: %s\nGlobal t: %.4f", closestPoint, tangent, globalT);
        }
    }

    /**
     * 传入任意一点，获取曲线上最近的点，和其标架信息
     * @param testPoint 任意一点
     * @return 标架信息(最近点、切线、全局t...)
     */
    public Frame getFrame(Vec3 testPoint) {
        double minDistSq = Double.MAX_VALUE;
        double bestLocalT = 0;
        int bestSegmentIdx = 0;

        for (int i = 0; i < segments.size(); i++) {
            double t = segments.get(i).findClosestT(testPoint);
            double dSq = segments.get(i).getPoint(t).distanceToSqr(testPoint);
            if (dSq < minDistSq) {
                minDistSq = dSq;
                bestLocalT = t;
                bestSegmentIdx = i;
            }
        }

        BezierSegment bestSeg = segments.get(bestSegmentIdx);
        Frame res = new Frame();
        res.closestPoint = bestSeg.getPoint(bestLocalT);
        res.tangent = bestSeg.getTangent(bestLocalT).normalize();
        res.globalT = (bestSegmentIdx + bestLocalT) / segments.size();

        // 标架计算
        res.tangent0 = new Vec3(res.tangent.x, 0, res.tangent.z).normalize();
        res.normal0 = new Vec3(0, 1, 0);
        res.binormal0 = res.tangent0.cross(res.normal0).normalize();

        return res;
    }

    /**
     * 三阶贝塞尔曲线段
     */
    public static class BezierSegment {
        private final Vec3 p0, p1, p2, p3;

        public BezierSegment(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
            this.p0 = p0;
            this.p1 = p1;
            this.p2 = p2;
            this.p3 = p3;
        }

        public static BezierSegment getBezierSegment(Vec3 startPos, Vec3 endPos, Vec3 startAxis, Vec3 endAxis) {
            double handleLength = determineHandleLength(startPos, endPos, startAxis, endAxis);
            return new BezierSegment(startPos, startPos.add(startAxis.scale(handleLength)), endPos.add(endAxis.scale(handleLength)), endPos);
        }

        public Vec3 getPoint(double t) {
            double u = 1 - t;
            double tt = t * t;
            double uu = u * u;
            double uuu = uu * u;
            double ttt = tt * t;

            Vec3 p = p0.scale(uuu);
            p = p.add(p1.scale(3 * uu * t));
            p = p.add(p2.scale(3 * u * tt));
            p = p.add(p3.scale(ttt));

            return p;
        }

        public Vec3 getTangent(double t) {
            double u = 1 - t;
            return p1.subtract(p0).scale(3 * u * u)
                    .add(p2.subtract(p1).scale(6 * u * t))
                    .add(p3.subtract(p2).scale(3 * t * t));
        }

        public double getLength(int steps) {
            double length = 0;
            Vec3 lastPoint = getPoint(0);
            for (int i = 1; i <= steps; i++) {
                double t = (double) i / steps;
                Vec3 currentPoint = getPoint(t);
                length += lastPoint.distanceTo(currentPoint);
                lastPoint = currentPoint;
            }
            return length;
        }

        public double findClosestT(Vec3 targetP) {
            double bestT = 0;
            double minDiv = 100;
            double minDistSq = Double.MAX_VALUE;

            for (int i = 0; i <= minDiv; i++) {
                double t = (double) i / minDiv;
                double dSq = getPoint(t).distanceToSqr(targetP);
                if (dSq < minDistSq) {
                    minDistSq = dSq;
                    bestT = t;
                }
            }

            double range = 1.0 / minDiv;
            for (int i = 0; i < 10; i++) {
                double leftT = Math.max(0, bestT - range);
                double rightT = Math.min(1, bestT + range);

                double midLeft = leftT + (rightT - leftT) * 0.382;
                double midRight = leftT + (rightT - leftT) * 0.618;

                if (getPoint(midLeft).distanceToSqr(targetP) < getPoint(midRight).distanceToSqr(targetP)) {
                    bestT = midLeft;
                } else {
                    bestT = midRight;
                }
                range *= 0.618;
            }

            return bestT;
        }
    }

    private static double determineHandleLength(Vec3 end1, Vec3 end2, Vec3 axis1, Vec3 axis2) {
        Vec3 cross1 = axis1.cross(new Vec3(0, 1, 0));
        Vec3 cross2 = axis2.cross(new Vec3(0, 1, 0));

        double a1 = Mth.atan2(-axis2.z, -axis2.x);
        double a2 = Mth.atan2(axis1.z, axis1.x);
        double angle = a1 - a2;

        float circle = 2 * Mth.PI;
        angle = (angle + circle) % circle;
        if (Math.abs(circle - angle) < Math.abs(angle))
            angle = circle - angle;

        if (Mth.equal(angle, 0)) {
            double[] intersect = intersect(end1, end2, axis1, cross2);
            if (intersect != null) {
                double t = Math.abs(intersect[0]);
                double u = Math.abs(intersect[1]);
                double min = Math.min(t, u);
                double max = Math.max(t, u);

                if (min > 1.2 && max / min > 1 && max / min < 3) {
                    return (max - min);
                }
            }

            return end2.distanceTo(end1) / 3;
        }

        double n = circle / angle;
        double factor = 4 / 3d * Math.tan(Math.PI / (2 * n));
        double[] intersect = intersect(end1, end2, cross1, cross2);

        if (intersect == null) {
            return end2.distanceTo(end1) / 3;
        }

        double radius = Math.abs(intersect[1]);
        double handleLength = radius * factor;
        if (Mth.equal(handleLength, 0))
            handleLength = 1;

        return handleLength;
    }

    private static double[] intersect(Vec3 p1, Vec3 p2, Vec3 r, Vec3 s) {
        Vec3 qminusp = p2.subtract(p1);
        double rcs = r.x * s.z - r.z * s.x;
        if (Mth.equal(rcs, 0))
            return null;
        Vec3 rdivrcs = r.scale(1 / rcs);
        Vec3 sdivrcs = s.scale(1 / rcs);
        double t = qminusp.x * sdivrcs.z - qminusp.z * sdivrcs.x;
        double u = qminusp.x * rdivrcs.z - qminusp.z * rdivrcs.x;
        return new double[] { t, u };
    }
}
