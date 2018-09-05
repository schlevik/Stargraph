package net.stargraph.core.search.database;


import java.util.Arrays;

public enum DBType {
    Graph,

    Document,

    Relational;

    //etc
    public static boolean contains(String name) {
        return Arrays.stream(DBType.values()).anyMatch(t -> t.name().equals(name));
    }
}
