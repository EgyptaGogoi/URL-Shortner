package com.urlshortener.exception;

/**
 * Thrown when the requested custom alias is already registered by another mapping.
 * Maps to HTTP 409 Conflict.
 */
public class AliasAlreadyTakenException extends RuntimeException {
    public AliasAlreadyTakenException(String alias) {
        super("Alias already taken: " + alias);
    }
}
