package com.quickcart.chat.security;

public record AuthenticatedUser(long id, String email, String role) {
}