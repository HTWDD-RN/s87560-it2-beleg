package rtsp;

import java.io.*;
import java.net.Socket;
import java.net.URI;

// https://github.com/tyazid/RTSP-Java-UrlConnection

public class Rtsp extends RtspDemo {
    // Serverinformationen (z.B. Hostname und Port)
    private String serverHost = "localhost";
    private int serverPort = 8554;

    // RTSP-spezifische Attribute
    private int cSeq = 0; // Sequenznummer für RTSP-Requests
    private Socket rtspSocket;
    private PrintWriter rtspWriter;
    private BufferedReader rtspReader;

    public Rtsp(URI url, int rtpRcvPort) {
        super(url, rtpRcvPort);
    }

    @Override
    public void getDescribe() {
        try {
            // Aufbau der Verbindung, falls nicht bereits verbunden
            if (rtspSocket == null || rtspSocket.isClosed()) {
                rtspSocket = new Socket(serverHost, serverPort);
                rtspWriter = new PrintWriter(rtspSocket.getOutputStream(), true);
                rtspReader = new BufferedReader(new InputStreamReader(rtspSocket.getInputStream()));
            }

            // RTSP DESCRIBE-Anfrage erstellen
            String rtspRequest = "DESCRIBE rtsp://example.com/sample.sdp RTSP/1.0\r\n" +
                    "CSeq: " + (++cSeq) + "\r\n" +
                    "Accept: application/sdp\r\n\r\n";

            // Anfrage an den Server senden
            rtspWriter.print(rtspRequest);
            rtspWriter.flush();

            System.out.println("RTSP DESCRIBE request sent:\n" + rtspRequest);

            // Serverantwort lesen und in der Konsole ausgeben
            String responseLine;
            System.out.println("Server response:");
            while ((responseLine = rtspReader.readLine()) != null) {
                System.out.println(responseLine);
                // Beenden der Ausgabe, sobald die Antwort vollständig gelesen wurde
                if (responseLine.trim().isEmpty()) {
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("Error during DESCRIBE request: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean play() {
        return false;
    }

    @Override
    public boolean pause() {
        return false;
    }

    @Override
    public boolean teardown() {
        return false;
    }

    @Override
    public void describe() {

    }

    @Override
    public void options() {

    }

    @Override
    public void send_RTSP_request(String request_type) {

    }
}





