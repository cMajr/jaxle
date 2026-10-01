package jaxle;

public interface JsonMapper {
    String toJson(Object object);
    <T> T fromJson(String content, Class<T> type);
}
