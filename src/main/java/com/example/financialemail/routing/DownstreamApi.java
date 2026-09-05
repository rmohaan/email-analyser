package com.example.financialemail.routing;

public enum DownstreamApi {
    API_1("API 1"),
    API_2("API 2"),
    API_3("API 3"),
    API_4("API 4");

    private final String displayName;

    DownstreamApi(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
