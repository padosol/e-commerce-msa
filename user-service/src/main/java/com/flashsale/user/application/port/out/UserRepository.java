package com.flashsale.user.application.port.out;

import com.flashsale.user.domain.User;
import java.util.Optional;

public interface UserRepository {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    /** 이메일 유니크 위반이면 EmailTakenException 을 던진다. */
    User save(User user);
}
