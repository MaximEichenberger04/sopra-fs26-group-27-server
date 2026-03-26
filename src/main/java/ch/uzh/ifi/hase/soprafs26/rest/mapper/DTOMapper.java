package ch.uzh.ifi.hase.soprafs26.rest.mapper;

import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPostDTO;

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
	@Mapping(source = "score", target = "score")
	@Mapping(source = "xp", target = "xp")
	@Mapping(source = "level", target = "level")
	@Mapping(source = "token", target = "token")
	@Mapping(source = "creationDate", target = "creationDate")
	@Mapping(source = "status", target = "status")
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
}
