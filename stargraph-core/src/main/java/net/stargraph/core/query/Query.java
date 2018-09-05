package net.stargraph.core.query;

import net.stargraph.core.query.response.QueryResponse;

import java.util.*;

public class Query {
    private String input;
    private Map<Class<? extends AbstractQueryResolver>, QueryResponse> responses;

    public Query(String input) {
        this.input = input;
        this.responses = new LinkedHashMap<>();
    }

    public String getInput() {
        return input;
    }

    public Collection<QueryResponse> getResponses() {
        return Collections.unmodifiableCollection(responses.values());
    }
    public QueryResponse getAny() {
        return responses.values().stream().findFirst().get();
    }

    public QueryResponse getResponse(Class<? extends AbstractQueryResolver> source) {
        return responses.getOrDefault(source, null);
    }

    public void appendResponse(QueryResponse response) {
        responses.put(response.getSource().getClass(), response);
    }
}
