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
