package jaxle;

import java.util.Objects;

public record Json(String content) {
    private static volatile JsonMapper mapper;

    public static void setMapper(JsonMapper mapper) {
        Json.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public static Json json(Object object) {
        if (mapper == null) {
            throw new IllegalStateException(
                "JSON mapper is not set. Call Json.setMapper before using json."
            );
        }

        return new Json(mapper.toJson(object));
    }
}
