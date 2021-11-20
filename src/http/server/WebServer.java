///A Simple Web Server (WebServer.java)

package http.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Example program from Chapter 1 Programming Spiders, Bots and Aggregators in
 * Java Copyright 2001 by Jeff Heaton
 * <p>
 * WebServer is a very simple web-server. Any request is responded with a very
 * simple web-page.
 *
 * @author Jeff Heaton
 * @version 1.0
 */
public class WebServer {

    public static final int PORT = 8080;
    private static final String RESOURCE_DIRECTORY = "doc";
    private static final String INDEX_FILENAME = "doc/index.html";
    private static final String ERROR_404 = "doc/404.html";


    /**
     * WebServer constructor.
     */
    protected void start() {
        ServerSocket s;

        System.out.println("Webserver starting up on port " + PORT);
        System.out.println("(press ctrl-c to exit)");
        try {
            // create the main server socket
            s = new ServerSocket(PORT);
        } catch (Exception e) {
            System.out.println("Error: " + e);
            return;
        }

        System.out.println("Waiting for connection");
        for (; ; ) {
            try {
                // wait for a connection
                Socket client = s.accept();
                handleClient(client);
            } catch (Exception e) {
                System.out.println("Error: " + e);
            }
        }
    }

    /**
     * Handles the client actions once there is a client connected
     * @param client
     * @throws IOException
     */
    private void handleClient(Socket client) throws IOException {
        //opening IO streams to communicate with client
        BufferedReader in = new BufferedReader(new InputStreamReader(
                client.getInputStream()));

        //Parsing request data
        StringBuilder requestBuilder = new StringBuilder();
        String line;
        while (!(line = in.readLine()).isBlank()) {
            requestBuilder.append(line + "\r\n");
        }
        String request = requestBuilder.toString();
        System.out.println(request);
        String[] requestsLines = request.split("\r\n");
        String[] requestLine = requestsLines[0].split(" ");
        String method = requestLine[0];
        String resource = requestLine[1].substring(1, requestLine[1].length());
        String version = requestLine[2];
        String host = requestsLines[1].split(" ")[1];

        List<String> headers = new ArrayList<>();
        for (int h = 2; h < requestsLines.length; h++) {
            String header = requestsLines[h];
            headers.add(header);
        }

        String accessLog = String.format("Client %s, method %s, path %s, version %s, host %s, headers %s",
                client, method, resource, version, host, headers);
        System.out.println(accessLog);

        /**
         * If resource is empty, redirect to index file
         * If it's withing the authorized directory, call the httpGet methode
         * Otherwise, access is forbidden for security purposes
         */
        if (resource.isEmpty()) {
            httpGET(client, INDEX_FILENAME);
        } else if (resource.startsWith(RESOURCE_DIRECTORY)) {
            if (method.equals("GET")) {
                httpGET(client, resource);
            } else {
                sendResponse(client, "501 Not Implemented", null, null);
            }
        }
        else{
            byte[] notFoundContent = "<h1>Access forbidden</h1>".getBytes();
            sendResponse(client, "403 Forbidden", "text/html", notFoundContent);

        }
    }

    /**
     * Given a client and a filename to access, returns to the client the content of the file if it exists
     * or 404 otherwise.
     * @param client
     * @param filename
     * @throws IOException
     */
    private void httpGET(Socket client, String filename) throws IOException {
        File resource = new File(filename);
        if (resource.exists() && resource.isFile()) {
            Path filePath = Paths.get(filename);
            String contentType = guessContentType(filePath);
            sendResponse(client, "200 OK", contentType, Files.readAllBytes(filePath));
        } else {
            Path filePath = Paths.get(ERROR_404);
            String contentType = guessContentType(filePath);
            sendResponse(client, "404 Not Found", contentType, Files.readAllBytes(filePath));
        }
    }

    /**
     * Sends the response to the client.
     * @param client
     * @param status
     * @param contentType
     * @param content
     * @throws IOException
     */
    private static void sendResponse(Socket client, String status, String contentType, byte[] content) throws IOException {
        OutputStream clientOutput = client.getOutputStream();
        clientOutput.write(("HTTP/1.1 \r\n" + status).getBytes());
        clientOutput.write(("ContentType: " + contentType + "\r\n").getBytes());
        clientOutput.write("\r\n".getBytes());
        clientOutput.write(content);
        clientOutput.write("\r\n\r\n".getBytes());
        clientOutput.flush();
        client.close();
    }

    private static String guessContentType(Path filePath) throws IOException {
        return Files.probeContentType(filePath);
    }

    /**
     * Start the application.
     *
     * @param args Command line parameters are not used.
     */
    public static void main(String[] args) {
        WebServer ws = new WebServer();
        ws.start();
    }
}
