package ru.stas.pupkov.preparations.databases.code;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Модель {@code SELECT … FOR UPDATE SKIP LOCKED}: залоченные строки пропускаем, не ждём.
 */
public class SkipLockedDemo {

    enum Status { NEW, DONE }

    static final class Job {
        final long id;
        Status status;
        boolean locked;

        Job(long id) {
            this.id = id;
            this.status = Status.NEW;
        }
    }

    static final class JobQueue {
        private final List<Job> jobs = new ArrayList<>();

        void offer(Job job) {
            jobs.add(job);
        }

        /** Как обычный FOR UPDATE: берём первую NEW, ждём если locked. */
        Optional<Job> claimWaiting() throws InterruptedException {
            while (true) {
                for (Job job : jobs) {
                    if (job.status != Status.NEW) {
                        continue;
                    }
                    if (!job.locked) {
                        job.locked = true;
                        return Optional.of(job);
                    }
                    // кто-то держит lock — в реальной БД ждали бы здесь
                    System.out.println("  wait: job " + job.id + " locked");
                    Thread.sleep(50);
                    break;
                }
                if (jobs.stream().noneMatch(j -> j.status == Status.NEW)) {
                    return Optional.empty();
                }
            }
        }

        /** SKIP LOCKED: залоченное пропускаем. */
        Optional<Job> claimSkipLocked() {
            for (Job job : jobs) {
                if (job.status == Status.NEW && !job.locked) {
                    job.locked = true;
                    return Optional.of(job);
                }
            }
            return Optional.empty();
        }

        void complete(Job job) {
            job.status = Status.DONE;
            job.locked = false;
        }
    }

    public static void main(String[] args) throws Exception {
        JobQueue queue = new JobQueue();
        queue.offer(new Job(1));
        queue.offer(new Job(2));
        queue.offer(new Job(3));

        System.out.println("=== SKIP LOCKED: two workers ===");
        Optional<Job> a = queue.claimSkipLocked();
        Optional<Job> b = queue.claimSkipLocked();
        System.out.println("worker A got " + a.map(j -> j.id).orElse(-1L));
        System.out.println("worker B got " + b.map(j -> j.id).orElse(-1L));
        a.ifPresent(queue::complete);
        b.ifPresent(queue::complete);

        System.out.println("=== Without SKIP LOCKED: second waits on locked row ===");
        JobQueue slow = new JobQueue();
        slow.offer(new Job(10));
        slow.offer(new Job(11));
        Job held = slow.claimSkipLocked().orElseThrow();
        System.out.println("worker A holds job " + held.id);
        Thread waiter = new Thread(() -> {
            try {
                Optional<Job> got = slow.claimWaiting();
                System.out.println("waiter finally got " + got.map(j -> j.id).orElse(-1L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "waiter");
        waiter.start();
        Thread.sleep(120);
        slow.complete(held);
        waiter.join();
    }
}
