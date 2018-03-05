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
import net.stargraph.core.KnowledgeBase;
import net.stargraph.core.NTriplesModelFactory;
import net.stargraph.core.Stargraph;
import net.stargraph.core.index.NullIndexFactory;
import net.stargraph.model.IndexID;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static net.stargraph.test.TestUtils.copyResource;
import static net.stargraph.test.TestUtils.createPath;

public final class KBCoreExtendTest {

    private IndexID indexID = IndexID.of("extend", "facts");
    private List<Statement> expected;
    private Stargraph stargraph;
    private KnowledgeBase core;

    @BeforeClass
    public void before() throws IOException {
        Path root = Files.createTempFile("stargraph-", "-dataDir");
        Path ntPath = createPath(root, indexID).resolve("triples.nt");
        copyResource("dataSets/simple/facts/triples.nt", ntPath);
        ConfigFactory.invalidateCaches();
        Config config = ConfigFactory.load().getConfig("stargraph");

        this.stargraph = new Stargraph(config, false);
        this.stargraph.setKBInitSet(indexID.getKnowledgeBase());
        this.stargraph.setDefaultIndicesFactory(new NullIndexFactory());
        stargraph.setDefaultGraphModelFactory(new NTriplesModelFactory(stargraph));
        this.stargraph.setDataRootDir(root.toFile());
        this.stargraph.initialize();
        this.core = stargraph.getKBCore(indexID.getKnowledgeBase());
        this.expected = Arrays.asList(
                ResourceFactory.createStatement(
                        ResourceFactory.createResource("http://lambda33.org/s"),
                        ResourceFactory.createProperty("http://lambda3.org/p"),
                        ResourceFactory.createPlainLiteral("o1")),
                ResourceFactory.createStatement(
                        ResourceFactory.createResource("http://ppl.org/s"),
                        ResourceFactory.createProperty("http://ppl.org/p"),
                        ResourceFactory.createPlainLiteral("o2"))
        );
    }

    @Test(enabled = false)
    public void successWhenExtendKBCoreTest() {
//        long size = this.core.getGraphModel().size();
//        this.core.extend(expected);
//        long newSize = this.core.getGraphModel().size();
//        Assert.assertEquals(size, newSize - 2);
    }


}
