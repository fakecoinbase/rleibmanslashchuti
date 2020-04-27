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

package game

import caliban.client.CalibanClientError.DecodingError
import caliban.client.FieldBuilder._
import caliban.client.Operations._
import caliban.client.SelectionBuilder._
import caliban.client.Value.{jsonToValue, _}
import caliban.client._
import io.circe.{Decoder, Json}

object GameClient {

  implicit val jsonDecoder: ScalarDecoder[Json] = (value: Value) => {
    Right(valueEncoder(value))
  }

  implicit val jsonEncoder: ArgEncoder[Json] = new ArgEncoder[Json] {
    override def encode(json: Json): Value = valueDecoder.decodeJson(json).toOption.get
    override def typeName: String = "Json"
  }

  sealed trait UserEventType extends scala.Product with scala.Serializable
  object UserEventType {
    case object AbandonedGame extends UserEventType
    case object Connected extends UserEventType
    case object Disconnected extends UserEventType
    case object JoinedGame extends UserEventType
    case object Modified extends UserEventType

    implicit val decoder: ScalarDecoder[UserEventType] = {
      case StringValue("AbandonedGame") => Right(UserEventType.AbandonedGame)
      case StringValue("Connected")     => Right(UserEventType.Connected)
      case StringValue("Disconnected")  => Right(UserEventType.Disconnected)
      case StringValue("JoinedGame")    => Right(UserEventType.JoinedGame)
      case StringValue("Modified")      => Right(UserEventType.Modified)
      case other                        => Left(DecodingError(s"Can't build UserEventType from input $other"))
    }
    implicit val encoder: ArgEncoder[UserEventType] = new ArgEncoder[UserEventType] {
      override def encode(value: UserEventType): Value = value match {
        case UserEventType.AbandonedGame => EnumValue("AbandonedGame")
        case UserEventType.Connected     => EnumValue("Connected")
        case UserEventType.Disconnected  => EnumValue("Disconnected")
        case UserEventType.JoinedGame    => EnumValue("JoinedGame")
        case UserEventType.Modified      => EnumValue("Modified")
      }
      override def typeName: String = "UserEventType"
    }
  }

  sealed trait UserStatus extends scala.Product with scala.Serializable
  object UserStatus {
    case object Idle extends UserStatus
    case object Offline extends UserStatus
    case object Playing extends UserStatus

    implicit val decoder: ScalarDecoder[UserStatus] = {
      case StringValue("Idle") => Right(UserStatus.Idle)
      case StringValue("Offline") => Right(UserStatus.Offline)
      case StringValue("Playing") => Right(UserStatus.Playing)
      case other                  => Left(DecodingError(s"Can't build UserStatus from input $other"))
    }
    implicit val encoder: ArgEncoder[UserStatus] = new ArgEncoder[UserStatus] {
      override def encode(value: UserStatus): Value = value match {
        case UserStatus.Idle => EnumValue("Idle")
        case UserStatus.Offline => EnumValue("Offline")
        case UserStatus.Playing => EnumValue("Playing")
      }
      override def typeName: String = "UserStatus"
    }
  }

  type ChannelId
  object ChannelId {
    def value: SelectionBuilder[ChannelId, Int] = Field("value", Scalar())
  }

  type User
  object User {
    def id[A](innerSelection: SelectionBuilder[UserId, A]): SelectionBuilder[User, Option[A]] =
      Field("id", OptionOf(Obj(innerSelection)))
    def email:      SelectionBuilder[User, String] = Field("email", Scalar())
    def name:       SelectionBuilder[User, String] = Field("name", Scalar())
    def userStatus: SelectionBuilder[User, UserStatus] = Field("userStatus", Scalar())
    def currentChannelId[A](
      innerSelection: SelectionBuilder[ChannelId, A]
    ):               SelectionBuilder[User, Option[A]] = Field("currentChannelId", OptionOf(Obj(innerSelection)))
    def created:     SelectionBuilder[User, Long] = Field("created", Scalar())
    def lastUpdated: SelectionBuilder[User, Long] = Field("lastUpdated", Scalar())
    def lastLoggedIn: SelectionBuilder[User, Option[Long]] =
      Field("lastLoggedIn", OptionOf(Scalar()))
    def wallet:  SelectionBuilder[User, Double] = Field("wallet", Scalar())
    def deleted: SelectionBuilder[User, Boolean] = Field("deleted", Scalar())
  }

  type UserEvent
  object UserEvent {
    def user[A](innerSelection: SelectionBuilder[User, A]): SelectionBuilder[UserEvent, A] =
      Field("user", Obj(innerSelection))
    def userEventType: SelectionBuilder[UserEvent, UserEventType] = Field("userEventType", Scalar())
  }

  type UserId
  object UserId {
    def value: SelectionBuilder[UserId, Int] = Field("value", Scalar())
  }

  type Queries = RootQuery
  object Queries {
    def getGame(value: Int): SelectionBuilder[RootQuery, Option[Json]] =
      Field("getGame", OptionOf(Scalar()), arguments = List(Argument("value", value)))
    def getGameForUser: SelectionBuilder[RootQuery, Option[Json]] =
      Field("getGameForUser", OptionOf(Scalar()))
    def getFriends[A](
      innerSelection: SelectionBuilder[UserId, A]
    ): SelectionBuilder[RootQuery, Option[List[A]]] =
      Field("getFriends", OptionOf(ListOf(Obj(innerSelection))))
    def getInvites: SelectionBuilder[RootQuery, Option[List[Json]]] =
      Field("getInvites", OptionOf(ListOf(Scalar())))
    def getLoggedInUsers[A](
      innerSelection: SelectionBuilder[User, A]
    ): SelectionBuilder[RootQuery, Option[List[A]]] =
      Field("getLoggedInUsers", OptionOf(ListOf(Obj(innerSelection))))
  }

  type Mutations = RootMutation
  object Mutations {
    def newGame: SelectionBuilder[RootMutation, Option[Json]] = Field("newGame", OptionOf(Scalar()))
    def joinRandomGame: SelectionBuilder[RootMutation, Option[Json]] =
      Field("joinRandomGame", OptionOf(Scalar()))
    def abandonGame(value: Int): SelectionBuilder[RootMutation, Option[Boolean]] =
      Field("abandonGame", OptionOf(Scalar()), arguments = List(Argument("value", value)))
    def acceptInvitation(value: Int): SelectionBuilder[RootMutation, Option[Json]] =
      Field("acceptInvitation", OptionOf(Scalar()))
    def declineInvitation(value: Int): SelectionBuilder[RootMutation, Option[Boolean]] =
      Field("declineInvitation", OptionOf(Scalar()), arguments = List(Argument("value", value)))
    def play(gameEvent: Json): SelectionBuilder[RootMutation, Option[Boolean]] =
      Field("play", OptionOf(Scalar()), arguments = List(Argument("gameEvent", gameEvent)))
  }

  type Subscriptions = RootSubscription
  object Subscriptions {
    def userStream[A](
      innerSelection: SelectionBuilder[UserEvent, A]
    ): SelectionBuilder[RootSubscription, A] =
      Field(
        name = "userStream",
        builder = Obj(innerSelection)
      )

    def gameStream[A](
      gameId: chuti.GameId
    )(
      innerSelection: SelectionBuilder[Json, A]
    ): SelectionBuilder[RootSubscription, A] =
      Field(
        name = "gameStream",
        builder = Obj(innerSelection),
        arguments = List(Argument("value", gameId.value))
      )
  }

}
