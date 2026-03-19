package com.springapp.proiectcrm.repository;

import com.springapp.proiectcrm.model.Child;
import com.springapp.proiectcrm.model.ChildGroup;
import com.springapp.proiectcrm.model.GroupClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChildGroupRepository extends JpaRepository<ChildGroup, Integer> {
    long countByGroupAndActiveTrue(GroupClass group);
    Optional<ChildGroup> findByChildAndGroup(Child child, GroupClass group);

    List<ChildGroup> findByChildAndActiveTrue(Child child);

    List<ChildGroup> findByGroupAndActiveTrue(GroupClass group);
    void deleteByGroup(GroupClass group);

    Optional<ChildGroup> findTopByChildAndActiveTrueOrderByEnrollmentDateDesc(Child child);

    boolean existsByChildAndGroupAndActiveTrue(Child child, GroupClass group);

    @Query("""
  SELECT cg
  FROM ChildGroup cg
  JOIN FETCH cg.child c
  JOIN FETCH cg.group g
  WHERE cg.active = true AND c.idChild IN :childIds
""")
    List<ChildGroup> findActiveByChildIds(@Param("childIds") List<Integer> childIds);

    List<ChildGroup> findByChildOrderByEnrollmentDateDesc(Child child);

    // ── NOU: Modul 5 Message Board ────────────────────────────────────────────

    /**
     * Verifică dacă un PARENT (identificat prin userId) are cel puțin un copil
     * activ înscris în grupa cu id-ul dat.
     *
     * Folosit în MessageBoardServiceImpl.checkGroupMembership() pentru a determina
     * dacă un PARENT are acces la canalul GROUP_{groupId}.
     *
     * Navigare JPA:
     *   child.parent.idUser = :parentUserId  (copilul aparține părintelui)
     *   group.idGroup = :groupId             (copilul este înscris în această grupă)
     *   active = true                        (înscrierea este activă)
     *
     * @param parentUserId  id-ul User-ului cu rol PARENT
     * @param groupId       id-ul grupei (GroupClass.idGroup)
     */
    boolean existsByChild_Parent_IdUserAndGroup_IdGroupAndActiveTrue(
            int parentUserId, int groupId);

}
