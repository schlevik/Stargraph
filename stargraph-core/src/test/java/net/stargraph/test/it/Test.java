package net.stargraph.test.it;

/*-
 * ==========================License-Start=============================
 * stargraph-core
 * --------------------------------------------------------------------
 * Copyright (C) 2017 - 2018 Lambda^3
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

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfhdt.hdt.hdt.HDT;
import org.rdfhdt.hdt.hdt.HDTManager;
import org.rdfhdt.hdtjena.HDTGraph;

import javax.ws.rs.core.PathSegment;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class Test {

    @org.testng.annotations.Test
    public void Test() throws IOException {
        File hdtFile = Paths.get("/home/viktor/stargraph-data/lucene-dbpedia/facts/triples.hdt").toFile();
        String hdtFilePathStr = hdtFile.getAbsolutePath();
        HDT hdt = HDTManager.loadIndexedHDT(hdtFilePathStr, null);

        HDTGraph graph = new HDTGraph(hdt);

        Model model = ModelFactory.createModelForGraph(graph);
        System.out.println(model.size());
        Query query = QueryFactory.create("SELECT * WHERE {<http://dbpedia.org/resource/Barack_Hussein_Obama> ?p ?o }");
        QueryExecution execution = QueryExecutionFactory.create(query, model);
        ResultSet result = execution.execSelect();
        System.out.println(">>>>>>>>>>>>>>");
        result.forEachRemaining(System.out::println);
    }

}
