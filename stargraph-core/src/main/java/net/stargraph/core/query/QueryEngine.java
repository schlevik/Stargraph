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
import net.stargraph.core.Namespace;
import net.stargraph.core.Stargraph;
import net.stargraph.core.query.nli.*;
import net.stargraph.core.query.response.AnswerSetResponse;
import net.stargraph.core.query.response.NoResponse;
import net.stargraph.core.query.response.SPARQLSelectResponse;
import net.stargraph.core.search.database.SparqlQuery;
import net.stargraph.core.search.database.SparqlResult;
import net.stargraph.core.search.index.DocumentIndexSearcher;
import net.stargraph.core.search.index.EntityIndexSearcher;
import net.stargraph.core.search.index.FactIndexSearcher;
import net.stargraph.model.InstanceEntity;
import net.stargraph.model.LabeledEntity;
import net.stargraph.model.Passage;
import net.stargraph.model.PropertyEntity;
import net.stargraph.query.InteractionMode;
import net.stargraph.query.Language;
import net.stargraph.rank.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static net.stargraph.query.InteractionMode.*;

/**
 * This class holds and ties together all existing {@link QueryResolver}s.
 * <p>
 * The input query is passed to all registered {@link QueryResolver}s which can append their answer to the query,
 * if they have one.
 */
public final class QueryEngine {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker = MarkerFactory.getMarker("query");

    private String dbId;
    private KnowledgeBase knowledgeBase;
    private Analyzers analyzers;
    //private GraphSearcher graphSearcher;
    private InteractionModeSelector modeSelector;
    private Namespace namespace;
    private Language language;

    private List<QueryResolver> queryResolvers;

    public QueryEngine(String dbId, Stargraph stargraph) {
        this.dbId = Objects.requireNonNull(dbId);
        this.knowledgeBase = Objects.requireNonNull(stargraph.getKnowledgeBase(dbId));
        this.analyzers = new Analyzers(stargraph.getConfig().getMainConfig());
        //this.graphSearcher = knowledgeBase.createGraphSearcher();
        this.namespace = knowledgeBase.getNamespace();
        this.language = knowledgeBase.getLanguage();

        this.modeSelector = new InteractionModeSelector(stargraph.getConfig().getMainConfig(), language);
    }

    public QueryResponse query(String query) {
        final InteractionMode mode = modeSelector.detect(query);
        QueryResponse response = new NoResponse(mode, query);

        long startTime = System.currentTimeMillis();
        try {
            switch (mode) {
                case NLI:
                    response = nliQuery(query, language);
                    break;
                case PASSAGE:
                    response = passageQuery(query);
                    break;
                case SPARQL:
                    response = sparqlQuery(query);
                    break;
                case ENTITY_SIMILARITY:
                    response = entitySimilarityQuery(query, language);
                    break;
                case DEFINITION:
                    response = definitionQuery(query, language);
                    break;
                default:
                    throw new StarGraphException("Input type not yet supported");
            }

            return response;

        } catch (Exception e) {
            logger.error(marker, "Query Error '{}'", query, e);
            throw new StarGraphException("Query Error", e);
        } finally {
            long millis = System.currentTimeMillis() - startTime;
            logger.info(marker, "Query Engine took {}s Response: {}", millis / 1000.0, response);
        }
    }

    private QueryResponse sparqlQuery(String userQuery) {
        SparqlResult vars = knowledgeBase.queryDatabase(new SparqlQuery(userQuery)).get(SparqlResult.class);
        if (!vars.isEmpty()) {
            return new SPARQLSelectResponse(SPARQL, userQuery, vars);
        }
        return new NoResponse(SPARQL, userQuery);
    }

    private QueryResponse passageQuery(String userQuery) {
        String query = userQuery.replace("PASSAGE ", "");
        PassageQuestionAnalyzer analyzer = this.analyzers.getPassageQuestionAnalyzer(language);
        PassageQuestionAnalysis analysis = analyzer.analyse(query);

        InstanceEntity pivot = resolvePivot(analysis.getInstance());
        DocumentIndexSearcher searcher = knowledgeBase.getSearcher(DocumentIndexSearcher.class);

        logger.debug(marker, "Analyzed: pivot={}, rest={}", pivot, analysis.getRest());

        // this is just a holder, we're not ranking anything atm
        ModifiableRankParams rankParams = new ModifiableRankParams(Threshold.auto(), RankingModel.LEVENSHTEIN);

        Scores<Passage> scores = searcher.pivotedFullTextPassageSearch(pivot,
                ModifiableSearchParams.create(dbId).term(analysis.getRest()), rankParams);

        if (scores.size() > 0) {
            AnswerSetResponse answerSet = new AnswerSetResponse(PASSAGE, userQuery);
            answerSet.setTextAnswer(scores.stream()
                    .map(score -> (score.getEntry()).getText())
                    .collect(Collectors.toList()));
            return answerSet;
        }

        // detect instance & rest
        // pivot instance
        // uh.. basically do a full text search on passages & rank according to input
        // example: What does barack obama like to eat?
        //                    ^instance^   ^everything else^
        // sentence = userQuery
        // entity, rest = myAnalyzer.analyse(sentence)
        // ----- how to do this?
        // 1) subclass QuestionAnalyzer
        // 2) make it do the following: annotate, apply _only_ INSTANCE rules, clean up, create a INSTANCE/rest view
        // relevantPassages = documents.passages.pivotedFullTextSearch(entity, rest)
        // ---- how to do this?
        // > well implement it in in the KnowledgeBase i guess
        // return sorted relevant passages
        return new NoResponse(PASSAGE, userQuery);
    }

    private QueryResponse nliQuery(String userQuery, Language language) {
        QuestionAnalyzer analyzer = this.analyzers.getQuestionAnalyzer(language);
        QuestionAnalysis analysis = analyzer.analyse(userQuery);
        SPARQLQueryBuilder queryBuilder = analysis.getSPARQLQueryBuilder();
        queryBuilder.setNS(namespace);

        QueryPlanPatterns triplePatterns = queryBuilder.getTriplePatterns();
        List<DataModelBinding> bindings = queryBuilder.getBindings();

        triplePatterns.forEach(triplePattern -> {
            logger.debug(marker, "Resolving {}", triplePattern);
            resolve(asTriple(triplePattern, bindings), queryBuilder);
        });

        String sparqlQueryStr = queryBuilder.build();

        SparqlResult vars = knowledgeBase.queryDatabase(new SparqlQuery(sparqlQueryStr)).get(SparqlResult.class);

        if (!vars.isEmpty()) {
            AnswerSetResponse answerSet = new AnswerSetResponse(NLI, userQuery, queryBuilder);

            Set<LabeledEntity> expanded = vars.get("VAR_1").stream()
                    .map(e -> namespace.expand(e)).collect(Collectors.toSet());

            answerSet.setEntityAnswer(new ArrayList<>(expanded)); // convention, answer must be bound to the first var
            answerSet.setMappings(queryBuilder.getMappings());
            answerSet.setSPARQLQuery(sparqlQueryStr);

            //
            //if (triplePattern.getTypes().contains("VARIABLE TYPE CLASS")) {
            //    entities = knowledgeBase.getEntitySearcher().searchByTypes(new HashSet<String>(Arrays.asList(triplePattern.objectLabel.split(" "))), true, 100);
            //}

            return answerSet;
        }

        return new NoResponse(NLI, userQuery);
    }

    private QueryResponse entitySimilarityQuery(String userQuery, Language language) {

        EntityQueryBuilder queryBuilder = new EntityQueryBuilder();
        EntityQuery query = queryBuilder.parse(userQuery, ENTITY_SIMILARITY);
        InstanceEntity instance = resolveInstance(query.getCoreEntity());

        Set<LabeledEntity> entities = new HashSet<>();
        // \TODO Call mltSearch here
        // mltSearch()
        // mltSearch will return Set<LabeledEntity>

        if (!entities.isEmpty()) {
            AnswerSetResponse answerSet = new AnswerSetResponse(ENTITY_SIMILARITY, userQuery);
            // \TODO define mappings for name entity
            // answerSet.setMappings();
            // answerSet.setMappings(); ->
            answerSet.setEntityAnswer(new ArrayList<>(entities));
            return answerSet;
        }

        return new NoResponse(NLI, userQuery);
    }

    public QueryResponse definitionQuery(String userQuery, Language language) {

        EntityQueryBuilder queryBuilder = new EntityQueryBuilder();
        EntityQuery query = queryBuilder.parse(userQuery, DEFINITION);
        InstanceEntity instance = resolveInstance(query.getCoreEntity());

        Set<LabeledEntity> entities = new HashSet<>();
        Set<String> textAnswers = new HashSet<>();
        // \TODO Call document search
        // Document document = knowledgeBase.getDocumentSearcher().getDocument(entities.entrySet().iterator().next().getKey().getKnowledgeBase());
        // \TODO Equate document with normalized entity id
        // final Entity def = new Entity(document.getKnowledgeBase());
        // Definition is the summary of the document
        // document.getSummary()

        if (!textAnswers.isEmpty()) {
            AnswerSetResponse answerSet = new AnswerSetResponse(DEFINITION, userQuery);
            // \TODO define mappings for name entity
            // answerSet.setMappings(); ->
            answerSet.setTextAnswer(new ArrayList<>(textAnswers));
            return answerSet;
        }

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

        Set<LabeledEntity> entities = new HashSet<>();
        if (!entities.isEmpty()) {
            AnswerSetResponse answerSet = new AnswerSetResponse(DEFINITION, userQuery);
            answerSet.setEntityAnswer(new ArrayList<>(entities));
            return answerSet;
        }

        return new NoResponse(NLI, userQuery);
    }

    private void resolve(Triple triple, SPARQLQueryBuilder builder) {
        if (triple.p.getModelType() != DataModelType.TYPE) {
            // if predicate is not a type assume: I (C|P) V pattern
            InstanceEntity pivot = resolvePivot(triple.s, builder);
            pivot = pivot != null ? pivot : resolvePivot(triple.o, builder);
            resolvePredicate(pivot, triple.p, builder);
        } else {
            // Probably is: V T C
            DataModelBinding binding = triple.s.getModelType() == DataModelType.VARIABLE ? triple.o : triple.s;
            resolveClass(binding, builder);
        }
    }

    private void resolveClass(DataModelBinding binding, SPARQLQueryBuilder builder) {
        if (binding.getModelType() == DataModelType.CLASS) {
            FactIndexSearcher searcher = knowledgeBase.getSearcher(FactIndexSearcher.class);
            ModifiableSearchParams searchParams = ModifiableSearchParams.create(dbId).term(binding.getTerm());
            ModifiableRankParams rankParams = ParamsBuilder.word2vec();
            Scores<LabeledEntity> scores = searcher.classSearch(searchParams, rankParams);
            builder.add(binding, scores.stream().limit(3).collect(Collectors.toList()));
        }
    }

    private void resolvePredicate(InstanceEntity pivot, DataModelBinding binding, SPARQLQueryBuilder builder) {
        if ((binding.getModelType() == DataModelType.CLASS
                || binding.getModelType() == DataModelType.PROPERTY) && !builder.isResolved(binding)) {

            FactIndexSearcher searcher = knowledgeBase.getSearcher(FactIndexSearcher.class);
            ModifiableSearchParams searchParams = ModifiableSearchParams.create().term(binding.getTerm());
            ModifiableRankParams rankParams = ParamsBuilder.word2vec();
            Scores<PropertyEntity> scores = searcher.pivotedSearch(pivot, searchParams, rankParams);
            builder.add(binding, scores.stream().limit(6).collect(Collectors.toList()));
        }
    }

    private InstanceEntity resolvePivot(DataModelBinding binding, SPARQLQueryBuilder builder) {
        List<Score> mappings = builder.getMappings(binding);
        if (!mappings.isEmpty()) {
            return (InstanceEntity) mappings.get(0).getEntry();
        }

        if (binding.getModelType() == DataModelType.INSTANCE) {
            EntityIndexSearcher searcher = knowledgeBase.getSearcher(EntityIndexSearcher.class);
            //information about the knowledge base is added later
            ModifiableSearchParams searchParams = ModifiableSearchParams.create().term(binding.getTerm());
            ModifiableRankParams rankParams = ParamsBuilder.levenshtein(); // threshold defaults to auto
            Scores<InstanceEntity> scores = searcher.instanceSearch(searchParams, rankParams);
            InstanceEntity instance = scores.get(0).getEntry();
            builder.add(binding, Collections.singletonList(scores.get(0)));
            return instance;
        }
        return null;
    }

    private InstanceEntity resolvePivot(DataModelBinding binding) {
        if (binding.getModelType() == DataModelType.INSTANCE) {
            EntityIndexSearcher searcher = knowledgeBase.getSearcher(EntityIndexSearcher.class);
            ModifiableSearchParams searchParams = ModifiableSearchParams.create(dbId).term(binding.getTerm());
            ModifiableRankParams rankParams = ParamsBuilder.levenshtein(); // threshold defaults to auto
            Scores<InstanceEntity> scores = searcher.instanceSearch(searchParams, rankParams);

            return scores.get(0).getEntry();
        }
        return null;
    }

    private InstanceEntity resolveInstance(String instanceTerm) {
        EntityIndexSearcher searcher = knowledgeBase.getSearcher(EntityIndexSearcher.class);
        ModifiableSearchParams searchParams = ModifiableSearchParams.create(dbId).term(instanceTerm);
        ModifiableRankParams rankParams = ParamsBuilder.levenshtein(); // threshold defaults to auto
        Scores<InstanceEntity> scores = searcher.instanceSearch(searchParams, rankParams);
        return scores.get(0).getEntry();
    }

    private Triple asTriple(TriplePattern pattern, List<DataModelBinding> bindings) {
        String[] components = pattern.getPattern().split("\\s");
        return new Triple(map(components[0], bindings), map(components[1], bindings), map(components[2], bindings));
    }

    private DataModelBinding map(String placeHolder, List<DataModelBinding> bindings) {
        if (placeHolder.startsWith("?VAR") || placeHolder.startsWith("TYPE")) {
            DataModelType type = placeHolder.startsWith("?VAR") ? DataModelType.VARIABLE : DataModelType.TYPE;
            return new DataModelBinding(type, placeHolder, placeHolder);
        }

        return bindings.stream()
                .filter(b -> b.getPlaceHolder().equals(placeHolder))
                .findAny().orElseThrow(() -> new StarGraphException("Unmapped placeholder '" + placeHolder + "'"));
    }

    private static class Triple {
        Triple(DataModelBinding s, DataModelBinding p, DataModelBinding o) {
            this.s = s;
            this.p = p;
            this.o = o;
        }

        public DataModelBinding s;
        public DataModelBinding p;
        public DataModelBinding o;

        @Override
        public String toString() {
            return "Triple{" +
                    "s=" + s +
                    ", p=" + p +
                    ", o=" + o +
                    '}';
        }
    }
}
