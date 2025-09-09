package com.aem.ai.pm.services;

import com.aem.ai.pm.dto.AppUser;

import java.util.Optional;

public interface UserService {
    AppUser registerOrUpdate(AppUser user);
    Optional<AppUser> findById(long userId);
    Optional<AppUser> findByIdOrEmail(String idOrEmail);

}
