package net.stargraph.data.processor;

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

import com.typesafe.config.Config;
import net.stargraph.StarGraphException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.Serializable;
import java.util.Objects;

public abstract class BaseProcessor implements Processor<Serializable> {
    private Config config;
    protected Logger logger = LoggerFactory.getLogger(getName());
    protected Marker marker = MarkerFactory.getMarker("processor");

    public BaseProcessor(Config config) {
        this.config = Objects.requireNonNull(config);
        if (!config.hasPath(getName())) {
            throw new StarGraphException("Configuration name mismatch.");
        }
    }

    public abstract void doRun(Holder<Serializable> holder) throws ProcessorException;

    public abstract String getName();

    public final Config getConfig() {
        return config.getConfig(getName());
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public final void run(Holder<Serializable> holder) throws ProcessorException {
        try {
            if (!holder.isSinkable()) {
                doRun(holder);
            }
        } catch (FatalProcessorException e) {
            throw e; // Flags that subsequent calls to this processors is useless. Probably an unrecoverable error.
        } catch (Exception e) {
            throw new ProcessorException("Processor '" + getName() + "' has failed.", e);
        }
    }
}
