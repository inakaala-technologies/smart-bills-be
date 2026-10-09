package com.bhive.common.config;

public record DatabaseCredentials(String url, String username, String password) {

    @Override
    public String toString() {
        return "DatabaseCredentials[url=" + url + ", username=" + username + ", password=<redacted>]";
    }
}
