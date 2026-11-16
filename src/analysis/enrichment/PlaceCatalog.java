package analysis.enrichment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Geographic coordinates come from checked-in records, never model output. */
public final class PlaceCatalog {
    public record Place(String name, double lat, double lon, String scope) {}
    private final Map<String,Place> countries = new HashMap<>();
    private final Map<String,Place> cities = new HashMap<>();
    public PlaceCatalog() throws Exception {
        try (var input = PlaceCatalog.class.getResourceAsStream("/world.json")) {
            if (input == null) throw new IllegalStateException("Country gazetteer unavailable");
            for (var feature : new ObjectMapper().readTree(input).path("features")) {
                var p = feature.path("properties");
                if (!p.path("label_x").isNumber() || !p.path("label_y").isNumber()) continue;
                var place = new Place(p.path("name_long").asText(),p.path("label_y").asDouble(),p.path("label_x").asDouble(),"COUNTRY");
                for (String field : new String[]{"iso_a2","iso_a2_eh","iso_a3","iso_a3_eh","adm0_a3","name","name_long"}) {
                    String value=p.path(field).asText();
                    if (!value.isBlank() && !value.equals("-99")) countries.put(normalize(value),place);
                }
            }
        }
        city("Paris","FR",48.8566,2.3522); city("London","GB",51.5074,-0.1278);
        city("Berlin","DE",52.5200,13.4050); city("Kyiv","UA",50.4501,30.5234);
        city("Moscow","RU",55.7558,37.6173); city("Tehran","IR",35.6892,51.3890);
        city("Tokyo","JP",35.6762,139.6503); city("New Delhi","IN",28.6139,77.2090);
        city("Bangkok","TH",13.7563,100.5018); city("Doha","QA",25.2854,51.5310);
        city("Barcelona","ES",41.3874,2.1686); city("Madrid","ES",40.4168,-3.7038);
        city("Valencia","ES",39.4699,-0.3763); city("San Francisco","US",37.7749,-122.4194);
        city("Washington DC","US",38.8951,-77.0364); city("New York","US",40.7128,-74.0060);
    }
    private static String normalize(String text) { return text.trim().toLowerCase(Locale.ROOT); }
    private void city(String name,String code,double lat,double lon) {
        var country=countries.get(normalize(code));
        if (country!=null) cities.put(normalize(name)+":"+normalize(code),new Place(name+", "+country.name(),lat,lon,"CITY"));
    }
    public Place lookup(String name,String code,String scope) {
        if (!scope.equals("CITY") && !scope.equals("COUNTRY")) return null;
        if (scope.equals("CITY")) {
            var city=cities.get(normalize(name)+":"+normalize(code));
            if (city!=null) return city;
        }
        return countries.get(normalize(code)); // Missing city records fall back explicitly to country-level.
    }
}
