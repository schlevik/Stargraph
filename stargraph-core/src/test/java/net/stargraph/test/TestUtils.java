package net.stargraph.test;

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

import net.stargraph.model.KBId;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

public final class TestUtils {

    public static Path createPath(Path root, KBId kbId) throws IOException {
        Files.delete(root);
        return Files.createDirectories(root.resolve(kbId.getId()).resolve(kbId.getModel()));
    }

    public static File copyResource(String resourceLocation, Path target) {
        try {
            InputStream stream = ClassLoader.getSystemResourceAsStream(resourceLocation);
            if (stream == null) {
                throw new RuntimeException("Can't locate: " + resourceLocation);
            }
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
            return target.toFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static Path prepareObamaTestEnv(String kbID) {
        Path root;
        try {
            root = Files.createTempFile("stargraph-", "-dataDir");
            Path factsPath = createPath(root, KBId.of(kbID, "facts"));
            Path hdtPath = factsPath.resolve("triples.hdt");
            Path ntFilePath = factsPath.resolve("triples.nt");
            copyResource("dataSets/obama/facts/triples.hdt", hdtPath);
            copyResource("dataSets/obama/facts/triples.nt", ntFilePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return root;
    }

    public static void cleanUpObamaTestEnv(Path root) {
        try {
            Files.walk(root, FileVisitOption.FOLLOW_LINKS)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .peek(System.out::println)
                    .forEach(File::delete);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
