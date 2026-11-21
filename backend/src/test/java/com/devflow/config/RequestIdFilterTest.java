package com.devflow.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 请求关联 ID：透传校验、响应头回传、MDC 清理（异常路径也要清理）。
 */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    private MockHttpServletResponse response;

    /** 执行过滤器，返回请求处理期间 MDC 中的 requestId */
    private String run(MockHttpServletRequest request, FilterChain chain) throws Exception {
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response.getHeader(RequestIdFilter.HEADER);
    }

    private String runWithCapture(MockHttpServletRequest request) throws Exception {
        String[] during = new String[1];
        FilterChain chain = (req, res) -> during[0] = MDC.get(RequestIdFilter.MDC_KEY);
        run(request, chain);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
        return during[0];
    }

    @Test
    void generatesIdWhenHeaderMissing() throws Exception {
        String mdcId = runWithCapture(new MockHttpServletRequest("GET", "/api/v1/projects"));
        assertThat(mdcId).isNotBlank().hasSize(32);
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(mdcId);
    }

    @Test
    void reusesWellFormedIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
        request.addHeader(RequestIdFilter.HEADER, "gateway-abc-123");
        assertThat(runWithCapture(request)).isEqualTo("gateway-abc-123");
    }

    @Test
    void rejectsHeaderInjectionAndOversizedValues() throws Exception {
        MockHttpServletRequest injected = new MockHttpServletRequest("GET", "/x");
        injected.addHeader(RequestIdFilter.HEADER, "abc\r\nSet-Cookie: pwnd");
        assertThat(runWithCapture(injected)).hasSize(32);

        MockHttpServletRequest tooLong = new MockHttpServletRequest("GET", "/x");
        tooLong.addHeader(RequestIdFilter.HEADER, "a".repeat(200));
        assertThat(runWithCapture(tooLong)).hasSize(32);
    }

    @Test
    void clearsMdcEvenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain boom = (req, response) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, res, boom))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void responseAlwaysCarriesHeader() throws Exception {
        run(new MockHttpServletRequest("GET", "/x"), (req, res) -> { });
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isNotBlank();
    }
}
