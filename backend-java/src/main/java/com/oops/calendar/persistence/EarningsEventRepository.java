package com.oops.calendar.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** 财报事件仓库。 */
public interface EarningsEventRepository extends JpaRepository<EarningsEventEntity, Long> {

    List<EarningsEventEntity> findByEventDateBetweenAndSourceOrderByEventDateAscSymbolAsc(
            LocalDate from, LocalDate to, String source);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("delete from EarningsEventEntity e where e.eventDate >= :from and e.eventDate <= :to and e.source = :source")
    void deleteRange(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("source") String source);
}
