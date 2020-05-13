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

package chuti

import chuti.Triunfo.{SinTriunfos, TriunfoNumero}

import scala.annotation.tailrec
import scala.util.Random

//////////////////////////////////////////////////////////////////////////////////////
// General stuff and parents
sealed trait EventInfo[T <: GameEvent] {
  def canDo(
    jugador: Jugador,
    game:    Game
  ): Boolean
  val values: Seq[EventInfo[_]] = Seq(NoOp)
}

sealed trait GameEvent {
  val gameId: Option[GameId]
  val userId: Option[UserId]
  val index:  Option[Int]
  def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent)
}

sealed trait PreGameEvent extends GameEvent
sealed trait PlayEvent extends GameEvent {
  def doEvent(
    jugador: Jugador,
    game:    Game
  ): (Game, GameEvent)
  final def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    userOpt.fold(throw GameException("Play events require a user")) { user =>
      if (game.gameStatus != GameStatus.jugando)
        throw GameException(s"No es el momento de ${game.gameStatus}, que onda?")

      doEvent(game.jugador(user.id), game)
    }
  }

}

object NoOp extends EventInfo[NoOp] {
  override def canDo(
    jugador: Jugador,
    game:    Game
  ): Boolean = true //always true
}

case class NoOp(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) =
    (
      game,
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
}

case class BorloteEvent(
  borlote: Borlote,
  gameId:  Option[GameId] = None,
  userId:  Option[UserId] = None,
  index:   Option[Int] = None
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) =
    (
      game,
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
}

//This event ends the game and shuts down the server... it can only be called by god
case class PoisonPill(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    (
      game,
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
  }
}

//////////////////////////////////////////////////////////////////////////////////////
// Start game and invitations
case class AbandonGame(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends PreGameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    val user = userOpt.getOrElse(throw GameException("User required for this move"))

    (
      game.copy(
        gameStatus =
          if ((game.gameStatus != GameStatus.esperandoJugadoresInvitados && game.gameStatus != GameStatus.esperandoJugadoresAzar) ||
              game.jugadores.size == 1) //if it's the last user in the game the game is finished
            {
              GameStatus.abandonado
            } else {
            game.gameStatus
          },
        jugadores = game.jugadores.filter(_.id != user.id)
      ),
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
  }
}

case class DeclineInvite(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends PreGameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    val user = userOpt.getOrElse(throw GameException("User required for this move"))
    (
      game.copy(jugadores = game.jugadores.filter(_.id != user.id)),
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
  }
}

case class InviteToGame(
  invited: User,
  gameId:  Option[GameId] = None,
  userId:  Option[UserId] = None,
  index:   Option[Int] = None
) extends PreGameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    if (game.jugadores.exists(j => j.id == invited.id))
      throw GameException("A player can't join a game twice")
    if (game.jugadores.exists(_.id == invited.id))
      throw GameException(s"User ${invited.id} is already in game")
    if (game.jugadores.length == game.numPlayers)
      throw GameException("The game is already full")
    (
      game.copy(jugadores = game.jugadores :+ Jugador(invited, invited = true)),
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
  }
}

case class JoinGame(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends PreGameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    val user = userOpt.getOrElse(throw GameException("User required for this move"))
    if (game.jugadores.exists(j => j.id == user.id && !j.invited)) {
      throw GameException("A player can't join a game twice")
    }

    val newPlayer = game.jugadores
      .find(_.id == user.id).fold(Jugador(user.copy(userStatus = UserStatus.Playing))) { j =>
        j.copy(invited = false, user = j.user.copy(userStatus = UserStatus.Playing))
      }

    (
      game.copy(jugadores = game.jugadores.filter(_.id != user.id) :+ newPlayer),
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
  }
}

case class NuevoPartido(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends PreGameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    (
      game.copy(
        gameStatus = GameStatus.comienzo,
        jugadores = game.jugadores.map(
          _.copy(
            mano = false,
            turno = false,
            cantante = false,
            cuantasCantas = None,
            cuenta = Seq.empty,
            fichas = List.empty,
            filas = List.empty
          )
        )
      ),
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
  }
}

case class Sopa(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None,
  turno:  Option[User] = None
) extends PreGameEvent {
  import Game._
  def sopa: List[Ficha] = Random.shuffle(todaLaFicha)
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    val jugadores = game.jugadores
    val newJugadores = sopa
      .grouped(todaLaFicha.length / jugadores.length)
      .zip(jugadores)
      .map {
        case (fichas, jugador) =>
          jugador.copy(
            user = jugador.user,
            fichas = fichas,
            filas = List.empty,
            cantante = turno.fold(fichas.contains(laMulota))(_ == jugador.user),
            turno = turno.fold(fichas.contains(laMulota))(_ == jugador.user),
            mano = false,
            cuantasCantas = None
          )
      }
      .toList

    (
      game.copy(
        jugadores = newJugadores,
        gameStatus = GameStatus.cantando
      ),
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
  }

}

//////////////////////////////////////////////////////////////////////////////////////
// Cantando

object CuantasCantas {
  sealed abstract class CuantasCantas(
    val numFilas:  Int,
    val prioridad: Int
  ) {
    def <=(cuantasCanto: CuantasCantas): Boolean = prioridad <= cuantasCanto.prioridad
    def >(cuantasCanto:  CuantasCantas): Boolean = prioridad > cuantasCanto.prioridad
  }

  case object Casa extends CuantasCantas(4, 4)

  case object Canto5 extends CuantasCantas(5, 5)

  case object Canto6 extends CuantasCantas(6, 6)

  case object Canto7 extends CuantasCantas(7, 7)

  case object CantoTodas extends CuantasCantas(21, 8)

  case object Buenas extends CuantasCantas(-1, -1)
}
import chuti.CuantasCantas._

case class Canta(
  cuantasCantas: CuantasCantas,
  gameId:        Option[GameId] = None,
  userId:        Option[UserId] = None,
  index:         Option[Int] = None
) extends PlayEvent {
  override def doEvent(
    jugador: Jugador,
    game:    Game
  ): (Game, GameEvent) = {
    if (game.gameStatus != GameStatus.cantando)
      throw GameException("No es el momento de cantar, que onda?")

    val nextPlayer = game.nextPlayer(jugador)
    val cantanteActual = game.jugadores.find(_.cantante).getOrElse(jugador)
    val nuevoCantante = cantanteActual.cuantasCantas.fold {
      //primer jugador cantando
      jugador.copy(cantante = true, mano = true, cuantasCantas = Option(cuantasCantas))
    } { cuantasCanto =>
      if (cuantasCantas <= cuantasCanto || cuantasCanto == CuantasCantas.CantoTodas) {
        //No es suficiente para salvarlo, dejalo como esta
        cantanteActual
      } else {
        //Lo salvaste, te llevas la mano
        jugador.copy(cantante = true, mano = true, cuantasCantas = Option(cuantasCantas))
      }
    }

    //El turno no cambia,
    val jugadoresNuevos =
      game.modifiedJugadores(
        _.id == nuevoCantante.id,
        _ => nuevoCantante,
        _.copy(cantante = false, mano = false, cuantasCantas = None)
      )

    val newGameStatus =
      if (nextPlayer.turno || cuantasCantas == CuantasCantas.CantoTodas) {
        //jugador es el ultimo en cantar, juego listo para empezar
        GameStatus.jugando
      } else {
        GameStatus.cantando
      }

    val modified = game.copy(jugadores = jugadoresNuevos, gameStatus = newGameStatus)
    (
      modified,
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = jugador.id)
    )
  }
}

//////////////////////////////////////////////////////////////////////////////////////
// Jugando

case class PideInicial(
  ficha:           Ficha,
  triunfo:         Triunfo,
  estrictaDerecha: Boolean,
  hoyoTecnico:     Option[String] = None,
  gameId:          Option[GameId] = None,
  userId:          Option[UserId] = None,
  index:           Option[Int] = None
) extends PlayEvent {
  override def doEvent(
    jugador: Jugador,
    game:    Game
  ): (Game, GameEvent) = {
    //Restricciones
    if (!jugador.fichas.contains(ficha)) {
      throw GameException("No puedes jugar una ficha que no tienes!")
    }

    (
      //transfiere la ficha al centro
      game
        .copy(
          triunfo = Option(triunfo),
          enJuego = List((jugador.id.get, ficha)),
          estrictaDerecha = estrictaDerecha,
          jugadores = game.modifiedJugadores(_.id == jugador.id, { j =>
            j.copy(fichas = j.dropFicha(ficha))
          })
        ),
      copy(
        index = Option(game.currentEventIndex),
        gameId = game.id,
        userId = jugador.id,
        hoyoTecnico =
          if (game.puedesCaerte(jugador)) Option("Podias haberte caido, no lo hiciste") else None
      )
    )
  }
}

case class Pide(
  ficha:           Ficha,
  estrictaDerecha: Boolean,
  hoyoTecnico:     Option[String] = None,
  gameId:          Option[GameId] = None,
  userId:          Option[UserId] = None,
  index:           Option[Int] = None
) extends PlayEvent {

  override def doEvent(
    jugador: Jugador,
    game:    Game
  ): (Game, GameEvent) = {
    //Restricciones
    if (!jugador.fichas.contains(ficha)) {
      throw GameException("No puedes jugar una ficha que no tienes!")
    }

    (
      //transfiere la ficha al centro
      game
        .copy(
          enJuego = List((jugador.id.get, ficha)),
          estrictaDerecha = estrictaDerecha,
          jugadores = game.modifiedJugadores(_.id == jugador.id, { j =>
            j.copy(fichas = j.dropFicha(ficha))
          })
        ),
      copy(
        index = Option(game.currentEventIndex),
        gameId = game.id,
        userId = jugador.id,
        hoyoTecnico =
          if (game.puedesCaerte(jugador)) Option("Podias haberte caido, no lo hiciste") else None
      )
    )
  }
}

case class Da(
  ficha:       Ficha,
  hoyoTecnico: Option[String] = None,
  gameId:      Option[GameId] = None,
  userId:      Option[UserId] = None,
  index:       Option[Int] = None
) extends PlayEvent {
  override def doEvent(
    jugador: Jugador,
    game:    Game
  ): (Game, GameEvent) = {
    //Restricciones
    if (game.estrictaDerecha && !jugador.mano) {
      throw GameException("No te adelantes")
    }
    if (!jugador.fichas.contains(ficha)) {
      throw GameException("Este jugador no tiene esta ficha!")
    }
    val enJuego = game.enJuego :+ (jugador.id.get, ficha)
    //Si es el cuarto jugador dando la ficha
    val modifiedGame: Game = if (enJuego.size == 4) {
      //Al ganador le damos las cuatro fichas, le damos también la mano, empezamos mano nueva
      //Nota, la primera fila se queda abierta, las demas se esconden y ya no importan.
      //TODO rewrite this using game.fichaGanadora(
      val ganador = Game.calculaJugadorGanador(enJuego, enJuego.head._2, game.triunfo.get)
      game.copy(jugadores = game.modifiedJugadores(
        _.id == Option(ganador._1), { j =>
          j.copy(
            mano = true,
            fichas = j.dropFicha(ficha),
            filas = j.filas :+ Fila(
              enJuego.map(_._2),
              abierta = game.jugadores.flatMap(_.filas).isEmpty
            )
          )
        }, { j =>
          j.copy(mano = false, fichas = j.dropFicha(ficha))
        }
      )
      )
    } else {
      //transfiere la ficha al centro
      game
        .copy(
          enJuego = enJuego,
          jugadores = game.modifiedJugadores(_.id == jugador.id, { j =>
            j.copy(fichas = j.dropFicha(ficha))
          })
        )
    }

    //Checar hoyo tecnico (el jugador dio una ficha que no fue pedida, o teniendo triunfo no lo solto)
    val pidióFicha = enJuego.head._2
    val pidioNum = game.triunfo match {
      case Some(TriunfoNumero(num)) => if (pidióFicha.es(num)) num else pidióFicha.arriba
      case Some(SinTriunfos)        => pidióFicha.arriba
    }

    val hoyoTecnico =
      if (!ficha.es(pidioNum) &&
          modifiedGame.jugador(jugador.id).fichas.exists(_.es(pidioNum))) {
        Option(s"Pidieron $pidioNum, diste $ficha pero si tienes")
      } else if (!ficha.es(pidioNum) &&
                 (game.triunfo match {
                   case Some(TriunfoNumero(num)) =>
                     modifiedGame.jugador(jugador.id).fichas.exists(_.es(num))
                   case Some(SinTriunfos) => false
                 })) {
        Option(s"Pidieron $pidioNum, diste $ficha pero si tienes triunfos")
      } else {
        None
      }

    (
      modifiedGame,
      copy(
        hoyoTecnico = hoyoTecnico,
        index = Option(game.currentEventIndex),
        gameId = game.id,
        userId = jugador.id
      )
    )
  }

}

//Acuerdate de los regalos
case class Caite(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends PlayEvent {
  override def doEvent(
    jugador: Jugador,
    game:    Game
  ): (Game, GameEvent) = {
    //Restricciones
    if (game.estrictaDerecha && !jugador.mano) {
      throw GameException("No te adelantes")
    }

    @tailrec def loopHastaElFinal(looped: Game): Game = {
      val loopedJugador = looped.jugador(jugador.id)
      if (loopedJugador.fichas.isEmpty) {
        looped
      } else {
        val juegaFicha: Ficha = looped
          .maxByTriunfo(loopedJugador.fichas).getOrElse(
            throw GameException("The player better have something to play")
          )

        val (fichaGanadora, fichaPerdedora) = looped
          .fichaGanadora(
            juegaFicha,
            looped.jugadores
              .filter(_.id != loopedJugador.id)
              .flatMap(_.fichas)
          )

        val ganador = looped.jugadores
          .find(_.fichas.contains(fichaGanadora)).getOrElse(
            throw GameException("Alguien tiene que ganar!!!")
          )

        loopHastaElFinal(
          looped.copy(
            jugadores = looped.modifiedJugadores(
              _ == ganador,
              j =>
                j.copy(
                  filas = j.filas :+ Fila(List(fichaGanadora), abierta = true),
                  fichas = j.fichas.filter(_ != fichaGanadora)
                ),
              j =>
                j.copy(
                  fichas = j.fichas.filter(_ != juegaFicha)
                )
            )
          )
        )
      }
    }

    val endState = loopHastaElFinal(game)
    //Checar si de caida es posible. Se anotan los puntos, regalos? termina juego.
    (endState, copy(index = Option(game.currentEventIndex), gameId = game.id, userId = jugador.id))
  }
}

case class MeRindo(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends PlayEvent {
  override def doEvent(
    jugador: Jugador,
    game:    Game
  ): (Game, GameEvent) = {
    //Nada mas te puedes rendir hasta correr la primera.
    //Se apunta un hoyo, juego nuevo
    if (jugador.filas.size > 1) {
      throw GameException("No te puedes rendir despues de la primera")
    }
    (
      game.copy(
        gameStatus = GameStatus.terminado,
        jugadores = game.modifiedJugadores(
          _.id == jugador.id,
          guey => guey.copy(cuenta = guey.cuenta :+ Cuenta(0, esHoyo = true))
        )
      ),
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = jugador.id)
    )
  }
}

case class HoyoTecnico(
  razon:  String,
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends PlayEvent {
  override def doEvent(
    jugador: Jugador,
    game:    Game
  ): (Game, GameEvent) = {
    (
      //Se apunta un hoyo, juego nuevo.
      game.copy(
        gameStatus = GameStatus.terminado,
        jugadores = game.modifiedJugadores(
          _.id == jugador.id,
          guey => guey.copy(cuenta = guey.cuenta :+ Cuenta(0, esHoyo = true))
        )
      ),
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = jugador.id)
    )
  }
}

//////////////////////////////////////////////////////////////////////////////////////
// Game End
case class TerminaJuego(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends PlayEvent {
  override def doEvent(
    jugador: Jugador,
    game:    Game
  ): (Game, GameEvent) = {
    val conCuentas = game.copy(
      gameStatus = if (game.partidoOver) GameStatus.partidoTerminado else GameStatus.terminado,
      jugadores = game.modifiedJugadores(
        _.cantante, { victima =>
          if (victima.filas.size < victima.cuantasCantas.fold(0)(_.numFilas)) {
            //Ya fue hoyo!
            victima
              .copy(cuenta = victima.cuenta :+ Cuenta(
                victima.cuantasCantas.fold(0)(_.numFilas),
                esHoyo = true
              )
              )
          } else {
            victima.copy(cuenta = victima.cuenta ++ victima.filas.headOption.map(_ =>
              Cuenta(victima.filas.size, esHoyo = false)
            )
            )
          }
        }, { otro =>
          otro.copy(cuenta = otro.cuenta ++ otro.filas.headOption.map(_ => Cuenta(otro.filas.size)))
        }
      )
    )

    if (conCuentas.partidoOver) {
      (
        conCuentas.copy(gameStatus = GameStatus.partidoTerminado),
        copy(index = Option(game.currentEventIndex), gameId = game.id, userId = jugador.id)
      )
    } else {
      (
        conCuentas,
        copy(index = Option(game.currentEventIndex), gameId = game.id, userId = jugador.id)
      )
    }
  }
}
