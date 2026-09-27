package jaxle;

import java.util.HashMap;
import java.util.Map;

final class QueryParser {
    private QueryParser() {

    }

    static String stripQuery(String target) {
        int queryStart = target.indexOf('?');
        if (queryStart < 0) {
            return target;
        }

        return target.substring(0, queryStart);
    }

    static String rawQuery(String target) {
        int queryStart = target.indexOf('?');
        if (queryStart < 0) {
            return "";
        }

        return target.substring(queryStart + 1);
    }

    static Map<String, String> parseQuery(String query) {
        Map<String, String> queryParams = new HashMap<>();
        String[] pairs = query.split("&");

        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }

            String[] pairParts = pair.split("=", 2);
            String name = PercentEncoding.decode(pairParts[0]);
            String value = pairParts.length >= 2 ? PercentEncoding.decode(pairParts[1]) : "";

            // A repeated name keeps its first value.
            queryParams.putIfAbsent(name, value);
        }

        return queryParams;
    }
}
