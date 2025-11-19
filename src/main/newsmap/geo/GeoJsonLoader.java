package main.newsmap.geo;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class GeoJsonLoader {
    public static JSONArray loadFromClasspath(String resource) {
        try (InputStream is = GeoJsonLoader.class.getResourceAsStream(resource)) {
            if (is == null) throw new RuntimeException("Resource not found: " + resource);
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(json);
            return root.getJSONArray("features");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
