package net.stargraph.core.query.srl;

/*-
 * ==========================License-Start=============================
 * stargraph-core
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
import net.stargraph.core.query.QueryType;
import net.stargraph.core.query.nli.SPARQLQueryBuilder;
import net.stargraph.core.query.annotator.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class QuestionAnalysis {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker = MarkerFactory.getMarker("nli");

    private QueryType queryType;
    private String question;
    private List<Word> annotatedWords;
    private Deque<AnalysisStep> steps;
    private SPARQLQueryBuilder sparqlQueryBuilder;

    QuestionAnalysis(String question, QueryType queryType) {
        this.question = Objects.requireNonNull(question);
        this.queryType = Objects.requireNonNull(queryType);
        this.steps = new ArrayDeque<>();
        logger.debug(marker, "Analyzing '{}', detected type is '{}'", question, queryType);
    }

    void annotate(List<Word> annotatedWords) {
        this.annotatedWords = Objects.requireNonNull(annotatedWords);
        this.steps.add(new AnalysisStep(annotatedWords));
    }

    void resolveDataModelBindings(List<DataModelTypePattern> rules) {
        if (steps.isEmpty()) {
            throw new IllegalStateException();
        }

        logger.debug(marker, "Resolving Data Models");

        boolean hasMatch;

        do {
            hasMatch = false;
            for (DataModelTypePattern rule : rules) {
                AnalysisStep step = steps.peek().resolve(rule);
                if (step != null) {
                    hasMatch = true;
                    steps.push(step);
                    break;
                }
            }
        } while (hasMatch);
    }

    void clean(List<Pattern> stopPatterns) {
        if (steps.isEmpty()) {
            throw new IllegalStateException();
        }

        logger.debug(marker, "Cleaning up");
        AnalysisStep step = steps.peek().clean(stopPatterns);
        if (step != null) {
            steps.push(step);
        }
    }

    void resolveSPARQL(List<QueryPlanPatterns> rules) {
        if (steps.isEmpty()) {
            throw new IllegalStateException();
        }

        AnalysisStep last = steps.peek();
        List<DataModelBinding> bindings = last.getBindings();
        String planId = last.getAnalyzedQuestionStr();

        QueryPlanPatterns plan = rules.stream()
                .filter(p -> p.match(planId))
                .findFirst().orElseThrow(() -> new StarGraphException("No plan for '" + planId + "'"));

        logger.debug(marker, "Creating SPARQL Query Builder, matched plan for '{}' is '{}'", planId, plan);

        sparqlQueryBuilder = new SPARQLQueryBuilder(queryType, plan, bindings);
    }

    public SPARQLQueryBuilder getSPARQLQueryBuilder() {
        if (sparqlQueryBuilder == null) {
            throw new IllegalStateException();
        }
        return sparqlQueryBuilder;
    }

    @Override
    public String toString() {
        return "Analysis{" +
                "q='" + question + '\'' +
                ", queryType'" + queryType + '\'' +
                ", POS=" + annotatedWords +
                ", Bindings='" + steps.peek().getBindings() + '\'' +
                '}';
    }
}
