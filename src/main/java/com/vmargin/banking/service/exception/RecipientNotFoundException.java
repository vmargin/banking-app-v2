package com.vmargin.banking.service.exception;

public class RecipientNotFoundException extends IllegalArgumentException {

    public RecipientNotFoundException(String message) {
        super(message);
    }
}
