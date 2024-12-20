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
import java.util.ListIterator;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import video.VideoMetadata;

// https://github.com/tyazid/RTSP-Java-UrlConnection

public abstract class  RtspDemo {

   URI url;
  Socket RTSPsocket; // socket used to send/receive RTSP messages

  public void setUrl(String url) {
    try {
      this.url = new URI( url);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  // input and output streeam filters
  public static String rtspServer;
  public static int rtspPort;
  public static String rtspUrl;
  static String VideoFileName; // video file to request to the server
  int RTP_RCV_PORT;         // port where the client will receive the RTP packets
  BufferedWriter RTSPBufferedWriter;  // TCP-Stream for RTSP-Requests
  BufferedReader RTSPBufferedReader; // TCP-Stream for RTSP-Responses
  int RTSPSeqNb = 0;    // RTSP sequence number
  String RTSPid = "0";  // RTSP session number (given by the RTSP Server), 0: not initialized
  static final String CRLF = "\r\n";
  static final String nl = System.getProperty("line.separator");


  //  Video constants:
  // ___________
 static final int FRAME_RATE = 40;
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



  /*  *********************** Server variables  **************************** */
  /**
   * Get the video file name
   * @return filename without path, e.g. htw.mjpeg
   */
//  public String getVideoFileName() {    return VideoFileName;  }
//  String VideoFileName;     // video file requested from the client

  public void setVideoMeta(VideoMetadata meta) {
    this.meta = meta;
  }

  VideoMetadata meta;

  static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video
  //static String VideoDir = "videos/"; // Directory for videos on the server
  String sdpTransportLine = "";
  public int getRTP_dest_port() {
    return RTP_dest_port;
  }
  private int RTP_dest_port; // destination port for RTP packets  (given by the RTSP Client)
  public int getFEC_dest_port() {
    return FEC_dest_port;
  }
  private int FEC_dest_port; // destination port for RTP-FEC packets  (RTP or RTP+2)
  // rtsp states
  static final int INIT = 0;
  static final int READY = 1;
  static final int PLAYING = 2;
  static final int SETUP = 3;
  static final int PLAY = 4;
  static final int PAUSE = 5;
  static final int TEARDOWN = 6;
  static final int OPTIONS = 7;
  static final int DESCRIBE = 8;
  static int RTSP_ID = 123456; // ID of the RTSP session

  /**
   * Constructor for Client
   * @param url  RTSP-URL
   * @param rtpRcvPort Port for RTP-Packets
   */
  public RtspDemo(URI url, int rtpRcvPort) {
    this.url = url;
    this.RTP_RCV_PORT = rtpRcvPort;
    this.state = State.INIT;
    connectServer();
  }


  /**
   * Constructor for Server
   * @param RTSPBufferedReader  Stream for RTSP-Requests
   * @param RTSPBufferedWriter  Stream for RTSP-Responses
   */
  public RtspDemo(BufferedReader RTSPBufferedReader, BufferedWriter RTSPBufferedWriter) {
    this.RTSPBufferedReader = RTSPBufferedReader;
    this.RTSPBufferedWriter = RTSPBufferedWriter;
    this.state = State.INIT;
  }

  /**
   * Connect to the RTSP-Server
   * @return Success or not
   */
  public boolean connectServer () {
    try {
      RTSPBufferedReader =
          new BufferedReader(new InputStreamReader(RTSPsocket.getInputStream()));
      RTSPBufferedWriter =
          new BufferedWriter(new OutputStreamWriter(RTSPsocket.getOutputStream()));

    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return true;
  }


  // Button handler for SETUP, PLAY, PAUSE, TEARDOWN, OPTIONS, DESCRIBE

  /**
   * Button handler for SETUP button
   * @return Success or not
   */
   public boolean setup() {
    // request is only valid if client is in correct state
    if (state != State.INIT) {
      logger.log(Level.WARNING, "RTSP state: " + state);
      return false;
    }
    RTSPSeqNb++;  // increase RTSP sequence number for every RTSP request sent
    send_RTSP_request("SETUP");
    // Wait for the response
    logger.log(Level.INFO, "Wait for response...");
    if (parse_server_response() != 200) {
      logger.log(Level.WARNING, "Invalid Server Response");
      return false;
    } else {
      state = State.READY;
      logger.log(Level.INFO, "New RTSP state: READY\n");
      return true;
    }
  }

    public abstract void getDescribe();

    public abstract boolean play();

  public abstract boolean pause();

  public abstract boolean teardown();

  public abstract void describe();
  public abstract void options();

  /**
   * Sends a RTSP request to the server
   * @param request_type String with request type (e.g. SETUP)
   * write Requests to the RTSPBufferedWriter-Stream
   * use logger.log() for logging the request to the console
   * end request with BufferedWriter.flush()
   * Variables to use:
   *   url: RTSP-URL
   *   RTSPSeqNb: Sequence number (CSeq)
   *   RTSPid: Session number (0 - no active session)
   *   RTP_RCV_PORT:  RTP-Port for receiving the media stream at the client
   *   RTSPBufferWriter: Stream for sending the RTSP-data
   *   logger: logging device
   */
  public abstract void send_RTSP_request(String request_type);


  /**
   * Parse the server response
   * @return Reply code from server
   */
   public int parse_server_response() {
    int reply_code = 0;
    int cl = 0;  // content length

    logger.log(Level.INFO, "Waiting for Server response...");
    try {
      // parse the whole reply
      ArrayList<String> respLines = new ArrayList<>();

      String line;
      do {
        line = RTSPBufferedReader.readLine();
        logger.log(Level.CONFIG, line);
        if (!line.isEmpty()) {
          respLines.add(line);
        }
      } while (!line.isEmpty());
      ListIterator<String> respIter = respLines.listIterator(0);

      StringTokenizer tokens = new StringTokenizer(respIter.next());
      tokens.nextToken(); // skip over the RTSP version
      reply_code = Integer.parseInt(tokens.nextToken());

      while (respIter.hasNext()) {
        line = respIter.next();
        StringTokenizer headerField = new StringTokenizer(line);

        switch (headerField.nextToken().toLowerCase()) {
          case "cseq:":
            logger.log(Level.FINE, "SNr: " + headerField.nextToken());
            break;

          case "session:":
            if (state == State.INIT) {
              RTSPid = headerField.nextToken().split(";")[0]; // cat semicolon
            }
            break;

          case "content-length:":
            cl = Integer.parseInt(headerField.nextToken());
            break;

          case "public:":
            logger.log(Level.INFO, "Options-Response: " + headerField.nextToken());
            break;

          case "content-type:":
            String ct = headerField.nextToken();
            logger.log(Level.INFO, "Content-Type: " + ct);
            break;

          case "transport:":
            logger.log(Level.INFO, "");
            break;

          default:
            logger.log(Level.INFO, "Unknown: " + line);
        }
      }
      logger.log(Level.INFO, "*** Response received ***\n----------------");

      // Describe will send content
      if (cl > 0) {
        parseSDP(cl);
      }

    } catch (Exception ex) {
      ex.printStackTrace();
      logger.log(Level.SEVERE, "Exception caught: " + ex);
      System.exit(0);
    }

    if (reply_code != 200) {
      logger.log(Level.WARNING, "Invalid Server Response");
    }
    return (reply_code);
  }

  /**
   * Parse SDP-Content
    * @param cl Content-Length in bytes
   * sets framerate and duration
   *
   * @throws IOException in case of read error
   */
  void parseSDP(int cl) throws IOException {
    char[] cBuf = new char[cl]; // number of bytes to read
    logger.log(Level.INFO, "*** Parsing Response Data...");
    int data = RTSPBufferedReader.read(cBuf, 0, cl);
    logger.log(Level.INFO, "Data: " + data);
    logger.log(Level.INFO, new String(cBuf));

    framerate = 0;
    duration = 0.0;

    String[] sBuf = new String(cBuf).split(CRLF);  // get lines
    for (String s : sBuf) {
      if (s.contains("framerate")) {
        String sfr = s.split(":")[1];
        framerate = Integer.parseInt(sfr);
        logger.log(Level.INFO, "framerate: " + framerate);
      } else if (s.contains("range:npt")) {
        String[] sdur = s.split("-");
        if (sdur.length > 1) {
          duration = Double.parseDouble(sdur[1]);
          logger.log(Level.INFO, "duration [s]: " + duration);
        } //else duration = videoLength;      // no duration available, set demo video length
      } // else: other attributes are not recognized here
    }
    logger.log(Level.INFO, "Finished Content Reading...");
  }



  /* *********************  RTSP for the Server   *************************************** */

  /** Creates a OPTIONS response string
   * @return  Options string, starting with: Public: ...
   */
  String getOptions() {
    return "Public: DESCRIBE, SETUP, TEARDOWN, PLAY, PAUSE" + CRLF;
  }

  /**
   * Creates a DESCRIBE response string
   * <a href="https://www.ietf.org/rfc/rfc2327.txt">...</a>
   * SDP includes:
   *    o The type of media (video, audio, etc.)
   *    o The transport protocol (RTP/UDP/IP, H.320, etc.)
   *    o The format of the media (H.261 video, MPEG video, etc.)
   *    o maybe Multicast address for media
   *    o maybe Transport Port for media
   *
   * @return String with content
   */
  String getDescribe(VideoMetadata meta, int RTP_dest_port) {
    StringWriter rtspHeader = new StringWriter();
    StringWriter rtspBody = new StringWriter();

    // Write the body first, so we can get the size later

    // Session description
    // v=  (protocol version)
    // o=  (owner/creator and session identifier).
    // s=  (session name)
    rtspBody.write("v=0" + CRLF);
    rtspBody.write("o=- 0 0 IN IP4 0.0.0.0" + CRLF);
    rtspBody.write("s=RTSP-Streaming" + CRLF);
    rtspBody.write("i=" + CRLF);

    // Time description
    // t= (time the session is active)
    rtspBody.write("t=0 0" + CRLF);
    // Stream control
    //

    // Media description
    // m=  (media name and transport address)
    rtspBody.write("m=video ..."  + CRLF);
    rtspBody.write("a=control ..." + CRLF);
    rtspBody.write("a=rtpmap:" + MJPEG_TYPE + " JPEG/90000" + CRLF);
    // rtspBody.write("a=mimetype:string;\"video/mjpeg\"" + CRLF);
    rtspBody.write("a=framerate:" + meta.getFramerate() + CRLF);
    // Audio ist not supported yet
    //rtspBody.write("m=audio " + "0" + " RTP/AVP " + "0" + CRLF);
    //rtspBody.write("a=rtpmap:" + "0" + " PCMU/8000" + CRLF);
    //rtspBody.write("a=control:trackID=" + "1" + CRLF);
    //
    rtspBody.write("a=range:npt=0-");
    if (meta.getDuration() > 0.0) {
      rtspBody.write(Double.toString(meta.getDuration()));
    }
    rtspBody.write(CRLF);

    // rtspHeader.write("Content-Base: " + VideoFileName + CRLF);
    rtspHeader.write("Content-Type: " + "application/sdp" + CRLF);
    rtspHeader.write("Content-Length: " + rtspBody.toString().length() + CRLF);
    rtspHeader.write(CRLF);

    return rtspHeader + rtspBody.toString();
  }





  /**
   * Parse RTSP-Request
   *
   * @return RTSP-Request Type (SETUP, PLAY, etc.)
   */
  public int parse_RTSP_request() throws IOException {
    int request_type = -1;
    logger.log(Level.INFO, "*** wait for RTSP-Request ***");
    String RequestLine = RTSPBufferedReader.readLine();
    if (RequestLine == null) {   // null in case of closed socket (EOF)
      logger.log(Level.WARNING, "RTSP-Request is null");
      throw new IOException("Socket closed by client");
    }
    logger.log(Level.CONFIG, "RTSP: Client-Request:" + RequestLine);

    StringTokenizer tokens = new StringTokenizer(RequestLine);
    String request_type_string = tokens.nextToken();

    // convert to request_type structure:
    request_type = switch ((request_type_string)) {
      case "SETUP" -> SETUP;
      case "PLAY" -> PLAY;
      case "PAUSE" -> PAUSE;
      case "TEARDOWN" -> TEARDOWN;
      case "OPTIONS" -> OPTIONS;
      case "DESCRIBE" -> DESCRIBE;
      default -> request_type;
    };

    if (request_type == SETUP || request_type == DESCRIBE) {
      String dir = tokens.nextToken(); // extract VideoFileName from RequestLine
      //String[] tok = dir.split(".+?/(?=[^/]+$)");
      String[] tok = dir.split("/");
      VideoFileName = tok[3];
      logger.log(Level.CONFIG, "File: " + VideoFileName);
    }

    String line;
    line = RTSPBufferedReader.readLine();
    while (!line.isEmpty()) {
      logger.log(Level.FINE, line);
      if (line.contains("CSeq")) {
        tokens = new StringTokenizer(line);
        tokens.nextToken();
        RTSPSeqNb = Integer.parseInt(tokens.nextToken());
      } else if (line.contains("Transport")) {
        sdpTransportLine = line;
        RTP_dest_port = Integer.parseInt(line.split("=")[1].split("-")[0]);
        FEC_dest_port = RTP_dest_port + 0;
        logger.log(Level.FINE, "Client-Port: " + RTP_dest_port);
      }
      // else is any other field, not checking for now

      line = RTSPBufferedReader.readLine();
    }
    logger.log(Level.INFO, "*** Request received ***\n");
    return (request_type);
  }

  /**
   * Send RTSP Response
   *
   * @param method RTSP-Method
   */
   public void send_RTSP_response(int method, int... localPort) {
    logger.log(Level.INFO, "*** send RTSP-Response ***");
    try {
      RTSPBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
      RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);

      // 3th line depends on Request
      switch (method) {
        case OPTIONS:
          RTSPBufferedWriter.write( getOptions() );
          break;
        case DESCRIBE:
          //VideoMetadata meta = Server.getVideoMetadata(VideoFileName);
          logger.log(Level.INFO, "SDP: " + getDescribe(meta, RTP_dest_port));
          RTSPBufferedWriter.write( getDescribe(meta, RTP_dest_port ));
          break;
        case SETUP:
          RTSPBufferedWriter.write(sdpTransportLine + ";server_port=");
          RTSPBufferedWriter.write(localPort[0] + "-");
          RTSPBufferedWriter.write((localPort[0]+1) + CRLF);
          // RTSPBufferedWriter.write(";ssrc=0;mode=play" + CRLF);
        default:
          RTSPBufferedWriter.write("Session: " + RTSP_ID + ";timeout=30000" + CRLF);
          break;
      }

      // Send end of response
      if (method != DESCRIBE) RTSPBufferedWriter.write(CRLF);
      RTSPBufferedWriter.flush();
      logger.log(Level.FINE, "*** RTSP-Server - Sent response to Client ***");

    } catch (Exception ex) {
      ex.printStackTrace();
      logger.log(Level.SEVERE, "Exception caught: " + ex);
      System.exit(0);
    }
  }

}
