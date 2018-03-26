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
import net.stargraph.core.impl.jena.JenaGraphDatabase;
import net.stargraph.core.search.database.DBType;
import net.stargraph.data.Indexable;
import net.stargraph.model.IndexID;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import static net.stargraph.ModelUtils.createInstance;

public final class EntityIterator implements Iterator<Indexable> {
    private Model model;
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker = MarkerFactory.getMarker("core");
    private IndexID indexID;
    private Namespace namespace;
    private Iterator<Node> iterator;
    private Node currentNode;

    public EntityIterator(Stargraph stargraph, IndexID indexID) {
        JenaGraphDatabase database = (JenaGraphDatabase) stargraph.getKnowledgeBase(indexID.getKnowledgeBase()).getDatabase(DBType.Graph);
        this.indexID = Objects.requireNonNull(indexID);
        this.namespace = database.getNamespace();
        this.model = database.getModel();
        this.iterator = createIterator();
    }

    public EntityIterator(Stargraph stargraph, IndexID indexID, List data) {
        JenaGraphDatabase database = (JenaGraphDatabase) stargraph.getKnowledgeBase(indexID.getKnowledgeBase()).getDatabase(DBType.Graph);
        this.indexID = Objects.requireNonNull(indexID);
        this.namespace = database.getNamespace();
        this.model = ModelFactory.createDefaultModel().add(data);
        this.iterator = createIterator();

    }


    @Override
    public boolean hasNext() {
        if (currentNode != null) {
            return true;
        }

        while (iterator.hasNext()) {
            currentNode = iterator.next();
            //skipping literals and blank nodes.
            if ((!currentNode.isBlank() && !currentNode.isLiteral())) {
                if (namespace.isFromMainNS(currentNode.getURI())) {
                    return true;
                } else {
                    logger.trace(marker, "Discarded. NOT from main NS: [{}]", currentNode.getURI());
                }
            }
        }

        currentNode = null;
        return false;
    }

    @Override
    public Indexable next() {
        try {
            if (currentNode == null) {
                throw new NoSuchElementException();
            }
            return new Indexable(createInstance(applyNS(currentNode.getURI())), indexID);
        } finally {
            currentNode = null;
        }
    }

    private String applyNS(String uri) {
        if (namespace != null) {
            return namespace.shrinkURI(uri);
        }
        return uri;
    }

    private Iterator<Node> createIterator() {
        Graph g = model.getGraph();
        ExtendedIterator<Triple> exIt = g.find(Node.ANY, null, null);
        ExtendedIterator<Node> subjIt = exIt.mapWith(Triple::getSubject);
        exIt = g.find(null, null, Node.ANY);
        ExtendedIterator<Node> objIt = exIt.mapWith(Triple::getObject);
        return Iterators.concat(subjIt, objIt);
    }
}
