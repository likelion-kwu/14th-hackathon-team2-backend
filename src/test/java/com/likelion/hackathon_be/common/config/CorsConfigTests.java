package com.likelion.hackathon_be.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

class CorsConfigTests {

    private static final String FRONTEND_ORIGIN = "https://godlife.likelion.uk";
    private static final String LOCAL_FRONTEND_ORIGIN = "http://localhost:5173";

    private final CorsFilter corsFilter = new CorsConfig().corsFilter();

    @Test
    void preflightAllowsProductionFrontendBearerHeaders() throws Exception {
        MockHttpServletRequest request = preflightRequest(FRONTEND_ORIGIN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> chainCalled.set(true);

        corsFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(FRONTEND_ORIGIN);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
                .contains("GET")
                .contains("POST")
                .contains("PUT")
                .contains("PATCH")
                .contains("DELETE")
                .contains("OPTIONS");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).toLowerCase(Locale.ROOT))
                .contains("authorization")
                .contains("content-type");
        assertThat(chainCalled).isFalse();
    }

    @Test
    void preflightAllowsLocalFrontendBearerHeaders() throws Exception {
        MockHttpServletRequest request = preflightRequest(LOCAL_FRONTEND_ORIGIN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> chainCalled.set(true);

        corsFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(LOCAL_FRONTEND_ORIGIN);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("POST");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).toLowerCase(Locale.ROOT))
                .contains("authorization")
                .contains("content-type");
        assertThat(chainCalled).isFalse();
    }

    @Test
    void preflightDoesNotAllowUnknownOrigin() throws Exception {
        MockHttpServletRequest request = preflightRequest("https://example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> chainCalled.set(true);

        corsFilter.doFilter(request, response, chain);

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        assertThat(chainCalled).isFalse();
    }

    private static MockHttpServletRequest preflightRequest(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/sessions");
        request.addHeader(HttpHeaders.ORIGIN, origin);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type");
        return request;
    }
}
