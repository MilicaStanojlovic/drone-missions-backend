package com.project.drone_missions.data.repository;

import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Null suspendedAt = active. */
    long countByRoleAndSuspendedAtIsNull(UserRole role);

    /** Suspended accounts across every role. */
    long countBySuspendedAtIsNotNull();

    @Query("select u.role as role, count(u) as total from User u group by u.role")
    List<RoleCount> countByRole();

    /** Spring Data projection — keeps the aggregate typed instead of an Object[] row. */
    interface RoleCount {
        UserRole getRole();

        Long getTotal();
    }
}
