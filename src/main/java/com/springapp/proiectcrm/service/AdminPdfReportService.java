package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.model.*;
import com.springapp.proiectcrm.repository.*;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.design.*;
import net.sf.jasperreports.engine.type.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

/**
 * Serviciu pentru generarea rapoartelor PDF cu JasperReports.
 * <p>
 * Generează PDF-uri profesionale fără fișiere .jrxml externe —
 * design-ul e construit programatic în Java pentru portabilitate maximă.
 * <p>
 * Rapoarte disponibile:
 * generateSumarGrupePdf()   → tabel sumar toate grupele cu statistici
 * generateFisaGrupaPdf(id)  → fișă individuală grupă cu lista copiilor
 * generatePrezenteLunare()  → statistici prezențe pe ultimele 6 luni
 */
@Service
@RequiredArgsConstructor
public class AdminPdfReportService {

    private final GroupClassRepository groupClassRepository;
    private final ChildGroupRepository childGroupRepository;
    private final SessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final String GROUPNAME = "groupName";
    private static final String COURSENAME = "courseName";
    private static final String SCHOOLNAME = "schoolName";
    private static final String ENROLLED = "enrolled";
    private static final String TOTAL = "total";
    private static final String TAUGHT = "taught";
    private static final String CANCELED = "canceled";
    private static final String PLANNED = "planned";
    private static final String REPORT_DATE = "REPORT_DATE";


    private static final String CHILDNAME = "childName";
    private static final String SCHOOLCLASS = "schoolClass";
    private static final String PARENTNAME = "parentName";
    private static final String PARENTEMAIL = "parentEmail";
    private static final String PARENTPHONE = "parentPhone";
    private static final String ENROLLDATE = "enrollDate";
    // ── Sumar Grupe ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateSumarGrupePdf() throws JRException {
        List<Map<String, Object>> data = new ArrayList<>();
        List<GroupClass> groups = groupClassRepository.findAll();

        for (GroupClass g : groups) {
            long enrolled = childGroupRepository.countByGroupAndActiveTrue(g);
            long total = sessionRepository.countByGroup(g);
            long taught = sessionRepository.countByGroupAndSessionStatus(g, SessionStatus.TAUGHT);
            long planned = sessionRepository.countByGroupAndSessionStatus(g, SessionStatus.PLANNED);
            long canceled = total - taught - planned;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put(GROUPNAME, g.getGroupName());
            row.put(COURSENAME, g.getCourse() != null ? g.getCourse().getName() : "—");
            row.put(SCHOOLNAME, g.getSchool() != null ? g.getSchool().getName() : "—");
            row.put(ENROLLED, (int) enrolled);
            row.put(TOTAL, (int) total);
            row.put(TAUGHT, (int) taught);
            row.put(CANCELED, (int) canceled);
            row.put(PLANNED, (int) planned);
            data.add(row);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("REPORT_TITLE", "Sumar Grupe");
        params.put(REPORT_DATE, LocalDate.now().format(DATE_FMT));
        params.put("TOTAL_GROUPS", groups.size());

        JasperReport report = buildSumarGroupeReport();
        JasperPrint print = JasperFillManager.fillReport(
                report, params, new JRBeanCollectionDataSource(data));
        return JasperExportManager.exportReportToPdf(print);
    }

    // ── Fișă Grupă Individuală ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateFisaGrupaPdf(int groupId) throws JRException {
        GroupClass g = groupClassRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Grupă negăsită: " + groupId));

        List<ChildGroup> enrollments = childGroupRepository.findByGroupAndActiveTrue(g);
        List<Map<String, Object>> data = new ArrayList<>();

        for (ChildGroup cg : enrollments) {
            data.add(buildFisaRow(cg));
        }

        Map<String, Object> params = new HashMap<>();
        params.put("GROUP_NAME", g.getGroupName());
        params.put("COURSE_NAME", g.getCourse() != null ? g.getCourse().getName() : "—");
        params.put("SCHOOL_NAME", g.getSchool() != null ? g.getSchool().getName() : "—");
        params.put("TOTAL_CHILDREN", enrollments.size());
        params.put(REPORT_DATE, LocalDate.now().format(DATE_FMT));

        JasperReport report = buildFisaGrupaReport();
        JasperPrint print = JasperFillManager.fillReport(
                report, params, new JRBeanCollectionDataSource(data));
        return JasperExportManager.exportReportToPdf(print);
    }

    // ── Prezențe Lunare ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generatePrezenteLunarePdf() throws JRException {
        LocalDate now = LocalDate.now();
        List<Map<String, Object>> data = new ArrayList<>();
        List<GroupClass> groups = groupClassRepository.findAll();

        for (GroupClass g : groups) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(GROUPNAME, g.getGroupName());

            long totalPresent = 0;
            for (int i = 5; i >= 0; i--) {
                LocalDate start = now.minusMonths(i).withDayOfMonth(1);
                LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
                String label = start.getMonth()
                        .getDisplayName(TextStyle.SHORT, new Locale("ro", "RO"))
                        .substring(0, 3);
                List<Session> sessions = sessionRepository
                        .findBySessionDateBetweenAndSessionStatus(start, end, SessionStatus.TAUGHT);
                long present = attendanceRepository.countBySessionIn(sessions);
                row.put("m" + (6 - i), (int) present);
                row.put("label" + (6 - i), label);
                totalPresent += present;
            }
            row.put(TOTAL, (int) totalPresent);
            data.add(row);
        }

        // Label luni pentru header
        Map<String, Object> params = new HashMap<>();
        params.put(REPORT_DATE, LocalDate.now().format(DATE_FMT));
        for (int i = 5; i >= 0; i--) {
            LocalDate m = now.minusMonths(i).withDayOfMonth(1);
            String label = m.getMonth().getDisplayName(TextStyle.SHORT, new Locale("ro", "RO"))
                    + " " + m.getYear();
            params.put("LABEL_M" + (6 - i), label);
        }

        JasperReport report = buildPrezenteLunareReport();
        JasperPrint print = JasperFillManager.fillReport(
                report, params, new JRBeanCollectionDataSource(data));
        return JasperExportManager.exportReportToPdf(print);
    }

    // ── Helper — construire rând fișă copil ───────────────────────────────────

    private Map<String, Object> buildFisaRow(ChildGroup cg) {
        Child child  = cg.getChild();
        User  parent = child.getParent();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(CHILDNAME,   child.getChildLastName() + " " + child.getChildFirstName());
        row.put("age",       child.getAge() != null ? child.getAge().toString() : "—");
        row.put(SCHOOLCLASS, child.getSchoolClass() != null ? child.getSchoolClass() : "—");
        row.put(PARENTNAME,  parent != null ? parent.getFirstName() + " " + parent.getLastName() : "—");
        row.put(PARENTEMAIL, parent != null && parent.getEmail() != null ? parent.getEmail() : "—");
        row.put(PARENTPHONE, parent != null && parent.getPhone() != null ? parent.getPhone() : "—");
        row.put(ENROLLDATE,  cg.getEnrollmentDate() != null ? cg.getEnrollmentDate().format(DATE_FMT) : "—");
        return row;
    }

    // ── Constructori rapoarte JasperReports (programatic) ────────────────────

    private JasperReport buildSumarGroupeReport() throws JRException {
        JasperDesign design = createBaseDesign("Sumar Grupe", 842, 595); // A4 landscape

        // Parameters
        addParam(design, "REPORT_TITLE", String.class);
        addParam(design, REPORT_DATE, String.class);
        addParam(design, "TOTAL_GROUPS", Integer.class);

        // Fields
        String[] fields = {GROUPNAME, COURSENAME, SCHOOLNAME, ENROLLED, TOTAL, TAUGHT, CANCELED, PLANNED};
        Class<?>[] types = {String.class, String.class, String.class, Integer.class, Integer.class, Integer.class, Integer.class, Integer.class};
        for (int i = 0; i < fields.length; i++) addField(design, fields[i], types[i]);

        // Title band
        JRDesignBand titleBand = new JRDesignBand();
        titleBand.setHeight(60);
        addStaticText(titleBand, "Sumar Grupe — Platforma CRM", 0, 5, 820, 25, 16, true);
        addTextFieldParam(titleBand, "\"Data generării: \" + $P{REPORT_DATE}", 0, 30, 400, 18, 10, false);
        addTextFieldParam(titleBand, "\"Total grupe: \" + $P{TOTAL_GROUPS}", 0, 48, 300, 12, 9, false);
        design.setTitle(titleBand);

        // Column header
        JRDesignBand colHeader = new JRDesignBand();
        colHeader.setHeight(22);
        int[] widths = {160, 130, 130, 60, 70, 60, 60, 70};
        String[] colLabels = {"Grupă", "Curs", "Școală", "Înscriși", "Total Ses.", "Ținute", "Anulate", "Planificate"};
        int x = 10;
        for (int i = 0; i < colLabels.length; i++) {
            addStaticHeader(colHeader, colLabels[i], x, 0, widths[i], 20);
            x += widths[i] + 5;
        }
        design.setColumnHeader(colHeader);

        // Detail band
        JRDesignBand detail = new JRDesignBand();
        detail.setHeight(18);
        String[] fieldNames = {GROUPNAME, COURSENAME, SCHOOLNAME, ENROLLED, TOTAL, TAUGHT, CANCELED, PLANNED};
        x = 10;
        for (int i = 0; i < fieldNames.length; i++) {
            addFieldCell(detail, fieldNames[i], x, 0, widths[i], 16);
            x += widths[i] + 5;
        }
        ((JRDesignSection) design.getDetailSection()).addBand(detail);

        return JasperCompileManager.compileReport(design);
    }

    private JasperReport buildFisaGrupaReport() throws JRException {
        JasperDesign design = createBaseDesign("Fișă Grupă", 595, 842); // A4 portrait

        addParam(design, "GROUP_NAME", String.class);
        addParam(design, "COURSE_NAME", String.class);
        addParam(design, "SCHOOL_NAME", String.class);
        addParam(design, "TOTAL_CHILDREN", Integer.class);
        addParam(design, REPORT_DATE, String.class);

        String[] fields = {CHILDNAME, "age", SCHOOLCLASS, PARENTNAME, PARENTEMAIL, PARENTPHONE, ENROLLDATE};
        for (String f : fields) addField(design, f, String.class);

        JRDesignBand title = new JRDesignBand();
        title.setHeight(80);
        addStaticText(title, "Fișă Grupă", 0, 0, 555, 22, 14, true);
        addTextFieldParam(title, "$P{GROUP_NAME} + \" — \" + $P{COURSE_NAME}", 0, 22, 555, 18, 11, true);
        addTextFieldParam(title, "\"Școală: \" + $P{SCHOOL_NAME}", 0, 42, 300, 14, 9, false);
        addTextFieldParam(title, "\"Copii înscriși: \" + $P{TOTAL_CHILDREN}", 0, 58, 200, 14, 9, false);
        addTextFieldParam(title, "\"Data: \" + $P{REPORT_DATE}", 350, 58, 200, 14, 9, false);
        design.setTitle(title);

        JRDesignBand colHeader = new JRDesignBand();
        colHeader.setHeight(20);
        int[] widths = {130, 30, 40, 110, 130, 80, 70};
        String[] labels = {"Copil", "Vârstă", "Clasă", "Părinte", "Email Părinte", "Telefon", "Înscris"};
        int x = 5;
        for (int i = 0; i < labels.length; i++) {
            addStaticHeader(colHeader, labels[i], x, 0, widths[i], 18);
            x += widths[i] + 3;
        }
        design.setColumnHeader(colHeader);

        JRDesignBand detail = new JRDesignBand();
        detail.setHeight(16);
        String[] flds = {CHILDNAME, "age", SCHOOLCLASS, PARENTNAME, PARENTEMAIL, PARENTPHONE, ENROLLDATE};
        x = 5;
        for (int i = 0; i < flds.length; i++) {
            addFieldCell(detail, flds[i], x, 0, widths[i], 14);
            x += widths[i] + 3;
        }
        ((JRDesignSection) design.getDetailSection()).addBand(detail);

        return JasperCompileManager.compileReport(design);
    }

    private JasperReport buildPrezenteLunareReport() throws JRException {
        JasperDesign design = createBaseDesign("Prezențe Lunare", 842, 595);

        addParam(design, REPORT_DATE, String.class);
        for (int i = 1; i <= 6; i++) addParam(design, "LABEL_M" + i, String.class);

        addField(design, GROUPNAME, String.class);
        for (int i = 1; i <= 6; i++) addField(design, "m" + i, Integer.class);
        addField(design, TOTAL, Integer.class);

        JRDesignBand title = new JRDesignBand();
        title.setHeight(50);
        addStaticText(title, "Statistici Lunare Prezențe — Ultimele 6 Luni", 0, 5, 820, 22, 14, true);
        addTextFieldParam(title, "\"Generat: \" + $P{REPORT_DATE}", 0, 30, 300, 15, 9, false);
        design.setTitle(title);

        JRDesignBand colHeader = new JRDesignBand();
        colHeader.setHeight(20);
        addStaticHeader(colHeader, "Grupă", 10, 0, 160, 18);
        int[] mWidths = {90, 90, 90, 90, 90, 90};
        int x = 175;
        for (int i = 1; i <= 6; i++) {
            addTextFieldParamHeader(colHeader, "$P{LABEL_M" + i + "}", x, 0, mWidths[i - 1], 18);
            x += mWidths[i - 1] + 5;
        }
        addStaticHeader(colHeader, TOTAL, x, 0, 60, 18);
        design.setColumnHeader(colHeader);

        JRDesignBand detail = new JRDesignBand();
        detail.setHeight(16);
        addFieldCell(detail, GROUPNAME, 10, 0, 160, 14);
        x = 175;
        for (int i = 1; i <= 6; i++) {
            addFieldCell(detail, "m" + i, x, 0, mWidths[i - 1], 14);
            x += mWidths[i - 1] + 5;
        }
        addFieldCell(detail, TOTAL, x, 0, 60, 14);
        ((JRDesignSection) design.getDetailSection()).addBand(detail);

        return JasperCompileManager.compileReport(design);
    }

    // ── Helpers JasperReports ─────────────────────────────────────────────────

    private JasperDesign createBaseDesign(String name, int pageWidth, int pageHeight) {
        JasperDesign d = new JasperDesign();
        d.setName(name);
        d.setPageWidth(pageWidth);
        d.setPageHeight(pageHeight);
        d.setLeftMargin(20);
        d.setRightMargin(20);
        d.setTopMargin(20);
        d.setBottomMargin(20);
        d.setColumnWidth(pageWidth - 40);
        return d;
    }

    private void addParam(JasperDesign d, String name, Class<?> clazz) throws JRException {
        JRDesignParameter p = new JRDesignParameter();
        p.setName(name);
        p.setValueClass(clazz);
        d.addParameter(p);
    }

    private void addField(JasperDesign d, String name, Class<?> clazz) throws JRException {
        JRDesignField f = new JRDesignField();
        f.setName(name);
        f.setValueClass(clazz);
        d.addField(f);
    }

    private void addStaticText(JRDesignBand band, String text, int x, int y, int w, int h, int fontSize, boolean bold) {
        JRDesignStaticText st = new JRDesignStaticText();
        st.setText(text);
        st.setX(x);
        st.setY(y);
        st.setWidth(w);
        st.setHeight(h);
        st.setFontSize((float) fontSize);
        st.setBold(bold);
        band.addElement(st);
    }

    private void addStaticHeader(JRDesignBand band, String text, int x, int y, int w, int h) {
        JRDesignStaticText st = new JRDesignStaticText();
        st.setText(text);
        st.setX(x);
        st.setY(y);
        st.setWidth(w);
        st.setHeight(h);
        st.setFontSize(8f);
        st.setBold(true);
        st.setBackcolor(new java.awt.Color(0x33, 0x66, 0x99));
        st.setForecolor(java.awt.Color.WHITE);
        st.setMode(ModeEnum.OPAQUE);
        st.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
        band.addElement(st);
    }

    private void addTextFieldParam(JRDesignBand band, String expr, int x, int y, int w, int h, int fontSize, boolean bold) {
        JRDesignTextField tf = new JRDesignTextField();
        JRDesignExpression exp = new JRDesignExpression();
        exp.setText(expr);
        tf.setExpression(exp);
        tf.setX(x);
        tf.setY(y);
        tf.setWidth(w);
        tf.setHeight(h);
        tf.setFontSize((float) fontSize);
        tf.setBold(bold);
        band.addElement(tf);
    }

    private void addTextFieldParamHeader(JRDesignBand band, String expr, int x, int y, int w, int h) {
        JRDesignTextField tf = new JRDesignTextField();
        JRDesignExpression exp = new JRDesignExpression();
        exp.setText(expr);
        tf.setExpression(exp);
        tf.setX(x);
        tf.setY(y);
        tf.setWidth(w);
        tf.setHeight(h);
        tf.setFontSize(8f);
        tf.setBold(true);
        tf.setBackcolor(new java.awt.Color(0x33, 0x66, 0x99));
        tf.setForecolor(java.awt.Color.WHITE);
        tf.setMode(ModeEnum.OPAQUE);
        tf.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
        band.addElement(tf);
    }

    private void addFieldCell(JRDesignBand band, String fieldName, int x, int y, int w, int h) {
        JRDesignTextField tf = new JRDesignTextField();
        JRDesignExpression exp = new JRDesignExpression();
        exp.setText("$F{" + fieldName + "}");
        tf.setExpression(exp);
        tf.setX(x);
        tf.setY(y);
        tf.setWidth(w);
        tf.setHeight(h);
        tf.setFontSize(8f);
        band.addElement(tf);
    }
}
