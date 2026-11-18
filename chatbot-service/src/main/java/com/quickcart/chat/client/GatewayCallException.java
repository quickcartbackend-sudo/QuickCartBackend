package com.quickcart.chat.client;

public class GatewayCallException extends RuntimeException {

    public enum Kind { UNAUTHORIZED, NOT_FOUND, UNAVAILABLE, REJECTED }

    private final Kind kind;

    public GatewayCallException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}