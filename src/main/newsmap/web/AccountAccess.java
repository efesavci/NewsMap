package main.newsmap.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

final class AccountAccess {
    private AccountAccess() {}

    static String requireOwner(Principal principal, String requestedUserId) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!principal.getName().equals(requestedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This profile belongs to another account");
        }
        return requestedUserId;
    }
}
