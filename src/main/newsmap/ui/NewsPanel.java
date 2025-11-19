package main.newsmap.ui;

import javafx.animation.*;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import main.newsmap.hotspot.Hotspot;
import main.newsmap.model.Article;

import java.util.List;

public class NewsPanel extends VBox {
    private boolean visibleSlide = false;

    public NewsPanel() {
        setStyle("""
      -fx-background-color: rgba(15,23,42,0.9);
      -fx-padding: 16;
      -fx-spacing: 12;
      -fx-text-fill: #f8fafc;
    """);
        setPrefWidth(300); setMinWidth(300); setMaxWidth(300);
    }

    public void show(Hotspot hs, Runnable onClose) {
        getChildren().clear();

        Label header = new Label("Breaking near "+ hs.location());
        header.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 16px; -fx-font-weight: bold;");
        getChildren().add(header);

        int limit = Math.min(5, hs.articles().size());
        for (int i = 0; i < limit; i++) {
            var a = hs.articles().get(i);
            VBox card = new VBox();
            card.setStyle("""
        -fx-background-color: rgba(30,41,59,0.6);
        -fx-padding: 8;
        -fx-background-radius: 8;
        -fx-spacing: 4;
      """);

            Label titleLbl = new Label(a.title());
            titleLbl.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 14px; -fx-font-weight: bold;");
            Label sourceLbl = new Label(a.source());
            sourceLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
            Hyperlink linkLbl = new Hyperlink(a.url());
            linkLbl.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 12px;");
            linkLbl.setOnAction(e -> onOpen(a.url()));

            card.getChildren().addAll(titleLbl, sourceLbl, linkLbl);
            getChildren().add(card);
        }

        Button close = new Button("Close");
        close.setStyle("""
      -fx-background-color: #1e293b; -fx-text-fill: #f8fafc;
      -fx-background-radius: 6; -fx-padding: 6 10; -fx-font-size: 12px;
    """);
        close.setOnAction(e -> { hide(); if (onClose != null) onClose.run(); });

        getChildren().add(close);
        slideIn();
    }

    private void onOpen(String url) {

        try { java.awt.Desktop.getDesktop().browse(new java.net.URI(url)); } catch (Exception ignored) {}
    }

    public void hide() { if (visibleSlide) slideOut(); }

    private void slideIn() {
        visibleSlide = true;
        play(0, 0);
    }
    private void slideOut() {
        visibleSlide = false;
        play(300, 250);
    }
    private void play(double toX, double ms) {
        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(ms == 0 ? 250 : ms),
                        new KeyValue(translateXProperty(), toX, Interpolator.EASE_BOTH)));
        t.play();
    }
}

