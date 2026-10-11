package ru.stas.pupkov.preparations.databases.code;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Наглядная модель INNER / LEFT / RIGHT / FULL / CROSS JOIN на маленьких наборах.
 */
public class SqlJoinTypesDemo {

    record Emp(int id, String name, Integer deptId) {
    }

    record Dept(int id, String title) {
    }

    record Row(String emp, String dept) {
        @Override
        public String toString() {
            return "(" + emp + ", " + dept + ")";
        }
    }

    public static void main(String[] args) {
        List<Emp> emps = List.of(
                new Emp(1, "Ann", 10),
                new Emp(2, "Bob", 20),
                new Emp(3, "Eve", null)
        );
        List<Dept> depts = List.of(
                new Dept(10, "Eng"),
                new Dept(30, "HR")
        );

        System.out.println("INNER: " + innerJoin(emps, depts));
        System.out.println("LEFT:  " + leftJoin(emps, depts));
        System.out.println("RIGHT: " + rightJoin(emps, depts));
        System.out.println("FULL:  " + fullJoin(emps, depts));
        System.out.println("CROSS size: " + crossJoin(emps, depts).size()
                + " (emps * depts)");
    }

    private static List<Row> innerJoin(List<Emp> emps, List<Dept> depts) {
        List<Row> out = new ArrayList<>();
        for (Emp e : emps) {
            for (Dept d : depts) {
                if (Objects.equals(e.deptId(), d.id())) {
                    out.add(new Row(e.name(), d.title()));
                }
            }
        }
        return out;
    }

    private static List<Row> leftJoin(List<Emp> emps, List<Dept> depts) {
        List<Row> out = new ArrayList<>();
        for (Emp e : emps) {
            List<Dept> matched = depts.stream()
                    .filter(d -> Objects.equals(e.deptId(), d.id()))
                    .toList();
            if (matched.isEmpty()) {
                out.add(new Row(e.name(), null));
            } else {
                matched.forEach(d -> out.add(new Row(e.name(), d.title())));
            }
        }
        return out;
    }

    private static List<Row> rightJoin(List<Emp> emps, List<Dept> depts) {
        List<Row> out = new ArrayList<>();
        for (Dept d : depts) {
            List<Emp> matched = emps.stream()
                    .filter(e -> Objects.equals(e.deptId(), d.id()))
                    .toList();
            if (matched.isEmpty()) {
                out.add(new Row(null, d.title()));
            } else {
                matched.forEach(e -> out.add(new Row(e.name(), d.title())));
            }
        }
        return out;
    }

    private static List<Row> fullJoin(List<Emp> emps, List<Dept> depts) {
        List<Row> out = new ArrayList<>(leftJoin(emps, depts));
        for (Dept d : depts) {
            boolean seen = emps.stream().anyMatch(e -> Objects.equals(e.deptId(), d.id()));
            if (!seen) {
                out.add(new Row(null, d.title()));
            }
        }
        return out;
    }

    private static List<Row> crossJoin(List<Emp> emps, List<Dept> depts) {
        List<Row> out = new ArrayList<>();
        for (Emp e : emps) {
            for (Dept d : depts) {
                out.add(new Row(e.name(), d.title()));
            }
        }
        return out;
    }
}
