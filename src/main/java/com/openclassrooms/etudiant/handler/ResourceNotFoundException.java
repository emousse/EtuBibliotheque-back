package com.openclassrooms.etudiant.handler;

/**
 * Levée quand une ressource demandée n'existe pas ; convertie en 404 par RestExceptionHandler.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
