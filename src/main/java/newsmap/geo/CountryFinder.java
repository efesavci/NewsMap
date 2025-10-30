package newsmap.geo;

import org.json.JSONArray;
import org.json.JSONObject;

public class CountryFinder {
    private final JSONArray features;

    public CountryFinder(JSONArray features) { this.features = features; }

    public String findCountry(double lat, double lon) {
        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.getJSONObject(i);
            JSONObject geometry = feature.optJSONObject("geometry");
            if (geometry == null) continue;
            JSONObject properties = feature.optJSONObject("properties");
            String name = properties != null ? properties.optString("name_en", "Unknown") : "Unknown";
            String type = geometry.optString("type","");
            JSONArray coordinates = geometry.optJSONArray("coordinates");
            if (coordinates == null) continue;

            if (type.equals("Polygon")) {
                if (polygonContains(coordinates, lat, lon)) return name;
            } else if (type.equals("MultiPolygon")) {
                for (int j = 0; j < coordinates.length(); j++) {
                    if (polygonContains(coordinates.getJSONArray(j), lat, lon)) return name;
                }
            }
        }
        return null;
    }

    private boolean polygonContains(JSONArray polygonCoords, double lat, double lon) {
        JSONArray ring = polygonCoords.getJSONArray(0);
        boolean inside = false;
        for (int i = 0, j = ring.length()-1; i < ring.length(); j = i++) {
            JSONArray pi = ring.getJSONArray(i), pj = ring.getJSONArray(j);
            double xi = pi.getDouble(0), yi = pi.getDouble(1);
            double xj = pj.getDouble(0), yj = pj.getDouble(1);
            boolean intersect = ((yi > lat) != (yj > lat)) &&
                    (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }

    public JSONArray getFeatures() { return features; }

}
