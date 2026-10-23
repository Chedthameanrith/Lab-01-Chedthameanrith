package edu.itc.se.lab01.task1;

import java.util.List;

/*
 * run() invokes the task on the calling thread; start() schedules a distinct
 * thread and returns immediately. In sequential mode the worker Thread objects
 * remain NEW because their run methods were invoked directly.
 */
public class Main {
    public static void main(String[] args) throws InterruptedException {
        boolean sequential = args.length > 0 && args[0].equals("--sequential");

        Thread apollo = new Countdown("apollo");
        Thread gemini = new Thread(new CountdownTask(), "gemini");
        Thread mercury = new Thread(
                () -> CountdownTask.countdown(Thread.currentThread().getName()), "mercury");
        Thread artemis = Thread.ofVirtual().name("artemis").unstarted(new CountdownTask());
        List<Thread> threads = List.of(apollo, gemini, mercury, artemis);

        for (Thread thread : threads) {
            if (sequential) {
                thread.run();
            } else {
                thread.start();
            }
        }
        System.out.println("Mission control: all launched");
        for (Thread thread : threads) {
            thread.join();
        }
        System.out.println("Mission control: all landed");
        for (Thread thread : threads) {
            System.out.printf("%-8s virtual=%-5b state=%s%n",
                    thread.getName(), thread.isVirtual(), thread.getState());
        }
    }
}

final class Countdown extends Thread {
    Countdown(String name) {
        super(name);
    }

    @Override
    public void run() {
        CountdownTask.countdown(getName());
    }
}

final class CountdownTask implements Runnable {
    @Override
    public void run() {
        countdown(Thread.currentThread().getName());
    }

    static void countdown(String name) {
        for (int count = 3; count > 0; count--) {
            System.out.println(name + ": " + count);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        System.out.println(name + ": liftoff!");
    }
}
