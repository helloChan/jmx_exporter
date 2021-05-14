package io.prometheus.jmx;

import java.math.BigDecimal;

/**
 * @author liuhui1@kungee.com
 * time 2018/12/27 16:53
 * <p>
 * JVM中Bean的分布状况
 */
public class JVMBeanStat {
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
