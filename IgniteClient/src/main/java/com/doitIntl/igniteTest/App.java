package com.doitIntl.igniteTest;

import com.google.devtools.common.options.OptionsParser;
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

import java.util.Collections;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class App {

    public static void main(String[] args) {
        OptionsParser parser = OptionsParser.newOptionsParser(IgniteTestClientOptions.class);
        parser.parseAndExitUponError(args);
        IgniteTestClientOptions options = parser.getOptions(IgniteTestClientOptions.class);
        if (options.host.isEmpty() || options.help) {
            printUsage(parser);
            return;
        }

        String host = String.format("%s:%d", options.host, options.port);

        int lowerBound = options.lowerBound;
        int count = options.count;
        String cacheName = options.name;
        int clientCount = options.sockets;
        int upperBound = options.upperBound;

        Random r = new Random();

        try {
            final String CACHE_NAME = cacheName;

            var cacheConf = new ClientCacheConfiguration().setAtomicityMode(CacheAtomicityMode.ATOMIC)
                    .setWriteSynchronizationMode(CacheWriteSynchronizationMode.PRIMARY_SYNC)
                    .setCacheMode(CacheMode.PARTITIONED).setName(CACHE_NAME).setReadFromBackup(true)
                    .setBackups(1).setStatisticsEnabled(true);

            ClientConfiguration cfg = new ClientConfiguration().setAddresses(host);
            IgniteClient[] clients = new IgniteClient[clientCount];

            System.out.format("Creating %d connections to %s ", clientCount, host);

            ClientCache<Integer, String>[] caches = new ClientCache[clientCount];
            for (int i = 0; i < clientCount; i++) {
                clients[i] = Ignition.startClient(cfg);
                caches[i] = clients[i].getOrCreateCache(cacheConf);
                System.out.print(".");
            }
            System.out.println();

            System.out.format("Cache name: %s", cacheName);

            System.out.println();

            final String value = createStringValue();

            if (options.put) {
                System.out.format("PUT %d items between %d and %d", count, lowerBound, upperBound < lowerBound ? lowerBound + count - 1 : upperBound);
                runBatch("PUT", key -> {
                    final var nextKey = getNextKey(lowerBound, upperBound, r, key);
                    (caches[key % clientCount]).put(nextKey, value);
                }, lowerBound, count);
                System.out.println();
            } else {
                System.out.println("Skipping PUT. Use option --put to enable PUT.");
            }
            System.out.println();

            if (options.get) {
                System.out.format("GET %d items between %d and %d", count, lowerBound, upperBound < lowerBound ? lowerBound + count - 1 : upperBound);
                runBatch("GET", key -> {
                    final var nextKey = getNextKey(lowerBound, upperBound, r, key);
                    (caches[key % clientCount]).get(nextKey);
                }, lowerBound, count);
                System.out.println();
            } else {
                System.out.println("Skipping GET. Use option --get to enable GET.");
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
        var nsLog = new long[count];
        var msLog = new double[count];

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

    private static String createStringValue() {
        final var charArray = new char[512];
        IntStream.range(0, 512).forEach(i -> charArray[i] = (char) (33 + i % 93));
        return new String(charArray);
    }

    private static void printUsage(OptionsParser parser) {
        System.out.println("Usage: java -jar igniteTestClient-0.1.0.jar OPTIONS");
        System.out.println(parser.describeOptions(Collections.<String, String>emptyMap(),
                OptionsParser.HelpVerbosity.LONG));
    }
}
