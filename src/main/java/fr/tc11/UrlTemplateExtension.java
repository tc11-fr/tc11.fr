package fr.tc11;

import io.quarkus.qute.TemplateExtension;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@TemplateExtension(namespace = "url")
public class UrlTemplateExtension {

    /**
     * URL-encode a string for safe use as a query parameter value.
     * Usage in Qute templates: {url:encode(page.title)}
     */
    public static String encode(String value) {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
