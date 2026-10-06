package com.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculatorTest {

    Calculator calculator = new Calculator();

    @Test
    void testAdd() {
        assertEquals(5, calculator.add(2, 3));
    }

    @Test
    void testSubtract() {
        assertEquals(2, calculator.subtract(5, 3));
    }

    @Test
    void testMultiply() {
        assertEquals(6, calculator.multiply(2, 3));
    }
}
