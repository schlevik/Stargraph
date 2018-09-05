package net.stargraph.rest;

import java.io.Serializable;
import java.util.Objects;

public abstract class PartialResponse implements Serializable {
    private final String source;

    public PartialResponse(String source) {
        this.source = Objects.requireNonNull(source);
    }

    public String getSource() {
        return this.source;
    }

    public static class EntityEntry {
        public String id;
        public String value;
        public double score;

        public EntityEntry(String id, String value) {
            this(id, value, 1);
        }

        public EntityEntry(String id, String value, double score) {
            this.id = id;
            this.value = value;
            this.score = score;
        }
    }
}
