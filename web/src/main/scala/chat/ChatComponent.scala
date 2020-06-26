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

import java.net.URI
import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.UUID

import caliban.client.scalajs.ScalaJSClientAdapter
import chat.ChatClient.{
  Mutations,
  Subscriptions,
  ChatMessage => CalibanChatMessage,
  User => CalibanUser
}
import chuti.{ChannelId, ChatMessage, User}
import io.circe.generic.auto._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{ReactMouseEventFrom, _}
import org.scalajs.dom.raw.HTMLButtonElement
import typings.semanticUiReact.buttonButtonMod.ButtonProps
import typings.semanticUiReact.components._
import typings.semanticUiReact.textAreaTextAreaMod.TextAreaProps

import scala.concurrent.Future
import scala.util.{Failure, Success}

object ChatComponent extends ScalaJSClientAdapter {
  private val connectionId = UUID.randomUUID().toString
  private val df = DateTimeFormatter.ofPattern("MM/dd HH:mm")

  case class State(
    chatMessages: Seq[ChatMessage] = Seq.empty,
    msgInFlux:    String = "",
    ws:           Option[WebSocketHandler] = None
  )

  lazy private val chatId = UUID.randomUUID().toString

  class Backend($ : BackendScope[Props, State]) {
    private def onMessageInFluxChange = { (_: ReactEventFromTextArea, obj: TextAreaProps) =>
      $.modState(_.copy(msgInFlux = obj.value.get.asInstanceOf[String]))
    }

    private def onSend(
      p: Props,
      s: State
    ) = { (_: ReactMouseEventFrom[HTMLButtonElement], _: ButtonProps) =>
      import sttp.client._
      implicit val backend: SttpBackend[Future, Nothing, NothingT] = FetchBackend()
      val mutation = Mutations.say(s.msgInFlux, p.channel.value)
      val serverUri = uri"http://localhost:8079/api/chat"
      val request = mutation.toRequest(serverUri)
      //TODO add headers as necessary
      import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

      Callback.log(s"Sending msg = ${s.msgInFlux}!") >> $.modState(_.copy(msgInFlux = "")) >> AsyncCallback
        .fromFuture(request.send())
        .completeWith {
          case Success(response) if response.code.isSuccess || response.code.isInformational =>
            Callback.log("Message sent")
          case Failure(exception) =>
            Callback.throwException(exception) //TODO handle error responses better
          case Success(response) =>
            Callback.log(s"Error: ${response.statusText}") //TODO handle error responses better
        }
    }

    def render(
      p: Props,
      s: State
    ): VdomNode =
      <.div(
        ^.className := "chat",
        <.h1(^.className := "title", "Chuti Chat"),
        <.div(
          ^.className := "messages",
          s.chatMessages.toVdomArray(msg =>
            <.div(
              ^.className := "receivedMessage",
              <.div(^.className := "sentBy", ^.fontWeight.bold, msg.fromUser.name, " "),
              <.div(^.className := "sentAt", ^.fontWeight.lighter, df.format(msg.date)),
              <.div(^.className := "messageText", msg.msg)
            )
          )
        ),
        <.div(
          ^.className := "sendMessage",
          TextArea(value = s.msgInFlux, onChange = onMessageInFluxChange)(),
          Button(compact = true, basic = true, disabled = s.msgInFlux.trim.isEmpty, onClick = onSend(p, s))("Send")
        )
      )

    def refresh(p: Props): Callback = {
      //TODO call getRecentMessages to prime the list
      $.modState { s =>
        s.copy(ws = Option(
          makeWebSocketClient[ChatMessage](
            uriOrSocket = Left(new URI("ws://localhost:8079/api/chat/ws")),
            query = Subscriptions
              .chatStream(p.channel.value, connectionId)(
                (CalibanChatMessage
                  .fromUser(CalibanUser.name) ~ CalibanChatMessage.date ~ CalibanChatMessage.toUser(
                  CalibanUser.name
                ) ~ CalibanChatMessage.msg)
                  .mapN(
                    (fromUsername: String, date: Long, toUsername: Option[String], msg: String) =>
                      ChatMessage(
                        fromUser = User(None, "", fromUsername),
                        msg = msg,
                        channelId = p.channel,
                        date = LocalDateTime.ofInstant(Instant.ofEpochMilli(date), ZoneOffset.UTC),
                        toUser = toUsername.map(name => User(None, "", name))
                      )
                  )
              ),
            onData = { (_, data) =>
              Callback.log(s"got data! $data")
              data
                .fold(Callback.empty) { msg =>
                  msg.toUser.fold($.modState(s => s.copy(s.chatMessages :+ msg))) { _ =>
                    $.props.flatMap(_.onPrivateMessage.fold(Callback.empty)(_(msg)))
                  }
                }
            },
            operationId = s"chat$chatId"
          )
        )
        )
      }
    }
  }

  case class Props(
    user:             User,
    channel:          ChannelId,
    onPrivateMessage: Option[ChatMessage => Callback] = None
  )

  private val component = ScalaComponent
    .builder[Props]("content")
    .initialState(State())
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.refresh($.props))
    .build

  def apply(
    user:             User,
    channel:          ChannelId,
    onPrivateMessage: Option[ChatMessage => Callback] = None
  ): Unmounted[Props, State, Backend] = component(Props(user, channel, onPrivateMessage))
}
