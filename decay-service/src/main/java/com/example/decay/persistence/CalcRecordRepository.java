package com.example.decay.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CalcRecordRepository
        extends JpaRepository<CalcRecord, String>, JpaSpecificationExecutor<CalcRecord> {
}
