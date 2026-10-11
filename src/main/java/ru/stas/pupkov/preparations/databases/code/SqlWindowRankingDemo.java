package ru.stas.pupkov.preparations.databases.code;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Разница ROW_NUMBER / RANK / DENSE_RANK на одинаковых значениях Marks.
 */
public class SqlWindowRankingDemo {

    record Exam(String student, int marks) {
    }

    record Ranked(String student, int marks, int rowNumber, int rank, int denseRank) {
        @Override
        public String toString() {
            return student + " marks=" + marks
                    + " rn=" + rowNumber + " rank=" + rank + " dense=" + denseRank;
        }
    }

    public static void main(String[] args) {
        List<Exam> rows = List.of(
                new Exam("Ann", 90),
                new Exam("Bob", 80),
                new Exam("Eve", 80),
                new Exam("Dan", 70)
        ).stream()
                .sorted(Comparator.comparingInt(Exam::marks).reversed())
                .toList();

        List<Ranked> ranked = new ArrayList<>();
        int rowNumber = 0;
        int rank = 0;
        int dense = 0;
        Integer prevMarks = null;
        for (Exam exam : rows) {
            rowNumber++;
            if (prevMarks == null || exam.marks() != prevMarks) {
                rank = rowNumber;
                dense++;
                prevMarks = exam.marks();
            }
            ranked.add(new Ranked(exam.student(), exam.marks(), rowNumber, rank, dense));
        }
        ranked.forEach(System.out::println);
        System.out.println("ties at 80: RANK skips next (→3), DENSE_RANK does not (→2)");
    }
}
