package jaxle;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

class Router {
    // Path is resolved first to distinguish 404 from 405.
    // When several patterns match, the first registered one wins.
    private final Map<String, Map<Method, Handler>> routes = new LinkedHashMap<>();
    record Match(Map<Method, Handler> handlers, Map<String, String> params) {}

    void add(Method method, String path, Handler handler) {
        String normalizedPath = normalizePath(path);
        Map<Method, Handler> inner = this.routes.get(normalizedPath);

        if (inner == null) {
            inner = new EnumMap<>(Method.class);
            // First method for this path.
            this.routes.put(normalizedPath, inner);
        }

        // Map from routes is modified in place.
        inner.put(method, handler);
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

        params.replaceAll((name, value) -> PercentEncoding.decode(value.replace("+", "%2B")));
        return params;
    }

    Match findRoute(String path) {
        var normalizedPath = normalizePath(path);

        var inner = routes.get(normalizedPath);

        if (inner != null) {
            return new Match(inner, Map.of());
        }

        for (Map.Entry<String, Map<Method, Handler>> route : routes.entrySet()) {
            var params = matchPath(route.getKey(), normalizedPath);
            if (params != null) {
                return new Match(route.getValue(), params);
            }
        }

        return null;
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
