package ru.stas.pupkov.preparations.livecoding.code;

/**
 * Типичный вывод (при достаточном heap для {@code [1000][1000]}, но не для {@code [100000][100000]}):
 * <pre>
 * OutOfMemory
 * Максимальный размер массива 1000
 * return=1000
 * </pre>
 * Запуск с ограниченной кучей: {@code java -Xmx64m ...OomExceptionTestDemo}
 */
public class OomExceptionTestDemo {

    public static void main(String[] args) {
        System.out.println("return=" + exceptionTest());
    }

    private static int exceptionTest() {
        int count = 1000;
        try {
            while (true) {
                Object[][] array = new Object[count][count];
                count *= 100;
            }
        } catch (OutOfMemoryError e) {
            System.out.println("OutOfMemory");
        } catch (Exception ex) {
            System.out.println("Exception");
        }
        count /= 100;
        System.out.println("Максимальный размер массива " + count);
        try {
            RuntimeException[][] array = new RuntimeException[count][count];
            array[0][1] = new NullPointerException();
        } catch (Exception ex) {
            System.out.println("Exception 2");
        } catch (Error e) {
            System.out.println("Error");
        }
        return count;
    }
}
