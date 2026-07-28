package br.com.operadorzero.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public final class GoogleAuthorizationRequestGate {
    private static final String SESSION_ATTRIBUTE =
        GoogleAuthorizationRequestGate.class.getName() + ".allowed";

    private GoogleAuthorizationRequestGate() {}

    public static void allowNext(HttpServletRequest request) {
        request.getSession(true).setAttribute(SESSION_ATTRIBUTE, Boolean.TRUE);
    }

    public static boolean consume(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !Boolean.TRUE.equals(session.getAttribute(SESSION_ATTRIBUTE))) {
            return false;
        }
        session.removeAttribute(SESSION_ATTRIBUTE);
        return true;
    }
}
