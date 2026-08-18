package com.oops.calendar.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 快讯收藏仓库(H2 文件库)。 */
public interface NewsFavoriteRepository extends JpaRepository<NewsFavoriteEntity, Long> {

    /** 某客户端的全部收藏,最近收藏的在前(id 为同时刻收藏的次序兜底)。 */
    List<NewsFavoriteEntity> findByClientIdOrderByCreatedAtDescIdDesc(String clientId);

    boolean existsByClientId(String clientId);

    Optional<NewsFavoriteEntity> findByClientIdAndItemId(String clientId, String itemId);
}
