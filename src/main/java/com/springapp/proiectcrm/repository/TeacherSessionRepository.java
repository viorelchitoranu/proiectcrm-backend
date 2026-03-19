package com.springapp.proiectcrm.repository;

import com.springapp.proiectcrm.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TeacherSessionRepository extends JpaRepository<TeacherSession, Integer> {

    // legatura pentru un set de sesiuni + profesor + rol (MAIN)
    List<TeacherSession> findBySessionInAndTeacherAndTeachingRole(
            List<Session> sessions,
            User teacher,
            TeachingRole teachingRole
    );

    boolean existsByTeacherAndTeachingRoleAndSession_SessionDateGreaterThanEqualAndSession_SessionStatus(
            User teacher,
            TeachingRole teachingRole,
            LocalDate date,
            SessionStatus status
    );

    //profesorul MAIN pentru fiecare sesiune
    List<TeacherSession> findBySessionInAndTeachingRole(List<Session> sessions, TeachingRole teachingRole);

    // inainte de stergerea unei sesiuni PLANNED, se sterg  legaturile teacher_session
    void deleteBySessionIn(List<Session> sessions);

}
