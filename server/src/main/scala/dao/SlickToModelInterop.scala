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

package dao
import java.sql.Timestamp

import chuti.{User, UserId}
import gen.Tables._

trait SlickToModelInterop {
  def UserRow2User(row: UserRow): User = User(
    id = Some(UserId(row.id)),
    email = row.email,
    name = row.name,
    created = row.created.toLocalDateTime,
    lastUpdated = row.created.toLocalDateTime,
    lastLoggedIn = row.lastloggedin.map(_.toLocalDateTime),
    wallet = 0.0, //TODO row.wallet,
    deleted = row.deleted
  )
  def User2UserRow(value: User): UserRow = UserRow(
    id = value.id.fold(0)(_.value),
    hashedpassword = "",
    name = value.name,
    email = value.email,
    created = Timestamp.valueOf(value.created),
    lastupdated = new Timestamp(System.currentTimeMillis()),
    lastloggedin = value.lastLoggedIn.map(Timestamp.valueOf)
  )
}
