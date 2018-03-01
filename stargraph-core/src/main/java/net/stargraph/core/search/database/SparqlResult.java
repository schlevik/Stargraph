package net.stargraph.core.search.database;

import net.stargraph.model.LabeledEntity;

import java.util.LinkedHashMap;
import java.util.List;

public class SparqlResult extends LinkedHashMap<String, List<LabeledEntity>> implements DatabaseResult {
}
