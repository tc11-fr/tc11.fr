package fr.tc11;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class InstagramFallbackRefreshTest {

    private static final String DEFAULT_OUTPUT_FILE = "src/main/resources/instagram.json";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    InstagramPostsFetcher fetcher;

    @Test
    @EnabledIfSystemProperty(named = "tc11.test.instagram.refresh", matches = "true")
    void refreshFallbackFromPlaywrightLive() throws IOException {
        Path outputPath = Path.of(System.getProperty("tc11.instagram.output-file", DEFAULT_OUTPUT_FILE));
        Files.createDirectories(outputPath.getParent());

        List<String> livePosts = fetcher.fetchInstagramPostsViaHeadlessBrowser();

        assertNotNull(livePosts);
        assertFalse(livePosts.isEmpty(), "Expected at least one Instagram post from Playwright fetch");

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(livePosts) + "\n";
        Files.writeString(outputPath, json);

        System.out.println("INSTAGRAM_FALLBACK_PRIMARY=" + livePosts.getFirst());
        System.out.println("INSTAGRAM_FALLBACK_COUNT=" + livePosts.size());
        System.out.println("INSTAGRAM_FALLBACK_FILE=" + outputPath);
    }
}