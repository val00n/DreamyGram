package com.val00n.dmpg.model;

public class AuthResult {
    public final boolean isLogged;
    public final long ping;

    public AuthResult(boolean isLogged, long ping) {
        this.isLogged = isLogged;
        this.ping = ping;
    }
}
