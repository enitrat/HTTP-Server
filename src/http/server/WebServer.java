///A Simple Web Server (WebServer.java)

package http.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.Buffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static javax.script.ScriptEngine.FILENAME;

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
    private static final String AUTHORIZED_USER_DIRECTORY = "doc/users/";
    private static final String INDEX_PATH = "doc/index.html";
    private static final String ERROR_PATH = "doc/404.html";


    /**
     * Start the application.
     *
     * @param args Command line parameters are not used.
     */
    public static void main(String[] args) {
        WebServer ws = new WebServer();
        ws.start();
    }

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

                ClientHandler clientSock = new ClientHandler(client);
                new Thread(clientSock).start();
            } catch (Exception e1) {
                System.out.println("Error: " + e1);
                e1.printStackTrace();

            }
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket client;

        // Constructor
        public ClientHandler(Socket socket) {
            this.client = socket;
        }

        @Override
        public void run() {
            try {
                handleClient(this.client);
            } catch (Exception e1) {
                System.out.println("Error: " + e1);
                e1.printStackTrace();
                try {
                    sendHeader(client, "500 Internal Server Error");
                } catch (Exception e2) {
                }
                try {
                    client.close();
                } catch (Exception e) {
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
            BufferedInputStream in = new BufferedInputStream(client.getInputStream());
            String request = new String();

            //While we don't encounter an EOF or a CRLF, we add to the request string the parameters.
            int bcur = '\0', bprec = '\0';
            boolean newline = false;
            while ((bcur = in.read()) != -1 && !(newline && bprec == '\r' && bcur == '\n')) {
                if (bprec == '\r' && bcur == '\n') {
                    newline = true;
                } else if (!(bprec == '\n' && bcur == '\r')) {
                    newline = false;
                }
                bprec = bcur;
                request += (char) bcur;
            }

            System.out.println("request: " + request);
            if (request.isEmpty()) {
                sendHeader(client, "400 Bad Request");
                return;
            }

            String[] requestsLines = request.split("\r\n");
            String[] requestLine = requestsLines[0].split(" ");

            String method = requestLine[0];
            String filename = requestLine[1].substring(1, requestLine[1].length());
            String version = requestLine[2];
            String host = requestsLines[1].split(" ")[1];

            List<String> headers = new ArrayList<>();
            for (int h = 2; h < requestsLines.length - 1; h++) {
                String header = requestsLines[h];
                headers.add(header);
            }
            /**
             * If resource is empty, redirect to index file
             * If it's withing the authorized directory, call the corresponding method
             * Otherwise, access is forbidden for security purposes
             */
            try {
                if (headers.isEmpty() || method.isEmpty()) {
                    sendHeader(client, "400 Bad Request");
                    return;
                }

                if (filename.isEmpty()) {
                    if (method.equals("GET")) {
                        doGET(client, INDEX_PATH);
                    } else if (method.equals("HEAD")) {
                        doHEAD(client, INDEX_PATH);
                    } else if (method.equals("PUT")) {
                        doPUT(in, client, filename);
                    } else if (method.equals("POST")) {
                        doPOST(in, client, filename);
                    } else {
                        sendHeader(client, "403 Forbidden");
                    }

                } else if (filename.startsWith(AUTHORIZED_DIRECTORY)) {
                    if (method.equals("GET")) {
                        doGET(client, filename);
                    } else if (method.equals("POST")) {
                        doPOST(in, client, filename);
                    } else if (method.equals("PUT")) {
                        doPUT(in, client, filename);
                    } else if (method.equals("HEAD")) {
                        doHEAD(client, filename);
                    } else if (method.equals("DELETE")) {
                        doDELETE(client, filename);
                    } else {
                        sendHeader(client, "501 Not Implemented");
                    }
                } else {
                    sendHeader(client, "403 Forbidden");
                }
            } catch (Exception e) {
                try {
                    sendHeader(client, "500 Internal Server Error");
                } catch (Exception e2) {
                }
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
                sendContentResponse(client, "200 OK", contentType, Files.readAllBytes(filePath), file.length());
            } else {
                sendHeader(client, "404 Not Found");
            }
        }

        private void doHEAD(Socket client, String filename) throws IOException {
            File file = new File(filename);
            if (file.exists() && file.isFile()) {
                Path filePath = Paths.get(filename);
                String contentType = guessContentType(filePath);
                sendHeader(client, "200 OK", contentType, file.length());
            } else {
                sendHeader(client, "404 Not Found");
            }
        }

        /**
         * handles the POST request.
         * Creates a resource if the specified file doesn't exist already.
         * Otherwise, appends the new information to the specified file.
         *
         * @param in
         * @param client
         * @param filename
         * @throws IOException
         */
        private void doPOST(BufferedInputStream in, Socket client, String filename) throws IOException {
            File file = new File(filename);
            boolean appendMode = file.exists();
            //Output stream will be in append mode if the file exists, otherwise in the beginning
            BufferedOutputStream fOut = new BufferedOutputStream(new FileOutputStream(file, appendMode));
            byte[] buffer = new byte[256];
            while (in.available() > 0) {
                int nbRead = in.read(buffer);
                fOut.write(buffer, 0, nbRead);
            }
            if (appendMode) {
                sendHeader(client, "200 OK");
            } else {
                sendHeader(client, "201 Created");
            }
            fOut.flush();
            fOut.close();
        }

        /**
         * Implementation of the HTTP PUT request method according to the specifications listed on
         * <a href=https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/PUT>the mozilla developer docs</a>
         *
         * @param in
         * @param client
         * @param filename
         */
        private void doPUT(BufferedInputStream in, Socket client, String filename) throws IOException {
            File file = new File(filename);//Output stream will be in append mode if the file exists, otherwise in the beginning
            boolean exists = file.exists();
            PrintWriter writer = new PrintWriter(filename);
            writer.print("");
            writer.close();
            BufferedOutputStream fOut = new BufferedOutputStream(new FileOutputStream(file));

            byte[] buffer = new byte[256];
            while (in.available() > 0) {
                int nbRead = in.read(buffer);
                fOut.write(buffer, 0, nbRead);
            }
            if (exists) {
                sendHeader(client, "204 No Content");
            } else {
                sendHeader(client, "201 Created");
            }
            fOut.flush();
            fOut.close();
        }

        private void doDELETE(Socket client, String filename) throws IOException {
            try {
                File file = new File(filename);//Output stream will be in append mode if the file exists, otherwise in the beginning
                boolean exists = file.exists();
                boolean deleted = false;
                if (file.exists() && file.isFile()) {
                    deleted = file.delete();
                }
                if (deleted) {
                    sendHeader(client, "204 No Content");
                } else if (!exists) {
                    sendHeader(client, "404 Not Found");
                } else {
                    sendHeader(client, "403 Forbidden");
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }

        private static void sendHeader(Socket client, String status) throws IOException {
            OutputStream clientOutput = client.getOutputStream();
            clientOutput.write(("HTTP/1.1 " + status + "\r\n").getBytes());
            clientOutput.write("\r\n".getBytes());
            clientOutput.flush();
            clientOutput.close();
        }

        private static void sendHeader(Socket client, String status, String contentType, long length) throws IOException {
            OutputStream clientOutput = client.getOutputStream();
            clientOutput.write(("HTTP/1.1 " + status + "\r\n").getBytes());
            clientOutput.write(("Content-Type: " + contentType + "\r\n").getBytes());
            clientOutput.write(("Content-Length: " + length + "\r\n").getBytes());
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
        private static void sendContentResponse(Socket client, String status, String contentType, byte[] content, long length) throws IOException {
            OutputStream clientOutput = client.getOutputStream();
            clientOutput.write(("HTTP/1.1 " + status + "\r\n").getBytes());
            clientOutput.write(("Content-Type: " + contentType + "\r\n").getBytes());
            clientOutput.write(("Content-Length: " + length + "\r\n").getBytes());
            clientOutput.write("\r\n".getBytes());
            clientOutput.write(content);
            clientOutput.write("\r\n\r\n".getBytes());
            clientOutput.flush();
            clientOutput.close();
        }

        private static String guessContentType(Path filePath) throws IOException {
            return Files.probeContentType(filePath);
        }
    }

}
