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

package com.couchbase.client.core.error;

import com.couchbase.client.core.msg.ResponseStatus;
import com.couchbase.client.core.msg.kv.KeyValueRequest;

import java.util.Map;

public class KeyValueErrorContext extends ErrorContext {

  private final KeyValueRequest<?> request;

  private KeyValueErrorContext(final KeyValueRequest<?> request, final ResponseStatus status) {
    super(status);
    this.request = request;
  }

  public static KeyValueErrorContext completedRequest(final KeyValueRequest<?> request, final ResponseStatus status) {
    return new KeyValueErrorContext(request, status);
  }

  public static KeyValueErrorContext incompleteRequest(final KeyValueRequest<?> request) {
    return new KeyValueErrorContext(request, null);
  }

  @Override
  public void injectExportableParams(final Map<String, Object> input) {
    super.injectExportableParams(input);
    if (request != null) {
      request.context().injectExportableParams(input);
    }
  }

}
