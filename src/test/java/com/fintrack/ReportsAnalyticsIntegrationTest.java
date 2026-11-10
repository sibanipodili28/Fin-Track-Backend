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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportsAnalyticsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String register(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", "password123", "fullName", "T"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private JsonNode send(String token, String url, Object body) throws Exception {
        String res = mvc.perform(post(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return json.readTree(res);
    }

    private JsonNode fetch(String token, String url) throws Exception {
        return json.readTree(mvc.perform(get(url).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private long category(String token, String type) throws Exception {
        return fetch(token, "/api/v1/categories?type=" + type).get(0).get("id").asLong();
    }

    private double summary(JsonNode report, String label) {
        for (JsonNode s : report.get("summary")) if (s.get("label").asText().equals(label)) return s.get("value").asDouble();
        fail("no summary item " + label);
        return 0;
    }

    private long setup(String token) throws Exception {
        long account = send(token, "/api/v1/accounts", Map.of("name", "Main", "type", "SAVINGS", "initialBalance", "1000.00")).get("id").asLong();
        String today = LocalDate.now().toString();
        send(token, "/api/v1/transactions", Map.of("accountId", account, "categoryId", category(token, "INCOME"), "type", "INCOME", "amount", "5000.00", "date", today));
        send(token, "/api/v1/transactions", Map.of("accountId", account, "categoryId", category(token, "EXPENSE"), "type", "EXPENSE", "amount", "1200.00", "date", today, "description", "Groceries, bulk"));
        return account;
    }

    @Test
    void monthlyReportIsAvailableInEveryFormat() throws Exception {
        String t = register("rep@test.com");
        setup(t);
        String month = YearMonth.now().toString();

        JsonNode report = fetch(t, "/api/v1/reports/monthly?month=" + month);
        assertEquals(5000.0, summary(report, "Total income"));
        assertEquals(1200.0, summary(report, "Total expenses"));
        assertEquals(3800.0, summary(report, "Net savings"));

        var csv = mvc.perform(get("/api/v1/reports/monthly?format=csv&month=" + month).header("Authorization", "Bearer " + t))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse();
        assertTrue(csv.getContentType().startsWith("text/csv"));
        assertTrue(new String(csv.getContentAsByteArray(), StandardCharsets.UTF_8).contains("Category"));

        byte[] xlsx = mvc.perform(get("/api/v1/reports/monthly?format=xlsx").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertEquals('P', xlsx[0]);
        assertEquals('K', xlsx[1]);

        byte[] pdf = mvc.perform(get("/api/v1/reports/monthly?format=pdf").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void statementClosingBalanceMatchesTheAccountBalance() throws Exception {
        String t = register("stmt@test.com");
        long account = setup(t);
        long other = send(t, "/api/v1/accounts", Map.of("name", "Cash", "type", "CASH_WALLET", "initialBalance", "0.00")).get("id").asLong();
        send(t, "/api/v1/transfers", Map.of("fromAccountId", account, "toAccountId", other, "amount", "300.00"));

        String from = LocalDate.now().minusYears(1).toString();
        String to = LocalDate.now().plusDays(1).toString();
        JsonNode report = fetch(t, "/api/v1/reports/account-statement?accountId=" + account + "&from=" + from + "&to=" + to);
        double balance = fetch(t, "/api/v1/accounts/" + account).get("currentBalance").asDouble();
        assertEquals(balance, summary(report, "Closing balance"), 0.001);
        assertEquals(1000.0, summary(report, "Opening balance"), 0.001);
        assertEquals(3, report.get("rows").size());
    }

    @Test
    void invalidRequestsAndOtherUsersDataAreRejected() throws Exception {
        String a = register("rep-a@test.com");
        String b = register("rep-b@test.com");
        long account = setup(a);
        mvc.perform(get("/api/v1/reports/payroll").header("Authorization", "Bearer " + a)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/monthly?format=docx").header("Authorization", "Bearer " + a)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/account-statement?accountId=" + account).header("Authorization", "Bearer " + b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/reports/account-statement").header("Authorization", "Bearer " + a)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/monthly")).andExpect(status().isUnauthorized());
        assertEquals(0.0, summary(fetch(b, "/api/v1/reports/monthly"), "Total income"));
    }

    @Test
    void budgetAndSavingsReportsWork() throws Exception {
        String t = register("rep-bs@test.com");
        setup(t);
        send(t, "/api/v1/budgets", Map.of("categoryId", category(t, "EXPENSE"), "month", YearMonth.now().toString(), "amount", "2000.00"));
        send(t, "/api/v1/goals", Map.of("name", "Trip", "targetAmount", "900.00"));
        assertEquals(2000.0, summary(fetch(t, "/api/v1/reports/budget"), "Total allocated"));
        assertEquals(900.0, summary(fetch(t, "/api/v1/reports/savings"), "Total target"));
    }

    @Test
    void analyticsReturnsAggregatedChartData() throws Exception {
        String t = register("ana@test.com");
        setup(t);
        JsonNode o = fetch(t, "/api/v1/analytics/overview");
        assertEquals(6, o.get("monthly").size());
        JsonNode last = o.get("monthly").get(5);
        assertEquals(5000.0, last.get("income").asDouble());
        assertEquals(1200.0, last.get("expense").asDouble());
        assertEquals(3800.0, last.get("savings").asDouble());
        assertEquals(1, o.get("expenseByCategory").size());
        assertEquals(1, o.get("accountBalances").size());

        JsonNode y = fetch(t, "/api/v1/analytics/yearly-comparison");
        assertEquals(12, y.get("months").size());
        mvc.perform(get("/api/v1/analytics/overview?from=2026-12-01&to=2026-01-01").header("Authorization", "Bearer " + t)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/analytics/yearly-comparison?year=1800").header("Authorization", "Bearer " + t)).andExpect(status().isBadRequest());
        assertEquals(0, fetch(register("ana2@test.com"), "/api/v1/analytics/overview").get("expenseByCategory").size());
    }
}
