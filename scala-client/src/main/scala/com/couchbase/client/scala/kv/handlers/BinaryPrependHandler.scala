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

package com.couchbase.client.scala.kv.handlers

import com.couchbase.client.core.msg.ResponseStatus
import com.couchbase.client.core.msg.kv.{
  InsertResponse,
  KeyValueRequest,
  PrependRequest,
  PrependResponse
}
import com.couchbase.client.core.retry.RetryStrategy
import com.couchbase.client.scala.HandlerParams
import com.couchbase.client.scala.api.MutationResult
import com.couchbase.client.scala.durability.Durability
import com.couchbase.client.scala.kv.DefaultErrors
import com.couchbase.client.scala.util.Validate

import scala.compat.java8.OptionConverters._
import scala.util.{Success, Try}

/**
  * Handles requests and responses for KV prepend operations.
  *
  * @author Graham Pople
  * @since 1.0.0
  */
private[scala] class BinaryPrependHandler(hp: HandlerParams)
    extends KeyValueRequestHandler[PrependResponse, MutationResult] {

  def request(
      id: String,
      content: Array[Byte],
      cas: Long = 0,
      durability: Durability,
      timeout: java.time.Duration,
      retryStrategy: RetryStrategy
  ): Try[PrependRequest] = {

    val validations: Try[PrependRequest] = for {
      _ <- Validate.notNullOrEmpty(id, "id")
      _ <- Validate.notNull(content, "content")
      _ <- Validate.notNull(cas, "cas")
      _ <- Validate.notNull(durability, "durability")
      _ <- Validate.notNull(timeout, "timeout")
      _ <- Validate.notNull(retryStrategy, "retryStrategy")
    } yield null

    if (validations.isFailure) {
      validations
    } else {
      Success(
        new PrependRequest(
          timeout,
          hp.core.context(),
          hp.collectionIdentifier,
          retryStrategy,
          id,
          content,
          cas,
          durability.toDurabilityLevel
        )
      )
    }
  }

  def response(
      request: KeyValueRequest[PrependResponse],
      id: String,
      response: PrependResponse
  ): MutationResult = {
    response.status() match {
      case ResponseStatus.SUCCESS =>
        MutationResult(response.cas(), response.mutationToken().asScala)

      case _ => throw DefaultErrors.throwOnBadResult(id, response.status())
    }
  }
}
