package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.model.*;
import com.springapp.proiectcrm.repository.AttendanceArchiveRepository;
import com.springapp.proiectcrm.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceArchiveServiceImpl implements AttendanceArchiveService {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceArchiveRepository attendanceArchiveRepository;

    /**
     * Arhivează și șterge attendance-urile viitoare ale unui copil dintr-o grupă.
     * Apelat la mutarea unui copil dintr-o grupă în alta.
     * <p>
     * REFACTORIZARE (SonarCloud — Cognitive Complexity):
     * Metoda originală avea complexitate 40 (limita e 15) din cauza lambda-ului
     * mare cu zeci de expresii ternare nested din stream().map().
     * Mapping-ul a fost extras în metoda privată toArchiveRecord() care
     * are o singură responsabilitate: conversia Attendance → AttendanceArchive.
     *
     * @param childId  ID-ul copilului
     * @param groupId  ID-ul grupei din care pleacă
     * @param fromDate data de la care se arhivează (inclusiv)
     * @return numărul de înregistrări arhivate
     */
    @Override
    @Transactional
    public int archiveAndDeleteFutureAttendance(int childId, int groupId, LocalDate fromDate) {

        List<Attendance> toArchive = attendanceRepository
                .findByChild_IdChildAndSession_Group_IdGroupAndSession_SessionDateGreaterThanEqual(
                        childId, groupId, fromDate
                );

        if (toArchive.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();

        // Mapping extras în metodă privată — reduce complexitatea ciclomatică
        List<AttendanceArchive> archives = toArchive.stream()
                .map(a -> toArchiveRecord(a, now))
                .toList();

        attendanceArchiveRepository.saveAll(archives);
        attendanceRepository.deleteAll(toArchive);

        return archives.size();
    }

    /**
     * Convertește un Attendance într-un AttendanceArchive pentru stocare istorică.
     * <p>
     * Toate câmpurile sunt copiate din entitățile asociate (Session, GroupClass,
     * Child, Parent) deoarece arhiva trebuie să fie auto-conținută — entitățile
     * originale pot fi șterse sau modificate ulterior.
     * <p>
     * REFACTORIZARE (SonarCloud — Cognitive Complexity):
     * Metoda originală avea complexitate 22 (limita e 15) din cauza celor 15+
     * expresii ternare (null checks) inline.
     * Setter-urile au fost grupate în 3 metode private cu responsabilitate unică:
     * fillGroupSessionFields()  → câmpuri grupă + sesiune
     * fillChildParentFields()   → câmpuri copil + părinte (GDPR snapshot)
     * fillOriginalFields()      → câmpurile originale ale attendance-ului
     * <p>
     * Notă: `s` (Session) nu poate fi null deoarece Attendance are întotdeauna o sesiune —
     * verificările null sunt păstrate pentru robustețe și compatibilitate cu date vechi.
     *
     * @param a   attendance-ul de arhivat
     * @param now timestamp-ul arhivării (calculat o dată pentru toată lista, anti N+1)
     * @return înregistrarea de arhivă completată
     */
    private AttendanceArchive toArchiveRecord(Attendance a, LocalDateTime now) {
        Session s = a.getSession();
        GroupClass g = s != null ? s.getGroup() : null;
        Child c = a.getChild();
        User p = c != null ? c.getParent() : null;

        AttendanceArchive ar = new AttendanceArchive();

        // IDs originale — pentru trasat înapoi la înregistrările originale dacă e nevoie
        ar.setOriginalAttendanceId(a.getIdAttendance());
        ar.setOriginalSessionId(s != null ? s.getIdSession() : null);
        ar.setOriginalGroupId(g != null ? g.getIdGroup() : null);

        fillGroupSessionFields(ar, g, s);  // snapshot grupă + sesiune
        fillChildParentFields(ar, c, p);   // snapshot copil + părinte (GDPR)
        fillOriginalFields(ar, a, now);    // câmpuri originale attendance + metadata

        return ar;
    }

    /**
     * Completează câmpurile grupei și sesiunii în arhivă.
     * <p>
     * Toate câmpurile sunt snapshot-uri la momentul arhivării — grupele și sesiunile
     * pot fi șterse sau redenumite ulterior, iar arhiva trebuie să rămână intactă.
     * <p>
     * schoolName și courseName: null dacă grupa nu are școală/curs asociat.
     * teacherName: concatenare LastName + FirstName, null dacă grupa nu are profesor.
     * sessionDate/Time/Status/Type: null dacă attendance-ul nu are sesiune (date vechi).
     *
     * @param ar arhiva de completat
     * @param g  grupa sesiunii originale (poate fi null pentru date vechi)
     * @param s  sesiunea originală (poate fi null pentru date vechi)
     */
    private void fillGroupSessionFields(AttendanceArchive ar, GroupClass g, Session s) {
        ar.setGroupName(g != null ? g.getGroupName() : null);
        ar.setCourseName(g != null && g.getCourse() != null ? g.getCourse().getName() : null);
        ar.setSchoolName(g != null && g.getSchool() != null ? g.getSchool().getName() : null);
        ar.setTeacherName(g != null && g.getTeacher() != null
                ? g.getTeacher().getLastName() + " " + g.getTeacher().getFirstName()
                : null);

        ar.setSessionDate(s != null ? s.getSessionDate() : null);
        ar.setSessionTime(s != null ? s.getTime() : null);
        ar.setSessionStatus(s != null ? s.getSessionStatus() : null);
        ar.setSessionType(s != null ? s.getSessionType() : null);
    }

    /**
     * Completează câmpurile copilului și părintelui în arhivă.
     * <p>
     * GDPR: stocăm snapshot-ul datelor personale la momentul arhivării.
     * Dacă contul de părinte e șters ulterior, arhiva păstrează datele
     * pentru audit și raportare istorică.
     * <p>
     * parentName: concatenare LastName + FirstName, null dacă copilul nu are părinte.
     *
     * @param ar arhiva de completat
     * @param c  copilul asociat attendance-ului (poate fi null pentru date vechi)
     * @param p  părintele copilului (poate fi null dacă copilul nu are părinte)
     */
    private void fillChildParentFields(AttendanceArchive ar, Child c, User p) {
        ar.setChildId(c != null ? c.getIdChild() : null);
        ar.setChildFirstName(c != null ? c.getChildFirstName() : null);
        ar.setChildLastName(c != null ? c.getChildLastName() : null);

        ar.setParentId(p != null ? p.getIdUser() : null);
        ar.setParentName(p != null ? p.getLastName() + " " + p.getFirstName() : null);
        ar.setParentEmail(p != null ? p.getEmail() : null);
        ar.setParentPhone(p != null ? p.getPhone() : null);
    }

    /**
     * Completează câmpurile originale ale attendance-ului și metadata arhivării.
     * <p>
     * Câmpurile originale sunt copiate 1:1 din attendance — nu se modifică nimic.
     * archivedAt: timestamp calculat o dată pentru toată lista (nu LocalDateTime.now()
     * per element) pentru consistență și performanță (evită N apeluri de sistem).
     * <p>
     * recoveryForSessionId: ID-ul sesiunii originale pentru care s-a făcut recuperarea
     * (relevant doar dacă isRecovery=true).
     *
     * @param ar  arhiva de completat
     * @param a   attendance-ul original
     * @param now timestamp arhivării (calculat o dată în archiveAndDeleteFutureAttendance)
     */
    private void fillOriginalFields(AttendanceArchive ar, Attendance a, LocalDateTime now) {
        ar.setAttendanceStatus(a.getStatus());
        ar.setNota(a.getNota());
        ar.setCreatedAt(a.getCreatedAt());
        ar.setUpdatedAt(a.getUpdatedAt());
        ar.setRecovery(a.isRecovery());
        ar.setRecoveryForSessionId(a.getRecoveryForSessionId());
        ar.setArchivedAt(now);
    }
}
