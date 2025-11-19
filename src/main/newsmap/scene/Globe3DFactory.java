package main.newsmap.scene;

import javafx.scene.*;
import javafx.scene.paint.Color;
import javafx.scene.PointLight;
import javafx.scene.AmbientLight;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;

public class Globe3DFactory {
    public static final double EARTH_RADIUS = 300;
    public static final double CAMERA_START_Z = -900;
    public static final double CAMERA_MIN_Z = -2500;
    public static final double CAMERA_MAX_Z = -450;

    private final Group globeGroup = new Group();
    private final Group root3D = new Group();
    private final PerspectiveCamera camera = new PerspectiveCamera(true);

    public Globe3DFactory() {

        AmbientLight ambient = new AmbientLight(Color.color(0.5, 0.5, 0.5));
        PointLight sun = new PointLight(Color.WHITE);
        sun.getTransforms().add(new javafx.scene.transform.Translate(-1000, -400, -1200));

        root3D.getChildren().addAll(globeGroup, ambient, sun);

        camera.setNearClip(0.1);
        camera.setFarClip(5000);
        camera.setTranslateZ(CAMERA_START_Z);
    }

    public SubScene createSubScene(double w, double h) {
        SubScene sub = new SubScene(root3D, w, h, true, SceneAntialiasing.BALANCED);
        sub.setFill(Color.web("#0b1020"));
        sub.setCamera(camera);
        return sub;
    }

    public Group getGlobeGroup() { return globeGroup; }
    public PerspectiveCamera getCamera() { return camera; }
    public double getEarthRadius() { return EARTH_RADIUS; }
}

