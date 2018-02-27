package net.stargraph.core.index;

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

import net.stargraph.core.Stargraph;
import net.stargraph.data.DataProvider;
import net.stargraph.data.processor.Holder;
import net.stargraph.model.KBId;

import java.io.Serializable;
import java.util.Iterator;

/**
 * IndexPopulator that doesn't do anything. Useful for testing without the backend.
 * Prints on stdout whatever it gets.
 */
final class NullIndexPopulator extends BaseIndexPopulator {

    NullIndexPopulator(KBId kbId, Stargraph core) {
        super(kbId, core);
    }

    @Override
    protected void beforeLoad(boolean reset) {
        logger.warn("This is the NullIndexPopulator.");
    }

    @Override
    protected void doIndex(Serializable data, KBId kbId) throws InterruptedException {
        //that'it, nothing is done.
        System.out.println(data);
    }

    @Override
    public void extend(DataProvider<? extends Holder> data) {
        Iterator<? extends Holder> dataIterator = data.iterator();
        while (dataIterator.hasNext()) {
            Holder datum = dataIterator.next();
            try {
                doIndex(datum.get(), this.kbId);
            } catch (InterruptedException e) {
                System.out.println("Interrupted.");
            }
        }
    }
}

