package net.stargraph.core.processors;

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

import com.typesafe.config.Config;
import net.stargraph.data.processor.BaseProcessor;
import net.stargraph.data.processor.FatalProcessorException;
import net.stargraph.data.processor.Holder;
import net.stargraph.data.processor.ProcessorException;
import net.stargraph.model.Document;
import org.lambda3.graphene.core.Graphene;
import org.lambda3.graphene.core.coreference.model.CoreferenceContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.Serializable;
import java.net.UnknownHostException;

/**
 * Can be placed in the workflow to resolve co-references.
 */
public final class CoreferenceResolutionProcessor extends BaseProcessor {
    public static String name = "coref-processor";
    public Logger logger = LoggerFactory.getLogger(this.getClass());
    public Marker marker = MarkerFactory.getMarker(name);

    private Graphene graphene;

    public CoreferenceResolutionProcessor(Config config) {
        super(config);
        graphene = new Graphene(getConfig());
    }

    @Override
    public void doRun(Holder<Serializable> holder) throws ProcessorException {
        Serializable entry = holder.get();

        if (entry instanceof Document) {
            logger.debug(marker, "Got document....");
            try {
                Document document = (Document) entry;
                logger.debug("Trying coreference...");
                logger.debug("with config {}", getConfig());
                CoreferenceContent cc = graphene.doCoreference(document.getText());
                String resolved = cc.getSubstitutedText();
                logger.debug("..coreference successfully resolved");
                holder.set(new Document(
                        document.getId(),
                        document.getTitle(),
                        document.getSummary(),
                        resolved
                ));
            } catch (Exception e) {
                logger.error(marker, "Error!", e);
                if (e.getCause() instanceof UnknownHostException) {
                    throw new FatalProcessorException(e);
                }
            }
        }
    }

    @Override
    public String getName() {
        return name;
    }
}
