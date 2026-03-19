package com.springapp.proiectcrm.scheduler;

import com.springapp.proiectcrm.repository.SessionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
//cauta la ora 00.05 in fiecare seara in baza de date sesiunile PLANNED care au ajuns la data de predare (TAUGHT) si le marchea za ca TAUGHT
public class SessionStatusScheduler {

    private final SessionRepository sessionRepository;

    @Transactional
    @Scheduled(cron = "0 5 0 * * *", zone = "Europe/Bucharest")
    public void autoMarkPastPlannedAsTaught() {

        LocalDate today = LocalDate.now(ZoneId.of("Europe/Bucharest"));

        sessionRepository.markPastPlannedAsTaught(today);

    }


}
