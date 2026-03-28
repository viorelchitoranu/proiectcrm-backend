-- ============================================================
-- V2__billing_placeholder.sql
-- Placeholder structură billing — creat înainte de implementarea completă.
--
-- SCOP:
--   Rezervă spațiu în schemă pentru billing fără nicio logică de business.
--   Toate câmpurile din child_group sunt nullable → zero impact pe codul existent.
--   Tabela payments este goală și nu e folosită de niciun endpoint încă.
--
-- REVIEW NECESAR CU CLIENTUL ÎNAINTE DE IMPLEMENTARE:
--   - Model tarifare: PER_SESSION / MONTHLY / PACKAGE?
--   - Cine plătește și cum: online (Stripe/PayU) sau manual (cash/transfer)?
--   - Ce se întâmplă la neplată: blocare automată sau notificare?
--   - Grace period: câte zile după scadență?
--   - Facturi: automate sau manuale?
--
-- Când se decide modelul, V3__billing_logic.sql va adăuga:
--   - Constraints, indexes, logică status
--   - Integrare payment gateway (dacă se alege online)
--   - Triggers / jobs pentru reminder neplată
-- ============================================================

-- ── Câmpuri billing în child_group ───────────────────────────────────────────
-- Adăugate la nivelul înscrierii — fiecare copil poate avea tarif diferit
-- față de prețul default al grupei (ex: reduceri, pachete speciale).

ALTER TABLE `child_group`
    -- Tipul de tarifare ales pentru această înscriere
    -- NULL = nesetat încă (backward compatible cu înregistrările existente)
    ADD COLUMN `billing_type` ENUM('PER_SESSION', 'MONTHLY', 'PACKAGE') NULL
        COMMENT 'Modelul de tarifare: per sesiune, abonament lunar sau pachet prepaid'
        AFTER `active`,

    -- Prețul efectiv per sesiune pentru acest copil
    -- Poate diferi de group_class.session_price (reduceri, negocieri)
    ADD COLUMN `billing_price_per_session` DECIMAL(10,2) NULL
        COMMENT 'Prețul per sesiune specific acestei înscrieri (override față de prețul grupei)'
        AFTER `billing_type`,

    -- Numărul total de sesiuni dintr-un pachet prepaid
    -- Relevant doar când billing_type = PACKAGE
    ADD COLUMN `billing_package_sessions_total` INT NULL
        COMMENT 'Numărul de sesiuni incluse în pachetul prepaid (doar pentru PACKAGE)'
        AFTER `billing_price_per_session`,

    -- Câte sesiuni din pachet au fost deja consumate
    ADD COLUMN `billing_package_sessions_used` INT NOT NULL DEFAULT 0
        COMMENT 'Sesiuni consumate din pachet — incrementat la fiecare sesiune prezentă'
        AFTER `billing_package_sessions_total`,

    -- Data de la care curge abonamentul lunar
    -- Relevant doar când billing_type = MONTHLY
    ADD COLUMN `billing_subscription_start` DATE NULL
        COMMENT 'Data de start a abonamentului lunar (doar pentru MONTHLY)'
        AFTER `billing_package_sessions_used`;

-- ── Tabela payments ───────────────────────────────────────────────────────────
-- Înregistrează plățile efectuate de părinți.
-- Structură generică — suportă atât plată manuală (cash/transfer)
-- cât și plată online (Stripe/PayU) prin câmpul external_payment_id.

CREATE TABLE IF NOT EXISTS `payments` (
    `id`                  BIGINT         NOT NULL AUTO_INCREMENT,

    -- Referințe la entitățile plătitoare
    `child_id`            INT            NOT NULL
        COMMENT 'Copilul pentru care se face plata',
    `group_id`            INT            NOT NULL
        COMMENT 'Grupa pentru care se face plata',
    `child_group_id`      INT            NULL
        COMMENT 'Legătura directă la înscrierea specifică (child_group)',

    -- Date financiare
    `amount`              DECIMAL(10,2)  NOT NULL
        COMMENT 'Suma plătită în RON',
    `currency`            VARCHAR(3)     NOT NULL DEFAULT 'RON'
        COMMENT 'Moneda — implicit RON',

    -- Tipul și metoda de plată
    `payment_type`        VARCHAR(50)    NULL
        COMMENT 'Tipul: SESSION, MONTHLY, PACKAGE, PARTIAL',
    `payment_method`      VARCHAR(30)    NULL
        COMMENT 'Metoda: CASH, BANK_TRANSFER, CARD_ONLINE, CARD_POS',

    -- Status plată
    -- PENDING   = inițiată, neconfirmată încă
    -- COMPLETED = confirmată de admin sau de payment gateway
    -- FAILED    = eșuată (doar pentru plăți online)
    -- REFUNDED  = returnată
    `status`              VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
        COMMENT 'Statusul plății: PENDING, COMPLETED, FAILED, REFUNDED',

    -- Date temporale
    `payment_date`        DATE           NULL
        COMMENT 'Data efectivă a plății (completată de admin pentru plăți manuale)',
    `due_date`            DATE           NULL
        COMMENT 'Data scadenței — pentru reminder automat de neplată',
    `created_at`          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT 'Data creării înregistrării',
    `updated_at`          DATETIME       NULL ON UPDATE CURRENT_TIMESTAMP
        COMMENT 'Data ultimei modificări',

    -- Plăți online — referință externă payment gateway (Stripe/PayU)
    -- NULL pentru plăți manuale
    `external_payment_id` VARCHAR(255)   NULL
        COMMENT 'ID-ul tranzacției din payment gateway (Stripe charge_id, PayU orderId etc.)',
    `external_status`     VARCHAR(50)    NULL
        COMMENT 'Statusul raw din payment gateway (pentru debugging)',

    -- Câmpul period acoperit de această plată
    -- Ex: "2026-02" pentru abonament lunar februarie
    -- Ex: "sesiuni 1-8" pentru pachet
    `period_covered`      VARCHAR(100)   NULL
        COMMENT 'Perioada acoperită de plată (ex: 2026-02, sesiuni 1-8)',

    -- Note admin
    `notes`               TEXT           NULL
        COMMENT 'Note interne administrator (ex: "plătit cash la recepție")',

    -- Referință factură
    `invoice_number`      VARCHAR(50)    NULL
        COMMENT 'Numărul facturii asociate (dacă există sistem de facturare)',

    PRIMARY KEY (`id`),

    -- Indexuri pentru query-uri frecvente
    KEY `idx_payments_child`      (`child_id`),
    KEY `idx_payments_group`      (`group_id`),
    KEY `idx_payments_status`     (`status`),
    KEY `idx_payments_due_date`   (`due_date`),
    KEY `idx_payments_created_at` (`created_at`),

    -- Foreign keys
    CONSTRAINT `fk_payments_child`       FOREIGN KEY (`child_id`)       REFERENCES `child`       (`id_child`),
    CONSTRAINT `fk_payments_group`       FOREIGN KEY (`group_id`)        REFERENCES `group_class` (`id_group`),
    CONSTRAINT `fk_payments_child_group` FOREIGN KEY (`child_group_id`) REFERENCES `child_group` (`id_child_group`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Plăți părinți — placeholder pentru billing. Logica completă în V3 după review client.';
