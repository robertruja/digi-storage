package ro.go.yomama.digistorage.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class Auth {

    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class Request {
        private String email;
        private String password;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class Response {
        private String token;
    }
}
