package com.springapp.proiectcrm.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "holidays", uniqueConstraints = @UniqueConstraint(name = "uk_holidays_date", columnNames = "holiday_date"))
@NoArgsConstructor
@Data
@AllArgsConstructor
public class Holiday {

    @Id
    @Column(name = "id_holiday")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idHoliday;// [pk]

    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate holidayDate;

    @Column(name = "description")
    private String description;




}
