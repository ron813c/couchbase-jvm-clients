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

package com.couchbase.client.tracing.opentracing;

import com.couchbase.client.core.cnc.RequestSpan;
import com.couchbase.client.core.cnc.RequestTracer;
import io.opentracing.Span;
import io.opentracing.Tracer;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static com.couchbase.client.core.util.Validators.notNullOrEmpty;

/**
 * Wraps the OpenTracing tracer so it is suitable to be passed in into the couchbase environment and picked up
 * by the rest of the SDK as a result.
 */
public class OpenTracingRequestTracer implements RequestTracer {

  /**
   * Holds the actual OT tracer.
   */
  private final Tracer tracer;

  /**
   * Wraps the OpenTracing tracer and returns a datatype that can be passed into the requestTracer method of the
   * environment.
   *
   * @param tracer the tracer to wrap.
   * @return the wrapped tracer ready to be passed in.
   */
  public static OpenTracingRequestTracer wrap(final Tracer tracer) {
    return new OpenTracingRequestTracer(tracer);
  }

  private OpenTracingRequestTracer(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public OpenTracingInternalSpan span(final String operationName, final RequestSpan requestSpan) {
    notNullOrEmpty(operationName, "OperationName");
    Span parent = null;
    if (requestSpan != null) {
      if (requestSpan instanceof OpenTracingRequestSpan) {
        parent = ((OpenTracingRequestSpan) requestSpan).span();
      } else {
        throw new IllegalArgumentException("RequestSpan must be of type OpenTracingRequestSpan");
      }
    }
    return new OpenTracingInternalSpan(tracer, parent, operationName);
  }

  @Override
  public Mono<Void> start() {
    return Mono.empty(); // Tracer is not started by us

  }

  @Override
  public Mono<Void> stop(Duration timeout) {
    return Mono.empty(); // Tracer should not be stopped by us
  }

}
