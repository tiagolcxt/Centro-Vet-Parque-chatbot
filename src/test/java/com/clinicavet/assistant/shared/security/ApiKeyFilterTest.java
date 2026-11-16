package com.clinicavet.assistant.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyFilterTest {

    private static final String KEY = "chave-certa-ficticia-0123456789abcdef";

    private final ApiKeyFilter filter = new ApiKeyFilter(KEY);

    private MockHttpServletResponse call(String providedKey, MockFilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/conversations/x/messages");
        if (providedKey != null) {
            request.addHeader("X-Api-Key", providedKey);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void correctKey_passesThrough() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = call(KEY, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void missingKey_is401_andStopsTheChain() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = call(null, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void wrongKey_is401_andNeitherKeyAppearsInTheResponse() throws Exception {
        String wrong = "chave-errada-ficticia-9876543210fedcba";

        MockHttpServletResponse response = call(wrong, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).doesNotContain(wrong).doesNotContain(KEY).contains("X-Api-Key");
    }

    @Test
    void keyWithDifferentLengthOrCase_isRejected() throws Exception {
        assertThat(call(KEY + "x", new MockFilterChain()).getStatus()).isEqualTo(401);
        assertThat(call(KEY.toUpperCase(), new MockFilterChain()).getStatus()).isEqualTo(401);
        assertThat(call("", new MockFilterChain()).getStatus()).isEqualTo(401);
    }

    @Test
    void applicationRefusesToStart_withoutKeyOrWithShortKey() {
        ApiKeyConfig config = new ApiKeyConfig();

        assertThatThrownBy(() -> config.apiKeyFilter("")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ASSISTANT_API_KEY");
        assertThatThrownBy(() -> config.apiKeyFilter("curta")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
