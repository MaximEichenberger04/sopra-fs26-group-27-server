package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.rest.dto.GifSearchResultDTO;
import ch.uzh.ifi.hase.soprafs26.service.GifService;
import ch.uzh.ifi.hase.soprafs26.service.UserService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/gifs")
public class GifController {

    private final GifService gifService;
    private final UserService userService;

    GifController(GifService gifService, UserService userService) {
        this.gifService  = gifService;
        this.userService = userService;
    }

    @GetMapping("/search")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public List<GifSearchResultDTO> search(
            @RequestParam(name = "q") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage,
            @RequestHeader("Authorization") String token) {

        userService.validateToken(token);
        return gifService.search(keyword, page, perPage);
    }
}
