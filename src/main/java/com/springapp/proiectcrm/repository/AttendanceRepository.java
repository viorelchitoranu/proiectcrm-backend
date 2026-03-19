package com.springapp.proiectcrm.repository;

import com.springapp.proiectcrm.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Integer> {
    List<Attendance> findBySession(Session session);

    Optional<Attendance> findBySessionAndChild(Session session, Child child);

    List<Attendance> findBySession_Group_Teacher(User teacher);


    List<Attendance> findBySessionIn(List<Session> sessions);

    boolean existsBySession(Session session);

    long countBySessionIn(List<Session> sessions);

    List<Attendance> findBySession_Group(GroupClass group);

    List<Attendance> findByChild_IdChildAndSession_Group_IdGroupAndSession_SessionDateGreaterThanEqual(
            int childId,
            int groupId,
            LocalDate fromDate
    );


    @Query("""
                select count(a)
                from Attendance a
                where a.session.group = :group
                  and a.isRecovery = true
                  and a.session.sessionDate >= :fromDate
            """)
    long countFutureRecovery(@Param("group") GroupClass group,
                             @Param("fromDate") LocalDate fromDate);


    @Query("""
            select count(a)
            from Attendance a
            where a.session = :session
              and a.isRecovery = true""")
    long countRecoveryForSession(@Param("session") Session session);

    List<Attendance> findByChild_IdChildAndSessionIn(int childId, List<Session> sessions);


}
