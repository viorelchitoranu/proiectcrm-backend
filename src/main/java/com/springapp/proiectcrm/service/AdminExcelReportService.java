package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.model.*;
import com.springapp.proiectcrm.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Serviciu pentru generarea rapoartelor Excel complexe cu Apache POI.
 *
 * Conținut workbook (4 sheet-uri):
 *   1. Sumar Grupe      — statistici per grupă (sesiuni, prezențe, înscriși)
 *   2. Prezențe Detalii — fiecare sesiune × copil cu status prezență
 *   3. Copii per Grupă  — lista copiilor cu date contact părinte
 *   4. Statistici Lunare— prezențe agregate pe ultimele 6 luni
 */
@Service
@RequiredArgsConstructor
public class AdminExcelReportService {

    private final GroupClassRepository  groupClassRepository;
    private final ChildGroupRepository  childGroupRepository;
    private final SessionRepository     sessionRepository;
    private final AttendanceRepository  attendanceRepository;

    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("ro", "RO"));

    private static final String[] PREZENTA_COLS = {"Copil", "Data Sesiunii", "Ora", "Status Prezență", "Observații"};
    private static final String[] COPII_COLS    = {"Copil", "Vârstă", "Clasă", "Email Părinte", "Telefon Părinte", "Data Înscrierii"};

    @Transactional(readOnly = true)
    public byte[] generateFullReport() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyles styles = new CellStyles(wb);

            buildSumarGrupe(wb, styles);
            buildPrezentaDetaliata(wb, styles);
            buildCopiiPerGrupa(wb, styles);
            buildStatisticiLunare(wb, styles);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── Sheet 1: Sumar Grupe ──────────────────────────────────────────────────

    private void buildSumarGrupe(XSSFWorkbook wb, CellStyles styles) {
        Sheet sheet = wb.createSheet("Sumar Grupe");
        sheet.setColumnWidth(0, 6000);
        sheet.setColumnWidth(1, 5000);
        sheet.setColumnWidth(2, 5000);
        sheet.setColumnWidth(3, 3000);
        sheet.setColumnWidth(4, 3000);
        sheet.setColumnWidth(5, 3000);
        sheet.setColumnWidth(6, 3000);
        sheet.setColumnWidth(7, 3000);

        int row = 0;

        Row titleRow = sheet.createRow(row++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Raport Sumar Grupe — " + LocalDate.now().format(DATE_FMT));
        titleCell.setCellStyle(styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

        row++;

        Row headerRow = sheet.createRow(row++);
        String[] headers = {"Grupă", "Curs", "Școală", "Înscriși", "Total Sesiuni", "Ținute", "Anulate", "Planificate"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header);
        }

        List<GroupClass> groups = groupClassRepository.findAll();
        boolean alternate = false;
        for (GroupClass g : groups) {
            Row dataRow = sheet.createRow(row++);
            CellStyle rowStyle = alternate ? styles.dataAlt : styles.data;
            alternate = !alternate;

            long enrolled = childGroupRepository.countByGroupAndActiveTrue(g);
            long total    = sessionRepository.countByGroup(g);
            long taught   = sessionRepository.countByGroupAndSessionStatus(g, SessionStatus.TAUGHT);
            long planned  = sessionRepository.countByGroupAndSessionStatus(g, SessionStatus.PLANNED);
            long canceled = total - taught - planned;

            setCellValue(dataRow, 0, g.getGroupName(), rowStyle);
            setCellValue(dataRow, 1, g.getCourse() != null ? g.getCourse().getName() : "—", rowStyle);
            setCellValue(dataRow, 2, g.getSchool()  != null ? g.getSchool().getName()  : "—", rowStyle);
            setCellNumeric(dataRow, 3, enrolled,  rowStyle);
            setCellNumeric(dataRow, 4, total,     rowStyle);
            setCellNumeric(dataRow, 5, taught,    rowStyle);
            setCellNumeric(dataRow, 6, canceled,  rowStyle);
            setCellNumeric(dataRow, 7, planned,   rowStyle);
        }

        sheet.createFreezePane(0, 3);
        sheet.setAutoFilter(new CellRangeAddress(2, 2, 0, 7));
    }

    // ── Sheet 2: Prezențe Detaliate ───────────────────────────────────────────

    private void buildPrezentaDetaliata(XSSFWorkbook wb, CellStyles styles) {
        Sheet sheet = wb.createSheet("Prezențe Detaliate");
        sheet.setColumnWidth(0, 5000);
        sheet.setColumnWidth(1, 4000);
        sheet.setColumnWidth(2, 4000);
        sheet.setColumnWidth(3, 3500);
        sheet.setColumnWidth(4, 3500);

        int row = 0;

        Row titleRow = sheet.createRow(row++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Prezențe Detaliate per Grupă — " + LocalDate.now().format(DATE_FMT));
        titleCell.setCellStyle(styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));
        row++;

        List<GroupClass> groups = groupClassRepository.findAll();
        for (GroupClass g : groups) {
            row = writePrezentaGrupa(sheet, styles, g, row);
            row++;
        }

        sheet.createFreezePane(0, 1);
    }

    // ── Extras din buildPrezentaDetaliata pentru a reduce Cognitive Complexity ─

    private int writePrezentaGrupa(Sheet sheet, CellStyles styles, GroupClass g, int row) {
        // Sub-titlu grupă
        Row groupRow = sheet.createRow(row++);
        Cell groupCell = groupRow.createCell(0);
        groupCell.setCellValue(g.getGroupName()
                + (g.getCourse() != null ? " — " + g.getCourse().getName() : ""));
        groupCell.setCellStyle(styles.subTitle);
        sheet.addMergedRegion(new CellRangeAddress(row - 1, row - 1, 0, 4));

        // Header coloane
        row = writeHeaderRow(sheet, styles.headerSmall, PREZENTA_COLS, row);

        // Sesiunile grupei
        List<Session> sessions = sessionRepository.findByGroupOrderBySessionDateAsc(g);
        for (Session s : sessions) {
            row = writePrezentaSession(sheet, styles, s, row);
        }
        return row;
    }

    private int writePrezentaSession(Sheet sheet, CellStyles styles, Session s, int row) {
        List<Attendance> attendances = attendanceRepository.findBySession(s);
        if (attendances.isEmpty()) {
            Row r = sheet.createRow(row++);
            setCellValue(r, 0, "—", styles.data);
            setCellValue(r, 1, s.getSessionDate().format(DATE_FMT), styles.data);
            setCellValue(r, 2, s.getTime().toString(), styles.data);
            setCellValue(r, 3, s.getSessionStatus().name(), styles.data);
            setCellValue(r, 4, "", styles.data);
        } else {
            for (Attendance a : attendances) {
                row = writePrezentaAttendance(sheet, styles, s, a, row);
            }
        }
        return row;
    }

    private int writePrezentaAttendance(Sheet sheet, CellStyles styles, Session s, Attendance a, int row) {
        Row r = sheet.createRow(row++);
        String childName = a.getChild().getChildLastName() + " " + a.getChild().getChildFirstName();
        setCellValue(r, 0, childName, styles.data);
        setCellValue(r, 1, s.getSessionDate().format(DATE_FMT), styles.data);
        setCellValue(r, 2, s.getTime().toString(), styles.data);
        setCellValue(r, 3, a.getStatus().name(), getAttendanceStyle(styles, a.getStatus()));
        setCellValue(r, 4, a.getNota() != null ? a.getNota() : "", styles.data);
        return row;
    }

    // ── Sheet 3: Copii per Grupă ──────────────────────────────────────────────

    private void buildCopiiPerGrupa(XSSFWorkbook wb, CellStyles styles) {
        Sheet sheet = wb.createSheet("Copii per Grupă");
        sheet.setColumnWidth(0, 5000);
        sheet.setColumnWidth(1, 4000);
        sheet.setColumnWidth(2, 2500);
        sheet.setColumnWidth(3, 5500);
        sheet.setColumnWidth(4, 4000);
        sheet.setColumnWidth(5, 4000);

        int row = 0;

        Row titleRow = sheet.createRow(row++);
        Cell tc = titleRow.createCell(0);
        tc.setCellValue("Listă Copii per Grupă — " + LocalDate.now().format(DATE_FMT));
        tc.setCellStyle(styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
        row++;

        List<GroupClass> groups = groupClassRepository.findAll();
        for (GroupClass g : groups) {
            List<ChildGroup> enrollments = childGroupRepository.findByGroupAndActiveTrue(g);
            if (enrollments.isEmpty()) continue;
            row = writeCopiiGrupa(sheet, styles, g, enrollments, row);
            row++;
        }

        sheet.createFreezePane(0, 1);
    }

    // ── Extras din buildCopiiPerGrupa pentru a reduce Cognitive Complexity ─────

    private int writeCopiiGrupa(Sheet sheet, CellStyles styles, GroupClass g,
                                 List<ChildGroup> enrollments, int row) {
        // Sub-titlu
        Row groupRow = sheet.createRow(row++);
        Cell gc = groupRow.createCell(0);
        gc.setCellValue(buildGroupLabel(g));
        gc.setCellStyle(styles.subTitle);
        sheet.addMergedRegion(new CellRangeAddress(row - 1, row - 1, 0, 5));

        // Header
        row = writeHeaderRow(sheet, styles.headerSmall, COPII_COLS, row);

        // Copii
        boolean alt = false;
        for (ChildGroup cg : enrollments) {
            CellStyle s = alt ? styles.dataAlt : styles.data;
            alt = !alt;
            row = writeCopilRow(sheet, cg, s, row);
        }
        return row;
    }

    private String buildGroupLabel(GroupClass g) {
        StringBuilder sb = new StringBuilder(g.getGroupName());
        if (g.getCourse() != null) sb.append(" — ").append(g.getCourse().getName());
        if (g.getSchool()  != null) sb.append(" / ").append(g.getSchool().getName());
        return sb.toString();
    }

    private int writeCopilRow(Sheet sheet, ChildGroup cg, CellStyle style, int row) {
        Child child  = cg.getChild();
        User  parent = child.getParent();
        Row r = sheet.createRow(row++);

        setCellValue(r, 0, child.getChildLastName() + " " + child.getChildFirstName(), style);
        setCellNumeric(r, 1, child.getAge() != null ? child.getAge() : 0, style);
        setCellValue(r, 2, child.getSchoolClass() != null ? child.getSchoolClass() : "—", style);
        setCellValue(r, 3, parent != null && parent.getEmail() != null ? parent.getEmail() : "—", style);
        setCellValue(r, 4, parent != null && parent.getPhone() != null ? parent.getPhone() : "—", style);
        setCellValue(r, 5, cg.getEnrollmentDate() != null ? cg.getEnrollmentDate().format(DATE_FMT) : "—", style);
        return row;
    }

    // ── Sheet 4: Statistici Lunare ────────────────────────────────────────────

    private void buildStatisticiLunare(XSSFWorkbook wb, CellStyles styles) {
        Sheet sheet = wb.createSheet("Statistici Lunare");
        sheet.setColumnWidth(0, 5000);
        for (int i = 1; i <= 6; i++) sheet.setColumnWidth(i, 3500);

        int row = 0;

        Row titleRow = sheet.createRow(row++);
        Cell tc = titleRow.createCell(0);
        tc.setCellValue("Statistici Lunare Prezențe — Ultimele 6 Luni");
        tc.setCellStyle(styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));
        row++;

        LocalDate now = LocalDate.now();
        LocalDate[] monthStarts = new LocalDate[6];
        String[]    monthLabels = new String[6];
        for (int i = 5; i >= 0; i--) {
            LocalDate m = now.minusMonths(i).withDayOfMonth(1);
            monthStarts[5 - i] = m;
            monthLabels[5 - i] = m.getMonth().getDisplayName(TextStyle.SHORT, new Locale("ro", "RO"))
                    + " " + m.getYear();
        }

        Row hRow = sheet.createRow(row++);
        Cell ghCell = hRow.createCell(0);
        ghCell.setCellValue("Grupă");
        ghCell.setCellStyle(styles.header);
        for (int i = 0; i < 6; i++) {
            Cell c = hRow.createCell(i + 1);
            c.setCellValue(monthLabels[i]);
            c.setCellStyle(styles.header);
        }

        List<GroupClass> groups = groupClassRepository.findAll();
        boolean alt = false;
        for (GroupClass g : groups) {
            Row r = sheet.createRow(row++);
            CellStyle s = alt ? styles.dataAlt : styles.data;
            alt = !alt;

            setCellValue(r, 0, g.getGroupName(), s);

            for (int i = 0; i < 6; i++) {
                LocalDate start   = monthStarts[i];
                LocalDate end     = start.withDayOfMonth(start.lengthOfMonth());
                List<Session> ses = sessionRepository
                        .findBySessionDateBetweenAndSessionStatus(start, end, SessionStatus.TAUGHT);
                long present = attendanceRepository.countBySessionIn(ses);
                setCellNumeric(r, i + 1, present, s);
            }
        }

        sheet.createFreezePane(1, 3);
    }

    // ── Helper comun — scriere rând header ────────────────────────────────────

    private int writeHeaderRow(Sheet sheet, CellStyle style, String[] cols, int row) {
        Row hRow = sheet.createRow(row++);
        for (int i = 0; i < cols.length; i++) {
            Cell c = hRow.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(style);
        }
        return row;
    }

    // ── Helpers celule ────────────────────────────────────────────────────────

    private void setCellValue(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private void setCellNumeric(Row row, int col, long value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private CellStyle getAttendanceStyle(CellStyles styles, AttendanceStatus status) {
        return switch (status) {
            case PRESENT -> styles.present;
            case ABSENT  -> styles.absent;
            default      -> styles.data;
        };
    }

    // ── Stiluri centralizate ──────────────────────────────────────────────────

    private static class CellStyles {
        final CellStyle title, subTitle, header, headerSmall, data, dataAlt, present, absent;

        CellStyles(XSSFWorkbook wb) {
            Font boldLarge  = createFont(wb, true,  14, IndexedColors.DARK_BLUE);
            Font boldMedium = createFont(wb, true,  11, IndexedColors.DARK_BLUE);
            Font boldWhite  = createFont(wb, true,  10, IndexedColors.WHITE);
            Font boldSmall  = createFont(wb, true,   9, IndexedColors.WHITE);
            Font regular    = createFont(wb, false,  9, IndexedColors.BLACK);

            title       = buildStyle(wb, boldLarge,  IndexedColors.LIGHT_CORNFLOWER_BLUE, HorizontalAlignment.LEFT,   true);
            subTitle    = buildStyle(wb, boldMedium, IndexedColors.PALE_BLUE,             HorizontalAlignment.LEFT,   true);
            header      = buildStyle(wb, boldWhite,  IndexedColors.DARK_BLUE,             HorizontalAlignment.CENTER, true);
            headerSmall = buildStyle(wb, boldSmall,  IndexedColors.CORNFLOWER_BLUE,       HorizontalAlignment.CENTER, true);
            data        = buildStyle(wb, regular,    IndexedColors.WHITE,                 HorizontalAlignment.LEFT,   true);
            dataAlt     = buildStyle(wb, regular,    IndexedColors.LEMON_CHIFFON,         HorizontalAlignment.LEFT,   true);
            present     = buildStyle(wb, regular,    IndexedColors.LIGHT_GREEN,           HorizontalAlignment.CENTER, true);
            absent      = buildStyle(wb, regular,    IndexedColors.ROSE,                  HorizontalAlignment.CENTER, true);
        }

        private Font createFont(XSSFWorkbook wb, boolean bold, int size, IndexedColors color) {
            Font f = wb.createFont();
            f.setBold(bold);
            f.setFontHeightInPoints((short) size);
            f.setColor(color.getIndex());
            return f;
        }

        private CellStyle buildStyle(XSSFWorkbook wb, Font font, IndexedColors bg,
                                     HorizontalAlignment align, boolean border) {
            CellStyle s = wb.createCellStyle();
            s.setFont(font);
            s.setFillForegroundColor(bg.getIndex());
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            s.setAlignment(align);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            if (border) {
                s.setBorderBottom(BorderStyle.THIN);
                s.setBorderTop(BorderStyle.THIN);
                s.setBorderLeft(BorderStyle.THIN);
                s.setBorderRight(BorderStyle.THIN);
            }
            s.setWrapText(false);
            return s;
        }
    }
}
