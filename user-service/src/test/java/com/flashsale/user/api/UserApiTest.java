package com.flashsale.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashsale.user.TestcontainersConfiguration;
import com.flashsale.user.auth.JwtProperties;
import com.flashsale.user.domain.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository repository;

    @Autowired
    JwtProperties jwtProperties;

    @Autowired
    JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void reset() {
        repository.deleteAll();
    }

    private ResultActions call(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions signup(String email) throws Exception {
        return call("/api/users/signup",
            "{\"email\":\"" + email + "\",\"password\":\"pw-1234\",\"name\":\"Kim\"}");
    }

    private String subject(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    @Test
    void signupThenLoginReturnsToken() throws Exception {
        JsonNode created = json(signup("a@test.com").andExpect(status().isCreated()));
        long userId = created.get("userId").asLong();

        JsonNode login = json(call("/api/users/login", "{\"email\":\"a@test.com\",\"password\":\"pw-1234\"}")
            .andExpect(status().isOk()));

        assertThat(subject(login.get("accessToken").asText())).isEqualTo(Long.toString(userId));
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        signup("a@test.com").andExpect(status().isCreated());
        signup("a@test.com").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        signup("a@test.com").andExpect(status().isCreated());
        call("/api/users/login", "{\"email\":\"a@test.com\",\"password\":\"nope\"}")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void missingFieldReturns400() throws Exception {
        call("/api/users/signup", "{\"email\":\"a@test.com\"}").andExpect(status().isBadRequest());
        call("/internal/test-users", "{}").andExpect(status().isBadRequest());
        call("/internal/test-users", "{\"count\":50001}").andExpect(status().isBadRequest());
    }

    @Test
    void testUsersAreIdempotent() throws Exception {
        JsonNode first = json(call("/internal/test-users", "{\"count\":3}").andExpect(status().isOk()));
        JsonNode second = json(call("/internal/test-users", "{\"count\":3}").andExpect(status().isOk()));

        assertThat(first.size()).isEqualTo(3);
        for (int i = 0; i < 3; i++) {
            assertThat(second.get(i).get("userId").asLong()).isEqualTo(first.get(i).get("userId").asLong());
        }
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    void testUsersReturnsTokensInOrder() throws Exception {
        JsonNode users = json(call("/internal/test-users", "{\"count\":3}").andExpect(status().isOk()));

        assertThat(users.size()).isEqualTo(3);
        for (int i = 0; i < 3; i++) {
            long id = users.get(i).get("userId").asLong();
            assertThat(subject(users.get(i).get("accessToken").asText())).isEqualTo(Long.toString(id));
            assertThat(repository.findById(id).orElseThrow().getEmail())
                .isEqualTo("loadtest-" + (i + 1) + "@test.local");
        }
    }

    @Test
    void bulkIssueOf5000FinishesQuickly() throws Exception {
        long start = System.nanoTime();
        JsonNode users = json(call("/internal/test-users", "{\"count\":5000}").andExpect(status().isOk()));
        long ms = (System.nanoTime() - start) / 1_000_000;

        assertThat(users.size()).isEqualTo(5000);
        assertThat(ms).isLessThan(15_000);
    }
}
