package com.project.drone_missions.data.repository;

import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Null suspendedAt = active. */
    long countByRoleAndSuspendedAtIsNull(UserRole role);
}
