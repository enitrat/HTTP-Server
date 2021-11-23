///A Simple Web Server (WebServer.java)

package http.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
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
                    sendHeader(client, "500 Internal Server Error");
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
                client.getInputStream(),StandardCharsets.UTF_8));

        //Parsing request data
        StringBuilder stringBuffer = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null && !inputLine.equals("")) {
            stringBuffer.append(inputLine);
            stringBuffer.append("\r\n");
        }

        String request = stringBuffer.toString();
        System.out.println("request: " + request);
        if (request.isEmpty()) {
            sendHeader(client,"400 Bad Request");
            return;
        }
        String[] requestsLines = request.split("\r\n");
        String[] requestLine = requestsLines[0].split(" ");

        String method = requestLine[0];
        String filename = requestLine[1].substring(1, requestLine[1].length());
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
                client, method, filename, version, host, headers);
        System.out.println(accessLog);

        /**
         * If resource is empty, redirect to index file
         * If it's withing the authorized directory, call the corresponding method
         * Otherwise, access is forbidden for security purposes
         */
        try{
            if(headers.isEmpty() || method.isEmpty()){
                sendHeader(client,"400 Bad Request");
                return;
            }

        if (filename.isEmpty() && (method.equals("GET") || method.equals("HEAD"))) {
            if (method.equals("GET")) {
                doGET(client, INDEX_PATH);
            }
            else if (method.equals("HEAD")){
                doHEAD(client,INDEX_PATH);
            }
        } else if (filename.isEmpty()){
            if (method.equals("PUT")) {
                String parsedString = getParsedString(in, contentLength);
                String data = parsedString;
                parsedString = createHTMLFile(data);
                filename = createHTMLFileName(data);
                doPUT(client,filename, parsedString);
            } else if(method.equals("POST")){
                String parsedString = getParsedString(in, contentLength);
                String data = parsedString;
                parsedString = createHTMLFile(data);
                filename = createHTMLFileName(data);
                doPOST(client, filename, parsedString);
            }
        }else if (filename.startsWith(AUTHORIZED_DIRECTORY)) {
            System.out.println("here");
            if (method.equals("GET")) {
                doGET(client, filename);
            } else if (method.equals("POST")) {
                String parsedString = getParsedString(in, contentLength);
                doPOST(client, filename, parsedString);
            } else if (method.equals("PUT")) {
                String parsedString = getParsedString(in, contentLength);
                doPUT(client, filename, parsedString);
            }else if (method.equals("HEAD")) {
                doHEAD(client, filename);
            }else if (method.equals("DELETE")){
                doDELETE(client,filename);
            } else {
                sendHeader(client, "501 Not Implemented");
            }
        } else {
            sendHeader(client, "403 Forbidden");
        }
        }catch(Exception e){
            try{
                sendHeader(client,"500 Internal Server Error");
            }catch(Exception e2){
            }
        }

        in.close();
    }

    /**
     * Returns a parsed string from a file sent by the client. The parsed string removes all boundaries and Content indications
     * from the file.
     * @param in
     * @param contentLength
     * @return
     * @throws IOException
     */
    private String getParsedString(BufferedReader in, int contentLength) throws IOException {
        char[] body = new char[contentLength];
        in.read(body);
        String strBody = new String(body);
        Scanner scanner = new Scanner(strBody);
        String boundaryLine = null;
        StringBuilder newStringBuilder = new StringBuilder();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.startsWith("--")){
                boundaryLine = line;
            }
            if (boundaryLine==null || (!line.startsWith(boundaryLine) && (!line.startsWith("Content")))){
                newStringBuilder.append(line+"\r\n");
            }
        }
        scanner.close();
        String parsedString = newStringBuilder.toString();
        return parsedString;
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
            sendContentResponse(client, "200 OK", contentType, Files.readAllBytes(filePath),file.length());
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
     * @param client
     * @param filename
     * @throws IOException
     */
    private void doPOST(Socket client, String filename, String body) throws IOException {
        File file = new File(filename);
        boolean appendMode = file.exists();
        //Output stream will be in append mode if the file exists, otherwise in the beginning
        BufferedOutputStream fOut = new BufferedOutputStream(new FileOutputStream(file, appendMode));
        fOut.write(body.getBytes(StandardCharsets.UTF_8));
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
     * @param client
     * @param filename
     * @param body
     */
    private void doPUT(Socket client, String filename, String body) throws IOException {
        File file = new File(filename);//Output stream will be in append mode if the file exists, otherwise in the beginning
        boolean exists = file.exists();
        PrintWriter writer = new PrintWriter(filename);
        writer.print("");
        writer.close();
        BufferedOutputStream fOut = new BufferedOutputStream(new FileOutputStream(file));
//        System.out.println(body);
        fOut.write(body.getBytes(StandardCharsets.UTF_8));
        if (exists) {
            sendHeader(client, "204 No Content");
        } else {
            sendHeader(client, "201 Created");
        }
        fOut.flush();
        fOut.close();
    }

    private void doDELETE(Socket client, String filename) throws IOException {
        File file = new File(filename);//Output stream will be in append mode if the file exists, otherwise in the beginning
        boolean exists = file.exists();
        boolean deleted = false;
        if (file.exists() && file.isFile()) {
            deleted = file.delete();
        }
        if(deleted){
            sendHeader(client,"204 No Content");
        }
        else if (!exists){
            sendHeader(client,"404 Not Found");
        }
        else{
            sendHeader(client,"403 Forbidden");
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

    /**
     * Start the application.
     *
     * @param args Command line parameters are not used.
     */
    public static void main(String[] args) {
        WebServer ws = new WebServer();
        ws.start();
    }


    private String createHTMLFile(String parsedString) {
        String[] data = parsedString.split("&");
        String type = data[0].split("=")[1];
        String resultat= "";
        if(type.equals("user")){
            resultat += "<HTML>";
            resultat += "<HEAD> <TITLE> Hello "+ data[1].split("=")[1].replace("+"," ") + "</TITLE> </HEAD>";
            resultat += "<BODY>";
            resultat += "<H1>"+ data[1].split("=")[1]+"</H1>";
            resultat += "<DIV>"+data[2].split("=")[0] + " : "+data[2].split("=")[1].replace("+"," ")+ "</DIV>";
            resultat += "<DIV>"+data[3].split("=")[0] + " : "+data[3].split("=")[1].replace("+"," ")+ "</DIV>";
            resultat += "<DIV>"+data[4].split("=")[0] + " : "+data[4].split("=")[1].replace("+"," ")+ "</DIV>";
            resultat += "</BODY></HTML>";
        }
        return resultat;
    }

    private String createHTMLFileName(String parsedString) {
        String[] data = parsedString.split("&");
        String type = data[0].split("=")[1];
        String resultat= "";
        if(type.equals("user")){
            resultat = AUTHORIZED_USER_DIRECTORY+data[1].split("=")[1].replace("+"," ")+".html";
        }
        return resultat;
    }
}
