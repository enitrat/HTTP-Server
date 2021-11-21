///A Simple Web Server (WebServer.java)

package http.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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
    private static final String AUTHORIZED_DIRECTORY = "doc";
    private static final String INDEX_PATH = "doc/index.html";
    private static final String ERROR_PATH = "doc/404.html";


    /**
     * WebServer constructor.
     */
    protected void start() {
        ServerSocket s;
        Socket client = null;

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
                client = s.accept();
                handleClient(client);
            } catch (Exception e1) {
                System.out.println("Error: " + e1);
                e1.printStackTrace();
                try {
                    sendEmptyResponse(client, "500 Internal Server Error");
                } catch (Exception e2) {
                }
                try {
                    client.close();
                } catch (Exception e) {
                }

            }
        }
    }


    /**
     * Handles the client actions once there is a client connected
     *
     * @param client
     * @throws IOException
     */
    private void handleClient(Socket client) throws IOException {
        //opening IO streams to communicate with client
        BufferedReader in = new BufferedReader(new InputStreamReader(
                client.getInputStream()));


        //Parsing request data
        StringBuilder stringBuffer = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null && !inputLine.equals("")) {
            stringBuffer.append(inputLine);
            stringBuffer.append("\r\n");
        }

        String request = stringBuffer.toString();
        System.out.println("request: " + request);

        String[] requestsLines = request.split("\r\n");
        String[] requestLine = requestsLines[0].split(" ");
        String method = requestLine[0];
        String resource = requestLine[1].substring(1, requestLine[1].length());
        String version = requestLine[2];
        String host = requestsLines[1].split(" ")[1];

        int contentLength = -1;
        List<String> headers = new ArrayList<>();
        for (int h = 2; h < requestsLines.length; h++) {
            String header = requestsLines[h];
            headers.add(header);
            if (header.startsWith("Content-Length:")) {
                String cl = header.substring("Content-Length:".length()).trim();
                contentLength = Integer.parseInt(cl);
            }
        }

        String accessLog = String.format("Client %s, method %s, path %s, version %s, host %s, headers %s",
                client, method, resource, version, host, headers);
        System.out.println(accessLog);

        /**
         * If resource is empty, redirect to index file
         * If it's withing the authorized directory, call the corresponding method
         * Otherwise, access is forbidden for security purposes
         */
        if (resource.isEmpty()) {
            doGET(client, INDEX_PATH);
        } else if (resource.startsWith(AUTHORIZED_DIRECTORY)) {
            if (method.equals("GET")) {
                doGET(client, resource);
            } else if (method.equals("POST")) {
                char[] body = new char[contentLength];  //<-- http body is here
                in.read(body);
                doPOST(client, resource, body);
            } else {
                sendEmptyResponse(client, "501 Not Implemented");
            }
        } else {
            byte[] notFoundContent = "<h1>Access forbidden</h1>".getBytes();
            sendContentResponse(client, "403 Forbidden", "text/html", notFoundContent);
        }
        in.close();
    }

    /**
     * Given a client and a filename to access, returns to the client the content of the file if it exists
     * or 404 otherwise.
     *
     * @param client
     * @param filename
     * @throws IOException
     */
    private void doGET(Socket client, String filename) throws IOException {
        File file = new File(filename);
        if (file.exists() && file.isFile()) {
            Path filePath = Paths.get(filename);
            String contentType = guessContentType(filePath);
            sendContentResponse(client, "200 OK", contentType, Files.readAllBytes(filePath));
        } else {
            Path filePath = Paths.get(ERROR_PATH);
            String contentType = guessContentType(filePath);
            sendContentResponse(client, "404 Not Found", contentType, Files.readAllBytes(filePath));
        }
    }

    /**
     * handles the POST request.
     * Creates a resource if the specified file doesn't exist already.
     * Otherwise, appends the new information to the specified file.
     *
     * @param client
     * @param filename
     * @throws IOException
     */
    private void doPOST(Socket client, String filename, char[] body) throws IOException {
        try {
            File file = new File(filename);
            boolean appendMode = file.exists();
            //Output stream will be in append mode if the file exists, otherwise in the beginning
            BufferedOutputStream fOut = new BufferedOutputStream(new FileOutputStream(file, appendMode));
            System.out.println(body);
            fOut.write(new String(body).getBytes());
            if (appendMode) {
                sendEmptyResponse(client, "200 OK");
            } else {
                sendEmptyResponse(client, "201 CREATED");
            }
            fOut.flush();
            fOut.close();
        } catch (Exception e1) {
            e1.printStackTrace();
            try {
                sendEmptyResponse(client, "500 Internal Server Error");
            } catch (Exception e2) {
                System.out.println(e2);
            }
        }

    }

    private static void sendEmptyResponse(Socket client, String status) throws IOException {
        OutputStream clientOutput = client.getOutputStream();
        clientOutput.write(("HTTP/1.1 " + status + "\r\n").getBytes());
        clientOutput.write("\r\n".getBytes());
        clientOutput.flush();
        clientOutput.close();
    }

    /**
     * Sends the response to the client.
     *
     * @param client
     * @param status
     * @param contentType
     * @param content
     * @throws IOException
     */
    private static void sendContentResponse(Socket client, String status, String contentType, byte[] content) throws IOException {
        OutputStream clientOutput = client.getOutputStream();
        clientOutput.write(("HTTP/1.1 " + status + "\r\n").getBytes());
        clientOutput.write(("ContentType: " + contentType + "\r\n").getBytes());
        clientOutput.write("\r\n".getBytes());
        clientOutput.write(content);
        clientOutput.write("\r\n\r\n".getBytes());
        clientOutput.flush();
        clientOutput.close();
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
