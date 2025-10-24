package com.example.newsmapdumen;

import javafx.animation.*;
import javafx.beans.property.DoubleProperty;
import javafx.application.Application;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Point3D;
import javafx.scene.*;
import javafx.scene.control.Hyperlink;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.io.InputStream;
import javafx.scene.control.Button;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;

import javafx.util.Duration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GlobeView extends Application {

    private static final double EARTH_RADIUS = 300;
    private static final double CAMERA_START_Z = -900;
    private static final double CAMERA_MIN_Z = -2500;
    private static final double CAMERA_MAX_Z = -450;
    ;

    private Group root3D;
    private Group globeGroup;
    private PerspectiveCamera camera;
    //newsPanel
    private VBox newsPanel;
    private boolean newsPanelVisible = false;

    // rotation state
    private double anchorX, anchorY;
    private double anchorAngleX = 0;
    private double anchorAngleY = 0;
    private final Rotate rotateX = new Rotate(0, Rotate.X_AXIS);
    private final Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);
    private final List<Hotspot> hotspots = new ArrayList<>();

    @Override
    public void start(Stage stage) {

        globeGroup = new Group();
        globeGroup.getTransforms().addAll(rotateX, rotateY);


        // === DATA GLOBE LAYER ===
        // 1. Borders mesh from GeoJSON (placeholder for now)
        MeshView bordersView = buildBordersMeshFromGeoJSON("world.json");

        // Borders material: light gray lines
        PhongMaterial borderMat = new PhongMaterial(Color.web("#6b7280")); // medium-gray
        bordersView.setMaterial(borderMat);

        // Add borders to globe group
        globeGroup.getChildren().add(bordersView);

        // Optional: debugging sphere (invisible / dev only)
        // This just helps to see the "planet core"
        Sphere debugCore = new Sphere(EARTH_RADIUS);
        debugCore.setMaterial(new PhongMaterial(Color.web("#1f2937"))); // dark gray land-ish fill
        debugCore.setCullFace(CullFace.FRONT); // draw back faces only if you want a hollow look, you can also remove this line
        globeGroup.getChildren().add(debugCore);

        // === LIGHTS ===
        AmbientLight ambient = new AmbientLight(Color.color(0.5, 0.5, 0.5));
        PointLight sun = new PointLight(Color.WHITE);
        sun.getTransforms().add(new Translate(-1000, -400, -1200));

        root3D = new Group(globeGroup, ambient, sun);

        // === CAMERA ===
        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(5000);
        camera.setTranslateZ(CAMERA_START_Z);
        // ===NEWSPANEL===


// initially hide panel content (slide out)


        // === SUBSCENE ===
        SubScene sub = new SubScene(root3D, 1200, 800, true, SceneAntialiasing.BALANCED);
        sub.setFill(Color.web("#0b1020")); // dark background
        sub.setCamera(camera);

        // mouse rotate + scroll zoom
        enableMouseControl(sub);
        sub.addEventHandler(ScrollEvent.SCROLL, e -> {
            double dz = e.getDeltaY() * 0.7;
            double target = camera.getTranslateZ() + dz; // note: we want scroll-up = zoom in
            if (target < CAMERA_MIN_Z) target = CAMERA_MIN_Z;
            if (target > CAMERA_MAX_Z) target = CAMERA_MAX_Z;
            camera.setTranslateZ(target);
        });

        newsPanel = buildNewsPanel();
        newsPanel.setMouseTransparent(false);      // let it eat clicks
        newsPanel.setTranslateX(300);              // start hidden (off to right)
        newsPanelVisible = false;

        // anchor panel to right side of the StackPane
        StackPane.setAlignment(newsPanel, javafx.geometry.Pos.CENTER_RIGHT);

        // --- 4. StackPane root overlay:
        // globe in back, panel on top of it
        StackPane globeWrapper = new StackPane(sub);
        globeWrapper.setStyle("-fx-background-color: #0b1020;");

        StackPane rootLayout = new StackPane();
        rootLayout.getChildren().addAll(globeWrapper, newsPanel);

        // --- 5. Scene / stage setup ---
        Scene scene = new Scene(rootLayout, 1200, 800);

        // keep the SubScene synced to the window size
        scene.widthProperty().addListener((obs, o, w) -> sub.setWidth(w.doubleValue()));
        scene.heightProperty().addListener((obs, o, h) -> sub.setHeight(h.doubleValue()));

        // === waves demo ===
        /** * TODO Here must be the embedding + clustering and news fetching implemented (for sake of cleanness in another class)
         *      and adjust the dummy Hotspots after fetching the news and listing them for each of the embedded locations we get.
         *
         * */
        List<Article> list  = List.of(new Article("Trump says he's terminating trade talks with Canada over TV ad about tariffs", "ABC-NEWS", "https://abcnews.go.com/Politics/trump-terminating-trade-talks-canada-tv-ad-tariffs/story?id=126821528", System.currentTimeMillis()));
        spawnHotspot(52.5200, 13.4050, list);    // Berlin
        spawnHotspot(40.7128, -74.0060, list);   // New York
        spawnHotspot(41.0082, 28.9784, list);    // Istanbul

        // === SCENE / STAGE ===
        stage.setTitle("News Globe (borders + pulses)");
        stage.setScene(scene);
        stage.show();

    }

    // -------------------------------------------------
    // INTERACTION
    // -------------------------------------------------
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
                // clamp X tilt so you can't flip under the south pole
                rotateX.setAngle(clamp(anchorAngleX + dy * sensitivity, -90, 90));
                rotateY.setAngle(anchorAngleY - dx * sensitivity);
            }
        });
    }
    private void showNewsPanel() {
        if (newsPanelVisible) return;
        newsPanelVisible = true;

        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(250),
                        new KeyValue(newsPanel.translateXProperty(), 0, Interpolator.EASE_BOTH))
        );
        t.play();
    }

    private void hideNewsPanel() {
        if (!newsPanelVisible) return;
        newsPanelVisible = false;

        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(250),
                        new KeyValue(newsPanel.translateXProperty(), 300, Interpolator.EASE_BOTH))
        );
        t.play();
    }

    private void toggleNewsPanel() {
        if (newsPanelVisible) {
            hideNewsPanel();
        } else {
            showNewsPanel();
        }
    }

    // -------------------------------------------------
    // GEOJSON -> BORDER MESH (stub for now)
    // -------------------------------------------------
    private static class BorderMeshBuilder {
        // We store all vertex positions in a float list
        private final List<Float> pts = new ArrayList<>();
        // Each face in TriangleMesh is: pIndex/tIndex pairs. We'll reuse a single texCoord (0,0),
        // so the tIndex is always 0.
        private final List<Integer> faces = new ArrayList<>();

        // add a single vertex to pts list, return its index
        int addPoint(Point3D p) {
            int idx = pts.size() / 3;
            pts.add((float) p.getX());
            pts.add((float) p.getY());
            pts.add((float) p.getZ());
            return idx;
        }

        // given four corner points of a quad (A_up, B_up, B_dn, A_dn)
        // we split into two triangles: (0,1,2) and (0,2,3)
        void addQuad(Point3D aUp, Point3D bUp, Point3D bDn, Point3D aDn) {
            int i0 = addPoint(aUp);
            int i1 = addPoint(bUp);
            int i2 = addPoint(bDn);
            int i3 = addPoint(aDn);

            // Triangle 1: i0, i1, i2
            faces.add(i0); faces.add(0);
            faces.add(i1); faces.add(0);
            faces.add(i2); faces.add(0);

            // Triangle 2: i0, i2, i3
            faces.add(i0); faces.add(0);
            faces.add(i2); faces.add(0);
            faces.add(i3); faces.add(0);
        }

        TriangleMesh buildMesh() {
            TriangleMesh mesh = new TriangleMesh();

            // dump points
            float[] pointsArray = new float[pts.size()];
            for (int i = 0; i < pts.size(); i++) {
                pointsArray[i] = pts.get(i);
            }
            mesh.getPoints().addAll(pointsArray);

            // We only use one dummy texCoord (0,0)
            mesh.getTexCoords().addAll(0f, 0f);

            // dump faces
            int[] facesArray = new int[faces.size()];
            for (int i = 0; i < faces.size(); i++) {
                facesArray[i] = faces.get(i);
            }
            mesh.getFaces().addAll(facesArray);

            return mesh;
        }
    }
    private void addBorderRing(BorderMeshBuilder builder,
                               List<double[]> ringLatLon,
                               double radius,
                               double halfWidth) {
        // ringLatLon is like [ [lon,lat], [lon,lat], ... ]
        // We'll connect point i -> i+1, and also last -> first.

        int n = ringLatLon.size();
        for (int i = 0; i < n; i++) {
            double[] aLL = ringLatLon.get(i);
            double[] bLL = ringLatLon.get((i+1) % n); // wrap to first

            double lonA = aLL[0];
            double latA = aLL[1];
            double lonB = bLL[0];
            double latB = bLL[1];

            Point3D A3 = latLonToPoint(latA, lonA, radius);
            Point3D B3 = latLonToPoint(latB, lonB, radius);

            Point3D d = B3.subtract(A3);       // segment direction
            Point3D nrm = A3.normalize();      // surface normal at A (roughly radial)
            Point3D s = d.crossProduct(nrm).normalize(); // sideways vector along surface

            Point3D A_up = A3.add(s.multiply(halfWidth));
            Point3D A_dn = A3.subtract(s.multiply(halfWidth));
            Point3D B_up = B3.add(s.multiply(halfWidth));
            Point3D B_dn = B3.subtract(s.multiply(halfWidth));

            builder.addQuad(A_up, B_up, B_dn, A_dn);
        }
    }
    private MeshView buildBordersMeshFromGeoJSON(String resourcePath) {
        try {
            // 1. load file from resources
            InputStream is = getClass().getResourceAsStream("/" + resourcePath);
            if (is == null) {
                System.err.println("Could not find " + resourcePath + " on classpath.");
                return emptyMeshView();
            }
            String jsonText = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            // debug preview
            System.out.println("=== DEBUG world.json start ===");
            System.out.println(jsonText.substring(0, Math.min(jsonText.length(), 400)));
            System.out.println("=== DEBUG world.json end ===");

            JSONObject root = new JSONObject(jsonText);

            BorderMeshBuilder builder = new BorderMeshBuilder();
            double radius = EARTH_RADIUS + 0.5;  // slightly above globe surface
            double halfWidth = 0.2;              // line thickness

            if (root.has("features")) {
                // ---------------------------------
                // CASE A: FeatureCollection
                // ---------------------------------
                JSONArray features = root.getJSONArray("features");
                for (int f = 0; f < features.length(); f++) {
                    JSONObject feature = features.getJSONObject(f);
                    JSONObject geom = feature.getJSONObject("geometry");
                    addGeometryToBuilder(geom, builder, radius, halfWidth);
                }

            } else if ("GeometryCollection".equals(root.optString("type"))) {
                // ---------------------------------
                // CASE B: GeometryCollection
                // ---------------------------------
                JSONArray geoms = root.getJSONArray("geometries");
                for (int g = 0; g < geoms.length(); g++) {
                    JSONObject geom = geoms.getJSONObject(g);
                    addGeometryToBuilder(geom, builder, radius, halfWidth);
                }

            } else if (root.has("type") && root.has("coordinates")) {
                // ---------------------------------
                // CASE C: bare Polygon / MultiPolygon
                // ---------------------------------
                addGeometryToBuilder(root, builder, radius, halfWidth);

            } else {
                System.err.println("JSON root is not FeatureCollection / GeometryCollection / Polygon / MultiPolygon. Keys: " + root.keySet());
            }

            TriangleMesh mesh = builder.buildMesh();
            MeshView mv = new MeshView(mesh);
            mv.setCullFace(CullFace.NONE);
            mv.setDepthTest(DepthTest.ENABLE);
            return mv;

        } catch (Exception e) {
            e.printStackTrace();
            return emptyMeshView();
        }
    }
    private MeshView emptyMeshView() {
        TriangleMesh mesh = new TriangleMesh();
        // JavaFX requires at least one texture coordinate, or it throws an exception
        mesh.getTexCoords().addAll(0f, 0f);
        MeshView mv = new MeshView(mesh);
        mv.setCullFace(CullFace.NONE);
        mv.setDepthTest(DepthTest.ENABLE);
        return mv;
    }
    private void addGeometryToBuilder(JSONObject geom,
                                      BorderMeshBuilder builder,
                                      double radius,
                                      double halfWidth) {

        String geomType = geom.getString("type");

        if (geomType.equals("Polygon")) {
            // coordinates: [ [ [lon,lat], [lon,lat], ... ], [hole...], ... ]
            JSONArray rings = geom.getJSONArray("coordinates");
            if (rings.length() == 0) return;

            // outer ring = rings[0]
            JSONArray outer = rings.getJSONArray(0);

            List<double[]> ringLatLon = new ArrayList<>(outer.length());
            for (int i = 0; i < outer.length(); i++) {
                JSONArray coord = outer.getJSONArray(i);
                double lon = coord.getDouble(0);
                double lat = coord.getDouble(1);
                ringLatLon.add(new double[]{lon, lat});
            }

            addBorderRing(builder, ringLatLon, radius, halfWidth);

        } else if (geomType.equals("MultiPolygon")) {
            // coordinates: [
            //   [ [ [lon,lat], ... ], [hole...] ],
            //   [ [ [lon,lat], ... ], ... ],
            //   ...
            // ]
            JSONArray polys = geom.getJSONArray("coordinates");

            for (int p = 0; p < polys.length(); p++) {
                JSONArray rings = polys.getJSONArray(p);
                if (rings.length() == 0) continue;

                // again: outer ring only
                JSONArray outer = rings.getJSONArray(0);

                List<double[]> ringLatLon = new ArrayList<>(outer.length());
                for (int i = 0; i < outer.length(); i++) {
                    JSONArray coord = outer.getJSONArray(i);
                    double lon = coord.getDouble(0);
                    double lat = coord.getDouble(1);
                    ringLatLon.add(new double[]{lon, lat});
                }

                addBorderRing(builder, ringLatLon, radius, halfWidth);
            }

        } else {
            // We skip LineString / Point etc. for now.
            // You *could* handle LineString here if your dataset has coastlines as LineStrings.
            // System.out.println("Skipping geometry type: " + geomType);
        }
    }


    // -------------------------------------------------
    // LAT/LON -> 3D POINT ON GLOBE
    // -------------------------------------------------
    private Point3D latLonToPoint(double latDeg, double lonDeg, double radius) {
        double lat = Math.toRadians(latDeg);
        double lon = Math.toRadians(lonDeg);

        double x = radius * Math.cos(lat) * Math.cos(lon);
        double y = -radius * Math.sin(lat);            // minus: so north is visually "up"
        double z = radius * Math.cos(lat) * Math.sin(lon);

        return new Point3D(x, y, z);
    }
    public void spawnHotspot(double latDeg, double lonDeg, List<Article> articles) {

        // Position in 3D
        Point3D center = latLonToPoint(latDeg, lonDeg, EARTH_RADIUS + 2.5);

        // Build tangent basis for wave quad
        Point3D n = center.normalize();
        Point3D up = new Point3D(0, -1, 0);
        if (Math.abs(n.dotProduct(up)) > 0.99) {
            up = new Point3D(1, 0, 0);
        }
        Point3D u = up.crossProduct(n).normalize();
        Point3D v = n.crossProduct(u).normalize();

        // Material (ring texture)
        Image ring = makeRingTexture(256, Color.RED);
        PhongMaterial mat = new PhongMaterial();
        mat.setDiffuseMap(ring);
        mat.setSpecularColor(Color.TRANSPARENT);

        double baseOpacity = 0.3;
        double startSize = 0.0;
        double endSize = 15.0;

        Group waveGroup = new Group();
        waveGroup.setPickOnBounds(true);
        globeGroup.getChildren().add(waveGroup);

        for (int i = 0; i < 2; i++) {
            TriangleMesh mesh = new TriangleMesh();
            mesh.getTexCoords().addAll(
                    0,0,
                    1,0,
                    1,1,
                    0,1
            );
            mesh.getFaces().addAll(
                    0,0, 1,1, 2,2,
                    0,0, 2,2, 3,3
            );

            MeshView wave = new MeshView(mesh);
            wave.setMaterial(mat);
            wave.setCullFace(CullFace.NONE);
            wave.setDepthTest(DepthTest.ENABLE);
            wave.setBlendMode(javafx.scene.effect.BlendMode.ADD);
            wave.setOpacity(0.0);

            // size property animation
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

            // keep quad updated
            sizeProp.addListener((obs, o, p) -> updater.run());
            globeGroup.scaleXProperty().addListener((obs, o, p) -> updater.run());
            globeGroup.scaleYProperty().addListener((obs, o, p) -> updater.run());
            globeGroup.scaleZProperty().addListener((obs, o, p) -> updater.run());
            updater.run();

            waveGroup.getChildren().add(wave);

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

        // this keeps the pulsing effect looping by recreating it
        PauseTransition repeatPause = new PauseTransition(Duration.seconds(2.0));
        repeatPause.setOnFinished(e -> {
            globeGroup.getChildren().remove(waveGroup);
            spawnHotspot(latDeg, lonDeg, articles);
        });
        repeatPause.play();

        // Create hotspot record and make it clickable.
        Hotspot hs = new Hotspot(latDeg, lonDeg, articles, waveGroup);

        waveGroup.setOnMouseClicked(e -> {
            if (e.isStillSincePress()) {
                onHotspotClicked(hs); // <- this will open / fill the side panel
            }
            e.consume();
        });

        hotspots.add(hs);
    }
    private VBox buildNewsPanel() {
        VBox box = new VBox();
        box.setStyle("""
        -fx-background-color: rgba(15,23,42,0.9); /* dark slate w/ alpha */
        -fx-padding: 16;
        -fx-spacing: 12;
        -fx-text-fill: #f8fafc; /* near-white */
    """);
        box.setPrefWidth(300);
        box.setMinWidth(300);
        box.setMaxWidth(300);
        return box;
    }

    private void onHotspotClicked(Hotspot hs) {
        newsPanel.getChildren().clear();

        Label header = new Label(
                "Breaking near (" + round2(hs.latDeg) + "°, " + round2(hs.lonDeg) + "°)"
        );
        header.setStyle("""
        -fx-text-fill: #f8fafc;
        -fx-font-size: 16px;
        -fx-font-weight: bold;
    """);
        newsPanel.getChildren().add(header);
        List<Article> arts = (hs.articles == null) ? List.of() : hs.articles;
        int limit = Math.min(5, arts.size());
        for (int i = 0; i < limit; i++) {
            Article a = arts.get(i);

            VBox card = new VBox();
            card.setStyle("""
            -fx-background-color: rgba(30,41,59,0.6);
            -fx-padding: 8;
            -fx-background-radius: 8;
            -fx-spacing: 4;
        """);

            Label titleLbl = new Label(a.title);
            titleLbl.setStyle("""
            -fx-text-fill: #e2e8f0;
            -fx-font-size: 14px;
            -fx-font-weight: bold;
        """);

            Label sourceLbl = new Label(a.source);
            sourceLbl.setStyle("""
            -fx-text-fill: #94a3b8;
            -fx-font-size: 12px;
        """);

            Hyperlink linkLbl = new Hyperlink(a.url);
            linkLbl.setStyle("""
            -fx-text-fill: #38bdf8;
            -fx-font-size: 12px;
        """);
            linkLbl.setOnAction(e -> getHostServices().showDocument(a.url));

            card.getChildren().addAll(titleLbl, sourceLbl, linkLbl);
            newsPanel.getChildren().add(card);
        }

        Button closeBtn = new Button("Close");
        closeBtn.setStyle("""
        -fx-background-color: #1e293b;
        -fx-text-fill: #f8fafc;
        -fx-background-radius: 6;
        -fx-padding: 6 10;
        -fx-font-size: 12px;
    """);
        closeBtn.setOnAction(e -> hideNewsPanel());
        newsPanel.getChildren().add(closeBtn);

        showNewsPanel();
    }


    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
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
