package io.prometheus.jmx;

import com.sun.tools.attach.VirtualMachine;
import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;
import sun.tools.attach.HotSpotVirtualMachine;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <pre>
 * bean信息采集
 * </pre>
 *
 * @author chenxinhuan  chenxinhuan@kungeek.com
 * @version 1.00.00
 * <pre>
 * 修改记录
 *    修改后版本:     修改人：  修改日期:     修改内容:
 * </pre>
 */
public class JmxBeanCollector extends Collector {

    @Override
    public <T extends Collector> T register() {
        return super.register();
    }

    @Override
    public List<MetricFamilySamples> collect() {
        List<JVMBeanStat> jvmBeanStats = this.beansStatWithMBean();
        if (jvmBeanStats == null || jvmBeanStats.size() == 0) {
            return null;
        }
        List<MetricFamilySamples> mfs = new ArrayList();
        GaugeMetricFamily classCount = new GaugeMetricFamily("srep_jmx_class_count", "jmx类总数", Collections.singletonList("name"));
        GaugeMetricFamily classTotalMemoryByte = new GaugeMetricFamily("srep_jmx_class_total_memory_byte", "jmx类占用总内存", Collections.singletonList("name"));
        jvmBeanStats.forEach(jvmBeanStat -> {
            classCount.addMetric(Collections.singletonList(jvmBeanStat.getClassName()), jvmBeanStat.getInstanceNum());
            classTotalMemoryByte.addMetric(Collections.singletonList(jvmBeanStat.getClassName()), jvmBeanStat.getMemoryByte().doubleValue());
        });
        mfs.add(classCount);
        mfs.add(classTotalMemoryByte);
        return mfs;
    }

    public List<JVMBeanStat> beansStatWithMBean() {
        StringBuilder sb = new StringBuilder();
        List<JVMBeanStat> jvmBeanStats = new ArrayList<>();
        HotSpotVirtualMachine hotSpot = null;
        BufferedReader in = null;

        try {
            String line;
            int count = 0;
            String pid = String.valueOf(processID());
            hotSpot = (HotSpotVirtualMachine) VirtualMachine.attach(pid);
            in = new BufferedReader(new InputStreamReader(hotSpot.heapHisto("-all")));

            while (true) {
                line = in.readLine();

                // 最后不足50行的数据
                if (line == null) {
                    if (count > 0) {
                        List<JVMBeanStat> tem = buildJVMBeanStat(sb.toString());
                        if (tem != null && tem.size() > 0)
                            jvmBeanStats.addAll(tem);
                    }

                    break;
                }

                if (line.length() < 1) {
                    continue;
                }

                sb.append(line).append("\n");
                count++;

                // 每50行清空一次
                if (count == 50) {
                    List<JVMBeanStat> tem = buildJVMBeanStat(sb.toString());
                    if (tem != null && tem.size() > 0) {
                        jvmBeanStats.addAll(tem);
                    }

                    sb.delete(0, sb.length());                               //清空
                    count = 0;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (hotSpot != null) {
                    hotSpot.detach();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return jvmBeanStats.stream().sorted(Comparator.comparing(JVMBeanStat::getMemoryByte).reversed()).limit(5).collect(Collectors.toList());
    }

    private List<JVMBeanStat> buildJVMBeanStat(String beanStr) {
        List<Map<String, String>> beansMap = handleBeanInfoStr(beanStr);
        if (beansMap == null) {
            return null;
        }

        List<JVMBeanStat> beans = new ArrayList<>();
        beansMap.forEach(map -> {
            JVMBeanStat bean = new JVMBeanStat();
            bean.setClassName(map.get("className"));
            bean.setInstanceNum(Integer.valueOf(map.get("instanceNum")));
            bean.setMemoryByte(new BigDecimal(map.get("memoryByte")));

            beans.add(bean);
        });

        return beans.stream().sorted(Comparator.comparing(JVMBeanStat::getMemoryByte).reversed()).limit(5).collect(Collectors.toList());
    }

    private List<Map<String, String>> handleBeanInfoStr(String beanInfoStr) {
        if (this.isEmpty(beanInfoStr)) {
            return null;
        }

        List<Map<String, String>> beans = new ArrayList<>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(beanInfoStr.getBytes())));

            String line;

            while ((line = br.readLine()) != null) {
                if (!line.contains("com.kungeek")) {
                    continue;
                }

                String[] tem = line.trim().split(" +");

                Map<String, String> bean = new HashMap<>();
                bean.put("instanceNum", tem[1]);
                bean.put("memoryByte", tem[2]);
                bean.put("className", tem[3]);

                beans.add(bean);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return beans;
    }

    public <T> boolean isEmpty(T t) {
        boolean flag = false;

        if (null == t)
            flag = true;

        if (t instanceof List) {
            if (0 == ((List) t).size())
                flag = true;
        }

        if (t instanceof Map) {
            if (0 == ((Map) t).size())
                flag = true;
        }

        if (t instanceof String) {
            if ("".equals(((String) t).trim()))
                flag = true;
        }

        return flag;
    }

    private long processID() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        String pid = name.split("@")[0];
        return Long.valueOf(pid);
    }
}
