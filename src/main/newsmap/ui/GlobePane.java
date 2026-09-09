package main.newsmap.ui;

import javafx.geometry.Insets;
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
import main.newsmap.model.HotspotCategory;

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

        FilterBar filterBar = new FilterBar(hotspotManager);
        StackPane.setAlignment(filterBar, Pos.TOP_LEFT);
        StackPane.setMargin(filterBar, new Insets(20));


        this.getChildren().addAll(sub, newsPanel, overlay, filterBar);
        overlay.toFront();


        GlobeControls controls = new GlobeControls(
                sub,
                globe.getGlobeGroup(),
                overlay,
                countryFinder,
                EARTH_RADIUS
        );
        controls.attach();

        /*
         * Load real clustered news hotspots from the Python pipeline outputs.
         */
        var dynamicHotspots = analysis.ClusterInspector.generateHotspots();
        for (var dto : dynamicHotspots) {
            // Convert DTOs back to JavaFX model for the UI
            var articles = dto.articles().stream()
                .map(a -> new main.newsmap.model.Article(a.title(), a.source(), a.url(), a.timestamp()))
                .toList();

            HotspotCategory cat = HotspotCategory.valueOf(dto.category());
            hotspotManager.spawnHotspot(dto.lat(), dto.lon(), articles, cat, dto.location());
        }
    }

    public void bindSubSceneTo(javafx.scene.Scene scene) {
        scene.widthProperty().addListener((o, ov, nv) -> sub.setWidth(nv.doubleValue()));
        scene.heightProperty().addListener((o, ov, nv) -> sub.setHeight(nv.doubleValue()));
    }
}
