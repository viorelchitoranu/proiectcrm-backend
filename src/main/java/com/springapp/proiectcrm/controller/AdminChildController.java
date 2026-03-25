package com.springapp.proiectcrm.controller;
import com.springapp.proiectcrm.dto.*;
import com.springapp.proiectcrm.service.AdminChildService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST pentru operațiunile admin pe copii.
 *
 * Toate endpoint-urile sunt sub /api/admin/children și necesită ROLE_ADMIN
 * (configurat global în SecurityConfig: .requestMatchers("/api/admin/**").hasRole("ADMIN")).
 *
 * Endpoint-uri existente:
 *   GET  /paged                    → lista paginată cu filtrare
 *   GET  /{childId}                → profil complet cu istoricul grupelor
 *   POST /{childId}/move           → mutare / alocare copil în altă grupă
 *
 * Endpoint-uri noi (dezactivare per-copil):
 *   PATCH /{childId}/deactivate    → dezactivează copilul + eliberează locurile din grupe
 *   PATCH /{childId}/activate      → reactivează copilul (fără re-înscriere automată)
 *
 * De ce PATCH și nu POST?
 *   PATCH = modificare parțială a unei resurse existente (schimbăm doar câmpul active).
 *   POST ar fi pentru crearea unei noi resurse.
 *   Consistență cu endpoint-urile existente pentru părinte: /deactivate și /activate.
 */
@RestController
@RequestMapping("/api/admin/children")
@RequiredArgsConstructor
public class AdminChildController {

    private final AdminChildService adminChildService;

    // ── Endpoint-uri existente ────────────────────────────────────────────────

    /** Lista paginată de copii cu căutare opțională pe nume/prenume/email. */
    @GetMapping("/paged")
    public AdminPagedResponse<AdminChildRowResponse> getChildrenPaged(
            @RequestParam(required = false)          String query,
            @RequestParam(defaultValue = "0")         int    page,
            @RequestParam(defaultValue = "10")        int    size
    ) {
        return adminChildService.getChildren(query, page, size);
    }

    /** Profil complet: date copil + date părinte + istoricul complet al grupelor. */
    @GetMapping("/{childId}")
    public AdminChildDetailsResponse getChildDetails(@PathVariable int childId) {
        return adminChildService.getChildDetails(childId);
    }

    /**
     * Mută copilul dintr-o grupă în alta (sau îl alocă dacă nu era înscris nicăieri).
     * Body: { toGroupId, fromGroupId (opțional), effectiveDate (opțional) }
     */
    @PostMapping("/{childId}/move")
    public AdminMoveChildResponse moveChild(
            @PathVariable int childId,
            @RequestBody  AdminMoveChildRequest request
    ) {
        return adminChildService.moveChild(childId, request);
    }

    // ── Endpoint-uri noi: dezactivare / reactivare per-copil ─────────────────

    /**
     * Dezactivează un copil individual.
     *
     * Efecte:
     *   - Toate ChildGroup-urile active ale copilului → active=false (locuri eliberate)
     *   - child.active → false
     *
     * Răspuns: ChildActivationResponse cu numărul de locuri eliberate.
     * HTTP 200 OK (nu 204) pentru că returnăm date despre operație.
     *
     * Erori posibile:
     *   404 → copilul nu există (CHILD_NOT_FOUND)
     *   400 → copilul este deja dezactivat (CHILD_ALREADY_INACTIVE)
     */
    @PatchMapping("/{childId}/deactivate")
    public ChildActivationResponse deactivateChild(@PathVariable int childId) {
        return adminChildService.deactivateChild(childId);
    }

    /**
     * Reactivează un copil individual.
     *
     * Efecte:
     *   - child.active → true
     *   - ChildGroup-urile NU sunt recreate (re-înscrierea se face manual)
     *
     * Răspuns: ChildActivationResponse cu affectedEnrollments=0.
     *
     * Erori posibile:
     *   404 → copilul nu există (CHILD_NOT_FOUND)
     *   400 → copilul este deja activ (CHILD_ALREADY_ACTIVE)
     */
    @PatchMapping("/{childId}/activate")
    public ChildActivationResponse activateChild(@PathVariable int childId) {
        return adminChildService.activateChild(childId);
    }
}
