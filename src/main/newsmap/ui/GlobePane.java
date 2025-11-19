package main.newsmap.ui;

import javafx.geometry.Pos;
import javafx.scene.SubScene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.Sphere;
import main.newsmap.scene.Globe3DFactory;
import main.newsmap.geo.BorderMeshFactory;
import main.newsmap.geo.GeoJsonLoader;
import main.newsmap.geo.CountryFinder;
import main.newsmap.interaction.GlobeControls;
import main.newsmap.hotspot.HotspotManager;
import main.newsmap.gfx.TextureFactory;
import main.newsmap.model.Article;

import java.util.List;

import static main.newsmap.scene.Globe3DFactory.EARTH_RADIUS;

public class GlobePane extends StackPane {
    private final OverlayPane overlay;
    private final NewsPanel newsPanel;
    private final SubScene sub;
    private final Globe3DFactory globe;
    private final CountryFinder countryFinder;
    private final HotspotManager hotspotManager;

    public GlobePane() {
        this.setStyle("-fx-background-color: #0b1020;");

        globe = new Globe3DFactory();

        Sphere pickSphere = new Sphere(EARTH_RADIUS);

        PhongMaterial mat = new PhongMaterial();
        mat.setDiffuseColor(Color.color(0,0,0,0));   // fully transparent
        mat.setSpecularColor(Color.color(0,0,0,0));
        pickSphere.setMaterial(mat);

        pickSphere.setDrawMode(DrawMode.FILL);
        pickSphere.setCullFace(CullFace.NONE);
        pickSphere.setMouseTransparent(false); // MUST be false to receive picks
        pickSphere.setPickOnBounds(false);      // helps when precision is off

        // Add it as the *first* child so borders sit above it visually
        globe.getGlobeGroup().getChildren().add(0, pickSphere);

        sub = globe.createSubScene(1200, 800);


        var features = GeoJsonLoader.loadFromClasspath("/world.json");
        var borders = BorderMeshFactory.buildFromFeatures(features, globe.getEarthRadius());
        borders.setMouseTransparent(true);
        globe.getGlobeGroup().getChildren().add(borders);


        overlay = new OverlayPane(this);
        overlay.setMouseTransparent(true);
        StackPane.setAlignment(overlay, Pos.TOP_LEFT);

        newsPanel = new NewsPanel();
        newsPanel.setMouseTransparent(false);
        newsPanel.setTranslateX(300);
        StackPane.setAlignment(newsPanel, Pos.CENTER_RIGHT);


        countryFinder = new CountryFinder(features);

        hotspotManager = new HotspotManager(globe, newsPanel, new TextureFactory());


        this.getChildren().addAll(sub, newsPanel, overlay);
        overlay.toFront();


        GlobeControls controls = new GlobeControls(
                sub,
                globe.getGlobeGroup(),
                overlay,
                countryFinder,
                EARTH_RADIUS
        );
        controls.attach();

        /* TODO
            Will be fetching news and use embedding techniques and cluster them by tags,location, and topic.
            Finally we will also be create a database to store each article with their location with 1 day expire time.
            We will most probably make a timeline because we just want to show the latest (last 2 hours or so)
            and users can access even earlier news using the timeline.
         */
        var list = List.of(new Article("Trump says he's terminating trade talks with Canada over TV ad about tariffs",
                "ABC-NEWS",
                "https://abcnews.go.com/Politics/trump-terminating-trade-talks-canada-tv-ad-tariffs/story?id=126821528",
                System.currentTimeMillis()));
        hotspotManager.spawnHotspot(52.5200, 13.4050, list);
        hotspotManager.spawnHotspot(40.7128, -74.0060, list);
        hotspotManager.spawnHotspot(41.0082, 28.9784, list);
        hotspotManager.spawnHotspot(51.509865, -0.118092, list);
    }

    public void bindSubSceneTo(javafx.scene.Scene scene) {
        scene.widthProperty().addListener((o, ov, nv) -> sub.setWidth(nv.doubleValue()));
        scene.heightProperty().addListener((o, ov, nv) -> sub.setHeight(nv.doubleValue()));
    }
}

