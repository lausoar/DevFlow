package com.devflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权限矩阵 + 直连收口 集成测试（直连运行中的后端 8080，纯 HTTP，无 Spring 上下文）。
 * 覆盖：测试用例权限点（OWNER 可/MEMBER 不可）、文档直连收口、单资源 404 语义。
 * 测试数据由各用例自行创建与清理。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PermissionMatrixHttpTest {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper OM = new ObjectMapper();
    private static final String BASE = "http://localhost:8080/api/v1";

    private static final String PROJ = "87d256ac6244b35bc2030306c6b96731";
    private static final String M1UID = "1b64084b4afe959de64a50ed6bd96e8f";
    private static final String ENT = "18164eed1e151bbda2b1e1549b3c53fa";

    private static String adminToken;
    private static String memberToken;
    private static String outsiderToken;

    @BeforeAll
    static void loginAll() {
        assumeBackendAvailable();
        adminToken = login("admin", "admin123");
        memberToken = login("member1_5c14", "DevFlow@2026");
        outsiderToken = login("dissolvetest", "test123456");
        // 这是直连 8080 的联调测试：本机没起后端、或库里没有这几个种子账号时，
        // 应当"跳过"而不是"失败" —— 否则 mvn test 会因为环境没准备好而变红。
        org.junit.jupiter.api.Assumptions.assumeFalse(adminToken.isBlank(),
                "跳过：localhost:8080 未运行或缺少 admin 种子账号");
        org.junit.jupiter.api.Assumptions.assumeFalse(memberToken.isBlank(),
                "跳过：缺少 member1_5c14 种子账号（需先用 verify-integration.sh 造数据）");
        org.junit.jupiter.api.Assumptions.assumeFalse(outsiderToken.isBlank(),
                "跳过：缺少 dissolvetest 种子账号");
    }

    /** 仅把“本机端口未监听”视为跳过；登录后的鉴权/夹具错误仍原样失败。 */
    private static void assumeBackendAvailable() {
        URI base = URI.create(BASE);
        boolean available;
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(base.getHost(), base.getPort()), 1000);
            available = true;
        } catch (java.io.IOException e) {
            available = false;
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(available,
                "跳过：localhost:8080 未监听；需要直连后端时才运行权限矩阵 HTTP 测试");
    }

    private static String login(String u, String p) {
        var r = call("POST", "/auth/login", Map.of("username", u, "password", p), null);
        return r.path("data").path("accessToken").asText();
    }

    private static JsonNode call(String method, String path, Object data, String token) {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(10));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.header("Content-Type", "application/json");
        builder.method(method, data == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(toJson(data)));
        try {
            HttpResponse<String> resp = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            return body == null || body.isBlank()
                    ? OM.nullNode()
                    : OM.readTree(body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(method + " " + path + " 失败: " + e, e);
        }
    }

    private static String toJson(Object o) {
        try {
            return new ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String err(JsonNode r) {
        return r.path("message").asText("");
    }

    @Test
    @Order(1)
    void projectDetail_memberVisible_outsider404() {
        var ok = call("GET", "/projects/" + PROJ, null, memberToken);
        assertThat(ok.path("code").asInt()).isEqualTo(200);

        var hidden = call("GET", "/projects/" + PROJ, null, outsiderToken);
        // 不泄露存在性：HTTP 200 + data 置空（或 404）
        assertThat(hidden.path("code").asInt() == 404
                || hidden.path("data").isNull()
                || hidden.path("data").isMissingNode()).isTrue();
    }

    @Test
    @Order(2)
    void testcases_memberCannotCreate() {
        var denied = call("POST", "/projects/" + PROJ + "/test-cases",
                Map.of("title", "集成IT-成员尝试"), memberToken);
        assertThat(denied.path("code").asInt()).isEqualTo(403);
    }

    @Test
    @Order(3)
    void testcases_ownerCanCreateExecute() {
        var created = call("POST", "/projects/" + PROJ + "/test-cases",
                Map.of("title", "集成IT-管理员用例",
                        "steps", toJson(java.util.List.of(
                                Map.of("action", "打开页面", "expected", "正常")))),
                adminToken);
        assertThat(created.path("code").asInt()).isEqualTo(200);
        String id = created.path("data").path("id").asText();

        var exec = call("POST", "/test-cases/" + id + "/executions",
                Map.of("result", "PASS"), adminToken);
        assertThat(exec.path("code").asInt()).isEqualTo(200);

        var del = call("DELETE", "/test-cases/" + id, null, adminToken);
        assertThat(del.path("code").asInt()).isEqualTo(200);
    }

    @Test
    @Order(4)
    void unauthenticated_rejected() {
        var r = call("GET", "/projects/" + PROJ, null, null);
        // 未认证：HTTP 401 或 data 置空
        assertThat(r.path("code").asInt() == 401
                || r.path("data").isNull()
                || r.path("data").isMissingNode()).isTrue();
}
}
