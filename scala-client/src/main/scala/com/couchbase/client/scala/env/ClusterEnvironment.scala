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

package com.couchbase.client.scala.env

import java.util.concurrent.{Executors, ThreadFactory}

import com.couchbase.client.core
import com.couchbase.client.core.env.ConnectionStringPropertyLoader
import com.couchbase.client.core.retry.RetryStrategy
import com.couchbase.client.scala.util.DurationConversions._
import com.couchbase.client.scala.util.FutureConversions
import reactor.core.scala.publisher.SMono
import reactor.core.scala.scheduler.ExecutionContextScheduler

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.util.Try

/** Functions to create a ClusterEnvironment, which provides configuration options for connecting to a Couchbase
  * cluster.
  *
  * This environment also contains long-lived resources such as a thread-pool, so the application should take care to
  * only create one of these.  The same environment can be shared by multiple cluster connections.
  */
object ClusterEnvironment {
  def builder: ClusterEnvironment.Builder = {
    // owned is false because the assumption is the application is calling this.  Internal code should explicitly
    // set owned=true
    ClusterEnvironment.Builder(owned = false)
  }

  def create: ClusterEnvironment = {
    // Can't fail so the .get is safe
    builder.build.get
  }

  case class Builder(
      private[scala] val owned: Boolean,
      private[scala] val connectionString: Option[String] = None,
      private[scala] val ioEnvironment: Option[IoEnvironment] = None,
      private[scala] val ioConfig: Option[IoConfig] = None,
      private[scala] val compressionConfig: Option[CompressionConfig] = None,
      private[scala] val securityConfig: Option[SecurityConfig] = None,
      private[scala] val timeoutConfig: Option[TimeoutConfig] = None,
      private[scala] val loggerConfig: Option[LoggerConfig] = None,
      private[scala] val retryStrategy: Option[RetryStrategy] = None
  ) {

    def build: Try[ClusterEnvironment] = Try(new ClusterEnvironment(this))

    /** This can only be used by internal Cluster.connect methods, to avoid the confusion of being able to pass a
      * connection string to both ClusterEnvironment and Cluster.connect
      */
    private[scala] def connectionString(value: String): ClusterEnvironment.Builder = {
      copy(connectionString = Some(value))
    }

    /** Sets the [[IoEnvironment]] config.
      *
      * @return this, for chaining
      */
    def ioEnvironment(config: IoEnvironment): ClusterEnvironment.Builder = {
      copy(ioEnvironment = Some(config))
    }

    /** Sets the [[IoConfig]] config.
      *
      * @return this, for chaining
      */
    def ioConfig(config: IoConfig): ClusterEnvironment.Builder = {
      copy(ioConfig = Some(config))
    }

    /** Sets the [[CompressionConfig]] config.
      *
      * @return this, for chaining
      */
    def compressionConfig(config: CompressionConfig): ClusterEnvironment.Builder = {
      copy(compressionConfig = Some(config))
    }

    /** Sets the [[SecurityConfig]] config.
      *
      * @return this, for chaining
      */
    def securityConfig(config: SecurityConfig): ClusterEnvironment.Builder = {
      copy(securityConfig = Some(config))
    }

    /** Sets the [[TimeoutConfig]] config.
      *
      * @return this, for chaining
      */
    def timeoutConfig(config: TimeoutConfig): ClusterEnvironment.Builder = {
      copy(timeoutConfig = Some(config))
    }

    /** Sets the [[IoConfig]] config.
      *
      * @return this, for chaining
      */
    def loggerConfig(config: LoggerConfig): ClusterEnvironment.Builder = {
      copy(loggerConfig = Some(config))
    }

    def retryStrategy(value: RetryStrategy): ClusterEnvironment.Builder = {
      copy(retryStrategy = Some(value))
    }
  }
}

private[scala] class CoreEnvironmentWrapper()
    extends core.env.CoreEnvironment.Builder[CoreEnvironmentWrapper]() {}

private[scala] class CoreEnvironment(
    builder: core.env.CoreEnvironment.Builder[CoreEnvironmentWrapper]
) extends core.env.CoreEnvironment(builder) {

  override protected def defaultAgentTitle(): String = "scala"

}

/** All configuration options related to a cluster environment, along with some long-lived resources including a
  * thread-pool.
  *
  * @param owned whether the cluster owns the environment, which will decide if it shuts it down automatically when
  *              the cluster is shutdown
  */
class ClusterEnvironment(private[scala] val builder: ClusterEnvironment.Builder) {
  private[scala] val owned: Boolean = builder.owned

  private[scala] def timeoutConfig = coreEnv.timeoutConfig()

  private[scala] def retryStrategy = coreEnv.retryStrategy()

  // Create the thread pool that will be used for all `Future`s throughout the SDK.
  // Note that the app will also need its own ExecutionContext to do anything with the returned `Future`. It could be
  // possible in future to expose this internal thread-pool:
  // 1. Easier for app as it doesn't have to make own.  Would still have to add an import line though.
  // 2. More dangerous for us as app could mis-use thread pool.
  // 3. Can be more efficient as less context switching.

  // Implementation note: there are some potentially long-running operations that will need to go on this pool.  E.g.
  // buffering a query result.  So, make it unlimited.
  private[scala] val threadPool = Executors.newCachedThreadPool(new ThreadFactory {
    override def newThread(runnable: Runnable): Thread = {
      val thread = new Thread(runnable)
      // Make it a daemon thread so it doesn't block app exit
      thread.setDaemon(true)
      thread.setName("cb-comps-" + thread.getId)
      thread
    }
  })
  private[scala] implicit val ec      = ExecutionContext.fromExecutor(threadPool)
  private[scala] val defaultScheduler = ExecutionContextScheduler(ec)

  private val coreBuilder = new CoreEnvironmentWrapper()

  builder.connectionString.foreach(v => coreBuilder.load(new ConnectionStringPropertyLoader(v)))
  builder.ioEnvironment.foreach(v => coreBuilder.ioEnvironment(v.toCore))
  builder.ioConfig.foreach(v => coreBuilder.ioConfig(v.toCore))
  builder.compressionConfig.foreach(v => coreBuilder.compressionConfig(v.toCore))
  builder.securityConfig.foreach(v => coreBuilder.securityConfig(v.toCore))
  builder.timeoutConfig.foreach(v => coreBuilder.timeoutConfig(v.toCore))
  builder.loggerConfig.foreach(v => coreBuilder.loggerConfig(v.toCore))
  builder.retryStrategy.foreach(rs => coreBuilder.retryStrategy(rs))

  private[scala] val coreEnv = new CoreEnvironment(coreBuilder)

  /**
    * Shuts this environment down.  If the application created this (i.e. rather than using one of the convenience
    * `Cluster.create` methods, then it is responsible for calling shutdown on it.
    *
    * This will block until everything is shutdown, or the timeout is exceeded.
    *
    * Note that once shutdown, the environment cannot be restarted so it is advised to perform this operation
    * at the very last operation in the SDK shutdown process.
    *
    * @param timeout the timeout to wait maximum.
    */
  def shutdown(timeout: Duration = coreEnv.timeoutConfig.disconnectTimeout): Unit =
    shutdownReactive(timeout).block()

  def shutdownReactive(timeout: Duration = coreEnv.timeoutConfig.disconnectTimeout): SMono[Unit] = {
    FutureConversions
      .javaMonoToScalaMono(
        coreEnv
          .shutdownReactive(timeout)
      )
      .then(SMono.defer[Unit](() => {
        threadPool.shutdownNow()
        defaultScheduler.dispose()
        SMono.empty
      }))
      .timeout(timeout)
  }

}
