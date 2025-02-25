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
import com.couchbase.client.java.codec.TypeRef;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static com.couchbase.client.core.logging.RedactableArgument.redactUser;

/**
 * Returned from all kinds of KeyValue Get operation to fetch a document or a subset of it.
 *
 * @since 3.0.0
 */
public class GetResult {

  /**
   * The encoded content when loading the document.
   */
  protected final byte[] content;

  /**
   * The flags from the kv operation.
   */
  protected final int flags;

  /**
   * The CAS of the fetched document.
   */
  private final long cas;

  /**
   * The expiry if fetched and present.
   */
  private final Optional<Duration> expiry;

  /**
   * The default transcoder which should be used.
   */
  protected final Transcoder transcoder;

  /**
   * Creates a new {@link GetResult}.
   *
   * @param cas the cas from the doc.
   * @param expiry the expiry if fetched from the doc.
   */
  GetResult(final byte[] content, final int flags, final long cas, final Optional<Duration> expiry, Transcoder transcoder) {
    this.cas = cas;
    this.content = content;
    this.flags = flags;
    this.expiry = expiry;
    this.transcoder = transcoder;
  }

  /**
   * Returns the CAS value of the loaded document.
   */
  public long cas() {
    return cas;
  }

  /**
   * If present, returns the expiry of the loaded document.
   *
   * <p>Note that the duration represents the time when the document has been loaded and can only
   * ever be an approximation.</p>
   */
  public Optional<Duration> expiry() {
    return expiry;
  }

  /**
   * Decodes the content of the document into a {@link JsonObject}.
   */
  public JsonObject contentAsObject() {
    return contentAs(JsonObject.class);
  }

  /**
   * Decodes the content of the document into a {@link JsonArray}.
   */
  public JsonArray contentAsArray() {
    return contentAs(JsonArray.class);
  }

  /**
   * Decodes the content of the document into an instance of the target class.
   *
   * @param target the target class to decode the encoded content into.
   */
  public <T> T contentAs(final Class<T> target) {
    return transcoder.decode(target, content, flags);
  }

  /**
   * Decodes the content of the document into an instance of the target type.
   * Example usage:
   * <pre>
   * List&lt;String> strings = result.contentAs(new TypeRef&lt;List&lt;String>>(){});
   * </pre>
   *
   * @param target the type to decode the encoded content into.
   */
  public <T> T contentAs(final TypeRef<T> target) {
    return transcoder.decode(target, content, flags);
  }

  @Override
  public String toString() {
    return "GetResult{" +
      "content=" + redactUser(Arrays.toString(content)) +
      ", flags=" + flags +
      ", cas=" + cas +
      ", expiry=" + expiry +
      '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GetResult getResult = (GetResult) o;
    return flags == getResult.flags &&
      cas == getResult.cas &&
      Arrays.equals(content, getResult.content) &&
      Objects.equals(expiry, getResult.expiry) &&
      Objects.equals(transcoder, getResult.transcoder);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(flags, cas, expiry, transcoder);
    result = 31 * result + Arrays.hashCode(content);
    return result;
  }
}
