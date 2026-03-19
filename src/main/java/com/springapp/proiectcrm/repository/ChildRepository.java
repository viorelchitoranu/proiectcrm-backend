package com.springapp.proiectcrm.repository;

import com.springapp.proiectcrm.model.Child;
import com.springapp.proiectcrm.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChildRepository extends JpaRepository<Child, Integer> {

    Optional<Child> findByParentAndChildLastNameAndChildFirstName(
            User parent,
            String childLastName,
            String childFirstName
    );

    List<Child> findByParent(User parent);

    @Query("""
      SELECT c
      FROM Child c
      JOIN c.parent p
      WHERE (:q IS NULL OR :q = '' OR
             LOWER(c.childFirstName) LIKE LOWER(CONCAT('%', :q, '%')) OR
             LOWER(c.childLastName)  LIKE LOWER(CONCAT('%', :q, '%')) OR
             LOWER(p.firstName)      LIKE LOWER(CONCAT('%', :q, '%')) OR
             LOWER(p.lastName)       LIKE LOWER(CONCAT('%', :q, '%')) OR
             LOWER(p.email)          LIKE LOWER(CONCAT('%', :q, '%')) OR
             LOWER(p.phone)          LIKE LOWER(CONCAT('%', :q, '%'))
      )
      ORDER BY c.childLastName ASC, c.childFirstName ASC
    """)
    Page<Child> searchChildren(@Param("q") String q, Pageable pageable);



}
