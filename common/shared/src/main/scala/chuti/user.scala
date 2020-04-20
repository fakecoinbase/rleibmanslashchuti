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

import java.time.LocalDateTime

case class UserId(value: Int) extends AnyVal

sealed trait UserStatus

object UserStatus {
  case object Playing extends UserStatus
  case object Offline extends UserStatus
  case object InLobby extends UserStatus
}

case class User(
  id:               Option[UserId],
  email:            String,
  name:             String,
  userStatus:       UserStatus = UserStatus.Offline,
  currentChannelId: Option[ChannelId] = None,
  created:          LocalDateTime = LocalDateTime.now,
  lastUpdated:      LocalDateTime = LocalDateTime.now,
  lastLoggedIn:     Option[LocalDateTime] = None,
  wallet:           Double = 0.0,
  deleted:          Boolean = false
) {
  def chatChannel: Option[ChannelId] = userStatus match {
    case UserStatus.InLobby => Option(ChannelId.lobbyChannel)
    case UserStatus.Offline => Option(ChannelId.emailChannel)
    case UserStatus.Playing => currentChannelId
  }
}

sealed trait UserEventType
object UserEventType {
  case object Disconnected extends UserEventType
  case object Connected extends UserEventType
  case object Modified extends UserEventType
}

case class UserEvent(
  user:              User,
  isFriendOfCurrent: Boolean,
  userEventType:     UserEventType
)
