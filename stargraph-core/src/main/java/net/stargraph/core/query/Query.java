package net.stargraph.core.query;

import java.util.*;

public class Query {
    private String input;
    private List<QueryResponse> responses;

    public Query(String input) {
        this.input = input;
        this.responses = new ArrayList<>();
    }

    public String getInput() {
        return input;
    }

    public List<QueryResponse> getResponses() {
        return Collections.unmodifiableList(responses);
    }


    public void appendResponse(QueryResponse response) {
        responses.add(response);
    }
}
