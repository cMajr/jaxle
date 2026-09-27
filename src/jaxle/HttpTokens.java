package jaxle;

import java.util.BitSet;

final class HttpTokens {
    private static final BitSet TOKEN = new BitSet(128);

    static {
        for (char c = 'a'; c <= 'z'; c++) TOKEN.set(c);
        for (char c = 'A'; c <= 'Z'; c++) TOKEN.set(c);
        for (char c = '0'; c <= '9'; c++) TOKEN.set(c);

        for (char c : "!#$%&'*+-.^_`|~".toCharArray()) TOKEN.set(c);
    }

    private HttpTokens() {

    }

    static int indexOfInvalid(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!TOKEN.get(c)) {
                return i;
            }
        }

        return -1;
    }
}
