/*
 * Copyright 2013 Peter Lawrey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.collections;

import net.openhft.affinity.AffinityLock;
import net.openhft.lang.model.DataValueClasses;
import net.openhft.lang.values.LongValue;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertNotNull;

/**
 * Created by peter on 28/02/14.
 * <pre>
 * For 1M entries
 * run 1 1000000 : 50/90/99/99.9/99.99/worst: 1.0/3.2/14/18/116/172
 * run 1 500000 : 50/90/99/99.9/99.99/worst: 0.2/3.0/11/16/45/163
 * run 1 250000 : 50/90/99/99.9/99.99/worst: 0.2/3.0/10/16/20/155
 * run 1 100000 : 50/90/99/99.9/99.99/worst: 0.2/3.0/9.2/15/20/147
 * run 1 50000 : 50/90/99/99.9/99.99/worst: 0.2/3.0/9.2/15/20/139
 * </pre><pre>
 * For 1M entries to ext4
 *     run 1 1000000 : 50/90/99/99.9/99.99/worst: 0.9/3.2/14/18/106/188
 * run 1 500000 : 50/90/99/99.9/99.99/worst: 0.2/3.0/11/16/46/172
 * run 1 250000 : 50/90/99/99.9/99.99/worst: 0.2/3.0/10/16/20/163
 * run 1 100000 : 50/90/99/99.9/99.99/worst: 0.2/3.0/9.9/15/20/163
 * run 1 50000 : 50/90/99/99.9/99.99/worst: 0.2/3.0/9.9/15/20/147
 * </pre><pre>
 * For 10M entries
 * run 1 1000000 : 50/90/99/99.9/99.99/worst: 1.4/6.2/16/34/135/180
 * run 1 500000 : 50/90/99/99.9/99.99/worst: 0.3/3.1/11/17/54/159
 * run 1 250000 : 50/90/99/99.9/99.99/worst: 0.3/3.0/10/15/21/147
 * run 1 100000 : 50/90/99/99.9/99.99/worst: 0.4/3.2/9.7/15/20/151
 * run 1 50000 : 50/90/99/99.9/99.99/worst: 0.4/3.2/9.4/15/20/147
 * </pre><pre>
 * For 100M entries
 * run 1 1000000 : 50/90/99/99.9/99.99/worst: 570425/2818572/3355443/3422552/3489660/3556769
 * run 1 500000 : 50/90/99/99.9/99.99/worst: 1.1/11/27/43/94/184
 * run 1 250000 : 50/90/99/99.9/99.99/worst: 0.7/3.3/12/19/40/167
 * run 1 100000 : 50/90/99/99.9/99.99/worst: 0.7/3.3/10/16/21/151
 * run 1 50000 : 50/90/99/99.9/99.99/worst: 0.7/3.3/10/16/22/155
 * </pre>
 */
public class SHMLatencyTestMain {
    static final int KEYS = 1000 * 1000;
    static final int RUN_TIME = 20;
    static final long START_TIME = System.currentTimeMillis();

    // TODO test passes but is under development.
    public static void main(String... ignored) throws IOException {
        AffinityLock lock = AffinityLock.acquireCore();
        File file = File.createTempFile("testSHMLatency", "deleteme");
//        File file = new File("/ocz/tmp/testSHMLatency.deleteme");
        SharedHashMap<LongValue, LongValue> countersMap = new SharedHashMapBuilder()
                .entries(KEYS * 3 / 2)
                .entrySize(24)
                .generatedKeyType(true)
                .generatedValueType(true)
                .create(file, LongValue.class, LongValue.class);

        // add keys
        LongValue key = DataValueClasses.newInstance(LongValue.class);
        LongValue value = DataValueClasses.newInstance(LongValue.class);
        for (long i = 0; i < KEYS; i++) {
            key.setValue(i);
            value.setValue(0);
            countersMap.put(key, value);
        }
        System.out.println("Keys created");
//        Monitor monitor = new Monitor();
        LongValue value2 = DataValueClasses.newDirectReference(LongValue.class);
        for (int t = 0; t < 5; t++) {
            for (int rate : new int[]{1000 * 1000, 500 * 1000, 250 * 1000, 100 * 1000, 50 * 1000}) {
                Histogram times = new Histogram();
                int u = 0;
                long start = System.nanoTime();
                long delay = 1000 * 1000 * 1000L / rate;
                long next = start + delay;
                for (long j = 0; j < RUN_TIME * rate; j += KEYS) {
                    int stride = Math.max(1, KEYS / (RUN_TIME * rate));
                    // the timed part
                    for (int i = 0; i < KEYS && u < RUN_TIME * rate; i += stride) {
                        // busy wait for next time.
                        while (System.nanoTime() < next - 12) ;
//                        monitor.sample = System.nanoTime();
                        long start0 = next;

                        // start the update.
                        key.setValue(i);
                        LongValue using = countersMap.getUsing(key, value2);
                        if (using == null)
                            assertNotNull(using);
                        value2.addAtomicValue(1);
                        // calculate the time using the time it should have started, not when it was able.
                        long elapse = System.nanoTime() - start0;
                        times.sample(elapse);
                        next += delay;
                    }
//                    monitor.sample = Long.MAX_VALUE;
                }
                System.out.print("run " + t + " " + rate + " : ");
                times.printPercentiles();
            }
        }
//        monitor.running = false;
        countersMap.close();
        file.delete();
    }

    static class Monitor implements Runnable {
        volatile boolean running = true;
        final Thread thread;
        volatile long sample;

        Monitor() {
            this.thread = Thread.currentThread();
            sample = Long.MAX_VALUE;
            new Thread(this).start();
        }

        @Override
        public void run() {
            while (running) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
                long delay = System.nanoTime() - sample;
                if (delay > 1000 * 1000) {
                    System.out.println("\n" + (System.currentTimeMillis() - START_TIME) + " : Delay of " + delay / 100000 / 10.0 + " ms.");
                    int count = 0;
                    for (StackTraceElement ste : thread.getStackTrace()) {
                        System.out.println("\tat " + ste);
                        if (count++ > 6) break;
                    }
                }
            }
        }
    }
}
