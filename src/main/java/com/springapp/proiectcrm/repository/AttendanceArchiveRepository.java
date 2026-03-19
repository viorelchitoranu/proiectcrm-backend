package com.springapp.proiectcrm.repository;

import com.springapp.proiectcrm.model.AttendanceArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface AttendanceArchiveRepository extends JpaRepository<AttendanceArchive, Long> {

}
