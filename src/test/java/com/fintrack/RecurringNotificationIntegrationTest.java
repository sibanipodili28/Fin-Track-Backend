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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecurringNotificationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String register(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", "password123", "fullName", "T"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private JsonNode send(String token, String url, Object body, int expected) throws Exception {
        String res = mvc.perform(post(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "" : json.writeValueAsString(body))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return res.isBlank() ? null : json.readTree(res);
    }

    private JsonNode fetch(String token, String url) throws Exception {
        return json.readTree(mvc.perform(get(url).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private long expenseCategory(String token) throws Exception {
        return fetch(token, "/api/v1/categories?type=EXPENSE").get(0).get("id").asLong();
    }

    @Test
    void dueRuleIsPostedOnceAndRerunningDoesNotDuplicate() throws Exception {
        String t = register("recur@test.com");
        long account = send(t, "/api/v1/accounts", Map.of("name", "Main", "type", "SAVINGS", "initialBalance", "1000.00"), 201).get("id").asLong();
        long cat = expenseCategory(t);
        JsonNode rule = send(t, "/api/v1/recurring", Map.of("accountId", account, "categoryId", cat, "type", "EXPENSE", "amount", "200.00",
                "description", "Gym", "frequency", "DAILY", "startDate", LocalDate.now().toString()), 201);
        long id = rule.get("id").asLong();

        assertEquals(1, send(t, "/api/v1/recurring/run-due", null, 200).get("executed").asInt());
        assertEquals(0, send(t, "/api/v1/recurring/run-due", null, 200).get("executed").asInt());

        mvc.perform(get("/api/v1/accounts/" + account).header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.currentBalance").value(800.0));
        JsonNode hist = fetch(t, "/api/v1/recurring/" + id + "/history");
        assertEquals(1, hist.size());
        assertEquals("SUCCESS", hist.get(0).get("status").asText());
        assertEquals(LocalDate.now().plusDays(1).toString(), fetch(t, "/api/v1/recurring").get(0).get("nextExecutionDate").asText());
        assertEquals(1, fetch(t, "/api/v1/transactions").get("totalElements").asInt());

        JsonNode unread = fetch(t, "/api/v1/notifications/unread-count");
        assertEquals(1, unread.get("count").asInt());
    }

    @Test
    void insufficientBalanceIsRecordedAsFailureAndNothingIsPosted() throws Exception {
        String t = register("recurfail@test.com");
        long account = send(t, "/api/v1/accounts", Map.of("name", "Tiny", "type", "SAVINGS", "initialBalance", "50.00"), 201).get("id").asLong();
        long cat = expenseCategory(t);
        long id = send(t, "/api/v1/recurring", Map.of("accountId", account, "categoryId", cat, "type", "EXPENSE", "amount", "500.00",
                "frequency", "MONTHLY", "startDate", LocalDate.now().toString()), 201).get("id").asLong();

        JsonNode run = send(t, "/api/v1/recurring/run-due", null, 200);
        assertEquals(0, run.get("executed").asInt());
        assertEquals(1, run.get("failed").asInt());
        mvc.perform(get("/api/v1/accounts/" + account).header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.currentBalance").value(50.0));
        assertEquals("FAILED", fetch(t, "/api/v1/recurring/" + id + "/history").get(0).get("status").asText());
        assertEquals("RECURRING_FAILED", fetch(t, "/api/v1/notifications").get("content").get(0).get("type").asText());
    }

    @Test
    void pausedRulesDoNotRunAndStatesAreValidated() throws Exception {
        String t = register("pause@test.com");
        long account = send(t, "/api/v1/accounts", Map.of("name", "Main", "type", "SAVINGS", "initialBalance", "1000.00"), 201).get("id").asLong();
        long cat = expenseCategory(t);
        long id = send(t, "/api/v1/recurring", Map.of("accountId", account, "categoryId", cat, "type", "EXPENSE", "amount", "10.00",
                "frequency", "DAILY", "startDate", LocalDate.now().toString()), 201).get("id").asLong();
        send(t, "/api/v1/recurring/" + id + "/pause", null, 200);
        assertEquals(0, send(t, "/api/v1/recurring/run-due", null, 200).get("executed").asInt());
        send(t, "/api/v1/recurring/" + id + "/pause", null, 422);
        send(t, "/api/v1/recurring/" + id + "/resume", null, 200);
        send(t, "/api/v1/recurring/" + id + "/cancel", null, 200);
        send(t, "/api/v1/recurring/" + id + "/cancel", null, 422);
        send(t, "/api/v1/recurring", Map.of("accountId", account, "categoryId", cat, "type", "EXPENSE", "amount", "10.00",
                "frequency", "DAILY", "startDate", LocalDate.now().minusDays(1).toString()), 422);
    }

    @Test
    void budgetAlertsAreCreatedOnceAndNotificationsCanBeManaged() throws Exception {
        String t = register("alerts@test.com");
        long account = send(t, "/api/v1/accounts", Map.of("name", "Main", "type", "SAVINGS", "initialBalance", "5000.00"), 201).get("id").asLong();
        long cat = expenseCategory(t);
        send(t, "/api/v1/budgets", Map.of("categoryId", cat, "month", YearMonth.now().toString(), "amount", "1000.00"), 201);
        Map<String, Object> spend = Map.of("accountId", account, "categoryId", cat, "type", "EXPENSE", "amount", "850.00", "date", LocalDate.now().toString());
        send(t, "/api/v1/transactions", spend, 201);
        send(t, "/api/v1/transactions", Map.of("accountId", account, "categoryId", cat, "type", "EXPENSE", "amount", "10.00", "date", LocalDate.now().toString()), 201);
        // still near limit: only one NEAR_LIMIT alert so far
        assertEquals(1, fetch(t, "/api/v1/notifications").get("totalElements").asInt());
        send(t, "/api/v1/transactions", Map.of("accountId", account, "categoryId", cat, "type", "EXPENSE", "amount", "300.00", "date", LocalDate.now().toString()), 201);
        JsonNode all = fetch(t, "/api/v1/notifications");
        assertEquals(2, all.get("totalElements").asInt());
        assertEquals("BUDGET_EXCEEDED", all.get("content").get(0).get("type").asText());

        long first = all.get("content").get(0).get("id").asLong();
        send(t, "/api/v1/notifications/" + first + "/read", null, 204);
        assertEquals(1, fetch(t, "/api/v1/notifications/unread-count").get("count").asInt());
        send(t, "/api/v1/notifications/read-all", null, 204);
        assertEquals(0, fetch(t, "/api/v1/notifications/unread-count").get("count").asInt());
        mvc.perform(delete("/api/v1/notifications/" + first).header("Authorization", "Bearer " + t)).andExpect(status().isNoContent());
        assertEquals(1, fetch(t, "/api/v1/notifications").get("totalElements").asInt());
    }

    @Test
    void usersCannotTouchEachOthersRulesOrNotifications() throws Exception {
        String a = register("own-a@test.com");
        String b = register("own-b@test.com");
        long account = send(a, "/api/v1/accounts", Map.of("name", "Main", "type", "SAVINGS", "initialBalance", "100.00"), 201).get("id").asLong();
        long cat = expenseCategory(a);
        long id = send(a, "/api/v1/recurring", Map.of("accountId", account, "categoryId", cat, "type", "EXPENSE", "amount", "5.00",
                "frequency", "DAILY", "startDate", LocalDate.now().toString()), 201).get("id").asLong();
        send(b, "/api/v1/recurring/" + id + "/pause", null, 404);
        mvc.perform(get("/api/v1/recurring/" + id + "/history").header("Authorization", "Bearer " + b)).andExpect(status().isNotFound());
        assertEquals(0, fetch(b, "/api/v1/recurring").size());
        send(b, "/api/v1/recurring", Map.of("accountId", account, "categoryId", cat, "type", "EXPENSE", "amount", "5.00",
                "frequency", "DAILY", "startDate", LocalDate.now().toString()), 404);
    }
}
