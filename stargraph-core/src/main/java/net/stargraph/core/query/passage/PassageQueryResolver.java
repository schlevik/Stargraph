package net.stargraph.core.query.passage;

import net.stargraph.core.KnowledgeBase;
import net.stargraph.core.query.AbstractQueryResolver;
import net.stargraph.core.query.Analyzers;
import net.stargraph.core.query.Query;
import net.stargraph.core.query.response.TextResponse;
import net.stargraph.core.query.srl.DataModelBinding;
import net.stargraph.core.query.srl.DataModelType;
import net.stargraph.core.query.srl.PassageQuestionAnalysis;
import net.stargraph.core.query.srl.PassageQuestionAnalyzer;
import net.stargraph.core.query.response.AnswerSetResponse;
import net.stargraph.core.search.index.DocumentIndexSearcher;
import net.stargraph.core.search.index.EntityIndexSearcher;
import net.stargraph.model.InstanceEntity;
import net.stargraph.model.Passage;
import net.stargraph.rank.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.awt.*;
import java.util.stream.Collectors;

import static net.stargraph.query.InteractionMode.PASSAGE;

public class PassageQueryResolver extends AbstractQueryResolver {
    private final String name = "passage";
    private PassageQuestionAnalyzer analyzer;

    public PassageQueryResolver(KnowledgeBase knowledgeBase, Analyzers analyzers) {
        super(knowledgeBase, analyzers);
        this.analyzer = analyzers.getPassageQuestionAnalyzer(knowledgeBase.getLanguage());

    }

    @Override
    public void resolveQuery(Query query) {
        String input = query.getInput().replace("PASSAGE ", "");
        PassageQuestionAnalysis analysis = analyzer.analyse(input);

        InstanceEntity pivot = resolvePivot(analysis.getInstance());
        DocumentIndexSearcher searcher = getIndexSearcher(DocumentIndexSearcher.class);

        logger.debug(marker, "Analyzed: pivot={}, rest={}", pivot, analysis.getRest());

        // this is just a holder, we're not ranking anything atm
        ModifiableRankParams rankParams = new ModifiableRankParams(Threshold.auto(), RankingModel.LEVENSHTEIN);

        Scores<Passage> scores = searcher.pivotedFullTextPassageSearch(pivot,
                ModifiableSearchParams.create().term(analysis.getRest()), rankParams);

        if (scores.size() > 0) {
            TextResponse answerSet = new TextResponse(this, input);
            answerSet.setTextAnswer(scores.stream()
                    .map(score -> (score.getEntry()).getText())
                    .collect(Collectors.toList()));
            query.appendResponse(answerSet);
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
    }

    @Override
    public String getName() {
        return name;
    }

    private InstanceEntity resolvePivot(DataModelBinding binding) {
        if (binding.getModelType() == DataModelType.INSTANCE) {
            EntityIndexSearcher searcher = getIndexSearcher(EntityIndexSearcher.class);
            ModifiableSearchParams searchParams = ModifiableSearchParams.create().term(binding.getTerm());
            ModifiableRankParams rankParams = ParamsBuilder.levenshtein(); // threshold defaults to auto
            Scores<InstanceEntity> scores = searcher.instanceSearch(searchParams, rankParams);

            return scores.get(0).getEntry();
        }
        return null;
    }
}
