package com.flashsale.user.adapter.out.persistence;

import com.flashsale.user.application.port.out.UserRepository;
import com.flashsale.user.domain.EmailTakenException;
import com.flashsale.user.domain.User;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository jpa;

    UserPersistenceAdapter(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmail(email);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmail(email).map(UserJpaEntity::toDomain);
    }

    @Override
    public User save(User user) {
        try {
            return jpa.saveAndFlush(UserJpaEntity.from(user)).toDomain();
        } catch (DataIntegrityViolationException e) {
            throw new EmailTakenException();
        }
    }
}
