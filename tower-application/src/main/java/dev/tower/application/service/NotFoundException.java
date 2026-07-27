package dev.tower.application.service;

/** Signals that a requested aggregate does not exist. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
