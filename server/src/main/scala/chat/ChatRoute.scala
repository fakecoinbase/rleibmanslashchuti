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

package chat

import akka.http.scaladsl.server.{Directives, Route}
import api.{ChutiSession, HasActorSystem, config}
import caliban.interop.circe.AkkaHttpCirceAdapter
import dao.{Repository, SessionProvider}
import zio.ZLayer
import zio.duration._
import zio.logging.slf4j.Slf4jLogger
import zio.logging.{LogAnnotation, Logging}

import scala.concurrent.ExecutionContextExecutor

trait ChatRoute
    extends Directives with AkkaHttpCirceAdapter with Repository.Service with HasActorSystem {
  implicit lazy val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
  import ChatService._

  val staticContentDir: String =
    config.live.config.getString(s"${config.live.configKey}.staticContentDir")

  private val logLayer: ZLayer[Any, Nothing, Logging] = Slf4jLogger.make((_, str) => str)

  def route(session: ChutiSession): Route = pathPrefix("chat") {
    pathEndOrSingleSlash {
      implicit val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default
      adapter.makeHttpService(
        interpreter.provideCustomLayer(SessionProvider.layer(session) ++ logLayer)
      )
    } ~
      path("schema") {
        get(complete(ChatApi.api.render))
      } ~
      path("ws") {
        adapter.makeWebSocketService(
          interpreter.provideCustomLayer(SessionProvider.layer(session) ++ logLayer),
          skipValidation = false,
          keepAliveTime = Option(58.seconds)
        )
      } ~ path("graphiql") {
      getFromFile(s"$staticContentDir/graphiql.html")
    }
  }
}
