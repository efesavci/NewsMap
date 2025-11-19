package main.newsmap.interaction;

import javafx.geometry.Point2D;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SubScene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.transform.Rotate;
import main.newsmap.ui.OverlayPane;
import main.newsmap.geo.CountryFinder;

import static main.newsmap.ui.OverlayPane.countryLabel;
import static main.newsmap.util.CoordinateUtils.clamp;
import static main.newsmap.util.CoordinateUtils.pointToLatLon;

public class GlobeControls {
    private final SubScene sub;
    private final Group globeGroup;

    private final OverlayPane overlay;
    private final CountryFinder countryFinder;
    private final double EARTH_RADIUS;
    private PerspectiveCamera camera;
    private final Rotate rotateX = new Rotate(0, Rotate.X_AXIS);
    private final Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);
    private double anchorX, anchorY;
    private long lastUpdate = 0;
    private static final long UPDATE_INTERVAL_NS = 16_000_000;
    private double anchorAngleX, anchorAngleY;

    private static final double MOVE_THRESHOLD = 4;


    public GlobeControls(SubScene sub,
                         Group globeGroup,
                         OverlayPane overlay,
                         CountryFinder finder,
                         double EARTH_RADIUS) {
        this.sub = sub;
        this.globeGroup = globeGroup;
        this.overlay = overlay;
        this.countryFinder = finder;
        this.EARTH_RADIUS = EARTH_RADIUS;
    }

    public void attach() {
        if (sub.getCamera() instanceof PerspectiveCamera c) camera = c;
        else {
            camera = new PerspectiveCamera(true);
            sub.setCamera(camera);
        }
        globeGroup.getTransforms().addAll(rotateX, rotateY);

        enableRotation();
        enableZoom();
        enableHover();
    }
    private void enableRotation() {
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
                rotateX.setAngle(clamp(anchorAngleX + dy * sensitivity, -90, 90));
                rotateY.setAngle(anchorAngleY - dx * sensitivity);
            }
        });
    }
    private void enableHover() {
        sub.setOnMouseMoved(e -> {
            long now = System.nanoTime();
            if (now - lastUpdate < UPDATE_INTERVAL_NS) {
                updateLabelPositionOnly(e);
                return;
            }
            lastUpdate = now;

            PickResult pick = e.getPickResult();

            // No intersection: hide label
            if (pick == null || pick.getIntersectedNode() == null) {
                countryLabel.setVisible(false);
                return;
            }

            // Get the intersection point (in picked node's local coordinates)
            Point3D localPoint = pick.getIntersectedPoint();
            if (localPoint == null) {
                countryLabel.setVisible(false);
                return;
            }

            // Convert intersection point to globeGroup local coordinates
            Node pickedNode = pick.getIntersectedNode();
            Point3D scenePoint = pickedNode.localToScene(localPoint);
            Point3D globeLocal = globeGroup.sceneToLocal(scenePoint);

            // Compute latitude/longitude
            double[] latLon = pointToLatLon(globeLocal, EARTH_RADIUS);
            String country = countryFinder.findCountry(latLon[0], latLon[1]);

            // Update label visibility and position
            if (country != null) {
                countryLabel.setText(country);

                // Convert mouse screen position to overlay local position
                Point2D local = overlay.screenToLocal(e.getScreenX(), e.getScreenY());
                countryLabel.relocate(local.getX() + 10, local.getY() - 10);
                countryLabel.setVisible(true);
            } else {
                countryLabel.setVisible(false);
            }
        });

    }
    private void updateLabelPositionOnly(javafx.scene.input.MouseEvent e) {
        if (!countryLabel.isVisible()) return;
        Point2D pos = overlay.screenToLocal(e.getScreenX(), e.getScreenY());
        countryLabel.relocate(pos.getX() + 10, pos.getY() - 10);
    }


    private void enableZoom() {
        sub.addEventHandler(ScrollEvent.SCROLL, e -> {
            double dz = e.getDeltaY() * 0.7;
            double target = camera.getTranslateZ() + dz;
            camera.setTranslateZ(clamp(target, -2500, -450));
        });
    }


}

