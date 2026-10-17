package com.newtonite.ops;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ItemService {
  // Workflow: "from>to" -> minimum role. Anything not listed is an invalid transition.
  static final Map<String, Role> RULES = Map.of(
    "OPEN>IN_PROGRESS", Role.MEMBER, "IN_PROGRESS>AWAITING_APPROVAL", Role.MEMBER,
    "AWAITING_APPROVAL>RESOLVED", Role.LEAD, "AWAITING_APPROVAL>IN_PROGRESS", Role.LEAD,
    "RESOLVED>CLOSED", Role.LEAD);
  final JdbcTemplate db; final Permissions perm;
  ItemService(JdbcTemplate db, Permissions perm) { this.db = db; this.perm = perm; }

  static boolean uidEq(Map<String, Object> i, String f, long uid) { return Long.valueOf(uid).equals(i.get(f)); }
  static long team(Map<String, Object> i) { return ((Number) i.get("team_id")).longValue(); }

  void log(long item, long actor, String type, String oldV, String newV) {   // always called inside the caller's transaction
    db.update("insert into activity_events(item_id, actor_id, event_type, old_value, new_value) values(?,?,?,?,?)", item, actor, type, oldV, newV);
  }
  Map<String, Object> find(long id) {
    var r = db.queryForList("select * from work_items where id = ?", id);
    if (r.isEmpty()) throw new ApiException(404, "not found");
    return r.get(0);
  }
  Map<String, Object> get(long uid, long id) { var i = find(id); perm.require(uid, team(i), Role.VIEWER); return i; }

  @Transactional
  Map<String, Object> create(long uid, Map<String, Object> b, String key) {
    long team = ((Number) b.get("teamId")).longValue();
    perm.require(uid, team, Role.MEMBER);
    if (key != null) {
      // Concurrent duplicate blocks here on the primary key until the first request commits, then sees 0 rows.
      int n = db.update("insert into idempotency_keys(user_id, idem_key) values(?,?) on conflict do nothing", uid, key);
      if (n == 0) return find(db.queryForObject("select item_id from idempotency_keys where user_id = ? and idem_key = ?", Long.class, uid, key));
    }
    Object due = b.get("dueDate") == null ? null : java.sql.Date.valueOf((String) b.get("dueDate"));
    long id = db.queryForObject("insert into work_items(title, description, type, priority, team_id, created_by, due_date) values(?,?,?,?,?,?,?) returning id",
      Long.class, b.get("title"), b.get("description"), b.get("type"), b.getOrDefault("priority", "MEDIUM"), team, uid, due);
    log(id, uid, "CREATED", null, null);
    if (key != null) db.update("update idempotency_keys set item_id = ? where user_id = ? and idem_key = ?", id, uid, key);
    return find(id);
  }

  Map<String, Object> list(long uid, Map<String, String> p) {
    var sql = new StringBuilder("select * from work_items w where w.team_id in (select team_id from memberships where user_id = ?)");
    List<Object> a = new ArrayList<>(List.of(uid));
    switch (p.getOrDefault("view", "")) {
      case "mine" -> { sql.append(" and w.assignee_id = ?"); a.add(uid); }
      case "urgent" -> sql.append(" and w.assignee_id is null and w.priority = 'URGENT'");
      case "approval" -> { sql.append(" and w.status = 'AWAITING_APPROVAL' and w.team_id in (select team_id from memberships where user_id = ? and role in ('LEAD','ADMIN'))"); a.add(uid); }
      default -> { }
    }
    for (String f : List.of("status", "priority", "type")) if (p.containsKey(f)) { sql.append(" and w." + f + " = ?"); a.add(p.get(f)); }
    if (p.containsKey("assignee")) { sql.append(" and w.assignee_id = ?"); a.add(Long.valueOf(p.get("assignee"))); }
    if (p.containsKey("q")) { sql.append(" and to_tsvector('english', w.title || ' ' || coalesce(w.description, '')) @@ plainto_tsquery('english', ?)"); a.add(p.get("q")); }
    if (p.containsKey("cursor")) { sql.append(" and w.id < ?"); a.add(Long.valueOf(p.get("cursor"))); }   // keyset pagination, not OFFSET
    int lim = Math.min(Integer.parseInt(p.getOrDefault("limit", "20")), 100);
    sql.append(" order by w.id desc limit ?"); a.add(lim + 1);
    var rows = db.queryForList(sql.toString(), a.toArray());
    Object next = null;
    if (rows.size() > lim) { rows = rows.subList(0, lim); next = rows.get(lim - 1).get("id"); }
    Map<String, Object> out = new HashMap<>(); out.put("items", rows); out.put("nextCursor", next);
    return out;
  }

  @Transactional
  Map<String, Object> update(long uid, long id, Map<String, Object> b) {
    var i = find(id); long t = team(i);
    perm.require(uid, t, Role.MEMBER);
    boolean lead = perm.roleIn(uid, t).ordinal() >= Role.LEAD.ordinal();
    if (!lead && !uidEq(i, "assignee_id", uid) && !uidEq(i, "created_by", uid)) throw new ApiException(403, "forbidden");
    long v = ((Number) b.get("version")).longValue();
    String np = (String) b.getOrDefault("priority", i.get("priority"));
    int n = db.update("update work_items set title = ?, description = ?, priority = ?, version = version + 1, updated_at = now() where id = ? and version = ?",
      b.getOrDefault("title", i.get("title")), b.getOrDefault("description", i.get("description")), np, id, v);
    if (n == 0) throw new ApiException(409, "item changed, reload", find(id));   // optimistic lock
    if (!np.equals(i.get("priority"))) log(id, uid, "PRIORITY_CHANGED", (String) i.get("priority"), np);
    return find(id);
  }

  @Transactional
  Map<String, Object> claim(long uid, long id) {
    var i = find(id); perm.require(uid, team(i), Role.MEMBER);
    // Single conditional UPDATE: the loser of a race re-evaluates "assignee_id is null" after the winner commits -> 0 rows.
    int n = db.update("update work_items set assignee_id = ?, version = version + 1, updated_at = now() where id = ? and assignee_id is null", uid, id);
    if (n == 0) throw new ApiException(409, "already claimed", find(id));
    log(id, uid, "CLAIMED", null, String.valueOf(uid));
    return find(id);
  }

  @Transactional
  Map<String, Object> transition(long uid, long id, String to, long version) {
    var i = find(id); long t = team(i);
    perm.require(uid, t, Role.VIEWER);
    String from = (String) i.get("status");
    Role need = RULES.get(from + ">" + to);
    if (need == null) throw new ApiException(422, "invalid transition " + from + " -> " + to);
    perm.require(uid, t, need);
    boolean lead = perm.roleIn(uid, t).ordinal() >= Role.LEAD.ordinal();
    if (!lead && !uidEq(i, "assignee_id", uid)) throw new ApiException(403, "only the assignee or a lead can move this item");
    if (to.equals("RESOLVED") && uidEq(i, "assignee_id", uid)) throw new ApiException(403, "cannot approve your own item");
    int n = db.update("update work_items set status = ?, version = version + 1, updated_at = now() where id = ? and version = ? and status = ?", to, id, version, from);
    if (n == 0) throw new ApiException(409, "item changed, reload", find(id));
    log(id, uid, "STATE_CHANGED", from, to);
    return find(id);
  }

  @Transactional
  void comment(long uid, long id, String text) { var i = find(id); perm.require(uid, team(i), Role.MEMBER); log(id, uid, "COMMENT", null, text); }
  List<Map<String, Object>> activity(long uid, long id) { get(uid, id); return db.queryForList("select * from activity_events where item_id = ? order by id", id); }
}
