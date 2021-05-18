package io.prometheus.jmx;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.spi.AttachProvider;
import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;
import sun.tools.attach.HotSpotVirtualMachine;
import sun.tools.attach.LinuxAttachProvider;
import sun.tools.attach.WindowsAttachProvider;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
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
public class JvmBeanCollector extends Collector {

    @Override
    public List<MetricFamilySamples> collect() {
        List<JVMBeanStat> jvmBeanStats = this.beansStatWithMBean(processID().toString());
        List<MetricFamilySamples> mfs = new ArrayList();
        if (jvmBeanStats == null || jvmBeanStats.size() == 0) {
            return mfs;
        }
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

    public static void main(String[] args) throws IOException, AttachNotSupportedException {
        HotSpotVirtualMachine hotSpot = (HotSpotVirtualMachine) JvmBeanCollector.attach("15640");
        System.out.println("--->");
        JvmBeanCollector jvm = new JvmBeanCollector();
        Long aLong = jvm.remoteProcessID("service:jmx:rmi:///jndi/rmi://:1099/jmxrmi");
        List<JVMBeanStat> jvmBeanStats = jvm.beansStatWithMBean(aLong.toString());
        System.out.println(jvmBeanStats.size());
    }

    public static VirtualMachine attach(String id) throws AttachNotSupportedException, IOException {
        AttachProvider provider = currentSystemAttachProvider();
        return provider.attachVirtualMachine(id);
    }

    public static AttachProvider currentSystemAttachProvider() {
        if (System.getProperty("os.name", "").toLowerCase().contains("windows")) {
            return new WindowsAttachProvider();
        } else {
            return new LinuxAttachProvider();
        }
    }
    /**
     * 虚拟机进程ID
     *
     * @param processID
     * @return
     */
    public List<JVMBeanStat> beansStatWithMBean(String processID) {
        StringBuilder sb = new StringBuilder();
        List<JVMBeanStat> jvmBeanStats = new ArrayList<>();
        HotSpotVirtualMachine hotSpot = null;
        BufferedReader in = null;

        try {
            String line;
            int count = 0;
            hotSpot = (HotSpotVirtualMachine) JvmBeanCollector.attach(processID);
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

    /**
     * 获取指定jmx的虚拟机进程号
     *
     * @return
     * @throws IOException
     * @Param service:jmx:rmi:///jndi/rmi://:1099/jmxrmi
     */
    public Long remoteProcessID(String jmxURL) throws IOException {
        JMXConnector conn = getRemoteJMXConnector(jmxURL);
        MBeanServerConnection remoteMBeanServerConnection = getRemoteMBeanServerConnection(conn);
        RuntimeMXBean remoteRuntimeMXBean = getRemoteRuntimeMXBean(remoteMBeanServerConnection);
        String name = remoteRuntimeMXBean.getName();
        String pid = name.split("@")[0];
        return Long.valueOf(pid);
    }

    public RuntimeMXBean getRemoteRuntimeMXBean(MBeanServerConnection connection) throws IOException {
        return ManagementFactory.newPlatformMXBeanProxy(connection, ManagementFactory.RUNTIME_MXBEAN_NAME, RuntimeMXBean.class);
    }

    public MBeanServerConnection getRemoteMBeanServerConnection(JMXConnector conn) throws IOException {
        return conn.getMBeanServerConnection();
    }

    public JMXConnector getRemoteJMXConnector(String jmxURL) throws IOException {
        JMXServiceURL serviceURL = new JMXServiceURL(jmxURL);
        JMXConnector conn = JMXConnectorFactory.connect(serviceURL);
        return conn;
    }

    /**
     * 获取的是当前Agent的虚拟机进程号
     *
     * @return
     */
    private Long processID() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        String pid = name.split("@")[0];
        return Long.valueOf(pid);
    }

    /**
     * @author liuhui1@kungee.com
     * time 2018/12/27 16:53
     * <p>
     * JVM中Bean的分布状况
     */
    public static class JVMBeanStat {
        private String className;  //类名
        private int instanceNum;  //实例数量
        private BigDecimal memoryByte; //占用多少byte内存  单位：byte

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public int getInstanceNum() {
            return instanceNum;
        }

        public void setInstanceNum(int instanceNum) {
            this.instanceNum = instanceNum;
        }

        public BigDecimal getMemoryByte() {
            return memoryByte;
        }

        public void setMemoryByte(BigDecimal memoryByte) {
            this.memoryByte = memoryByte;
        }

        @Override
        public String toString() {
            return "JVMBeanStat{" +
                    "className='" + className + '\'' +
                    ", instanceNum=" + instanceNum +
                    ", memoryByte=" + memoryByte +
                    '}';
        }
    }
}

