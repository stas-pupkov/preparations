package ru.stas.pupkov.preparations.kafka.code;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Удаление по retention работает посегментно: сегмент уходит целиком, когда
 * «устарело» его последнее сообщение — отдельные offset'ы посередине сегмента не чистят.
 */
public class SegmentRetentionDemo {

    public static void main(String[] args) {
        // retention=5 «дней»: сегмент [msg-1..3] (lastDay=3) удалится на day=8 (8-3>=5),
        // хотя msg-1 «протух» раньше — ждём устаревания последнего сообщения сегмента.
        long retentionDays = 5;
        PartitionLog log = new PartitionLog(/* segmentCapacity= */ 3);

        for (int day = 1; day <= 10; day++) {
            log.append(new Message(day, "msg-" + day));
            System.out.println("day=" + day + " after append: " + log.snapshot());
            log.deleteExpiredSegments(day, retentionDays);
            System.out.println("day=" + day + " after retention: " + log.snapshot());
            System.out.println();
        }
    }

    private record Message(long day, String value) {
    }

    private static final class Segment {
        private final List<Message> messages = new ArrayList<>();
        private final int capacity;

        private Segment(int capacity) {
            this.capacity = capacity;
        }

        private boolean isFull() {
            return messages.size() >= capacity;
        }

        private void add(Message message) {
            messages.add(message);
        }

        private long lastDay() {
            return messages.get(messages.size() - 1).day();
        }

        private boolean expired(long nowDay, long retentionDays) {
            return nowDay - lastDay() >= retentionDays;
        }

        @Override
        public String toString() {
            return messages.stream().map(Message::value).toList().toString();
        }
    }

    private static final class PartitionLog {
        private final int segmentCapacity;
        private final List<Segment> segments = new ArrayList<>();
        private Segment active;

        private PartitionLog(int segmentCapacity) {
            this.segmentCapacity = segmentCapacity;
            this.active = new Segment(segmentCapacity);
            this.segments.add(active);
        }

        private void append(Message message) {
            if (active.isFull()) {
                active = new Segment(segmentCapacity);
                segments.add(active);
            }
            active.add(message);
        }

        private void deleteExpiredSegments(long nowDay, long retentionDays) {
            Iterator<Segment> it = segments.iterator();
            while (it.hasNext()) {
                Segment segment = it.next();
                if (segment == active) {
                    break; // активный сегмент не удаляют
                }
                if (segment.expired(nowDay, retentionDays)) {
                    it.remove();
                }
            }
        }

        private String snapshot() {
            return segments.toString();
        }
    }
}
