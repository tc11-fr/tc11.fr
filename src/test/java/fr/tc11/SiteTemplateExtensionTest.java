package fr.tc11;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteTemplateExtensionTest {

    @Test
    void allowsZeroOrOneHomepageHighlight() {
        List<FakePost> posts = List.of(
                new FakePost("Post 1", "https://tc11.fr/posts/post-1/", false),
                new FakePost("Post 2", "https://tc11.fr/posts/post-2/", true),
                new FakePost("Post 3", "https://tc11.fr/posts/post-3/", false));

        String result = assertDoesNotThrow(() -> SiteTemplateExtension.requireSingleHomepageHighlight(posts));

        assertEquals("", result);
    }

    @Test
    void failsWhenSeveralHomepageHighlightsAreDefined() {
        List<FakePost> posts = List.of(
                new FakePost("Stages Toussaint", "https://tc11.fr/posts/stages-jeunes-toussaint-tc-11/", true),
                new FakePost("Fête du club", "https://tc11.fr/posts/f-te-du-club-dimanche-27-septembre/", true));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SiteTemplateExtension.requireSingleHomepageHighlight(posts));

        assertTrue(error.getMessage().contains("Stages Toussaint"));
        assertTrue(error.getMessage().contains("Fête du club"));
        assertTrue(error.getMessage().contains("Keep homepageHighlight: true on only one post"));
    }

    static final class FakePost {
        private final String title;
        private final FakeUrl url;
        private final Map<String, Object> data;

        FakePost(String title, String absoluteUrl, boolean homepageHighlight) {
            this.title = title;
            this.url = new FakeUrl(absoluteUrl);
            this.data = Map.of("homepageHighlight", homepageHighlight);
        }

        public String title() {
            return title;
        }

        public FakeUrl url() {
            return url;
        }

        public Map<String, Object> data() {
            return data;
        }
    }

    static final class FakeUrl {
        private final String absolute;

        FakeUrl(String absolute) {
            this.absolute = absolute;
        }

        public String absolute() {
            return absolute;
        }
    }
}
