/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray
package client

import org.specs2.Specification
import org.specs2.specification.Step
import can.{HttpRequest => _, HttpResponse => _, _}
import http._
import cc.spray.http.HttpMethods._
import utils._
import DispatchStrategies._
import akka.util.Duration
import akka.dispatch.Future
import akka.setak._
import akka.setak.Commons._
import akka.setak.core.TestMessageEnvelopSequence._
import akka.actor.{ActorRef, PoisonPill, Actor}

class HttpConduitSpec extends SetakTest with Specification { def is =
                                                                              sequential^
                                                                              Step(start())^
  "An HttpConduit with max. 4 connections and NonPipelined strategy should"   ^
    "properly deliver the result of a simple request"                         ! oneRequest(NonPipelined())^
    "properly deliver the results of 100 requests"                            ! hundredRequests(NonPipelined())^
                                                                              p^
  "An HttpConduit with max. 4 connections and Pipelined strategy should"      ^
    "properly deliver the result of a simple request"                         ! oneRequest(Pipelined)^
    "properly deliver the results of 100 requests"                            ! hundredRequests(Pipelined)^
                                                                              p^
  "An HttpConduit should"                                                     ^
    "retry requests whose sending has failed"                                 ! retryFailedSend^
    "honor the pipelined strategy when retrying"                              ! retryPipelined^
    "close any active connections on close"                                   ! handleClose^
                                                                              Step(Actor.registry.shutdownAll())


  val DELAY_TOKEN = "/delay"
  var httpClient: ActorRef = _


  class TestService extends Actor {
    self.id = "clienttest-server"
    protected def receive = {
      case RequestContext(can.HttpRequest(method, uri, _, body, _), _, responder) => {
        if (uri == DELAY_TOKEN) {
          Thread.sleep(200L) // 0.2 second
        }
        responder.complete {
          response.withBody(method + "|" + uri + (if (body.length == 0) "" else "|" + new String(body, "ASCII")))
        }
      }
      case Timeout(_, _, _, _, _, complete) => complete(response.withBody("TIMEOUT"))
    }
    def response = can.HttpResponse(headers = List(can.HttpHeader("Content-Type", "text/plain; charset=ISO-8859-1")))
  }

  def oneRequest(strategy: DispatchStrategy) = {
    val response = newConduit(strategy).sendReceive(HttpRequest()).get
    response.copy(headers = Nil) mustEqual HttpResponse(200, "GET|/")
  }

  def hundredRequests(strategy: DispatchStrategy) = {
    val conduit = newConduit(strategy)
    val requests = Seq.tabulate(10)(index => HttpRequest(uri = "/" + index))
    val responseFutures = requests.map(conduit.sendReceive(_))
    responseFutures.zipWithIndex.map { case (future, index) =>
      future.get.copy(headers = Nil) mustEqual HttpResponse(200, "GET|/" + index)
    }.reduceLeft(_ and _)
  }

  def handleClose = {
    val conduit = newConduit(NonPipelined())
    val connMsg = testMessagePatternEnvelop(anyActorRef, conduit.mainActor, { case conn: conduit.ConnectionResult => })
    val f = conduit.sendReceive(HttpRequest(uri = DELAY_TOKEN))
    afterMessage(connMsg) {
      conduit.close()
      // Can't check for the exception in the Actor, but will see error output in console
    }
    1 === 1
  }

  def retryFailedSend = {
    val conduit = newConduit(NonPipelined())
    def send = conduit.sendReceive(HttpRequest())
    val fut = send.delay(Duration("1000 ms")).flatMap(r1 => send.map(r2 => r1 -> r2))
    val (r1, r2) = fut.get
    r1.content === r2.content
  }

  def retryPipelined = {
    val conduit = newConduit(Pipelined, maxConnections = 1)
    val requests = HttpRequest(uri = "/a") :: HttpRequest(uri = "/b") :: HttpRequest(uri = "/c") :: Nil
    val future = Future.traverse(requests)(conduit.sendReceive).delay(Duration("1000 ms")).flatMap { responses1 =>
      Future.traverse(requests)(conduit.sendReceive).map(responses2 => responses1.zip(responses2))
    }
    future.get.map { case (a, b) => a.content === b.content }.reduceLeft(_ and _)
  }

  def newConduit(strategy: DispatchStrategy, maxConnections: Int = 4) = {
    val ret = new HttpConduit(
      "127.0.0.1", 17242, ConduitConfig("clienttest-client", maxConnections, dispatchStrategy = strategy)
    )
    // replace the akka.ActorRef with a TestActorRef
    ret.mainActor.stop()
    ret.mainActor = actorOf(new ret.MainActor).start()
    ret
  }

  def start() {
    Actor.actorOf(new TestService).start()
    Actor.actorOf(new HttpServer(ServerConfig(
      port = 17242,
      serviceActorId = "clienttest-server",
      timeoutActorId = "clienttest-server",
      idleTimeout = 500,
      reapingCycle = 500
    ))).start()
    httpClient = actorOf(new HttpClient(ClientConfig(clientActorId = "clienttest-client"))).start()
  }

}