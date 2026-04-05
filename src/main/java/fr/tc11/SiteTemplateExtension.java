package fr.tc11;

import io.quarkus.arc.Unremovable;
import io.quarkus.qute.TemplateExtension;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Singleton;

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
