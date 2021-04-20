package io.prometheus.jmx;


import io.prometheus.client.exporter.PushGateway;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.jmx.quartz.job.CollectorJob;
import io.prometheus.jmx.quartz.QuartzManager;
import io.prometheus.jmx.quartz.SchedulerProperties;
import org.quartz.Scheduler;

import javax.management.MalformedObjectNameException;
import java.io.File;
import java.io.IOException;

public class SrepJmxAgent {
    public static PushGateway pushGateway;

    public static void main(String[] args) throws IOException, MalformedObjectNameException {
        collectorRegister("E:\\workspace\\jmx_exporter\\example_configs\\tomcat.yml");
        initPushGateway("10.10.1.31:9091");
        initPushJob();
    }

    public static void collectorRegister(String pathName) throws IOException, MalformedObjectNameException {
        new BuildInfoCollector().register();
        // 通过启动参数传入
        new JmxCollector(new File(pathName)).register();
        DefaultExports.initialize();
    }

    public static void initPushGateway(String address) {
        pushGateway = new PushGateway(address);
    }

    public static void initPushJob() {
        SchedulerProperties properties = new SchedulerProperties();
        properties.setTriggerName("collectorTrigger");
        properties.setJobName("collectorJob");
        properties.setCron("0/10 * * * * ?");
        properties.setGroup("collectorGroup");
        properties.setType(CollectorJob.class.getName());
        Scheduler scheduler = QuartzManager.getScheduler();
        QuartzManager.addCronJob(scheduler,properties);
        QuartzManager.start(scheduler);
    }

}
