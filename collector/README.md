### 注意
## JmxBeanCollector需要jdk tools.jar支持
1、将tools.jar放到collector模块下，重名为tools-1.8.jar
2、在lib目录下将jar安装到本地maven
```
    mvn install:install-file -Dfile='D:\IdeaProjects\jmx_exporter\collector\lib\tools-1.8.jar' -DgroupId='com.kungeek.srep' -DartifactId='tools' -Dversion='1.8' -Dpackaging=jar
```
3、添加依赖
```
<dependency>
  <groupId>com.kungeek.srep</groupId>
  <artifactId>tools</artifactId>
  <version>1.8</version>
</dependency>
```