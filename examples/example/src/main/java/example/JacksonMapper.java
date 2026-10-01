package example;

import jaxle.InvalidJsonException;
import jaxle.JsonMapper;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;

public class JacksonMapper implements JsonMapper {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String toJson(Object object) {
        return mapper.writeValueAsString(object);
    }

    @Override
    public <T> T fromJson(String content, Class<T> type) {
        try {
            return mapper.readValue(content, type);
        } catch (StreamReadException | MismatchedInputException e) {
            throw new InvalidJsonException("cannot read " + type.getSimpleName(), e);
        }
    }
}
