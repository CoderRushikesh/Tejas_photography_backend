package com.tejas.photography;

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

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Walks through the exact calls the React app makes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InquiryFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String send(String url, String body, String token, int expected) throws Exception {
        var req = post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) req.header("Authorization", "Bearer " + token);
        MvcResult r = mvc.perform(req).andExpect(status().is(expected)).andReturn();
        return r.getResponse().getContentAsString();
    }

    private String adminToken() throws Exception {
        String res = send("/api/auth/login", "{\"role\":\"admin\",\"email\":\"admin@test.com\",\"password\":\"Admin@12345\"}", null, 200);
        JsonNode n = json.readTree(res);
        assertEquals("admin", n.get("role").asText());
        return n.get("token").asText();
    }

    @Test
    void registerLoginAndRoleTabs() throws Exception {
        String res = send("/api/auth/register", "{\"name\":\"Asha\",\"phone\":\"9876543210\",\"email\":\"asha@example.com\",\"password\":\"secret1\"}", null, 201);
        JsonNode n = json.readTree(res);
        assertEquals("user", n.get("role").asText());
        assertEquals("+919876543210", n.get("phone").asText());

        send("/api/auth/register", "{\"name\":\"Asha\",\"phone\":\"9876543210\",\"email\":\"asha@example.com\",\"password\":\"secret1\"}", null, 409);
        send("/api/auth/login", "{\"role\":\"user\",\"email\":\"asha@example.com\",\"password\":\"secret1\"}", null, 200);
        send("/api/auth/login", "{\"role\":\"user\",\"email\":\"asha@example.com\",\"password\":\"wrong\"}", null, 401);
        // customer cannot use the Admin tab
        send("/api/auth/login", "{\"role\":\"admin\",\"email\":\"asha@example.com\",\"password\":\"secret1\"}", null, 401);
    }

    @Test
    void dashboardRequiresAdmin() throws Exception {
        mvc.perform(get("/api/inquiries")).andExpect(status().isUnauthorized());
        String reg = send("/api/auth/register", "{\"name\":\"Ravi\",\"phone\":\"9123456780\",\"email\":\"ravi@example.com\",\"password\":\"secret1\"}", null, 201);
        String userToken = json.readTree(reg).get("token").asText();
        mvc.perform(get("/api/inquiries").header("Authorization", "Bearer " + userToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/inquiries").header("Authorization", "Bearer " + adminToken())).andExpect(status().isOk());
    }

    @Test
    void enquiryToConfirmedWithoutDoubleBooking() throws Exception {
        String date = LocalDate.now().plusDays(30).toString();
        String body1 = "{\"name\":\"Meera\",\"phone\":\"9000000001\",\"email\":\"\",\"date\":\"" + date
                + "\",\"service\":\"Wedding Photography\",\"message\":\"hi\"}";
        String body2 = body1.replace("Meera", "Kiran").replace("9000000001", "9000000002");

        mvc.perform(get("/api/dates/check").param("date", date)).andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0)).andExpect(jsonPath("$.booked").value(false));

        long id1 = json.readTree(send("/api/inquiries", body1, null, 201)).get("id").asLong();
        long id2 = json.readTree(send("/api/inquiries", body2, null, 201)).get("id").asLong();
        mvc.perform(get("/api/dates/check").param("date", date))
                .andExpect(jsonPath("$.count").value(2)).andExpect(jsonPath("$.booked").value(false));

        String admin = adminToken();
        mvc.perform(patch("/api/inquiries/" + id1 + "/status").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"confirmed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("confirmed"))
                .andExpect(jsonPath("$.whatsappStatus").value("DISABLED"))
                .andExpect(jsonPath("$.whatsappLink").value(org.hamcrest.Matchers.startsWith("https://wa.me/919000000001?text=")));

        // second confirmation for the same date is refused
        mvc.perform(patch("/api/inquiries/" + id2 + "/status").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"confirmed\"}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/dates/check").param("date", date))
                .andExpect(jsonPath("$.booked").value(true));

        // rejecting frees the count
        mvc.perform(patch("/api/inquiries/" + id2 + "/status").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"rejected\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dates/check").param("date", date))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void validation() throws Exception {
        send("/api/inquiries", "{\"name\":\"\",\"phone\":\"1\"}", null, 400);
        send("/api/inquiries", "{\"name\":\"A\",\"phone\":\"9000000003\",\"date\":\"2001-01-01\"}", null, 400);
        mvc.perform(get("/api/dates/check").param("date", "nope")).andExpect(status().isBadRequest());
        mvc.perform(options("/api/inquiries").header("Origin", "https://tejas.vercel.app")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk());
    }
}
