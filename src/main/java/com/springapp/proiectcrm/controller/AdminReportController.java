package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.dto.GroupStatsResponse;
import com.springapp.proiectcrm.service.AdminExcelReportService;
import com.springapp.proiectcrm.service.AdminPdfReportService;
import com.springapp.proiectcrm.service.AdminReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.JRException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controller rapoarte admin — extins cu Excel (Apache POI) și PDF (JasperReports).
 *
 * Endpoint-uri existente (neschimbate):
 *   GET /api/admin/reports/groups       → statistici JSON
 *   GET /api/admin/reports/groups/{id}  → statistici grupă JSON
 *   GET /api/admin/reports/groups/csv   → export CSV
 *
 * Endpoint-uri noi (Faza 4):
 *   GET /api/admin/reports/groups/excel          → Excel complet (4 sheet-uri)
 *   GET /api/admin/reports/groups/pdf/sumar      → PDF sumar grupe
 *   GET /api/admin/reports/groups/{id}/pdf/fisa  → PDF fișă grupă
 *   GET /api/admin/reports/groups/pdf/lunare     → PDF prezențe lunare
 */
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@Slf4j
public class AdminReportController {

    private final AdminReportService      adminReportService;
    private final AdminExcelReportService adminExcelReportService;
    private final AdminPdfReportService   adminPdfReportService;

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ── Endpoint-uri existente (neschimbate) ──────────────────────────────────

    @GetMapping("/groups")
    public List<GroupStatsResponse> getGroupsStats() {
        return adminReportService.getGroupStats();
    }

    @GetMapping("/groups/{groupId}")
    public GroupStatsResponse getGroupStats(@PathVariable int groupId) {
        return adminReportService.getGroupStats(groupId);
    }

    @GetMapping("/groups/csv")
    public ResponseEntity<byte[]> exportGroupsStatsCsv() {
        String csv   = adminReportService.exportGroupStatsAsCsv();
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=group_stats.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    // ── Excel (Apache POI) ────────────────────────────────────────────────────

    /**
     * Export Excel complet cu 4 sheet-uri:
     *   1. Sumar Grupe
     *   2. Prezențe Detaliate
     *   3. Copii per Grupă
     *   4. Statistici Lunare
     */
    @GetMapping("/groups/excel")
    public ResponseEntity<byte[]> exportGroupsExcel() {
        try {
            byte[] bytes    = adminExcelReportService.generateFullReport();
            String filename = "raport_grupe_" + LocalDate.now().format(FILE_DATE) + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(bytes);
        } catch (IOException e) {
            log.error("Eroare generare Excel: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── PDF (JasperReports) ───────────────────────────────────────────────────

    /** PDF sumar toate grupele cu statistici */
    @GetMapping("/groups/pdf/sumar")
    public ResponseEntity<byte[]> exportSumarGrupePdf() {
        try {
            byte[] bytes    = adminPdfReportService.generateSumarGrupePdf();
            String filename = "sumar_grupe_" + LocalDate.now().format(FILE_DATE) + ".pdf";
            return pdfResponse(bytes, filename);
        } catch (JRException e) {
            log.error("Eroare generare PDF sumar: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /** PDF fișă individuală grupă cu lista copiilor */
    @GetMapping("/groups/{groupId}/pdf/fisa")
    public ResponseEntity<byte[]> exportFisaGrupaPdf(@PathVariable int groupId) {
        try {
            byte[] bytes    = adminPdfReportService.generateFisaGrupaPdf(groupId);
            String filename = "fisa_grupa_" + groupId + "_" + LocalDate.now().format(FILE_DATE) + ".pdf";
            return pdfResponse(bytes, filename);
        } catch (JRException e) {
            log.error("Eroare generare PDF fișă grupă {}: {}", groupId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /** PDF raport prezențe lunare */
    @GetMapping("/groups/pdf/lunare")
    public ResponseEntity<byte[]> exportPrezenteLunarePdf() {
        try {
            byte[] bytes    = adminPdfReportService.generatePrezenteLunarePdf();
            String filename = "prezente_lunare_" + LocalDate.now().format(FILE_DATE) + ".pdf";
            return pdfResponse(bytes, filename);
        } catch (JRException e) {
            log.error("Eroare generare PDF prezențe lunare: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ResponseEntity<byte[]> pdfResponse(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
