package jaxle;

import java.util.Objects;

public record Json(String content) {
    private static volatile JsonMapper mapper;

    public static void setMapper(JsonMapper mapper) {
        Json.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    static JsonMapper mapper() {
        if (mapper == null) {
            throw new IllegalStateException(
                "JSON mapper is not set. Call Json.setMapper before using json."
            );
        }

        return mapper;
    }

    public static Json json(Object object) {
        return new Json(mapper().toJson(object));
    }
}
