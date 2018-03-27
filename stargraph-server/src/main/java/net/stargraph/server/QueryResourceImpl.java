package net.stargraph.server;

/*-
 * ==========================License-Start=============================
 * stargraph-server
 * --------------------------------------------------------------------
 * Copyright (C) 2017 Lambda^3
 * --------------------------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * ==========================License-End===============================
 */

import net.stargraph.core.Stargraph;
import net.stargraph.core.query.Query;
import net.stargraph.core.query.QueryEngine;
import net.stargraph.core.query.response.*;
import net.stargraph.model.LabeledEntity;
import net.stargraph.rest.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import javax.ws.rs.core.Response;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class QueryResourceImpl implements QueryResource {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker = MarkerFactory.getMarker("server");
    private Stargraph core;
    private Map<String, QueryEngine> engines;

    public QueryResourceImpl(Stargraph core) {
        this.core = Objects.requireNonNull(core);
        this.engines = new ConcurrentHashMap<>();
    }

    @Override
    public Response query(String id, String q) {
        try {
            if (core.hasKB(id)) {
                QueryEngine engine = engines.computeIfAbsent(id, (k) -> new QueryEngine(k, core));
                Query result = engine.query(q);
                return Response.status(Response.Status.OK).entity(buildUserResponse(result)).build();
            }
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (Exception e) {
            logger.error(marker, "Query execution failed: '{}' on '{}'", q, id, e);
        }
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }

    private UserResponse buildUserResponse(Query result) {

        if (result.getResponses().isEmpty()) {
            return new NoUserResponse(result.getInput());
        }
        UserResponse userResponse = new UserResponse(result.getInput());

        for (QueryResponse responseEntry : result.getResponses()) {
            String source = responseEntry.getSource().getName();

            if (responseEntry instanceof AnswerSetResponse) {
                AnswerSetResponse answerSet = (AnswerSetResponse) responseEntry;
                SchemaAgnosticUserResponse response =
                        new SchemaAgnosticUserResponse(source, answerSet.getSparqlQuery());

                List<SchemaAgnosticUserResponse.EntityEntry> answers = answerSet.getEntityAnswer().stream()
                        .map(a -> new SchemaAgnosticUserResponse.EntityEntry(a.getId(), a.getValue())).collect(Collectors.toList());

                response.setAnswers(answers);

                final Map<String, List<SchemaAgnosticUserResponse.EntityEntry>> mappings = new HashMap<>();
                answerSet.getMappings().forEach((modelBinding, scoreList) -> {
                    List<SchemaAgnosticUserResponse.EntityEntry> entries = scoreList.stream()
                            .map(s -> new SchemaAgnosticUserResponse.EntityEntry(s.getRankableView().getId(),
                                    s.getRankableView().getValue(), s.getValue()))
                            .collect(Collectors.toList());
                    mappings.computeIfAbsent(modelBinding.getTerm(), (term) -> new ArrayList<>()).addAll(entries);
                });

                response.setMappings(mappings);
                userResponse.addResponse(response);
            } else if (responseEntry instanceof SPARQLSelectResponse) {
                SPARQLSelectResponse selectResponse = (SPARQLSelectResponse) responseEntry;
                final Map<String, List<String>> bindings = new LinkedHashMap<>();
                selectResponse.getBindings().forEach((key, value) -> {
                    List<String> entityEntryList = value.stream()
                            .map(LabeledEntity::getId)
                            .collect(Collectors.toList());
                    bindings.put(key, entityEntryList);
                });

                SPARQLSelectUserResponse response =
                        new SPARQLSelectUserResponse(source);

                response.setBindings(bindings);
//                return response;
                userResponse.addResponse(response);
            } else if (responseEntry instanceof TextResponse) {
                TextResponse textResponse = (TextResponse) responseEntry;
                TextUserResponse response =
                        new TextUserResponse(source);
                response.setAnswers(textResponse.getTextAnswer());

                userResponse.addResponse(response);

            } else {
                throw new UnsupportedOperationException("Can't create REST response");
            }
        }
        return userResponse;
    }
}
