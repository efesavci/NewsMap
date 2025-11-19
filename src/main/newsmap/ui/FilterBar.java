package main.newsmap.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import main.newsmap.hotspot.HotspotManager;
import main.newsmap.model.HotspotCategory;

public class FilterBar extends HBox {
    private final ToggleGroup toggleGroup = new ToggleGroup();

    public FilterBar(HotspotManager hotspotManager) {
        setSpacing(8);
        setPadding(new Insets(10));
        setAlignment(Pos.CENTER_LEFT);
        setStyle("""
        -fx-background-color: rgba(15,23,42,0.85);
        -fx-background-radius: 12;
        -fx-border-color: rgba(148,163,184,0.4);
        -fx-border-radius: 12;
        """);

        ToggleButton allButton = createToggle("All");
        allButton.setSelected(true);
        allButton.setOnAction(e -> hotspotManager.applyCategoryFilter(null));
        getChildren().add(allButton);

        for (HotspotCategory category : HotspotCategory.values()) {
            ToggleButton toggle = createToggle(category.displayName());
            toggle.setOnAction(e -> hotspotManager.applyCategoryFilter(category));
            getChildren().add(toggle);
        }
    }

    private ToggleButton createToggle(String text) {
        ToggleButton toggle = new ToggleButton(text);
        toggle.setToggleGroup(toggleGroup);
        toggle.setStyle("""
        -fx-background-color: rgba(30,41,59,0.8);
        -fx-text-fill: #e2e8f0;
        -fx-border-color: rgba(148,163,184,0.6);
        -fx-border-radius: 8;
        -fx-background-radius: 8;
        -fx-padding: 6 12;
        """);
        toggle.setOnMouseEntered(e -> toggle.setOpacity(0.9));
        toggle.setOnMouseExited(e -> toggle.setOpacity(1.0));
        return toggle;
    }
}
