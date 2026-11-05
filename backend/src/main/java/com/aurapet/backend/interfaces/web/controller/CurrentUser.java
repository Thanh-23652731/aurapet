package com.aurapet.backend.interfaces.web.controller;

import com.aurapet.backend.application.service.Actor;
import com.aurapet.backend.infrastructure.security.CustomUserDetails;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

final class CurrentUser {

    private CurrentUser() {
    }

    static Actor of(CustomUserDetails user) {
        if (user == null) {
            throw new AuthenticationCredentialsNotFoundException("Chưa đăng nhập");
        }
        return user.toActor();
    }
}
