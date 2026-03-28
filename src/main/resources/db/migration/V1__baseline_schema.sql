-- ============================================================
-- V1__baseline_schema.sql
-- Flyway baseline — schema existentă la momentul introducerii Flyway.
--
-- IMPORTANT: Acest fișier NU se execută automat pe baza de date existentă.
-- Rulează `flyway baseline` o singură dată pe DB-ul existent pentru a
-- marca V1 ca deja aplicat (inserează rândul în flyway_schema_history).
--
-- Pe instanțe NOI (clienți noi), V1 se execută automat și creează
-- întreaga schemă de la zero.
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ── role ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `role` (
    `id_role`      INT          NOT NULL AUTO_INCREMENT,
    `role_name`    VARCHAR(255) DEFAULT NULL,
    `created_date` DATE         DEFAULT NULL,
    PRIMARY KEY (`id_role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── user ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `user` (
    `id_user`    INT          NOT NULL AUTO_INCREMENT,
    `password`   VARCHAR(255) DEFAULT NULL,
    `last_name`  VARCHAR(255) DEFAULT NULL,
    `first_name` VARCHAR(255) DEFAULT NULL,
    `address`    VARCHAR(255) DEFAULT NULL,
    `phone`      VARCHAR(255) DEFAULT NULL,
    `email`      VARCHAR(255) NOT NULL,
    `role_id`    INT          DEFAULT NULL,
    `is_active`  TINYINT(1)   NOT NULL DEFAULT '1',
    `created_at` DATE         DEFAULT NULL,
    `active`     TINYINT(1)   DEFAULT NULL,
    PRIMARY KEY (`id_user`),
    KEY `role_id` (`role_id`),
    CONSTRAINT `user_ibfk_1` FOREIGN KEY (`role_id`) REFERENCES `role` (`id_role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── school ───────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `school` (
    `id_school` INT          NOT NULL AUTO_INCREMENT,
    `name`      VARCHAR(255) DEFAULT NULL,
    `address`   VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (`id_school`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── course ───────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `course` (
    `id_course`  INT          NOT NULL AUTO_INCREMENT,
    `name`       VARCHAR(255) DEFAULT NULL,
    `created_at` DATE         DEFAULT NULL,
    PRIMARY KEY (`id_course`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── group_class ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `group_class` (
    `id_group`           INT            NOT NULL AUTO_INCREMENT,
    `teacher_id`         INT            DEFAULT NULL,
    `school_id`          INT            DEFAULT NULL,
    `course_id`          INT            DEFAULT NULL,
    `name`               VARCHAR(255)   DEFAULT NULL,
    `start_date`         DATE           DEFAULT NULL,
    `end_date`           DATE           DEFAULT NULL,
    `session_start_time` TIME           DEFAULT NULL,
    `max_capacity`       INT            DEFAULT NULL,
    `is_active`          TINYINT(1)     DEFAULT NULL,
    `session_price`      DECIMAL(10,0)  DEFAULT NULL,
    `total_price`        DECIMAL(10,0)  DEFAULT NULL,
    `created_at`         DATE           DEFAULT NULL,
    `max_recovery_slots` INT            DEFAULT NULL,
    `start_confirmed_at` DATETIME       DEFAULT NULL,
    `force_stop_at`      DATETIME       DEFAULT NULL,
    PRIMARY KEY (`id_group`),
    KEY `course_id`  (`course_id`),
    KEY `school_id`  (`school_id`),
    KEY `teacher_id` (`teacher_id`),
    CONSTRAINT `group_class_ibfk_1` FOREIGN KEY (`course_id`)  REFERENCES `course` (`id_course`),
    CONSTRAINT `group_class_ibfk_2` FOREIGN KEY (`school_id`)  REFERENCES `school` (`id_school`),
    CONSTRAINT `group_class_ibfk_3` FOREIGN KEY (`teacher_id`) REFERENCES `user`   (`id_user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── child ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `child` (
    `id_child`     INT          NOT NULL AUTO_INCREMENT,
    `parent_id`    INT          DEFAULT NULL,
    `last_name`    VARCHAR(255) DEFAULT NULL,
    `first_name`   VARCHAR(255) DEFAULT NULL,
    `age`          INT          DEFAULT NULL,
    `school`       VARCHAR(255) DEFAULT NULL,
    `school_class` VARCHAR(255) DEFAULT NULL,
    `created_at`   DATE         DEFAULT NULL,
    `active`       TINYINT(1)   NOT NULL DEFAULT '1'
        COMMENT 'Starea copilului: 1=activ, 0=dezactivat de admin.',
    PRIMARY KEY (`id_child`),
    KEY `parent_id` (`parent_id`),
    CONSTRAINT `child_ibfk_1` FOREIGN KEY (`parent_id`) REFERENCES `user` (`id_user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── child_group ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `child_group` (
    `id_child_group`  INT  NOT NULL AUTO_INCREMENT,
    `child_id`        INT  NOT NULL,
    `group_id`        INT  NOT NULL,
    `enrollment_date` DATE DEFAULT NULL,
    `active`          TINYINT(1) DEFAULT NULL,
    PRIMARY KEY (`id_child_group`),
    UNIQUE KEY `uq_child_group_child_group` (`child_id`, `group_id`),
    KEY `group_id` (`group_id`),
    CONSTRAINT `child_group_ibfk_2`   FOREIGN KEY (`group_id`) REFERENCES `group_class` (`id_group`),
    CONSTRAINT `fk_child_group_child` FOREIGN KEY (`child_id`) REFERENCES `child`       (`id_child`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── holidays ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `holidays` (
    `id_holiday`   INT          NOT NULL AUTO_INCREMENT,
    `holiday_date` DATE         DEFAULT NULL,
    `description`  VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (`id_holiday`),
    UNIQUE KEY `uq_holidays_holiday_date` (`holiday_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── session ───────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `session` (
    `id_session`          INT          NOT NULL AUTO_INCREMENT,
    `group_id`            INT          DEFAULT NULL,
    `name`                VARCHAR(255) DEFAULT NULL,
    `session_date`        DATE         DEFAULT NULL,
    `time`                TIME         DEFAULT NULL,
    `session_type`        VARCHAR(16)  NOT NULL,
    `created_at`          DATE         DEFAULT NULL,
    `session_status`      VARCHAR(32)  NOT NULL,
    `school_id`           INT          DEFAULT NULL,
    `sequence_no`         INT          NOT NULL,
    `attendance_taken_at` DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id_session`),
    UNIQUE KEY `uq_group_sequence` (`group_id`, `sequence_no`),
    KEY `group_id`          (`group_id`),
    KEY `fk_session_school` (`school_id`),
    CONSTRAINT `fk_session_school` FOREIGN KEY (`school_id`) REFERENCES `school`      (`id_school`),
    CONSTRAINT `session_ibfk_1`    FOREIGN KEY (`group_id`)  REFERENCES `group_class` (`id_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── teacher_session ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `teacher_session` (
    `id_teacher_session` INT  NOT NULL AUTO_INCREMENT,
    `teacher_id`         INT  NOT NULL,
    `session_id`         INT  NOT NULL,
    `teaching_role`      ENUM('MAIN','ASSISTANT') NOT NULL DEFAULT 'MAIN',
    `created_at`         DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id_teacher_session`),
    UNIQUE KEY `uq_teacher_session` (`teacher_id`, `session_id`),
    KEY `fk_teacher_session_session` (`session_id`),
    CONSTRAINT `fk_teacher_session_session` FOREIGN KEY (`session_id`)  REFERENCES `session` (`id_session`),
    CONSTRAINT `fk_teacher_session_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `user`    (`id_user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── attendance ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `attendance` (
    `id_attendance`          INT  NOT NULL AUTO_INCREMENT,
    `session_id`             INT  DEFAULT NULL,
    `child_id`               INT  DEFAULT NULL,
    `status`                 ENUM('PRESENT','ABSENT','EXCUSED','CANCELLED_BY_PARENT',
                                  'RECOVERY_REQUESTED','RECOVERY_BOOKED','PENDING') NOT NULL,
    `created_at`             DATETIME   DEFAULT NULL,
    `is_recovery`            TINYINT(1) DEFAULT NULL,
    `recovery_for_session_id` INT       DEFAULT NULL,
    `nota`                   VARCHAR(255) DEFAULT NULL,
    `updated_at`             DATETIME   DEFAULT NULL,
    `assigned_to_session_id` INT        DEFAULT NULL,
    PRIMARY KEY (`id_attendance`),
    KEY `session_id`              (`session_id`),
    KEY `child_id`                (`child_id`),
    KEY `recovery_for_session_id` (`recovery_for_session_id`),
    CONSTRAINT `attendance_ibfk_1` FOREIGN KEY (`session_id`)             REFERENCES `session` (`id_session`),
    CONSTRAINT `attendance_ibfk_2` FOREIGN KEY (`child_id`)               REFERENCES `child`   (`id_child`),
    CONSTRAINT `attendance_ibfk_3` FOREIGN KEY (`recovery_for_session_id`) REFERENCES `session` (`id_session`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── attendance_archive ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `attendance_archive` (
    `id_attendance_archive`  BIGINT      NOT NULL AUTO_INCREMENT,
    `original_attendance_id` INT         DEFAULT NULL,
    `original_session_id`    INT         DEFAULT NULL,
    `original_group_id`      INT         DEFAULT NULL,
    `group_name`             VARCHAR(255) DEFAULT NULL,
    `course_name`            VARCHAR(255) DEFAULT NULL,
    `school_name`            VARCHAR(255) DEFAULT NULL,
    `teacher_name`           VARCHAR(255) DEFAULT NULL,
    `session_date`           DATE         DEFAULT NULL,
    `session_time`           TIME         DEFAULT NULL,
    `session_status`         VARCHAR(32)  DEFAULT NULL,
    `session_type`           VARCHAR(32)  DEFAULT NULL,
    `child_id`               INT          DEFAULT NULL,
    `child_first_name`       VARCHAR(255) DEFAULT NULL,
    `child_last_name`        VARCHAR(255) DEFAULT NULL,
    `parent_id`              INT          DEFAULT NULL,
    `parent_name`            VARCHAR(255) DEFAULT NULL,
    `parent_email`           VARCHAR(255) DEFAULT NULL,
    `parent_phone`           VARCHAR(64)  DEFAULT NULL,
    `attendance_status`      VARCHAR(32)  NOT NULL,
    `nota`                   TEXT         DEFAULT NULL,
    `created_at`             DATETIME     DEFAULT NULL,
    `updated_at`             DATETIME     DEFAULT NULL,
    `is_recovery`            TINYINT(1)   NOT NULL DEFAULT '0',
    `recovery_for_session_id` INT         DEFAULT NULL,
    `archived_at`            DATETIME     NOT NULL,
    PRIMARY KEY (`id_attendance_archive`),
    KEY `idx_att_arch_group`       (`original_group_id`),
    KEY `idx_att_arch_session`     (`original_session_id`),
    KEY `idx_att_arch_child`       (`child_id`),
    KEY `idx_att_arch_archived_at` (`archived_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── post ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `post` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `channel`         VARCHAR(60)  NOT NULL,
    `author_id`       INT          NOT NULL,
    `content`         TEXT         NOT NULL,
    `created_at`      DATETIME     NOT NULL,
    `edited_at`       DATETIME     DEFAULT NULL,
    `attachment_path` VARCHAR(500) DEFAULT NULL,
    `attachment_type` VARCHAR(10)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `fk_post_author`        (`author_id`),
    KEY `idx_post_channel_date` (`channel`, `created_at` DESC),
    CONSTRAINT `fk_post_author` FOREIGN KEY (`author_id`) REFERENCES `user` (`id_user`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── post_comment ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `post_comment` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT,
    `post_id`    BIGINT   NOT NULL,
    `author_id`  INT      NOT NULL,
    `content`    TEXT     NOT NULL,
    `created_at` DATETIME NOT NULL,
    `edited_at`  DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `fk_comment_author`    (`author_id`),
    KEY `idx_comment_post_date`(`post_id`, `created_at`),
    CONSTRAINT `fk_comment_author` FOREIGN KEY (`author_id`) REFERENCES `user` (`id_user`) ON DELETE RESTRICT,
    CONSTRAINT `fk_comment_post`   FOREIGN KEY (`post_id`)   REFERENCES `post` (`id`)      ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── reaction ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `reaction` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `post_id`    BIGINT      NOT NULL,
    `user_id`    INT         NOT NULL,
    `type`       VARCHAR(10) NOT NULL,
    `created_at` DATETIME    NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_reaction_post_user` (`post_id`, `user_id`),
    KEY `fk_reaction_user`  (`user_id`),
    KEY `idx_reaction_post` (`post_id`, `type`),
    CONSTRAINT `fk_reaction_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)  ON DELETE CASCADE,
    CONSTRAINT `fk_reaction_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id_user`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── waitlist_entry ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `waitlist_entry` (
    `id`                  INT          NOT NULL AUTO_INCREMENT,
    `parent_first_name`   VARCHAR(100) NOT NULL,
    `parent_last_name`    VARCHAR(100) NOT NULL,
    `parent_email`        VARCHAR(255) NOT NULL,
    `parent_phone`        VARCHAR(50)  DEFAULT NULL,
    `parent_address`      VARCHAR(500) DEFAULT NULL,
    `child_first_name`    VARCHAR(100) NOT NULL,
    `child_last_name`     VARCHAR(100) NOT NULL,
    `child_age`           INT          DEFAULT NULL,
    `child_school`        VARCHAR(255) DEFAULT NULL,
    `child_school_class`  VARCHAR(50)  DEFAULT NULL,
    `preferred_course_name` VARCHAR(255) DEFAULT NULL,
    `preferred_school_name` VARCHAR(255) DEFAULT NULL,
    `notes`               TEXT         DEFAULT NULL,
    `status`              VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    `created_at`          DATETIME     NOT NULL,
    `allocated_at`        DATETIME     DEFAULT NULL,
    `allocated_group_id`  INT          DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `fk_waitlist_group`         (`allocated_group_id`),
    KEY `idx_waitlist_email_status` (`parent_email`, `status`),
    CONSTRAINT `fk_waitlist_group` FOREIGN KEY (`allocated_group_id`)
        REFERENCES `group_class` (`id_group`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
