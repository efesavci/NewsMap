package main.newsmap.geo;

import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.paint.Color;
import javafx.scene.DepthTest;
import javafx.scene.shape.CullFace;
import javafx.geometry.Point3D;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import main.newsmap.util.CoordinateUtils;

public class BorderMeshFactory {

    public static MeshView buildFromFeatures(JSONArray features, double earthRadius) {
        BorderMeshBuilder builder = new BorderMeshBuilder();
        double radius = earthRadius + 0.5;
        double halfWidth = 0.2;

        for (int f = 0; f < features.length(); f++) {
            JSONObject feature = features.getJSONObject(f);
            addGeometryToBuilder(feature.getJSONObject("geometry"), builder, radius, halfWidth);
        }

        TriangleMesh mesh = builder.buildMesh();
        MeshView mv = new MeshView(mesh);
        mv.setCullFace(CullFace.NONE);
        mv.setDepthTest(DepthTest.ENABLE);
        mv.setMaterial(new PhongMaterial(Color.web("#6b7280")));
        return mv;
    }

    private static void addGeometryToBuilder(JSONObject geom, BorderMeshBuilder builder,
                                             double radius, double halfWidth) {
        String type = geom.getString("type");
        if (type.equals("Polygon")) {
            JSONArray rings = geom.getJSONArray("coordinates");
            if (rings.length() == 0) return;
            addRing(builder, rings.getJSONArray(0), radius, halfWidth);
        } else if (type.equals("MultiPolygon")) {
            JSONArray polys = geom.getJSONArray("coordinates");
            for (int p = 0; p < polys.length(); p++) {
                JSONArray rings = polys.getJSONArray(p);
                if (rings.length() == 0) continue;
                addRing(builder, rings.getJSONArray(0), radius, halfWidth);
            }
        }
    }

    private static void addRing(BorderMeshBuilder builder, JSONArray ring, double radius, double halfWidth) {
        List<double[]> ringLatLon = new ArrayList<>(ring.length());
        for (int i = 0; i < ring.length(); i++) {
            JSONArray c = ring.getJSONArray(i);
            ringLatLon.add(new double[]{ c.getDouble(0), c.getDouble(1) });
        }
        for (int i = 0; i < ringLatLon.size(); i++) {
            var a = ringLatLon.get(i);
            var b = ringLatLon.get((i+1) % ringLatLon.size());
            Point3D A3 = CoordinateUtils.latLonToPoint(a[1], a[0], radius);
            Point3D B3 = CoordinateUtils.latLonToPoint(b[1], b[0], radius);
            Point3D d = B3.subtract(A3);
            Point3D nrm = A3.normalize();
            Point3D s = d.crossProduct(nrm).normalize();
            builder.addQuad(A3.add(s.multiply(halfWidth)),
                    B3.add(s.multiply(halfWidth)),
                    B3.subtract(s.multiply(halfWidth)),
                    A3.subtract(s.multiply(halfWidth)));
        }
    }
}

