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

    public HandleRequest(){}


    public void doPOST(BufferedInputStream in, Socket client) throws IOException {
        File file = new File("copyFile");

        String request = getRequest(in, file);

        boolean res = file.delete();
        if(res){
            Map<String, String> map = buildRequest(request);
            if(map.containsKey("request")){
                switch (map.get("request")){
                    case "CreateUser" :
                        String result = createUser(map);
                        String filename = AUTHORIZED_USER_DIRECTORY+map.get("user")+".html";
                        PrintWriter out = new PrintWriter(filename);
                        out.println(result);
                        out.close();
                        sendContentResponse(client, "200 OK", "text/html", filename, filename.length());
                        break;
                    case "Adder" :
                        result = doAdder(map);
                        sendContentResponse(client, "200 OK", "text/html", result, result.length());
                        break;
                    default :
                        sendHeader(client, "406 Not Acceptable");
                        break;
                }
            } else {
                sendHeader(client, "406 Not Acceptable");
            }
        }

    }


    public void doGET(BufferedInputStream in, Socket client) throws IOException {
        sendHeader(client, "405 Method Not Allowed");

    }

    public Map<String, String> buildRequest(String request){
        Map<String, String> map = new HashMap<String, String>();
        for(String parameter : request.split("&")){
            if(parameter.split("=").length==1){
                map.put(parameter.split("=")[0], "");
            } else {
                String id = parameter.split("=")[0];
                String value = parameter.split("=")[1];
                map.put(id, value);
            }
        }
        return map;
    }


    public String getRequest(BufferedInputStream in, File file) throws IOException {
        String result ="";
        boolean appendMode = file.exists();
        //Output stream will be in append mode if the file exists, otherwise in the beginning
        BufferedOutputStream fOut = new BufferedOutputStream(new FileOutputStream(file, appendMode));
        byte[] buffer = new byte[256];
        while (in.available() > 0) {
            int nbRead = in.read(buffer);
            fOut.write(buffer, 0, nbRead);
        }


        fOut.flush();
        fOut.close();

        BufferedReader brTest = new BufferedReader(new FileReader("copyFile"));
        String request = brTest.readLine();
        brTest.close();
        result = request;
        return result;
    }


    private static void sendHeader(Socket client, String status) throws IOException {
        OutputStream clientOutput = client.getOutputStream();
        clientOutput.write(("HTTP/1.1 " + status + "\r\n").getBytes());
        clientOutput.write("\r\n".getBytes());
        clientOutput.flush();
        clientOutput.close();
    }

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



    private String createUser(Map<String, String> map) {
        String resultat="";
        resultat += "<HTML>";
        resultat += "<HEAD> <TITLE> Hello "+ map.get("user") + "</TITLE> </HEAD>";
        resultat += "<BODY>";
        resultat += "<H1>"+ map.get("user")+"</H1>";
        resultat += "<DIV> Phone : "+map.get("phone")+ "</DIV>";
        resultat += "<DIV> Email : "+map.get("mail")+ "</DIV>";
        resultat += "<DIV> Password : "+map.get("password")+ "</DIV>";
        resultat += "</BODY></HTML>";

        return resultat;
    }


    private String doAdder(Map<String, String> map) {
        String res = "";
        try {
            double result = Double.parseDouble(map.get("number1"))+ Double.parseDouble(map.get("number2"));
            res = Double.toString(result);
        } catch (Exception e) {
            res = "Wrong format";
        }
        return res;
    }
}
