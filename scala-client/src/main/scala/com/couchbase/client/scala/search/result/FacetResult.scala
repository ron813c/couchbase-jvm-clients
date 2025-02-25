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

package com.couchbase.client.scala.search.result

/**
  * Base interface for all facet results.
  *
  * @define name    the name of the { @link SearchFacet} this result corresponds to.
  * @define field   the field the { @link SearchFacet} was targeting.
  * @define total   the total number of valued facet results (it doesn't include missing).
  * @define missing the number of results that couldn't be faceted, missing the
  *                 adequate value. No matter how many more
  *                 buckets are added to the original facet, these
  *                 result won't ever be included in one.
  * @define other   the number of results that could have been faceted (because
  *                 they have a value for the facet's field) but
  *                 weren't, due to not having a bucket in which they belong.
  *                 Adding a bucket can result in these results being faceted.
  * @since 1.0.0
  */
sealed trait FacetResult {

  /** $name */
  def name: String

  /** $field */
  def field: String

  /** $total */
  def total: Long

  /** $missing */
  def missing: Long

  /** $other */
  def other: Long
}

object FacetResult {

  /**
    * A range (or bucket) for a [[DateRangeFacetResult]]. Counts the number of matches
    * that fall into the named range (which can overlap with other user-defined ranges).
    *
    * @since 1.0.0
    */
  case class DateRange(name: String, start: String, end: String, count: Long)

  /**
    * Represents the result for a [[com.couchbase.client.scala.search.facet.DateRangeFacet]].
    *
    * @param name    $name
    * @param field   $field
    * @param total   $total
    * @param missing $missing
    * @param other   $other
    *
    * @since 1.0.0
    */
  case class DateRangeFacetResult(
      name: String,
      field: String,
      total: Long,
      missing: Long,
      other: Long,
      dateRanges: Seq[DateRange]
  ) extends FacetResult

  /**
    * Represents the result for a [[com.couchbase.client.scala.search.facet.NumericRangeFacet]].
    *
    * @param name    $name
    * @param field   $field
    * @param total   $total
    * @param missing $missing
    * @param other   $other
    *
    * @since 1.0.0
    */
  case class NumericRangeFacetResult(
      name: String,
      field: String,
      total: Long,
      missing: Long,
      other: Long,
      numericRanges: Seq[NumericRange]
  ) extends FacetResult

  case class NumericRange(name: String, min: Double, max: Double, count: Long)

  /**
    * Represents the result for a [[com.couchbase.client.scala.search.facet.TermFacet]].
    *
    * @param name    $name
    * @param field   $field
    * @param total   $total
    * @param missing $missing
    * @param other   $other
    *
    * @since 1.0.0
    */
  case class TermFacetResult(
      name: String,
      field: String,
      total: Long,
      missing: Long,
      other: Long,
      terms: Seq[TermRange]
  ) extends FacetResult

  /**
    * A range (or bucket) for a [[TermFacetResult]].
    * Counts the number of occurrences of a given term.
    *
    * @since 1.0.0
    */
  case class TermRange(name: String, count: Long)

}
