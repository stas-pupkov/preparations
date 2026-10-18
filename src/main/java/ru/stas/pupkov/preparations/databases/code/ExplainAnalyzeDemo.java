package ru.stas.pupkov.preparations.databases.code;

/**
 * Учебный разбор: чем EXPLAIN отличается от EXPLAIN ANALYZE и на что смотреть в плане.
 * Это не вывод живой СУБД — схема полей как в PostgreSQL.
 */
public class ExplainAnalyzeDemo {

    public static void main(String[] args) {
        System.out.println("=== EXPLAIN (только оценки, запрос НЕ выполняется) ===");
        System.out.println("""
                Seq Scan on orders  (cost=0.00..25000.00 rows=100 width=40)
                  Filter: (status = 'NEW'::text)
                Смотрим: cost и estimated rows — прогноз оптимизатора, не миллисекунды.
                """);

        System.out.println("=== EXPLAIN (ANALYZE, BUFFERS) — запрос выполнен ===");
        System.out.println("""
                Nested Loop  (cost=0.00..100.00 rows=10 width=64)
                             (actual time=0.050..850.200 rows=50000 loops=1)
                  ->  Seq Scan on users  (cost=0.00..20.00 rows=10 width=8)
                                         (actual time=0.010..0.400 rows=10000 loops=1)
                  ->  Index Scan on orders  (cost=0.00..8.00 rows=1 width=56)
                                            (actual time=0.080..0.082 rows=5 loops=10000)
                        Index Cond: (user_id = users.id)
                  Buffers: shared hit=1200 read=45000
                """);

        System.out.println("--- что бросается в глаза ---");
        System.out.println("1) estimated rows=10 на Nested Loop, actual rows=50000 → статистика врала");
        System.out.println("2) Index Scan loops=10000 → внешняя сторона раздулась, nested loop взорвался");
        System.out.println("3) actual time ~850 ms на корне — почти всё время здесь");
        System.out.println("4) shared read=45000 → много чтения с диска (холодный кэш / огромный объём)");
        System.out.println();
        System.out.println("EXPLAIN = прогноз плана; EXPLAIN ANALYZE = прогон с замерами.");
        System.out.println("Команда ANALYZE (сбор статистики) — другое; созвучие не путать.");
    }
}
