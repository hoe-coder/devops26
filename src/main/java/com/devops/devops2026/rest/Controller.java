package com.devops.devops2026.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    @GetMapping("/")
    public String hello() {
        return "Hello!";
    }

    @GetMapping("/test/{name}")
    public ResponseEntity<String> test(@PathVariable String name) {
        return ResponseEntity.ok(name);
    }
}
