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

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BudgetGoalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String register(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", "password123", "fullName", "T"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private JsonNode postJson(String token, String url, Object body, int expected) throws Exception {
        String res = mvc.perform(post(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return res.isBlank() ? null : json.readTree(res);
    }

    private long expenseCategory(String token) throws Exception {
        String res = mvc.perform(get("/api/v1/categories").param("type", "EXPENSE").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get(0).get("id").asLong();
    }

    @Test
    void budgetShowsSpendingFromRealExpensesAndRejectsDuplicates() throws Exception {
        String t = register("budget@test.com");
        long account = postJson(t, "/api/v1/accounts", Map.of("name", "Main", "type", "SAVINGS", "initialBalance", "10000.00"), 201).get("id").asLong();
        long food = expenseCategory(t);
        String month = YearMonth.now().toString();

        postJson(t, "/api/v1/budgets", Map.of("categoryId", food, "month", month, "amount", "1000.00"), 201);
        postJson(t, "/api/v1/budgets", Map.of("categoryId", food, "month", month, "amount", "500.00"), 409);
        postJson(t, "/api/v1/transactions", Map.of("accountId", account, "categoryId", food, "type", "EXPENSE",
                "amount", "850.00", "date", LocalDate.now().toString()), 201);

        String res = mvc.perform(get("/api/v1/budgets").param("month", month).header("Authorization", "Bearer " + t))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode o = json.readTree(res);
        assertEquals(850.0, o.get("totalSpent").asDouble());
        assertEquals(150.0, o.get("totalRemaining").asDouble());
        assertEquals("NEAR_LIMIT", o.get("items").get(0).get("status").asText());
        assertEquals(1, o.get("alerts").size());
    }

    @Test
    void goalContributionDoesNotChangeAccountBalanceAndRespectsLimits() throws Exception {
        String t = register("goal@test.com");
        long account = postJson(t, "/api/v1/accounts", Map.of("name", "Main", "type", "SAVINGS", "initialBalance", "1000.00"), 201).get("id").asLong();
        long goal = postJson(t, "/api/v1/goals", Map.of("name", "Laptop", "targetAmount", "800.00"), 201).get("id").asLong();

        JsonNode after = postJson(t, "/api/v1/goals/" + goal + "/contributions", Map.of("amount", "300.00"), 201);
        assertEquals(300.0, after.get("savedAmount").asDouble());
        mvc.perform(get("/api/v1/accounts/" + account).header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.currentBalance").value(1000.0));

        postJson(t, "/api/v1/goals/" + goal + "/contributions", Map.of("amount", "600.00"), 422);   // more than remaining
        JsonNode done = postJson(t, "/api/v1/goals/" + goal + "/contributions", Map.of("amount", "500.00"), 201);
        assertEquals("COMPLETED", done.get("status").asText());
        postJson(t, "/api/v1/goals/" + goal + "/contributions", Map.of("amount", "1.00"), 422);     // completed
    }

    @Test
    void goalsAndBudgetsAreIsolatedBetweenUsers() throws Exception {
        String a = register("iso-a@test.com");
        String b = register("iso-b@test.com");
        postJson(a, "/api/v1/accounts", Map.of("name", "Main", "type", "SAVINGS", "initialBalance", "500.00"), 201);
        long goal = postJson(a, "/api/v1/goals", Map.of("name", "Trip", "targetAmount", "100.00"), 201).get("id").asLong();
        postJson(b, "/api/v1/goals/" + goal + "/contributions", Map.of("amount", "10.00"), 404);
        mvc.perform(get("/api/v1/goals/" + goal + "/contributions").header("Authorization", "Bearer " + b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/goals").header("Authorization", "Bearer " + b)).andExpect(content().json("[]"));
    }
}
