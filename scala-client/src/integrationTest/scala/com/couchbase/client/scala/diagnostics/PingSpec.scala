/*
 * Copyright (c) 2018 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"));
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

package com.couchbase.client.scala.diagnostics

import com.couchbase.client.core.diag.PingServiceHealth
import com.couchbase.client.core.service.ServiceType
import com.couchbase.client.scala.util.ScalaIntegrationTest
import com.couchbase.client.scala.{Bucket, Cluster}
import com.couchbase.client.test.{ClusterAwareIntegrationTest, ClusterType, IgnoreWhen}
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api._
@TestInstance(Lifecycle.PER_CLASS)
@IgnoreWhen(clusterTypes = Array(ClusterType.MOCKED))
class PingSpec extends ScalaIntegrationTest {
  private var cluster: Cluster   = _
  private var bucket: Bucket     = _
  private var bucketName: String = _
  @BeforeAll
  def setup(): Unit = {
    cluster = connectToCluster()
    bucketName = ClusterAwareIntegrationTest.config().bucketname()
    bucket = cluster.bucket(bucketName)
  }

  @AfterAll
  def tearDown(): Unit = {
    cluster.disconnect()
  }

  @Test
  def ping(): Unit = {
    // TODO: Force a bucket connection. Needs to wait for bucket to be ready.
    bucket.defaultCollection.get("does_not_exist")

    val pr = bucket.ping().get
    assert(!pr.services.isEmpty)
    val psh = pr.services.stream.filter(_.`type` == ServiceType.KV).findFirst.get
    assertTrue(psh.latency != 0)
    assertEquals(PingServiceHealth.PingState.OK, psh.state)
  }
}
