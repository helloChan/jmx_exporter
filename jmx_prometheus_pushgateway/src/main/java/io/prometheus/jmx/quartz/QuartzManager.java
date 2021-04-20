package io.prometheus.jmx.quartz;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import static org.quartz.JobBuilder.newJob;
import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * 定时任务管理器
 */
public class QuartzManager {
    private static final Logger log = LoggerFactory.getLogger(QuartzManager.class);
    public static Scheduler scheduler = null;

    public static Scheduler getScheduler() {
        if (scheduler != null) {
            return scheduler;
        }
        // 首先，必需要取得一个Scheduler的引用
        SchedulerFactory sf = new StdSchedulerFactory();
        try {
            scheduler = sf.getScheduler();
        } catch (SchedulerException e) {
            log.error("获取Scheduler失败：{}", e.getMessage(), e);
        }
        return scheduler;
    }

    public static void start(Scheduler scheduler) {
        if (scheduler == null) {
            log.error("Scheduler为空，调度任务终止........");
            return;
        }
        try {
            scheduler.start();
        } catch (SchedulerException e) {
            log.error("定时调度启动失败：{}", e.getMessage(), e);
        }
    }

    public static void close(Scheduler scheduler) {
        try {
            scheduler.shutdown(true);
        } catch (SchedulerException e) {
            log.error("定时调度关闭失败：{}", e.getMessage(), e);
        }
    }

    public static void addCronJob(Scheduler scheduler, SchedulerProperties properties) {
        try {
            Class<?> a = Class.forName(properties.getType());
            Job o = (Job) a.newInstance();
            // jobs可以在scheduled的sched.start()方法前被调用
            JobDetail job = newJob(o.getClass()).withIdentity(properties.getJobName(), properties.getGroup()).build();
            CronTrigger trigger = newTrigger().withIdentity(properties.getTriggerName(), properties.getGroup()).withSchedule(cronSchedule(properties.getCron())).build();
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            log.error("添加任务失败，任务名称：{}，任务组：{}\n异常信息：{}", properties.getJobName(), properties.getGroup(), e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
