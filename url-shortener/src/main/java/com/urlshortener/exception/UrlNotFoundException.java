package com.urlshortener.exception;

/**
 * Thrown when no mapping exists for the requested short code.
 * Maps to HTTP 404 Not Found.
 */
public class UrlNotFoundException extends RuntimeException {
    public UrlNotFoundException(String code) {
        super("Short code not found: " + code);
    }
}
