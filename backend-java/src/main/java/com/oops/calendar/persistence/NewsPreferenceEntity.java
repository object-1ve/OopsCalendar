package com.oops.calendar.persistence;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 快讯数据源偏好持久化实体(H2 文件库)。
 * 每个客户端一行,开启的数据源 key 以 JSON 数组文本存于 sources 列
 * (示例:["jin10","cls"]),空数组 = 已配置但全部禁用(configured 仍为 true)。
 */
@Entity
@Table(
        name = "news_preference",
        uniqueConstraints = @UniqueConstraint(name = "uk_news_pref_client", columnNames = {"client_id"})
)
public class NewsPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 浏览器客户端标识(前端生成,localStorage + cookie 持久化)。 */
    @Column(name = "client_id", nullable = false)
    private String clientId;

    /** 启用的数据源 key 列表(JSON 数组文本),空数组 = 全部禁用。 */
    @Lob
    @Column(name = "sources", nullable = false)
    private String sourcesJson;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA 需要无参构造器;NewsPreferencesService 用构造器引用新建行,因此为 public。 */
    public NewsPreferenceEntity() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getSourcesJson() { return sourcesJson; }
    public void setSourcesJson(String sourcesJson) { this.sourcesJson = sourcesJson; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}