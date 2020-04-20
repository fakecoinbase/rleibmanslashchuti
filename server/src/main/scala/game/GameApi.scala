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

import java.time.{Instant, LocalDateTime, ZoneOffset}

import caliban.CalibanError.ExecutionError
import caliban.GraphQL.graphQL
import caliban.Value.IntValue
import caliban.schema.{ArgBuilder, GenericSchema, Schema}
import caliban.wrappers.ApolloTracing.apolloTracing
import caliban.wrappers.Wrappers.{maxDepth, maxFields, printSlowQueries, timeout}
import caliban.{GraphQL, RootResolver}
import game.GameService.{GameLayer, GameService}
import chuti._
import dao.SessionProvider
import zio.{URIO, ZIO}
import zio.clock.Clock
import zio.console.Console
import zio.stream.ZStream
import zio.duration._
import zioslick.RepositoryException

object GameApi extends GenericSchema[GameService with GameLayer] {

  case class Queries(getGame: GameId => ZIO[GameService with GameLayer, RepositoryException, GameState])
  case class Mutations(
    newGame:        ZIO[GameService with GameLayer, RepositoryException, GameState],
    joinRandomGame: ZIO[GameService with GameLayer, RepositoryException, GameState],
    abandonGame:    GameId => ZIO[GameService with GameLayer, RepositoryException, Boolean],
    play:           GameEvent => ZIO[GameService with GameLayer, RepositoryException, GameState]
  )
  case class Subscriptions(
    gameStream: GameId => ZStream[GameService with GameLayer, RepositoryException, GameEvent],
    userStream: ZStream[GameService with GameLayer, RepositoryException, UserEvent]
  )

  implicit val localDateTimeSchema: Typeclass[LocalDateTime] =
    Schema.longSchema.contramap(_.toInstant(ZoneOffset.UTC).toEpochMilli)
  implicit val localDateTimeArgBuilder: ArgBuilder[LocalDateTime] = {
    case value: IntValue =>
      Right(LocalDateTime.ofInstant(Instant.ofEpochMilli(value.toLong), ZoneOffset.UTC))
    case other => Left(ExecutionError(s"Can't build a LocalDateTime from input $other"))
  }
  implicit private val userSchema = gen[User]
  implicit private val numeroTypeSchema = gen[Numero]
  implicit private val estadoSchema = gen[Estado]
  implicit private val triunfoSchema = gen[Triunfo]
  implicit private val fichaSchema = gen[Ficha]
  implicit private val filaSchema = gen[Fila]
  implicit private val jugadorSchema = gen[Jugador]
  implicit private val gameStateSchema = gen[GameState]
  implicit private val jugadaSchema = gen[GameEvent]

  val api: GraphQL[Console with Clock with GameService with GameLayer] =
    graphQL(
      RootResolver(
        Queries(
          getGame = gameId => GameService.getGame(gameId)
        ),
        Mutations(
          newGame = GameService.newGame(),
          joinRandomGame = GameService.joinRandomGame(),
          abandonGame = gameId => GameService.abandonGame(gameId),
          play = gameEvent => GameService.play(gameEvent)
        ),
        Subscriptions(
          gameStream = gameId => GameService.gameStream(gameId),
          userStream = GameService.userStream
        )
      )
    ) @@
      maxFields(200) @@ // query analyzer that limit query fields
      maxDepth(30) @@ // query analyzer that limit query depth
      timeout(3.seconds) @@ // wrapper that fails slow queries
      printSlowQueries(500.millis) @@ // wrapper that logs slow queries
      apolloTracing // wrapper for https://github.com/apollographql/apollo-tracing
  val schema =
    "schema {\n  query: Queries\n  mutation: Mutations\n  subscription: Subscriptions\n}\n\nscalar Long\n\nunion GameEvent = Canta | Da | DeCaida | EmpiezaJuego | HoyoTecnico | InviteFriend | JoinGame | MeRindo | NoOp | Pide | PideInicial | PoisonPill | TerminaJuego\n\nunion Triunfo = SinTriunfos | TriunfanMulas | TriunfoNumero\n\nenum Estado {\n  cantando\n  comienzo\n  jugando\n  terminado\n}\n\nenum Numero {\n  Numero0\n  Numero1\n  Numero2\n  Numero3\n  Numero4\n  Numero5\n  Numero6\n}\n\nenum UserEventType {\n  Connected\n  Disconnected\n  Modified\n}\n\nenum UserStatus {\n  InLobby\n  Offline\n  Playing\n}\n\ntype Canta {\n  gameId: GameId!\n  index: Int\n  cuantas: Int!\n}\n\ntype ChannelId {\n  value: Int!\n}\n\ntype Da {\n  gameId: GameId!\n  index: Int\n  ficha: Ficha!\n}\n\ntype DeCaida {\n  gameId: GameId!\n  index: Int\n}\n\ntype EmpiezaJuego {\n  gameId: GameId!\n  index: Int\n  turno: User\n}\n\ntype Ficha {\n  arriba: Numero!\n  abajo: Numero!\n}\n\ntype Fila {\n  fichas: [Ficha!]!\n}\n\ntype GameId {\n  value: Int!\n}\n\ntype GameState {\n  id: GameId\n  jugadores: [Jugador!]!\n  enJuego: [Ficha!]!\n  triunfo: Triunfo\n  estado: Estado!\n  currentIndex: Int!\n}\n\ntype HoyoTecnico {\n  gameId: GameId!\n  index: Int\n}\n\ntype InviteFriend {\n  gameId: GameId!\n  index: Int\n  user: User!\n}\n\ntype JoinGame {\n  gameId: GameId!\n  index: Int\n  user: User!\n}\n\ntype Jugador {\n  user: User!\n  fichas: [Ficha!]!\n  casas: [Fila!]!\n  cantador: Boolean!\n  mano: Boolean!\n}\n\ntype MeRindo {\n  gameId: GameId!\n  index: Int\n}\n\ntype Mutations {\n  newGame: GameState\n  joinRandomGame: GameState\n  abandonGame(value: Int!): Boolean\n  play: GameState\n}\n\ntype NoOp {\n  gameId: GameId!\n  index: Int\n}\n\ntype Pide {\n  gameId: GameId!\n  index: Int\n  ficha: Ficha!\n  estrictaDerecha: Boolean!\n}\n\ntype PideInicial {\n  gameId: GameId!\n  index: Int\n  ficha: Ficha!\n  triunfan: Numero!\n  estrictaDerecha: Boolean!\n}\n\ntype PoisonPill {\n  gameId: GameId!\n  index: Int\n}\n\ntype Queries {\n  getGame(value: Int!): GameState\n}\n\ntype SinTriunfos {\n  _: Boolean\n}\n\ntype Subscriptions {\n  gameStream(value: Int!): GameEvent!\n  userStream: UserEvent!\n}\n\ntype TerminaJuego {\n  gameId: GameId!\n  index: Int\n}\n\ntype TriunfanMulas {\n  _: Boolean\n}\n\ntype TriunfoNumero {\n  num: Numero!\n}\n\ntype User {\n  id: UserId\n  email: String!\n  name: String!\n  userStatus: UserStatus!\n  currentChannelId: ChannelId\n  created: Long!\n  lastUpdated: Long!\n  lastLoggedIn: Long\n  wallet: Float!\n  deleted: Boolean!\n}\n\ntype UserEvent {\n  user: User!\n  isFriendOfCurrent: Boolean!\n  userEventType: UserEventType!\n}\n\ntype UserId {\n  value: Int!\n}"

  //Generate client with
  // calibanGenClient /Volumes/Personal/projects/chuti/server/src/main/graphql/game.schema /Volumes/Personal/projects/chuti/web/src/main/scala/game/GameClient.scala
}
