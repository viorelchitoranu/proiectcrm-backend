package com.springapp.proiectcrm.repository;

import com.springapp.proiectcrm.model.School;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface SchoolRepository extends JpaRepository<School, Integer> {

}
