/*
 * Copyright (c) 2012-2013 SnowPlow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.enrich.hadoop
package inputs

// Java
import java.net.URI
import java.net.URLDecoder

// Scala
import scala.collection.JavaConversions._

// Scalaz
import scalaz._
import Scalaz._

// Apache URLEncodedUtils
import org.apache.http.NameValuePair
import org.apache.http.client.utils.URLEncodedUtils

// Joda-Time
import org.joda.time.DateTime

/**
 * The canonical input format for the ETL
 * process: it should be possible to
 * convert any collector input format to
 * this format, ready for the main,
 * collector-agnostic stage of the ETL.
 */
final case class CanonicalInput(
    timestamp:  DateTime, // Collector timestamp
    payload:    TrackerPayload, // See below for defn.
    source:     InputSource,    // See below for defn.
    encoding:   String, 
    ipAddress:  Option[String],
    userAgent:  Option[String],
    refererUri: Option[String],
    headers:    List[String],   // May be Nil so not a Nel
    userId:     Option[String])

/**
 * Unambiguously identifies the collector
 * source of this input line.
 */
final case class InputSource(
    collector: String, // Collector name/version
    hostname:  Option[String])

/**
 * All payloads sent by trackers must inherit from
 * this class.
 */
trait TrackerPayload

/**
 * All GET payloads sent by trackers inherit from
 * this class.
 */
trait GetPayload extends TrackerPayload

/**
 * A tracker payload for a single event, delivered
 * via a set of name-value pairs on the querystring
 * of a GET.
 */
case class NVGetPayload(payload: NameValueNel) extends GetPayload

/**
 * A tracker payload for a single event, delivered
 * via a data= parameter on the querystring of a GET.
 *
 * TODO: can we define payload with something other
 * than a String?
 */
case class JsonGetPayload(payload: String) extends GetPayload

/**
 * A companion object which holds
 * factories to extract the
 * different possible payloads,
 * and related types.
 */
object TrackerPayload {

  /**
   * Converts a querystring String
   * into the GetPayload for SnowPlow:
   * a non-empty list of NameValuePairs.
   *
   * Returns a non-empty list of 
   * NameValuePairs, or an error.
   *
   * @param qs The querystring
   *        String to extract name-value
   *        pairs from
   * @param encoding The encoding used
   *        by this querystring
   * @return either a NonEmptyList of
   *         NameValuePairs or an error
   *         message, boxed in a Scalaz
   *         Validation
   */
  def extractGetPayload(qs: String, encoding: String): ValidatedNameValueNel =
    try {
      parseQs(qs, encoding) match {
        case x :: xs => NonEmptyList[NameValuePair](x, xs: _*).success
        case Nil => "No name-value pairs extractable from querystring [%s] with encoding [%s]".format(qs, encoding).fail
      }
    } catch {
      case e => "Exception extracting name-value pairs from querystring [%s] with encoding [%s]: [%s]".format(qs, encoding, e.getMessage).fail
    }

  /**
   * Helper to extract NameValuePairs.
   *
   * Health warning: does not handle any
   * exceptions from encoding errors.
   * Only call this from a method that
   * catches exceptions.
   *
   * @param qs The querystring
   *        String to extract name-value
   *        pairs from
   * @param encoding The encoding used
   *        by this querystring
   * @return the List of NameValuePairs
   */
  private def parseQs(qs: String, encoding: String): List[NameValuePair] = {
    val dePcts = doubleEncodePcts(qs)
    URLEncodedUtils.parse(URI.create("http://localhost/?" + dePcts), encoding).toList
  }

  /**
   * On 17th August 2013, Amazon made an
   * unannounced change to their CloudFront
   * log format - they went from always encoding
   * % characters, to only encoding % characters
   * which were not previously encoded. For a
   * full discussion of this see:
   *
   * https://forums.aws.amazon.com/thread.jspa?threadID=134017&tstart=0#
   *
   * Because of this change, and to preserve backwards compatibility,
   * we will "double-encode" any % signs in the querystring which are only
   * singly encoded. In other words, if we find: % NOT followed by 25, we
   * will insert 25.
   *
   * Examples:
   * 1. "page=Celestial%2520Tarot" - no change
   * 2. "page=Dreaming%20Way%20Tarot" -> "page=Dreaming%2520Way%2520Tarot"
   *
   * TODO: at a later stage, we can probably move from double-encoding %s,
   * to single-encoding all %s (i.e. fixing pre-Aug 17th double-encoded %s),
   * and then removing all decodeString calls in the EnrichmentManager.
   *
   * @param qs The querystring String to double-encode %s within
   * @return the querystring with %s double-encoded
   */
  private[inputs] def doubleEncodePcts(qs: String): String =
    qs.replaceAll("%(?!25)", "%25")

}
