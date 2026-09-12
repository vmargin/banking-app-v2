package com.vmargin.banking.service.exception;

public class AccessDeniedException extends SecurityException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
