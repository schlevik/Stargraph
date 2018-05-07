package net.stargraph.rank;

/*-
 * ==========================License-Start=============================
 * stargraph-index
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

import net.stargraph.model.BuiltInIndex;
import net.stargraph.model.IndexID;

import java.io.Serializable;

public final class ModifiableSearchParams {

    private int limit;
    private String knowledgeBase;
    private String index;
    private String searchTerm;
    private Class<? extends Serializable> modelClass;

    private ModifiableSearchParams(String kbId) {
        // defaults
        this.limit = -1;
        this.knowledgeBase = kbId;
    }

    public ModifiableSearchParams() {
        this.limit = -1;
    }

    public final ModifiableSearchParams term(String searchTerm) {
        this.searchTerm = searchTerm;
        return this;
    }

    public final ModifiableSearchParams limit(int maxEntries) {
        this.limit = maxEntries;
        return this;
    }

    public final ModifiableSearchParams index(String id) {
        this.index = id;
        return this;
    }

    public final ModifiableSearchParams index(BuiltInIndex builtInIndex) {
        this.index = builtInIndex.modelId;
        return this;
    }

    public final ModifiableSearchParams index(IndexID index) {
        this.knowledgeBase = index.getKnowledgeBase();
        this.index = index.getIndex();
        return this;
    }


    public final int getLimit() {
        return limit;
    }

    public final IndexID getIndexID() {
        return IndexID.of(knowledgeBase, index);
    }

    public final String getSearchTerm() {
        return searchTerm;
    }

    public static ModifiableSearchParams create(String dbId) {
        return new ModifiableSearchParams(dbId);
    }

    public static ModifiableSearchParams create() {
        return new ModifiableSearchParams();
    }

    public Class<? extends Serializable> getModelClass() {
        return this.modelClass;
    }

    public void model(Class<? extends Serializable> model) {
        this.modelClass = model;
    }
}
