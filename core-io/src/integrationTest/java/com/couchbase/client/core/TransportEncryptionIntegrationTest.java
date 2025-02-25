/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.couchbase.client.core;

import com.couchbase.client.core.cnc.Event;
import com.couchbase.client.core.cnc.EventBus;
import com.couchbase.client.core.cnc.events.endpoint.EndpointConnectionFailedEvent;
import com.couchbase.client.core.cnc.events.io.SecureConnectionFailedEvent;
import com.couchbase.client.core.env.CoreEnvironment;
import com.couchbase.client.core.env.SecurityConfig;
import com.couchbase.client.core.env.SeedNode;
import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.core.msg.kv.GetRequest;
import com.couchbase.client.core.msg.kv.GetResponse;
import com.couchbase.client.core.msg.kv.InsertRequest;
import com.couchbase.client.core.msg.kv.InsertResponse;
import com.couchbase.client.core.util.CoreIntegrationTest;
import com.couchbase.client.test.ClusterType;
import com.couchbase.client.test.IgnoreWhen;
import com.couchbase.client.test.Services;
import com.couchbase.client.core.deps.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import com.couchbase.client.util.SimpleEventBus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.net.ssl.TrustManagerFactory;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.couchbase.client.test.Util.waitUntilCondition;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * Verifies that the core is able to connect and perform operations over encrypted
 * transports.
 *
 * <p>Note that since the mock does not support encrypted connections, they are ignored
 * on it.</p>
 */
class TransportEncryptionIntegrationTest extends CoreIntegrationTest {

  /**
   * Helper method to configure the secure environment based on the integration seed nodes
   * from the target cluster and the security config from each test.
   *
   * @param config the security config to use.
   * @return a core environment, set up for encrypted networking.
   */
  private CoreEnvironment secureEnvironment(final SecurityConfig.Builder config, EventBus customEventBus) {
    CoreEnvironment.Builder builder = environment().securityConfig(config);

    if (customEventBus != null) {
      builder.eventBus(customEventBus);
    }

    return builder.build();
  }

  private Set<SeedNode> secureSeeds() {
    return config().nodes().stream().map(cfg -> SeedNode.create(
      cfg.hostname(),
      Optional.of(cfg.ports().get(Services.KV_TLS)),
      Optional.of(cfg.ports().get(Services.MANAGER_TLS))
    )).collect(Collectors.toSet());
  }

  @Test
  @IgnoreWhen(clusterTypes = { ClusterType.MOCKED })
  void performsKeyValueIgnoringServerCert() throws Exception {
    CoreEnvironment env = secureEnvironment(SecurityConfig
      .enableTls(true)
      .trustManagerFactory(InsecureTrustManagerFactory.INSTANCE), null);
    Core core = Core.create(env, authenticator(), secureSeeds());
    core.openBucket(config().bucketname());

    waitUntilCondition(() -> core.clusterConfig().hasClusterOrBucketConfig());

    try {
      String id = UUID.randomUUID().toString();
      byte[] content = "hello, world".getBytes(UTF_8);

      InsertRequest insertRequest = new InsertRequest(id, content, 0, 0,
        Duration.ofSeconds(1), core.context(), CollectionIdentifier.fromDefault(config().bucketname()), env.retryStrategy(), Optional.empty());
      core.send(insertRequest);

      InsertResponse insertResponse = insertRequest.response().get();
      assertTrue(insertResponse.status().success());

      GetRequest getRequest = new GetRequest(id, Duration.ofSeconds(1),
        core.context(), CollectionIdentifier.fromDefault(config().bucketname()), env.retryStrategy());
      core.send(getRequest);

      GetResponse getResponse = getRequest.response().get();
      assertTrue(getResponse.status().success());
      assertArrayEquals(content, getResponse.content());
      assertTrue(getResponse.cas() != 0);
    } finally {
      core.shutdown().block();
      env.shutdown();
    }
  }

  @Test
  @IgnoreWhen(clusterTypes = { ClusterType.MOCKED })
  @Disabled("With hostname validation the server cert SAN validation fails, need to figure out how to fix that")
  void performsKeyValueWithServerCert() throws Exception {
    if (!config().clusterCert().isPresent()) {
      fail("Cluster Certificate must be present for this test!");
    }

    CoreEnvironment env = secureEnvironment(SecurityConfig
      .enableTls(true)
      .trustCertificates(Collections.singletonList(config().clusterCert().get())), null);
    Core core = Core.create(env, authenticator(), secureSeeds());
    core.openBucket(config().bucketname());

    try {
      String id = UUID.randomUUID().toString();
      byte[] content = "hello, world".getBytes(UTF_8);

      InsertRequest insertRequest = new InsertRequest(id, content, 0, 0,
        Duration.ofSeconds(1), core.context(), CollectionIdentifier.fromDefault(config().bucketname()), env.retryStrategy(), Optional.empty());
      core.send(insertRequest);

      InsertResponse insertResponse = insertRequest.response().get();
      assertTrue(insertResponse.status().success());

      GetRequest getRequest = new GetRequest(id, Duration.ofSeconds(1),
        core.context(), CollectionIdentifier.fromDefault(config().bucketname()), env.retryStrategy());
      core.send(getRequest);

      GetResponse getResponse = getRequest.response().get();
      assertTrue(getResponse.status().success());
      assertArrayEquals(content, getResponse.content());
      assertTrue(getResponse.cas() != 0);
    } finally {
      core.shutdown().block();
      env.shutdown();
    }
  }

  @Test
  @IgnoreWhen(clusterTypes = { ClusterType.MOCKED })
  void failsIfNoTrustPresent() {
    assertThrows(IllegalArgumentException.class, () -> secureEnvironment(SecurityConfig.enableTls(true), null));
  }

  @Test
  @IgnoreWhen(clusterTypes = { ClusterType.MOCKED })
  @SuppressWarnings("unchecked")
  void failsIfMoreThanOneTrustPresent() {
    assertThrows(IllegalArgumentException.class, () -> secureEnvironment(SecurityConfig
      .enableTls(true)
      .trustManagerFactory(mock(TrustManagerFactory.class))
      .trustCertificates(mock(List.class)), null)
    );
  }

  @Test
  @IgnoreWhen(clusterTypes = { ClusterType.MOCKED })
  @SuppressWarnings("unchecked")
  void failsIfWrongCertPresent() {
    SimpleEventBus eventBus = new SimpleEventBus(true);
    CoreEnvironment env = secureEnvironment(SecurityConfig
      .enableTls(true)
      .trustCertificates(mock(List.class)), eventBus);
    Core core = Core.create(env, authenticator(), secureSeeds());

    try {
      core.openBucket(config().bucketname());

      waitUntilCondition(() -> {
        boolean hasEndpointConnectFailedEvent = false;
        boolean hasSecureConnectionFailedEvent = false;
        for (Event event : eventBus.publishedEvents()) {
          if (event instanceof EndpointConnectionFailedEvent) {
            hasEndpointConnectFailedEvent = true;
          }
          if (event instanceof SecureConnectionFailedEvent) {
            hasSecureConnectionFailedEvent = true;
          }
        }

        return hasEndpointConnectFailedEvent && hasSecureConnectionFailedEvent;
      });
    } finally {
      core.shutdown().block();
      env.shutdown();
    }


  }

}
