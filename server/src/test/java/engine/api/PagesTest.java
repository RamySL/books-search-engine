package engine.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PagesTest {

    /** Pages.next lit la requête en cours : on en simule une, sans serveur. */
    private static void currentRequest(String uri, String query) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setQueryString(query);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void offsetOfFirstPagesIsZeroThenTwenty() {
        assertEquals(0, Pages.offset(1));
        assertEquals(20, Pages.offset(2));
    }

    @Test
    void nextKeepsTheOtherParametersAndIsRelative() {
        currentRequest("/api/search", "q=babylon&sort=pagerank&page=1");
        assertEquals("/api/search?q=babylon&sort=pagerank&page=2", Pages.next(1, 45));
    }

    @Test
    void nextAddsPageWhenItIsMissing() {
        currentRequest("/api/books", null);
        assertEquals("/api/books?page=2", Pages.next(1, 21));
    }

    @Test
    void noNextOnTheLastPage() {
        currentRequest("/api/books", "page=3");
        assertNull(Pages.next(3, 45));   // 45 livres : pages 1 à 3, la 3e contient les 5 derniers
        assertNull(Pages.next(1, 20));   // exactement une page pleine
    }
}
