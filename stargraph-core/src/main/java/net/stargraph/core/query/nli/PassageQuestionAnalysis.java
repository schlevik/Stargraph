package net.stargraph.core.query.nli;

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

import net.stargraph.core.query.annotator.Word;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class PassageQuestionAnalysis {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker = MarkerFactory.getMarker("passage");

    private String question;
    private List<Word> annotatedWords;
    private Deque<AnalysisStep> steps;

    private DataModelBinding instance;
    private String rest;

    PassageQuestionAnalysis(String question) {
        this.question = Objects.requireNonNull(question);
        this.steps = new ArrayDeque<>();
        logger.debug(marker, "Analyzing '{}', detected type is '{}'", question);
    }

    void annotate(List<Word> annotatedWords) {
        this.annotatedWords = Objects.requireNonNull(annotatedWords);
        this.steps.add(new AnalysisStep(annotatedWords));
    }

    void resolveInstances(List<DataModelTypePattern> instanceRules) {
        // for now, resolves only the first occurrence of the instance
        // i think this should be enough.
        if (steps.isEmpty()) {
            throw new IllegalStateException();
        }

        logger.debug(marker, "Resolving Data Models");

        boolean hasMatch;

        do {
            hasMatch = false;
            for (DataModelTypePattern rule : instanceRules) {
                AnalysisStep step = steps.peek().resolve(rule);
                if (step != null) {
                    steps.push(step);
                    // as soon as we found one instance, we're done.
                    return;
                }
            }
        } while (hasMatch);
    }

    private void clean(List<Pattern> stopPatterns) {
        if (steps.isEmpty()) {
            throw new IllegalStateException();
        }

        logger.debug(marker, "Cleaning up");
        AnalysisStep step = steps.peek().clean(stopPatterns);
        if (step != null) {
            steps.push(step);
        }
    }

    void finalize(List<Pattern> stopPatterns) {
        clean(stopPatterns);
        // get instance, it's the instance found the last step.
        if (steps.size() > 0) {
            this.instance = steps.peek().getBindings().get(0);
        } else {
            this.instance = null;
        }
        // now get rest, it should be the rest of the sentence minus the first found instance
        // (this is an assumption, there surely is a better way to do this)
        this.rest = StringUtils.normalizeSpace(
                steps.peek().getAnalyzedQuestionStr().replace("INSTANCE_1", ""));
        //TODO: maybe lemmatize or sth
    }

    public DataModelBinding getInstance() {
        return this.instance;
    }

    public String getRest() {
        return this.rest;
    }

    @Override
    public String toString() {
        return "PassageAnalysis{" +
                "q='" + question + '\'' +
                ", POS=" + annotatedWords +
                ", Instance='" + this.instance + '\'' +
                ", Rest='" + this.rest + '\'' +
                '}';
    }
}
