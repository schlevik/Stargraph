package net.stargraph.core.search.database;


public class SparqlQuery implements DatabaseQuery<String> {
    private String query;

    public SparqlQuery(String query) {
        this.query = query;
    }

    @Override
    public DBType getType() {
        return DBType.Graph;
    }

    @Override
    public String getQuery() {
        return this.query;
    }
}
