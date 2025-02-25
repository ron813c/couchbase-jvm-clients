/*
 * Copyright (c) 2016 Couchbase, Inc.
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

package com.couchbase.client.core.error;

/**
 * Naming TBD!
 * The synchronous replication durability work can return an ambiguous error (or we timeout waiting for the response,
 * which is effectively the same).  Here we know the change is on a majority of replicas, or it's on none.
 */
public class DurabilityAmbiguousException extends CouchbaseException {

    public DurabilityAmbiguousException(final KeyValueErrorContext ctx) {
        super("The server returned with a durability ambiguous response on this request", ctx);
    }
}
