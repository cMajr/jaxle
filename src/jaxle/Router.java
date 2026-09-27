package jaxle;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

class Router {
    // Path is resolved first to distinguish 404 from 405.
    // When several patterns match, the first registered one wins.
    private final Map<String, Map<Method, Handler>> staticRoutes = new HashMap<>();
    private final Map<String, Map<Method, Handler>> patternRoutes = new LinkedHashMap<>();
    record Match(Map<Method, Handler> handlers, Map<String, String> params) {}

    void add(Method method, String path, Handler handler) {
        String normalizedPath = normalizePath(path);
        Map<String, Map<Method, Handler>> routes;
        if (normalizedPath.contains("{")) {
            routes = patternRoutes;
        } else {
            routes = staticRoutes;
        }

        Map<Method, Handler> inner = routes.get(normalizedPath);
        if (inner == null) {
            inner = new EnumMap<>(Method.class);
            // First method for this path.
            routes.put(normalizedPath, inner);
        }

        // Updates the map inside routes directly.
        inner.put(method, handler);
    }

    Match findRoute(String path) {
        String normalizedPath = normalizePath(path);
        String decodedPath = decodePath(normalizedPath);
        var inner = staticRoutes.get(decodedPath);

        if (inner != null) {
            return new Match(inner, Map.of());
        }

        for (Map.Entry<String, Map<Method, Handler>> route : patternRoutes.entrySet()) {
            var params = matchPath(route.getKey(), decodedPath);
            if (params != null) {
                return new Match(route.getValue(), params);
            }
        }

        return null;
    }

    private static Map<String, String> matchPath(String pattern, String path) {
        String[] patternParts = pattern.split("/");
        String[] pathParts = path.split("/");

        if (patternParts.length != pathParts.length) {
            return null;
        }

        Map<String, String> params = new HashMap<>();

        for (int i = 0; i < patternParts.length; i++) {
            String patternPart = patternParts[i];
            String pathPart = pathParts[i];

            if (patternPart.startsWith("{") && patternPart.endsWith("}")) {
                String name = patternPart.substring(1, patternPart.length() - 1);
                params.put(name, pathPart);
            } else if (!patternPart.equals(pathPart)) {
                return null;
            }
        }

        return params;
    }

    private static String decodePath(String path) {
        if (path.toLowerCase(Locale.ROOT).contains("%2f")) {
            throw new HttpException(400, "encoded slash in path");
        }

        // Without a -1 limit, split() turns a path like "/" into
        // ["", ""] and starts discarding empty strings from the
        // end. Then join("/", []) returns "" instead of "/".
        // "//" is not merged into "/".
        String[] parts = path.split("/", -1);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = PercentEncoding.decode(parts[i].replace("+", "%2B"));
        }

        return String.join("/", parts);
    }

    private static String normalizePath(String path) {
        if (path.endsWith("/") && !path.equals("/")) {
            return path.substring(0, path.length() - 1);
        }

        return path;
    }

    static String allowedMethods(Match route) {
        var allowed = EnumSet.copyOf(route.handlers().keySet());
        // The server answers OPTIONS on every path, with or without a handler.
        allowed.add(Method.OPTIONS);

        // Every path with GET also answers HEAD through the GET handler.
        if (allowed.contains(Method.GET)) {
            allowed.add(Method.HEAD);
        }

        return allowed
            .stream()
            .map(Method::name)
            .collect(Collectors.joining(", "));
    }

}
