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

package com.couchbase.client.scala

import com.couchbase.client.core.env.Authenticator
import com.couchbase.client.scala.env.{ClusterEnvironment, PasswordAuthenticator, SeedNode}

case class ClusterOptions(
    authenticator: Authenticator,
    environment: Option[ClusterEnvironment] = None,
    seedNodes: Option[Set[SeedNode]] = None
) {
  def environment(environment: ClusterEnvironment): ClusterOptions = {
    copy(environment = Some(environment))
  }
  def seedNodes(seedNodes: Set[SeedNode]): ClusterOptions = {
    copy(seedNodes = Some(seedNodes))
  }
}

object ClusterOptions {
  def create(username: String, password: String): ClusterOptions = {
    ClusterOptions(PasswordAuthenticator(username, password))
  }
  def create(authenticator: Authenticator): ClusterOptions = {
    ClusterOptions(authenticator)
  }
}
