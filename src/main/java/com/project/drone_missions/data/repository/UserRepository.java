package com.project.drone_missions.data.repository;

import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.model.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** A null role means "not filtering", mirroring the audit search's convention. */
    @Query("select u from User u where (:role is null or u.role = :role)")
    Page<User> search(@Param("role") UserRole role, Pageable pageable);

    long countByRoleAndSuspendedFalse(UserRole role);

    /** Suspended accounts across every role. */
    long countBySuspendedTrue();

    @Query("select u.role as role, count(u) as total from User u group by u.role")
    List<RoleCount> countByRole();

    /** Spring Data projection — keeps the aggregate typed instead of an Object[] row. */
    interface RoleCount {
        UserRole getRole();

        Long getTotal();
    }
}
