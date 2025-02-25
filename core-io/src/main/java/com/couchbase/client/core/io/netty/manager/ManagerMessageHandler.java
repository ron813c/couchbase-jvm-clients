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

package com.couchbase.client.core.io.netty.manager;

import com.couchbase.client.core.CoreContext;
import com.couchbase.client.core.cnc.EventBus;
import com.couchbase.client.core.cnc.events.io.ChannelClosedProactivelyEvent;
import com.couchbase.client.core.cnc.events.io.InvalidRequestDetectedEvent;
import com.couchbase.client.core.cnc.events.io.UnsupportedResponseTypeReceivedEvent;
import com.couchbase.client.core.deps.io.netty.buffer.ByteBuf;
import com.couchbase.client.core.deps.io.netty.channel.ChannelDuplexHandler;
import com.couchbase.client.core.deps.io.netty.channel.ChannelHandlerContext;
import com.couchbase.client.core.deps.io.netty.channel.ChannelPromise;
import com.couchbase.client.core.deps.io.netty.handler.codec.http.FullHttpRequest;
import com.couchbase.client.core.deps.io.netty.handler.codec.http.HttpContent;
import com.couchbase.client.core.deps.io.netty.handler.codec.http.HttpHeaderNames;
import com.couchbase.client.core.deps.io.netty.handler.codec.http.HttpResponse;
import com.couchbase.client.core.deps.io.netty.handler.codec.http.LastHttpContent;
import com.couchbase.client.core.deps.io.netty.util.ReferenceCountUtil;
import com.couchbase.client.core.endpoint.BaseEndpoint;
import com.couchbase.client.core.io.IoContext;
import com.couchbase.client.core.msg.Response;
import com.couchbase.client.core.msg.manager.BucketConfigStreamingRequest;
import com.couchbase.client.core.msg.manager.BucketConfigStreamingResponse;
import com.couchbase.client.core.msg.manager.ManagerRequest;
import com.couchbase.client.core.retry.RetryOrchestrator;
import com.couchbase.client.core.retry.RetryReason;
import com.couchbase.client.core.service.ServiceType;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static com.couchbase.client.core.io.netty.HttpProtocol.remoteHttpHost;

/**
 * This handler dispatches requests and responses against the cluster manager service.
 *
 * <p>Note that since one of the messages is a long streaming connection to get continuous
 * updates on configs, the channel might be occupied for a long time. As a result, the upper
 * layers (service pooling) need to be responsible for opening another handler if all the
 * current ones are occupied.</p>
 *
 * @since 1.0.0
 */
public class ManagerMessageHandler extends ChannelDuplexHandler {

  private final CoreContext coreContext;
  private IoContext ioContext;
  private ManagerRequest<Response> currentRequest;

  /**
   * Holds the bucket streaming response, but only if the current request in question
   * is one.
   */
  private BucketConfigStreamingResponse streamingResponse;

  /**
   * Stores the remote host for caching purposes.
   */
  private String remoteHost;

  private ByteBuf currentContent;
  private HttpResponse currentResponse;
  private final EventBus eventBus;
  private final BaseEndpoint endpoint;

  public ManagerMessageHandler(final BaseEndpoint endpoint, final CoreContext coreContext) {
    this.endpoint = endpoint;
    this.coreContext = coreContext;
    this.eventBus = coreContext.environment().eventBus();
  }

  @Override
  public void channelActive(final ChannelHandlerContext ctx) {
    ioContext = new IoContext(
      coreContext,
      ctx.channel().localAddress(),
      ctx.channel().remoteAddress(),
      Optional.empty()
    );

    remoteHost = remoteHttpHost(ctx.channel().remoteAddress());
    currentContent = ctx.alloc().buffer();
    ctx.fireChannelActive();
  }

  @Override
  @SuppressWarnings({ "unchecked" })
  public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
    if (msg instanceof ManagerRequest) {
      if (currentRequest != null) {
        RetryOrchestrator.maybeRetry(coreContext, (ManagerRequest<Response>) msg, RetryReason.NOT_PIPELINED_REQUEST_IN_FLIGHT);
        if (endpoint != null) {
          endpoint.decrementOutstandingRequests();
        }
        return;
      }

      try {
        currentRequest = (ManagerRequest<Response>) msg;
        FullHttpRequest encoded = currentRequest.encode();
        encoded.headers().set(HttpHeaderNames.HOST, remoteHost);
        encoded.headers().set(HttpHeaderNames.USER_AGENT, endpoint.endpointContext().environment().userAgent().formattedLong());
        ctx.writeAndFlush(encoded);
      } catch (Throwable t) {
        currentRequest.response().completeExceptionally(t);
        if (endpoint != null) {
          endpoint.decrementOutstandingRequests();
        }
      }
      currentContent.clear();
    } else {
      if (endpoint != null) {
        endpoint.decrementOutstandingRequests();
      }
      eventBus.publish(new InvalidRequestDetectedEvent(ioContext, ServiceType.MANAGER, msg));
      ctx.channel().close().addListener(f -> eventBus.publish(new ChannelClosedProactivelyEvent(
        ioContext,
        ChannelClosedProactivelyEvent.Reason.INVALID_REQUEST_DETECTED)
      ));
    }
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (msg instanceof HttpResponse) {
      currentResponse = ((HttpResponse) msg);

      if (isStreamingConfigRequest()) {
        streamingResponse = (BucketConfigStreamingResponse) currentRequest.decode(currentResponse, null);
        currentRequest.succeed(streamingResponse);
      }
    } else if (msg instanceof HttpContent) {
      currentContent.writeBytes(((HttpContent) msg).content());

      if (isStreamingConfigRequest()) {
        // there might be more than one config in the full batch, so keep iterating until all are pushed
        while (true) {
          String encodedConfig = currentContent.toString(StandardCharsets.UTF_8);
          int separatorIndex = encodedConfig.indexOf("\n\n\n\n");
          // if -1 is returned it means that no full config has been located yet, need to wait for more chunks
          if (separatorIndex >= 0) {
            String content = encodedConfig.substring(0, separatorIndex);
            streamingResponse.pushConfig(content.trim());
            currentContent.clear();
            currentContent.writeBytes(encodedConfig.substring(separatorIndex + 4).getBytes(StandardCharsets.UTF_8));
          } else {
            break;
          }
        }
      }

      if (msg instanceof LastHttpContent) {
        if (isStreamingConfigRequest()) {
          streamingResponse.completeStream();
          streamingResponse = null;
        } else {
          byte[] copy = new byte[currentContent.readableBytes()];
          currentContent.readBytes(copy);
          Response response = currentRequest.decode(currentResponse, copy);
          currentRequest.succeed(response);
        }

        currentRequest = null;
        if (endpoint != null) {
          endpoint.markRequestCompletion();
        }
      }
    } else {
      ioContext.environment().eventBus().publish(
        new UnsupportedResponseTypeReceivedEvent(ioContext, msg)
      );
    }

    ReferenceCountUtil.release(msg);
  }

  private boolean isStreamingConfigRequest() {
    return BucketConfigStreamingRequest.class.isAssignableFrom(currentRequest.getClass());
  }

  @Override
  public void channelInactive(final ChannelHandlerContext ctx) {
    ReferenceCountUtil.release(currentContent);
    if (streamingResponse != null) {
      streamingResponse.completeStream();
    }
    if (currentRequest != null) {
      RetryOrchestrator.maybeRetry(ioContext, currentRequest, RetryReason.CHANNEL_CLOSED_WHILE_IN_FLIGHT);
    }
    ctx.fireChannelInactive();
  }

}
