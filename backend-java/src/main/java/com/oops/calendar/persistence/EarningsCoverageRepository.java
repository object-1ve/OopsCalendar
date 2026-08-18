package com.oops.calendar.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** 财报覆盖记录仓库。 */
public interface EarningsCoverageRepository extends JpaRepository<EarningsCoverageEntity, String> {
}
