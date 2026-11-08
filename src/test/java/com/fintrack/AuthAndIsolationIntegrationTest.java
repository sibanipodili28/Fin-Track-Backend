package com.fintrack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndIsolationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String register(String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "password123", "fullName", "Test User"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long createAccount(String token, String name, String balance) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/accounts").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name, "type", "SAVINGS", "initialBalance", balance))))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private long expenseCategoryId(String token) throws Exception {
        MvcResult res = mvc.perform(get("/api/v1/categories").param("type", "EXPENSE").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        JsonNode list = json.readTree(res.getResponse().getContentAsString());
        assertTrue(list.size() > 0, "categories must be seeded");
        return list.get(0).get("id").asLong();
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsWrongPasswordAndAcceptsCorrectOne() throws Exception {
        register("login@test.com");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"login@test.com\",\"password\":\"wrong-password\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"login@test.com\",\"password\":\"password123\"}")).andExpect(status().isOk());
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        register("dup@test.com");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dup@test.com\",\"password\":\"password123\",\"fullName\":\"X\"}")).andExpect(status().isConflict());
    }

    @Test
    void usersCannotSeeOrTouchEachOthersAccounts() throws Exception {
        String alice = register("alice@test.com");
        String bob = register("bob@test.com");
        long aliceAccount = createAccount(alice, "Alice Savings", "500.00");

        mvc.perform(get("/api/v1/accounts/" + aliceAccount).header("Authorization", "Bearer " + bob)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/accounts/" + aliceAccount).header("Authorization", "Bearer " + bob)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void expenseUpdatesBalanceAndOverdraftIsRejected() throws Exception {
        String token = register("spender@test.com");
        long account = createAccount(token, "Main", "100.00");
        long category = expenseCategoryId(token);

        String ok = json.writeValueAsString(Map.of("accountId", account, "categoryId", category, "type", "EXPENSE",
                "amount", "40.00", "date", "2026-01-15", "description", "Lunch"));
        mvc.perform(post("/api/v1/transactions").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(ok)).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/accounts/" + account).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.currentBalance").value(60.0));

        String tooMuch = json.writeValueAsString(Map.of("accountId", account, "categoryId", category, "type", "EXPENSE",
                "amount", "60.01", "date", "2026-01-16"));
        mvc.perform(post("/api/v1/transactions").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(tooMuch)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/accounts/" + account).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.currentBalance").value(60.0));
    }

    @Test
    void adminEndpointsAreForbiddenForNormalUsers() throws Exception {
        String token = register("plain@test.com");
        mvc.perform(get("/api/v1/admin/stats").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }
}
