module com.example.newsmapdumen {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires com.almasb.fxgl.all;
    requires org.json;
    requires java.desktop;

    opens newsmap to javafx.fxml;
    exports newsmap;
    exports newsmap.model;
    opens newsmap.model to javafx.fxml;
    exports newsmap.hotspot;
    opens newsmap.hotspot to javafx.fxml;
    exports newsmap.ui;
    opens newsmap.ui to javafx.fxml;
}