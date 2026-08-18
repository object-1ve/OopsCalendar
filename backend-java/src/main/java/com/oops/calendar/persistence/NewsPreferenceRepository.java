package com.oops.calendar.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 快讯数据源偏好仓库(H2 文件库)。 */
public interface NewsPreferenceRepository extends JpaRepository<NewsPreferenceEntity, Long> {

    Optional<NewsPreferenceEntity> findByClientId(String clientId);

    boolean existsByClientId(String clientId);
}