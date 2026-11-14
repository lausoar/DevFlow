package com.devflow.service;

import com.devflow.entity.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;

/**
 * 知识库同步服务
 * 文档创建/更新/删除后异步同步到 AI 服务的向量索引（ChromaDB）。
 * 同步失败只记录日志，不影响文档主流程。
 *
 * AI 服务已开启强制认证：服务间调用统一带 X-Service-Token（ai.service.token），
 * 并显式下发 enterprise_id / project_id，供检索侧做租户隔离。
 */
@Slf4j
@Service
public class AiKnowledgeSyncService {

    private final RestClient restClient;
    private final String serviceToken;

    public AiKnowledgeSyncService(
            @Value("${ai.service.base-url:http://localhost:8000/api/v1}") String baseUrl,
            @Value("${ai.service.token:}") String serviceToken) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.serviceToken = serviceToken;
    }

    /**
     * 文档创建/更新后重新索引
     * AI 服务侧会先清理旧分片再写入，重复调用幂等
     */
    @Async("aiExecutor")
    public void indexDocument(Document doc) {
        if (doc == null || doc.getId() == null) {
            return;
        }
        try {
            if (doc.getContent() == null || doc.getContent().isBlank()) {
                return;
            }
            HashMap<String, Object> body = new HashMap<>();
            body.put("doc_id", doc.getId());
            body.put("title", doc.getTitle());
            body.put("content", doc.getContent());
            body.put("doc_type", doc.getType() != null ? doc.getType().toLowerCase() : "doc");
            if (StringUtils.hasText(doc.getProjectId())) {
                body.put("project_id", doc.getProjectId());
            }
            if (StringUtils.hasText(doc.getEnterpriseId())) {
                body.put("enterprise_id", doc.getEnterpriseId());
            }
            String result = restClient.post()
                    .uri("/rag/index")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(this::applyServiceToken)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            log.info("文档已同步到知识库: id={}, title={}, 响应={}", doc.getId(), doc.getTitle(), result);
        } catch (Exception e) {
            log.warn("文档同步知识库失败（不影响主流程）: id={}, title={}, 原因={}",
                    doc.getId(), doc.getTitle(), e.getMessage());
        }
    }

    /** 文档删除后清理向量索引 */
    @Async("aiExecutor")
    public void deleteDocument(String docId) {
        if (docId == null || docId.isBlank()) {
            return;
        }
        try {
            restClient.delete()
                    .uri("/rag/index/{docId}", docId)
                    .headers(this::applyServiceToken)
                    .retrieve()
                    .toBodilessEntity();
            log.info("文档索引已从知识库删除: {}", docId);
        } catch (Exception e) {
            log.warn("文档索引删除失败（不影响主流程）: id={}, 原因={}", docId, e.getMessage());
        }
    }

    private void applyServiceToken(org.springframework.http.HttpHeaders headers) {
        if (StringUtils.hasText(serviceToken)) {
            headers.set("X-Service-Token", serviceToken);
        }
    }
}
