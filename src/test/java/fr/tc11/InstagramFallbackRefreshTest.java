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

import static org.junit.jupiter.api.Assertions.fail;

@QuarkusTest
class InstagramFallbackRefreshTest {

    private static final String DEFAULT_OUTPUT_FILE = "src/main/resources/instagram.json";
    private static final int MAX_ATTEMPTS = 10;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    InstagramPostsFetcher fetcher;

    @Test
    @EnabledIfSystemProperty(named = "tc11.test.instagram.refresh", matches = "true")
    void refreshFallbackFromPlaywrightLive() throws IOException {
        Path outputPath = Path.of(System.getProperty("tc11.instagram.output-file", DEFAULT_OUTPUT_FILE));
        Files.createDirectories(outputPath.getParent());

        List<String> livePosts = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            System.out.println("INSTAGRAM_REFRESH_ATTEMPT=" + attempt);
            livePosts = fetcher.fetchInstagramPostsViaHeadlessBrowser();
            if (livePosts != null && !livePosts.isEmpty()) {
                break;
            }
            System.out.println("INSTAGRAM_REFRESH_ATTEMPT_FAILED=" + attempt);
        }

        if (livePosts == null || livePosts.isEmpty()) {
            fail("Expected at least one Instagram post from Playwright fetch after " + MAX_ATTEMPTS + " attempts");
        }

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(livePosts) + "\n";
        Files.writeString(outputPath, json);

        System.out.println("INSTAGRAM_FALLBACK_PRIMARY=" + livePosts.getFirst());
        System.out.println("INSTAGRAM_FALLBACK_COUNT=" + livePosts.size());
        System.out.println("INSTAGRAM_FALLBACK_FILE=" + outputPath);
    }

    @Test
    @EnabledIfSystemProperty(named = "tc11.test.instagram.api.refresh", matches = "true")
    void refreshFallbackFromApiLive() throws Exception {
        Path outputPath = Path.of(System.getProperty("tc11.instagram.output-file", DEFAULT_OUTPUT_FILE));
        Files.createDirectories(outputPath.getParent());

        List<String> livePosts = fetcher.testFetchInstagramPostsViaInstagramApi();

        if (livePosts == null || livePosts.isEmpty()) {
            fail("Expected at least one Instagram post from Instagram API fetch");
        }

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(livePosts) + "\n";
        Files.writeString(outputPath, json);

        System.out.println("INSTAGRAM_FALLBACK_PRIMARY=" + livePosts.getFirst());
        System.out.println("INSTAGRAM_FALLBACK_COUNT=" + livePosts.size());
        System.out.println("INSTAGRAM_FALLBACK_FILE=" + outputPath);
    }
}