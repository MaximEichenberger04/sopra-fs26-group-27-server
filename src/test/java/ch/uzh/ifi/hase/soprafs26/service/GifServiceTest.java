package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.rest.dto.GifSearchResultDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GifServiceTest {

    // The inner Klipy* classes are private — reach them via reflection to
    // keep the production code free of test-only visibility relaxations.
    private static final String INNER_PKG = "ch.uzh.ifi.hase.soprafs26.service.GifService$";

    @Test
    public void search_apiKeyNull_returnsEmptyList() {
        GifService service = new GifService((String) null);

        List<GifSearchResultDTO> result = service.search("cats", 1, 20);

        assertTrue(result.isEmpty());
    }

    @Test
    public void search_apiKeyBlank_returnsEmptyList() {
        GifService service = new GifService("   ");

        assertTrue(service.search("cats", 1, 20).isEmpty());
    }

    @Test
    public void search_klipyReturnsNullResponse_returnsEmptyList() {
        GifService service = newServiceWithMockClient(mockClientReturning(null));

        assertTrue(service.search("cats", 1, 20).isEmpty());
    }

    @Test
    public void search_klipyReturnsNullData_returnsEmptyList() throws Exception {
        Object response = newInner("KlipyResponse");
        setField(response, "data", null);
        GifService service = newServiceWithMockClient(mockClientReturning(response));

        assertTrue(service.search("cats", 1, 20).isEmpty());
    }

    @Test
    public void search_klipyReturnsNullInnerData_returnsEmptyList() throws Exception {
        Object response = newInner("KlipyResponse");
        Object page = newInner("KlipyPage");
        setField(page, "data", null);
        setField(response, "data", page);
        GifService service = newServiceWithMockClient(mockClientReturning(response));

        assertTrue(service.search("cats", 1, 20).isEmpty());
    }

    @Test
    public void search_klipyReturnsGifs_mapsPreviewAndFullUrls() throws Exception {
        Object response = buildResponse(
                buildGif(1L, "sm.gif", "md.gif"),
                buildGif(2L, "sm2.gif", null)
        );
        GifService service = newServiceWithMockClient(mockClientReturning(response));

        List<GifSearchResultDTO> result = service.search("cats", 1, 20);

        assertEquals(2, result.size());
        assertEquals("1", result.get(0).getId());
        assertEquals("sm.gif", result.get(0).getPreviewUrl());
        assertEquals("md.gif", result.get(0).getGifUrl());
        // When md is missing, gifUrl falls back to sm
        assertEquals("2", result.get(1).getId());
        assertEquals("sm2.gif", result.get(1).getPreviewUrl());
        assertEquals("sm2.gif", result.get(1).getGifUrl());
    }

    @Test
    public void search_gifWithNullFile_mapsToNullUrls() throws Exception {
        Object gif = newInner("KlipyGif");
        setField(gif, "id", 99L);
        setField(gif, "file", null);
        Object response = buildResponse(gif);
        GifService service = newServiceWithMockClient(mockClientReturning(response));

        List<GifSearchResultDTO> result = service.search("cats", 1, 20);

        assertEquals(1, result.size());
        assertEquals("99", result.get(0).getId());
        assertNull(result.get(0).getPreviewUrl());
        assertNull(result.get(0).getGifUrl());
    }

    @Test
    public void search_restClientThrows_throwsBadGateway() {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        when(restClient.get().uri(anyString(), any(), any(), any())
                .retrieve()
                .body(any(Class.class))).thenThrow(new RestClientException("boom"));
        GifService service = newServiceWithMockClient(restClient);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.search("cats", 1, 20));
        assertEquals(502, ex.getStatusCode().value());
    }

    // ---------- Reflection helpers ----------
    private GifService newServiceWithMockClient(RestClient restClient) {
        GifService service = new GifService("test-key");
        ReflectionTestUtils.setField(service, "restClient", restClient);
        return service;
    }

    private RestClient mockClientReturning(Object body) {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        when(restClient.get().uri(anyString(), any(), any(), any())
                .retrieve()
                .body(any(Class.class))).thenReturn(body);
        return restClient;
    }

    private Object newInner(String simpleName) throws Exception {
        Class<?> cls = Class.forName(INNER_PKG + simpleName);
        Constructor<?> ctor = cls.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private Object buildResponse(Object... gifs) throws Exception {
        Object response = newInner("KlipyResponse");
        Object page = newInner("KlipyPage");
        setField(page, "data", List.of(gifs));
        setField(response, "data", page);
        return response;
    }

    private Object buildGif(long id, String smUrl, String mdUrl) throws Exception {
        Object gif = newInner("KlipyGif");
        setField(gif, "id", id);
        Map<String, Object> file = new HashMap<>();
        if (smUrl != null) file.put("sm", buildSizeVariant(smUrl));
        if (mdUrl != null) file.put("md", buildSizeVariant(mdUrl));
        setField(gif, "file", file);
        return gif;
    }

    private Object buildSizeVariant(String url) throws Exception {
        Object variant = newInner("KlipySizeVariant");
        Object format = newInner("KlipyFormat");
        setField(format, "url", url);
        setField(variant, "gif", format);
        return variant;
    }
}
