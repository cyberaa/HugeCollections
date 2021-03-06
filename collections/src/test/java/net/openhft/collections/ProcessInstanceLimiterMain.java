package net.openhft.collections;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

// todo make this example more efficient e.g. not using Serializable.
public class ProcessInstanceLimiterMain implements Runnable {
    private static final long TIME_UPDATE_INTERVAL = 100L;
    private final String sharedMapName;
    private final Map<String, Data> theSharedMap;
    private final Callback callback;
    private final Map<String, IntWrapper> localUpdates = new ConcurrentHashMap<String, IntWrapper>();

    public ProcessInstanceLimiterMain(String sharedMapName, Callback callback) throws IOException {
        this.sharedMapName = sharedMapName;
        this.callback = callback;
        SharedHashMapBuilder builder = new SharedHashMapBuilder();
        builder.entries(10000);
        builder.minSegments(2);
        this.theSharedMap = builder.create(new File(System.getProperty("java.io.tmpdir") + "/" + sharedMapName), String.class, Data.class);
        Thread t = new Thread(this, "ProcessInstanceLimiterMain updater");
        t.setDaemon(true);
        t.start();
    }

    public void run() {
        //every TIME_UPDATE_INTERVAL milliseconds, update the time
        while (true) {
            try {
                pause(TIME_UPDATE_INTERVAL);
                String processType;
                for (Entry<String, IntWrapper> entry : this.localUpdates.entrySet()) {
                    processType = entry.getKey();
                    int index = entry.getValue().intValue;
                    Data data = this.theSharedMap.get(processType);
                    if (data != null) {
                        long timenow = System.currentTimeMillis();
                        data.time[index] = timenow;
                        this.theSharedMap.put(processType, data);
                        while ((data = this.theSharedMap.get(processType)).time[index] != timenow) {
                            data.time[index] = timenow;
                            this.theSharedMap.put(processType, data);
                        }
                    }
                }
            } catch (Exception e) {
                // TODO
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Callback callback = new Callback() {
            public void tooManyProcessesOfType(String processType) {
                System.out.println("Too many processes of type " + processType + " have been started, so exiting this process");
                System.exit(0);
            }

            public void noDefinitionForProcessesOfType(String processType) {
                System.out.println("No definition for processes of type " + processType + " has been set, so exiting this process");
                System.exit(0);
            }
        };
        ProcessInstanceLimiterMain limiter = new ProcessInstanceLimiterMain("test", callback);
        limiter.setMaxNumberOfProcessesOfType("x", 2);
        limiter.startingProcessOfType("x");
        Thread.sleep(60L * 1000L);
    }

    public void startingProcessOfType(String processType) {
        Data data = this.theSharedMap.get(processType);
        if (data == null) {
            this.callback.noDefinitionForProcessesOfType(processType);
            this.callback.tooManyProcessesOfType(processType);
            return;
        }
        long[] times1 = data.time;
        pause(2L * TIME_UPDATE_INTERVAL);
        long[] times2 = this.theSharedMap.get(processType).time;
        for (int i = 0; i < times1.length; i++) {
            if (times2[i] == times1[i]) {
                //we have an index which has not been updated in 200ms
                //so we have a spare slot - use this slot
                this.localUpdates.put(processType, new IntWrapper(i));
                return;
            }
        }
        this.callback.tooManyProcessesOfType(processType);
    }

    public static void pause(long pause) {
        long start = System.currentTimeMillis();
        long elapsedTime;
        while ((elapsedTime = System.currentTimeMillis() - start) < pause) {
            try {
                Thread.sleep(pause - elapsedTime);
            } catch (InterruptedException e) {
            }
        }
    }

    public void setMaxNumberOfProcessesOfType(String processType, int maxNumberOfProcessesAllowed) {
        this.theSharedMap.put(processType, new Data(processType, maxNumberOfProcessesAllowed));
    }

    public interface Callback {
        public void tooManyProcessesOfType(String processType);

        public void noDefinitionForProcessesOfType(String processType);
    }

    public static class Data implements Serializable {
        /**
         * generated serialVersionUID
         */
        private static final long serialVersionUID = 9163018396438735118L;
        String processType;
        int maxNumberOfProcessesAllowed;
        long[] time;

        public Data(String processType, int maxNumberOfProcessesAllowed) {
            this.processType = processType;
            this.maxNumberOfProcessesAllowed = maxNumberOfProcessesAllowed;
            this.time = new long[maxNumberOfProcessesAllowed];
        }
    }

    public static class IntWrapper {
        int intValue;

        public IntWrapper(int intValue) {
            this.intValue = intValue;
        }
    }
}
