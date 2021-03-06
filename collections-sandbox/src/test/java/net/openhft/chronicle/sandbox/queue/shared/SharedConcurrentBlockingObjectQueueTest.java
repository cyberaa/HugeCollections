package net.openhft.chronicle.sandbox.queue.shared;

import net.openhft.chronicle.sandbox.queue.SharedConcurrentBlockingObjectQueue;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Copyright 2013 Peter Lawrey
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Rob Austin
 */

public class SharedConcurrentBlockingObjectQueueTest {

    @Test
    public void testTake() throws Exception {

        final BlockingQueue<Integer> queue = new SharedConcurrentBlockingObjectQueue<Integer>(Integer.class);

        // writer thread
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    queue.put(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        final ArrayBlockingQueue<Integer> actual = new ArrayBlockingQueue<Integer>(1);

        // reader thread
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                final int value;
                try {
                    value = queue.take();
                    actual.add(value);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        });

        final Integer value = actual.poll(1, TimeUnit.SECONDS);
        Assert.assertEquals((int) value, 1);
        Thread.sleep(100);
    }


    @Test
    public void testWrite() throws Exception {

    }

    @Test
    public void testRead() throws Exception {
        final BlockingQueue<Integer> queue = new SharedConcurrentBlockingObjectQueue<Integer>(Integer.class);
        queue.put(10);
        final int value = queue.take();
        junit.framework.Assert.assertEquals(10, value);
    }

    @Test
    public void testRead2() throws Exception {
        final BlockingQueue<Integer> queue = new SharedConcurrentBlockingObjectQueue<Integer>(Integer.class);
        queue.put(10);
        queue.put(11);
        final int value = queue.take();
        junit.framework.Assert.assertEquals(10, value);
        final int value1 = queue.take();
        junit.framework.Assert.assertEquals(11, value1);
    }

    @Test
    public void testReadLoop() throws Exception {
        final BlockingQueue<Integer> queue = new SharedConcurrentBlockingObjectQueue<Integer>(Integer.class);

        for (int i = 1; i < 50; i++) {
            queue.put(i);
            final int value = queue.take();
            junit.framework.Assert.assertEquals(i, value);
        }
    }

    /**
     * reader and add, reader and writers on different threads
     *
     * @throws Exception
     */
    @Test
    public void testWithFasterReader() throws Exception {

        final BlockingQueue<Integer> queue = new SharedConcurrentBlockingObjectQueue<Integer>(Integer.class);
        final int max = 100;
        final CountDownLatch countDown = new CountDownLatch(1);

        final AtomicBoolean success = new AtomicBoolean(true);


        new Thread(
                new Runnable() {

                    @Override
                    public void run() {
                        for (int i = 1; i < max; i++) {
                            try {
                                queue.put(i);
                                Thread.sleep((int) (Math.random() * 100));
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                    }
                }
        ).start();


        new Thread(
                new Runnable() {

                    @Override
                    public void run() {

                        int value = 0;
                        for (int i = 1; i < max; i++) {
                            try {

                                final int newValue = queue.take();

                                junit.framework.Assert.assertEquals(i, newValue);


                                if (newValue != value + 1) {
                                    success.set(false);
                                    return;
                                }

                                value = newValue;


                                Thread.sleep((int) (Math.random() * 10));
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        countDown.countDown();

                    }
                }
        ).start();

        countDown.await();

        Assert.assertTrue(success.get());
    }


    /**
     * faster writer
     *
     * @throws Exception
     */
    @Test
    public void testWithFasterWriter() throws Exception {

        final BlockingQueue<Integer> queue = new SharedConcurrentBlockingObjectQueue<Integer>(Integer.class);
        final int max = 200;
        final CountDownLatch countDown = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(true);

        new Thread(
                new Runnable() {

                    @Override
                    public void run() {
                        for (int i = 1; i < max; i++) {
                            try {
                                queue.put(i);

                                Thread.sleep((int) (Math.random() * 3));
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                    }
                }
        ).start();


        new Thread(
                new Runnable() {

                    @Override
                    public void run() {

                        int value = 0;
                        for (int i = 1; i < max; i++) {

                            try {
                                final int newValue = queue.take();

                                junit.framework.Assert.assertEquals(i, newValue);


                                if (newValue != value + 1) {
                                    success.set(false);
                                    return;
                                }

                                value = newValue;


                                Thread.sleep((int) (Math.random() * 10));
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        countDown.countDown();

                    }
                }
        ).start();

        countDown.await();
        Assert.assertTrue(success.get());
    }


    @Test
    @Ignore
    public void testFlatOut() throws Exception {
        testConcurrentBlockingObjectQueue(Integer.MAX_VALUE);
    }

    private void testConcurrentBlockingObjectQueue(final int nTimes) throws InterruptedException, IOException {
        final BlockingQueue<Integer> queue = new SharedConcurrentBlockingObjectQueue<Integer>(1024, Integer.class);
        final CountDownLatch countDown = new CountDownLatch(1);

        final AtomicBoolean success = new AtomicBoolean(true);

        Thread writerThread = new Thread(
                new Runnable() {

                    @Override
                    public void run() {
                        try {
                            for (int i = 1; i < nTimes; i++) {
                                queue.put(i);

                            }

                        } catch (Throwable e) {
                            e.printStackTrace();
                        }

                    }
                }
        );


        writerThread.setName("ConcurrentBlockingObjectQueue<Integer>-writer");

        Thread readerThread = new Thread(
                new Runnable() {

                    @Override
                    public void run() {

                        int value = 0;
                        for (int i = 1; i < nTimes; i++) {

                            final int newValue;
                            try {
                                newValue = queue.take();


                                if (newValue != value + 1) {
                                    success.set(false);
                                    return;
                                }

                                value = newValue;
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                        }
                        countDown.countDown();

                    }
                }
        );

        readerThread.setName("ConcurrentBlockingObjectQueue<Integer>-reader");

        writerThread.start();
        readerThread.start();

        countDown.await();

        writerThread.stop();
        readerThread.stop();
    }


    private void testArrayBlockingQueue(final int nTimes) throws InterruptedException {

        final ArrayBlockingQueue<Integer> queue = new ArrayBlockingQueue<Integer>(1024);
        final CountDownLatch countDown = new CountDownLatch(1);

        final AtomicBoolean success = new AtomicBoolean(true);

        Thread writerThread = new Thread(
                new Runnable() {

                    @Override
                    public void run() {
                        try {
                            for (int i = 1; i < nTimes; i++) {
                                queue.put(i);

                            }

                        } catch (Throwable e) {
                            e.printStackTrace();
                        }

                    }
                }
        );


        writerThread.setName("ArrayBlockingQueue-writer");

        Thread readerThread = new Thread(
                new Runnable() {

                    @Override
                    public void run() {

                        int value = 0;
                        for (int i = 1; i < nTimes; i++) {

                            final int newValue;

                            try {
                                newValue = queue.take();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                return;
                            }

                            if (newValue != value + 1) {
                                success.set(false);
                                return;
                            }

                            value = newValue;

                        }
                        countDown.countDown();

                    }
                }
        );

        readerThread.setName("ArrayBlockingQueue-reader");

        writerThread.start();
        readerThread.start();

        countDown.await();

        writerThread.stop();
        readerThread.stop();
    }


    @Test
    @Ignore
    public void testLatency() throws NoSuchFieldException, InterruptedException, IOException {


        for (int pwr = 2; pwr < 200; pwr++) {
            int i = (int) Math.pow(2, pwr);


            final long arrayBlockingQueueStart = System.nanoTime();
            testArrayBlockingQueue(i);
            final double arrayBlockingDuration = System.nanoTime() - arrayBlockingQueueStart;


            final long queueStart = System.nanoTime();
            testConcurrentBlockingObjectQueue(i);
            final double concurrentBlockingDuration = System.nanoTime() - queueStart;

            System.out.printf("Performing %,d loops, ArrayBlockingQueue() took %.3f ms and calling ConcurrentBlockingObjectQueue<Integer> took %.3f ms on average, ratio=%.1f%n",
                    i, arrayBlockingDuration / 1000000.0, concurrentBlockingDuration / 1000000.0, (double) arrayBlockingDuration / (double) concurrentBlockingDuration);
            /**
             System.out.printf("%d\t%.3f\t%.3f\n",
             i, arrayBlockingDuration / 1000000.0, concurrentBlockingDuration / 1000000.0, (double) arrayBlockingDuration / (double) concurrentBlockingDuration);
             **/
        }


    }
}
