package ru.stas.pupkov.preparations.databases.code;

import java.util.List;

/**
 * Нарушения 2NF/3NF и декомпозиция к нормализованной схеме.
 */
public class NormalizationFormsDemo {

    /** Составной ключ (orderId, productId); productName зависит только от productId — не 2NF. */
    record OrderLineNot2NF(long orderId, long productId, String productName, int qty) {
    }

    record Product(long productId, String productName) {
    }

    record OrderLine(long orderId, long productId, int qty) {
    }

    /** deptName зависит от deptId, а не напрямую только от employeeId — не 3NF. */
    record EmployeeNot3NF(long employeeId, long deptId, String deptName) {
    }

    record Department(long deptId, String deptName) {
    }

    record Employee(long employeeId, long deptId) {
    }

    public static void main(String[] args) {
        List<OrderLineNot2NF> denormalizedLines = List.of(
                new OrderLineNot2NF(1, 10, "Keyboard", 2),
                new OrderLineNot2NF(1, 11, "Mouse", 1),
                new OrderLineNot2NF(2, 10, "Keyboard", 5)
        );
        System.out.println("not 2NF rows: " + denormalizedLines);
        System.out.println("anomaly: rename Keyboard -> must update multiple rows");

        List<Product> products = List.of(
                new Product(10, "Keyboard"),
                new Product(11, "Mouse")
        );
        List<OrderLine> lines = List.of(
                new OrderLine(1, 10, 2),
                new OrderLine(1, 11, 1),
                new OrderLine(2, 10, 5)
        );
        System.out.println("2NF products: " + products);
        System.out.println("2NF order_lines: " + lines);

        List<EmployeeNot3NF> denormalizedEmployees = List.of(
                new EmployeeNot3NF(1, 7, "Platform"),
                new EmployeeNot3NF(2, 7, "Platform"),
                new EmployeeNot3NF(3, 8, "Billing")
        );
        System.out.println("not 3NF rows: " + denormalizedEmployees);

        List<Department> departments = List.of(
                new Department(7, "Platform"),
                new Department(8, "Billing")
        );
        List<Employee> employees = List.of(
                new Employee(1, 7),
                new Employee(2, 7),
                new Employee(3, 8)
        );
        System.out.println("3NF departments: " + departments);
        System.out.println("3NF employees: " + employees);
    }
}
