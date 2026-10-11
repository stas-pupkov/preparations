package ru.stas.pupkov.preparations.core.code;

/**
 * {@code switch} по {@code String}: case-sensitive сравнение через {@code equals},
 * NPE на {@code null}-selector; Java 14+ switch expression для контраста.
 */
public class SwitchOnStringDemo {

    static String classifyStatement(String value) {
        switch (value) {
            case "ok":
            case "OK": // отдельный label — регистр важен, это НЕ ignore-case
                return "ascii ok variants need explicit cases";
            case "pending":
                return "pending";
            default:
                return "other: " + value;
        }
    }

    static String classifyExpression(String value) {
        // switch expression (14+): исчерпываемость / yield; null по-прежнему NPE до 21 pattern
        return switch (value) {
            case "ok" -> "ok";
            case "pending" -> "pending";
            default -> "other: " + value;
        };
    }

    public static void main(String[] args) {
        System.out.println("--- case sensitive ---");
        System.out.println("ok -> " + classifyStatement("ok"));
        System.out.println("OK -> " + classifyStatement("OK"));
        System.out.println("Ok -> " + classifyStatement("Ok"));

        System.out.println("--- null selector ---");
        try {
            System.out.println(classifyStatement(null));
        } catch (NullPointerException npe) {
            System.out.println("statement switch(null): " + npe.getClass().getSimpleName());
        }
        try {
            System.out.println(classifyExpression(null));
        } catch (NullPointerException npe) {
            System.out.println("expression switch(null): " + npe.getClass().getSimpleName());
        }

        System.out.println("--- switch expression ---");
        System.out.println(classifyExpression("pending"));
        System.out.println(classifyExpression("unknown"));

        System.out.println("--- bytecode idea: hashCode + equals, not identity ---");
        String a = new String("ok");
        System.out.println("new String(\"ok\") matches case \"ok\": " + classifyStatement(a));
    }
}
