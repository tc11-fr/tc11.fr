package fr.tc11; // adapte le package


import io.quarkus.qute.TemplateExtension;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@TemplateExtension(namespace = "files") // utilisation: {files:images(page)}
public class FilesViewHelpers {

    private static final Pattern ATTACH_RX = Pattern.compile(
        ".*\\.(pdf|docx|xlsx|pptx|zip|rar|txt|csv|odt|ods|odp)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern IMAGE_RX = Pattern.compile(
        ".*\\.(jpg|jpeg|png|gif|bmp|webp|svg)$",
        Pattern.CASE_INSENSITIVE
    );

    private static final String DEFAULT_OG_IMAGE_PATH = "/assets/hero-banner.jpg";

    /* ====== API Qute (safe) ====== */

    public static List<String> images(Object page) {
        var urls = safeFileUrls(page);
        // filtre sur le "nom" (basename) pour détecter l’extension
        return urls.stream().filter(u -> isImage(basename(u))).toList();
    }

    public static String firstImage(Object page) {
        var imgs = images(page);
        return imgs.isEmpty() ? null : imgs.get(0);
    }

    public static boolean hasImages(Object page) {
        return !images(page).isEmpty();
    }

    public static int imagesCount(Object page) {
        return images(page).size();
    }

    public static List<String> attachments(Object page) {
        var urls = safeFileUrls(page);
        return urls.stream().filter(u -> isAttachment(basename(u))).toList();
    }

    public static boolean hasAttachments(Object page) {
        return !attachments(page).isEmpty();
    }

    public static int attachmentsCount(Object page) {
        return attachments(page).size();
    }

    /**
     * Returns the absolute URL of the best available image for a page's Open Graph (og:image)
     * and Twitter Card (twitter:image) meta tags.
     *
     * Resolution order:
     * 1. page.data.cover (frontmatter cover image)
     * 2. First image attached to the page (files in the same directory)
     * 3. Default site banner (/assets/hero-banner.jpg)
     *
     * Usage in templates: {files:ogImage(page)}
     */
    public static String ogImage(Object page) {
        String siteUrl = org.eclipse.microprofile.config.ConfigProvider.getConfig()
                .getOptionalValue("site.url", String.class)
                .orElse("");

        String cover = getPageDataString(page, "cover");
        if (cover != null && !cover.isBlank()) {
            return toAbsolute(cover, siteUrl, getPageUrlAbsolute(page));
        }

        String img = firstImage(page);
        if (img != null && !img.isBlank()) {
            return toAbsolute(img, siteUrl, getPageUrlAbsolute(page));
        }

        return siteUrl + DEFAULT_OG_IMAGE_PATH;
    }

    /**
     * Converts an image path to an absolute URL:
     * - already absolute (starts with "http") → return as-is
     * - root-relative (starts with "/") → prepend siteUrl
     * - relative filename → prepend pageUrlAbsolute (the page URL, which ends with "/")
     */
    private static String toAbsolute(String path, String siteUrl, String pageUrlAbsolute) {
        if (path.startsWith("http")) return path;
        if (path.startsWith("/")) return siteUrl + path;
        return (pageUrlAbsolute != null ? pageUrlAbsolute : siteUrl + "/") + path;
    }

    /** Accesses a string value from page.data (frontmatter) via reflection. */
    private static String getPageDataString(Object page, String key) {
        try {
            Method dataMethod = page.getClass().getMethod("data");
            Object data = dataMethod.invoke(page);
            if (data == null) return null;
            Method getMethod = data.getClass().getMethod("get", Object.class);
            Object val = getMethod.invoke(data, key);
            return val instanceof String s ? s : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Returns the absolute URL of the page (e.g. "https://tc11.fr/posts/my-post/") via reflection. */
    private static String getPageUrlAbsolute(Object page) {
        try {
            Method urlMethod = page.getClass().getMethod("url");
            Object url = urlMethod.invoke(page);
            if (url == null) return null;
            Method absoluteMethod = url.getClass().getMethod("absolute");
            Object abs = absoluteMethod.invoke(url);
            return abs instanceof String s ? s : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Afficher un nom "propre" (pour alt/titre) */
    @TemplateExtension
    public static String displayName(String path) {
        if (path == null || path.isBlank()) return "";
        String name = basename(path);
        name = URLDecoder.decode(name, StandardCharsets.UTF_8);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.replace('-', ' ').replace('_', ' ');
    }

    /* ====== Helpers ====== */

    private static boolean isAttachment(String name) {
        return ATTACH_RX.matcher(name).matches();
    }

    private static boolean isImage(String name) {
        return IMAGE_RX.matcher(name).matches();
    }

    private static String basename(String s) {
        if (s == null) return "";
        int q = s.indexOf('?'); if (q >= 0) s = s.substring(0, q);
        int h = s.indexOf('#'); if (h >= 0) s = s.substring(0, h);
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (slash >= 0) s = s.substring(slash + 1);
        return s;
    }

    /**
     * Récupère une liste d’URLs de fichiers de manière "ultra-safe".
     * Essaie successivement: getFiles(), files(), getAssets(), assets()
     * - Si String: on ajoute tel quel
     * - Si Asset (Roq): on ajoute asset.getUrl() (et on utilise asset.getName() pour le filtrage via basename)
     */
    @SuppressWarnings("unchecked")
    private static List<String> safeFileUrls(Object page) {
        if (page == null) return List.of();

        // 1) Tente files (String)
        var filesList = (List<String>) invokeList(page, "getFiles", String.class);
        if (filesList == null) filesList = (List<String>) invokeList(page, "files", String.class);

        // 2) Tente assets (Roq Asset)
        List<Object> assets = invokeList(page, "getAssets", Object.class);
        if (assets == null) assets = invokeList(page, "assets", Object.class);

        // Concatène ce qu’on a trouvé
        var urls = new ArrayList<String>();
        if (filesList != null) {
            urls.addAll(filesList);
        }
        if (assets != null) {
            for (Object a : assets) {
                // Essayons d’appeler getUrl()/getName() via réflexion pour ne pas dépendre des classes Roq
                String url = callString(a, "getUrl");
                if (url == null) url = callString(a, "url"); // au cas où
                if (url != null) {
                    urls.add(url);
                }
            }
        }
        return urls;
    }

    /** Invoke une méthode sans param retournant une List<?>; renvoie null si absente/exception */
    @SuppressWarnings("unchecked")
    private static <T> List<T> invokeList(Object target, String methodName, Class<T> elemType) {
        try {
            Method m = target.getClass().getMethod(methodName);
            Object val = m.invoke(target);
            if (val instanceof List<?> list) {
                // Pas de cast strict sur elemType pour rester permissif
                return (List<T>) list;
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            // inclut les RoqStaticFileException etc. -> on renvoie null
        }
        return null;
    }

    /** Appelle une méthode String sans param; null si indisponible */
    private static String callString(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            Object val = m.invoke(target);
            return (val instanceof String) ? (String) val : null;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }
}
