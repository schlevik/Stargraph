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

import com.typesafe.config.Config;
import net.stargraph.StarGraphException;
import net.stargraph.core.Stargraph;
import net.stargraph.core.index.IndexPopulator;
import net.stargraph.model.KBId;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.testng.Assert;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

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
        return prepareGenericTestEnv(kbID,
                "dataSets/obama/facts/triples.nt",
                "dataSets/obama/facts/triples.hdt"
        );
//
//        Path root;
//        try {
//            root = Files.createTempFile("stargraph-", "-dataDir");
//            Path factsPath = createPath(root, KBId.of(kbID, "facts"));
//            Path hdtPath = factsPath.resolve("triples.hdt");
//            Path ntFilePath = factsPath.resolve("triples.nt");
//            copyResource("dataSets/obama/facts/triples.hdt", hdtPath);
//            copyResource("dataSets/obama/facts/triples.nt", ntFilePath);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//        return root;
    }

    public static Path prepareGenericTestEnv(String kbID, String ntResourceLocation, String hdtResourceLocation) {
        Path root;
        try {
            root = Files.createTempFile("stargraph-", "-dataDir");
            Path factsPath = createPath(root, KBId.of(kbID, "facts"));
            Path ntFilePath = factsPath.resolve("triples.nt");
            copyResource(Paths.get(ntResourceLocation).toString(), ntFilePath);
            if (hdtResourceLocation != null) {
                Path hdtPath = factsPath.resolve("triples.hdt");
                copyResource(hdtResourceLocation, hdtPath);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return root;
    }

    public static void cleanUpTestEnv(Path root) {
        try {
            Files.walk(root, FileVisitOption.FOLLOW_LINKS)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void assertElasticRunning(Config... cfgs) {
        Arrays.asList(cfgs).forEach(cfg -> Assert.assertTrue(
                isElasticRunning(cfg),
                "Elastic search not running! Refer to test doc!"
                )
        );
    }

    public static boolean isElasticRunning(Config cfg) {
        String clusterName = cfg.getString("elastic.cluster-name");
        List<String> servers = cfg.getStringList("elastic.servers");
        return isElasticRunning(clusterName, servers);
    }

    public static boolean isElasticRunning(String clusterName, List<String> addresses) {
        Settings settings = Settings.builder().put("cluster.name", clusterName).build();
        TransportClient client = new PreBuiltTransportClient(settings);
        try {

            for (String addr : addresses) {
                String[] a = addr.split(":");
                String host = a[0];
                int port = Integer.parseInt(a[1]);
                client.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), port));
            }
        } catch (Exception e) {
            return false;
        }

        boolean running = !client.connectedNodes().isEmpty();
        client.close();
        return running;
    }

    public static void ensureLuceneIndexExists(Stargraph stargraph, KBId entityIndex) {
        boolean indexExists = true;
        try {
            stargraph.getSearcher(entityIndex).countDocuments();
        } catch (StarGraphException e) {
            if(e.getMessage().contains("Index not found")) {
                indexExists = false;
            }
        }
        if(!indexExists) {
        //if (!doesLuceneIndexExist(Paths.get(stargraph.getDataRootDir()), entityIndex)) {
            try {
                populateEntityIndex(stargraph.getIndexer(entityIndex));
            } catch (InterruptedException | TimeoutException | ExecutionException e) {
                throw new TestFailureException();
            }
        }
    }

    public static boolean doesLuceneIndexExist(Path path, KBId kbAndIndex) {
        Path idxPath = path.resolve(kbAndIndex.getId()).resolve(kbAndIndex.getModel()).resolve("idx");
        return Files.exists(idxPath);

    }

    public static void populateEntityIndex(IndexPopulator indexer) throws InterruptedException, TimeoutException, ExecutionException {
        indexer.load(true, -1);
        indexer.awaitLoader();

    }

    public static void assertCorefRunning(String location) {


        try {
            URL url = new URL(location);
            url.openConnection();
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.getResponseCode();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
