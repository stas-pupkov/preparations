package ru.stas.pupkov.preparations.livecoding.code;

/**
 * К {@code // doStuff} eligible for GC: {@code CardBoard} от {@code c1} + его {@code Short(200)} → 2.
 * {@code c2} (и его {@code Short}) ещё достижимы; {@code go} не обнуляет внешнюю ссылку.
 */
public class CardBoardGcEligibleDemo {

    static class CardBoard {
        Short story = 200;

        CardBoard go(CardBoard cb) {
            cb = null;
            return cb;
        }
    }

    public static void main(String[] args) {
        CardBoard c1 = new CardBoard();
        CardBoard c2 = new CardBoard();
        CardBoard c3 = c1.go(c2);
        c1 = null;
        // doStuff — eligible: 2 (orphan CardBoard + its Short)

        System.out.println("c2 still reachable: " + (c2 != null));
        System.out.println("c3 is null: " + (c3 == null));
        System.out.println("Short cache upper=127, story=200 → отдельный Short на инстанс");
        System.out.println("eligible for GC at // doStuff: 2");
    }
}
