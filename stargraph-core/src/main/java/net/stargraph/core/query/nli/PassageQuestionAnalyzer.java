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

import net.stargraph.StarGraphException;
import net.stargraph.core.query.Rules;
import net.stargraph.core.query.annotator.Annotator;
import net.stargraph.query.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class PassageQuestionAnalyzer {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker = MarkerFactory.getMarker("passage");
    private Language language;
    private Annotator annotator;
    private List<DataModelTypePattern> instanceRules;
    private List<Pattern> stopPatterns;

    public PassageQuestionAnalyzer(Language language, Annotator annotator, Rules rules) {
        logger.info(marker, "Creating analyzer for '{}'", language);
        this.language = Objects.requireNonNull(language);
        this.annotator = Objects.requireNonNull(annotator);
        this.instanceRules = rules.getDataModelTypeRules(language).stream().filter(
                (rule) -> rule.getDataModelType().equals(DataModelType.INSTANCE)).collect(Collectors.toList());
        logger.info(marker, "Instance rules: {}", rules.getDataModelTypeRules(language));

        this.stopPatterns = rules.getStopRules(language);
    }

    public PassageQuestionAnalysis analyse(String question) {
        PassageQuestionAnalysis analysis = null;
        try {
            long startTime = System.currentTimeMillis();
            analysis = new PassageQuestionAnalysis(question);
            // add POS-tags
            analysis.annotate(annotator.run(language, question));
            // replace instances with instance variables
            analysis.resolveInstances(instanceRules);
            // clean up stop words
            analysis.finalize(stopPatterns);

            logger.info(marker, "{}", getTimingReport(question, startTime));
            return analysis;
        } catch (Exception e) {
            logger.error(marker, "Analysis failure. Last step: {}", analysis);
            throw new StarGraphException(e);
        }
    }


    private String getTimingReport(String q, long start) {
        long elapsedTime = System.currentTimeMillis() - start;
        return String.format("'%s' analyzed in %.3fs", q, elapsedTime / 1000.0);
    }

}
