package net.stargraph.core.search.database;

import net.stargraph.StarGraphException;

public interface DatabaseResult {
    default <T extends DatabaseResult> T get(Class<T> cls) {
        try {
            return (T) this;
        } catch (ClassCastException e) {
            throw new StarGraphException(String.format("Cannot express query of type %s as %s (yet)!",
                    this.getClass().getCanonicalName(), cls.getClass().getCanonicalName()));
        }
    }
}
