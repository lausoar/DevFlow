package com.devflow.controller;

import com.devflow.common.result.R;
import com.devflow.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 全局搜索
 * 结果按用户参与的项目过滤；响应禁缓存（避免浏览器启发式缓存返回旧结果）
 */
@Tag(name = "全局搜索", description = "跨项目/任务/Bug/需求/文档聚合检索")
@RestController
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "全局搜索")
    @GetMapping("/search")
    public R<Map<String, Object>> search(@RequestParam("q") String q,
                                         Authentication authentication,
                                         HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return R.ok(searchService.search((String) authentication.getPrincipal(), q));
    }
}
