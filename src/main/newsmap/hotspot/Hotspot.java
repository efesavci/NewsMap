package main.newsmap.hotspot;

import javafx.scene.Group;
import main.newsmap.model.Article;
import main.newsmap.model.HotspotCategory;

import java.util.List;

public record Hotspot(double latDeg,
                      double lonDeg,
                      List<Article> articles,
                      HotspotCategory category,
                      Group node) {

}

