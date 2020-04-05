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

package routes

import java.math.BigInteger
import java.security.SecureRandom

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{AuthorizationFailedRejection, Directives, Route}
import api._
import better.files.File
import buildinfo.BuildInfo
import chuti.{User, UserId}
import com.softwaremill.session.CsrfDirectives.setNewCsrfToken
import com.softwaremill.session.CsrfOptions.checkHeader
import courier.{Envelope, Multipart}
import dao.{DatabaseProvider, EmptySearch, Repository}
import game.GameServer
import io.circe.generic.auto._
import javax.mail.internet.InternetAddress
import mail.Postman
import scalacache.Cache
import scalacache.caffeine.CaffeineCache
import slick.basic.BasicBackend
import zio.{Task, UIO, ZIO}

import scala.concurrent.duration._

trait AuthRoute
    extends CRUDRoute[User, UserId, EmptySearch[User]] with Repository with Postman with Config
    with SessionUtils with HasActorSystem {

  lazy private val adminSession = ChutiSession(GameServer.god)

  override def crudRoute: CRUDRoute.Service[User, UserId, EmptySearch[User]] =
    new CRUDRoute.Service[User, UserId, EmptySearch[User]]() with ZIODirectives with Directives {

      private val staticContentDir: String = config.getString("chuti.staticContentDir")

      override val url: String = "auth"

      override val ops = repository.userOperations

      override def getPK(obj: User): UserId = obj.id.get

      override val databaseProvider: DatabaseProvider.Service = new DatabaseProvider.Service {
        override def db: UIO[BasicBackend#DatabaseDef] = ???
      }

      import scalacache.ZioEffect.modes._

      implicit val userTokenCache: Cache[User] = CaffeineCache[User]

      override def unauthRoute: Route =
        extractLog { log =>
          path("serverVersion") {
            complete(BuildInfo.version)
          } ~ path("loginForm") {
            get {
              println(s"$staticContentDir/login.html")
              println(File(s"$staticContentDir/login.html").path.toAbsolutePath)
              getFromFile(s"$staticContentDir/login.html")
            }
          } ~
            path("passwordReset") {
              post {
                parameters(Symbol("token").as[String]) { token =>
                  entity(as[String]) { password =>
                    complete {
                      val z = (for {
                        userOpt <- scalacache.get(token)
                        passwordChanged <- ZIO.foreach(userOpt)(user =>
                          ops.changePassword(user, password)
                        )
                        _ <- ZIO.foreach(userOpt)(user => scalacache.remove(token))
                      } yield passwordChanged.getOrElse(false))

                      val runtime = zio.Runtime.default

                      z.provideLayer(fullLayer(adminSession)).tapError(e =>
                          Task.succeed(e.printStackTrace())
                        ).absorb

                    }
                  }
                }
              }
            } ~
            path("passwordRecoveryRequest") {
              post {
                entity(as[String]) { email =>
                  complete {
                    (for {
                      userOpt <- ops.userByEmail(email)
                      userTokenOpt <- ZIO
                        .foreach(userOpt) { user =>
                          val random = SecureRandom.getInstanceStrong
                          val token = new BigInteger(12 * 5, random).toString(32)
                          scalacache.put(token)(user, Option(3.hours)).as((user, token))
                        }
                      emailed <- ZIO.foreach(userTokenOpt) { userToken =>
                        postman.deliver(
                          Envelope
                            .from(new InternetAddress("system@chuti.fun"))
                            .to(new InternetAddress(userToken._1.email))
                            .subject(
                              s"Password reset request"
                            )
                            .content(Multipart().html(s"""<html><body>
                               | <p>We are sorry you've lost your password</p>
                               | <p>We have temporarily created a link for you that will allow you to reset it.</p>
                               | <p>Please go here to reset it: <a href="http://www.chuti.fun/loginForm?passwordReset=true&token=${userToken._2}">http://www.meal-o-rama.com/loginForm?passwordReset=true&?token=${userToken._2}</a>.</p>
                               | <p>Note that this token will expire after a while, please change your password as soon as you can</p>
                               |</body></html>""".stripMargin))
                        )
                      }
                    } yield emailed.nonEmpty).provideLayer(fullLayer(adminSession)).absorb
                  }
                }
              }
            } ~
            path("doLogin") {
              post {
                formFields(
                  (
                    Symbol("accountname").as[String],
                    Symbol("username").as[String],
                    Symbol("password").as[String]
                  )
                ) { (accountname, username, password) =>
                  log.info(s"Logging in $accountname:$username")

                  val zio = ops
                    .login(accountname, username, password).map {
                      case Some(user) =>
                        mySetSession(ChutiSession(user)) {
                          setNewCsrfToken(checkHeader) { ctx =>
                            ctx.redirect("/", StatusCodes.Found)
                          }
                        }
                      case None =>
                        redirect("/loginForm?bad=true", StatusCodes.Found)
                    }
                    .provideLayer(fullLayer(adminSession)).absorb
                  zio

                //TODO log login

                }
              }
            } ~
            pathPrefix("unauth") {
              get {
                val allowed = Set(
                  "/login.html",
                  "/css/chuti.css",
                  "/css/app-sui-theme.css",
                  "/chuti-login-opt-bundle.js",
                  "/chuti-login-opt-bundle.js.map",
                  "/css/app.css",
                  "/images/favicon.png"
                )

                extractUnmatchedPath { path =>
                  if (allowed(path.toString())) {
                    log.debug(s"GET $path")
                    encodeResponse {
                      getFromDirectory(staticContentDir)
                    }
                  } else {
                    println(s"Trying to get $path, not in $allowed")
                    reject(AuthorizationFailedRejection)
                  }
                }
              }
            }
        }

      override def other(session: ChutiSession): Route =
        //randomTokenCsrfProtection(checkHeader) //TODO this is necessary, but it wasn't working, so we're leaving it for now.
        // This should be protected and accessible only when logged in
        path("whoami") {
          get {
            complete(Seq(session.user))
          }
        } ~
          path("doLogout") {
            extractLog { log =>
              post {
                myInvalidateSession { ctx =>
                  log.info(s"Logging out $session")
                  ctx.redirect("/loginForm", StatusCodes.Found)
                }
              } ~
                get {
                  myInvalidateSession { ctx =>
                    log.info(s"Logging out $session")
                    ctx.redirect("/loginForm", StatusCodes.Found)
                  }
                }
            }
          }
    }

}
