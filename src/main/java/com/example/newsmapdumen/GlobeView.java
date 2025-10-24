package com.example.newsmapdumen;

import javafx.animation.*;
import javafx.beans.property.DoubleProperty;
import javafx.application.Application;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Point3D;
import javafx.scene.*;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;

import javafx.util.Duration;

public class GlobeView extends Application {

    private static final double EARTH_RADIUS = 300;
    private static final double MARKER_RADIUS = 4;
    private static final double CAMERA_START_Z = -900;
    private static final String EARTH_TEXTURE_PATH = "earth.jpg";

    private Group root3D;
    private Group globeGroup;
    private PerspectiveCamera camera;

    private double anchorX, anchorY;
    private double anchorAngleX = 0;
    private double anchorAngleY = 0;
    private final Rotate rotateX = new Rotate(0, Rotate.X_AXIS);
    private final Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);

    @Override
    public void start(Stage stage) {
        root3D = new Group();
        Sphere earth = new Sphere(EARTH_RADIUS);
        globeGroup = new Group();
        globeGroup.setScaleY(0.99665);
        globeGroup.getTransforms().addAll(rotateX, rotateY);

        // --- Earth sphere

        PhongMaterial mat = new PhongMaterial(Color.LIGHTGRAY);
        try {
            Image texture = new Image(EARTH_TEXTURE_PATH);
            mat.setDiffuseMap(texture);
            mat.setSpecularColor(Color.gray(0.2));
        } catch (Exception e) {
            System.out.println("Couldn't load earth texture: " + e.getMessage());
        }
        earth.setMaterial(mat);
        globeGroup.getChildren().add(earth);

        // --- Lighting
        AmbientLight ambient = new AmbientLight(Color.color(0.7, 0.7, 0.7));
        PointLight sun = new PointLight(Color.WHITE);
        sun.getTransforms().add(new Translate(-1000, -400, -1200));
        root3D.getChildren().addAll(globeGroup, ambient, sun);

        // --- Camera
        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(5000);
        camera.setTranslateZ(CAMERA_START_Z);

        // --- SubScene
        SubScene sub = new SubScene(root3D, 1200, 800, true, SceneAntialiasing.BALANCED);
        sub.setFill(Color.web("#0b1020"));
        sub.setCamera(camera);

        // --- Mouse controls only (no auto rotation)
        enableMouseControl(sub);

        // --- Zoom
        sub.addEventHandler(ScrollEvent.SCROLL, e -> {
            double dz = e.getDeltaY() * 0.7;
            double target = camera.getTranslateZ() + (-dz);
            target = clamp(target, -2500, -450);
            camera.setTranslateZ(target);
        });

        // Example markers
        triggerWave(52.5200, 13.4050);    // Berlin
        triggerWave(40.7128, -74.0060);  // New York

        // --- Scene
        Group container = new Group(sub);
        Scene scene = new Scene(container);
        stage.setTitle("3D Globe (mouse-controlled)");
        stage.setScene(scene);
        stage.show();

        scene.widthProperty().addListener((obs, o, n) -> sub.setWidth(n.doubleValue()));
        scene.heightProperty().addListener((obs, o, n) -> sub.setHeight(n.doubleValue()));
    }

    private void enableMouseControl(SubScene sub) {

        sub.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                anchorX = e.getSceneX();
                anchorY = e.getSceneY();
                anchorAngleX = rotateX.getAngle();
                anchorAngleY = rotateY.getAngle();
            }
        });

        sub.setOnMouseDragged(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                double dx = e.getSceneX() - anchorX;
                double dy = e.getSceneY() - anchorY;
                double sensitivity = 0.06;
                rotateX.setAngle(clamp((anchorAngleX + dy * sensitivity), -90, 90));
                rotateY.setAngle(anchorAngleY - dx * sensitivity);
            }
        });
    }

    public void triggerWave(double latitudeDeg, double longitudeDeg) {
        double startSize = 0.0;
        double endSize = 15.0;

        double lat = Math.toRadians(latitudeDeg - 17.0);
        double lonAdj = Math.toRadians(longitudeDeg - 90.0);
        double r = EARTH_RADIUS + 2.5;

        double x = r * Math.cos(lat) * Math.cos(lonAdj);
        double y = r * Math.sin(lat);
        double z = r * Math.cos(lat) * Math.sin(lonAdj);

        // Note: your scene uses -y when placing nodes
        Point3D center = new Point3D(x, -y, z);

        // 2) Build a tangent basis (u, v) on the globe surface at 'center'
        Point3D n = center.normalize(); // outward normal (since globe center is origin)
        Point3D up = new Point3D(0,1,0);
        if (Math.abs(n.normalize().dotProduct(up)) > 0.99) {
            up = new Point3D(1, 0, 0);
        }
        Point3D u = up.crossProduct(n).normalize();
        Point3D v = n.crossProduct(u).normalize();

        Image ring = makeRingTexture(256, Color.RED);
        PhongMaterial mat = new PhongMaterial();
        mat.setDiffuseMap(ring);
        mat.setSpecularColor(Color.TRANSPARENT);

        double baseOpacity = 0.3;
        Group waveGroup = new Group();
        globeGroup.getChildren().add(waveGroup);

        for (int i = 0; i < 2; i++) {
            // each wave gets its own mesh so they animate independently
            TriangleMesh mesh = new TriangleMesh();
            mesh.getTexCoords().addAll(0,0, 1,0, 1,1, 0,1);
            mesh.getFaces().addAll(0,0, 1,1, 2,2, 0,0, 2,2, 3,3);

            MeshView wave = new MeshView(mesh);
            wave.setMaterial(mat);
            wave.setCullFace(javafx.scene.shape.CullFace.NONE);
            wave.setDepthTest(javafx.scene.DepthTest.ENABLE);
            wave.setBlendMode(javafx.scene.effect.BlendMode.ADD);
            wave.setOpacity(0.0);

            // size property + updater for this wave
            DoubleProperty sizeProp = new SimpleDoubleProperty(startSize);
            Runnable updater = () -> {
                double zoom = globeGroup.getScaleX();
                double s = sizeProp.get() * zoom;
                Point3D p0 = center.add(u.multiply(-s)).add(v.multiply(-s));
                Point3D p1 = center.add(u.multiply( s)).add(v.multiply(-s));
                Point3D p2 = center.add(u.multiply( s)).add(v.multiply( s));
                Point3D p3 = center.add(u.multiply(-s)).add(v.multiply( s));
                ((TriangleMesh) wave.getMesh()).getPoints().setAll(
                        (float)p0.getX(), (float)p0.getY(), (float)p0.getZ(),
                        (float)p1.getX(), (float)p1.getY(), (float)p1.getZ(),
                        (float)p2.getX(), (float)p2.getY(), (float)p2.getZ(),
                        (float)p3.getX(), (float)p3.getY(), (float)p3.getZ()
                );
            };
            sizeProp.addListener((obs, o, p) -> updater.run());
            globeGroup.scaleXProperty().addListener((obs, o, p) -> updater.run());
            globeGroup.scaleYProperty().addListener((obs, o, p) -> updater.run());
            globeGroup.scaleZProperty().addListener((obs, o, p) -> updater.run());
            updater.run();

            waveGroup.getChildren().add(wave);

            // one wave’s animation: fade from baseOpacity to 0 while scaling out
            Timeline anim = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(wave.opacityProperty(), baseOpacity),
                            new KeyValue(sizeProp, startSize)
                    ),
                    new KeyFrame(Duration.seconds(2.0),
                            new KeyValue(wave.opacityProperty(), 0.0),
                            new KeyValue(sizeProp, endSize, Interpolator.EASE_OUT)
                    )
            );
            anim.setCycleCount(Animation.INDEFINITE);
            anim.setDelay(Duration.seconds(i * 0.6));
            anim.play();
        }


        PauseTransition repeatPause = new PauseTransition(Duration.seconds(2.0));
        repeatPause.setOnFinished(e -> {
            globeGroup.getChildren().remove(waveGroup);
            triggerWave(latitudeDeg, longitudeDeg);
        });
        repeatPause.play();
    }

    private Image makeRingTexture(int size, Color color) {
        WritableImage img = new WritableImage(size, size);
        PixelWriter pw = img.getPixelWriter();

        double cx = (size - 1) / 2.0;
        double cy = (size - 1) / 2.0;
        double maxR = Math.min(cx, cy);

        double inner = 0.75 * maxR;
        double outer = 0.95 * maxR;
        double thickness = outer - inner;
        double feather = 0.6 * thickness;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = x - cx, dy = y - cy;
                double r = Math.hypot(dx, dy);

                double centerline = inner + thickness / 2.0;
                double dist = Math.abs(r - centerline);

                double a = 1.0 - clamp((dist - (thickness/2.0 - feather)) / feather, 0.0, 1.0);

                double fadeOuter = 1.0 - clamp((r - outer + feather) / feather, 0.0, 1.0);
                double fadeInner = 1.0 - clamp((inner - r + feather) / feather, 0.0, 1.0);
                double alpha = a * fadeOuter * fadeInner;

                if (alpha <= 0) {
                    pw.setColor(x, y, Color.TRANSPARENT);
                } else {
                    pw.setColor(x, y, new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
                }
            }
        }
        return img;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
