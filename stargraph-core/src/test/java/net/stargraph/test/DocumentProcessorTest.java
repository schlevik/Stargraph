package net.stargraph.test;

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
import com.typesafe.config.ConfigFactory;
import net.stargraph.core.Stargraph;
import net.stargraph.core.processors.PassageProcessor;
import net.stargraph.data.Indexable;
import net.stargraph.data.processor.Holder;
import net.stargraph.model.Document;
import net.stargraph.model.IndexID;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;


public final class DocumentProcessorTest {
    IndexID indexID = IndexID.of("obama", "documents");
    PassageProcessor processor;
    String text;

    @BeforeClass
    public void beforeClass() {
        ConfigFactory.invalidateCaches();
        Config config = ConfigFactory.load();
        Stargraph stargraph = Mockito.mock(Stargraph.class);
        Config processorCfg = config.getConfig("testing-processor").withOnlyPath(PassageProcessor.name);

        processor = new PassageProcessor(stargraph, processorCfg);
        text = "Barack Obama is a nice guy. " +
                "Barack Obama was the president of the United States. " +
                "Barack Obama likes to eat garlic bread.";
    }

    @Test
    public void processTest() {
        System.out.println(this.text);
        Holder holder = new Indexable(new Document("test.txt", "Test", this.text), this.indexID);
        processor.run(holder);

        Assert.assertFalse(holder.isSinkable());

    }
}
