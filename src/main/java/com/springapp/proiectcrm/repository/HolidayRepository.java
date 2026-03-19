package com.springapp.proiectcrm.repository;

import com.springapp.proiectcrm.model.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Integer> {
    List<Holiday> findByHolidayDateBetween(
            LocalDate start, LocalDate end);

    Optional<Holiday> findByHolidayDate(LocalDate date);

}
