package net.shiroha233.roadweaver.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 单段三阶贝塞尔曲线
 */
class BezierSegment {
    Vec3 p0, p1, p2, p3;

    public BezierSegment(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
        this.p0 = p0;
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;
    }

    public static BezierSegment getBezierSegment(Vec3 startPos, Vec3 endPos, Vec3 startAxis, Vec3 endAxis) {
        Vec3 axis1 = startAxis.normalize();
        Vec3 axis2 = endAxis.normalize();
        double handleLength = determineHandleLength(startPos, endPos, axis1, axis2);
        Vec3 p0 = startPos;
        Vec3 p1 = startPos.add(axis1.scale(handleLength));
        Vec3 p2 = endPos.add(axis2.scale(handleLength));
        Vec3 p3 = endPos;
        return new BezierSegment(p0, p1, p2, p3);
    }

    public Vec3 getPoint(double t) {
        double it = 1.0 - t;
        return p0.scale(it * it * it)
                .add(p1.scale(3 * it * it * t))
                .add(p2.scale(3 * it * t * t))
                .add(p3.scale(t * t * t));
    }

    public Vec3 getTangent(double t) {
        double it = 1.0 - t;
        return p1.subtract(p0).scale(3 * it * it)
                .add(p2.subtract(p1).scale(6 * it * t))
                .add(p3.subtract(p2).scale(3 * t * t));
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
        for (int iter = 0; iter < 10; iter++) {
            double leftT = Math.max(0, bestT - range);
            double rightT = Math.min(1, bestT + range);
            double midLeft = leftT + (rightT - leftT) * 0.382;
            double midRight = leftT + (rightT - leftT) * 0.618;
            if (getPoint(midLeft).distanceToSqr(targetP) < getPoint(midRight).distanceToSqr(targetP)) {
                bestT = midLeft;
            } else {
                bestT = midRight;
            }
            range *= 0.7;
        }
        return bestT;
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

/**
 * 连续贝塞尔曲线类 - 用于桥梁路径
 */
public class Curve {
    private final List<BezierSegment> segments = new ArrayList<>();

    public void addSegment(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
        segments.add(new BezierSegment(p0, p1, p2, p3));
    }

    public void addSegment0(Vec3 startPos, Vec3 endPos, Vec3 startAxis, Vec3 endAxis) {
        segments.add(BezierSegment.getBezierSegment(startPos, endPos, startAxis, endAxis));
    }

    public void addLineSegment(Vec3 p0, Vec3 p1) {
        segments.add(new BezierSegment(p0, p0.add(p1.subtract(p0).scale(1/3f)), 
                p0.add(p1.subtract(p0).scale(2/3f)), p1));
    }

    public double getTotalLength() {
        double totalLength = 0;
        for (BezierSegment segment : segments) {
            totalLength += segment.getLength(100);
        }
        return totalLength;
    }

    public static class Frame {
        public Vec3 closestPoint;
        public Vec3 tangent;
        public double globalT;
        public Vec3 tangent0;
        public Vec3 normal0;
        public Vec3 binormal0;
    }

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
        res.tangent0 = new Vec3(res.tangent.x, 0, res.tangent.z).normalize();
        res.normal0 = new Vec3(0, 1, 0);
        res.binormal0 = res.tangent0.cross(res.normal0).normalize();

        return res;
    }
    
    public boolean isEmpty() {
        return segments.isEmpty();
    }
}
