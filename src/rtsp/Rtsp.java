package rtsp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import video.VideoMetadata;

// https://github.com/tyazid/RTSP-Java-UrlConnection

public class Rtsp extends RtspDemo {
    static final String CRLF = "\r\n";  // Line-Ending for Internet Protocols
    URI url;
    Socket RTSPsocket; // socket used to send/receive RTSP messages

    public Rtsp(URI url, int rtpRcvPort) {
        super(url, rtpRcvPort);
        this.url = url;

    }

    public void setUrl(String url) {
        try {
            this.url = new URI(url);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    int RTP_RCV_PORT;         // port where the client will receive the RTP packets
    BufferedWriter RTSPBufferedWriter;  // TCP-Stream for RTSP-Requests
    BufferedReader RTSPBufferedReader; // TCP-Stream for RTSP-Responses
    int RTSPSeqNb = 0;    // RTSP sequence number
    String RTSPid = "0";  // RTSP session number (given by the RTSP Server), 0: not initialized

    public int getFramerate() {
        return framerate != 0 ? framerate : DEFAULT_FPS;
    }

    int framerate = 0;     // framerate of the video (given by the RTSP Server via SDP)
    final int DEFAULT_FPS = 25; // default framerate if not available

    public double getDuration() {
        return duration != 0.0 ? duration : videoLength;
    }

    double duration = 0.0;  // duration of the video (given by the RTSP Server via SDP)
    final static int videoLength = 112;  // => 2008 frames, Demovideo htw.mjpeg, no metadata in MJPEG

    enum State {INIT, READY, PLAYING}  // RTSP states

    State state;
    static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    String VideoFileName;     // video file requested from the client
    VideoMetadata meta;

    static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video
    String sdpTransportLine = "";
    private int RTP_dest_port; // destination port for RTP packets (given by the RTSP Client)
    private int FEC_dest_port; // destination port for RTP-FEC packets (RTP or RTP+2)
    static final int SETUP = 3;
    static final int PLAY = 4;
    static final int PAUSE = 5;
    static final int TEARDOWN = 6;
    static final int OPTIONS = 7;
    static final int DESCRIBE = 8;
    static int RTSP_ID = 123456; // ID of the RTSP session





    public boolean setup() {
        if (state != State.INIT) {
            logger.log(Level.WARNING, "RTSP state: " + state);
            return false;
        }
        RTSPSeqNb++;
        send_RTSP_request("SETUP");
        if (parse_server_response() != 200) {
            logger.log(Level.WARNING, "Invalid Server Response");
            return false;
        } else {
            state = State.READY;
            logger.log(Level.INFO, "New RTSP state: READY\n");
            return true;
        }
    }

    @Override
    public boolean play() {
        if (state != State.READY) {
            logger.log(Level.WARNING, "Cannot play, RTSP state: " + state);
            return false;
        }
        RTSPSeqNb++;
        send_RTSP_request("PLAY");
        if (parse_server_response() != 200) {
            return false;
        } else {
            state = State.PLAYING;
            logger.log(Level.INFO, "RTSP state: PLAYING");
            return true;
        }
    }

    @Override
    public boolean pause() {
        if (state != State.PLAYING) {
            logger.log(Level.WARNING, "Cannot pause, RTSP state: " + state);
            return false;
        }
        RTSPSeqNb++;
        send_RTSP_request("PAUSE");
        if (parse_server_response() != 200) {
            return false;
        } else {
            state = State.READY;
            logger.log(Level.INFO, "RTSP state: READY");
            return true;
        }
    }

    @Override
    public boolean teardown() {
        RTSPSeqNb++;
        send_RTSP_request("TEARDOWN");
        if (parse_server_response() != 200) {
            return false;
        } else {
            state = State.INIT;
            logger.log(Level.INFO, "RTSP state: INIT");
            return true;
        }
    }

    @Override
    public void describe() {
        RTSPSeqNb++;
        send_RTSP_request("DESCRIBE");
        if (parse_server_response() != 200) {
            logger.log(Level.WARNING, "Failed to describe");
        }
    }

    @Override
    public void options() {
        RTSPSeqNb++;
        send_RTSP_request("OPTIONS");
        if (parse_server_response() != 200) {
            logger.log(Level.WARNING, "Failed to process options");
        }
    }

    @Override
    public void send_RTSP_request(String request_type) {
        try {
            RTSPBufferedWriter.write(request_type + " " + url + " RTSP/1.0" + CRLF);
            RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
            RTSPBufferedWriter.write("Session: " + RTSPid + CRLF);
            RTSPBufferedWriter.flush();
            logger.log(Level.INFO, "Sent RTSP Request: " + request_type);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error sending RTSP request: " + e.getMessage());
        }
    }

    public int parse_server_response() {
        int reply_code = 0;
        try {
            String line;
            ArrayList<String> respLines = new ArrayList<>();
            while (!(line = RTSPBufferedReader.readLine()).isEmpty()) {
                respLines.add(line);
                logger.log(Level.CONFIG, line);
            }

            StringTokenizer tokens = new StringTokenizer(respLines.get(0));
            tokens.nextToken(); // Skip RTSP version
            reply_code = Integer.parseInt(tokens.nextToken());

            for (String headerLine : respLines) {
                if (headerLine.toLowerCase().startsWith("session:")) {
                    RTSPid = headerLine.split(":")[1].trim();
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error reading server response: " + e.getMessage());
        }
        return reply_code;
    }

    public String getOptions() {
        return "Public: DESCRIBE, SETUP, TEARDOWN, PLAY, PAUSE" + CRLF;
    }

    public String getDescribe(VideoMetadata meta, int RTP_dest_port) {
        StringWriter rtspHeader = new StringWriter();
        StringWriter rtspBody = new StringWriter();
        rtspBody.write("v=0" + CRLF);
        rtspBody.write("o=- 0 0 IN IP4 127.0.0.1" + CRLF);
        rtspBody.write("s=RTSP-Streaming" + CRLF);
        rtspBody.write("i=Streaming Example" + CRLF);
        rtspBody.write("t=0 0" + CRLF);

        rtspBody.write("m=video " + RTP_dest_port + " RTP/AVP " + MJPEG_TYPE + CRLF);
        rtspBody.write("a=control:trackID=0" + CRLF);
        rtspBody.write("a=rtpmap:" + MJPEG_TYPE + " JPEG/90000" + CRLF);
        rtspBody.write("a=framerate:" + meta.getFramerate() + CRLF);
        rtspBody.write("a=range:npt=0-" + meta.getDuration() + CRLF);

        rtspHeader.write("Content-Type: application/sdp" + CRLF);
        rtspHeader.write("Content-Length: " + rtspBody.toString().length() + CRLF);
        rtspHeader.write(CRLF);

        return rtspHeader + rtspBody.toString();
    }
}