package ru.stas.pupkov.preparations.databases.code;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Учебная модель составного B-tree: порядок столбцов = порядок сортировки ключей,
 * поиск эффективен по левому префиксу без дырок.
 */
public class CompositeIndexDemo {

    record Row(int userId, String name, int taxId) {
        @Override
        public String toString() {
            return "(" + userId + ", " + name + ", " + taxId + ")";
        }
    }

    public static void main(String[] args) {
        List<Row> table = List.of(
                new Row(1, "Anna", 100),
                new Row(1, "Anna", 200),
                new Row(1, "Boris", 50),
                new Row(2, "Anna", 10),
                new Row(2, "Zoe", 1)
        );

        System.out.println("=== индекс (user_id, name, tax_id) — порядок в листьях ===");
        List<Row> byUserNameTax = sorted(table,
                Comparator.comparingInt(Row::userId)
                        .thenComparing(Row::name)
                        .thenComparingInt(Row::taxId));
        byUserNameTax.forEach(System.out::println);

        System.out.println("\n--- WHERE user_id=1 AND name='Anna' AND tax_id=200 (полный префикс) ---");
        System.out.println(seekFull(byUserNameTax, 1, "Anna", 200));

        System.out.println("\n--- WHERE user_id=1 (только левый столбец) ---");
        System.out.println(scanPrefixUser(byUserNameTax, 1));

        System.out.println("\n--- WHERE user_id=1 AND tax_id=200 (дырка в name) ---");
        System.out.println("seek по user_id, затем фильтр tax_id: "
                + scanPrefixUser(byUserNameTax, 1).stream()
                .filter(r -> r.taxId() == 200)
                .toList());

        System.out.println("\n--- WHERE tax_id=200 alone — по этому порядку ключей seek нет ---");
        System.out.println("пришлось бы сканировать весь индекс: "
                + byUserNameTax.stream().filter(r -> r.taxId() == 200).toList());

        System.out.println("\n=== тот же набор, индекс (tax_id, name, user_id) ===");
        List<Row> byTaxNameUser = sorted(table,
                Comparator.comparingInt(Row::taxId)
                        .thenComparing(Row::name)
                        .thenComparingInt(Row::userId));
        byTaxNameUser.forEach(System.out::println);
        System.out.println("WHERE tax_id=200 → плотный кусок: "
                + scanPrefixTax(byTaxNameUser, 200));
        System.out.println("WHERE user_id=1 → строки размазаны, seek по user_id нет");
    }

    private static List<Row> sorted(List<Row> rows, Comparator<Row> order) {
        List<Row> copy = new ArrayList<>(rows);
        copy.sort(order);
        return List.copyOf(copy);
    }

    private static List<Row> seekFull(List<Row> index, int userId, String name, int taxId) {
        // аналог точечного lookup по полному ключу
        return index.stream()
                .filter(r -> r.userId() == userId && r.name().equals(name) && r.taxId() == taxId)
                .toList();
    }

    private static List<Row> scanPrefixUser(List<Row> index, int userId) {
        // после seek на первый userId — плотный диапазон, пока userId совпадает
        List<Row> out = new ArrayList<>();
        boolean started = false;
        for (Row r : index) {
            if (r.userId() == userId) {
                started = true;
                out.add(r);
            } else if (started) {
                break;
            }
        }
        return out;
    }

    private static List<Row> scanPrefixTax(List<Row> index, int taxId) {
        List<Row> out = new ArrayList<>();
        boolean started = false;
        for (Row r : index) {
            if (r.taxId() == taxId) {
                started = true;
                out.add(r);
            } else if (started) {
                break;
            }
        }
        return out;
    }
}
