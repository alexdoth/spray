/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.spray.can.server

import cc.spray.can.parsing.ParserSettings
import com.typesafe.config.{ConfigFactory, Config}

private[can] class ServerSettings(config: Config = ConfigFactory.load(), val ConfirmedSends: Boolean) {
  private[this] val c: Config = {
    val c = config.withFallback(ConfigFactory.defaultReference())
    c.checkValid(ConfigFactory.defaultReference(), "spray.can.server")
    c.getConfig("spray.can.server")
  }

  val ServerHeader                  = c getString       "server-header"
  val PipeliningLimit               = c getInt          "pipelining-limit"
  val IdleTimeout                   = c getMilliseconds "idle-timeout"
  val RequestTimeout                = c getMilliseconds "request-timeout"
  val TimeoutTimeout                = c getMilliseconds "timeout-timeout"
  val ReapingCycle                  = c getMilliseconds "reaping-cycle"
  val DirectResponding              = c getBoolean      "direct-responding"
  val StatsSupport                  = c getBoolean      "stats-support"
  val TimeoutHandler                = c getString       "timeout-handler"
  val ChunklessStreaming            = c getBoolean      "chunkless-streaming"
  val ConfirmToSender               = c getBoolean      "confirm-to-sender"
  val RequestChunkAggregationLimit  = c getBytes        "request-chunk-aggregation-limit"
  val ResponseSizeHint              = c getBytes        "response-size-hint"

  require(PipeliningLimit >= 0, "pipelining-limit must be >= 0")
  require(IdleTimeout     >= 0, "idle-timeout must be >= 0")
  require(RequestTimeout  >= 0, "request-timeout must be >= 0")
  require(TimeoutTimeout  >= 0, "timeout-timeout must be >= 0")
  require(ReapingCycle    >= 0, "reaping-cycle must be >= 0")
  require(0 <= RequestChunkAggregationLimit && RequestChunkAggregationLimit <= Int.MaxValue,
    "request-chunk-aggregation-limit must be >= 0 and <= Int.MaxValue")
  require(0 <= ResponseSizeHint && ResponseSizeHint <= Int.MaxValue,
    "response-size-hint must be >= 0 and <= Int.MaxValue")

  val ParserSettings = new ParserSettings(config)
}
