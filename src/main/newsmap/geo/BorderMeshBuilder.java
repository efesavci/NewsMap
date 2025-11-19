package main.newsmap.geo;

import javafx.geometry.Point3D;
import javafx.scene.shape.TriangleMesh;
import java.util.ArrayList;
import java.util.List;


public class BorderMeshBuilder {

    private final List<Float> pts = new ArrayList<>();
    private final List<Integer> faces = new ArrayList<>();

    private int addPoint(Point3D p) {
        int idx = pts.size() / 3;
        pts.add((float) p.getX());
        pts.add((float) p.getY());
        pts.add((float) p.getZ());
        return idx;
    }

    public void addQuad(Point3D aUp, Point3D bUp, Point3D bDn, Point3D aDn) {
        int i0 = addPoint(aUp);
        int i1 = addPoint(bUp);
        int i2 = addPoint(bDn);
        int i3 = addPoint(aDn);

        faces.add(i0); faces.add(0);
        faces.add(i1); faces.add(0);
        faces.add(i2); faces.add(0);


        faces.add(i0); faces.add(0);
        faces.add(i2); faces.add(0);
        faces.add(i3); faces.add(0);
    }

    public TriangleMesh buildMesh() {
        TriangleMesh mesh = new TriangleMesh();

        float[] pointsArray = new float[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            pointsArray[i] = pts.get(i);
        }
        mesh.getPoints().addAll(pointsArray);


        mesh.getTexCoords().addAll(0f, 0f);

        int[] facesArray = new int[faces.size()];
        for (int i = 0; i < faces.size(); i++) {
            facesArray[i] = faces.get(i);
        }
        mesh.getFaces().addAll(facesArray);

        return mesh;
    }
}

