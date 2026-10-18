package com.ecommerce.platform.modules.users.repository;

import com.ecommerce.platform.modules.users.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = {"roles"})
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    @EntityGraph(attributePaths = {"roles"})
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdWithRoles(@Param("id") Long id);

    @EntityGraph(attributePaths = {"roles"})
    @Query("select u from User u")
    Page<User> findAllWithRoles(Pageable pageable);
}
