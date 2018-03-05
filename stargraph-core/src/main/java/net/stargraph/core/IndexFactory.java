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

import net.stargraph.StarGraphException;
import net.stargraph.core.index.BaseIndexPopulator;
import net.stargraph.core.search.executor.IndexSearchExecutor;
import net.stargraph.core.search.index.IndexSearcher;
import net.stargraph.model.IndexID;


public interface IndexFactory {

    BaseIndexPopulator createIndexer(IndexID indexID, Stargraph stargraph);

    IndexSearchExecutor createSearchExecutor(IndexID indexID, Stargraph stargraph);

    default IndexSearcher createIndexSearcher(Index index, Stargraph stargraph) {
        // get index searcher type (e.g. its interface) or a concrete implementing class from config
        Class<? extends IndexSearcher> cls = stargraph.getIndexSearcherType(index.getID());
        // if interface, let concrete factory create the corresponding entry
        if (cls.isInterface()) {
            Class implCls = getImplementationFor(cls);
            if (implCls.isInterface() || !cls.isAssignableFrom(implCls)) {
                throw new StarGraphException(this.getClass() +
                        " has an implementation error! getImplementationFor " +
                        "should return a concrete class that implements the interface " + cls.getName() + "! " +
                        "(What it returned: " + implCls.getName() + ").");
            }
            cls = (Class<? extends IndexSearcher>) implCls;

        }
        return stargraph.createIndexSearcher(cls, index);
    }

    /**
     * Classes implementing this method should return an implementation class for the given interface.
     *
     * @param iFace Interface of a concrete index searcher.
     * @return Its implementation.
     */
    Class getImplementationFor(Class<? extends IndexSearcher> iFace);
}
