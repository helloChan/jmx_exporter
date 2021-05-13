package io.prometheus.jmx.quartz.job;


import io.prometheus.client.CollectorRegistry;
import io.prometheus.jmx.SrepJmxAgent;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class CollectorJob implements Job {

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            SrepJmxAgent.pushGateway.push(CollectorRegistry.defaultRegistry,"jmx_job");
            if (1 == 2) {
                Map<String, String> groupingKey = new HashMap<String, String>();
                InetAddress addr = InetAddress.getLocalHost();
                groupingKey.put("instance", addr.getHostAddress());
                groupingKey.put("group", "srep");
                SrepJmxAgent.pushGateway.push(CollectorRegistry.defaultRegistry,"jmx_job", groupingKey);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
