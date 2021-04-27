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
/**
 *
 默认 PushGateway 不做数据持久化操作，当 PushGateway 重启或者异常挂掉，导致数据的丢失，我们可以通过启动时添加 -persistence.file
 和 -persistence.interval 参数来持久化数据。-persistence.file 表示本地持久化的文件，
 将 Push 的指标数据持久化保存到指定文件，-persistence.interval 表示本地持久化的指标数据保留时间，若设置为 5m，则表示 5 分钟后将删除存储的指标数据。

 Prometheus 每次从 PushGateway 拉取的数据，并不是拉取周期内用户推送上来的所有数据，
 而是最后一次 Push 到 PushGateway 上的数据，所以推荐设置推送时间小于或等于 Prometheus 拉取的时间，
 这样保证每次拉取的数据是最新 Push 上来的。

 pushgateway单节点故障
 */
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
