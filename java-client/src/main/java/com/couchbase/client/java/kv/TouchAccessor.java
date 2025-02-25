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

package com.couchbase.client.java.kv;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.error.*;
import com.couchbase.client.core.msg.ResponseStatus;
import com.couchbase.client.core.msg.kv.TouchRequest;

import java.util.concurrent.CompletableFuture;

import static com.couchbase.client.java.kv.DurabilityUtils.wrapWithDurability;

public class TouchAccessor {

  public static CompletableFuture<MutationResult> touch(final Core core, final TouchRequest request, final String key) {
    core.send(request);
    return request
      .response()
      .thenApply(response -> {
        if (response.status().success()) {
          return new MutationResult(response.cas(), response.mutationToken());
        }

        final KeyValueErrorContext ctx = KeyValueErrorContext.completedRequest(request, response.status());
        switch (response.status()) {
          case NOT_FOUND: throw new DocumentNotFoundException(ctx);
          case LOCKED: throw new DocumentLockedException(ctx);
          case OUT_OF_MEMORY: throw new ServerOutOfMemoryException(ctx);
          case TEMPORARY_FAILURE: // intended fallthrough to the case below
          case SERVER_BUSY: throw new TemporaryFailureException(ctx);
          case SYNC_WRITE_RE_COMMIT_IN_PROGRESS: throw new DurableWriteReCommitInProgressException(ctx);
          default: throw new CouchbaseException("Touch operation failed", ctx);
        }
      });
  }

}
