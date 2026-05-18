package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.rest.dto.GifSearchResultDTO;
import ch.uzh.ifi.hase.soprafs26.service.GifService;
import ch.uzh.ifi.hase.soprafs26.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GifController.class)
public class GifControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GifService gifService;

    @MockitoBean
    private UserService userService;

    @Test
    public void search_validQuery_returnsList() throws Exception {
        GifSearchResultDTO a = new GifSearchResultDTO();
        a.setId("1");
        a.setPreviewUrl("p1");
        a.setGifUrl("g1");
        GifSearchResultDTO b = new GifSearchResultDTO();
        b.setId("2");
        b.setPreviewUrl("p2");
        b.setGifUrl("g2");
        given(gifService.search(eq("cats"), eq(1), eq(20))).willReturn(List.of(a, b));

        mockMvc.perform(get("/gifs/search")
                        .param("q", "cats")
                        .header("Authorization", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is("1")))
                .andExpect(jsonPath("$[0].previewUrl", is("p1")))
                .andExpect(jsonPath("$[0].gifUrl", is("g1")));

        verify(userService).validateToken("valid-token");
    }

    @Test
    public void search_withCustomPagination_passesThroughToService() throws Exception {
        given(gifService.search(eq("dogs"), eq(3), eq(50))).willReturn(Collections.emptyList());

        mockMvc.perform(get("/gifs/search")
                        .param("q", "dogs")
                        .param("page", "3")
                        .param("per_page", "50")
                        .header("Authorization", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(gifService).search("dogs", 3, 50);
    }

    @Test
    public void search_missingQuery_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/gifs/search")
                        .header("Authorization", "valid-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void search_emptyResult_returnsEmptyArray() throws Exception {
        given(gifService.search(eq("xyz"), anyInt(), anyInt())).willReturn(Collections.emptyList());

        mockMvc.perform(get("/gifs/search")
                        .param("q", "xyz")
                        .header("Authorization", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void search_invalidToken_returnsUnauthorized() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .when(userService).validateToken("bad");

        mockMvc.perform(get("/gifs/search")
                        .param("q", "cats")
                        .header("Authorization", "bad"))
                .andExpect(status().isUnauthorized());
    }
}
