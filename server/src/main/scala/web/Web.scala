/*
 * Copyright 2020 Roberto Leibman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package web

import akka.event.{Logging, LoggingAdapter}
import akka.http.javadsl.server.Route
import akka.http.scaladsl.Http
import akka.stream.scaladsl._
import api.Api
import chat.ChatService
import chat.ChatService.ChatService
import core.{Core, CoreActors}
import zio.{CancelableFuture, Task, UIO, ZIO}

import scala.concurrent.Future
import scala.util.control.NonFatal

// $COVERAGE-OFF$ This is actual code that we can't test, so we shouldn't report on it
/**
  * Provides the web server (spray-can) for the REST api in ``Api``, using the actor system
  * defined in ``Core``.
  *
  * You may sometimes wish to construct separate ``ActorSystem`` for the web server machinery.
  * However, for this simple application, we shall use the same ``ActorSystem`` for the
  * entire application.
  *
  * Benefits of separate ``ActorSystem`` include the ability to use completely different
  * configuration, especially when it comes to the threading model.
  */
trait Web {
  this: Api with CoreActors with Core =>

  private val log: LoggingAdapter = Logging.getLogger(actorSystem, this)

  val config = api.config.live

  val serverSource: Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
    Http()
      .bind(
        interface = config.config.getString(s"${config.configKey}.host"),
        port = config.config.getInt(s"${config.configKey}.port")
      )

  val bindingFuture: Future[Http.ServerBinding] =
    serverSource
      .to(Sink.foreach { connection => // foreach materializes the source
        log.debug("Accepted new connection from " + connection.remoteAddress)
        // ... and then actually handle the connection
        try {
          connection.flow.joinMat(routes)(Keep.both).run()
          ()
        } catch {
          case NonFatal(e) =>
            log.error(e, "Could not materialize handling flow for {}", connection)
            throw e
        }
      })
      .run()


  val zRoutes: ZIO[ChatService, Throwable, Route]

  val zBindingFuture =
    ChatService.TheChatService.use { chatLayer =>
      for {
        routes <- zRoutes.provideCustomLayer(chatLayer)
      } yield routes

      val z: Task[Http.ServerBinding] = ZIO.fromFuture { ec =>
        serverSource
          .to(Sink.foreach { connection => // foreach materializes the source
            log.debug("Accepted new connection from " + connection.remoteAddress)
            // ... and then actually handle the connection
            try {
              connection.flow.joinMat(routes)(Keep.both).run()
              ()
            } catch {
              case NonFatal(e) =>
                log.error(e, "Could not materialize handling flow for {}", connection)
                throw e
            }
          })
          .run()
      }

        val fut: Future = zio.Runtime.default.unsafeRunToFuture(z)
        fut
    }
}
// $COVERAGE-ON$
