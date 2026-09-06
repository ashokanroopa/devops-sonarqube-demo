package com.example;

public class Calculator {

    public int add(int a, int b) {
        return a + b;
    }

    public int subtract(int a, int b) {
        return a - b;
    }

    public int multiply(int a, int b) {
        return a * b;
    }

    public int divide(int a, int b) {
        if (b == 0) {
            throw new IllegalArgumentException("Cannot divide by zero");
        }

        return a / b;
    }

    public static void main(String[] args) {

        Calculator calculator = new Calculator();

        System.out.println("Addition: "
                + calculator.add(10, 20));

        System.out.println("Subtraction: "
                + calculator.subtract(20, 10));
    }
}
