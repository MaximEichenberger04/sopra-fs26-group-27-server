package ch.uzh.ifi.hase.soprafs26.controller;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPatchDTO;
import ch.uzh.ifi.hase.soprafs26.service.UserService;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserControllerTest
 * This is a WebMvcTest which allows to test the UserController i.e. GET/POST
 * request without actually sending them over the network.
 * This tests if the UserController works.
 */
@WebMvcTest(UserController.class)
public class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	private User createMockUser() {
		User user = new User();
		user.setId(1L);
		user.setDisplayName("Test User");
		user.setUsername("testUser");
		user.setPassword("TestPass1");
		user.setToken("test-token-123");
		user.setStatus(UserStatus.ONLINE);
		user.setCreationDate(LocalDate.now());
		user.setCoins(1000);
		return user;
	}

	// ═══════════════════════════════════════════════
	// GET /users
	// ═══════════════════════════════════════════════

	@Test
	public void getUsers_returnsJsonArray() throws Exception {
		User user = createMockUser();
		given(userService.getUsers()).willReturn(Collections.singletonList(user));

		mockMvc.perform(get("/users").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].displayName", is("Test User")))
				.andExpect(jsonPath("$[0].username", is("testUser")))
				.andExpect(jsonPath("$[0].status", is("ONLINE")));
	}

	@Test
	public void getUsers_emptyList_returnsEmptyArray() throws Exception {
		given(userService.getUsers()).willReturn(List.of());

		mockMvc.perform(get("/users").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	// ═══════════════════════════════════════════════
	// POST /users (register)
	// ═══════════════════════════════════════════════

	@Test
	public void createUser_validInput_returnsCreated() throws Exception {
		User user = createMockUser();

		UserPostDTO dto = new UserPostDTO();
		dto.setDisplayName("Test User");
		dto.setUsername("testUser");
		dto.setPassword("TestPass1");

		given(userService.createUser(any())).willReturn(user);

		mockMvc.perform(post("/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(asJsonString(dto)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", is(1)))
				.andExpect(jsonPath("$.displayName", is("Test User")))
				.andExpect(jsonPath("$.username", is("testUser")))
				.andExpect(jsonPath("$.status", is("ONLINE")))
				.andExpect(jsonPath("$.coins", is(1000)));
	}

	@Test
	public void createUser_duplicateUsername_returnsConflict() throws Exception {
		UserPostDTO dto = new UserPostDTO();
		dto.setDisplayName("Test");
		dto.setUsername("existing");

		given(userService.createUser(any()))
				.willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username not unique"));

		mockMvc.perform(post("/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(asJsonString(dto)))
				.andExpect(status().isBadRequest());
	}

	// ═══════════════════════════════════════════════
	// POST /login
	// ═══════════════════════════════════════════════

	@Test
	public void loginUser_validCredentials_returnsOk() throws Exception {
		User user = createMockUser();

		UserPostDTO dto = new UserPostDTO();
		dto.setUsername("testUser");
		dto.setPassword("TestPass1");

		given(userService.loginUser("testUser", "TestPass1")).willReturn(user);

		mockMvc.perform(post("/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(asJsonString(dto)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(1)))
				.andExpect(jsonPath("$.token", is("test-token-123")));
	}

	@Test
	public void loginUser_wrongPassword_returnsUnauthorized() throws Exception {
		UserPostDTO dto = new UserPostDTO();
		dto.setUsername("testUser");
		dto.setPassword("wrong");

		given(userService.loginUser("testUser", "wrong"))
				.willThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong password"));

		mockMvc.perform(post("/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(asJsonString(dto)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	public void loginUser_unknownUser_returnsUnauthorized() throws Exception {
		UserPostDTO dto = new UserPostDTO();
		dto.setUsername("nobody");
		dto.setPassword("pass");

		given(userService.loginUser("nobody", "pass"))
				.willThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

		mockMvc.perform(post("/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(asJsonString(dto)))
				.andExpect(status().isUnauthorized());
	}

	// ═══════════════════════════════════════════════
	// GET /users/{id}
	// ═══════════════════════════════════════════════

	@Test
	public void getUserById_validToken_returnsUser() throws Exception {
		User user = createMockUser();

		doNothing().when(userService).validateToken("test-token-123");
		given(userService.getUserById(1L)).willReturn(user);

		mockMvc.perform(get("/users/1")
				.header("Authorization", "test-token-123")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(1)))
				.andExpect(jsonPath("$.username", is("testUser")));
	}

	@Test
	public void getUserById_invalidToken_returnsUnauthorized() throws Exception {
		doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"))
				.when(userService).validateToken("bad-token");

		mockMvc.perform(get("/users/1")
				.header("Authorization", "bad-token")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isUnauthorized());
	}

	@Test
	public void getUserById_notFound_returns404() throws Exception {
		doNothing().when(userService).validateToken("test-token-123");
		given(userService.getUserById(999L))
				.willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

		mockMvc.perform(get("/users/999")
				.header("Authorization", "test-token-123")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	// ═══════════════════════════════════════════════
	// PATCH /users/{id}
	// ═══════════════════════════════════════════════

	@Test
	public void updateUser_validPatch_returnsUpdatedUser() throws Exception {
		User updated = createMockUser();
		updated.setDisplayName("Updated Name");

		doNothing().when(userService).validateToken("test-token-123");
		given(userService.updateUser(eq(1L), eq("test-token-123"), any())).willReturn(updated);

		UserPatchDTO dto = new UserPatchDTO();
		dto.setDisplayName("Updated Name");

		mockMvc.perform(patch("/users/1")
				.header("Authorization", "test-token-123")
				.contentType(MediaType.APPLICATION_JSON)
				.content(asJsonString(dto)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName", is("Updated Name")));
	}

	@Test
	public void updateUser_otherUser_returnsForbidden() throws Exception {
		doNothing().when(userService).validateToken("other-token");
		given(userService.updateUser(eq(1L), eq("other-token"), any()))
				.willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your profile"));

		UserPatchDTO dto = new UserPatchDTO();
		dto.setDisplayName("Hacked");

		mockMvc.perform(patch("/users/1")
				.header("Authorization", "other-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(asJsonString(dto)))
				.andExpect(status().isForbidden());
	}

	@Test
	public void updateUser_equipCosmetic_returnsUpdated() throws Exception {
		User updated = createMockUser();
		updated.setEquippedBorder("border-crimson");

		doNothing().when(userService).validateToken("test-token-123");
		given(userService.updateUser(eq(1L), eq("test-token-123"), any())).willReturn(updated);

		UserPatchDTO dto = new UserPatchDTO();
		dto.setEquippedBorder("border-crimson");

		mockMvc.perform(patch("/users/1")
				.header("Authorization", "test-token-123")
				.contentType(MediaType.APPLICATION_JSON)
				.content(asJsonString(dto)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.equippedBorder", is("border-crimson")));
	}

	// ═══════════════════════════════════════════════
	// POST /users/{id}/cosmetics/buy
	// ═══════════════════════════════════════════════

	@Test
	public void buyCosmetic_validPurchase_returnsUpdatedUser() throws Exception {
		User updated = createMockUser();
		updated.setCoins(700);
		updated.setOwnedCosmetics("border-crimson");

		doNothing().when(userService).validateToken("test-token-123");
		given(userService.buyCosmetic(eq(1L), eq("test-token-123"), eq("border-crimson")))
				.willReturn(updated);

		mockMvc.perform(post("/users/1/cosmetics/buy")
				.header("Authorization", "test-token-123")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cosmeticId\":\"border-crimson\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.coins", is(700)))
				.andExpect(jsonPath("$.ownedCosmetics", is("border-crimson")));
	}

	@Test
	public void buyCosmetic_notEnoughCoins_returnsBadRequest() throws Exception {
		doNothing().when(userService).validateToken("test-token-123");
		given(userService.buyCosmetic(eq(1L), eq("test-token-123"), eq("border-rainbow")))
				.willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough coins"));

		mockMvc.perform(post("/users/1/cosmetics/buy")
				.header("Authorization", "test-token-123")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cosmeticId\":\"border-rainbow\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	public void buyCosmetic_invalidToken_returnsUnauthorized() throws Exception {
		doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"))
				.when(userService).validateToken("bad-token");

		mockMvc.perform(post("/users/1/cosmetics/buy")
				.header("Authorization", "bad-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cosmeticId\":\"border-crimson\"}"))
				.andExpect(status().isUnauthorized());
	}

	// ═══════════════════════════════════════════════
	// PUT /logout
	// ═══════════════════════════════════════════════

	@Test
	public void logoutUser_validToken_returnsNoContent() throws Exception {
		doNothing().when(userService).logoutUser("test-token-123");

		mockMvc.perform(put("/logout")
				.header("Authorization", "test-token-123")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNoContent());
	}

	@Test
	public void logoutUser_invalidToken_returnsNotFound() throws Exception {
		doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"))
				.when(userService).logoutUser("bad-token");

		mockMvc.perform(put("/logout")
				.header("Authorization", "bad-token")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	// ═══════════════════════════════════════════════
	// Helper
	// ═══════════════════════════════════════════════

	private String asJsonString(final Object object) {
		try {
			return new ObjectMapper().writeValueAsString(object);
		} catch (JacksonException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					String.format("The request body could not be created.%s", e.toString()));
		}
	}
}