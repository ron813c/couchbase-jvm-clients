/*
 * Copyright (c) 2019 Couchbase, Inc.
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

package com.couchbase.client.core.io.netty.analytics;

import com.couchbase.client.core.endpoint.BaseEndpoint;
import com.couchbase.client.core.endpoint.EndpointContext;
import com.couchbase.client.core.io.netty.chunk.ChunkedMessageHandler;
import com.couchbase.client.core.msg.analytics.AnalyticsChunkHeader;
import com.couchbase.client.core.msg.analytics.AnalyticsChunkRow;
import com.couchbase.client.core.msg.analytics.AnalyticsChunkTrailer;
import com.couchbase.client.core.msg.analytics.AnalyticsRequest;
import com.couchbase.client.core.msg.analytics.AnalyticsResponse;

public class AnalyticsMessageHandler
  extends ChunkedMessageHandler<AnalyticsChunkHeader, AnalyticsChunkRow, AnalyticsChunkTrailer, AnalyticsResponse, AnalyticsRequest> {

  public AnalyticsMessageHandler(BaseEndpoint endpoint, EndpointContext endpointContext) {
    super(endpoint, endpointContext, new AnalyticsChunkResponseParser());
  }

}
