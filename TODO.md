# TODO 

- DONE. Paint all dominos
- DONE. Finalize wireframe
    - DONE Where does the "table" go?
- DONE. Database
    - DONE mysql with json column 
- DONE. Server
    - decide server technology.
      - DONE Zio, akka-http, caliban, circe
    - 
- DONE. Client
    - DONE decide client technology
        - DONE Scalajs, scalablytyped, websockets, caliban, circe
    - code pages
        - Set up table
        - Game page
        - Cuentas
- User system
    - DONE Registration
    - DONE Login
    - DONE Lost password
    - Set up table
- DONE. Code game engine     
- FUTURE. Chose domino back logo

##Random
- Consistent language
- Translate to English
- Versioning and upgrade in Game object

##Server
- Rehydrate the system after restart
    - Tokens
    - Games
    - Logged in User repo
    - (in client) load last 10 minutes of messages

## Web client
- Clean up GameException errors
- Clean up presentation of really bad errors

### Pregame
DONE - Create new game
DONE - Join random game (first user)
DONE - Join random game (second and third users)
- Join random game (fourth user)
DONE - Abandon unstarted game
- Abandon a started game
DONE - Invite one existing user to game
- Invite non-existing user to game
DONE - Accept game invite
DONE - Reject game invite

### Lobby
- Mark users who are invited or playing in our game already
- On last accept the screen ends up in "No es el momento de esperandoJugadoresInvitados", como parte e RedoEvent.
    This happens because joining is not an event, so the system doesn't know we're ready to play

### Game
- Main screen setup
- Decide what can be done depending on where you are (look at bot)
- Animations of Borlotes
    - Sopa
    - Poner ficha
    - Hoyo
    - Chuti
    - El hoyo que camina
    - Tecnico

### All these graphql calls should be exercised

```def broadcastGameEvent(gameEvent: GameEvent): ZIO[GameLayer, GameException, GameEvent]```

```def joinRandomGame(): ZIO[GameLayer, GameException, Game]```

```def newGame():        ZIO[GameLayer, GameException, Game]```

```def play(gameId:    GameId,playEvent: PlayEvent):                  ZIO[GameLayer, GameException, Game]```

```def getGameForUser: ZIO[GameLayer, GameException, Option[Game]]```

```def getGame(gameId:     GameId): ZIO[GameLayer, GameException, Option[Game]]```

```def abandonGame(gameId: GameId): ZIO[GameLayer, GameException, Boolean]```

```def getFriends:       ZIO[GameLayer, GameException, Seq[UserId]]```

```def getGameInvites:   ZIO[GameLayer, GameException, Seq[Game]]```

```def getLoggedInUsers: ZIO[GameLayer, GameException, Seq[User]]```

```def inviteToGame(userId: UserId,gameId: GameId): ZIO[GameLayer, GameException, Boolean]```

```def inviteFriend(friend:          User):   ZIO[GameLayer, GameException, Boolean]```

```def acceptGameInvitation(gameId:  GameId): ZIO[GameLayer, GameException, Game]```

```def declineGameInvitation(gameId: GameId): ZIO[GameLayer, GameException, Boolean]```

```def acceptFriendship(friend:      User):   ZIO[GameLayer, GameException, Boolean]```

```def unfriend(enemy:               User):   ZIO[GameLayer, GameException, Boolean]```

```def gameStream(gameId: GameId): ZStream[GameLayer, GameException, GameEvent]```

```def userStream: ZStream[GameLayer, GameException, UserEvent]```

## Admin screen
Games playing, game index, event list
Glimpse into queues

## Bugs
- "Juega con quien sea" is not sending out a game event when joining 
- Last person joining is causing huge issues.
- onComponentUnmount... close down ws sessions 
- Aplicando #C, ya esta listo para caerse pero el juego no lo detecta correctamente.
- Aplicando #C, me encuentro en una posicion en la que dos personas pueden pedir... porque?

## To test
- Invite by email, unhappy paths
- Transfer of ownership when original user abandons game
- Add unique constraint to friends

## Other
- Remove all "Repository with DatabaseProvider", Repository should stand on it's own 

#Interesting games
#Por alguna razon se la deberia llevar test1, pero se la lleva aoeu
update game set current_index = 108, lastSnapshot = '{"id": {"value": 62}, "created": "2020-06-19T11:27:58.450679", "enJuego": [[{"value": 47}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 1}}], [{"value": 39}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 4}}], [{"value": 1}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 3}}]], "triunfo": {"TriunfoNumero": {"num": {"value": 2}}}, "jugadores": [{"mano": false, "user": {"id": {"value": 1}, "name": "Roberto", "email": "roberto@leibman.net", "active": true, "created": "2020-04-09T00:28:28", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-04-09T00:28:28", "lastLoggedIn": "2020-05-22T15:36:12"}, "filas": [], "turno": false, "cuenta": [{"esHoyo": true, "puntos": 0}, {"esHoyo": false, "puntos": 2}, {"esHoyo": false, "puntos": 5}], "fichas": [{"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 0}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 5}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}, {"mano": false, "user": {"id": {"value": 39}, "name": "test1", "email": "roberto+test1@leibman.net", "active": true, "created": "2020-05-25T11:01:29", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-05-25T11:01:29", "lastLoggedIn": null}, "filas": [], "turno": false, "cuenta": [{"esHoyo": true, "puntos": 0}, {"esHoyo": false, "puntos": 6}], "fichas": [{"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 6}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 5}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}, {"mano": true, "user": {"id": {"value": 47}, "name": "aoeuaoeu", "email": "roberto+aoeuaoeu@leibman.net", "active": true, "created": "2020-06-04T09:15:58", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-06-04T09:15:58", "lastLoggedIn": null}, "filas": [{"index": 0, "fichas": [{"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 6}}]}], "turno": true, "cuenta": [{"esHoyo": false, "puntos": 2}, {"esHoyo": false, "puntos": 1}], "fichas": [{"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 6}}], "invited": false, "cantante": true, "statusString": "", "cuantasCantas": {"Casa": {}}}, {"mano": false, "user": {"id": {"value": 46}, "name": "aoeu", "email": "roberto+aoeu@leibman.net", "active": true, "created": "2020-06-04T09:12:10", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-06-04T09:12:10", "lastLoggedIn": null}, "filas": [], "turno": false, "cuenta": [{"esHoyo": false, "puntos": 5}], "fichas": [{"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 1}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 5}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}], "gameStatus": {"jugando": {}}, "statusString": "aoeuaoeu se llevo la ultima fila", "estrictaDerecha": false, "satoshiPerPoint": 100, "currentEventIndex": 108}';
#Tanto test1 como aoeuaoeu pueden pedir, como esta eso?
{"id": {"value": 62}, "created": "2020-06-19T11:27:58.450679", "enJuego": [], "triunfo": {"TriunfoNumero": {"num": {"value": 2}}}, "jugadores": [{"mano": false, "user": {"id": {"value": 1}, "name": "Roberto", "email": "roberto@leibman.net", "active": true, "created": "2020-04-09T00:28:28", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-04-09T00:28:28", "lastLoggedIn": "2020-05-22T15:36:12"}, "filas": [], "turno": false, "cuenta": [{"esHoyo": true, "puntos": 0}, {"esHoyo": false, "puntos": 2}, {"esHoyo": false, "puntos": 5}], "fichas": [{"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 0}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 5}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}, {"mano": true, "user": {"id": {"value": 39}, "name": "test1", "email": "roberto+test1@leibman.net", "active": true, "created": "2020-05-25T11:01:29", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-05-25T11:01:29", "lastLoggedIn": null}, "filas": [{"index": 1, "fichas": [{"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 1}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 1}}]}, {"index": 2, "fichas": [{"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 3}}]}, {"index": 3, "fichas": [{"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 6}}]}], "turno": false, "cuenta": [{"esHoyo": true, "puntos": 0}, {"esHoyo": false, "puntos": 6}], "fichas": [{"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 6}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 6}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}, {"mano": true, "user": {"id": {"value": 47}, "name": "aoeuaoeu", "email": "roberto+aoeuaoeu@leibman.net", "active": true, "created": "2020-06-04T09:15:58", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-06-04T09:15:58", "lastLoggedIn": null}, "filas": [{"index": 0, "fichas": [{"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 6}}]}], "turno": true, "cuenta": [{"esHoyo": false, "puntos": 2}, {"esHoyo": false, "puntos": 1}], "fichas": [{"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 6}}], "invited": false, "cantante": true, "statusString": "", "cuantasCantas": {"Casa": {}}}, {"mano": false, "user": {"id": {"value": 46}, "name": "aoeu", "email": "roberto+aoeu@leibman.net", "active": true, "created": "2020-06-04T09:12:10", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-06-04T09:12:10", "lastLoggedIn": null}, "filas": [], "turno": false, "cuenta": [{"esHoyo": false, "puntos": 5}], "fichas": [{"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 5}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}]

