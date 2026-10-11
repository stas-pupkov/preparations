package ru.stas.pupkov.preparations.databases.code;

/**
 * Учебная cost-модель: когда sequential scan дешевле index + heap lookups.
 */
public class IndexSelectivityDemo {

    public static void main(String[] args) {
        int rows = 1_000_000;
        double seqPageCost = 1.0;
        double randomPageCost = 4.0;
        int rowsPerPage = 100;
        int pages = rows / rowsPerPage;

        evaluate("gender = 'M' (~50%)", rows, 0.50, pages, seqPageCost, randomPageCost);
        evaluate("status = 'ACTIVE' (~2%)", rows, 0.02, pages, seqPageCost, randomPageCost);
        evaluate("id = ?", rows, 1.0 / rows, pages, seqPageCost, randomPageCost);
        evaluate("tiny table 200 rows", 200, 0.50, 2, seqPageCost, randomPageCost);
    }

    private static void evaluate(String label,
                                 int rows,
                                 double selectivity,
                                 int pages,
                                 double seqPageCost,
                                 double randomPageCost) {
        int matched = Math.max(1, (int) Math.round(rows * selectivity));
        double seqCost = pages * seqPageCost;
        // упрощение: оцениваем число heap-страниц, а не «1 random read на строку»
        int rowsPerPage = Math.max(1, rows / Math.max(1, pages));
        double heapPages = Math.min(pages, Math.ceil(matched / (double) rowsPerPage));
        double indexCost = heapPages * randomPageCost + Math.log(Math.max(2, rows));
        String winner = seqCost <= indexCost ? "SEQ SCAN" : "INDEX";
        System.out.printf(
                "%-28s matched=%-7d seqCost=%8.1f indexCost=%8.1f => %s%n",
                label, matched, seqCost, indexCost, winner
        );
    }
}
