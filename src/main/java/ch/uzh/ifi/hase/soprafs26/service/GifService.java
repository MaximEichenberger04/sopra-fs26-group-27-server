package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.rest.dto.GifSearchResultDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GifService {

    private static final String KLIPY_BASE = "https://api.klipy.com/api/v1";

    private final RestClient restClient;
    private final String apiKey;

    public GifService(@Value("${klipy.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.create();
    }

    public List<GifSearchResultDTO> search(String keyword, int page, int perPage) {
        if (apiKey == null || apiKey.isBlank()) return Collections.emptyList();
        try {
            String uri = KLIPY_BASE + "/" + apiKey + "/gifs/search"
                    + "?q={keyword}&page={page}&per_page={perPage}";
            KlipyResponse response = restClient.get()
                    .uri(uri, keyword, page, perPage)
                    .retrieve()
                    .body(KlipyResponse.class); // takes the response body (raw JSON) and deserializes it into a KlipyResponse object
            if (response == null || response.data == null || response.data.data == null) {
                return Collections.emptyList();
            }
            // map each Klipy GIF to a flat DTO and return as list
            return response.data.data.stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GIF search unavailable");
        }
    }

    //Converts one KlipyGif (the raw Klipy data) into a flat GifSearchResultDTO (what the frontend receives):
    private GifSearchResultDTO toDTO(KlipyGif gif) {
        GifSearchResultDTO dto = new GifSearchResultDTO();
        dto.setId(String.valueOf(gif.id)); // Sets the id. 
        KlipySizeVariant sm = gif.file != null ? gif.file.get("sm") : null; // Looks up the sm and md size variants from the file map 
        KlipySizeVariant md = gif.file != null ? gif.file.get("md") : null;  // sm = small, md = medium
        String smUrl = (sm != null && sm.gif != null) ? sm.gif.url : null; // Extracts the URL string from each variant (again null-safe)
        String mdUrl = (md != null && md.gif != null) ? md.gif.url : null;
        dto.setPreviewUrl(smUrl);
        dto.setGifUrl(mdUrl != null ? mdUrl : smUrl);
        return dto;
    }

    // Classes for deserializing the Klipy response. Klipy returns a lot of fields
    // we don't need, so @JsonIgnoreProperties(ignoreUnknown = true) keeps things clean.

    // Structure overview:
    // KlipyResponse -> data (KlipyPage)
    //   -> data (List<KlipyGif>)
    //     -> each gif has: id, file (Map<String, KlipySizeVariant>)
    //       -> file holds size keys like "sm" or "md"
    //         -> each KlipySizeVariant has a gif (KlipyFormat)
    //           -> KlipyFormat exposes the url (String)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class KlipyResponse {
        public KlipyPage data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class KlipyPage {
        public List<KlipyGif> data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class KlipyGif {
        public long id;
        public Map<String, KlipySizeVariant> file;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class KlipySizeVariant {
        public KlipyFormat gif;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class KlipyFormat {
        public String url;
    }
}
