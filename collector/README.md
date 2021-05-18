### 注意
## JmxBeanCollector需要jdk tools.jar支持
1、添加依赖（jdk tools.jar的部分代码）
```
<dependency>
  <groupId>io.earcam.wrapped</groupId>
  <artifactId>com.sun.tools.attach</artifactId>
  <version>1.8.0_jdk8u172-b11</version>
</dependency>
```
2、打包
使用父工程打包，拿jmx_prometheus_javaagent模块下的jar包

## jar要跟tomcat一起启动（新增指标需要）
