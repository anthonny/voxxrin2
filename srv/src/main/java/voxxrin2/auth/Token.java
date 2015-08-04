package voxxrin2.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Token {
    String token;

    public Token(@JsonProperty("token") String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }
}
