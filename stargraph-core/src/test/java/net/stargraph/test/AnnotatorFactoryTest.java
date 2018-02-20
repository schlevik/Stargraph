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
import net.stargraph.core.impl.corenlp.CoreNLPAnnotator;
import net.stargraph.core.impl.corenlp.CoreNLPAnnotatorFactory;
import net.stargraph.core.impl.opennlp.OpenNLPAnnotator;
import net.stargraph.core.impl.opennlp.OpenNLPAnnotatorFactory;
import net.stargraph.core.query.Analyzers;
import net.stargraph.core.query.annotator.AnnotatorFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;

public final class AnnotatorFactoryTest {


    @Test
    public void initCoreNLPTest() {
        ConfigFactory.invalidateCaches();
        Config config = buildConfig(CoreNLPAnnotatorFactory.class.getCanonicalName(), null);
        AnnotatorFactory factory = Analyzers.createAnnotatorFactory(config);
        Assert.assertNotNull(factory);
        Assert.assertEquals(factory.create().getClass(), CoreNLPAnnotator.class);
    }


    @Test
    public void initOpenNLPTest() {
        ConfigFactory.invalidateCaches();
        Config config = buildConfig(OpenNLPAnnotatorFactory.class.getCanonicalName(), "/tmp");
        AnnotatorFactory factory = Analyzers.createAnnotatorFactory(config);
        Assert.assertNotNull(factory);
        Assert.assertEquals(factory.create().getClass(), OpenNLPAnnotator.class);
    }

    private Config buildConfig(String name, String modelsDir) {
        return ConfigFactory.parseMap(new HashMap<String, String>() {{
            put("annotator.factory.class", name);
            if (modelsDir != null) { // hello from the python side
                put("annotator.factory.models-dir", modelsDir);
            }
        }});
    }
}
