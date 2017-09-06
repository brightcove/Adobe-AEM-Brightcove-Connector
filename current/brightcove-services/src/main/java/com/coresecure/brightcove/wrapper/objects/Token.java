package com.coresecure.brightcove.wrapper.objects;

public class Token {
    private static String token_type;
    private static String token;
    private static int expire_in;

    public Token(String aToken, String aToken_type, int aExpire_in) {
        token = aToken;
        token_type = aToken_type;
        expire_in = aExpire_in;
    }

    public String getToken() {
        return token;
    }

    public String getTokenType() {
        return token_type;
    }

    public int getExpire_in() {
        return expire_in;
    }
}
