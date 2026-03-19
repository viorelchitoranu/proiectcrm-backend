package com.springapp.proiectcrm.repository;


import com.springapp.proiectcrm.dto.AdminParentSummaryResponse;
import com.springapp.proiectcrm.model.Role;
import com.springapp.proiectcrm.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    @EntityGraph(attributePaths = "role")
    Optional<User> findByEmail(String email);

    List<User> findByRole(Role role);

    boolean existsByEmail(String email);

    @Query(
            value = """
                      SELECT new com.springapp.proiectcrm.dto.AdminParentSummaryResponse(
                        u.idUser,
                        u.firstName,
                        u.lastName,
                        u.email,
                        u.phone,
                        COUNT(c),
                        CASE WHEN u.active IS NULL THEN TRUE ELSE u.active END
                      )
                      FROM User u
                      JOIN u.role r
                      LEFT JOIN u.children c
                      WHERE LOWER(r.roleName) = 'parent'
                        AND (
                             :q IS NULL OR :q = '' OR
                             LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR
                             LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :q, '%')) OR
                             LOWER(u.email)     LIKE LOWER(CONCAT('%', :q, '%')) OR
                             LOWER(u.phone)     LIKE LOWER(CONCAT('%', :q, '%'))
                        )
                      GROUP BY u.idUser, u.firstName, u.lastName, u.email, u.phone, u.active
                      ORDER BY u.lastName ASC, u.firstName ASC
                    """,
            countQuery = """
                      SELECT COUNT(DISTINCT u.idUser)
                      FROM User u
                      JOIN u.role r
                      WHERE LOWER(r.roleName) = 'parent'
                        AND (
                             :q IS NULL OR :q = '' OR
                             LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR
                             LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :q, '%')) OR
                             LOWER(u.email)     LIKE LOWER(CONCAT('%', :q, '%')) OR
                             LOWER(u.phone)     LIKE LOWER(CONCAT('%', :q, '%'))
                        )
                    """
    )
    Page<AdminParentSummaryResponse> findParentsPaged(@Param("q") String q, Pageable pageable);

}
