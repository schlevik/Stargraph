package net.stargraph.core.query.sparql;

import net.stargraph.core.KnowledgeBase;
import net.stargraph.core.query.AbstractQueryResolver;
import net.stargraph.core.query.Analyzers;
import net.stargraph.core.query.Query;
import net.stargraph.core.query.response.SPARQLSelectResponse;
import net.stargraph.core.search.database.SparqlQuery;
import net.stargraph.core.search.database.SparqlResult;
import net.stargraph.query.InteractionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class SparqlQueryResolver extends AbstractQueryResolver {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker = MarkerFactory.getMarker("sparql-query");

    public SparqlQueryResolver(KnowledgeBase knowledgeBase, Analyzers analyzers) {
        super(knowledgeBase, analyzers);
    }

    @Override
    public void resolveQuery(Query query) {
        String input = query.getInput();
        if (eligible(input)) {
            try {
                SparqlResult result = getKnowledgeBase().queryDatabase(new SparqlQuery(input)).get(SparqlResult.class);
                query.appendResponse(new SPARQLSelectResponse(InteractionMode.SPARQL, input, result));
            } catch (Exception e) {
                logger.info("Got exception when perfoming SPARQL query on '{}' malformed: '{}': {}",
                        getKnowledgeBase().getName(), input, e);
            }
        }
    }

    private boolean eligible(String queryString) {
        return queryString.contains("SELECT") || queryString.contains("ASK") || queryString.contains("CONSTRUCT");
    }
}