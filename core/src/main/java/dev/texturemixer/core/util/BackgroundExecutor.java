package dev.texturemixer.core.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Daemon thread pools used for work that must never run on the render thread:
 * scanning packs, decoding preview bytes, writing generated packs and saving profiles.
 */
public final class BackgroundExecutor {
    /** Scanning and pack generation. One thread: these jobs touch the same files. */
    public static final ExecutorService IO = Executors.newSingleThreadExecutor(factory("TextureMixer-IO"));

    /** Preview loading. A couple of threads keep scrolling responsive on big packs. */
    public static final ExecutorService PREVIEWS = Executors.newFixedThreadPool(2, factory("TextureMixer-Preview"));

    /** Profile saving, kept separate so a long scan never delays a save. */
    public static final ExecutorService SAVE = Executors.newSingleThreadExecutor(factory("TextureMixer-Save"));

    private BackgroundExecutor() {
    }

    private static ThreadFactory factory(String name) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, name + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            thread.setUncaughtExceptionHandler((t, e) -> Log.LOGGER.error("Uncaught error on {}", t.getName(), e));
            return thread;
        };
    }
}
