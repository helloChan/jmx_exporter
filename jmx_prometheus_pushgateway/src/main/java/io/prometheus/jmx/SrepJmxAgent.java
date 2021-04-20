package io.prometheus.jmx;


import io.prometheus.client.exporter.PushGateway;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.jmx.quartz.job.CollectorJob;
import io.prometheus.jmx.quartz.QuartzManager;
import io.prometheus.jmx.quartz.SchedulerProperties;
import org.quartz.Scheduler;

import javax.management.MalformedObjectNameException;
import java.io.*;
import java.util.Properties;

public class SrepJmxAgent {
    public static PushGateway pushGateway;
    public static Properties properties;

    /**
     * 启动参数使用空格分开
     * pushgatewayAddredd=10.10.1.31:9091 config=tomcat.yml
     * @param args
     * @throws IOException
     * @throws MalformedObjectNameException
     */
    public static void main(String[] args) throws IOException, MalformedObjectNameException {
        String address = "";
        String pathName = "";
        for (String arg : args) {
            if (arg.contains("pushgatewayAddredd=")) {
                address = arg.substring(arg.indexOf("=")+1);
            }
            if (arg.contains("config=")) {
                pathName = "E:\\workspace\\jmx_exporter\\example_configs\\" + arg.substring(arg.indexOf("=")+1);
            }
        }
        collectorRegister(pathName);
        initPushGateway(address);
        initPushJob();
    }

    public static void initProperties() throws IOException {
        InputStream in = new FileInputStream(new File(System.getProperty("user.dir") + File.separator + "application.properties"));
        properties.load(in);
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
