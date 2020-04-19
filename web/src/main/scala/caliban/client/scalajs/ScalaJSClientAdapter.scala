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

package caliban.client.scalajs

import java.net.URI
import java.time.LocalDateTime

import caliban.client.Operations.RootSubscription
import caliban.client.{GraphQLRequest, SelectionBuilder}
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Decoder, Error, Json}
import org.scalajs.dom
import org.scalajs.dom.WebSocket
import zio.duration._

trait ScalaJSClientAdapter {

  trait WebSocketHandler {
    def close(): Unit
  }

  //TODO we will replace this with some zio thing as soon as I figure out how
  def makeWebSocketClient[A: Decoder](
    uriOrSocket:          Either[URI, WebSocket],
    query:                SelectionBuilder[RootSubscription, A],
    operationId:          String,
    onData:               (String, Option[A]) => Unit,
    connectionParams:     Option[Json] = None,
    timeout:              Duration = 30.seconds, //how long the client should wait in ms for a keep-alive message from the server (default 30000 ms), this parameter is ignored if the server does not send keep-alive messages. This will also be used to calculate the max connection time per connect/reconnect
    reconnect:            Boolean = true,
    reconnectionAttempts: Int = 3,
    onConnected: (String, Option[Json]) => Unit = { (_, _) =>
      ()
    },
    onReconnected: (String, Option[Json]) => Unit = { (_, _) =>
      ()
    },
    onReconnecting: (String) => Unit = { _ =>
      ()
    },
    onConnecting: () => Unit = { () =>
      ()
    },
    onDisconnected: (String, Option[Json]) => Unit = { (_, _) =>
      ()
    },
    onKeepAlive: Option[Json] => Unit = { _ =>
      ()
    },
    onServerError: (String, Option[Json]) => Unit = { (_, _) =>
      ()
    },
    onClientError: Throwable => Unit = { _ =>
      ()
    }
  )(
    implicit decoder: Decoder[A]
  ): WebSocketHandler = {

    val graphql = query.toGraphQL()

    object GQLOperationMessage {
      //Client messages
      val GQL_CONNECTION_INIT = "connection_init"
      val GQL_START = "start"
      val GQL_STOP = "stop"
      val GQL_CONNECTION_TERMINATE = "connection_terminate"
      //Server messages
      val GQL_COMPLETE = "complete"
      val GQL_CONNECTION_ACK = "connection_ack"
      val GQL_CONNECTION_ERROR = "connection_error"
      val GQL_CONNECTION_KEEP_ALIVE = "ka"
      val GQL_DATA = "data"
      val GQL_ERROR = "error"
      val GQL_UNKNOWN = "unknown"

      def GQLConnectionInit(): GQLOperationMessage =
        GQLOperationMessage(GQL_CONNECTION_INIT, Option(operationId), connectionParams)

      def GQLStart(query: GraphQLRequest): GQLOperationMessage =
        GQLOperationMessage(GQL_START, Option(operationId), payload = Option(query.asJson))

      def GQLStop(): GQLOperationMessage =
        GQLOperationMessage(GQL_STOP, Option(operationId))

      def GQLConnectionTerminate(): GQLOperationMessage =
        GQLOperationMessage(GQL_CONNECTION_TERMINATE, Option(operationId))
    }

    import GQLOperationMessage._

    case class GQLOperationMessage(
      `type`:  String,
      id:      Option[String] = None,
      payload: Option[Json] = None
    )

    val socket: WebSocket = uriOrSocket match {
      case Left(uri)        => new dom.WebSocket(uri.toString, "graphql-ws")
      case Right(webSocket) => webSocket
    }

    //TODO, move this into some sort of Ref/state class
    case class ConnectionState(
      lastKAOpt:       Option[LocalDateTime] = None,
      kaIntervalOpt:   Option[Int] = None,
      firstConnection: Boolean = true,
      reconnectCount:  Int = 0
    )

    var connectionState = ConnectionState()

    def doConnect(): Unit = {
      val sendMe = GQLConnectionInit()
      println(s"Sending: $sendMe")
      socket.send(sendMe.asJson.noSpaces)
    }

    socket.onmessage = { (e: dom.MessageEvent) =>
      val strMsg = e.data.toString
      val msg: Either[Error, GQLOperationMessage] =
        decode[GQLOperationMessage](strMsg)
      println(s"Received: $strMsg")
      msg match {
        case Right(GQLOperationMessage(GQL_COMPLETE, id, payload)) =>
          connectionState.kaIntervalOpt.foreach(id => dom.window.clearInterval(id))
          onDisconnected(id.getOrElse(""), payload)
        //Nothing else to do, really
        case Right(GQLOperationMessage(GQL_CONNECTION_ACK, id, payload)) =>
          //We should only do this the first time
          if (connectionState.firstConnection) {
            onConnected(id.getOrElse(""), payload)
            connectionState = connectionState.copy(firstConnection = false, reconnectCount = 0)
          } else {
            onReconnected(id.getOrElse(""), payload)
          }
          val sendMe = GQLStart(graphql)
          println(s"Sending: $sendMe")
          socket.send(sendMe.asJson.noSpaces)
        case Right(GQLOperationMessage(GQL_CONNECTION_ERROR, id, payload)) =>
          //if this is part of the initial connection, there's nothing to do, we could't connect and that's that.
          onServerError(id.getOrElse(""), payload)
          println(s"Connection Error from server $payload")
        case Right(GQLOperationMessage(GQL_CONNECTION_KEEP_ALIVE, id, payload)) =>
          connectionState = connectionState.copy(reconnectCount = 0)

          if (connectionState.lastKAOpt.isEmpty) {
            //This is the first time we get a keep alive, which means the server is configured for keep-alive,
            //If we never get this, then the server does not support it

            connectionState = connectionState.copy(kaIntervalOpt = Option(
              dom.window.setInterval(
                () => {
                  connectionState.lastKAOpt.map { lastKA =>
                    val timeFromLastKA =
                      java.time.Duration.between(lastKA, LocalDateTime.now).toMillis.milliseconds
                    if (timeFromLastKA > timeout) {
                      //Assume we've gotten disconnected, we haven't received a KA in a while
                      if (reconnect && connectionState.reconnectCount <= reconnectionAttempts) {
                        connectionState =
                          connectionState.copy(reconnectCount = connectionState.reconnectCount + 1)
                        onReconnecting(id.getOrElse(""))
                        doConnect()
                      } else if (connectionState.reconnectCount > reconnectionAttempts) {
                        println("Maximum number of connection retries exceeded")
                      }
                    }
                  }
                },
                timeout.toMillis.toDouble
              )
            )
            )
          }
          connectionState = connectionState.copy(lastKAOpt = Option(LocalDateTime.now()))
          onKeepAlive(payload)
        case Right(GQLOperationMessage(GQL_DATA, id, payloadOpt)) =>
          connectionState = connectionState.copy(reconnectCount = 0)
          for {
            payload     <- payloadOpt
            data        <- payload.asObject.flatMap(_("data"))
            inner       <- data.asObject.flatMap(_.values.headOption)
            dataOrError <- Option(decoder.decodeJson(inner))
          } yield {
            dataOrError match {
              case Right(data) =>
                onData(id.getOrElse(""), Option(data))
              case Left(error) =>
                error.printStackTrace()
                onClientError(error)
            }
          }
        case Right(GQLOperationMessage(GQL_ERROR, id, payload)) =>
          println(s"Error from server $payload")
          onServerError(id.getOrElse(""), payload)
        case Right(GQLOperationMessage(GQL_UNKNOWN, id, payload)) =>
          println(s"Unknown server operation! GQL_UNKNOWN $payload")
          onServerError(id.getOrElse(""), payload)
        case Right(GQLOperationMessage(typ, id, payload)) =>
          println(s"Unknown server operation! $typ $payload $id")
        case Left(error) =>
          onClientError(error)
          error.printStackTrace()
      }
    }
    socket.onerror = { (_: dom.Event) =>
      onClientError(new Exception(s"We've got a socket error, no further info"))
    }
    socket.onopen = { (e: dom.Event) =>
      println(socket.protocol)
      println(e.`type`)
      onConnecting()
      doConnect()
    }

    () => {
      connectionState.kaIntervalOpt.foreach(id => dom.window.clearInterval(id))
      socket.send(GQLStop().asJson.noSpaces)
      socket.send(GQLConnectionTerminate().asJson.noSpaces)
    }
  }

}
