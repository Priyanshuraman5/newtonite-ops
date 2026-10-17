package com.newtonite.ops;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
class Api {
  final ItemService s; final Auth auth;
  Api(ItemService s, Auth auth) { this.s = s; this.auth = auth; }
  @PostMapping("/auth/login") Map<String, String> login(@RequestBody Map<String, String> b) { return Map.of("token", auth.login(b.get("email"), b.get("password"))); }
  @GetMapping("/items") Map<String, Object> list(@RequestAttribute("uid") long uid, @RequestParam Map<String, String> p) { return s.list(uid, p); }
  @PostMapping("/items") ResponseEntity<Map<String, Object>> create(@RequestAttribute("uid") long uid, @RequestHeader(value = "Idempotency-Key", required = false) String k, @RequestBody Map<String, Object> b) { return ResponseEntity.status(201).body(s.create(uid, b, k)); }
  @GetMapping("/items/{id}") Map<String, Object> get(@RequestAttribute("uid") long uid, @PathVariable long id) { return s.get(uid, id); }
  @PatchMapping("/items/{id}") Map<String, Object> patch(@RequestAttribute("uid") long uid, @PathVariable long id, @RequestBody Map<String, Object> b) { return s.update(uid, id, b); }
  @PostMapping("/items/{id}/claim") Map<String, Object> claim(@RequestAttribute("uid") long uid, @PathVariable long id) { return s.claim(uid, id); }
  @PostMapping("/items/{id}/transition") Map<String, Object> transition(@RequestAttribute("uid") long uid, @PathVariable long id, @RequestBody Map<String, Object> b) { return s.transition(uid, id, (String) b.get("to"), ((Number) b.get("version")).longValue()); }
  @GetMapping("/items/{id}/activity") List<Map<String, Object>> activity(@RequestAttribute("uid") long uid, @PathVariable long id) { return s.activity(uid, id); }
  @PostMapping("/items/{id}/comments") ResponseEntity<Void> comment(@RequestAttribute("uid") long uid, @PathVariable long id, @RequestBody Map<String, String> b) { s.comment(uid, id, b.get("text")); return ResponseEntity.status(201).build(); }
}
