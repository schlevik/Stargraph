package net.stargraph.core.search.database;


public interface RDFGraphDatabase extends Database<SparqlQuery, SparqlResult> {
    @Override
    default DBType getType() {
        return DBType.Graph;
    }

}
