package ru.stas.pupkov.preparations.livecoding.code;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Модель dual-write при rename: метаданные (БД) vs содержимое на диске.
 *
 * <p>Показывает рассинхрон, если FS меняют до «rollback» метаданных, и более безопасный
 * порядок: сначала зафиксировать имя в БД, потом FS (аналог afterCommit).
 */
public class FileRenameConsistencyDemo {

    public static void main(String[] args) {
        System.out.println("--- anti-pattern: FS first, then DB fails ---");
        Store broken = Store.seed("report.pdf", "PDF-BYTES");
        try {
            broken.renameFsFirstThenDb("report.pdf", "annual.pdf", true);
        } catch (RuntimeException ex) {
            System.out.println("DB update failed: " + ex.getMessage());
        }
        System.out.println("DB names: " + broken.dbNames());
        System.out.println("FS keys:  " + broken.fsKeys());
        System.out.println("consistent: " + broken.isConsistent());

        System.out.println();
        System.out.println("--- safer: DB commit first, then FS ---");
        Store safer = Store.seed("report.pdf", "PDF-BYTES");
        safer.renameDbFirstThenFs("report.pdf", "annual.pdf");
        System.out.println("DB names: " + safer.dbNames());
        System.out.println("FS keys:  " + safer.fsKeys());
        System.out.println("consistent: " + safer.isConsistent());

        System.out.println();
        System.out.println("--- safer path, FS fails after DB commit ---");
        Store fsFail = Store.seed("report.pdf", "PDF-BYTES");
        try {
            fsFail.renameDbFirstThenFs("report.pdf", "annual.pdf", true);
        } catch (RuntimeException ex) {
            System.out.println("FS rename failed: " + ex.getMessage());
        }
        System.out.println("DB names: " + fsFail.dbNames());
        System.out.println("FS keys:  " + fsFail.fsKeys());
        System.out.println("consistent: " + fsFail.isConsistent() + " (нужен retry/reconcile)");
    }

    /**
     * Упрощённое хранилище: map «имя → id» как БД и map «имя → bytes» как FS.
     */
    static final class Store {

        private final Map<String, Long> db = new HashMap<>();
        private final Map<String, String> fs = new HashMap<>();
        private long seq = 1L;

        static Store seed(String name, String content) {
            Store store = new Store();
            long id = store.seq++;
            store.db.put(name, id);
            store.fs.put(name, content);
            return store;
        }

        /**
         * Как в исходном коде: сначала диск, потом БД. При падении БД — рассинхрон.
         *
         * @param oldName      старое имя
         * @param newName      новое имя
         * @param failDbUpdate симулировать ошибку update
         */
        void renameFsFirstThenDb(String oldName, String newName, boolean failDbUpdate) {
            Long id = db.get(oldName);
            Objects.requireNonNull(id, "not found in db: " + oldName);

            String content = fs.remove(oldName);
            fs.put(newName, content);

            if (failDbUpdate) {
                throw new RuntimeException("update file set name=... failed");
            }
            db.remove(oldName);
            db.put(newName, id);
        }

        void renameDbFirstThenFs(String oldName, String newName) {
            renameDbFirstThenFs(oldName, newName, false);
        }

        /**
         * Ближе к afterCommit: метаданные фиксируем первыми, FS — после.
         *
         * @param oldName     старое имя
         * @param newName     новое имя
         * @param failFs      симулировать ошибку FS после «commit» БД
         */
        void renameDbFirstThenFs(String oldName, String newName, boolean failFs) {
            Long id = db.get(oldName);
            Objects.requireNonNull(id, "not found in db: " + oldName);
            if (db.containsKey(newName)) {
                throw new IllegalStateException("name already taken: " + newName);
            }

            db.remove(oldName);
            db.put(newName, id);

            if (failFs) {
                throw new RuntimeException("Files.move failed");
            }
            String content = fs.remove(oldName);
            fs.put(newName, content);
        }

        Map<String, Long> dbNames() {
            return Map.copyOf(db);
        }

        Map<String, String> fsKeys() {
            return Map.copyOf(fs);
        }

        boolean isConsistent() {
            return db.keySet().equals(fs.keySet());
        }
    }
}
