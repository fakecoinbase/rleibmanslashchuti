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

package router

import app.{ChutiState, GameViewMode, GlobalDialog}
import components.components.ChutiComponent
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, ScalaComponent}
import org.scalajs.dom._
import pages._
import typings.semanticUiReact.components._

object AppRouter extends ChutiComponent {

  sealed trait AppPage

  case object GameAppPage extends AppPage

  case object RulesAppPage extends AppPage

  case object UserSettingsAppPage extends AppPage

  object DialogRenderer {
    class Backend($ : BackendScope[_, _]) {

      def render(): VdomElement = ChutiState.ctx.consume { chutiState =>
        def renderCuentasDialog: VdomArray = {
          chutiState.gameInProgress.toVdomArray { game =>
            Modal(key = "cuentasDialog", open = chutiState.currentDialog == GlobalDialog.cuentas)(
              ModalHeader()("Cuentas"),
              ModalContent()(
                Table()(
                  TableHeader()(
                    TableRow(key = "cuentasHeader")(
                      TableHeaderCell()("Jugador"),
                      TableHeaderCell()("Cuentas"),
                      TableHeaderCell()("Total"),
                      TableHeaderCell()("Satoshi")
                    )
                  ),
                  game.jugadores.zipWithIndex.toVdomArray {
                    case (jugador, jugadorIndex) =>
                      TableRow(key = s"cuenta$jugadorIndex")(
                        TableCell()(jugador.user.name),
                        TableCell()(
                          jugador.cuenta.zipWithIndex.toVdomArray {
                            case (cuenta, cuentaIndex) =>
                              <.span(
                                ^.key       := s"cuenta_num${jugadorIndex}_$cuentaIndex",
                                ^.className := (if (cuenta.esHoyo) "hoyo" else ""),
                                s"${if (cuenta.puntos > 0) "+" else ""} ${cuenta.puntos}"
                              )
                          }
                        ),
                        TableCell()(jugador.cuenta.map(_.puntos).sum),
                        TableCell()(0)
                      )
                  }
                )
              ),
              ModalActions()(Button(compact = true, basic = true, onClick = { (_, _) =>
                chutiState.showDialog(GlobalDialog.none)
              })("Ok"))
            )
          }
        }
        VdomArray(renderCuentasDialog)
      }
    }
    private val component = ScalaComponent
      .builder[Unit]("content")
      .renderBackend[Backend]
      .build
    def apply() = component()
  }

  private def layout(
    page:       RouterCtl[AppPage],
    resolution: Resolution[AppPage]
  ): VdomElement = {
    assert(page != null)
    ChutiState.ctx.consume { chutiState =>
      def renderMenu = {
        VdomArray(
          <.div(
            ^.key       := "menu",
            ^.height    := 100.pct,
            ^.className := "no-print headerMenu",
            Menu(
              attached = false,
              compact = true,
              text = true,
              borderless = true
            )(
              Dropdown(
                item = true,
//                simple = true,
                compact = true,
                text = "☰ Menu"
              )(
                DropdownMenu()(
                  chutiState.gameInProgress.filter(_.gameStatus.enJuego).map { _ =>
                    VdomArray(
                      MenuItem(onClick = { (_, _) =>
                        chutiState.onGameViewModeChanged(GameViewMode.game)
                      })("Entrar al Juego"),
                      MenuItem(onClick = { (_, _) =>
                        chutiState.showDialog(GlobalDialog.cuentas)
                      })("Cuentas")
                    )
                  },
                  MenuItem(onClick = { (_, _) =>
                    chutiState.onGameViewModeChanged(GameViewMode.lobby)
                  })("Lobby"),
                  Divider()(),
                  MenuItem(onClick = { (_, _) =>
                    Callback.alert("en construcción") //TODO write this
                  })("Reglas de Chuti"),
                  MenuItem(onClick = { (_, _) =>
                    Callback {
                      document.location.href = "/api/auth/doLogout"
                    }
                  })("Cerrar sesión"),
                  MenuItem(onClick = { (_, _) =>
                    Callback.alert("en construcción") //TODO write this
                  })("Administración de usuario"),
                  MenuItem(onClick = { (_, _) =>
                    Callback.alert("en construcción") //TODO write this
                  })("Acerca de Chuti")
                )
              )
            )
          ),
          <.div(
            ^.key       := "user",
            ^.className := "user",
            s"${chutiState.user.fold("")(u => s"Hola ${u.name}!")}"
          )
        )
      }

      <.div(
        ^.className := "innerContent",
        <.div(^.className := "header", renderMenu, DialogRenderer()),
        resolution.render()
      )
    }
  }

  private val config: RouterConfig[AppPage] = RouterConfigDsl[AppPage].buildConfig { dsl =>
    import dsl._

    (trimSlashes
      | staticRoute("#game", GameAppPage) ~> renderR(_ => GamePage())
      | staticRoute("#rules", RulesAppPage) ~> renderR(_ => RulesPage())
      | staticRoute("#userSettings", UserSettingsAppPage) ~> renderR(_ => UserSettingsPage()))
      .notFound(redirectToPage(GameAppPage)(SetRouteVia.HistoryReplace))
      .renderWith(layout)
  }
  private val baseUrl: BaseUrl = BaseUrl.fromWindowOrigin_/

  val router: Router[AppPage] = Router.apply(baseUrl, config)
}
