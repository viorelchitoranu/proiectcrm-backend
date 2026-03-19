package com.springapp.proiectcrm.repository;

import com.springapp.proiectcrm.model.GroupClass;
import com.springapp.proiectcrm.model.School;
import com.springapp.proiectcrm.model.Session;
import com.springapp.proiectcrm.model.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, Integer> {

    long countByGroup(GroupClass group);

    long countByGroupAndSessionStatus(GroupClass group, SessionStatus status);

    List<Session> findByGroupOrderBySessionDateAsc(GroupClass group);

    List<Session> findByGroupAndSessionDateGreaterThanEqualOrderBySessionDateAsc(
            GroupClass group, LocalDate fromDate);

    List<Session> findBySessionDateBetweenAndSessionStatus(
            LocalDate startDate,
            LocalDate endDate,
            SessionStatus status
    );

    List<Session> findBySessionDate(LocalDate date);

    //toate sesiunile viitoare PLANNED pentru o grupa
    List<Session> findByGroupAndSessionDateGreaterThanEqualAndSessionStatus(
            GroupClass group,
            LocalDate date,
            SessionStatus status
    );

    List<Session> findByGroupAndSessionDateAfterAndSessionStatus(
            GroupClass group,
            LocalDate date,
            SessionStatus status
    );

    boolean existsByGroupAndSessionDate(GroupClass group, LocalDate sessionDate);

    Optional<Session> findTopByGroupOrderBySequenceNoDesc(GroupClass group);

    List<Session> findByGroup(GroupClass group);


    // Pentru PURGE (se sterg  toate NON-TAUGHT, indiferent de data)
    List<Session> findByGroupAndSessionStatusIn(GroupClass group, List<SessionStatus> statuses);

    // Pentru extensii:
    Optional<Session> findTopByGroupOrderBySessionDateDesc(GroupClass group);

    List<Session> findBySessionDateAndSessionStatus(LocalDate date, SessionStatus status);

    long countByGroupAndSessionStatusIn(GroupClass group, List<SessionStatus> statuses);

    List<Session> findBySessionDateBetween(LocalDate startDate, LocalDate endDate);

    @Query("""
            select sess
            from Session sess
            where sess.sessionStatus = :status
              and sess.sessionDate >= :fromDate
              and (
                    (sess.school = :school)
                    or
                    (sess.group.school = :school)
                  )
              and sess.group.maxRecoverySlots is not null
              and sess.group.maxRecoverySlots > 0
            order by sess.sessionDate asc, sess.time asc
            """)
    List<Session> findPlannedFutureRecoveryTargetsBySchool(
            @Param("school") School school,
            @Param("fromDate") LocalDate fromDate,
            @Param("status") SessionStatus status
    );

    @Modifying
    @Query("""
                update Session s
                   set s.sessionStatus = com.springapp.proiectcrm.model.SessionStatus.TAUGHT
                 where s.sessionStatus = com.springapp.proiectcrm.model.SessionStatus.PLANNED
                   and s.sessionDate is not null
                   and s.sessionDate < :today
            """)
    void markPastPlannedAsTaught(@Param("today") LocalDate today);


}
