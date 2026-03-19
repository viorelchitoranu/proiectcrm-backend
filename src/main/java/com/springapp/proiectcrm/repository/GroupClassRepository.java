package com.springapp.proiectcrm.repository;

import com.springapp.proiectcrm.model.Course;

import com.springapp.proiectcrm.model.GroupClass;
import com.springapp.proiectcrm.model.School;
import com.springapp.proiectcrm.model.User;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupClassRepository extends JpaRepository<GroupClass, Integer> {

    // toate grupele active
    List<GroupClass> findByIsActiveTrue();

    // grupele active dintr-o scoala
    List<GroupClass> findBySchoolAndIsActiveTrue(School school);

    // grupele active pentru o combinație scoala + curs
    List<GroupClass> findBySchoolAndCourseAndIsActiveTrue(School school, Course course);

    List<GroupClass> findByCourse(Course course);

    List<GroupClass> findBySchool(School school);

    List<GroupClass> findByTeacher(User teacher);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           SELECT g
           FROM GroupClass g
           WHERE g.idGroup IN :ids
           ORDER BY g.idGroup ASC
           """)
    List<GroupClass> findAllByIdGroupInForUpdate(@Param("ids") List<Integer> ids);

    // ── NOU: Modul 5 Message Board ────────────────────────────────────────────

    /**
     * Verifică dacă un profesor (identificat prin userId) predă la grupa dată.
     *
     * Folosit în MessageBoardServiceImpl.checkGroupMembership() pentru a determina
     * dacă un TEACHER are acces la canalul GROUP_{groupId}.
     *
     * @param groupId    id-ul grupei (GroupClass.idGroup)
     * @param teacherUserId  id-ul User-ului cu rol TEACHER
     */
    boolean existsByIdGroupAndTeacher_IdUser(int groupId, int teacherUserId);

}
