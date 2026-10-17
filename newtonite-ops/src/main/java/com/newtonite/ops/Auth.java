package com.newtonite.ops;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Service
class Auth {
  final JdbcTemplate db; final Map<String, Long> tokens = new ConcurrentHashMap<>();
  final BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
  Auth(JdbcTemplate db) { this.db = db; }
  String login(String email, String pw) {
    var rows = db.queryForList("select id, password from users where email = ?", email);
    if (rows.isEmpty() || !enc.matches(pw, (String) rows.get(0).get("password"))) throw new ApiException(401, "bad credentials");
    String t = UUID.randomUUID().toString();
    tokens.put(t, ((Number) rows.get(0).get("id")).longValue());
    return t;
  }
  Long user(String token) { return tokens.get(token); }
}

/** The ONE place authorization is decided. Every service method calls require(). */
@Service
class Permissions {
  final JdbcTemplate db;
  Permissions(JdbcTemplate db) { this.db = db; }
  Role roleIn(long uid, long team) {
    var r = db.queryForList("select role from memberships where user_id = ? and team_id = ?", String.class, uid, team);
    return r.isEmpty() ? null : Role.valueOf(r.get(0));
  }
  void require(long uid, long team, Role min) {
    Role r = roleIn(uid, team);
    if (r == null) throw new ApiException(404, "not found");      // outsiders can't learn the item exists
    if (r.ordinal() < min.ordinal()) throw new ApiException(403, "forbidden");
  }
}

@Component
class TokenFilter extends OncePerRequestFilter {
  final Auth auth;
  TokenFilter(Auth auth) { this.auth = auth; }
  @Override protected void doFilterInternal(HttpServletRequest rq, HttpServletResponse rs, FilterChain c) throws ServletException, IOException {
    if (rq.getRequestURI().equals("/auth/login")) { c.doFilter(rq, rs); return; }
    String h = rq.getHeader("Authorization");
    Long uid = h != null && h.startsWith("Bearer ") ? auth.user(h.substring(7)) : null;
    if (uid == null) { rs.setStatus(401); rs.setContentType("application/json"); rs.getWriter().write("{\"error\":\"unauthorized\"}"); return; }
    rq.setAttribute("uid", uid);
    c.doFilter(rq, rs);
  }
}
