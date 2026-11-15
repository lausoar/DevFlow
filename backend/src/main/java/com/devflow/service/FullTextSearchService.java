package com.devflow.service;

import java.util.List;
import java.util.Map;

/**
 * 全文检索（MySQL FULLTEXT + ngram 分词）。
 *
 * 与 SearchServiceImpl 的 LIKE 版本并存：
 * - LIKE '%kw%' 走全表扫描，但能匹配任意子串（含单字、符号组合）；
 * - FULLTEXT ngram 走倒排索引，中文按 2-gram 切分，能撑住数据量，但**单字检索命中取决于
 *   ngram_token_size**，短查询（1 个字）在默认配置下搜不到。
 *
 * 策略：先用 FULLTEXT 拿结果，查询词很短（< ngram_token_size）时回落 LIKE。
 * 这样常见查询走索引，边角查询仍可用。
 */
public interface FullTextSearchService {

    /** 索引是否已就绪（未建 FULLTEXT 索引时自动回落到 LIKE） */
    boolean isAvailable();

    /** 在指定实体内检索，返回 id 列表（保持相关度排序） */
    List<String> searchIds(String entity, String keyword, List<String> projectIds, int limit);

    /** 以 id 集合回表取实体（保持传入顺序） */
    <T> List<T> fetchByIds(String entity, List<String> ids);

    /** 诊断信息：索引是否可用、使用了哪种策略 */
    Map<String, Object> diagnostics();
}
