package ch.uzh.ifi.hase.soprafs26.rest.mapper;

import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

// User
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPostDTO;

// Lobby
import ch.uzh.ifi.hase.soprafs26.entity.Lobby;
import ch.uzh.ifi.hase.soprafs26.rest.dto.LobbyGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.LobbyPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.LobbyPutDTO;

// Game
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.Pawn;
import ch.uzh.ifi.hase.soprafs26.entity.Wall;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PawnGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.WallGetDTO;

/**
 * DTOMapper
 * This class is responsible for generating classes that will automatically
 * transform/map the internal representation
 * of an entity (e.g., the User) to the external/API representation (e.g.,
 * UserGetDTO for getting, UserPostDTO for creating)
 * and vice versa.
 * Additional mappers can be defined for new entities.
 * Always created one mapper for getting information (GET) and one mapper for
 * creating information (POST).
 */
@Mapper
public interface DTOMapper {

	DTOMapper INSTANCE = Mappers.getMapper(DTOMapper.class);

	@Mapping(source = "username", target = "username")
	@Mapping(source = "password", target = "password")
	@Mapping(source = "displayName", target = "displayName")
	@Mapping(source = "biography", target = "biography")
	@Mapping(source = "avatarURL", target = "avatarURL")
	@Mapping(source = "preferredLanguage", target = "preferredLanguage")
	User convertUserPatchDTOtoEntity(UserPatchDTO userPatchDTO);

	@Mapping(source = "username", target = "username")
	@Mapping(source = "displayName", target = "displayName")
	@Mapping(source = "biography", target = "biography")
	@Mapping(source = "avatarURL", target = "avatarURL")
	@Mapping(source = "preferredLanguage", target = "preferredLanguage")
	@Mapping(source = "score", target = "score")
	@Mapping(source = "xp", target = "xp")
	@Mapping(source = "level", target = "level")
	@Mapping(source = "token", target = "token")
	@Mapping(source = "creationDate", target = "creationDate")
	@Mapping(source = "status", target = "status")
	@Mapping(source = "currentPassword", target = "currentPassword")
	User convertUserPostDTOtoEntity(UserPostDTO userPostDTO);

	@Mapping(source = "id", target = "id")
	@Mapping(source = "username", target = "username")
	@Mapping(source = "displayName", target = "displayName")
	@Mapping(source = "biography", target = "biography")
	@Mapping(source = "avatarURL", target = "avatarURL")
	@Mapping(source = "preferredLanguage", target = "preferredLanguage")
	@Mapping(source = "score", target = "score")
	@Mapping(source = "xp", target = "xp")
	@Mapping(source = "level", target = "level")
	@Mapping(source = "token", target = "token")
	@Mapping(source = "creationDate", target = "creationDate")
	@Mapping(source = "status", target = "status")
	UserGetDTO convertEntityToUserGetDTO(User user);

	// LOBBY MAPPINGS

	@Mapping(source = "id", target = "id")
	@Mapping(source = "name", target = "name")
	@Mapping(source = "inviteCode", target = "inviteCode")
	@Mapping(source = "maxPlayers", target = "maxPlayers")
	@Mapping(source = "hostId", target = "hostId")
	@Mapping(source = "currentPlayers", target = "currentPlayers")
	@Mapping(source = "lobbyStatus", target = "lobbyStatus")
	@Mapping(source = "gameMode", target = "gameMode")
	@Mapping(source = "playerIds", target = "playerIds")
	@Mapping(source = "gameId", target = "gameId")
	LobbyGetDTO convertEntityToLobbyGetDTO(Lobby lobby);

	@Mapping(source = "name", target = "name")
	@Mapping(source = "gameMode", target = "gameMode")
	@Mapping(source = "maxPlayers", target = "maxPlayers")
	Lobby convertLobbyPostDTOtoEntity(LobbyPostDTO lobbyPostDTO);

	@Mapping(source = "name", target = "name")
	@Mapping(source = "gameMode", target = "gameMode")
	@Mapping(source = "maxPlayers", target = "maxPlayers")
	Lobby convertLobbyPutDTOtoEntity(LobbyPutDTO lobbyPutDTO);

	// GAME MAPPINGS
	// Note: pawns and walls lists are populated manually in GameService, ignored here.

	@Mapping(source = "id", target = "id")
	@Mapping(source = "lobbyId", target = "lobbyId")
	@Mapping(source = "gameStatus", target = "gameStatus")
	@Mapping(source = "sizeBoard", target = "sizeBoard")
	@Mapping(source = "creatorId", target = "creatorId")
	@Mapping(source = "currentTurnUserId", target = "currentTurnUserId")
	@Mapping(source = "wallsPerPlayer", target = "wallsPerPlayer")
	@Mapping(source = "winnerId", target = "winnerId")
	@Mapping(source = "playerIds", target = "playerIds")
	@Mapping(target = "pawns", ignore = true)
	@Mapping(target = "walls", ignore = true)
	GameGetDTO convertEntityToGameGetDTO(Game game);

	@Mapping(source = "id", target = "id")
	@Mapping(source = "userId", target = "userId")
	@Mapping(source = "row", target = "row")
	@Mapping(source = "col", target = "col")
	PawnGetDTO convertEntityToPawnGetDTO(Pawn pawn);

	@Mapping(source = "id", target = "id")
	@Mapping(source = "userId", target = "userId")
	@Mapping(source = "row", target = "row")
	@Mapping(source = "col", target = "col")
	@Mapping(source = "orientation", target = "orientation")
	WallGetDTO convertEntityToWallGetDTO(Wall wall);
}
