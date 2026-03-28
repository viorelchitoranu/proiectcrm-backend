-- ============================================================
-- V4__email_templates.sql
-- Tabelă pentru template-uri email editabile din interfața admin.
--
-- Fiecare template are un cod unic (EMAIL_TYPE) care corespunde
-- unui tip de email trimis de platformă.
--
-- Variabile disponibile în subject și body (înlocuite automat):
--   {{firstName}}          → prenumele destinatarului
--   {{lastName}}           → numele destinatarului
--   {{email}}              → emailul destinatarului
--   {{password}}           → parola temporară (doar la creare cont)
--   {{groupName}}          → numele grupei (waitlist, înscrieri)
--   {{newEmail}}           → noua adresă email (la schimbare email)
--   {{releasedEnrollments}} → numărul de grupe eliberate (la dezactivare)
--   {{platformName}}       → numele platformei (din TenantConfig)
--   {{teamName}}           → numele echipei (din TenantConfig)
-- ============================================================

CREATE TABLE IF NOT EXISTS `email_template` (
    `id`               INT          NOT NULL AUTO_INCREMENT,
    `code`             VARCHAR(64)  NOT NULL COMMENT 'Cod unic al template-ului (ex: PARENT_CREDENTIALS)',
    `name`             VARCHAR(128) NOT NULL COMMENT 'Nume afișat în interfața admin',
    `subject`          VARCHAR(255) NOT NULL COMMENT 'Subiectul emailului — poate conține variabile {{}}',
    `body`             TEXT         NOT NULL COMMENT 'Corpul emailului — poate conține variabile {{}}',
    `available_vars`   VARCHAR(512) NOT NULL COMMENT 'Lista variabilelor disponibile — afișată în UI ca hint',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     NULL     ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_email_template_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Template-uri email editabile din interfața admin';

-- ── Date inițiale — template-uri default ─────────────────────────────────────
-- Acestea sunt template-urile implicite. Adminul le poate modifica din UI.
-- La resetare, adminul poate șterge rândul și repopula din această migrare.

INSERT INTO `email_template` (`code`, `name`, `subject`, `body`, `available_vars`) VALUES

('PARENT_CREDENTIALS',
 'Cont părinte creat',
 'Contul tău de părinte a fost creat — {{platformName}}',
 'Bună {{firstName}} {{lastName}},\n\nȚi-a fost creat un cont de părinte în platforma {{platformName}}.\n\nDate de autentificare:\n- Email: {{email}}\n- Parolă: {{password}}\n\nÎți recomandăm să îți schimbi parola după primul login, din secțiunea „Profil".\n\nCu drag,\n{{teamName}}',
 '{{firstName}}, {{lastName}}, {{email}}, {{password}}, {{platformName}}, {{teamName}}'),

('PARENT_PASSWORD_RESET',
 'Parolă părinte resetată',
 'Parola contului tău a fost schimbată — {{platformName}}',
 'Bună {{firstName}} {{lastName}},\n\nParola contului tău de părinte în platforma {{platformName}} a fost actualizată.\n\nNoua parolă este:\n- {{password}}\n\nDacă nu ai inițiat tu această schimbare, te rugăm să ne contactezi cât mai curând.\n\nCu drag,\n{{teamName}}',
 '{{firstName}}, {{lastName}}, {{password}}, {{platformName}}, {{teamName}}'),

('PARENT_RESET_OWN_PASSWORD',
 'Resetare parolă de către părinte',
 'Parola ta a fost modificată — {{platformName}}',
 'Bună {{firstName}} {{lastName}},\n\nParola contului tău din platforma {{platformName}} a fost modificată cu succes.\n\nDacă nu ai inițiat tu această schimbare, te rugăm să ne contactezi imediat.\n\nCu drag,\n{{teamName}}',
 '{{firstName}}, {{lastName}}, {{platformName}}, {{teamName}}'),

('TEACHER_CREDENTIALS',
 'Cont profesor creat',
 'Cont profesor creat în {{platformName}}',
 'Bună {{firstName}} {{lastName}},\n\nȚi-a fost creat un cont de profesor în platforma {{platformName}}.\n\nDate de autentificare:\n- Email: {{email}}\n- Parolă: {{password}}\n\nTe rugăm ca după prima autentificare să îți schimbi parola din secțiunea „Profil".\n\nToate cele bune,\n{{teamName}}',
 '{{firstName}}, {{lastName}}, {{email}}, {{password}}, {{platformName}}, {{teamName}}'),

('TEACHER_PASSWORD_RESET',
 'Parolă profesor resetată',
 'Parola ta a fost resetată — {{platformName}}',
 'Bună {{firstName}} {{lastName}},\n\nParola contului tău de profesor în platforma {{platformName}} a fost resetată de un administrator.\n\nNoua parolă este:\n- {{password}}\n\nTe rugăm ca după autentificare să o modifici din secțiunea „Profil".\n\nToate cele bune,\n{{teamName}}',
 '{{firstName}}, {{lastName}}, {{password}}, {{platformName}}, {{teamName}}'),

('EMAIL_CHANGED',
 'Notificare schimbare email',
 'Adresa ta de email a fost modificată — {{platformName}}',
 'Bună {{firstName}},\n\nUn administrator a modificat adresa de email asociată contului tău în platforma {{platformName}}.\n\nNoua adresă de email este: {{newEmail}}\n\nDe acum înainte, folosește această adresă pentru a te autentifica în platformă.\n\nDacă nu ai autorizat această modificare, te rugăm să ne contactezi cât mai curând.\n\nCu drag,\n{{teamName}}',
 '{{firstName}}, {{newEmail}}, {{platformName}}, {{teamName}}'),

('WAITLIST_ALLOCATED_NEW_ACCOUNT',
 'Alocat din waitlist — cont nou',
 'Ai fost înscris în {{platformName}} și alocat la o grupă!',
 'Bună {{firstName}} {{lastName}},\n\nCererea ta de pe lista de așteptare a fost procesată.\nUn administrator te-a alocat la grupa: {{groupName}}\n\nȚi-a fost creat un cont de părinte cu următoarele date de autentificare:\n- Email: {{email}}\n- Parolă temporară: {{password}}\n\nTe rugăm să te autentifici și să îți schimbi parola din secțiunea „Profil".\n\nCu drag,\n{{teamName}}',
 '{{firstName}}, {{lastName}}, {{email}}, {{password}}, {{groupName}}, {{platformName}}, {{teamName}}'),

('WAITLIST_ALLOCATED_EXISTING_ACCOUNT',
 'Alocat din waitlist — cont existent',
 'Copilul tău a fost alocat la o grupă — {{platformName}}',
 'Bună {{firstName}} {{lastName}},\n\nCererea ta de pe lista de așteptare a fost procesată.\nUn administrator a alocat copilul tău la grupa: {{groupName}}\n\nTe poți autentifica cu datele existente pentru a vedea detaliile.\n\nCu drag,\n{{teamName}}',
 '{{firstName}}, {{lastName}}, {{groupName}}, {{platformName}}, {{teamName}}');
