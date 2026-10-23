# Lab-01: Multithreading in Java

Requires JDK 25 and Maven. Build and run the tests with:

```powershell
mvn -q verify
```

Run each exercise from the project root:

```powershell
java -cp target/classes edu.itc.se.lab01.task1.Main
java -cp target/classes edu.itc.se.lab01.task1.Main --sequential
java -cp target/classes edu.itc.se.lab01.task2.Bench
java -cp target/classes edu.itc.se.lab01.task3.Main
java -cp target/classes edu.itc.se.lab01.task4.Main
java -cp target/classes edu.itc.se.lab01.task5.Bank --deadlock
java -cp target/classes edu.itc.se.lab01.task5.Bank --ordered
java -cp target/classes edu.itc.se.lab01.task5.Bank --try-lock
java -cp target/classes edu.itc.se.lab01.task5.Bank --graceful
```

Task 1's sequential mode calls `run()` directly, so it runs on the main thread. `start()` schedules a separate thread; it returns while that thread is still working. Because `run()` does not start the `Thread` object, its state remains `NEW` after a sequential countdown.

## Task 2: Counter correctness

The unsynchronized `count++` is a read-modify-write sequence. Multiple threads can read the same old value and overwrite one another's increments. `SynchronizedCounter` synchronizes both methods so every access uses the same monitor. `LockCounter` similarly protects reads and writes and releases its lock in `finally`. `AtomicCounter` provides an atomic increment. `LongAdder` spreads contended updates across cells and combines them when `sum()` is called; it is intended for high-contention statistics, not for an atomic snapshot while updates continue.

Making only `increment()` synchronized protects increments against each other, but an unsynchronized `get()` still races with writes and has no monitor-based visibility guarantee. A read concurrent with updates can observe a stale value. Synchronizing both methods provides a consistent concurrent read. The benchmark calls `get()` after joining all threads, which also establishes a happens-before edge for the completed worker actions; this does not make a concurrent unsynchronized read safe.

## Task 3: Producer-consumer

The hand-built buffer checks its full/empty conditions in `while` loops because a thread can wake spuriously or another waiter can consume the condition before it reacquires the monitor. `notifyAll()` wakes both kinds of waiters; each rechecks its own guard. An `if` can proceed when the guard is false, and `notify()` can wake a waiter whose condition is still false while leaving the correct waiter asleep. Producers coordinate poison-pill insertion with a latch so the pills are added only after all producers have finished.

## Task 4: Futures and executors

Prime counts are summed from 16 disjoint future results and compared with the sequential count. The download exercise uses simulated 200 ms I/O, ten failing tasks, and one separately timed-out task. The virtual-thread executor permits many blocking tasks without reserving one platform thread per task; it is not expected to accelerate CPU-bound prime counting.

## Task 5: Deadlock and cancellation

`Bank --deadlock` intentionally runs the flawed lock-ordering example and starts a daemon watchdog. While it is hung, use `jps -l` to find its PID and run `jstack <pid> > deadlock-full.txt`. Copy the real `Found one Java-level deadlock` section from that output into `deadlock.txt`; that evidence must be captured on the machine running the exercise and is deliberately not fabricated here. `--ordered` uses ascending account IDs for every transfer lock; `--try-lock` releases any acquired lock and retries after a random back-off. Both preserve the total while avoiding circular wait.

The interruptible run exits its worker loops on interruption. If an interruptible lock acquisition throws, the worker restores its interrupt status before returning from the loop.

## Challenge: Word-count analyser

Generate the 200-file sample (200 files × 50,000 words) and run the analyzer:

```powershell
java -cp target/classes edu.itc.se.lab01.challenge.WordCountData .\data 200 50000
java -cp target/classes edu.itc.se.lab01.challenge.WordCount .\data --top 10 --timeout 30
java -cp target/classes edu.itc.se.lab01.challenge.WordCount .\data --top 10 --workers 4 --virtual
```

The scanner feeds a bounded queue of 16 paths, applying back-pressure while the workers read files. The map's `LongAdder` counters avoid serializing every token update behind a single map lock; a synchronized map would make all workers contend on one critical section. `ConcurrentHashMap.computeIfAbsent` safely publishes each per-word adder, while the adders handle concurrent increments. The reference implementation uses the same tokenizer and returns an ordinary immutable count map. The JUnit test compares it with the parallel pipeline for a 20-file fixture and checks that a one-second deadline returns within three seconds on a 5,000-file fixture.

The pipeline avoids the Coffman circular-wait condition: workers do not acquire application locks, and only the scanner inserts paths while workers consume them. Queue back-pressure can block the scanner, but workers never wait for the scanner to finish in order to process queued files. Poison pills are inserted only after scanning has ended, and interruption releases blocked `put`/`take` calls during cancellation.

For a repeatable local comparison, run the same data directory and worker count for each row. Timings are machine- and filesystem-dependent, so record measurements from your JDK 25 machine rather than treating sample timings as guarantees.

| Mode | Elapsed time |
|---|---:|
| 1 platform worker (`--workers 1`) | Not measured here |
| 2 platform workers (`--workers 2`) | Not measured here |
| 4 platform workers (`--workers 4`) | Not measured here |
| 8 platform workers (`--workers 8`) | Not measured here |
| Virtual workers (`--workers 8 --virtual`) | Not measured here |

The analyzer writes all word counts and percentages to `result.csv`; the console displays only the requested top N.
