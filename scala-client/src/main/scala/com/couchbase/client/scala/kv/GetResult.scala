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

package com.couchbase.client.scala.kv

import com.couchbase.client.scala.codec._
import com.couchbase.client.scala.json.JsonObject
import scala.reflect.runtime.universe._

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/** The result of a `get` operation, e.g. the contents of a document.
  *
  * @param id         the unique identifier of the document
  * @param cas        the document's CAS value at the time of the lookup
  * @param expiry     if the document was fetched with the `withExpiry` flag set then this will contain the
  *                   document's expiration value.  Otherwise it will be None.
  *
  * @define SupportedTypes this can be of any type for which an implicit
  *                        [[com.couchbase.client.scala.codec.JsonDeserializer]] can be found: a list
  *                        of types that are supported 'out of the box' is available at ***CHANGEME:TYPES***
  * @author Graham Pople
  * @since 1.0.0
  */
case class GetResult(
    id: String,
    // It's Right only in the case where projections were requested
    private val _content: Either[Array[Byte], JsonObject],
    private[scala] val flags: Int,
    cas: Long,
    expiry: Option[Duration],
    transcoder: Transcoder
) {

  /** Return the content, converted into the application's preferred representation.
    *
    * <b>Projections</b>: if the advanced feature projections has been used (e.g. if a `project` array was provided
    * to the `get` call), then the results can only be converted into a
    * [[JsonObject]] - though it would be possible to change this, please let us know if you'd like to see support for
    * other types.
    *
    * @tparam T $SupportedTypes
    */
  def contentAs[T](
      implicit deserializer: JsonDeserializer[T],
      tt: WeakTypeTag[T],
      tag: ClassTag[T]
  ): Try[T] = {
    // Both WeakTypeTag and ClassTag are used, WeakTypeTag can be removed if find a way to do tag.unapply(obj) below with
    // ClassTag.
    // WeakTypeTag is used over TypeTag so app does not have make every case class top-level.

    _content match {
      case Left(bytes) =>
        // Regular case
        transcoder match {
          case t: TranscoderWithSerializer    => t.decode(bytes, flags, deserializer)
          case t: TranscoderWithoutSerializer => t.decode(bytes, flags)
        }

      case Right(obj) =>
        // Projection
        tag.unapply(obj) match {
          case Some(o) => Success(o)
          case _ =>
            Failure(
              new IllegalArgumentException(
                "Projection results can currently only be returned with " +
                  "contentAs[JsonObject]"
              )
            )
        }
    }
  }
}
