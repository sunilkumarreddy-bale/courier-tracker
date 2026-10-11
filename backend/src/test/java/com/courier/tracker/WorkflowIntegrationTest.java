package com.courier.tracker;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:workflow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.demo-enabled=true",
      "app.email-enabled=false"
    })
@AutoConfigureMockMvc
@Transactional
class WorkflowIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired ParcelRepository parcels;
  @Autowired EventRepository events;
  @Autowired NotificationRepository notifications;

  String parcelBody() throws Exception {
    return json.writeValueAsString(
        Map.ofEntries(
            Map.entry("senderName", "Test Sender"),
            Map.entry("senderEmail", "sender@example.com"),
            Map.entry("senderPhone", "+91 9000000000"),
            Map.entry("senderAddress", "1 Sender Street"),
            Map.entry("senderCity", "Bengaluru"),
            Map.entry("recipientName", "Test Recipient"),
            Map.entry("recipientEmail", "recipient@example.com"),
            Map.entry("recipientPhone", "+91 9111111111"),
            Map.entry("recipientAddress", "2 Receiver Street"),
            Map.entry("recipientCity", "Mumbai"),
            Map.entry("description", "Books"),
            Map.entry("weightKg", 2.5),
            Map.entry("serviceType", "EXPRESS"),
            Map.entry("expectedDelivery", LocalDate.now(ZoneOffset.UTC).plusDays(2).toString())));
  }

  Long create() throws Exception {
    var result =
        mvc.perform(
                post("/api/parcels")
                    .with(csrf())
                    .contentType("application/json")
                    .content(parcelBody()))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  void change(Long id, String status, int expected) throws Exception {
    mvc.perform(
            patch("/api/parcels/{id}/status", id)
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"status\":\""
                        + status
                        + "\",\"location\":\"Mumbai\",\"note\":\"Test workflow update\"}"))
        .andExpect(status().is(expected));
  }

  @Test
  void loginRequiresCsrfAndCreatesSession() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"admin@courier.local\",\"password\":\"DemoAdmin!2026\"}"))
        .andExpect(status().isForbidden());
    var result =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content("{\"email\":\"admin@courier.local\",\"password\":\"DemoAdmin!2026\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andReturn();
    var session = (MockHttpSession) result.getRequest().getSession(false);
    assertNotNull(session);
    mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
    mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
        .andExpect(status().isNoContent());
    assertTrue(session.isInvalid());
  }

  @Test
  void rejectsInvalidLoginAndAnonymousManagement() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content("{\"email\":\"admin@courier.local\",\"password\":\"bad\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/parcels")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(username = "admin@courier.local", roles = "ADMIN")
  void completeWorkflowCreatesHistoryAndNotificationsAndRejectsTerminalEdits() throws Exception {
    Long id = create();
    Parcel p = parcels.findById(id).orElseThrow();
    assertTrue(p.trackingId.matches("CT-[A-F0-9]{32}"));
    change(id, "PICKED_UP", 409);
    Long agentId = users.findByEmail("agent@courier.local").orElseThrow().id;
    mvc.perform(
            patch("/api/parcels/{id}/assignment", id)
                .with(csrf())
                .contentType("application/json")
                .content("{\"agentId\":" + agentId + "}"))
        .andExpect(status().isOk());
    change(id, "DELIVERED", 409);
    change(id, "PICKED_UP", 200);
    change(id, "IN_TRANSIT", 200);
    change(id, "OUT_FOR_DELIVERY", 200);
    change(id, "DELIVERY_FAILED", 200);
    change(id, "OUT_FOR_DELIVERY", 200);
    change(id, "DELIVERED", 200);
    change(id, "RETURNED", 409);
    mvc.perform(
            patch("/api/parcels/{id}/assignment", id)
                .with(csrf())
                .contentType("application/json")
                .content("{\"agentId\":" + agentId + "}"))
        .andExpect(status().isConflict());
    assertEquals(8, events.findByParcelIdOrderByOccurredAtAscIdAsc(id).size());
    assertEquals(Status.DELIVERED, parcels.findById(id).orElseThrow().status);
    assertEquals(
        7,
        notifications
            .findTop100ByUserIdOrderByCreatedAtDescIdDesc(
                users.findByEmail("admin@courier.local").orElseThrow().id)
            .stream()
            .filter(n -> n.parcel.id.equals(id))
            .count());
  }

  @Test
  @WithMockUser(username = "admin@courier.local", roles = "ADMIN")
  void publicTrackingDoesNotExposeContactsAddressesNotesOrAgent() throws Exception {
    Long id = create();
    String trackingId = parcels.findById(id).orElseThrow().trackingId;
    mvc.perform(get("/api/tracking/{id}", trackingId.toLowerCase()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.origin").value("Bengaluru"))
        .andExpect(jsonPath("$.recipientEmail").doesNotExist())
        .andExpect(jsonPath("$.recipientAddress").doesNotExist())
        .andExpect(jsonPath("$.senderName").doesNotExist())
        .andExpect(jsonPath("$.agent").doesNotExist())
        .andExpect(jsonPath("$.history[0].note").doesNotExist());
    mvc.perform(get("/api/tracking/CT-MISSING")).andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(username = "priya@courier.local", roles = "AGENT")
  void agentCannotReadOtherParcelsOrCreateManageTeamOrReadOthersNotifications() throws Exception {
    Long id = parcels.findByTrackingId("CT-DEMO-1002").orElseThrow().id;
    mvc.perform(get("/api/parcels"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get("/api/dashboard"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(get("/api/parcels/{id}", id)).andExpect(status().isNotFound());
    change(id, "IN_TRANSIT", 404);
    mvc.perform(
            post("/api/parcels").with(csrf()).contentType("application/json").content(parcelBody()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/agents")).andExpect(status().isForbidden());
    mvc.perform(
            patch("/api/notifications/{id}/read", notifications.findAll().get(0).id).with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(username = "agent@courier.local", roles = "AGENT")
  void agentCanUpdateAssignedParcelAndReceivesAssignments() throws Exception {
    Long id = parcels.findByTrackingId("CT-DEMO-1002").orElseThrow().id;
    change(id, "IN_TRANSIT", 200);
    mvc.perform(get("/api/notifications"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("New delivery assignment"));
    mvc.perform(get("/api/parcels")).andExpect(jsonPath("$.totalElements").value(5));
  }

  @Test
  @WithMockUser(username = "admin@courier.local", roles = "ADMIN")
  void validatesDatesWeightStatusPaginationAndLiteralSearch() throws Exception {
    mvc.perform(
            post("/api/parcels")
                .with(csrf())
                .contentType("application/json")
                .content(parcelBody().replace("2.5", "0")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.weightKg").exists());
    mvc.perform(get("/api/parcels").param("status", "BOGUS")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/parcels").param("size", "10000")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/parcels").param("page", "-1")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/parcels").param("search", "%"))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get("/api/parcels").param("search", "ct-demo-100").param("size", "2"))
        .andExpect(jsonPath("$.totalElements").value(6))
        .andExpect(jsonPath("$.content.length()").value(2));
  }

  @Test
  @WithMockUser(username = "admin@courier.local", roles = "ADMIN")
  void adminCanCancelAndMarkNotificationsRead() throws Exception {
    Long id = create();
    change(id, "CANCELLED", 200);
    change(id, "PICKED_UP", 409);
    Long notificationId =
        notifications
            .findTop100ByUserIdOrderByCreatedAtDescIdDesc(
                users.findByEmail("admin@courier.local").orElseThrow().id)
            .get(0)
            .id;
    mvc.perform(patch("/api/notifications/{id}/read", notificationId).with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/notifications"))
        .andExpect(jsonPath("$[0].read").value(true))
        .andExpect(jsonPath("$[0].emailStatus").value("NOT_CONFIGURED"));
  }

  @Test
  @WithMockUser(username = "admin@courier.local", roles = "ADMIN")
  void createsAgentAndRejectsDuplicateEmail() throws Exception {
    String body =
        "{\"name\":\"New"
            + " Agent\",\"email\":\"new@example.com\",\"password\":\"StrongPassword!2026\"}";
    mvc.perform(post("/api/agents").with(csrf()).contentType("application/json").content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
    mvc.perform(post("/api/agents").with(csrf()).contentType("application/json").content(body))
        .andExpect(status().isConflict());
    assertNotEquals(
        "StrongPassword!2026", users.findByEmail("new@example.com").orElseThrow().passwordHash);
  }
}
