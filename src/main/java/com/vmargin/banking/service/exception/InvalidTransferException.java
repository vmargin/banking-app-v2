package com.vmargin.banking.service.exception;

public class InvalidTransferException extends IllegalArgumentException {

    public InvalidTransferException(String message) {
        super(message);
    }
}
