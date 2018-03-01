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

import com.fasterxml.jackson.core.JsonProcessingException;
import net.stargraph.StarGraphException;
import net.stargraph.core.Stargraph;
import net.stargraph.core.index.BaseIndexPopulator;
import net.stargraph.core.index.IndexingException;
import net.stargraph.model.IndexID;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.forcemerge.ForceMergeResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.UUIDs;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexNotFoundException;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * IndexPopulator backed by Elastic Search engine.
 */
public final class ElasticIndexPopulator extends BaseIndexPopulator {

    private ElasticClient esClient;
    private BulkProcessor bulkProcessor;
    private ConcurrentHashMap<String, IndexRequest> indexRequests;

    public ElasticIndexPopulator(IndexID indexID, Stargraph core) {
        super(indexID, core);
        this.indexRequests = new ConcurrentHashMap<>();
    }

    @Override
    protected void beforeLoad(boolean reset) {
        try {
            IndicesExistsResponse res = esClient.prepareExists().get();

            if (!res.isExists()) {
                createIndex();
            } else {
                if (reset) {
                    deleteAll();
                }
            }
        } catch (Exception e) {
            throw new IndexingException(e);
        }
    }

    @Override
    protected void afterLoad() throws InterruptedException {
        if (bulkProcessor != null) {
            logger.info(marker, "Waiting for transport to serialize all remaining documents.");

            if (!bulkProcessor.awaitClose(120, TimeUnit.MINUTES)) {
                logger.warn(marker, "Closing time expired BEFORE sending all documents!");
            }

            logger.info(marker, "Optimizing index for reading..");
            ForceMergeResponse res = esClient.prepareForceMerge().get();
            if (res.getFailedShards() != 0) {
                logger.warn(marker, "An error was detected during optimization. Check logs.");
            }

            if (!indexRequests.isEmpty()) {
                logger.error(marker, "Still pending {} index requests!?", indexRequests.size()); // should not happen
                indexRequests.clear();
            }
        }
    }

    @Override
    protected void doDeleteAll() {
        try {
            deleteIndex();
        } catch (IndexNotFoundException e) {
            logger.warn("Tried deleting non-existent index. Don't worry, nothing happened.");
        }
        createIndex();
    }

    @Override
    protected void doFlush() {
        logger.trace(marker, "Entering flush..");
        logger.debug(marker, "..flushing bulkProcessor..");
        bulkProcessor.flush();
        logger.debug(marker, "..flushed bulkProcessor..");
        try {
            //TODO: Figure out why flushing is not always being honored without delaying.
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.debug(marker, "..slept for 2 seconds..");
        ForceMergeResponse res = esClient.prepareForceMerge().get();
        logger.debug(marker, "..forced merge..");
        if (res.getFailedShards() > 0) {
            logger.warn("Flush request failure detected on {}", indexID);
        }
    }

    @Override
    protected void onStart() {
        this.esClient = new ElasticClient(stargraph, this.indexID);
        this.bulkProcessor = createBulkProcessor();
    }

    @Override
    protected void onStop() {
        try {
            doFlush();
            if (!bulkProcessor.awaitClose(30, TimeUnit.SECONDS)) {
                logger.warn(marker, "Time out after waiting pending requests.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (this.esClient != null) {
            this.esClient.getTransport().close();
        }
    }


    @Override
    protected void doIndex(Serializable data, IndexID indexID) throws InterruptedException {
        if (bulkProcessor == null) {
            throw new StarGraphException("Back-end is unreachable now.");
        }


        try {
            final String id = UUIDs.base64UUID();
            IndexRequest request = esClient.createIndexRequest(id, true);
            this.indexRequests.put(id, request);
            bulkProcessor.add(request.source(mapper.writeValueAsBytes(data)));
        } catch (JsonProcessingException e) {
            throw new IndexingException(e);
        }
    }

    private void createIndex() {
        Settings settings = Settings.builder()
                .put("index.refresh_interval", "-1")
                .put("index.translog.sync_interval", "10s")
                .put("index.translog.durability", "async")
                .put("index.number_of_replicas", "0")
                .build();

        CreateIndexResponse res = esClient.prepareCreate().setSettings(settings).get();

        if (!res.isAcknowledged()) {
            throw new IndexingException("Fail to create index for " + this.indexID);
        }
    }

    private void deleteIndex() {
        DeleteIndexResponse deleteRes = esClient.prepareDelete().get();
        if (!deleteRes.isAcknowledged()) {
            throw new IndexingException("Fail to delete old index.");
        }
    }


    private BulkProcessor createBulkProcessor() {
        int processors = Runtime.getRuntime().availableProcessors();
        processors = processors > 1 ? processors - 1 : 1;
        int concurrency = stargraph.getModelConfig(indexID).getInt("elastic.bulk.concurrency");
        concurrency = concurrency > 0 ? concurrency : processors;
        int bulkActions = stargraph.getModelConfig(indexID).getInt("elastic.bulk.actions");

        logger.info(marker, "Creating Bulk Processor. Concurrency = {}, actions = {}.", concurrency, bulkActions);

        return BulkProcessor.builder(esClient.getTransport(), new BulkProcessor.Listener() {
            @Override
            public void beforeBulk(long executionId, BulkRequest request) {
                logger.trace(marker, "Sending {} request(s) in bulk.", request.numberOfActions());
            }

            @Override
            public void afterBulk(long executionId, BulkRequest bulkRequest, BulkResponse bulkResponse) {
                if (bulkResponse.hasFailures()) {
                    logger.error(marker, "Bulk id={} has failures", executionId);
                    for (BulkItemResponse res : bulkResponse) {
                        if (res.isFailed()) {
                            IndexRequest indexRequest = (IndexRequest) bulkRequest.requests().get(res.getItemId());
                            logger.error(marker, "Failed to index {}", indexRequest, res.getFailure().getCause());
                        }
                    }
                }

                clearRef(bulkRequest);
            }

            @Override
            public void afterBulk(long executionId, BulkRequest request, Throwable failure) {
                logger.error(marker, "Bulk {} failed.", executionId, failure);
                request.requests().forEach(r -> {
                    logger.error(marker, "Not Indexed {}", r);
                });

                clearRef(request);
            }
        }).setBulkActions(bulkActions).setConcurrentRequests(concurrency).build();
    }

    private void clearRef(BulkRequest bulkRequest) {
        if (bulkRequest == null) {
            logger.error(marker, "bulkRequest argument is mandatory!");
        } else {
            bulkRequest.requests().forEach(r -> {
                if (indexRequests.remove(((IndexRequest) r).id()) == null) {
                    logger.warn(marker, "Attempt to remove unmapped index request!");
                }
            });
        }
    }


}
