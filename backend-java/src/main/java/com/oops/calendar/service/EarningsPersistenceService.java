package com.oops.calendar.service;

import com.oops.calendar.dto.EarningsEvent;
import com.oops.calendar.persistence.EarningsCoverageEntity;
import com.oops.calendar.persistence.EarningsCoverageRepository;
import com.oops.calendar.persistence.EarningsEventEntity;
import com.oops.calendar.persistence.EarningsEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 财报数据持久化(H2 文件库)。
 * <ul>
 *   <li>replaceRange:拉取到上游最新数据后,整段替换 [from, to] 并记录覆盖时间(真实数据源才落库,mock 不落)。</li>
 *   <li>loadRange:内存缓存失效后,若覆盖时间仍在 TTL 内,则从库读回,避免整段回源。</li>
 * </ul>
 * 仅作为二级缓存;不改变上游为权威数据源、内存缓存为一层的语义。
 */
@Service
public class EarningsPersistenceService {

    private final EarningsEventRepository eventRepository;
    private final EarningsCoverageRepository coverageRepository;

    public EarningsPersistenceService(EarningsEventRepository eventRepository,
                                      EarningsCoverageRepository coverageRepository) {
        this.eventRepository = eventRepository;
        this.coverageRepository = coverageRepository;
    }

    /** 从库读回的结果;未覆盖或已过期时返回 null。 */
    public static final class LoadedRange {
        public final List<EarningsEvent> events;
        public final String source;

        LoadedRange(List<EarningsEvent> events, String source) {
            this.events = events;
            this.source = source;
        }
    }

    /** 内存缓存未命中时:若 [from, to] 覆盖且未过期,读回事件;否则返回 null。 */
    @Transactional(readOnly = true)
    public LoadedRange loadRange(LocalDate from, LocalDate to, long ttlMs) {
        String rangeKey = from + "|" + to;
        Optional<EarningsCoverageEntity> cov = coverageRepository.findById(rangeKey);
        if (!cov.isPresent()) {
            return null;
        }
        Instant fetchedAt = cov.get().getFetchedAt();
        if (fetchedAt == null || fetchedAt.plusMillis(ttlMs).isBefore(Instant.now())) {
            return null; // 覆盖记录已过期,按正常流程回源
        }
        String source = cov.get().getSource();
        List<EarningsEvent> events = eventRepository
                .findByEventDateBetweenAndSourceOrderByEventDateAscSymbolAsc(from, to, source)
                .stream()
                .map(EarningsEventEntity::toDto)
                .collect(Collectors.toList());
        return new LoadedRange(events, source);
    }

    /**
     * 整段替换 [from, to] 事件并记录覆盖时间。调用方保证 source 非 mock。
     * 并发请求(整月 + 单日等重叠区间)会竞争 delete+save 的顺序,synchronized
     * 保证每次替换原子完成,避免交错写入产生重复行。
     */
    @Transactional
    public synchronized void replaceRange(LocalDate from, LocalDate to, String source, List<EarningsEvent> events) {
        eventRepository.deleteRange(from, to, source);
        if (!events.isEmpty()) {
            eventRepository.saveAll(events.stream()
                    .map(EarningsEventEntity::from)
                    .collect(Collectors.toList()));
        }
        EarningsCoverageEntity cov = new EarningsCoverageEntity();
        cov.setRangeKey(from + "|" + to);
        cov.setSource(source);
        cov.setFetchedAt(Instant.now());
        // 立即 flush:后续 replaceRange 的批量 delete(clearAutomatically=true)会清空持久化上下文,
        // 若不先落库,本事务里尚未 flush 的覆盖记录可能被丢弃,导致 loadRange 误判"未覆盖"。
        coverageRepository.saveAndFlush(cov);
    }
}
