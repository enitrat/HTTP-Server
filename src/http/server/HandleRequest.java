package http.server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

public class HandleRequest {

    private static final String AUTHORIZED_USER_DIRECTORY = "doc/users/";

    public HandleRequest() {
    }

    /**
     * Handles a post request directed to /HandleRequest
     * Our WebClients allows 2 different actions: CreateUser and Adder.
     * Depending on the action, the server acts differently
     * @param in
     * @param client
     * @throws IOException
     */
    public void doPOST(BufferedInputStream in, Socket client) throws IOException {
        String request = getRequest(in);
        Map<String, String> map = buildRequest(request);
        if (map.containsKey("request")) {
            switch (map.get("request")) {
                case "CreateUser":
                    String result = createUser(map);
                    String filename = AUTHORIZED_USER_DIRECTORY + map.get("user") + ".html";
                    PrintWriter out = new PrintWriter(filename);
                    out.println(result);
                    out.close();
                    sendContentResponse(client, "200 OK", "text/html", filename, filename.length());
                    break;
                case "Adder":
                    result = doAdder(map);
                    sendContentResponse(client, "200 OK", "text/html", result, result.length());
                    break;
                default:
                    sendHeader(client, "406 Not Acceptable");
                    break;
            }
        } else {
            sendHeader(client, "400 Bad Request");
        }
    }

    /**
     * Parses the parameters of the request
     * @param request
     * @return
     */
    public Map<String, String> buildRequest(String request) {
        Map<String, String> map = new HashMap<String, String>();
        for (String parameter : request.split("&")) {
            if (parameter.split("=").length == 1) {
                map.put(parameter.split("=")[0], "");
            } else {
                String id = parameter.split("=")[0];
                String value = parameter.split("=")[1];
                map.put(id, value);
            }
        }
        return map;
    }


    /**
     * Reads the body of a request and stores it into a String
     *
     * @param in
     * @return String, request body
     * @throws IOException
     */
    public String getRequest(BufferedInputStream in) throws IOException {
        String result = "";
        //Output stream will be in append mode if the file exists, otherwise in the beginning
        int currentByte = -1;
        while (in.available()>0) {
            result += (char) in.read();
        }
        System.out.println(result);
        return result;
    }

    /**
     * Sends a response with only a header and the response status
     * @param client
     * @param status
     * @throws IOException
     */
    private static void sendHeader(Socket client, String status) throws IOException {
        OutputStream clientOutput = client.getOutputStream();
        clientOutput.write(("HTTP/1.1 " + status + "\r\n").getBytes());
        clientOutput.write("\r\n".getBytes());
        clientOutput.flush();
        clientOutput.close();
    }

    /**
     * Sends a response with a header and a body for the GET request
     * @param client
     * @param status
     * @param contentType
     * @param content
     * @throws IOException
     */
    private static void sendContentResponse(Socket client, String status, String contentType, String content, long length) throws IOException {
        OutputStream clientOutput = client.getOutputStream();
        clientOutput.write(("HTTP/1.1 " + status + "\r\n").getBytes());
        clientOutput.write(("Content-Type: " + contentType + "\r\n").getBytes());
        clientOutput.write(("Content-Length: " + length + "\r\n").getBytes());
        clientOutput.write("\r\n".getBytes());
        clientOutput.write(content.getBytes());
        clientOutput.write("\r\n\r\n".getBytes());
        clientOutput.flush();
        clientOutput.close();
    }


    /**
     * Creates an HTML file for a user
     * @param map
     * @return String with the content of the html file
     */
    private String createUser(Map<String, String> map) {
        String resultat = "";
        resultat += "<HTML>";
        resultat += "<HEAD> <TITLE> Hello " + map.get("user") + "</TITLE> </HEAD>";
        resultat += "<BODY>";
        resultat += "<H1>" + map.get("user") + "</H1>";
        resultat += "<DIV> Phone : " + map.get("phone") + "</DIV>";
        resultat += "<DIV> Email : " + map.get("mail") + "</DIV>";
        resultat += "<DIV> Password : " + map.get("password") + "</DIV>";
        resultat += "</BODY></HTML>";

        return resultat;
    }


    /**
     * Realises an adder operation
     * @param map with the numbers to add
     * @return result of the operation
     */
    private String doAdder(Map<String, String> map) {
        String res = "";
        try {
            double result = Double.parseDouble(map.get("number1")) + Double.parseDouble(map.get("number2"));
            res = Double.toString(result);
        } catch (Exception e) {
            res = "Wrong format";
        }
        return res;
    }
}
