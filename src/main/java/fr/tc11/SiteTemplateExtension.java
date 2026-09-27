package fr.tc11;

import io.quarkus.arc.Unremovable;
import io.quarkus.qute.TemplateExtension;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Singleton;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Qute template extension to expose site configuration to templates.
 *
 * Usage in templates: {site:url}
 */
@TemplateExtension(namespace = "site")
public class SiteTemplateExtension {

    /**
     * Returns the site base URL from configuration (e.g. https://tc11.fr).
     * Used to construct absolute URLs for Open Graph image meta tags.
     *
     * @return site base URL string
     */
    public static String url() {
        return CDI.current().select(SiteConfig.class).get().getUrl();
    }

    /**
     * Returns the current year (Europe/Paris) at site generation time.
     *
     * Usage in templates: {site:year}
     */
    public static int year() {
        return java.time.Year.now(java.time.ZoneId.of("Europe/Paris")).getValue();
    }

    /**
     * Fails the build if more than one post is marked with homepageHighlight: true.
     *
     * Usage in templates: {site:requireSingleHomepageHighlight(site.collections.posts)}
     */
    public static String requireSingleHomepageHighlight(Iterable<?> posts) {
        List<String> highlightedPosts = new ArrayList<>();
        if (posts != null) {
            for (Object post : posts) {
                if (isHomepageHighlight(post)) {
                    highlightedPosts.add(describePost(post));
                }
            }
        }

        if (highlightedPosts.size() > 1) {
            throw new IllegalStateException(
                    "Multiple posts have homepageHighlight: true: " + String.join(", ", highlightedPosts)
                            + ". Keep homepageHighlight: true on only one post and remove it from the others.");
        }

        return "";
    }

    private static boolean isHomepageHighlight(Object post) {
        Object data = call(post, "data");
        if (data == null) {
            return false;
        }
        if (data instanceof Map<?, ?> map) {
            return Boolean.TRUE.equals(map.get("homepageHighlight"));
        }

        Object value = call(data, "getBoolean", "homepageHighlight");
        if (value == null) {
            value = call(data, "getValue", "homepageHighlight");
        }
        if (value == null) {
            value = call(data, "get", "homepageHighlight");
        }

        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static String describePost(Object post) {
        Object title = call(post, "title");
        if (title == null) {
            title = call(post, "getTitle");
        }

        Object url = call(post, "url");
        Object absoluteUrl = url != null ? call(url, "absolute") : null;
        if (absoluteUrl == null && url != null) {
            absoluteUrl = call(url, "getAbsolute");
        }

        if (title != null && absoluteUrl != null) {
            return "\"" + title + "\" (" + absoluteUrl + ")";
        }
        if (title != null) {
            return "\"" + title + "\"";
        }
        if (absoluteUrl != null) {
            return String.valueOf(absoluteUrl);
        }
        return "<unknown post>";
    }

    private static Object call(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = findMethod(target.getClass(), methodName, args.length);
            if (method == null) {
                return null;
            }
            return method.invoke(target, args);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String methodName, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        return null;
    }

    @Singleton
    @Unremovable
    public static class SiteConfig {
        @ConfigProperty(name = "site.url")
        String url;

        public String getUrl() {
            return url;
        }
    }
}
