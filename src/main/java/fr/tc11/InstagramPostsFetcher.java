package fr.tc11;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to fetch Instagram posts during site generation.
 * 
 * Posts are kept in memory and exposed via {@link InstagramTemplateExtension}
 * to Qute templates, allowing instagram.json to be generated dynamically.
 * 
 * Uses the following fallback chain:
 * 1. Headless browser scraping via Playwright
 * 2. Instagram Graph API (if credentials configured)
 * 3. RSS Bridge (no authentication required, simple HTTP request)
 * 4. Existing instagram.json file from classpath (if all else fails)
 * 
 * @see <a href="https://rss-bridge.org/">RSS Bridge</a>
 * @see <a href="https://developers.facebook.com/docs/instagram-api/">Instagram Graph API Documentation</a>
 */
@Startup
@ApplicationScoped
public class InstagramPostsFetcher {

    private static final Logger LOG = Logger.getLogger(InstagramPostsFetcher.class);
    
    // RSS Bridge URL for fetching Instagram posts without authentication
    private static final String RSS_BRIDGE_URL = "https://rss-bridge.org/bridge01/?action=display&context=Username&u=%s&bridge=InstagramBridge&format=Json";
    
    // Instagram API endpoint (only requires access token)
    private static final String INSTAGRAM_API_BASE = "https://graph.instagram.com";
    
    // Instagram Graph API endpoints (requires both access token and account ID)
    private static final String GRAPH_API_BASE = "https://graph.facebook.com/v21.0";
    private static final String MEDIA_FIELDS = "id,caption,media_type,media_url,permalink,thumbnail_url,timestamp";
    
    // Instagram profile URL for headless browser scraping
    private static final String INSTAGRAM_PROFILE_URL = "https://www.instagram.com/%s/";
    
    // Pattern to find post shortcodes in the page
    private static final Pattern POST_LINK_PATTERN = Pattern.compile("/p/([A-Za-z0-9_-]+)");
    private static final Pattern REEL_LINK_PATTERN = Pattern.compile("/reel/([A-Za-z0-9_-]+)");
    private static final Pattern SHORTCODE_JSON_PATTERN = Pattern.compile("\\\"shortcode\\\":\\\"([A-Za-z0-9_-]+)\\\"");
    
    private static final int MAX_POSTS = 6;
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final int REQUEST_TIMEOUT_SECONDS = 30;
    private static final int BROWSER_TIMEOUT_MS = 30000;
    private static final int BROWSER_CONTENT_LOAD_WAIT_MS = 2000;
    private static final int BROWSER_FETCH_ATTEMPTS = 2;
    private static final DateTimeFormatter DEBUG_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    
    // Classpath resource path for fallback instagram.json
    private static final String FALLBACK_RESOURCE_PATH = "/instagram.json";

    @ConfigProperty(name = "tc11.instagram.enabled", defaultValue = "true")
    boolean enabled;
    
    @ConfigProperty(name = "tc11.instagram.username", defaultValue = "tc11assb")
    String instagramUsername;
    
    // Access token from environment variable (recommended) or application.properties
    @ConfigProperty(name = "tc11.instagram.access-token")
    Optional<String> accessToken;
    
    // Instagram Business Account ID (required for Graph API)
    @ConfigProperty(name = "tc11.instagram.account-id")
    Optional<String> accountId;
    
    // Comma-separated list of Instagram post shortcodes or URLs to exclude from the gallery
    @ConfigProperty(name = "tc11.instagram.blacklist")
    Optional<String> blacklist;

    @ConfigProperty(name = "tc11.instagram.debug.enabled", defaultValue = "false")
    boolean debugEnabled;

    @ConfigProperty(name = "tc11.instagram.debug.output-dir", defaultValue = "target/instagram-debug")
    String debugOutputDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    
    // In-memory storage for fetched posts (initialized once in @PostConstruct)
    private List<String> instagramPosts = Collections.emptyList();

    public InstagramPostsFetcher() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @PostConstruct
    void init() {
        if (!enabled) {
            LOG.info("Instagram posts fetcher is disabled");
            // Load fallback posts even when disabled so the REST endpoint always returns data
            instagramPosts = readFallbackPosts();
            return;
        }

        List<String> fallbackPosts = readFallbackPosts();
        List<String> fetchedUrls = null;
        
        // Try Instagram API first (preferred - only requires access token)
        if (hasInstagramApiToken()) {
            LOG.info("Fetching Instagram posts via Instagram API...");
            try {
                fetchedUrls = fetchInstagramPostsViaInstagramApi();
                if (!fetchedUrls.isEmpty()) {
                    instagramPosts = Collections.unmodifiableList(new ArrayList<>(fetchedUrls));
                    LOG.infof("Successfully fetched %d Instagram posts via Instagram API", fetchedUrls.size());
                    return;
                }
            } catch (Exception e) {
                LOG.warnf("Instagram API failed: %s. Trying other methods...", e.getMessage());
            }
        } else {
            LOG.info("Instagram API token not configured. Trying headless browser...");
        }

        // Try headless browser scraping
        LOG.info("Fetching Instagram posts via headless browser...");
        try {
            fetchedUrls = fetchInstagramPostsViaHeadlessBrowser();
            if (!fetchedUrls.isEmpty()) {
                instagramPosts = Collections.unmodifiableList(new ArrayList<>(fetchedUrls));
                LOG.infof("Successfully fetched %d Instagram posts via headless browser", fetchedUrls.size());
                return;
            }
        } catch (Exception e) {
            LOG.warnf("Headless browser scraping failed: %s. Trying other methods...", e.getMessage());
        }

        // Try legacy Graph API if both credentials are configured
        if (hasGraphApiCredentials()) {
            LOG.info("Fetching Instagram posts via Graph API");
            try {
                fetchedUrls = fetchInstagramPostsViaGraphApi();
                if (!fetchedUrls.isEmpty()) {
                    instagramPosts = Collections.unmodifiableList(new ArrayList<>(fetchedUrls));
                    LOG.infof("Successfully fetched %d Instagram posts via Graph API", fetchedUrls.size());
                    return;
                }
            } catch (Exception e) {
                LOG.warnf("Graph API failed: %s. Trying RSS Bridge...", e.getMessage());
            }
        }
        
        // Fallback to RSS Bridge
        LOG.info("Fetching Instagram posts via RSS Bridge...");
        try {
            fetchedUrls = fetchInstagramPostsViaRssBridge();
            if (!fetchedUrls.isEmpty()) {
                instagramPosts = Collections.unmodifiableList(new ArrayList<>(fetchedUrls));
                LOG.infof("Successfully fetched %d Instagram posts via RSS Bridge", fetchedUrls.size());
                return;
            }
        } catch (Exception e) {
            LOG.warnf("RSS Bridge failed: %s", e.getMessage());
        }
        
        // Final fallback to existing instagram.json from classpath
        if (!fallbackPosts.isEmpty()) {
            instagramPosts = Collections.unmodifiableList(new ArrayList<>(fallbackPosts));
            LOG.infof("Using %d fallback posts from instagram.json", fallbackPosts.size());
        } else {
            LOG.warn("No Instagram posts available - instagram.json will be empty");
            instagramPosts = Collections.emptyList();
        }
    }
    
    /**
     * Returns the list of Instagram post URLs.
     * This is used by the Qute template extension to expose posts to templates.
     * 
     * @return unmodifiable list of Instagram post URLs (with blacklisted posts filtered out)
     */
    public List<String> getInstagramPosts() {
        return filterBlacklistedPosts(instagramPosts);
    }

    /**
     * Filters out blacklisted posts from the given list.
     * Blacklist can contain either shortcodes (e.g., "DKurQ_ktdgw") or full URLs.
     * 
     * @param posts the list of Instagram post URLs to filter
     * @return filtered list with blacklisted posts removed
     */
    private List<String> filterBlacklistedPosts(List<String> posts) {
        if (blacklist.isEmpty() || blacklist.get().isBlank()) {
            return posts;
        }
        
        // Parse blacklist entries (can be shortcodes or full URLs)
        String[] blacklistEntries = blacklist.get().split(",");
        Set<String> blacklistedShortcodes = new LinkedHashSet<>();
        
        for (String entry : blacklistEntries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;
            
            // If it's a full URL, extract the shortcode
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                // Extract shortcode from URLs like https://www.instagram.com/p/DKurQ_ktdgw or https://www.instagram.com/p/DKurQ_ktdgw/
                Matcher matcher = POST_LINK_PATTERN.matcher(trimmed);
                if (matcher.find()) {
                    blacklistedShortcodes.add(matcher.group(1));
                }
            } else {
                // It's already a shortcode
                blacklistedShortcodes.add(trimmed);
            }
        }
        
        if (blacklistedShortcodes.isEmpty()) {
            return posts;
        }
        
        // Filter out blacklisted posts
        List<String> filtered = new ArrayList<>();
        for (String post : posts) {
            boolean isBlacklisted = false;
            for (String shortcode : blacklistedShortcodes) {
                if (isPostBlacklisted(post, shortcode)) {
                    isBlacklisted = true;
                    LOG.debugf("Filtering out blacklisted post: %s (shortcode: %s)", post, shortcode);
                    break;
                }
            }
            if (!isBlacklisted) {
                filtered.add(post);
            }
        }
        
        if (filtered.size() < posts.size()) {
            LOG.infof("Filtered %d blacklisted posts, %d posts remaining", posts.size() - filtered.size(), filtered.size());
        }
        
        return Collections.unmodifiableList(filtered);
    }
    
    /**
     * Checks if a post URL matches a blacklisted shortcode.
     * Uses precise matching with URL delimiters to avoid false positives.
     * 
     * @param postUrl the Instagram post URL to check
     * @param shortcode the blacklisted shortcode to match against
     * @return true if the post should be blacklisted
     */
    private boolean isPostBlacklisted(String postUrl, String shortcode) {
        // Instagram URLs are in the format: https://www.instagram.com/p/SHORTCODE or https://www.instagram.com/p/SHORTCODE/
        // Match with proper delimiters to avoid false positives
        return postUrl.contains("/p/" + shortcode) || 
               postUrl.contains("/reel/" + shortcode) || 
               postUrl.endsWith("/" + shortcode) || 
               postUrl.endsWith("/" + shortcode + "/");
    }

    /**
     * Checks if an Instagram API token is configured.
     * The Instagram API (graph.instagram.com) only requires an access token.
     */
    private boolean hasInstagramApiToken() {
        return accessToken.isPresent() && !accessToken.get().isBlank();
    }

    /**
     * Checks if Graph API credentials are configured.
     */
    private boolean hasGraphApiCredentials() {
        return accessToken.isPresent() && !accessToken.get().isBlank() 
                && accountId.isPresent() && !accountId.get().isBlank();
    }

    /**
     * Fetches Instagram posts using the Instagram API (graph.instagram.com).
     * Only requires a valid access token — no account ID needed.
     * This is the preferred method when a token is available.
     *
     * @see <a href="https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login/get-started">
     *      Instagram API with Instagram Login</a>
     */
    List<String> fetchInstagramPostsViaInstagramApi() throws IOException, InterruptedException {
        String token = accessToken.orElseThrow(() -> new IllegalStateException("Access token not configured"));

        String apiUrl = String.format("%s/me/media?fields=%s&limit=%d&access_token=%s",
                INSTAGRAM_API_BASE,
                MEDIA_FIELDS,
                MAX_POSTS,
                URLEncoder.encode(token, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            String errorMessage = parseGraphApiError(response.body());
            throw new IOException("Instagram API returned status " + response.statusCode() + ": " + errorMessage);
        }

        return parseMediaResponse(response.body());
    }

    /**
     * Fetches Instagram posts using RSS Bridge.
     * This is the simplest method - no authentication required, just a simple HTTP request.
     * Uses rss-bridge.org to get Instagram feed as JSON.
     */
    List<String> fetchInstagramPostsViaRssBridge() throws IOException, InterruptedException {
        String rssBridgeUrl = String.format(RSS_BRIDGE_URL, URLEncoder.encode(instagramUsername, StandardCharsets.UTF_8));
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(rssBridgeUrl))
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (compatible; TC11SiteBot/1.0)")
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("RSS Bridge returned status " + response.statusCode());
        }

        return parseRssBridgeResponse(response.body());
    }

    /**
     * Parses the RSS Bridge JSON response and extracts post URLs.
     * The response follows the JSON Feed format with items containing 'url' or 'id' fields.
     */
    List<String> parseRssBridgeResponse(String jsonResponse) {
        List<String> postUrls = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode items = root.path("items");
            
            if (items.isArray()) {
                for (JsonNode item : items) {
                    if (postUrls.size() >= MAX_POSTS) break;
                    
                    // Try 'url' field first, then 'id'
                    String url = item.path("url").asText();
                    if (url == null || url.isEmpty()) {
                        url = item.path("id").asText();
                    }
                    
                    if (url != null && !url.isEmpty() && url.contains("instagram.com/p/")) {
                        postUrls.add(url);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warnf("Failed to parse RSS Bridge response: %s", e.getMessage());
        }
        
        return postUrls;
    }

    /**
     * Fetches Instagram posts using the Graph API.
     * Requires a valid access token and Instagram Business Account ID.
     */
    List<String> fetchInstagramPostsViaGraphApi() throws IOException, InterruptedException {
        String token = accessToken.orElseThrow(() -> new IllegalStateException("Access token not configured"));
        String igAccountId = accountId.orElseThrow(() -> new IllegalStateException("Account ID not configured"));
        
        // Build the API URL to fetch recent media
        String apiUrl = String.format("%s/%s/media?fields=%s&limit=%d&access_token=%s",
                GRAPH_API_BASE,
                URLEncoder.encode(igAccountId, StandardCharsets.UTF_8),
                MEDIA_FIELDS,
                MAX_POSTS,
                URLEncoder.encode(token, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            String errorMessage = parseGraphApiError(response.body());
            throw new IOException("Graph API returned status " + response.statusCode() + ": " + errorMessage);
        }

        return parseMediaResponse(response.body());
    }

    /**
     * Fetches Instagram posts using a headless browser (Playwright).
     * Navigates to the public Instagram profile page anonymously and extracts
     * post links from the rendered DOM via JavaScript.
     */
    List<String> fetchInstagramPostsViaHeadlessBrowser() {
        LOG.infof("Starting headless browser to scrape @%s", instagramUsername);
        
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setTimeout(BROWSER_TIMEOUT_MS)
                    .setArgs(List.of(
                        "--no-sandbox",
                        "--disable-setuid-sandbox",
                        "--disable-blink-features=AutomationControlled",
                        "--disable-dev-shm-usage"
                    ));
            
            try (Browser browser = playwright.chromium().launch(launchOptions)) {
                BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .setViewportSize(1280, 900)
                        .setLocale("fr-FR")
                        .setTimezoneId("Europe/Paris"));

                // Hide the webdriver flag that headless browsers expose
                context.addInitScript("Object.defineProperty(navigator, 'webdriver', { get: () => undefined })");

                Page page = context.newPage();

                String profileUrl = String.format(INSTAGRAM_PROFILE_URL, instagramUsername);
                String profileUrlWithLang = profileUrl + "?hl=en";

                for (int attempt = 1; attempt <= BROWSER_FETCH_ATTEMPTS; attempt++) {
                    String targetUrl = attempt == 1 ? profileUrl : profileUrlWithLang;
                    LOG.debugf("Navigating to %s (attempt %d/%d)", targetUrl, attempt, BROWSER_FETCH_ATTEMPTS);

                    page.navigate(targetUrl, new Page.NavigateOptions()
                            .setTimeout(BROWSER_TIMEOUT_MS)
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

                    try {
                        page.waitForSelector("a[href*='/p/'], a[href*='/reel/']",
                                new Page.WaitForSelectorOptions().setTimeout(BROWSER_TIMEOUT_MS));
                    } catch (Exception e) {
                        LOG.warnf("Post link selector timed out on attempt %d/%d", attempt, BROWSER_FETCH_ATTEMPTS);
                    }

                    page.waitForTimeout(BROWSER_CONTENT_LOAD_WAIT_MS);
                    page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                    page.waitForTimeout(BROWSER_CONTENT_LOAD_WAIT_MS);

                    writeDebugArtifacts(page, attempt);

                    List<String> postUrls = extractPostUrlsFromPage(page);
                    if (!postUrls.isEmpty()) {
                        return postUrls;
                    }
                }

                return List.of();
            }
        } catch (Exception e) {
            LOG.warnf("Headless browser error: %s", e.getMessage());
            throw new RuntimeException("Failed to scrape Instagram via headless browser", e);
        }
    }

    private List<String> extractPostUrlsFromPage(Page page) {
        Object result = page.evaluate(
                "() => Array.from(document.querySelectorAll('a[href]'))" +
                        ".map(a => a.getAttribute('href'))" +
                        ".filter(h => h && (h.includes('/p/') || h.includes('/reel/')))"
        );

        List<String> hrefs = new ArrayList<>();
        if (result instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    hrefs.add(s);
                }
            }
        }

        List<String> fromDom = extractPostUrlsFromLinks(hrefs);
        if (!fromDom.isEmpty()) {
            return fromDom;
        }

        try {
            String html = page.content();
            List<String> fromHtml = extractPostUrlsFromHtml(html);
            if (debugEnabled) {
                LOG.infof("Instagram debug parse counts - DOM: %d, HTML: %d", fromDom.size(), fromHtml.size());
            }
            return fromHtml;
        } catch (Exception e) {
            LOG.debugf("Failed to read page content for HTML fallback: %s", e.getMessage());
            return List.of();
        }
    }

    private void writeDebugArtifacts(Page page, int attempt) {
        if (!debugEnabled) {
            return;
        }
        try {
            Path baseDir = Path.of(debugOutputDir,
                    LocalDateTime.now().format(DEBUG_TIMESTAMP_FORMATTER) + "-attempt-" + attempt);
            Files.createDirectories(baseDir);

            Path screenshotPath = baseDir.resolve("page.png");
            page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(true));

            String html = page.content();
            Files.writeString(baseDir.resolve("page.html"), html, StandardCharsets.UTF_8);

            Object result = page.evaluate(
                    "() => Array.from(document.querySelectorAll('a[href]'))" +
                            ".map(a => a.getAttribute('href'))" +
                            ".filter(h => h && (h.includes('/p/') || h.includes('/reel/')))"
            );

            List<String> hrefs = new ArrayList<>();
            if (result instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String s) {
                        hrefs.add(s);
                    }
                }
            }

            List<String> domParsed = extractPostUrlsFromLinks(hrefs);
            List<String> htmlParsed = extractPostUrlsFromHtml(html);

            List<String> report = new ArrayList<>();
            report.add("url=" + page.url());
            report.add("title=" + page.title());
            report.add("dom_href_count=" + hrefs.size());
            report.add("dom_parsed_count=" + domParsed.size());
            report.add("html_parsed_count=" + htmlParsed.size());
            report.add("dom_hrefs=");
            report.addAll(hrefs);
            report.add("dom_parsed_urls=");
            report.addAll(domParsed);
            report.add("html_parsed_urls=");
            report.addAll(htmlParsed);

            Files.write(baseDir.resolve("analysis.txt"), report, StandardCharsets.UTF_8);
            LOG.infof("Instagram debug artifacts written to %s", baseDir);
        } catch (Exception e) {
            LOG.warnf("Failed to write Instagram debug artifacts: %s", e.getMessage());
        }
    }

    /**
     * Extracts unique Instagram post URLs from a list of href attribute values.
     * Accepts both /p/ and /reel/ paths and normalises everything to /p/ for embed compatibility.
     */
    List<String> extractPostUrlsFromLinks(List<String> hrefs) {
        Set<String> shortcodes = new LinkedHashSet<>();

        for (String href : hrefs) {
            Matcher postMatcher = POST_LINK_PATTERN.matcher(href);
            if (postMatcher.find()) {
                shortcodes.add(postMatcher.group(1));
                continue;
            }
            Matcher reelMatcher = REEL_LINK_PATTERN.matcher(href);
            if (reelMatcher.find()) {
                shortcodes.add(reelMatcher.group(1));
            }
        }

        List<String> postUrls = new ArrayList<>();
        for (String shortcode : shortcodes) {
            if (postUrls.size() >= MAX_POSTS) break;
            postUrls.add("https://www.instagram.com/p/" + shortcode);
        }

        LOG.debugf("Extracted %d posts from DOM links", postUrls.size());
        return postUrls;
    }

    /**
     * Extracts Instagram post URLs from the rendered HTML page.
     * Uses /p/ URL format for all content types as it works for embedding both posts and reels.
     */
    List<String> extractPostUrlsFromHtml(String html) {
        Set<String> shortcodes = new LinkedHashSet<>();
        
        // Extract from post links (/p/)
        Matcher postMatcher = POST_LINK_PATTERN.matcher(html);
        while (postMatcher.find()) {
            String shortcode = postMatcher.group(1);
            if (shortcode.length() >= 10 && shortcode.length() <= 12) {
                shortcodes.add(shortcode);
            }
        }
        
        // Extract from reel links (/reel/) - we use /p/ format as it works for embedding both types
        Matcher reelMatcher = REEL_LINK_PATTERN.matcher(html);
        while (reelMatcher.find()) {
            String shortcode = reelMatcher.group(1);
            if (shortcode.length() >= 10 && shortcode.length() <= 12) {
                shortcodes.add(shortcode);
            }
        }

        // Extract from JSON payloads embedded in script tags (fallback when links are not in anchors)
        Matcher jsonMatcher = SHORTCODE_JSON_PATTERN.matcher(html);
        while (jsonMatcher.find()) {
            String shortcode = jsonMatcher.group(1);
            if (shortcode.length() >= 8 && shortcode.length() <= 20) {
                shortcodes.add(shortcode);
            }
        }
        
        // Convert shortcodes to full URLs using /p/ format (works for embedding both posts and reels)
        List<String> postUrls = new ArrayList<>();
        for (String shortcode : shortcodes) {
            if (postUrls.size() >= MAX_POSTS) break;
            postUrls.add("https://www.instagram.com/p/" + shortcode);
        }
        
        LOG.debugf("Extracted %d posts from HTML", postUrls.size());
        return postUrls;
    }

    /**
     * Parses the Graph API media response and extracts post permalinks.
     */
    List<String> parseMediaResponse(String jsonResponse) {
        List<String> postUrls = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode data = root.path("data");
            
            if (data.isArray()) {
                for (JsonNode media : data) {
                    String permalink = media.path("permalink").asText();
                    if (permalink != null && !permalink.isEmpty()) {
                        postUrls.add(permalink);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warnf("Failed to parse Graph API response: %s", e.getMessage());
        }
        
        return postUrls;
    }

    /**
     * Parses error message from Graph API error response.
     */
    String parseGraphApiError(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode error = root.path("error");
            if (!error.isMissingNode()) {
                String message = error.path("message").asText("Unknown error");
                String type = error.path("type").asText("");
                int code = error.path("code").asInt(0);
                return String.format("%s (type: %s, code: %d)", message, type, code);
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return jsonResponse;
    }

    /**
     * Reads fallback posts from instagram.json in the classpath.
     * This is used when all fetching methods fail.
     */
    List<String> readFallbackPosts() {
        try (InputStream is = getClass().getResourceAsStream(FALLBACK_RESOURCE_PATH)) {
            if (is != null) {
                return objectMapper.readValue(is, new TypeReference<List<String>>() {});
            }
        } catch (IOException e) {
            LOG.debugf("Failed to read fallback instagram.json from classpath: %s", e.getMessage());
        }
        return List.of();
    }
    
    // Package-private methods for testing
    
    /**
     * For testing: parse media response.
     */
    List<String> testParseMediaResponse(String jsonResponse) {
        return parseMediaResponse(jsonResponse);
    }
    
    /**
     * For testing: fetch via Instagram API.
     */
    List<String> testFetchInstagramPostsViaInstagramApi() throws IOException, InterruptedException {
        return fetchInstagramPostsViaInstagramApi();
    }
    
    /**
     * For testing: extract post URLs from HTML.
     */
    List<String> testExtractPostUrlsFromHtml(String html) {
        return extractPostUrlsFromHtml(html);
    }

    /**
     * For testing: extract post URLs from a list of href values.
     */
    List<String> testExtractPostUrlsFromLinks(List<String> hrefs) {
        return extractPostUrlsFromLinks(hrefs);
    }
    
    /**
     * For testing: parse RSS Bridge response.
     */
    List<String> testParseRssBridgeResponse(String jsonResponse) {
        return parseRssBridgeResponse(jsonResponse);
    }
}
