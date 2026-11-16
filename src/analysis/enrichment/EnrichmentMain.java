package analysis.enrichment;

/** Re-enrich existing events without fetching news or starting the web server. */
public final class EnrichmentMain {
    public static void main(String[] args) {
        if (!OpenRouterEnricher.configured()) {
            System.err.println("Add OPENROUTER_API_KEY to deploy/openrouter.env.local before running enrichment.");
            System.exit(2);
        }
        analysis.EventLocationEnricher.enrichAndSave();
    }
}
