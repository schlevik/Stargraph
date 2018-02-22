package net.stargraph.test.it;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfhdt.hdt.hdt.HDT;
import org.rdfhdt.hdt.hdt.HDTManager;
import org.rdfhdt.hdtjena.HDTGraph;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class Test {

    @org.testng.annotations.Test
    public void Test() throws IOException {
        File hdtFile = Paths.get("/home/viktor/stargraph-data/lucene-dbpedia/facts/triples.hdt").toFile();
        String hdtFilePathStr = hdtFile.getAbsolutePath();
        HDT hdt = HDTManager.loadHDT(hdtFilePathStr, null);

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
