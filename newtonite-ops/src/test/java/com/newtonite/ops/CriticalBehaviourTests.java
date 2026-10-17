package com.newtonite.ops;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Seed users: 1 alice LEAD t1, 2 bob MEMBER t1, 3 carol MEMBER t1, 4 dave VIEWER t1, 5 erin LEAD t2
@SpringBootTest @Testcontainers
class CriticalBehaviourTests {
  @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");
  @Autowired ItemService s;

  long item(long uid, long team, String key) {
    return ((Number) s.create(uid, Map.of("title", "t", "type", "TASK", "priority", "URGENT", "teamId", team), key).get("id")).longValue();
  }
  long ver(long id) { return ((Number) s.find(id).get("version")).longValue(); }
  int status(Runnable r) { try { r.run(); return 200; } catch (ApiException e) { return e.status; } }

  @Test void onlyOneConcurrentClaimWins() throws Exception {
    long id = item(2, 1, null);
    var ex = Executors.newFixedThreadPool(2);
    List<Future<Integer>> f = List.of(ex.submit(() -> status(() -> s.claim(2, id))), ex.submit(() -> status(() -> s.claim(3, id))));
    int ok = 0, conflict = 0;
    for (var x : f) { int c = x.get(); if (c == 200) ok++; if (c == 409) conflict++; }
    assertEquals(1, ok); assertEquals(1, conflict);
  }
  @Test void staleUpdateRejectedAndDataUnchanged() {
    long id = item(2, 1, null); long v = ver(id);
    s.update(2, id, Map.of("title", "new", "version", v));
    assertEquals(409, status(() -> s.update(2, id, Map.of("title", "old", "version", v))));
    assertEquals("new", s.find(id).get("title"));
  }
  @Test void sameIdempotencyKeyCreatesOneItem() {
    String k = UUID.randomUUID().toString();
    assertEquals(item(2, 1, k), item(2, 1, k));
  }
  @Test void invalidJumpAndNonLeadApprovalRejected() {
    long id = item(2, 1, null);
    assertEquals(422, status(() -> s.transition(1, id, "CLOSED", ver(id))));
    s.claim(2, id);
    s.transition(2, id, "IN_PROGRESS", ver(id));
    s.transition(2, id, "AWAITING_APPROVAL", ver(id));
    assertEquals(403, status(() -> s.transition(2, id, "RESOLVED", ver(id))));
    assertEquals(200, status(() -> s.transition(1, id, "RESOLVED", ver(id))));
  }
  @Test void otherTeamAndViewerAreBlocked() {
    long id = item(2, 1, null);
    assertEquals(404, status(() -> s.get(5, id)));
    assertEquals(404, status(() -> s.claim(5, id)));
    assertEquals(403, status(() -> s.claim(4, id)));
  }
}
