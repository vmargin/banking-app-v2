package com.vmargin.banking.web;

import java.sql.SQLException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/** Keeps database failures at the HTML boundary safe and actionable. */
@ControllerAdvice(annotations = Controller.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class BankingExceptionHandler {
    @ExceptionHandler(SQLException.class)
    public ModelAndView databaseUnavailable() {
        ModelAndView response = new ModelAndView("service-unavailable");
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE);
        return response;
    }
}
