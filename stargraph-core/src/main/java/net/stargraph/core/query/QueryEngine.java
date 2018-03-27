package net.stargraph.core.query;

/*-
 * ==========================License-Start=============================
 * stargraph-knowledgeBase
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

import net.stargraph.StarGraphException;
import net.stargraph.core.KnowledgeBase;
import net.stargraph.core.Stargraph;
import net.stargraph.core.query.nli.NLIQueryResolver;
import net.stargraph.core.query.passage.PassageQueryResolver;
import net.stargraph.core.query.response.AnswerSetResponse;
import net.stargraph.core.query.response.NoResponse;
import net.stargraph.core.query.response.SPARQLSelectResponse;
import net.stargraph.core.search.database.SparqlQuery;
import net.stargraph.core.search.database.SparqlResult;
import net.stargraph.model.LabeledEntity;
import net.stargraph.query.InteractionMode;
import net.stargraph.query.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.*;

import static net.stargraph.query.InteractionMode.*;

/**
 * This class holds and ties together all existing {@link AbstractQueryResolver}s.
 * <p>
 * The input query is passed to all registered {@link AbstractQueryResolver}s which can append their answer to the query,
 * if they have one.
 */
public final class QueryEngine {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker = MarkerFactory.getMarker("query");

    private KnowledgeBase knowledgeBase;
    private InteractionModeSelector modeSelector;

    private List<AbstractQueryResolver> queryResolvers;

    public QueryEngine(String kbName, Stargraph stargraph) {
        this.knowledgeBase = stargraph.getKnowledgeBase(Objects.requireNonNull(kbName));
        Analyzers analyzers = new Analyzers(stargraph.getConfig().getMainConfig());
        Language language = knowledgeBase.getLanguage();
        queryResolvers = new LinkedList<>();
        queryResolvers.add(new PassageQueryResolver(knowledgeBase, analyzers));
        queryResolvers.add(new NLIQueryResolver(knowledgeBase, analyzers));
        this.modeSelector = new InteractionModeSelector(stargraph.getConfig().getMainConfig(), language);
    }

    public List<QueryResponse> query(String input) {
        //final InteractionMode mode = modeSelector.detect(input);
        Query query = new Query(input);


        long startTime = System.currentTimeMillis();
        try {
            queryResolvers.forEach(qr -> qr.resolveQuery(query));
            if (query.getResponses().isEmpty()) {
                query.appendResponse(new NoResponse(NLI, input));
            }
            return query.getResponses();

        } catch (Exception e) {
            logger.error(marker, "Query Error '{}'", input, e);
            throw new StarGraphException("Query Error", e);
        } finally {
            long millis = System.currentTimeMillis() - startTime;
            logger.info(marker, "Query Engine took {}s Response: {}", millis / 1000.0, query.getResponses());
        }
    }

    private QueryResponse sparqlQuery(String userQuery) {
        SparqlResult vars = knowledgeBase.queryDatabase(new SparqlQuery(userQuery)).get(SparqlResult.class);
        if (!vars.isEmpty()) {
            return new SPARQLSelectResponse(SPARQL, userQuery, vars);
        }
        return new NoResponse(SPARQL, userQuery);
    }


//    private InstanceEntity resolveInstance(String instanceTerm) {
//        EntityIndexSearcher searcher = getIndexSearcher(EntityIndexSearcher.class);
//        ModifiableSearchParams searchParams = ModifiableSearchParams.create().term(instanceTerm);
//        ModifiableRankParams rankParams = ParamsBuilder.levenshtein(); // threshold defaults to auto
//        Scores<InstanceEntity> scores = searcher.instanceSearch(searchParams, rankParams);
//        return scores.get(0).getEntry();
//    }

    private QueryResponse entitySimilarityQuery(String userQuery, Language language) {

//        EntityQueryBuilder queryBuilder = new EntityQueryBuilder();
//        EntityQuery query = queryBuilder.parse(userQuery, ENTITY_SIMILARITY);
//        InstanceEntity instance = resolveInstance(query.getCoreEntity());
//
//        Set<LabeledEntity> entities = new HashSet<>();
//        // \TODO Call mltSearch here
//        // mltSearch()
//        // mltSearch will return Set<LabeledEntity>
//
//        if (!entities.isEmpty()) {
//            AnswerSetResponse answerSet = new AnswerSetResponse(ENTITY_SIMILARITY, userQuery);
//            // \TODO define mappings for name entity
//            // answerSet.setMappings();
//            // answerSet.setMappings(); ->
//            answerSet.setEntityAnswer(new ArrayList<>(entities));
//            return answerSet;
//        }

        return new NoResponse(NLI, userQuery);
    }

    public QueryResponse definitionQuery(String userQuery, Language language) {

//        EntityQueryBuilder queryBuilder = new EntityQueryBuilder();
//        EntityQuery query = queryBuilder.parse(userQuery, DEFINITION);
//        InstanceEntity instance = resolveInstance(query.getCoreEntity());
//
//        Set<LabeledEntity> entities = new HashSet<>();
//        Set<String> textAnswers = new HashSet<>();
//        // \TODO Call document search
//        // Document document = knowledgeBase.getDocumentSearcher().getDocument(entities.entrySet().iterator().next().getKey().getKnowledgeBase());
//        // \TODO Equate document with normalized entity id
//        // final Entity def = new Entity(document.getKnowledgeBase());
//        // Definition is the summary of the document
//        // document.getSummary()
//
//        if (!textAnswers.isEmpty()) {
//            AnswerSetResponse answerSet = new AnswerSetResponse(DEFINITION, userQuery);
//            // \TODO define mappings for name entity
//            // answerSet.setMappings(); ->
//            answerSet.setTextAnswer(new ArrayList<>(textAnswers));
//            return answerSet;
//        }

        return new NoResponse(NLI, userQuery);

    }

    public QueryResponse clueQuery(String userQuery, Language language) {

//      These filters will be used very soon
//      ClueAnalyzer clueAnalyzer = new ClueAnalyzer();
//      String pronominalAnswerType = clueAnalyzer.getPronominalAnswerType(userQuery);
//      String lexicalAnswerType = clueAnalyzer.getLexicalAnswerType(userQuery);
//      String abstractLexicalAnswerType = clueAnalyzer.getAbstractType(lexicalAnswerType);

//      Get documents containing the keywords
//      Map<Document, Double> documents = knowledgeBase.getDocumentSearcher().searchDocuments(userQuery, 3);
//
//        Set<LabeledEntity> entities = new HashSet<>();
//        if (!entities.isEmpty()) {
//            AnswerSetResponse answerSet = new AnswerSetResponse(DEFINITION, userQuery);
//            answerSet.setEntityAnswer(new ArrayList<>(entities));
//            return answerSet;
//        }

        return new NoResponse(NLI, userQuery);
    }


}
