package net.stargraph.core.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.stargraph.model.IndexID;

public interface ObjectSerializer {

    ObjectMapper createMapper(IndexID indexID);
}
