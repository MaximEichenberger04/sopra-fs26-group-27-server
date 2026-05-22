# Quoridor Chaos Arena backend

## Introduction

Quoridor is a strategic board game built on two simple actions: move your pawn, or drop a wall to block your rival. The first player to reach the opposite side wins. Simple rules, deep tactics.

Our project brings Quoridor to life as a modern online multiplayer web application, and pushes the classic formula further with smooth matchmaking, real-time gameplay, player progression, and the social layer that turns a game into a community.

The backend is the engine room behind it all. It handles users, authentication, lobbies, game creation, move validation, wall placement rules, Chaos mode abilities, chat, statistics, match history, leaderboards, cosmetics, and live WebSocket updates that keep every player in sync.

Our goal was a server that holds the game state as a single source of truth, enforces every rule centrally so no client can cheat or drift, and exposes reliable APIs that let the frontend stay perfectly synchronized through every move, wall, and ability of a multiplayer match.

## Technologies Used

* Java
* Spring Boot
* Spring Web / REST APIs
* Spring Data JPA / Hibernate
* H2 in-memory database for local development
* WebSockets for live game refresh events
* Gradle
* Docker
* Sonar
* SQL



This is the backend implementation. For the frontend implementation, click [here](https://github.com/MaximEichenberger04/sopra-fs26-group-27-client).

## High-Level Components

### User, Profile, Progression and Cosmetics

User-related functionality is handled mainly by [`UserController`](src/main/java/ch/uzh/ifi/hase/soprafs26/controller/UserController.java), [`UserService`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/UserService.java), and [`UserRepository`](src/main/java/ch/uzh/ifi/hase/soprafs26/repository/UserRepository.java). This component covers registration, login, logout, profile updates, authentication token validation, cosmetics purchasing/equipping, leaderboard data, achievements, and user progression.

The [`User`](src/main/java/ch/uzh/ifi/hase/soprafs26/entity/User.java) entity stores account data, display information, score, XP, level, coins, owned cosmetics, and equipped cosmetics. Statistics and match-related profile data are exposed through [`StatisticsService`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/StatisticsService.java).

### Lobby and Match Setup

The lobby system is implemented through [`LobbyController`](src/main/java/ch/uzh/ifi/hase/soprafs26/controller/LobbyController.java) and [`LobbyService`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/LobbyService.java). It allows players to create lobbies, join open lobbies, join by invite code, update lobby settings, leave lobbies, and start games.

A lobby stores the host, invited/current players, maximum player count, game mode, invite code, map theme, and the created game ID once the match starts. The lobby flow connects directly to the game creation logic in [`GameService`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/GameService.java).

### Game Engine and Move Validation

Core game lifecycle logic is implemented in [`GameService`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/GameService.java). It creates games from lobbies, initializes players and board state, tracks active players, checks win conditions, handles forfeits/disconnects, advances turns, and ends games.

Move and wall placement validation is handled by [`MoveService`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/MoveService.java). It validates standard Quoridor pawn movement, jumps, diagonal jumps, wall placement rules, wall collision, and path availability using BFS so that no player can be fully blocked from reaching their goal.

Game metadata is persisted in [`Game`](src/main/java/ch/uzh/ifi/hase/soprafs26/entity/Game.java), while live board state such as pawns, walls, remaining walls, ability inventories, and temporary effects is managed in [`GameStateCache`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/GameStateCache.java).

### Chaos Mode and Abilities

Chaos mode extends classic Quoridor with ability cards. The available ability types are defined in [`AbilityType`](src/main/java/ch/uzh/ifi/hase/soprafs26/constant/AbilityType.java), and their behavior is implemented in [`AbilityService`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/AbilityService.java).

Supported abilities include:

* `FIREBALL`: destroys wall segments in a target area
* `EARTHQUAKE`: randomly shifts or destroys walls in a target area
* `POISON`: creates a temporary blocked zone
* `FREEZE`: makes an opponent skip their next turn
* `PLUS_TWO_WALLS`: grants additional wall capacity
* `TWO_MOVES`: grants an additional action

The ability system uses the game state cache to track card inventories, poison zones, frozen players, bonus actions, and additional wall counts.

### Real-Time Communication, Chat and GIFs

The backend uses [`GameWebSocketHandler`](src/main/java/ch/uzh/ifi/hase/soprafs26/websocket/GameWebSocketHandler.java) and [`WebSocketConfig`](src/main/java/ch/uzh/ifi/hase/soprafs26/websocket/WebSocketConfig.java) to notify connected clients when game-relevant events occur. Events include moves, wall placements, chat messages, ability usage, forfeits, game starts, and game endings.

Chat functionality is implemented through [`ChatController`](src/main/java/ch/uzh/ifi/hase/soprafs26/controller/ChatController.java), [`ChatService`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/ChatService.java), and [`ChatCache`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/ChatCache.java). Messages are stored in memory for active games and broadcast to clients through WebSocket refresh events.

GIF search is exposed through [`GifController`](src/main/java/ch/uzh/ifi/hase/soprafs26/controller/GifController.java) and [`GifService`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/GifService.java), which integrates with the Klipy API using the `KLIPY_API_KEY` environment variable.

## Launch and Deployment

### Prerequisites

* Java 17 or newer
* Gradle or the included Gradle wrapper
* Optional: Docker
* Optional: `KLIPY_API_KEY` environment variable for GIF search

### Local Development

Run the backend locally:

```Shell
./gradlew bootRun
```

The backend starts on:

```text
http://localhost:8080
```

The local H2 database console is available at:

```text
http://localhost:8080/h2-console
```

Default local database settings are defined in [`application.properties`](src/main/resources/application.properties):

```text
JDBC URL: jdbc:h2:mem:testdb
User: sa
Password: 
```

### Build

```Shell
./gradlew build
```

### Run Tests

```Shell
./gradlew test
```

The test suite covers controllers, services, repositories, DTO mapping, game state cache, move logic, lobby logic, chat, and user functionality.

### Releases

A typical release flow is:

1. Ensure all tests pass with `./gradlew test`.
2. Build the project with `./gradlew build`.
3. Build and push a Docker image if deploying via container infrastructure.
4. Deploy the produced image or application package to the selected hosting platform.

## Illustrations

A typical backend flow is:

* A user registers or logs in through the frontend, and the backend creates or validates the account.
* Players browse and join lobbies through `LobbyController`, which manages lobby state and settings.
* When a match starts, `GameService` initializes game state and the WebSocket handler notifies connected clients.
* During gameplay, `MoveService` validates moves and wall placements while `ChatController` and `GifController` handle chat and GIF features.
* When the game ends, the backend updates statistics, leaderboards, and match history.

## Roadmap

* Add persistent database support beyond the current H2 in-memory database.
* Add integration tests for frontend-backend API flows and WebSocket events.
* Add CI/CD deployment automation for cloud hosting.

## Authors and Acknowledgment

Developed by the SoPra group 27.

Team members:

* Flint Menzi
* Maxim Eichenberger
* Eldar Kryeziu
* Timon Weidmann
* Jonas Metzger

This project was developed as part of the Software Praktikum at the University of Zurich.

## License

Apache License 2.0 — see `LICENSE`.
