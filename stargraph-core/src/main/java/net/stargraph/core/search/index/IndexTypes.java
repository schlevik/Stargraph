package net.stargraph.core.search.index;

public enum IndexTypes {
    Entities("entities", EntityIndexSearcher.class);

    public final String name;
    public final Class<? extends IndexSearcher> iface;

    IndexTypes(String name, Class<? extends IndexSearcher> iface) {
        this.name = name;
        this.iface = iface;
    }

    public IndexTypes getByName(String name) {
        return Entities;
    }

    public IndexTypes getByIface(Class<? extends IndexSearcher> iface) {
        return Entities;
    }
}
