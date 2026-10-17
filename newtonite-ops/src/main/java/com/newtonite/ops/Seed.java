package com.newtonite.ops;
import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class Seed implements CommandLineRunner {
  final JdbcTemplate db;
  Seed(JdbcTemplate db) { this.db = db; }
  public void run(String... args) {
    if (db.queryForObject("select count(*) from users", Integer.class) > 0) return;
    String pw = new BCryptPasswordEncoder().encode("password123");
    for (String t : List.of("Payments Ops", "Customer Support", "Compliance")) db.update("insert into teams(name) values(?)", t);
    for (String n : List.of("alice", "bob", "carol", "dave", "erin", "frank", "grace", "heidi")) db.update("insert into users(email, password, name) values(?,?,?)", n + "@ops.test", pw, n);
    // user id, team id, role (ids are 1..8 / 1..3 on a fresh DB)
    String[][] m = {{"1","1","LEAD"},{"2","1","MEMBER"},{"3","1","MEMBER"},{"4","1","VIEWER"},{"5","2","LEAD"},{"6","2","MEMBER"},{"7","3","MEMBER"},{"8","3","LEAD"}};
    for (String[] r : m) db.update("insert into memberships(user_id, team_id, role) values(?,?,?)", Long.valueOf(r[0]), Long.valueOf(r[1]), r[2]);
    var rnd = new Random(42);
    String[] ty = {"INCIDENT", "CUSTOMER_ISSUE", "PAYMENT", "COMPLIANCE", "TASK"}, pr = {"LOW", "MEDIUM", "HIGH", "URGENT"}, st = {"OPEN", "OPEN", "IN_PROGRESS", "RESOLVED"};
    List<Object[]> rows = new ArrayList<>();
    for (int i = 0; i < 5000; i++) {
      int team = 1 + rnd.nextInt(3); String s = st[rnd.nextInt(4)];
      long creator = team == 1 ? 2 : team == 2 ? 6 : 7;
      Long assignee = s.equals("OPEN") ? null : creator;
      rows.add(new Object[]{"Item " + i + " " + ty[rnd.nextInt(5)], "Seeded item", ty[rnd.nextInt(5)], pr[rnd.nextInt(4)], s, (long) team, assignee, creator});
    }
    db.batchUpdate("insert into work_items(title, description, type, priority, status, team_id, assignee_id, created_by) values(?,?,?,?,?,?,?,?)", rows);
  }
}
