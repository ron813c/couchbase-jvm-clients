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

import com.couchbase.client.java.codec.Transcoder;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static com.couchbase.client.core.logging.RedactableArgument.redactUser;

/**
 * Extends GetResult to include additional information for get-from-replica style calls.
 *
 * @since 3.0.0
 */
public class GetReplicaResult extends GetResult {

  private final boolean isReplica;

  /**
   * Creates a new {@link GetReplicaResult}.
   *
   * @param cas the cas from the doc.
   * @param expiration the expiration if fetched from the doc.
   * @param isReplica whether the active/master replica returned this result
   */
  private GetReplicaResult(final byte[] content,
                   final int flags,
                   final long cas,
                   final Optional<Duration> expiration,
                   Transcoder transcoder,
                   boolean isReplica) {
    super(content, flags, cas, expiration, transcoder);
    this.isReplica = isReplica;
  }

  public static GetReplicaResult from(GetResult response, boolean isReplica) {
    return new GetReplicaResult(response.content,
            response.flags,
            response.cas(),
            response.expiry(),
            response.transcoder,
      isReplica);
  }

  /**
   * Returns whether the replica that returned this result was the master.
   */
  public boolean isReplica() {
    return isReplica;
  }

  @Override
  public String toString() {
    return "GetReplicaResult{" +
            "content=" + redactUser(Arrays.toString(content)) +
            ", flags=" + flags +
            ", cas=" + cas() +
            ", expiration=" + expiry() +
            ", isReplica=" + isReplica +
            '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GetReplicaResult getResult = (GetReplicaResult) o;

    if (isReplica != getResult.isReplica) return false;
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(content, flags, cas(), expiry(), isReplica);
  }
}
