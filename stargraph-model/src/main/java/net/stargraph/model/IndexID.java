package net.stargraph.model;

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

import java.io.Serializable;

/**
 * Identity for a tuple (kb, index)
 */
public final class IndexID implements Serializable {

    private String knowledgeBase;
    private String index;

    private IndexID(String knowledgeBase, String index) {
        this.knowledgeBase = knowledgeBase;
        this.index = index;
    }

    public static IndexID of(String id, String model) {
        if (id == null || model == null || id.isEmpty() || model.isEmpty()) {
            throw new IllegalArgumentException(String.format("knowledgeBase=%s, index=%s", id, model));
        }
        return new IndexID(id, model);
    }

    public String getIndexPath() {
        return String.format("kb.%s.model.%s", knowledgeBase, index);
    }

    public String getKBPath() {
        return String.format("kb.%s", knowledgeBase);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IndexID indexID = (IndexID) o;

        return knowledgeBase.equals(indexID.knowledgeBase) && index.equals(indexID.index);

    }

    @Override
    public int hashCode() {
        int result = knowledgeBase.hashCode();
        result = 31 * result + index.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s.%s", knowledgeBase, index);
    }

    public String getKnowledgeBase() {
        return knowledgeBase;
    }

    public String getIndex() {
        return index;
    }

}
