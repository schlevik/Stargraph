package net.stargraph.core.query.nli;

import net.stargraph.StarGraphException;
import net.stargraph.core.KnowledgeBase;
import net.stargraph.core.Namespace;
import net.stargraph.core.query.AbstractQueryResolver;
import net.stargraph.core.query.Analyzers;
import net.stargraph.core.query.Query;
import net.stargraph.core.query.TriplePattern;
import net.stargraph.core.query.response.AnswerSetResponse;
import net.stargraph.core.query.srl.*;
import net.stargraph.core.search.database.SparqlQuery;
import net.stargraph.core.search.database.SparqlResult;
import net.stargraph.core.search.index.EntityIndexSearcher;
import net.stargraph.core.search.index.FactIndexSearcher;
import net.stargraph.model.InstanceEntity;
import net.stargraph.model.LabeledEntity;
import net.stargraph.model.PropertyEntity;
import net.stargraph.rank.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static net.stargraph.query.InteractionMode.NLI;

public class NLIQueryResolver extends AbstractQueryResolver {
    private final String name = "nli";

    @Override
    public String getName() {
        return name;
    }

    private QuestionAnalyzer analyzer;

    public NLIQueryResolver(KnowledgeBase knowledgeBase, Analyzers analyzers) {
        super(knowledgeBase, analyzers);
        analyzer = analyzers.getQuestionAnalyzer(knowledgeBase.getLanguage());

    }

    @Override
    public void resolveQuery(Query query) {
        String userQuery = query.getInput();
        QuestionAnalysis analysis = null;
        try {
            analysis = analyzer.analyse(userQuery);
        } catch (AnalysisException e) {
            logger.info(marker, "Couldn't analyze the input query '{}', skipping.", userQuery);
        }
        if (analysis != null) {
            // if analysis actually worked...

            SPARQLQueryBuilder queryBuilder = analysis.getSPARQLQueryBuilder();
            Namespace namespace = getKnowledgeBase().getNamespace();
            queryBuilder.setNS(namespace);

            QueryPlanPatterns triplePatterns = queryBuilder.getTriplePatterns();
            List<DataModelBinding> bindings = queryBuilder.getBindings();

            triplePatterns.forEach(triplePattern -> {
                logger.debug(marker, "Resolving {}", triplePattern);
                resolve(asTriple(triplePattern, bindings), queryBuilder);
            });

            String sparqlQueryStr = queryBuilder.build();

            SparqlResult vars = getKnowledgeBase().queryDatabase(new SparqlQuery(sparqlQueryStr)).get(SparqlResult.class);

            if (!vars.isEmpty()) {
                AnswerSetResponse answerSet = new AnswerSetResponse(this, userQuery, queryBuilder);

                Set<LabeledEntity> expanded = vars.get("VAR_1").stream()
                        .map(namespace::expand).collect(Collectors.toSet());

                answerSet.setEntityAnswer(new ArrayList<>(expanded)); // convention, answer must be bound to the first var
                answerSet.setMappings(queryBuilder.getMappings());
                answerSet.setSPARQLQuery(sparqlQueryStr);

                //
                //if (triplePattern.getTypes().contains("VARIABLE TYPE CLASS")) {
                //    entities = knowledgeBase.getEntitySearcher().searchByTypes(new HashSet<String>(Arrays.asList(triplePattern.objectLabel.split(" "))), true, 100);
                //}

                query.appendResponse(answerSet);
            }
        }
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
            FactIndexSearcher searcher = getIndexSearcher(FactIndexSearcher.class);
            ModifiableSearchParams searchParams = ModifiableSearchParams.create().term(binding.getTerm());
            ModifiableRankParams rankParams = ParamsBuilder.word2vec();
            Scores<LabeledEntity> scores = searcher.classSearch(searchParams, rankParams);
            builder.add(binding, scores.stream().limit(3).collect(Collectors.toList()));
        }
    }

    private void resolvePredicate(InstanceEntity pivot, DataModelBinding binding, SPARQLQueryBuilder builder) {
        if ((binding.getModelType() == DataModelType.CLASS
                || binding.getModelType() == DataModelType.PROPERTY) && !builder.isResolved(binding)) {

            FactIndexSearcher searcher = getIndexSearcher(FactIndexSearcher.class);
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
            EntityIndexSearcher searcher = getIndexSearcher(EntityIndexSearcher.class);
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
