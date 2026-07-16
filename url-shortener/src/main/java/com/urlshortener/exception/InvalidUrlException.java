package com.urlshortener.exception;

/**
 * Thrown when the submitted URL is syntactically invalid, uses a disallowed scheme,
 * or targets a private/loopback address (SSRF prevention). Maps to HTTP 400 Bad Request.
 */
public class InvalidUrlException extends RuntimeException {
    public InvalidUrlException(String message) {
        super(message);
    }
}
