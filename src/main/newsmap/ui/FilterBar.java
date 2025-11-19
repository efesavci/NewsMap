package main.newsmap.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import main.newsmap.hotspot.HotspotManager;
import main.newsmap.model.HotspotCategory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FilterBar extends VBox {

    private final Set<HotspotCategory> activeFilters = new HashSet<>();
    private final Map<HotspotCategory, ToggleButton> buttonMap = new HashMap<>();
    private final ToggleButton allButton;

    public FilterBar(HotspotManager hotspotManager) {
        setSpacing(6);
        setPadding(new Insets(12));
        setAlignment(Pos.TOP_LEFT);
        setPrefWidth(140);
        setMaxWidth(140);
        setPrefHeight(200);
        setMaxHeight(200);

        setStyle("""
        -fx-background-color: rgba(15,23,42,0.85);
        -fx-background-radius: 12;
        -fx-border-color: rgba(148,163,184,0.4);
        -fx-border-radius: 12;
    """);

        // ALL button
        allButton = createToggle("All");
        allButton.setOnAction(e -> {
            activeFilters.clear();                  // remove all category filters
            hotspotManager.applyCategoryFilters(activeFilters);
            updateButtonStyles();
        });
        getChildren().add(allButton);

        // CATEGORY BUTTONS
        for (HotspotCategory category : HotspotCategory.values()) {
            ToggleButton toggle = createToggle(category.displayName());
            buttonMap.put(category, toggle);

            toggle.setOnAction(e -> {
                if (toggle.isSelected())
                    activeFilters.add(category);
                else
                    activeFilters.remove(category);

                hotspotManager.applyCategoryFilters(activeFilters);
                updateButtonStyles();
            });

            getChildren().add(toggle);
        }
    }
    private void updateButtonStyles() {
        // All button highlighted only when NOTHING is selected
        if (activeFilters.isEmpty()) {
            allButton.setStyle("""
            -fx-background-color: rgba(59,130,246,0.4);
            -fx-text-fill: white;
            -fx-border-color: rgba(147,197,253,0.8);
            -fx-border-radius: 8;
            -fx-background-radius: 8;
        """);
        } else {
            resetButtonStyle(allButton);
        }

        // Category buttons
        for (var entry : buttonMap.entrySet()) {
            HotspotCategory category = entry.getKey();
            ToggleButton button = entry.getValue();

            if (activeFilters.contains(category)) {
                button.setStyle("""
                -fx-background-color: rgba(59,130,246,0.4);
                -fx-text-fill: white;
                -fx-border-color: rgba(147,197,253,0.8);
                -fx-border-radius: 8;
                -fx-background-radius: 8;
            """);
            } else {
                resetButtonStyle(button);
            }
        }
    }private void resetButtonStyle(ToggleButton button) {
        button.setStyle("""
        -fx-background-color: rgba(30,41,59,0.8);
        -fx-text-fill: #e2e8f0;
        -fx-border-color: rgba(148,163,184,0.6);
        -fx-border-radius: 8;
        -fx-background-radius: 8;
    """);
    }




    private ToggleButton createToggle(String text) {
        ToggleButton toggle = new ToggleButton(text);

        toggle.setMaxWidth(Double.MAX_VALUE);
        toggle.setPrefWidth(80);
        toggle.setStyle("""
        -fx-background-color: rgba(30,41,59,0.8);
        -fx-text-fill: #e2e8f0;
        -fx-border-color: rgba(148,163,184,0.6);
        -fx-border-radius: 8;
        -fx-background-radius: 8;
        -fx-padding: 6 12;
    """);

        toggle.selectedProperty().addListener((obs, oldV, selected) -> {
            if (selected) {
                toggle.setStyle("""
            -fx-background-color: rgba(59,130,246,0.4);
            -fx-text-fill: white;
            -fx-border-color: rgba(147,197,253,0.8);
            -fx-border-radius: 8;
            -fx-background-radius: 8;
            -fx-padding: 6 12;
        """);
            } else {
                toggle.setStyle("""
            -fx-background-color: rgba(30,41,59,0.8);
            -fx-text-fill: #e2e8f0;
            -fx-border-color: rgba(148,163,184,0.6);
            -fx-border-radius: 8;
            -fx-background-radius: 8;
            -fx-padding: 6 12;
        """);
            }
        });
        toggle.setOnMouseEntered(e -> toggle.setOpacity(0.9));
        toggle.setOnMouseExited(e -> toggle.setOpacity(1.0));
        return toggle;
    }
}
