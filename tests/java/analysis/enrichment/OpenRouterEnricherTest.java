package analysis.enrichment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class OpenRouterEnricherTest {
    @TempDir Path directory;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final String TITLE="Flooding in Valencia, Spain damages homes";
    private static final Map<String,String> CONFIG=Map.of("OPENROUTER_API_KEY","synthetic-test-key","NEWSMAP_ENRICHMENT_MAX_EVENTS_PER_RUN","5");
    ObjectNode event() throws Exception {
        var event=JSON.createObjectNode(); event.put("id","evt_test");
        event.putArray("articles").addObject().put("articleId","a-1").put("title",TITLE).put("publishTime","2026-11-05T12:00:00Z");
        return event;
    }
    ObjectNode result() throws Exception {
        return (ObjectNode)JSON.readTree("""
            {"category":"OTHER","tags":["flooding","extreme-weather"],"scope":"CITY","placeName":"Valencia","countryCode":"ES",
             "evidence":[{"articleId":"a-1","quote":"Flooding in Valencia, Spain"}]}
            """);
    }
    String reply(ObjectNode result) throws Exception {
        var response=JSON.createObjectNode(); response.putObject("usage").put("cost",0.0001);
        var choice=response.putArray("choices").addObject(); choice.put("finish_reason","stop");
        choice.putObject("message").put("content",result.toString());
        return response.toString();
    }
    @Test void validatedResultUsesGazetteerAndCacheSurvivesRestart() throws Exception {
        var store=new EnrichmentStore(directory.resolve("enrichment.db")); var calls=new AtomicInteger();
        OpenRouterEnricher.Transport transport=request->{ calls.incrementAndGet();
            assertEquals("https://openrouter.ai/api/v1/chat/completions",request.uri().toString());
            assertTrue(request.headers().firstValue("Authorization").isPresent());
            assertFalse(request.uri().toString().contains("synthetic-test-key"));
            return new OpenRouterEnricher.Reply(200,reply(result())); };
        var first=event(); assertTrue(new OpenRouterEnricher(CONFIG,store,transport).enrich(first));
        assertEquals(39.4699,first.path("lat").asDouble(),0.0001);
        assertEquals("CITY",first.path("locationScope").asText()); assertTrue(first.path("locationConfidence").isNull());
        assertEquals("flooding",first.path("tags").get(0).asText());
        var second=event(); assertTrue(new OpenRouterEnricher(CONFIG,new EnrichmentStore(directory.resolve("enrichment.db")),transport).enrich(second));
        assertEquals(1,calls.get()); assertEquals(0.0001,store.spent(),0.000001);
    }
    @Test void rejectsFabricatedEvidenceAndStopsFurtherCallsForThePass() throws Exception {
        var store=new EnrichmentStore(directory.resolve("invalid.db")); var calls=new AtomicInteger();
        var invalid=result(); ((ObjectNode)invalid.path("evidence").get(0)).put("quote","Invented quotation in Paris");
        var enricher=new OpenRouterEnricher(CONFIG,store,request->{calls.incrementAndGet();return new OpenRouterEnricher.Reply(200,reply(invalid));});
        var event=event(); event.put("location","Existing location");
        assertFalse(enricher.enrich(event)); assertEquals("Existing location",event.path("location").asText());
        assertFalse(enricher.enrich(event())); assertEquals(1,calls.get()); assertTrue(store.cached("invalid").isEmpty());
    }
    @Test void timeoutKeepsReservationAndBudgetPersists() throws Exception {
        var path=directory.resolve("budget.db"); var store=new EnrichmentStore(path);
        String id=store.reserve("one",0.6,1.0); store.finish(id,"one",null,null);
        var reopened=new EnrichmentStore(path); assertEquals(0.6,reopened.spent(),0.00001);
        assertThrows(IllegalStateException.class,()->reopened.reserve("two",0.6,1.0));
        assertEquals(0.6,reopened.spent(),0.00001);
    }
    @Test void missingKeyMakesNoRequestsAndCountryFallbackIsExplicit() throws Exception {
        var calls=new AtomicInteger();
        var enricher=new OpenRouterEnricher(Map.of(),new EnrichmentStore(directory.resolve("nokey.db")),request->{calls.incrementAndGet();return null;});
        assertFalse(enricher.enrich(event())); assertEquals(0,calls.get());
        var place=new PlaceCatalog().lookup("Unlisted city","ES","CITY"); assertEquals("COUNTRY",place.scope());
        assertEquals("Spain",place.name());
        assertEquals("CITY",new PlaceCatalog().lookup("Paris","FR","CITY").scope());
        assertEquals("Norway",new PlaceCatalog().lookup("Norway","NO","COUNTRY").name());
    }
    @Test void budgetAndRunLimitStopRequestsBeforeTransport() throws Exception {
        var calls=new AtomicInteger();
        OpenRouterEnricher.Transport transport=request->{calls.incrementAndGet();return new OpenRouterEnricher.Reply(200,reply(result()));};
        var tiny=new java.util.HashMap<>(CONFIG); tiny.put("NEWSMAP_ENRICHMENT_BUDGET_USD","0.000000001");
        assertFalse(new OpenRouterEnricher(tiny,new EnrichmentStore(directory.resolve("tiny.db")),transport).enrich(event()));
        assertEquals(0,calls.get());
        var one=new java.util.HashMap<>(CONFIG); one.put("NEWSMAP_ENRICHMENT_MAX_EVENTS_PER_RUN","1");
        var enricher=new OpenRouterEnricher(one,new EnrichmentStore(directory.resolve("one.db")),transport);
        assertTrue(enricher.enrich(event()));
        var other=event(); other.put("id","evt_other");
        assertFalse(enricher.enrich(other)); assertEquals(1,calls.get());
    }

    @Test void schemaAndPlaceEvidenceAreValidated() throws Exception {
        var enricher=new OpenRouterEnricher(CONFIG,new EnrichmentStore(directory.resolve("schema.db")),request->null);
        var input=(ArrayNode)event().path("articles");
        var missing=result(); missing.remove("placeName"); missing.put("other","Valencia");
        assertThrows(IllegalArgumentException.class,()->enricher.validate(missing,input));
        var wrong=result(); wrong.put("placeName","Paris").put("countryCode","FR");
        assertThrows(IllegalArgumentException.class,()->enricher.validate(wrong,input));
        var global=result(); global.put("scope","GLOBAL").putNull("placeName").putNull("countryCode");
        assertNotNull(enricher.validate(global,input));
    }
}
