import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.IgniteDataStreamer;

import java.util.Map;
import java.util.TreeMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.cache.query.QueryCursor;
import javax.cache.Cache;

import org.apache.ignite.cache.query.TextQuery;
import org.apache.ignite.cache.QueryEntity;
import java.util.Collections;

public class IgniteClient {

    private static final Logger logger = Logger.getLogger(IgniteClient.class.getName());

    public static void main(String[] args) {
        setupLogger();
        Ignite ignite = connectToIgnite(args);
        String cacheName = "TestCache";

        if (ignite == null) {
            logger.severe("Could not start Ignite instance. Exiting.");
            return;
        }

        try {
            IgniteCache<Integer, String> cache = createOrGetCache(ignite, cacheName);

            if (args.length > 1) {
                String action = args[1];
                switch (action) {
                    case "put":
                        putAndGet(cache);
                        break;

                    case "putAll":
                        int count = parseCountArg(args, 2, 1000);
                        putAllToCache(cache, count);
                        break;

                    case "stream":
                        int countST = parseCountArg(args, 2, 1000);
                        streamToCache(ignite, cache.getName(), countST);
                        break;

                    case "query":
                        int limit = parseCountArg(args, 2, 10);
                        int pageSize = parseCountArg(args, 3, 1000);
                        query(cache, limit, pageSize);
                        break;

                    case "textQuery":
                        String textCacheName = "TestTxtCache";

                        IgniteCache<Integer, String> textCache = createOrGetCache(ignite, textCacheName);

                        int entryCount = 5000;
                        streamToCache(ignite, textCacheName, entryCount);

                        boolean indexed = false;
                        int attempts = 0;
                        while (!indexed && attempts++ < 5) {
                            try {
                                TextQuery<Integer, String> probeQuery = new TextQuery<>(String.class, "value");
                                probeQuery.setPageSize(10);

                                try (QueryCursor<Cache.Entry<Integer, String>> cur = textCache.query(probeQuery)) {
                                    if (cur.iterator().hasNext()) {
                                        indexed = true;
                                        logger.info("Lucene index is ready.");
                                    } else {
                                        logger.info("Lucene index not ready yet. Retrying...");
                                        Thread.sleep(1000);
                                    }
                                }
                            } catch (Exception e) {
                                logger.warning("Error checking Lucene index readiness: " + e.getMessage());
                                Thread.sleep(1000);
                            }
                        }

                        int textQuerylimit = parseCountArg(args, 2, 10);
                        int textQuerypageSize = parseCountArg(args, 3, 1000);
                        String searchText = args.length > 4 ? args[4] : "value";

                        queryText(textCache, textQuerylimit, textQuerypageSize, searchText);
                        break;
                }
            } else {
                logger.info("No specific action provided. Defaulting to 'put'.");
                // putAndGet(cache);
            }
        } catch (Exception e) {
            logger.severe("Runtime exception: " + e.getMessage());
        } finally {
            ignite.close();
            logger.info("Ignite instance closed.");
        }
    }

    private static void setupLogger() {
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.INFO);
        logger.addHandler(consoleHandler);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.INFO);
    }

    private static Ignite connectToIgnite(String[] args) {
        try {
            Ignite ignite;
            if (args.length > 0) {
                String configPath = args[0];
                logger.info("Using Ignite config: " + configPath);
                ignite = Ignition.start(configPath);
            } else {
                logger.info("No config path provided. Starting Ignite with default configuration.");
                ignite = Ignition.start();
            }

            if (ignite.cluster().active()) {
                logger.info("Ignite Client connected sucessfully and Cluster is ACTIVE.");
            } else {
                logger.warning("Ignite started, but cluster is INACTIVE.");
            }

            return ignite;

        } catch (Exception e) {
            logger.severe("Failed to start Ignite: " + e.getMessage());
            return null;
        }
    }

    private static IgniteCache<Integer, String> createOrGetCache(Ignite ignite, String cacheName) {

        CacheConfiguration<Integer, String> cacheConfig = new CacheConfiguration<>(cacheName);

        QueryEntity qe = new QueryEntity();
        qe.setKeyType(Integer.class.getName());
        qe.setValueType(String.class.getName());
        // Expose the value itself as a text‐searchable field called "this"
        qe.addQueryField("this", String.class.getName(), null);
        cacheConfig.setQueryEntities(Collections.singletonList(qe));

        if (!ignite.cacheNames().contains(cacheName)) {
            logger.info("Creating new cache: " + cacheName);
        } else {
            logger.info("Reusing existing cache: " + cacheName);
        }

        cacheConfig.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
        logger.info("Creating or retrieving cache '" + cacheName + "'...");
        return ignite.getOrCreateCache(cacheConfig);
    }

    private static void putAndGet(IgniteCache<Integer, String> cache) {
        logger.info("Putting (1, 'Hello Ignite') into cache.");
        cache.put(1, "Hello Ignite");

        String result = cache.get(1);
        logger.info("Retrieved from cache: " + result);
    }

    private static void putAllToCache(IgniteCache<Integer, String> cache, Integer count) {
        logger.info("Putting " + count + " entries into cache...");
        Map<Integer, String> data = new TreeMap<>();
        for (int i = 0; i < count; i++) {
            data.put(i, "value-" + i);
        }
        cache.putAll(data);
        logger.info("putAll successful and completed.");
    }

    private static void streamToCache(Ignite ignite, String cacheName, int count) {
        logger.info("Streaming " + count + " entries into cache '" + cacheName + "' via DataStreamer…");

        try (IgniteDataStreamer<Integer, String> streamer = ignite.dataStreamer(cacheName)) {
            streamer.perNodeBufferSize(1024);

            for (int i = 0; i < count; i++) {
                streamer.addData(i, "value-" + i);
                if (i > 0 && i % 50000 == 0)
                    logger.info("  Streamed " + i + " entries…");
            }

            streamer.flush();
            logger.info("DataStreamer flush complete.");
            logger.info("Successfully streamed " + count + " entries into cache '" + cacheName + "'.");

        } catch (Exception e) {
            logger.severe("Error during streaming: " + e.getMessage());
        }
    }

    private static void query(IgniteCache<Integer, String> cache, int limit, int pageSize) {
        logger.info(">>> Starting query: will read only " + limit
                + " entries, but use pageSize=" + pageSize);

        ScanQuery<Integer, String> scan = new ScanQuery<>();
        scan.setPageSize(pageSize);

        try (QueryCursor<Cache.Entry<Integer, String>> cur = cache.query(scan)) {
            int read = 0;
            for (Cache.Entry<Integer, String> e : cur) {
                // System.out.println("Entry[" + read + "] = " + e.getKey());
                read++;
                if (read >= limit) {
                    logger.info(">>> Reached client-side limit: " + limit);
                    break;
                }
            }
        }

        logger.info(">>> Query complete. All pages were transmitted from server nodes.");
    }

    private static void queryText(IgniteCache<Integer, String> cache,
            int limit,
            int pageSize,
            String text) {
        logger.info(">>> Starting TEXT query: term=\"" + text
                + "\", limit=" + limit + ", pageSize=" + pageSize);

        TextQuery<Integer, String> txt = new TextQuery<>(String.class, text);
        txt.setPageSize(pageSize);

        try (QueryCursor<Cache.Entry<Integer, String>> cur = cache.query(txt)) {
            int read = 0;
            for (Cache.Entry<Integer, String> e : cur) {
                logger.info("Entry[" + read + "] = key=" + e.getKey()
                        + ", val=\"" + e.getValue() + "\"");
                if (++read >= limit) {
                    logger.info(">>> Reached client-side limit: " + limit);
                    break;
                }
            }
        }

        logger.info(">>> TextQuery complete. (All pages still transmitted from server nodes.)");
    }

    private static int parseCountArg(String[] args, int index, int defaultValue) {
        if (args.length > index) {
            try {
                return Integer.parseInt(args[index]);
            } catch (NumberFormatException e) {
                logger.warning("Invalid number format at arg[" + index + "]: " + args[index] + ", using default="
                        + defaultValue);
            }
        }
        return defaultValue;
    }
}
