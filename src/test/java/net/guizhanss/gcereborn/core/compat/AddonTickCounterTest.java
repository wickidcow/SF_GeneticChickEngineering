package net.guizhanss.gcereborn.core.compat;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.guizhanss.guizhanlib.slimefun.addon.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AddonTickCounterTest {

    private Field serverField;
    private Object originalServer;

    @BeforeEach
    void retainBukkitServer() throws ReflectiveOperationException {
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        originalServer = serverField.get(null);
    }

    @AfterEach
    void restoreBukkitServer() throws IllegalAccessException {
        serverField.set(null, originalServer);
    }

    @Test
    void paperKeepsTheOriginalOneTickDelayAndPeriod() throws IllegalAccessException {
        RecordingServer fixture = install(false);
        AtomicInteger counter = new AtomicInteger();

        AddonTickCounter.start(fixture.plugin, 17, counter::incrementAndGet);

        assertEquals(1, fixture.bukkitSubmissions);
        assertEquals(0, fixture.globalSubmissions);
        assertSame(fixture.plugin, fixture.submittedOwner);
        assertEquals(1L, fixture.initialDelay);
        assertEquals(17L, fixture.period);
        assertEquals(0, counter.get(), "the first tick must not execute inline during startup");
        fixture.fire();
        assertEquals(1, counter.get());
    }

    @Test
    void foliaUsesOnlyItsNativeGlobalBookkeepingScheduler() throws IllegalAccessException {
        RecordingServer fixture = install(true);
        AtomicInteger counter = new AtomicInteger();

        AddonTickCounter.start(fixture.plugin, 7, counter::incrementAndGet);

        assertEquals(0, fixture.bukkitSubmissions);
        assertEquals(1, fixture.globalSubmissions);
        assertSame(fixture.plugin, fixture.submittedOwner);
        assertEquals(1L, fixture.initialDelay);
        assertEquals(7L, fixture.period);
        fixture.fire();
        fixture.fire();
        assertEquals(2, counter.get());
    }

    @Test
    void retainsTheUpstreamMinimumIntervalOnBothPlatforms() throws IllegalAccessException {
        for (boolean folia : new boolean[] {false, true}) {
            for (int rate : new int[] {0, -1, Integer.MIN_VALUE}) {
                RecordingServer fixture = install(folia);
                AddonTickCounter.start(fixture.plugin, rate, () -> {});
                assertEquals(1L, fixture.initialDelay);
                assertEquals(1L, fixture.period);
            }
        }
    }

    @Test
    void shutdownCancelsOnceAndDiscardsLateDeliveryOnBothPlatforms() throws IllegalAccessException {
        for (boolean folia : new boolean[] {false, true}) {
            RecordingServer fixture = install(folia);
            AtomicInteger counter = new AtomicInteger();
            Runnable cancel = AddonTickCounter.start(fixture.plugin, 5, counter::incrementAndGet);
            fixture.fire();

            cancel.run();
            cancel.run();
            fixture.fire();

            assertEquals(1, fixture.cancellations);
            assertEquals(1, counter.get());
        }
    }

    @Test
    void refusesRejectedTaskSubmissionWithoutHidingTheFailure() throws IllegalAccessException {
        for (boolean folia : new boolean[] {false, true}) {
            RecordingServer fixture = install(folia);
            fixture.submissionFailure = new IllegalStateException("scheduler is stopped");
            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> AddonTickCounter.start(fixture.plugin, 5, () -> {}));
            assertSame(fixture.submissionFailure, thrown);
            assertEquals(0, fixture.cancellations);
        }
    }

    @Test
    void originalGuizhanCounterPathFailsOnTheSameFoliaFixtureThatTheFixAccepts()
        throws IllegalAccessException {
        RecordingServer fixture = install(true);
        AtomicInteger counter = new AtomicInteger();

        assertThrows(UnsupportedOperationException.class,
            () -> new Scheduler(fixture.plugin).repeat(7, counter::incrementAndGet));
        assertEquals(0, counter.get());

        AddonTickCounter.start(fixture.plugin, 7, counter::incrementAndGet);
        fixture.fire();
        assertEquals(1, counter.get());
    }

    private RecordingServer install(boolean folia) throws IllegalAccessException {
        RecordingServer fixture = new RecordingServer(folia);
        serverField.set(null, fixture.server);
        return fixture;
    }

    private static final class RecordingServer implements InvocationHandler {

        private final boolean folia;
        private final Plugin plugin = proxy(Plugin.class);
        private final Server server = proxy(Server.class);
        private final BukkitScheduler bukkit = proxy(BukkitScheduler.class);
        private final GlobalRegionScheduler global = proxy(GlobalRegionScheduler.class);
        private final BukkitTask bukkitTask = proxy(BukkitTask.class);
        private final ScheduledTask foliaTask = proxy(ScheduledTask.class);
        private Plugin submittedOwner;
        private long initialDelay;
        private long period;
        private int bukkitSubmissions;
        private int globalSubmissions;
        private int cancellations;
        private Runnable tick;
        private RuntimeException submissionFailure;

        private RecordingServer(boolean folia) {
            this.folia = folia;
        }

        private <T> T proxy(Class<T> type) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, this));
        }

        private void fire() {
            tick.run();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object target, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "getServer" -> server;
                case "getName", "getVersion" -> folia ? "Folia" : "Paper";
                case "getScheduler" -> {
                    if (folia) {
                        throw new UnsupportedOperationException("Folia has no Bukkit scheduler");
                    }
                    yield bukkit;
                }
                case "getGlobalRegionScheduler" -> global;
                case "runTaskTimer", "runAtFixedRate" -> {
                    if (submissionFailure != null) {
                        throw submissionFailure;
                    }
                    submittedOwner = (Plugin) arguments[0];
                    initialDelay = (Long) arguments[2];
                    period = (Long) arguments[3];
                    if (method.getName().equals("runTaskTimer")) {
                        bukkitSubmissions++;
                        tick = (Runnable) arguments[1];
                        yield bukkitTask;
                    }
                    globalSubmissions++;
                    Consumer<ScheduledTask> callback = (Consumer<ScheduledTask>) arguments[1];
                    tick = () -> callback.accept(foliaTask);
                    yield foliaTask;
                }
                case "cancel" -> {
                    cancellations++;
                    yield method.getReturnType() == void.class ? null : ScheduledTask.CancelledState.CANCELLED_BY_CALLER;
                }
                case "equals" -> target == arguments[0];
                case "hashCode" -> System.identityHashCode(target);
                case "toString" -> "Recording" + target.getClass().getInterfaces()[0].getSimpleName();
                default -> throw new AssertionError("Unexpected API use: " + method);
            };
        }
    }
}
