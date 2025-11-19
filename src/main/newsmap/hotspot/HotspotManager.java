package main.newsmap.hotspot;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.util.Duration;
import main.newsmap.gfx.TextureFactory;
import main.newsmap.model.Article;
import main.newsmap.scene.Globe3DFactory;
import main.newsmap.ui.NewsPanel;
import main.newsmap.util.CoordinateUtils;
import main.newsmap.model.HotspotCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class HotspotManager {

    private final Globe3DFactory globe;
    private final NewsPanel newsPanel;
    private final TextureFactory textures;
    private final List<Hotspot> hotspots = new ArrayList<>();

    public HotspotManager(Globe3DFactory globe, NewsPanel newsPanel, TextureFactory textures) {
        this.globe = globe;
        this.newsPanel = newsPanel;
        this.textures = textures;
    }


    public Hotspot spawnHotspot(double latDeg, double lonDeg, List<Article> articles, HotspotCategory category, String location) {


        Point3D center = CoordinateUtils.latLonToPoint(latDeg, lonDeg, globe.getEarthRadius() + 2.5);


        Point3D n = center.normalize();
        Point3D up = new Point3D(0, -1, 0);
        if (Math.abs(n.dotProduct(up)) > 0.99) up = new Point3D(1, 0, 0);
        Point3D u = up.crossProduct(n).normalize();
        Point3D v = n.crossProduct(u).normalize();


        Image ring = textures.makeRingTexture(128, Color.RED);
        PhongMaterial mat = new PhongMaterial();
        mat.setDiffuseMap(ring);
        mat.setSpecularColor(Color.TRANSPARENT);


        Group waveGroup = new Group();
        waveGroup.setPickOnBounds(true);
        globe.getGlobeGroup().getChildren().add(waveGroup);


        for (int i = 0; i < 2; i++) {
            TriangleMesh mesh = quadMesh();
            MeshView wave = new MeshView(mesh);
            wave.setMaterial(mat);
            wave.setCullFace(CullFace.NONE);
            wave.setBlendMode(BlendMode.ADD);
            wave.setOpacity(0.0);


            final double startSize = 0.0;
            final double endSize = 10.0;
            final double baseOpacity = 0.30;

            DoubleProperty sizeProp = new SimpleDoubleProperty(startSize);

            Runnable updater = () -> {
                double zoom = globe.getGlobeGroup().getScaleX();
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
            globe.getGlobeGroup().scaleXProperty().addListener((obs, o, p) -> updater.run());
            globe.getGlobeGroup().scaleYProperty().addListener((obs, o, p) -> updater.run());
            globe.getGlobeGroup().scaleZProperty().addListener((obs, o, p) -> updater.run());
            updater.run();

            waveGroup.getChildren().add(wave);

            Timeline anim = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(wave.opacityProperty(), baseOpacity),
                            new KeyValue(sizeProp, startSize)
                    ),
                    new KeyFrame(Duration.seconds(3.0),
                            new KeyValue(wave.opacityProperty(), 0.0),
                            new KeyValue(sizeProp, endSize, Interpolator.EASE_OUT)
                    )
            );
            anim.setCycleCount(Animation.INDEFINITE);
            anim.setDelay(Duration.seconds(i));
            anim.play();
        }

            //will get location from cluster + category
        Hotspot hs = new Hotspot(latDeg, lonDeg, articles, category, waveGroup, location);
        waveGroup.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.isStillSincePress()) onHotspotClicked(hs);
            e.consume();
        });

        hotspots.add(hs);
        return hs;
    }


    public void clearAll() {
        for (Hotspot h : hotspots) {
            globe.getGlobeGroup().getChildren().remove(h.node());
        }
        hotspots.clear();
    }


    public void remove(Hotspot h) {
        globe.getGlobeGroup().getChildren().remove(h.node());
        hotspots.remove(h);
    }


    public void applyCategoryFilters(Set<HotspotCategory> categories) {
        boolean showAll = categories.isEmpty();

        for (Hotspot hotspot : hotspots) {   // your internal hotspot list
            if (showAll) {
                hotspot.setVisible(true);
                continue;
            }

            boolean matches = categories.contains(hotspot.getCategory());
            hotspot.setVisible(matches);
        }
    }


    private void onHotspotClicked(Hotspot hs) {
        newsPanel.show(hs, null);
    }


    private static TriangleMesh quadMesh() {
        TriangleMesh mesh = new TriangleMesh();
        mesh.getTexCoords().addAll(
                0,0, 1,0, 1,1, 0,1
        );

        mesh.getPoints().addAll(
                0,0,0,
                0,0,0,
                0,0,0,
                0,0,0
        );
        mesh.getFaces().addAll(
                0,0, 1,1, 2,2,
                0,0, 2,2, 3,3
        );
        return mesh;
    }
}

