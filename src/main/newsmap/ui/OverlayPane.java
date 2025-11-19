package main.newsmap.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.geometry.Point2D;

public class OverlayPane extends Pane {
    public final static Label countryLabel = new Label();

    public OverlayPane(StackPane root) {
        setMouseTransparent(true);
        setPickOnBounds(false);

        prefWidthProperty().bind(root.widthProperty());
        prefHeightProperty().bind(root.heightProperty());

        countryLabel.setVisible(false);
        countryLabel.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: white; -fx-padding: 3;");
        getChildren().add(countryLabel);
    }

    public void showCountryAtScreen(String text, double screenX, double screenY) {

        Point2D local = screenToLocal(screenX, screenY);
        countryLabel.relocate(local.getX() + 10, local.getY() - 10);
        countryLabel.setText(text);
        countryLabel.setVisible(true);
    }

    public void hideCountry() { countryLabel.setVisible(false); }

    public static Label getCountryLabel() { return countryLabel;}
}

