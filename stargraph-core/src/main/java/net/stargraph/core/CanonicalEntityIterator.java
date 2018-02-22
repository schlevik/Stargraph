package net.stargraph.core;

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

import com.google.common.collect.Iterators;
import net.stargraph.ModelUtils;
import net.stargraph.data.Indexable;
import net.stargraph.model.KBId;
import org.apache.jena.base.Sys;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.sparql.util.NodeUtils;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.semanticweb.yars.nx.namespace.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class CanonicalEntityIterator implements Iterator<Indexable> {
    private Model model;
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker = MarkerFactory.getMarker("core");
    private KBId kbId;
    private KBCore core;
    private Namespace namespace;
    private Iterator<Tuple> iterator;
    private Tuple currentTuple;

    public CanonicalEntityIterator(Stargraph stargraph, KBId kbId) {
        this.kbId = Objects.requireNonNull(kbId);
        this.core = stargraph.getKBCore(kbId.getId());
        this.namespace = stargraph.getKBCore(kbId.getId()).getNamespace();
        this.model = core.getGraphModel();
        this.iterator = createIterator();
    }


    @Override
    public boolean hasNext() {
        if (currentTuple != null) {
            return true;
        }

        while (iterator.hasNext()) {
            currentTuple = iterator.next();
            //skipping literals and blank nodes.
            if ((!currentTuple.res.isBlank() && !currentTuple.res.isLiteral())) {
                if (namespace.isFromMainNS(currentTuple.res.getURI())) {
                    return true;
                } else {
                    logger.trace(marker, "Discarded. NOT from main NS: [{}]", currentTuple.res.getURI());

                }
            }
        }
        currentTuple = null;
        return false;
    }

    @Override
    public Indexable next() {
        try {
            if (currentTuple == null) {
                throw new NoSuchElementException();
            }
            return new Indexable(ModelUtils.createCanonicalEntity(
                    applyNS(currentTuple.resUri()),
                    applyNS(currentTuple.refUri())
            ), kbId);

        } finally {
            currentTuple = null;
        }
    }

    private String applyNS(String uri) {
        if (namespace != null && uri != null) {
            return namespace.shrinkURI(uri);
        }
        return uri;
    }

    private final static String ENTITY_QUERY_WITH_REDIRECTS =
            "SELECT DISTINCT ?resource ?redir WHERE { {" +
                    " ?resource ?p ?o. " +
                    "OPTIONAL { ?resource <http://dbpedia.org/ontology/wikiPageRedirects> ?redir }" +
                    "} UNION {" +
                    " ?s ?p ?resource." +
                    "OPTIONAL { ?resource <http://dbpedia.org/ontology/wikiPageRedirects> ?redir }" +
                    "} }";

    private final static String ENTITY_QUERY_SUBJ_ONLY =
            "SELECT ?resource ?redir WHERE {" +
                    " ?resource ?p ?o . " +
                    "OPTIONAL { ?resource <http://dbpedia.org/ontology/wikiPageRedirects> ?redir . }" +
                    "}";


    public Iterator<Tuple> createIterator() {
        logger.debug(marker, "Model has {} entries.", model.size());
        //Query query = QueryFactory.create(ENTITY_QUERY_WITH_REDIRECTS);
        Graph g = model.getGraph();
//        Triple(null, model.createProperty("<http://dbpedia.org/ontology/wikiPageRedirects>").asNode(), null
        ExtendedIterator<Triple> exIt = g.find(Node.ANY, NodeUtils.asNode("http://dbpedia.org/ontology/wikiPageRedirects"), null);


        ExtendedIterator<Tuple> redirIt = exIt.mapWith(Tuple::new);
        exIt = g.find(Node.ANY, null, null);
        ExtendedIterator<Tuple> subjIt = exIt.mapWith(triple -> new Tuple(triple.getSubject(), null));
        exIt = g.find(null, null, Node.ANY);
        ExtendedIterator<Tuple> objIt = exIt.mapWith(triple -> new Tuple(triple.getObject(), null));

//        logger.debug(marker, "Performing following query on the model:\n{}", query.toString());
//        QueryExecution execution = QueryExecutionFactory.create(query, model);
//        ResultSet resultSet = execution.execSelect();
//        return redirIt;
        return Iterators.concat(redirIt, subjIt, objIt);
    }

    class Tuple {
        final Node res;
        private final Node ref;
        private boolean hasRef = false;

        private Tuple(Node x, Node y) {
            this.res = x;
            if (y != null) {
                this.ref = y;
                this.hasRef = true;
            } else {
                ref = null;
            }
        }

        Tuple(Triple triple) {
            this(triple.getSubject(), triple.getObject());
            //logger.debug(marker, "Creating new tuple from triple {}", triple);
        }

//        Tuple(QuerySolution row) {
//            this(row.get("resource"), row.get("redir"));
//        }

        public String resUri() {
            return res.getURI();
        }

        public String refUri() {
            return hasRef ? ref.getURI() : null;
        }
    }
}
