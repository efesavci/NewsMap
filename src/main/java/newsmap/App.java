package newsmap;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import newsmap.ui.GlobePane;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        GlobePane root = new GlobePane();
        Scene scene = new Scene(root, 1200, 800);
        stage.setTitle("News Globe (borders + pulses)");
        stage.setScene(scene);
        stage.show();

        root.bindSubSceneTo(scene);
    }

    public static void main(String[] args) { launch(args); }
}

