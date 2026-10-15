package com.coresecure.brightcove.wrapper.schedulers.asset_integrator.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.coresecure.brightcove.wrapper.schedulers.asset_integrator.runnables.AssetPropertyIntegratorRunnable;

/**
 * Pins the schedule wiring of the DAM import cron: it is always unscheduled first (so a
 * reconfigure or disable never leaves a stale job), only scheduled when enabled, runs
 * non-concurrently under its fixed job name with the configured expression, and exposes the
 * configured thread count to the manual servlet.
 */
class AssetIntegratorCronBundleImplTest {

    private static final String JOB = "BrightcoveAssetIntegratorCron";

    private Scheduler scheduler;
    private ScheduleOptions options;
    private AssetIntegratorCronBundleImpl bundle;

    private static AssetIntegratorCronBundleImpl.Config config(Map<String, Object> values) {
        return (AssetIntegratorCronBundleImpl.Config) Proxy.newProxyInstance(
                AssetIntegratorCronBundleImplTest.class.getClassLoader(),
                new Class<?>[] { AssetIntegratorCronBundleImpl.Config.class },
                (p, m, a) -> values.containsKey(m.getName()) ? values.get(m.getName()) : m.getDefaultValue());
    }

    @BeforeEach
    void setUp() throws Exception {
        scheduler = mock(Scheduler.class);
        options = mock(ScheduleOptions.class);
        when(scheduler.EXPR(anyString())).thenReturn(options);
        when(options.name(anyString())).thenReturn(options);
        when(options.canRunConcurrently(anyBoolean())).thenReturn(options);
        bundle = new AssetIntegratorCronBundleImpl();
        Field f = AssetIntegratorCronBundleImpl.class.getDeclaredField("scheduler");
        f.setAccessible(true);
        f.set(bundle, scheduler);
    }

    @Test
    void disabledByDefaultUnschedulesAndSchedulesNothing() {
        bundle.activate(config(new HashMap<>()));

        verify(scheduler).unschedule(JOB);
        verify(scheduler, never()).schedule(any(Runnable.class), any(ScheduleOptions.class));
        assertEquals(20, bundle.getMaxThreadNum());
    }

    @Test
    void enabledSchedulesTheRunnableWithTheConfiguredExpression() {
        Map<String, Object> v = new HashMap<>();
        v.put("enable_cron", true);
        v.put("scheduler_expression", "0 0/5 * * * ?");
        v.put("maxThreads", 3);

        bundle.activate(config(v));

        verify(scheduler).unschedule(JOB);
        verify(scheduler).EXPR("0 0/5 * * * ?");
        verify(options).name(JOB);
        verify(options).canRunConcurrently(false);
        ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(job.capture(), any(ScheduleOptions.class));
        assertTrue(job.getValue() instanceof AssetPropertyIntegratorRunnable);
        assertEquals(3, bundle.getMaxThreadNum());
    }

    @Test
    void defaultCronExpressionIsWeeklySundayJustAfterMidnight() {
        Map<String, Object> v = new HashMap<>();
        v.put("enable_cron", true);

        bundle.activate(config(v));

        verify(scheduler).EXPR("0 5 0 ? * SUN");
    }

    @Test
    void deactivateUnschedulesTheJob() {
        bundle.deactivate(config(new HashMap<>()));

        verify(scheduler).unschedule(JOB);
    }
}
