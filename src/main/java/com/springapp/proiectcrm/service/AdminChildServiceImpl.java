package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.*;
import com.springapp.proiectcrm.exception.BusinessException;
import com.springapp.proiectcrm.exception.ErrorCode;
import com.springapp.proiectcrm.model.*;
import com.springapp.proiectcrm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Serviciu admin pentru operații pe copii: listare, detalii, mutare în grupe,
 * dezactivare și reactivare individuală.
 *
 * Logging:
 *   MOVE_CHILD_SUCCESS   → INFO după mutare/alocare reușită
 *   DEACTIVATE_CHILD_OK  → INFO după dezactivare (cu numărul de locuri eliberate)
 *   ACTIVATE_CHILD_OK    → INFO după reactivare
 *   Erorile de business sunt interceptate de GlobalExceptionHandler.
 *
 * Operațiile de START (DEACTIVATE_CHILD_START, MOVE_CHILD_START etc.) sunt
 * logate de AuditLoggingAspect.java prin AOP — nu sunt duplicate aici.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminChildServiceImpl implements AdminChildService {

    private final ChildRepository           childRepository;
    private final GroupClassRepository      groupClassRepository;
    private final ChildGroupRepository      childGroupRepository;
    private final AttendanceRepository      attendanceRepository;
    private final AttendanceArchiveService  attendanceArchiveService;

    // ══════════════════════════════════════════════════════════════════════════
    // Helper privat
    // ══════════════════════════════════════════════════════════════════════════

    private Child requireChild(int childId) {
        return childRepository.findById(childId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CHILD_NOT_FOUND,
                        "Copilul cu id " + childId + " nu există."
                ));
    }

    /**
     * Construiește AdminChildRowResponse centralizat pentru a evita duplicarea.
     * Câmpul `active` — null tratat ca true (compatibilitate cu datele vechi).
     */
    private AdminChildRowResponse toChildRow(Child c, ChildGroup activeCg) {
        User pUser       = c.getParent();
        Integer groupId  = activeCg != null ? activeCg.getGroup().getIdGroup()   : null;
        String groupName = activeCg != null ? activeCg.getGroup().getGroupName() : null;
        LocalDate enrollDate = activeCg != null ? activeCg.getEnrollmentDate()   : null;

        return new AdminChildRowResponse(
                c.getIdChild(), c.getChildFirstName(), c.getChildLastName(),
                c.getAge(), c.getSchool(), c.getSchoolClass(),
                pUser != null ? pUser.getIdUser()                                  : null,
                pUser != null ? (pUser.getLastName() + " " + pUser.getFirstName()) : null,
                pUser != null ? pUser.getEmail()                                   : null,
                pUser != null ? pUser.getPhone()                                   : null,
                groupId, groupName, enrollDate,
                c.getActive()
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Listare paginată
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Listare paginată cu căutare LIKE. Optimizată anti N+1:
     * un singur query findActiveByChildIds() pentru toate ChildGroup-urile active
     * ale paginii curente, în loc de N query-uri separate.
     */
    @Override
    public AdminPagedResponse<AdminChildRowResponse> getChildren(String query, int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 5), 100);

        Page<Child> pg      = childRepository.searchChildren(query, PageRequest.of(p, s));
        List<Child> content = pg.getContent();

        List<Integer> childIds = content.stream().map(Child::getIdChild).toList();

        Map<Integer, ChildGroup> activeCgByChildId =
                childGroupRepository.findActiveByChildIds(childIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                cg -> cg.getChild().getIdChild(),
                                cg -> cg,
                                (a, b) -> a
                        ));

        List<AdminChildRowResponse> items = content.stream()
                .map(c -> toChildRow(c, activeCgByChildId.get(c.getIdChild())))
                .toList();

        return new AdminPagedResponse<>(items, pg.getNumber(), pg.getSize(),
                pg.getTotalElements(), pg.getTotalPages());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Profil complet copil
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public AdminChildDetailsResponse getChildDetails(int childId) {
        Child c = requireChild(childId);
        User  p = c.getParent();
        if (p == null) throw new IllegalStateException("Copilul nu are un părinte asociat.");

        ChildGroup activeCg = childGroupRepository
                .findTopByChildAndActiveTrueOrderByEnrollmentDateDesc(c)
                .orElse(null);

        AdminChildRowResponse childRow = toChildRow(c, activeCg);

        long kidsCount = (p.getChildren() != null) ? p.getChildren().size() : 0L;
        AdminParentSummaryResponse parentDto = new AdminParentSummaryResponse(
                p.getIdUser(), p.getFirstName(), p.getLastName(),
                p.getEmail(), p.getPhone(), kidsCount, p.getActive()
        );

        var cgs = childGroupRepository.findByChildOrderByEnrollmentDateDesc(c);
        var enrollments = cgs.stream().map(cg -> new AdminChildEnrollmentRowResponse(
                cg.getGroup().getIdGroup(), cg.getGroup().getGroupName(),
                cg.getGroup().getCourse() != null ? cg.getGroup().getCourse().getName() : null,
                cg.getGroup().getSchool() != null ? cg.getGroup().getSchool().getName() : null,
                cg.getEnrollmentDate(), cg.getGroup().getGroupStartDate(),
                cg.getGroup().getGroupEndDate(), cg.getGroup().getSessionStartTime(),
                cg.getActive()
        )).toList();

        return new AdminChildDetailsResponse(childRow, parentDto, enrollments);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Mutare copil în altă grupă
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Mută sau alocă un copil într-o grupă (7 pași, @Transactional atomic).
     * AuditLoggingAspect loghează MOVE_CHILD_START înainte de execuție.
     * Noi loghăm MOVE_CHILD_SUCCESS la final cu rezultatul complet.
     *
     * REFACTORIZARE (SonarCloud — Cognitive Complexity):
     *   Metoda originală avea complexitate 23 (limita e 15).
     *   Logica a fost extrasă în 4 metode private:
     *     validateMoveRequest()     → validări inițiale (request null, copil inactiv)
     *     resolveToGroup()          → încărcare și validare grupă destinație
     *     resolveFromGroup()        → determinare grupă sursă (explicit sau autodetect)
     *     validateTargetCapacity()  → verificare capacitate și duplicat
     *   Fiecare metodă privată are o singură responsabilitate clară.
     */
    @Override
    @Transactional
    public AdminMoveChildResponse moveChild(int childId, AdminMoveChildRequest request) {

        // Pasul 1: validări inițiale — request null, câmp obligatoriu, copil inactiv
        validateMoveRequest(request);
        LocalDate effective = (request.getEffectiveDate() != null)
                ? request.getEffectiveDate() : LocalDate.now();
        Child child = requireChild(childId);
        validateChildActive(child);

        // Pasul 2: încarcă și validează grupa destinație
        GroupClass toGroup = resolveToGroup(request.getToGroupId(), effective);

        // Pasul 3: determină grupa sursă (din request sau autodetect)
        GroupClass fromGroup = resolveFromGroup(request, child, toGroup);

        // Pasul 4: verifică capacitate și că nu e deja înscris
        validateTargetCapacity(child, toGroup);

        // Pasul 5: dezactivează înscrierea veche dacă există
        if (fromGroup != null) {
            deactivateEnrollmentsInGroup(child, fromGroup);
        }

        // Pasul 6: creează sau reactivează înscrierea nouă
        activateEnrollmentInGroup(child, toGroup, effective);

        // Pasul 7: arhivează sesiunile viitoare din grupa veche
        int archivedCount = 0;
        if (fromGroup != null) {
            archivedCount = attendanceArchiveService.archiveAndDeleteFutureAttendance(
                    child.getIdChild(), fromGroup.getIdGroup(), effective);
        }

        log.info("MOVE_CHILD_SUCCESS childId={} fromGroupId={} toGroupId={} effectiveDate={} archivedAttendance={}",
                childId,
                fromGroup != null ? fromGroup.getIdGroup() : "none",
                toGroup.getIdGroup(),
                effective,
                archivedCount);

        return new AdminMoveChildResponse(
                child.getIdChild(),
                fromGroup != null ? fromGroup.getIdGroup() : null,
                toGroup.getIdGroup(), effective, archivedCount,
                fromGroup == null
                        ? "Copil alocat cu succes la grupa " + toGroup.getGroupName() + "."
                        : "Copil mutat cu succes în grupa " + toGroup.getGroupName() + "."
        );
    }

    // ── Metode private extrase din moveChild() pentru reducerea complexității ──

    /**
     * Validează că request-ul nu e null și că toGroupId e prezent.
     * Aruncă IllegalArgumentException dacă validarea eșuează.
     */
    private void validateMoveRequest(AdminMoveChildRequest request) {
        if (request == null || request.getToGroupId() == null) {
            throw new IllegalArgumentException("toGroupId este obligatoriu.");
        }
    }

    /**
     * Verifică că un copil este activ înainte de a-l înscrie.
     * Un copil dezactivat individual nu poate fi înscris fără reactivare prealabilă.
     */
    private void validateChildActive(Child child) {
        if (Boolean.FALSE.equals(child.getActive())) {
            throw new BusinessException(ErrorCode.CHILD_IS_INACTIVE,
                    "Copilul " + child.getChildFirstName() + " " + child.getChildLastName()
                            + " este dezactivat. Reactivează-l înainte de a-l înscrie într-o grupă.");
        }
    }

    /**
     * Încarcă grupa destinație și o validează:
     *   - Există în BD
     *   - isActive = true
     *   - groupEndDate == null SAU effectiveDate <= groupEndDate
     *
     * @param toGroupId    ID-ul grupei destinație
     * @param effective    data efectivă a mutării
     * @return             GroupClass validată
     */
    private GroupClass resolveToGroup(Integer toGroupId, LocalDate effective) {
        GroupClass toGroup = groupClassRepository.findById(toGroupId)
                .orElseThrow(() -> new IllegalArgumentException("Grupa destinație nu a fost găsită."));
        if (!Boolean.TRUE.equals(toGroup.getIsActive())) {
            throw new IllegalStateException("Grupa destinație nu este activă.");
        }
        if (toGroup.getGroupEndDate() != null && effective.isAfter(toGroup.getGroupEndDate())) {
            throw new IllegalStateException("Data efectivă este după data de sfârșit a grupei.");
        }
        return toGroup;
    }

    /**
     * Determină grupa sursă (fromGroup) din care pleacă copilul.
     *
     * Logica:
     *   - Dacă request.fromGroupId != null → încărcăm explicit acea grupă
     *   - Dacă request.fromGroupId == null → autodetectăm din grupele active ale copilului:
     *       - 0 grupe active → fromGroup = null (copilul nu era nicăieri → alocare nouă)
     *       - 1 grupă activă → fromGroup = acea grupă
     *       - 2+ grupe active → eroare, client trebuie să trimită fromGroupId explicit
     *
     * Verifică că fromGroup != toGroup (același grup = operație inutilă).
     *
     * @param request  request-ul de mutare
     * @param child    copilul de mutat
     * @param toGroup  grupa destinație (pentru verificarea de identitate)
     * @return         GroupClass sursă sau null dacă e alocare nouă
     */
    private GroupClass resolveFromGroup(AdminMoveChildRequest request, Child child, GroupClass toGroup) {
        GroupClass fromGroup;
        if (request.getFromGroupId() != null) {
            fromGroup = groupClassRepository.findById(request.getFromGroupId())
                    .orElseThrow(() -> new IllegalArgumentException("Grupa sursă nu a fost găsită."));
        } else {
            List<ChildGroup> active = childGroupRepository.findByChildAndActiveTrue(child);
            if (active.size() > 1) {
                throw new IllegalStateException(
                        "Copilul are mai multe grupe active — trimite fromGroupId.");
            }
            fromGroup = active.size() == 1 ? active.getFirst().getGroup() : null;
        }
        if (fromGroup != null && fromGroup.getIdGroup() == toGroup.getIdGroup()) {
            throw new IllegalStateException("Grupa sursă și grupa destinație sunt aceeași.");
        }
        return fromGroup;
    }

    /**
     * Verifică că grupa destinație are capacitate disponibilă și că
     * copilul nu este deja înscris activ în ea.
     *
     * @param child    copilul care se înscrie
     * @param toGroup  grupa destinație
     */
    private void validateTargetCapacity(Child child, GroupClass toGroup) {
        long activeInTarget = childGroupRepository.countByGroupAndActiveTrue(toGroup);
        if (toGroup.getGroupMaxCapacity() > 0 && activeInTarget >= toGroup.getGroupMaxCapacity()) {
            throw new IllegalStateException("Grupa " + toGroup.getGroupName() + " este plină ("
                    + activeInTarget + "/" + toGroup.getGroupMaxCapacity() + " locuri).");
        }
        if (childGroupRepository.existsByChildAndGroupAndActiveTrue(child, toGroup)) {
            throw new IllegalStateException(
                    "Copilul este deja înscris activ în grupa " + toGroup.getGroupName() + ".");
        }
    }

    /**
     * Dezactivează toate înscrieriileactive ale copilului în grupa specificată.
     * Salvează modificările în BD.
     *
     * @param child      copilul
     * @param fromGroup  grupa din care pleacă
     */
    private void deactivateEnrollmentsInGroup(Child child, GroupClass fromGroup) {
        final GroupClass fg = fromGroup;
        List<ChildGroup> fromEnrollments = childGroupRepository
                .findByChildAndActiveTrue(child).stream()
                .filter(x -> x.getGroup().getIdGroup() == fg.getIdGroup())
                .toList();
        fromEnrollments.forEach(cg -> cg.setActive(Boolean.FALSE));
        childGroupRepository.saveAll(fromEnrollments);
    }

    /**
     * Creează sau reactivează înscrierea copilului în grupa destinație.
     *
     * orElseGet: previne duplicate key dacă înregistrarea inactivă există deja —
     * în loc să creeze un rând nou, reactivăm cel existent.
     *
     * @param child      copilul
     * @param toGroup    grupa destinație
     * @param effective  data efectivă a înscrierii
     */
    private void activateEnrollmentInGroup(Child child, GroupClass toGroup, LocalDate effective) {
        ChildGroup enrollment = childGroupRepository.findByChildAndGroup(child, toGroup)
                .orElseGet(() -> {
                    ChildGroup cg = new ChildGroup();
                    cg.setChild(child);
                    cg.setGroup(toGroup);
                    return cg;
                });
        enrollment.setEnrollmentDate(effective);
        enrollment.setActive(Boolean.TRUE);
        childGroupRepository.save(enrollment);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Dezactivare / reactivare INDIVIDUAL per copil
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Dezactivează un copil individual + eliberează locurile din toate grupele active.
     * AuditLoggingAspect loghează DEACTIVATE_CHILD_START.
     * Noi loghăm DEACTIVATE_CHILD_OK cu numărul de locuri eliberate.
     */
    @Override
    @Transactional
    public ChildActivationResponse deactivateChild(int childId) {
        Child child = requireChild(childId);

        if (Boolean.FALSE.equals(child.getActive())) {
            throw new BusinessException(ErrorCode.CHILD_ALREADY_INACTIVE,
                    "Copilul " + child.getChildFirstName() + " " + child.getChildLastName()
                            + " este deja dezactivat.");
        }

        // Eliberare locuri din toate grupele active ale acestui copil
        List<ChildGroup> activeEnrollments = childGroupRepository.findByChildAndActiveTrue(child);
        int releasedSlots = activeEnrollments.size();

        if (!activeEnrollments.isEmpty()) {
            activeEnrollments.forEach(cg -> cg.setActive(false));
            childGroupRepository.saveAll(activeEnrollments);
        }

        child.setActive(false);
        Child saved = childRepository.save(child);

        String childName = saved.getChildLastName() + " " + saved.getChildFirstName();
        Integer parentId = saved.getParent() != null ? saved.getParent().getIdUser() : null;

        // INFO cu rezultatul complet — util pentru audit: cine a eliberat câte locuri
        log.info("DEACTIVATE_CHILD_OK childId={} childName=\"{}\" parentId={} slotsReleased={}",
                childId, childName, parentId, releasedSlots);

        return new ChildActivationResponse(saved.getIdChild(), childName, parentId,
                false, releasedSlots, LocalDateTime.now(),
                "Copilul " + childName + " a fost dezactivat. " + releasedSlots + " loc(uri) de grupă eliberate.");
    }

    /**
     * Reactivează un copil individual.
     * Re-înscrierea în grupe NU se face automat — locurile pot fi ocupate.
     * AuditLoggingAspect loghează ACTIVATE_CHILD_START.
     */
    @Override
    @Transactional
    public ChildActivationResponse activateChild(int childId) {
        Child child = requireChild(childId);

        // Guard: null tratat ca activ (compatibilitate cu datele vechi)
        if (!Boolean.FALSE.equals(child.getActive())) {
            throw new BusinessException(ErrorCode.CHILD_ALREADY_ACTIVE,
                    "Copilul " + child.getChildFirstName() + " " + child.getChildLastName()
                            + " este deja activ.");
        }

        child.setActive(true);
        Child saved = childRepository.save(child);

        String childName = saved.getChildLastName() + " " + saved.getChildFirstName();
        Integer parentId = saved.getParent() != null ? saved.getParent().getIdUser() : null;

        log.info("ACTIVATE_CHILD_OK childId={} childName=\"{}\" parentId={}",
                childId, childName, parentId);

        return new ChildActivationResponse(saved.getIdChild(), childName, parentId,
                true, 0, LocalDateTime.now(),
                "Copilul " + childName + " a fost reactivat. Înscrierea în grupe trebuie refăcută manual.");
    }
}
