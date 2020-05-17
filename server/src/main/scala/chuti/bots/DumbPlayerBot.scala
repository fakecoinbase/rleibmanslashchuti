package chuti.bots

import chuti.Triunfo._
import chuti._

case object DumbPlayerBot extends PlayerBot {
  private def calculaCasa(
    jugador: Jugador,
    game:    Game
  ): (Int, Triunfo) = {
    //Si el jugador le toca cantar, no le queda de otra, tiene que arriesgarse, asi es que usa otra heuristica... el numero de fichas
    val (deCaidaCount, deCaidaTriunfo) = calculaDeCaida(jugador, game)

    val numTriunfos =
      jugador.fichas
        .flatMap(f => if(f.esMula) Seq(f.arriba) else Seq(f.arriba, f.abajo))
        .groupBy(identity)
        .map {
          case (n, l) =>
            (n, l.size)
        }
        .maxBy(_._2)

    if (numTriunfos._2 > deCaidaCount) {
      //Tenemos algunos triunfos, pero no suficientes para ser de caida, asi es que cantamos uno menos de lo que tenemos
      (numTriunfos._2 - 1, TriunfoNumero(numTriunfos._1))}
    else {
      (deCaidaCount, deCaidaTriunfo)}
  }

  private def calculaDeCaida(
    jugador: Jugador,
    game:    Game
  ): (Int, Triunfo) = {
    //Este jugador es muy conservador, no se fija en el numero de fichas de un numero que tiene, solo en cuantas son de caida
    //En el futuro podemos inventar jugadores que se fijen en ambas partes y que sean mas o menos conservadores
    //Esta bien que las mulas cuenten por dos.
    val numerosQueTengo = jugador.fichas
      .flatMap(f => Seq(f.arriba, f.abajo))
      .distinct
    val fichasDeOtros = Game.todaLaFicha.diff(jugador.fichas)
    //Calcula todas las posibilidades de caida
    val posiblesGanancias = numerosQueTengo
      .map(num =>
        (
          num,
          game
            .copy(triunfo = Option(TriunfoNumero(num))).cuantasDeCaida(
              jugador.fichas,
              fichasDeOtros
            ).size
        )
      )

    val conTriunfosCount =
      posiblesGanancias.filter(_._2 == posiblesGanancias.maxBy(_._2)._2).maxBy(_._1.value)
    val sinTriunfosCount =
      game.copy(triunfo = Option(SinTriunfos)).cuantasDeCaida(jugador.fichas, fichasDeOtros).size

    if (conTriunfosCount._2 > sinTriunfosCount) {
      (conTriunfosCount._2, TriunfoNumero(conTriunfosCount._1))
    } else {
      (sinTriunfosCount, SinTriunfos)
    }
  }

  private def calculaCanto(
    jugador: Jugador,
    game:    Game
  ): (Int, Triunfo) = {
    if (jugador.turno) calculaCasa(jugador, game)
    else calculaDeCaida(jugador, game)
  }

  private def pideInicial(
    jugador: Jugador,
    game:    Game
  ): PideInicial = {
    val (cuantasCantas, triunfo) = calculaCanto(jugador, game)
    triunfo match {
      case SinTriunfos =>
        PideInicial(
          jugador.fichas.filter(_.esMula).maxBy(_.arriba.value),
          SinTriunfos,
          estrictaDerecha = false
        )
      case TriunfoNumero(num) =>
        val triunfos = jugador.fichas.filter(_.es(num))
        PideInicial(
          triunfos.find(_.esMula).getOrElse(triunfos.maxBy(_.other(num).value)),
          Triunfo.TriunfoNumero(num),
          estrictaDerecha = false
        )
    }
  }

  private def pide(
    jugador: Jugador,
    game:    Game
  ): Pide = {
    game.triunfo match {
      case None => throw GameException("Should never happen!")
      case Some(SinTriunfos) =>
        Pide(
          jugador.fichas.maxBy(ficha => if (ficha.esMula) 100 else ficha.arriba.value),
          estrictaDerecha = false
        )
      case Some(TriunfoNumero(triunfo)) =>
        Pide(
          jugador.fichas.maxBy(ficha =>
            (if (ficha.es(triunfo)) 200 else if (ficha.esMula) 100 else 0) + ficha.arriba.value
          ),
          estrictaDerecha = false
        )
    }
  }

  private def da(
    jugador: Jugador,
    game:    Game
  ): Da = {
    val first = game.enJuego.head
    game.triunfo match {
      case None => throw GameException("Should never happen!")
      case Some(SinTriunfos) =>
        val pideNum = first._2.arriba
        Da(
          jugador.fichas
            .filter(_.es(pideNum))
            .sortBy(_.other(pideNum).value)
            .headOption
            .getOrElse(jugador.fichas.minBy(f => if (f.esMula) 100 else f.value))
        )
      case Some(TriunfoNumero(triunfo)) =>
        val pideNum = if (first._2.es(triunfo)) {
          triunfo
        } else {
          first._2.arriba
        }
        Da(
          jugador.fichas
            .filter(_.es(pideNum))
            .sortBy(_.other(pideNum).value)
            .headOption
            .getOrElse(
              jugador.fichas.minBy(f =>
                if (f.es(triunfo)) {
                  triunfo.value - 100 - f.other(triunfo).value
                } else {
                  if (f.esMula) 100 else f.value
                }
              )
            )
        )
    }
  }

  def caite(): Caite = Caite()

  def canta(
    jugador: Jugador,
    game:    Game
  ): Canta = {
    import CuantasCantas._
    val (cuantas, _) = calculaCanto(jugador, game)

    val cuantasCantas =
      if (cuantas <= 4 && jugador.turno)
        Casa
      else if (cuantas <= 4)
        Buenas
      else CuantasCantas.byNum(cuantas)

    if (jugador.turno) {
      Canta(cuantasCantas)
    } else {
      val prev = game.prevPlayer(jugador).cuantasCantas.getOrElse(Casa)
      if (cuantasCantas > prev) {
        Canta(cuantasCantas)
      } else {
        Canta(Buenas)
      }
    }
  }

  override def decideTurn(
    user: User,
    game: Game
  ): Option[PlayEvent] = {
    val jugador = game.jugador(user.id)
    game.gameStatus match {
      case GameStatus.jugando =>
        if (jugador.cantante && jugador.mano && jugador.filas.isEmpty) {
          Option(pideInicial(jugador, game))
        } else if (jugador.mano && game.puedesCaerte(jugador)) {
          Option(caite())
        } else if (jugador.mano) {
          Option(pide(jugador, game))
        } else {
          Option(da(jugador, game))
        }
      case GameStatus.cantando =>
        Option(canta(jugador, game))
      case other =>
        println(s"I'm too dumb to know what to do when $other")
        None
    }
  }

}
