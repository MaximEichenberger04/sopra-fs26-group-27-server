# Chat Integration Plan

Plan for implementing in-game chat for Quoridor. Chat is **temporary** — messages exist only for the duration of the match and are discarded when the game ends. No DB persistence.

---

## Summary

The skeleton for chat is already partially in place: `ChatMessage` entity, `ChatWebSocketHandler`, and the `/chat_refresh_websocket` WebSocket endpoint registered in `WebSocketConfig`. `ChatMessageRepository` has been removed since persistence is not needed.

The main work is:
1. Strip JPA annotations from `ChatMessage` (make it a plain POJO)
2. Add chat message storage to `GameStateCache`
3. Implement `ChatWebSocketHandler` (connect, disconnect, receive, broadcast, replay history)
4. Hook eviction into `GameService` so chat is cleared on game end

---

## Current State

| File | Status |
|---|---|
| `entity/ChatMessage.java` | Exists but still has JPA annotations — needs to become a POJO |
| `websocket/ChatWebSocketHandler.java` | Skeleton only — `gameSessions` map present, no methods implemented |
| `websocket/WebSocketConfig.java` | Already registers `/chat_refresh_websocket` — no changes needed |
| `service/GameStateCache.java` | No chat storage — needs a new map |
| `repository/ChatMessageRepository.java` | **Deleted** — was JPA-backed, not needed |

---

## Step 1 — Make `ChatMessage` a Plain POJO

**File:** `entity/ChatMessage.java`

Currently `ChatMessage` is a JPA `@Entity` targeting a `chat_messages` table. Since messages are in-memory only, all JPA annotations must be removed. The fields themselves are correct and stay as-is.

**Remove:**
- `@Entity`, `@Table`, `implements Serializable`, `serialVersionUID`
- All `@Id`, `@GeneratedValue`, `@Column` annotations
- The `jakarta.persistence.*` import

**Keep:**
- `id` (Long) — used to identify messages in outgoing JSON
- `gameId` (Long)
- `userId` (Long)
- `username` (String) — denormalized; avoids a user lookup on every message broadcast
- `text` (String) — nullable; a message may be GIF-only
- `gifUrl` (String) — nullable; a message may be text-only
- `timestamp` (long) — epoch millis, set at receive time

**Result:** A plain POJO, identical in structure to `Pawn` and `Wall`.

---

## Step 2 — Add Chat Storage to `GameStateCache`

**File:** `service/GameStateCache.java`

Add a new map alongside the existing `wallGrids`, `walls`, and `pawns` maps:

```java
private final Map<Long, List<ChatMessage>> chatMessages = new ConcurrentHashMap<>();
```

### New methods to add

**`initGame`** (already exists — extend it):
```java
chatMessages.put(gameId, new CopyOnWriteArrayList<>());
```
Use `CopyOnWriteArrayList` so `ChatWebSocketHandler` can iterate the history concurrently while new messages arrive.

**`addChatMessage`** — called by `ChatWebSocketHandler` when a message is received:
```java
public void addChatMessage(Long gameId, ChatMessage message) {
    chatMessages.get(gameId).add(message);
}
```

**`getChatHistory`** — called on new WebSocket connect to replay history:
```java
public List<ChatMessage> getChatHistory(Long gameId) {
    return chatMessages.getOrDefault(gameId, List.of());
}
```

**`evictGame`** (already exists — extend it):
```java
chatMessages.remove(gameId);
```
This is the only cleanup needed — no DB deletes required.

---

## Step 3 — Implement `ChatWebSocketHandler`

**File:** `websocket/ChatWebSocketHandler.java`

The handler already has:
```java
private final Map<Long, CopyOnWriteArraySet<WebSocketSession>> gameSessions;
```

It needs a reference to `GameStateCache` (injected via constructor) and an `ObjectMapper` for JSON serialization.

### `afterConnectionEstablished`

1. Parse `gameId` from the WebSocket handshake URI query param: `?gameId=<id>`
2. Register the session: `gameSessions.computeIfAbsent(gameId, k -> new CopyOnWriteArraySet<>()).add(session)`
3. Replay chat history: fetch `gameStateCache.getChatHistory(gameId)`, serialize each message, send to the new session only

### `afterConnectionClosed`

1. Parse `gameId` from the session URI
2. Remove the session from `gameSessions.get(gameId)`
3. If the set is now empty, remove the `gameId` key from `gameSessions` to avoid accumulation

### `handleTextMessage`

Incoming JSON payload (from client):
```json
{"userId": 42, "username": "alice", "text": "gg", "gifUrl": null}
```

Steps:
1. Parse `gameId` from the session URI
2. Deserialize the payload into a `ChatMessage`
3. Set `message.setGameId(gameId)` and `message.setTimestamp(System.currentTimeMillis())`
4. Assign a message id (e.g. incrementing counter per game, or `UUID`)
5. Call `gameStateCache.addChatMessage(gameId, message)`
6. Broadcast the full message JSON to **all** sessions in `gameSessions.get(gameId)`

### Broadcast helper

```java
private void broadcast(Long gameId, String json) {
    Set<WebSocketSession> sessions = gameSessions.get(gameId);
    if (sessions == null) return;
    for (WebSocketSession s : sessions) {
        if (s.isOpen()) {
            s.sendMessage(new TextMessage(json));
        }
    }
}
```

Close dead sessions on send failure rather than letting them accumulate.

### Outgoing message JSON

```json
{
  "id": 1,
  "gameId": 7,
  "userId": 42,
  "username": "alice",
  "text": "gg",
  "gifUrl": null,
  "timestamp": 1712150400000
}
```

This matches the format already documented in the class Javadoc.

---

## Step 4 — Eviction on Game End

**File:** `service/GameService.java`

`GameStateCache.evictGame` is already called in `forfeitGame` and after a win. Since `evictGame` will be extended in Step 2 to also call `chatMessages.remove(gameId)`, no additional changes are needed in `GameService`.

Chat is cleared at the same time as walls and pawns — one call, one place.

---

## Step 5 — Input Validation

Add lightweight validation in `handleTextMessage` before storing/broadcasting:

- `text` and `gifUrl` cannot both be null/blank — a message must have content
- `text` length cap (e.g. 500 characters) to prevent abuse
- `userId` must be a player in the game — look up via `gameStateCache` or `gameRepository`
- Reject messages for games that are not `RUNNING` (check `GameStateCache` or `GameRepository`)

Respond to invalid messages by closing the session or sending an error frame — do not broadcast.

---

## What Does NOT Need to Change

| Concern | Why it's fine |
|---|---|
| `WebSocketConfig` | Already registers `/chat_refresh_websocket` with `setAllowedOrigins("*")` |
| `GameWebSocketHandler` | Completely separate handler/endpoint — chat does not touch it |
| `Game` entity | No changes needed — `gameId` on `ChatMessage` is enough to scope messages |
| DB schema | No table needed — chat is fully in-memory |
| Turn system | Chat is not a game action — sending a message does not advance the turn |

---

## Recommended Implementation Order

1. **Strip JPA from `ChatMessage`** — make it a POJO (`entity/ChatMessage.java`)
2. **Add chat map to `GameStateCache`** — `addChatMessage`, `getChatHistory`, extend `initGame` and `evictGame`
3. **Implement `ChatWebSocketHandler`** — connect, disconnect, receive, broadcast, history replay
4. **Add input validation** in `handleTextMessage`
5. **Manual test** — connect two sessions to the same `gameId`, verify history replay on late join, verify eviction on game end
