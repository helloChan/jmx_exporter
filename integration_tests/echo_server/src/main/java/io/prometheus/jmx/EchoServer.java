package io.prometheus.jmx;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Simple echo server. Takes TCP connections on the specified port,
 * and echoes the messages sent to it.
 * Terminates when it receives the message "quit".
 * <p/>
 * The goal is to have some simple sample application that is able to run on Java 6.
 */
public class EchoServer {

  public static void main(String[] args) throws Exception {
    int port = -1;
    if (args.length == 0) {
      System.err.println("Usage: EchoServer <port>");
      System.exit(-1);
    }
    try {
      port = Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      System.err.println(args[0] + ": invalid port");
      System.exit(-1);
    }
    System.out.println("Starting " + EchoServer.class.getSimpleName() + " on port " + port + ".");
    ServerSocket serverSocket = new ServerSocket(port);
    while (true) {
      Socket clientSocket = serverSocket.accept();
      Thread thread = new Thread(new ConnectionHandler(clientSocket));
      thread.setDaemon(true);
      thread.start();
    }
  }

  private static class ConnectionHandler implements Runnable {

    private final Socket clientSocket;

    private ConnectionHandler(Socket clientSocket) {
      this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
      try {
        System.out.println("client connected from " + clientSocket.getInetAddress());
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        String line;
        while ((line = in.readLine()) != null) {
          if ("quit".equals(line.trim())) {
            out.println("goodbye\n");
            out.flush();
            out.close();
            in.close();
            clientSocket.close();
            return;
          }
          out.println(line);
        }
      } catch (IOException e) {
        System.out.println("client disconnected: " + e.getMessage());
      }
    }
  }
}
