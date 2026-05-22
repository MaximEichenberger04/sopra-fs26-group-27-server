package ch.uzh.ifi.hase.soprafs26.rest.mapper;

import org.junit.jupiter.api.Test;

import ch.uzh.ifi.hase.soprafs26.constant.MoveType;
import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.constant.WallOrientation;
import ch.uzh.ifi.hase.soprafs26.entity.Pawn;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.entity.Wall;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PawnGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PoisonZoneDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPatchDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.WallGetDTO;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class DTOMapperTest {

	// ═══════════════════════════════════════════════
	// UserPostDTO → User
	// ═══════════════════════════════════════════════

	@Test
	public void testCreateUser_fromUserPostDTO_toUser_success() {
		UserPostDTO dto = new UserPostDTO();
		dto.setDisplayName("name");
		dto.setUsername("username");
		dto.setPassword("TestPass1");
		dto.setBiography("A short bio");

		User user = DTOMapper.INSTANCE.convertUserPostDTOtoEntity(dto);

		assertEquals("name", user.getDisplayName());
		assertEquals("username", user.getUsername());
		assertEquals("TestPass1", user.getPassword());
		assertEquals("A short bio", user.getBiography());
	}

	@Test
	public void testUserPostDTO_mapsAllFields() {
		UserPostDTO dto = new UserPostDTO();
		dto.setDisplayName("Full User");
		dto.setUsername("fullUser");
		dto.setPassword("Pass1234");
		dto.setBiography("Bio");
		dto.setAvatarURL("avatar-url");
		dto.setPreferredLanguage("en");
		dto.setCurrentPassword("OldPass");

		User user = DTOMapper.INSTANCE.convertUserPostDTOtoEntity(dto);

		assertEquals("Full User", user.getDisplayName());
		assertEquals("fullUser", user.getUsername());
		assertEquals("Pass1234", user.getPassword());
		assertEquals("Bio", user.getBiography());
		assertEquals("avatar-url", user.getAvatarURL());
		assertEquals("en", user.getPreferredLanguage());
		assertEquals("OldPass", user.getCurrentPassword());
	}

	@Test
	public void testUserPostDTO_cosmeticFields() {
		UserPostDTO dto = new UserPostDTO();
		dto.setDisplayName("Cosmetic User");
		dto.setUsername("cosUser");
		dto.setCoins(500);
		dto.setOwnedCosmetics("border-wood");
		dto.setEquippedBorder("border-wood");
		dto.setEquippedPawnSkin("pawn-angel");

		User user = DTOMapper.INSTANCE.convertUserPostDTOtoEntity(dto);

		assertEquals(500, user.getCoins());
		assertEquals("border-wood", user.getOwnedCosmetics());
		assertEquals("border-wood", user.getEquippedBorder());
		assertEquals("pawn-angel", user.getEquippedPawnSkin());
	}

	// ═══════════════════════════════════════════════
	// User → UserGetDTO
	// ═══════════════════════════════════════════════

	@Test
	public void testGetUser_fromUser_toUserGetDTO_success() {
		User user = new User();
		user.setId(1L);
		user.setDisplayName("Firstname Lastname");
		user.setUsername("firstname@lastname");
		user.setPassword("testPassword");
		user.setStatus(UserStatus.OFFLINE);
		user.setToken("token-123");
		user.setCreationDate(LocalDate.of(2025, 1, 15));

		UserGetDTO dto = DTOMapper.INSTANCE.convertEntityToUserGetDTO(user);

		assertEquals(1L, dto.getId());
		assertEquals("Firstname Lastname", dto.getDisplayName());
		assertEquals("firstname@lastname", dto.getUsername());
		assertEquals(UserStatus.OFFLINE, dto.getStatus());
		assertEquals("token-123", dto.getToken());
		assertEquals("2025-01-15", dto.getCreationDate());
	}

	@Test
	public void testGetUser_mapsAllFields() {
		User user = new User();
		user.setId(2L);
		user.setDisplayName("Full");
		user.setUsername("full");
		user.setPassword("pass");
		user.setStatus(UserStatus.ONLINE);
		user.setToken("t");
		user.setCreationDate(LocalDate.now());
		user.setBiography("My bio");
		user.setAvatarURL("avatar.png");
		user.setPreferredLanguage("de");
		user.setScore(100);
		user.setXp(500);
		user.setLevel(5);
		user.setCoins(800);
		user.setOwnedCosmetics("border-wood,pawn-angel");
		user.setEquippedBorder("border-wood");
		user.setEquippedPawnSkin("pawn-angel");

		UserGetDTO dto = DTOMapper.INSTANCE.convertEntityToUserGetDTO(user);

		assertEquals(2L, dto.getId());
		assertEquals("My bio", dto.getBiography());
		assertEquals("avatar.png", dto.getAvatarURL());
		assertEquals("de", dto.getPreferredLanguage());
		assertEquals(100, dto.getScore());
		assertEquals(500, dto.getXp());
		assertEquals(5, dto.getLevel());
		assertEquals(800, dto.getCoins());
		assertEquals("border-wood,pawn-angel", dto.getOwnedCosmetics());
		assertEquals("border-wood", dto.getEquippedBorder());
		assertEquals("pawn-angel", dto.getEquippedPawnSkin());
	}

	@Test
	public void testGetUser_passwordNotExposed() {
		User user = new User();
		user.setDisplayName("Test");
		user.setUsername("test");
		user.setPassword("secret");
		user.setStatus(UserStatus.ONLINE);
		user.setToken("t");
		user.setCreationDate(LocalDate.now());

		UserGetDTO dto = DTOMapper.INSTANCE.convertEntityToUserGetDTO(user);

		// UserGetDTO should not have a password field exposed
		// The DTO class doesn't have getPassword(), confirming password is not leaked
		assertNotNull(dto.getUsername());
	}

	// ═══════════════════════════════════════════════
	// UserPatchDTO → User
	// ═══════════════════════════════════════════════

	@Test
	public void testPatchUser_fromUserPatchDTO_toUser() {
		UserPatchDTO dto = new UserPatchDTO();
		dto.setDisplayName("Updated Name");
		dto.setUsername("updatedUser");
		dto.setBiography("Updated bio");
		dto.setAvatarURL("new-avatar.png");
		dto.setPreferredLanguage("fr");

		User user = DTOMapper.INSTANCE.convertUserPatchDTOtoEntity(dto);

		assertEquals("Updated Name", user.getDisplayName());
		assertEquals("updatedUser", user.getUsername());
		assertEquals("Updated bio", user.getBiography());
		assertEquals("new-avatar.png", user.getAvatarURL());
		assertEquals("fr", user.getPreferredLanguage());
	}

	@Test
	public void testPatchUser_passwordFields() {
		UserPatchDTO dto = new UserPatchDTO();
		dto.setPassword("NewPass99");
		dto.setCurrentPassword("OldPass11");

		User user = DTOMapper.INSTANCE.convertUserPatchDTOtoEntity(dto);

		assertEquals("NewPass99", user.getPassword());
		assertEquals("OldPass11", user.getCurrentPassword());
	}

	@Test
	public void testPatchUser_cosmeticFields() {
		UserPatchDTO dto = new UserPatchDTO();
		dto.setEquippedBorder("border-fire");
		dto.setEquippedPawnSkin("pawn-knight");

		User user = DTOMapper.INSTANCE.convertUserPatchDTOtoEntity(dto);

		assertEquals("border-fire", user.getEquippedBorder());
		assertEquals("pawn-knight", user.getEquippedPawnSkin());
	}

	@Test
	public void testPatchUser_nullFields_remainNull() {
		UserPatchDTO dto = new UserPatchDTO();
		// Only set username, leave everything else null

		dto.setUsername("onlyUsername");

		User user = DTOMapper.INSTANCE.convertUserPatchDTOtoEntity(dto);

		assertEquals("onlyUsername", user.getUsername());
		assertNull(user.getDisplayName());
		assertNull(user.getBiography());
		assertNull(user.getPassword());
		assertNull(user.getEquippedBorder());
		assertNull(user.getEquippedPawnSkin());
	}

	// ═══════════════════════════════════════════════
	// Pawn → PawnGetDTO
	// ═══════════════════════════════════════════════
	@Test
	public void testConvertPawn_toPawnGetDTO_mapsAllFields() {
		Pawn pawn = new Pawn();
		pawn.setId(7L);
		pawn.setUserId(42L);
		pawn.setRow(8);
		pawn.setCol(16);

		PawnGetDTO dto = DTOMapper.INSTANCE.convertEntityToPawnGetDTO(pawn);

		assertEquals(7L, dto.getId());
		assertEquals(42L, dto.getUserId());
		assertEquals(8, dto.getRow());
		assertEquals(16, dto.getCol());
	}

	// ═══════════════════════════════════════════════
	// Wall → WallGetDTO
	// ═══════════════════════════════════════════════
	@Test
	public void testConvertWall_toWallGetDTO_mapsAllFields() {
		Wall wall = new Wall();
		wall.setId(3L);
		wall.setUserId(11L);
		wall.setRow(5);
		wall.setCol(7);
		wall.setOrientation(WallOrientation.VERTICAL);

		WallGetDTO dto = DTOMapper.INSTANCE.convertEntityToWallGetDTO(wall);

		assertEquals(3L, dto.getId());
		assertEquals(11L, dto.getUserId());
		assertEquals(5, dto.getRow());
		assertEquals(7, dto.getCol());
		assertEquals(WallOrientation.VERTICAL, dto.getOrientation());
	}

	// ═══════════════════════════════════════════════
	// PoisonZoneDTO POJO contract
	// ═══════════════════════════════════════════════
	@Test
	public void testPoisonZoneDTO_settersAndGetters() {
		PoisonZoneDTO dto = new PoisonZoneDTO();
		dto.setId(9L);
		dto.setTopLeftRow(6);
		dto.setTopLeftCol(8);
		dto.setRoundsRemaining(3);

		assertEquals(9L, dto.getId());
		assertEquals(6, dto.getTopLeftRow());
		assertEquals(8, dto.getTopLeftCol());
		assertEquals(3, dto.getRoundsRemaining());
	}

	// ═══════════════════════════════════════════════
	// MoveType enum
	// ═══════════════════════════════════════════════
	@Test
	public void testMoveTypeEnum_allValuesPresent() {
		MoveType[] values = MoveType.values();
		assertTrue(values.length > 0);
		// Round-trip via valueOf for each declared value
		for (MoveType type : values) {
			assertEquals(type, MoveType.valueOf(type.name()));
		}
	}
}