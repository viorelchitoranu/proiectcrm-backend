package com.springapp.proiectcrm.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entitate care stochează o cerere de înscriere pe lista de așteptare.
 *
 * Scenariul de utilizare:
 *   Părintele completează formularul public de waitlist când nu găsește
 *   locuri disponibile în nicio grupă pentru combinația școală + curs dorită.
 *   Adminul vede toate cererile în panoul de administrare și poate aloca manual
 *   fiecare copil la orice grupă activă din sistem.
 *
 * Design:
 *   Datele de preferință (cursul și școala dorite) sunt stocate ca STRING-uri,
 *   NU ca FK-uri. Motivul: dacă un curs sau o școală este ștearsă din sistem,
 *   intrarea în waitlist rămâne intactă și lizibilă de admin.
 *   Alocarea finală se face la o grupă concretă (FK nullable spre group_class).
 *
 * Mapare tabelă: waitlist_entry
 * SQL de creare: vezi waitlist_migration.sql
 */
@Entity
@Table(name = "waitlist_entry")
@NoArgsConstructor
@Getter
@Setter
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    // ── Date părinte ──────────────────────────────────────────────────────────

    @Column(name = "parent_first_name", nullable = false)
    private String parentFirstName;

    @Column(name = "parent_last_name", nullable = false)
    private String parentLastName;

    // GDPR: emailul este folosit ulterior pentru crearea contului la alocare
    @Column(name = "parent_email", nullable = false)
    private String parentEmail;

    @Column(name = "parent_phone")
    private String parentPhone;

    @Column(name = "parent_address")
    private String parentAddress;

    // ── Date copil ────────────────────────────────────────────────────────────

    @Column(name = "child_first_name", nullable = false)
    private String childFirstName;

    @Column(name = "child_last_name", nullable = false)
    private String childLastName;

    @Column(name = "child_age")
    private Integer childAge;

    @Column(name = "child_school")
    private String childSchool;

    @Column(name = "child_school_class")
    private String childSchoolClass;

    // ── Preferințe (stocate ca text, NU FK — rezistente la ștergeri) ─────────

    @Column(name = "preferred_course_name")
    private String preferredCourseName;

    @Column(name = "preferred_school_name")
    private String preferredSchoolName;

    // Mesaj opțional din partea părintelui (ex: "prefer dimineața", "orice grupă")
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ── Status și alocare ─────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WaitlistStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "allocated_at")
    private LocalDateTime allocatedAt;

    // FK opțional spre group_class — setat doar când status = ALLOCATED
    // Null când status = WAITING sau CANCELLED
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocated_group_id")
    private GroupClass allocatedGroup;
}
