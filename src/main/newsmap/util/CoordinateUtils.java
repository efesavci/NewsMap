package main.newsmap.util;

import javafx.geometry.Point3D;

public class CoordinateUtils {
    public static Point3D latLonToPoint(double latDeg, double lonDeg, double radius) {
        double lat = Math.toRadians(latDeg);
        double lon = Math.toRadians(lonDeg);
        double x = radius * Math.cos(lat) * Math.cos(lon);
        double y = -radius * Math.sin(lat);
        double z = radius * Math.cos(lat) * Math.sin(lon);
        return new Point3D(x, y, z);
    }

    public static double[] pointToLatLon(Point3D p, double radius) {
        double lat = -Math.asin(p.getY() / radius) * 180 / Math.PI;
        double lon = Math.atan2(p.getZ(), p.getX()) * 180 / Math.PI;
        return new double[]{lat, lon};
    }

    public static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
