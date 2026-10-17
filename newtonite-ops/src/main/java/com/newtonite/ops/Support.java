package com.newtonite.ops;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

enum Role { VIEWER, MEMBER, LEAD, ADMIN }   // ordinal order = privilege order

class ApiException extends RuntimeException {
  final int status; final Object data;
  ApiException(int status, String msg, Object data) { super(msg); this.status = status; this.data = data; }
  ApiException(int status, String msg) { this(status, msg, null); }
}

@RestControllerAdvice
class Errors {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<Map<String, Object>> handle(ApiException e) {
    Map<String, Object> b = new HashMap<>();
    b.put("error", e.getMessage());
    if (e.data != null) b.put("latest", e.data);   // on 409 the client gets the current item
    return ResponseEntity.status(e.status).body(b);
  }
}
