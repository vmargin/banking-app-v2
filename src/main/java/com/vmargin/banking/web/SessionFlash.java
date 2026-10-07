package com.vmargin.banking.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;

final class SessionFlash {
    static final String ERROR_ATTRIBUTE = "actionError";
    static final String SUCCESS_ATTRIBUTE = "actionSuccess";

    private SessionFlash() {
    }

    static void takeValue(HttpSession session, Model model, String key, String modelName) {
        Object value = session.getAttribute(key);
        if (value != null) {
            model.addAttribute(modelName, value);
            session.removeAttribute(key);
        }
    }

    static void consumeActions(HttpSession session, Model model) {
        takeValue(session, model, ERROR_ATTRIBUTE, "error");
        takeValue(session, model, SUCCESS_ATTRIBUTE, "success");
    }

    static void clearActions(HttpSession session) {
        session.removeAttribute(ERROR_ATTRIBUTE);
        session.removeAttribute(SUCCESS_ATTRIBUTE);
    }
}
