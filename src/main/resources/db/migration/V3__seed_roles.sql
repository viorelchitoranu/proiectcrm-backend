-- ============================================================
-- V3__seed_roles.sql
-- Date inițiale — rulează după ce Flyway a creat schema (V1+V2).
--
-- Conține:
--   - Cele 3 roluri ale aplicației (ADMIN, TEACHER, PARENT)
--   - Contul admin implicit (parolă: Admin@1234 — de schimbat!)
--
-- INSERT IGNORE — nu aruncă eroare dacă datele există deja.
-- ============================================================
 
-- IMPORTANT: Ordinea rolurilor trebuie să corespundă cu baza de date
-- existentă a clientului:
--   1 = PARENT
--   2 = TEACHER
--   3 = ADMIN
INSERT IGNORE INTO `role` (`role_name`, `created_date`) VALUES
    ('PARENT',  CURDATE()),
    ('TEACHER', CURDATE()),
    ('ADMIN',   CURDATE());
 
-- Cont admin implicit — role_id=3 = ADMIN
-- Parolă: de setat manual după primul deploy
-- Cont admin implicit — role_id=3 = ADMIN
-- IMPORTANT: Schimbă parola după primul login!
-- Parola implicită se setează manual în baza de date după deployment.
-- Nu stoca hash-uri reale în codul sursă.
INSERT IGNORE INTO `user`
    (`email`, `password`, `first_name`, `last_name`, `is_active`, `active`, `created_at`, `role_id`)
VALUES (
    'admin@platform.ro',
    'CHANGE_ME_SET_REAL_BCRYPT_HASH_AFTER_DEPLOY',
    'Admin',
    'Platform',
    1,
    1,
    CURDATE(),
    3
);
