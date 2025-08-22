package com.pm.services;

import com.pm.dto.AppUser;
import java.util.Optional;

public interface UserService {
    AppUser registerOrUpdate(AppUser user);
    Optional<AppUser> findById(long userId);
}
