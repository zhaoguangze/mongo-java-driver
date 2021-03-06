/*
 * Copyright 2015 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.async.client;

import com.mongodb.Block;
import com.mongodb.Function;
import com.mongodb.MongoNamespace;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.async.AsyncBatchCursor;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.client.model.FindOptions;
import com.mongodb.client.model.MapReduceAction;
import com.mongodb.operation.AsyncOperationExecutor;
import com.mongodb.operation.MapReduceStatistics;
import com.mongodb.operation.MapReduceToCollectionOperation;
import com.mongodb.operation.MapReduceWithInlineResultsOperation;
import org.bson.BsonDocument;
import org.bson.BsonJavaScript;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static com.mongodb.ReadPreference.primary;
import static com.mongodb.assertions.Assertions.notNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

class MapReduceIterableImpl<TDocument, TResult> implements MapReduceIterable<TResult> {
    private final MongoNamespace namespace;
    private final Class<TDocument> documentClass;
    private final Class<TResult> resultClass;
    private final ReadPreference readPreference;
    private final ReadConcern readConcern;
    private final CodecRegistry codecRegistry;
    private final AsyncOperationExecutor executor;
    private final String mapFunction;
    private final String reduceFunction;

    private boolean inline = true;
    private String collectionName;
    private String finalizeFunction;
    private Bson scope;
    private Bson filter;
    private Bson sort;
    private int limit;
    private boolean jsMode;
    private boolean verbose = true;
    private long maxTimeMS;
    private MapReduceAction action = MapReduceAction.REPLACE;
    private String databaseName;
    private boolean sharded;
    private boolean nonAtomic;
    private int batchSize;
    private Boolean bypassDocumentValidation;

    MapReduceIterableImpl(final MongoNamespace namespace, final Class<TDocument> documentClass, final Class<TResult> resultClass,
                          final CodecRegistry codecRegistry, final ReadPreference readPreference, final ReadConcern readConcern,
                          final AsyncOperationExecutor executor, final String mapFunction, final String reduceFunction) {
        this.namespace = notNull("namespace", namespace);
        this.documentClass = notNull("documentClass", documentClass);
        this.resultClass = notNull("resultClass", resultClass);
        this.codecRegistry = notNull("codecRegistry", codecRegistry);
        this.readPreference = notNull("readPreference", readPreference);
        this.readConcern = notNull("readConcern", readConcern);
        this.executor = notNull("executor", executor);
        this.mapFunction = notNull("mapFunction", mapFunction);
        this.reduceFunction = notNull("reduceFunction", reduceFunction);
    }

    @Override
    public MapReduceIterable<TResult> collectionName(final String collectionName) {
        this.collectionName = notNull("collectionName", collectionName);
        this.inline = false;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> finalizeFunction(final String finalizeFunction) {
        this.finalizeFunction = finalizeFunction;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> scope(final Bson scope) {
        this.scope = scope;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> sort(final Bson sort) {
        this.sort = sort;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> filter(final Bson filter) {
        this.filter = filter;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> limit(final int limit) {
        this.limit = limit;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> jsMode(final boolean jsMode) {
        this.jsMode = jsMode;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> verbose(final boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> maxTime(final long maxTime, final TimeUnit timeUnit) {
        notNull("timeUnit", timeUnit);
        this.maxTimeMS = TimeUnit.MILLISECONDS.convert(maxTime, timeUnit);
        return this;
    }

    @Override
    public MapReduceIterable<TResult> action(final MapReduceAction action) {
        this.action = action;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> databaseName(final String databaseName) {
        this.databaseName = databaseName;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> sharded(final boolean sharded) {
        this.sharded = sharded;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> nonAtomic(final boolean nonAtomic) {
        this.nonAtomic = nonAtomic;
        return this;
    }

    @Override
    public MapReduceIterable<TResult> batchSize(final int batchSize) {
        this.batchSize = batchSize;
        return this;
    }


    @Override
    public MapReduceIterable<TResult> bypassDocumentValidation(final Boolean bypassDocumentValidation) {
        this.bypassDocumentValidation = bypassDocumentValidation;
        return this;
    }

    @Override
    public void toCollection(final SingleResultCallback<Void> callback) {
        notNull("callback", callback);
        if (inline) {
            throw new IllegalArgumentException("The options must specify a non-inline result");
        }
        executor.execute(createMapReduceToCollectionOperation(),
                new SingleResultCallback<MapReduceStatistics>() {
                    @Override
                    public void onResult(final MapReduceStatistics result, final Throwable t) {
                        callback.onResult(null, t);
                    }
                });
    }

    @Override
    public void first(final SingleResultCallback<TResult> callback) {
        notNull("callback", callback);
        execute().first(callback);
    }

    @Override
    public void forEach(final Block<? super TResult> block, final SingleResultCallback<Void> callback) {
        notNull("block", block);
        notNull("callback", callback);
        execute().forEach(block, callback);
    }

    @Override
    public <A extends Collection<? super TResult>> void into(final A target, final SingleResultCallback<A> callback) {
        notNull("target", target);
        notNull("callback", callback);
        execute().into(target, callback);
    }

    @Override
    public <U> MongoIterable<U> map(final Function<TResult, U> mapper) {
        return new MappingIterable<TResult, U>(this, mapper);
    }

    @Override
    public void batchCursor(final SingleResultCallback<AsyncBatchCursor<TResult>> callback) {
        notNull("callback", callback);
        execute().batchCursor(callback);
    }

    MongoIterable<TResult> execute() {
        if (inline) {
            MapReduceWithInlineResultsOperation<TResult> operation =
                    new MapReduceWithInlineResultsOperation<TResult>(namespace,
                            new BsonJavaScript(mapFunction),
                            new BsonJavaScript(reduceFunction),
                            codecRegistry.get(resultClass))
                            .filter(toBsonDocument(filter))
                            .limit(limit)
                            .maxTime(maxTimeMS, MILLISECONDS)
                            .jsMode(jsMode)
                            .scope(toBsonDocument(scope))
                            .sort(toBsonDocument(sort))
                            .verbose(verbose)
                            .readConcern(readConcern);
            if (finalizeFunction != null) {
                operation.finalizeFunction(new BsonJavaScript(finalizeFunction));
            }
            return new OperationIterable<TResult>(operation, readPreference, executor);
        } else {
            MapReduceToCollectionOperation operation = createMapReduceToCollectionOperation();

            String dbName = databaseName != null ? databaseName : namespace.getDatabaseName();
            MongoIterable<TResult> delegated = new FindIterableImpl<TDocument, TResult>(new MongoNamespace(dbName, collectionName),
                    documentClass, resultClass, codecRegistry, primary(), readConcern, executor, new BsonDocument(),
                    new FindOptions()).batchSize(batchSize);
            return new AwaitingWriteOperationIterable<TResult, MapReduceStatistics>(operation, executor, delegated);
        }
    }

    private MapReduceToCollectionOperation createMapReduceToCollectionOperation() {
        MapReduceToCollectionOperation operation = new MapReduceToCollectionOperation(namespace, new BsonJavaScript(mapFunction),
                new BsonJavaScript(reduceFunction), collectionName)
                .filter(toBsonDocument(filter))
                .limit(limit)
                .maxTime(maxTimeMS, MILLISECONDS)
                .jsMode(jsMode)
                .scope(toBsonDocument(scope))
                .sort(toBsonDocument(sort))
                .verbose(verbose)
                .action(action.getValue())
                .nonAtomic(nonAtomic)
                .sharded(sharded)
                .databaseName(databaseName)
                .bypassDocumentValidation(bypassDocumentValidation);

        if (finalizeFunction != null) {
            operation.finalizeFunction(new BsonJavaScript(finalizeFunction));
        }
        return operation;
    }

    private BsonDocument toBsonDocument(final Bson document) {
        return document == null ? null : document.toBsonDocument(documentClass, codecRegistry);
    }
}
