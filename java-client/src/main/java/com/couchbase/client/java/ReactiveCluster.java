/*
 * Copyright (c) 2018 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.couchbase.client.java;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.annotation.Stability;
import com.couchbase.client.core.env.Authenticator;
import com.couchbase.client.core.env.PasswordAuthenticator;
import com.couchbase.client.core.env.SeedNode;
import com.couchbase.client.core.error.ReducedAnalyticsErrorContext;
import com.couchbase.client.core.error.ReducedQueryErrorContext;
import com.couchbase.client.core.error.ReducedSearchErrorContext;
import com.couchbase.client.core.msg.search.SearchRequest;
import com.couchbase.client.java.analytics.AnalyticsAccessor;
import com.couchbase.client.java.analytics.AnalyticsOptions;
import com.couchbase.client.java.analytics.ReactiveAnalyticsResult;
import com.couchbase.client.java.codec.JsonSerializer;
import com.couchbase.client.java.diagnostics.DiagnosticsOptions;
import com.couchbase.client.java.env.ClusterEnvironment;
import com.couchbase.client.java.manager.analytics.ReactiveAnalyticsIndexManager;
import com.couchbase.client.java.manager.bucket.ReactiveBucketManager;
import com.couchbase.client.java.manager.query.AsyncQueryIndexManager;
import com.couchbase.client.java.manager.query.ReactiveQueryIndexManager;
import com.couchbase.client.java.manager.user.ReactiveUserManager;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.ReactiveQueryResult;
import com.couchbase.client.java.search.SearchAccessor;
import com.couchbase.client.java.search.SearchOptions;
import com.couchbase.client.java.search.SearchQuery;
import com.couchbase.client.java.search.result.ReactiveSearchResult;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

import static com.couchbase.client.core.util.Validators.notNull;
import static com.couchbase.client.java.AsyncCluster.extractClusterEnvironment;
import static com.couchbase.client.java.AsyncCluster.seedNodesFromConnectionString;
import static com.couchbase.client.java.ClusterOptions.clusterOptions;
import static com.couchbase.client.java.analytics.AnalyticsOptions.analyticsOptions;
import static com.couchbase.client.java.diagnostics.DiagnosticsOptions.diagnosticsOptions;
import static com.couchbase.client.java.query.QueryOptions.queryOptions;
import static com.couchbase.client.java.search.SearchOptions.searchOptions;

public class ReactiveCluster {

  static final QueryOptions DEFAULT_QUERY_OPTIONS = queryOptions();
  static final SearchOptions DEFAULT_SEARCH_OPTIONS = searchOptions();
  static final AnalyticsOptions DEFAULT_ANALYTICS_OPTIONS = analyticsOptions();
  static final DiagnosticsOptions DEFAULT_DIAGNOSTICS_OPTIONS = diagnosticsOptions();

  /**
   * Holds the underlying async cluster reference.
   */
  private final AsyncCluster asyncCluster;

  /**
   * Connect to a Couchbase cluster with a username and a password as credentials.
   *
   * @param connectionString connection string used to locate the Couchbase cluster.
   * @param username the name of the user with appropriate permissions on the cluster.
   * @param password the password of the user with appropriate permissions on the cluster.
   * @return if properly connected, returns a {@link ReactiveCluster}.
   */
  public static ReactiveCluster connect(final String connectionString, final String username,
                                        final String password) {
    return connect(connectionString, clusterOptions(PasswordAuthenticator.create(username, password)));
  }

  /**
   * Connect to a Couchbase cluster with custom {@link Authenticator}.
   *
   * @param connectionString connection string used to locate the Couchbase cluster.
   * @param options custom options when creating the cluster.
   * @return if properly connected, returns a {@link ReactiveCluster}.
   */
  public static ReactiveCluster connect(final String connectionString, final ClusterOptions options) {
    ClusterOptions.Built opts = options.build();
    Supplier<ClusterEnvironment> environmentSupplier = extractClusterEnvironment(connectionString, opts);

    Set<SeedNode> seedNodes;
    if (opts.seedNodes() != null && !opts.seedNodes().isEmpty()) {
      seedNodes = opts.seedNodes();
    } else {
      seedNodes = seedNodesFromConnectionString(connectionString, environmentSupplier.get());
    }
    return new ReactiveCluster(environmentSupplier, opts.authenticator(), seedNodes);
  }

  /**
   * Creates a new cluster from a {@link ClusterEnvironment}.
   *
   * @param environment the environment to use for this cluster.
   */
  private ReactiveCluster(final Supplier<ClusterEnvironment> environment, final Authenticator authenticator, Set<SeedNode> seedNodes) {
    this(new AsyncCluster(environment, authenticator, seedNodes));
  }

  /**
   * Creates a {@link ReactiveCluster} from an {@link AsyncCluster}.
   *
   * @param asyncCluster the underlying async cluster.
   */
  ReactiveCluster(final AsyncCluster asyncCluster) {
    this.asyncCluster = asyncCluster;
  }


  /**
   * Provides access to the underlying {@link Core}.
   *
   * <p>This is advanced API, use with care!</p>
   */
  @Stability.Volatile
  public Core core() {
    return asyncCluster.core();
  }

  /**
   * Provides access to the user management services.
   */
  @Stability.Volatile
  public ReactiveUserManager users() {
    return new ReactiveUserManager(asyncCluster.users());
  }

  /**
   * Provides access to the bucket management services.
   */
  @Stability.Volatile
  public ReactiveBucketManager buckets() {
    return new ReactiveBucketManager(async().buckets());
  }

  /**
   * Provides access to the Analytics index management services.
   */
  @Stability.Volatile
  public ReactiveAnalyticsIndexManager analyticsIndexes() {
    return new ReactiveAnalyticsIndexManager(async());
  }

  /**
   * Provides access to the N1QL index management services.
   */
  @Stability.Volatile
  public ReactiveQueryIndexManager queryIndexes() {
    return new ReactiveQueryIndexManager(new AsyncQueryIndexManager(async()));
  }

  /**
   * Provides access to the underlying {@link AsyncCluster}.
   */
  public AsyncCluster async() {
    return asyncCluster;
  }

  /**
   * Provides access to the configured {@link ClusterEnvironment} for this cluster.
   */
  public ClusterEnvironment environment() {
    return asyncCluster.environment();
  }

  /**
   * Performs a N1QL query with default {@link QueryOptions}.
   *
   * @param statement the N1QL query statement as a raw string.
   * @return the {@link ReactiveQueryResult} once the response arrives successfully.
   */
  public Mono<ReactiveQueryResult> query(final String statement) {
    return this.query(statement, DEFAULT_QUERY_OPTIONS);
  }

  /**
   * Performs a N1QL query with custom {@link QueryOptions}.
   *
   * @param statement the N1QL query statement as a raw string.
   * @param options the custom options for this query.
   * @return the {@link ReactiveQueryResult} once the response arrives successfully.
   */
  public Mono<ReactiveQueryResult> query(final String statement, final QueryOptions options) {
    notNull(options, "QueryOptions", () -> new ReducedQueryErrorContext(statement));
    final QueryOptions.Built opts = options.build();
    JsonSerializer serializer = opts.serializer() == null ? environment().jsonSerializer() : opts.serializer();
    return asyncCluster.queryAccessor().queryReactive(
      asyncCluster.queryRequest(statement, opts),
      opts,
      serializer
    );
  }

  /**
   * Performs an Analytics query with default {@link AnalyticsOptions}.
   *
   * @param statement the Analytics query statement as a raw string.
   * @return the {@link ReactiveAnalyticsResult} once the response arrives successfully.
   */
  public Mono<ReactiveAnalyticsResult> analyticsQuery(final String statement) {
    return analyticsQuery(statement, DEFAULT_ANALYTICS_OPTIONS);
  }


  /**
   * Performs an Analytics query with custom {@link AnalyticsOptions}.
   *
   * @param statement the Analytics query statement as a raw string.
   * @param options the custom options for this analytics query.
   * @return the {@link ReactiveAnalyticsResult} once the response arrives successfully.
   */
  public Mono<ReactiveAnalyticsResult> analyticsQuery(final String statement, final AnalyticsOptions options) {
    notNull(options, "AnalyticsOptions", () -> new ReducedAnalyticsErrorContext(statement));
    AnalyticsOptions.Built opts = options.build();
    JsonSerializer serializer = opts.serializer() == null ? environment().jsonSerializer() : opts.serializer();
    return AnalyticsAccessor.analyticsQueryReactive(
      asyncCluster.core(),
      asyncCluster.analyticsRequest(statement, opts),
      serializer
    );
  }

  /**
   * Performs a Full Text Search (FTS) query with default {@link SearchOptions}.
   *
   * @param query the query, in the form of a {@link SearchQuery}
   * @return the {@link SearchRequest} once the response arrives successfully, inside a {@link Mono}
   */
  public Mono<ReactiveSearchResult> searchQuery(final String indexName, SearchQuery query) {
    return searchQuery(indexName, query, DEFAULT_SEARCH_OPTIONS);
  }

  /**
   * Performs a Full Text Search (FTS) query with custom {@link SearchOptions}.
   *
   * @param query the query, in the form of a {@link SearchQuery}
   * @param options the custom options for this query.
   * @return the {@link SearchRequest} once the response arrives successfully, inside a {@link Mono}
   */
  public Mono<ReactiveSearchResult> searchQuery(final String indexName, final SearchQuery query, final SearchOptions options) {
    notNull(query, "SearchQuery", () -> new ReducedSearchErrorContext(indexName, null));
    notNull(options, "SearchOptions", () -> new ReducedSearchErrorContext(indexName, query.export().toMap()));
    SearchOptions.Built opts = options.build();
    JsonSerializer serializer = opts.serializer() == null ? environment().jsonSerializer() : opts.serializer();
    return SearchAccessor.searchQueryReactive(asyncCluster.core(), asyncCluster.searchRequest(indexName, query, opts), serializer);
  }

  /**
   * Opens a {@link ReactiveBucket} with the given name.
   *
   * @param bucketName the name of the bucket to open.
   * @return a {@link ReactiveBucket} once opened.
   */
  public ReactiveBucket bucket(final String bucketName) {
    return new ReactiveBucket(asyncCluster.bucket(bucketName));
  }

  /**
   * Performs a non-reversible disconnect of this {@link ReactiveCluster}.
   */
  public Mono<Void> disconnect() {
    return disconnect(environment().timeoutConfig().disconnectTimeout());
  }

  /**
   * Performs a non-reversible disconnect of this {@link ReactiveCluster}.
   *
   * @param timeout overriding the default disconnect timeout if needed.
   */
  public Mono<Void> disconnect(final Duration timeout) {
    return asyncCluster.disconnectInternal(timeout);
  }

}
