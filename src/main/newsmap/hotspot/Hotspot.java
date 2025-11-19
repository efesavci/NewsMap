package main.newsmap.hotspot;

import javafx.scene.Group;
import main.newsmap.model.Article;
import java.util.List;

public record Hotspot(double latDeg, double lonDeg, List<Article> articles, Group node) {

}

