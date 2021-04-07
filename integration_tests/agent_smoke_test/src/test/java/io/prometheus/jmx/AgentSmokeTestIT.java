package io.prometheus.jmx;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Runs the EchoServer on different Java Docker images with the jmx_exporter agent attached,
 * and checks if the metric java_lang_Memory_NonHeapMemoryUsage_committed is exported.
 * Run with
 * <pre>mvn verify</pre>
 */
@RunWith(Parameterized.class)
public class AgentSmokeTestIT {

  private final String propertiesFile = "test.properties";
  private final OkHttpClient client = new OkHttpClient();

  private final String baseImage;
  private final int serverPort;
  private final int metricsPort;

  @Rule
  public JavaContainer javaContainer;

  @Parameterized.Parameters
  public static String[] images() {
    return new String[] {
        "openjdk:11-jre",
        "openjdk:8-jre",
        "ticketfly/java:6",
        "ibmjava:8-jre",
    };
  }

  public AgentSmokeTestIT(String baseImage) throws IOException {
    this.baseImage = baseImage;
    Properties properties = new Properties();
    properties.load(AgentSmokeTestIT.class.getResourceAsStream("/" + propertiesFile));
    serverPort = parseInt(properties, "server.port");
    metricsPort = parseInt(properties, "metrics.port");

    String agentJarName = readString(properties, "agent.jar");
    String dockerfileContent = loadDockerfile();

    javaContainer = new JavaContainer(dockerfileContent, agentJarName).withExposedPorts(metricsPort, serverPort);
  }

  private static class JavaContainer extends GenericContainer<JavaContainer> {
    JavaContainer(String dockerFileContent, String agentJarName) {
      super(new ImageFromDockerfile("jmx_exporter-echo-server")
          .withFileFromPath("echo_server.jar", Paths.get("../echo_server/target/echo_server.jar"))
          .withFileFromPath(agentJarName, Paths.get("../../jmx_prometheus_javaagent/target/" + agentJarName))
          .withFileFromClasspath("config.yml", "/config.yml")
          .withFileFromString("Dockerfile", dockerFileContent));
    }
  }

  @Test
  public void testMetrics() throws Exception {
    System.out.println("Running agent smoke test with Docker image " + baseImage);
    String metricName = "java_lang_Memory_NonHeapMemoryUsage_committed";
    List<String> lines = scrapeMetrics(10 * 1000);
    boolean found = false;
    for (String line : lines) {
      if (line.startsWith(metricName)) {
        double value = Double.parseDouble(line.split(" ")[1]);
        Assert.assertTrue(metricName + " should be > 0", value > 0);
        found = true;
        break;
      }
    }
    if (!found) {
      Assert.fail(metricName + " not found");
    }
    gracefulShutdown();
  }

  private List<String> scrapeMetrics(long timeoutMillis) {
    long start = System.currentTimeMillis();
    Exception exception = null;
    String host = javaContainer.getHost();
    Integer port = javaContainer.getMappedPort(metricsPort);
    String metricsUrl = "http://" + host + ":" + port + "/metrics";
    while (System.currentTimeMillis() - start < timeoutMillis) {
      try {
        Request request = new Request.Builder()
            .header("Accept", "application/openmetrics-text; version=1.0.0; charset=utf-8")
            .url(metricsUrl)
            .build();
        try (Response response = client.newCall(request).execute()) {
          return Arrays.asList(response.body().string().split("\\n"));
        }
      } catch (Exception e) {
        exception = e;
        try {
          Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }
      }
    }
    if (exception != null) {
      exception.printStackTrace();
    }
    Assert.fail("Timeout while getting metrics from " + metricsUrl + " (orig port: " + metricsPort + ")");
    return null; // will not happen
  }

  private void gracefulShutdown() throws Exception {
    Socket clientSocket = connect(10 * 1000);
    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    out.println("quit");
    String response = in.readLine();
    if (!"goodbye".equals(response.trim())) {
      throw new Exception("Received unexpected response: " + response);
    }
    out.close();
    in.close();
    clientSocket.close();
  }

  private Socket connect(long timeoutMillis) {
    long start = System.currentTimeMillis();
    String host = javaContainer.getHost();
    Integer port = javaContainer.getMappedPort(serverPort);
    Exception exception = null;
    while (System.currentTimeMillis() - start < timeoutMillis) {
      try {
        return new Socket(javaContainer.getHost(), javaContainer.getMappedPort(serverPort));
      } catch (IOException e) {
        exception = e;
      }
    }
    if (exception != null) {
      exception.printStackTrace();
    }
    Assert.fail("Timeout while connecting to " + host + " on port " + port);
    return null; // will not happen
  }

  private String readString(Properties props, String key) {
    if (!props.containsKey(key)) {
      throw new RuntimeException(key + " not found in " + propertiesFile);
    }
    return props.getProperty(key);
  }

  private int parseInt(Properties props, String key) {
    String value = readString(props, key);
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new RuntimeException(value + " is not a valid number for " + key + " in " + propertiesFile);
    }
  }

  private String loadDockerfile() throws IOException {
    InputStream in = AgentSmokeTestIT.class.getResourceAsStream("/Dockerfile");
    StringBuilder result = new StringBuilder();
    try (Reader reader = new BufferedReader(new InputStreamReader(in, Charset.forName(UTF_8.name())))) {
      int c;
      while ((c = reader.read()) != -1) {
        result.append((char) c);
      }
    }
    return result.toString()
        .replace("${base.image}", baseImage)
        .replace("${metrics.port}", Integer.toString(metricsPort))
        .replace("${server.port}", Integer.toString(serverPort));
  }
}
