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

import api.ChutiSession
import chuti.{GameState, User, UserId}
import zio.ZIO
import zioslick.RepositoryException

/**
 * This trait defines all of the Model's database methods.
 */
//@accessible
//@mockable
trait Repository {

  def repository: Repository.Service
}

object Repository {
  trait UserOperations extends CRUDOperations[User, UserId, EmptySearch[User]] {
    def unfriend(user: User, enemy: User): RepositoryIO[Boolean]
    def friends(user: User): RepositoryIO[Seq[User]]
  }
  trait GameStateOperations extends CRUDOperations[GameState, Int, EmptySearch[GameState]] {

  }

  trait Service {
    val gameStateOperations: GameStateOperations

    val userOperations: UserOperations
  }
}
