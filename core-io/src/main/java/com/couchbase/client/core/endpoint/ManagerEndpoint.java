package com.couchbase.client.core.endpoint;

import com.couchbase.client.core.io.netty.manager.ManagerMessageHandler;
import com.couchbase.client.core.service.ServiceContext;
import com.couchbase.client.core.service.ServiceType;
import com.couchbase.client.core.deps.io.netty.channel.ChannelPipeline;
import com.couchbase.client.core.deps.io.netty.handler.codec.http.HttpClientCodec;

public class ManagerEndpoint extends BaseEndpoint {

  public ManagerEndpoint(final ServiceContext ctx, final String hostname, final int port) {
    super(hostname, port, ctx.environment().ioEnvironment().managerEventLoopGroup().get(),
      ctx, ctx.environment().ioConfig().managerCircuitBreakerConfig(), ServiceType.MANAGER, false);
  }

  @Override
  protected PipelineInitializer pipelineInitializer() {
    return new ManagerPipelineInitializer(endpointContext());
  }

  public static class ManagerPipelineInitializer implements PipelineInitializer {

    private final EndpointContext endpointContext;

    public ManagerPipelineInitializer(EndpointContext endpointContext) {
      this.endpointContext = endpointContext;
    }

    @Override
    public void init(BaseEndpoint endpoint, ChannelPipeline pipeline) {
      pipeline.addLast(new HttpClientCodec());
      pipeline.addLast(new ManagerMessageHandler(endpoint, endpointContext));
    }
  }
}
