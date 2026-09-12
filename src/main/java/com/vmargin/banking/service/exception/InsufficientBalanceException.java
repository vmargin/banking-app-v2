package com.vmargin.banking.service.exception;

public class InsufficientBalanceException extends IllegalArgumentException {

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
