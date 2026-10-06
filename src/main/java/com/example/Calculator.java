package com.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Calculator {

    @GetMapping("/add")
    public int add(
            @RequestParam int a,
            @RequestParam int b) {
        return a + b;
    }

    @GetMapping("/subtract")
    public int subtract(
            @RequestParam int a,
            @RequestParam int b) {
        return a - b;
    }

    @GetMapping("/multiply")
    public int multiply(
            @RequestParam int a,
            @RequestParam int b) {
        return a * b;
    }

    @GetMapping("/divide")
    public int divide(
            @RequestParam int a,
            @RequestParam int b) {

        if (b == 0) {
            throw new IllegalArgumentException("Cannot divide by zero");
        }

        return a / b;
    }
}
