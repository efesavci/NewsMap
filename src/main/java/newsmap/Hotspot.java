package newsmap;

import javafx.scene.Group;

import java.util.List;

public class Hotspot {
    public final double latDeg;
    public final double lonDeg;
    public final List<Article> articles;
    public final Group node;

    public Hotspot(double latDeg, double lonDeg, List<Article> articles, Group node) {
        this.latDeg = latDeg;
        this.lonDeg = lonDeg;
        this.articles = articles;
        this.node = node;
    }

}
