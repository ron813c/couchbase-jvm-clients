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

import com.couchbase.client.core.msg.RequestContext;
import com.couchbase.client.core.msg.ResponseStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SearchErrorContext extends ErrorContext {

  private final RequestContext requestContext;
  private final int httpStatus;

  public SearchErrorContext(final ResponseStatus responseStatus, final RequestContext requestContext, final int httpStatus) {
    super(responseStatus);
    this.requestContext = requestContext;
    this.httpStatus = httpStatus;
  }

  public RequestContext requestContext() {
    return requestContext;
  }


  @Override
  public void injectExportableParams(final Map<String, Object> input) {
    super.injectExportableParams(input);
    if (requestContext != null) {
      requestContext.injectExportableParams(input);
    }
    input.put("httpStatus", httpStatus);
  }

}
