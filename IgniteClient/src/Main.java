import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.client.ClientCache;
import org.apache.ignite.client.ClientCacheConfiguration;
import org.apache.ignite.client.ClientException;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.configuration.ClientConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class Main {

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost:10800";
        ClientConfiguration cfg = new ClientConfiguration().setAddresses(host);
        int lowerBound = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        int count = args.length > 2 ? Integer.parseInt(args[2]) : 100;
        String verb = args.length > 3 ? args[3].toUpperCase() : "";
        String cacheName = args.length > 4 ? args[4] : "int-string-cache";
        int clientCount = args.length > 5 ? Integer.parseInt(args[5]) : 3;
        int upperBound = args.length > 6 ? Integer.parseInt(args[6]) : -1;

        Random r = new Random();

        try {
            final String CACHE_NAME = cacheName;

            var cacheConf = new ClientCacheConfiguration().setAtomicityMode(CacheAtomicityMode.ATOMIC)
                    .setWriteSynchronizationMode(CacheWriteSynchronizationMode.PRIMARY_SYNC)
                    .setCacheMode(CacheMode.PARTITIONED).setName(CACHE_NAME).setReadFromBackup(true)
                    .setBackups(1).setStatisticsEnabled(true);


            IgniteClient[] clients = new IgniteClient[clientCount];
            ClientCache<Integer, String>[] caches = new ClientCache[clientCount];
            for (int i = 0; i < clientCount; i++) {
                clients[i] = Ignition.startClient(cfg);
                caches[i] = clients[i].getOrCreateCache(cacheConf);
            }

            final String value = createStringValue();

            if (verb.equals("") || verb.equals("PUT")) {
                runBatch("PUT", key -> {
                    final var nextKey = getNextKey(lowerBound, upperBound, r, key);
                    //System.out.printf("PUT%d ", nextKey);
                    (caches[key % clientCount]).put(nextKey, value);
                }, lowerBound, count);
                System.out.println();
            }

            if (verb.equals("") || verb.equals("GET")) {
                runBatch("GET", key -> {
                    final var nextKey = getNextKey(lowerBound, upperBound, r, key);
                    //System.out.printf("GET%d ", nextKey);
                    (caches[key % clientCount]).get(nextKey);
                }, lowerBound, count);
                System.out.println();
            }
        } catch (ClientException e) {
            System.err.println(e.getMessage());
        } catch (Exception e) {
            System.err.format("Unexpected failure: %s\n", e);
        }
    }

    public static int getNextKey(int lowerBound, int upperBound, Random r, Integer key) {
        return upperBound < lowerBound ? key : lowerBound + r.nextInt(upperBound - lowerBound);
    }

    public static void runBatch(String operationName, Consumer<Integer> operation, int lowerBound, int count) {
        long[] nsLog = new long[count];
        long startBatch = System.nanoTime();
        IntStream.rangeClosed(lowerBound, (count + lowerBound - 1)).parallel().forEach(index -> {
            long start = System.nanoTime();
            operation.accept(index);
            long finish = System.nanoTime();
            long timeElapsed = finish - start;
            nsLog[index - lowerBound] = timeElapsed;
        });
        long endBatch = System.nanoTime();

        final var nanosecondsPerMillisecond = 1.0E06;

        double threshold1 = 15 * nanosecondsPerMillisecond;
        double threshold2 = 20 * nanosecondsPerMillisecond;

        int countBelowThreshold1 = 0;
        int countBelowThreshold2 = 0;


        long min = Long.MAX_VALUE;
        long max = 0L;
        long sum = 0L;

        var msLog = new double[count];
        for (int i = 0; i < count; i++) {

            long nanoseconds = nsLog[i];
            msLog[i] = (double) nanoseconds / nanosecondsPerMillisecond;

            sum += nanoseconds;

            if (nanoseconds < min) {
                min = nanoseconds;
            }

            if (nanoseconds > max) {
                max = nanoseconds;
            }

            if (nanoseconds < threshold1) {
                countBelowThreshold1++;
            }

            if (nanoseconds < threshold2) {
                countBelowThreshold2++;
            }

        }
        var mean = sum / count;

        final var nanosecondsPerSecond = 1.0E09;
        System.out.format("\n[%s] [%s] items in [%s] seconds. Avg [%s] items/second.\nMin: [%s] ms\nMax: [%s] ms\nMean: [%s] ms\nBelow [%s] ms: [%s] ([%s] %%)\nBelow [%s] ms: [%s] ([%s] %%)\n\n",
                operationName, count, (endBatch - startBatch) / nanosecondsPerSecond,
                count / ((endBatch - startBatch) / nanosecondsPerSecond),
                min / nanosecondsPerMillisecond, max / nanosecondsPerMillisecond, mean / nanosecondsPerMillisecond,
                threshold1 / nanosecondsPerMillisecond, countBelowThreshold1, 100.0 * countBelowThreshold1 / count,
                threshold2 / nanosecondsPerMillisecond, countBelowThreshold2, 100.0 * countBelowThreshold2 / count);

        DescriptiveStatistics descriptiveStatistics = new DescriptiveStatistics(msLog);
        System.out.println(descriptiveStatistics);
    }

    @NotNull
    private static String createStringValue() {
        final var charArray = new char[512];
        IntStream.range(0, 512).forEach(i -> charArray[i] = (char) (33 + i % 93));
        return new String(charArray);
    }
}
