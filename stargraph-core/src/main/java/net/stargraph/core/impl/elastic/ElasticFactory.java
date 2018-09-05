package net.stargraph.core.impl.elastic;

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

import net.stargraph.core.IndexFactory;
import net.stargraph.core.Stargraph;
import net.stargraph.core.index.BaseIndexPopulator;
import net.stargraph.core.search.index.IndexSearcher;
import net.stargraph.model.IndexID;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;


import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class ElasticFactory implements IndexFactory {
    //TODO: read from config
    private Set<Class<? extends ElasticBaseIndexSearcher>> allSearcherClasses = new HashSet<>(Arrays.asList(
            ElasticDocumentIndexSearcher.class,
            ElasticEntityIndexSearcher.class,
            ElasticFactIndexSearcher.class,
            ElasticPropertyIndexSearcher.class
    ));

    @Override
    public BaseIndexPopulator createIndexer(IndexID indexID, Stargraph stargraph) {
        return new ElasticIndexPopulator(indexID, stargraph);
    }


    @Override
    public ElasticIndexSearchExecutor createSearchExecutor(IndexID indexID, Stargraph stargraph) {
        return new ElasticIndexSearchExecutor(indexID, stargraph);
    }

    @Override
    public Class getImplementationFor(Class<? extends IndexSearcher> iFace) {
        for (Class<? extends ElasticBaseIndexSearcher> searcherClass : allSearcherClasses) {
            if (iFace.isAssignableFrom(searcherClass)) {
                return searcherClass;
            }
        }
        throw new NotImplementedException();
    }


}
