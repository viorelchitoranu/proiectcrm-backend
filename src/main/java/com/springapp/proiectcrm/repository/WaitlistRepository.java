package com.springapp.proiectcrm.repository;

import com.springapp.proiectcrm.model.WaitlistEntry;
import com.springapp.proiectcrm.model.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository pentru lista de așteptare.
 *
 * Queries folosite de admin:
 *   findAllByOrderByCreatedAtDesc() → toate intrările, cele mai noi primele
 *   findByStatus()                  → filtrare după status (WAITING, ALLOCATED, CANCELLED)
 *
 * Query folosit pentru unicitate:
 *   existsByParentEmailAndStatus()  → previne dublarea: același email nu poate
 *                                     fi pe waitlist de două ori cu status WAITING
 */
@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Integer> {

    // Toate intrările sortate descrescător după dată — pentru pagina admin
    List<WaitlistEntry> findAllByOrderByCreatedAtDesc();

    // Filtrare după status — util pentru a vedea doar cererile în așteptare
    List<WaitlistEntry> findByStatusOrderByCreatedAtDesc(WaitlistStatus status);

    // Verificare unicitate: un email poate fi pe waitlist O SINGURĂ dată cu status WAITING
    boolean existsByParentEmailAndStatus(String email, WaitlistStatus status);
}
