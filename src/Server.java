/* ------------------
Server
usage: java Server [RTSP listening port]
---------------------- */

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.awt.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.imageio.ImageIO;
import com.github.sarxos.webcam.Webcam;
import rtp.RtpHandler;
import rtsp.Rtsp;
import rtsp.RtspDemo;
import utils.CustomLoggingHandler;
import video.VideoMetadata;
import video.VideoReader;

public class Server extends JFrame implements ActionListener, ChangeListener {

  InetAddress ClientIPAddr; // Client IP address
  static int startGroupSize = 2;
  RtpHandler rtpHandler;
  RtspDemo rtsp = null;
  static private double lossRate = 0.0;  // Channel errors

  // **************** GUI **************************
  JLabel label;
  static JLabel stateLabel;
  private ButtonGroup encryptionButtons = null;

  // **************** Video variables: ***************
  static int imagenb = 0; // image nb of the image currently transmitted
  VideoReader video; // VideoStream object used to access video frames
  static String VideoDir = "videos/"; // Directory for videos on the server
  public VideoMetadata videoMeta = null;
  static Webcam webcam;
  Timer timer; // timer used to send the images at the video frame rate

  // **************** RTSP variables *****
  // rtsp states
  static final int INIT = 0;
  static final int READY = 1;
  static final int PLAYING = 2;
  // rtsp message types
  static final int SETUP = 3;
  static final int PLAY = 4;
  static final int PAUSE = 5;
  static final int TEARDOWN = 6;
  static final int OPTIONS = 7;
  static final int DESCRIBE = 8;

  static int state; // RTSP Server state == INIT or READY or PLAY
  static Socket RTSPsocket; // socket used to send/receive RTSP messages
  static int RTSPport = 8554; // standard port for RTSP
  // input and output stream filters
  static BufferedReader RTSPBufferedReader;
  static BufferedWriter RTSPBufferedWriter;
  static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

  public Server() {
    super("Server"); // init Frame
    rtpHandler = new RtpHandler(startGroupSize); // init RTP socket and FEC
    // Handler to close the main window
    addWindowListener(
        new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            if (timer != null) timer.stop();
            System.exit(0);
          }
        });

    
    // GUI:
    label = new JLabel("Send frame #        ", JLabel.CENTER);
    stateLabel = new JLabel("State:         ",JLabel.CENTER);
    getContentPane().add(label, BorderLayout.NORTH);
    getContentPane().add(stateLabel, BorderLayout.SOUTH);
    // Error Slider
    JPanel mainPanel = new JPanel();
    mainPanel.setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    JSlider dropRate = new JSlider(JSlider.HORIZONTAL, 0, 100, (int) (lossRate * 100));
    dropRate.addChangeListener(this);
    dropRate.setMajorTickSpacing(10);
    dropRate.setMinorTickSpacing(5);
    dropRate.setPaintTicks(true);
    dropRate.setPaintLabels(true);
    dropRate.setName("p");
    JSlider groupSize = new JSlider(JSlider.HORIZONTAL, 2, 48, startGroupSize);
    groupSize.addChangeListener(this::stateChanged);
    groupSize.setMajorTickSpacing(4);
    groupSize.setMinorTickSpacing(1);
    groupSize.setPaintLabels(true);
    groupSize.setPaintTicks(true);
    groupSize.setName("k");
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 4;
    gbc.weighty = 1;
    gbc.fill = GridBagConstraints.BOTH;
    mainPanel.add(groupSize, gbc);
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.gridwidth = 4;
    gbc.weighty = 1;
    gbc.fill = GridBagConstraints.BOTH;
    mainPanel.add(dropRate, gbc);

    initGuiEncryption(mainPanel);
    getContentPane().add(mainPanel, BorderLayout.CENTER);
  }

  /**
   * Handler for Channel error Slider
   *
   * @param e Change Event
   */
  public void stateChanged(ChangeEvent e) {
    JSlider source = (JSlider) e.getSource();
    if (!source.getValueIsAdjusting()) {
      if (source.getName().equals("k")) {
        int k = source.getValue();
        rtpHandler.setFecGroupSize(k);
        logger.log(Level.INFO, "New Group size: " + k);
      } else {
        lossRate = source.getValue();
        lossRate = lossRate / 100;
        logger.log(Level.INFO, "New packet error rate: " + lossRate);
      }
    }
  }

  /**
   * Handler for encryption RadioButtons.
   * <p>
   * The ItemEvent is just fired if a Button is selected
   * which previous was not.
   *
   * @param ev ItemEvent
   */
  public void radioButtonSelected(ItemEvent ev) {
    JRadioButton rb = (JRadioButton)ev.getItem();
    if (rb.isSelected()) {
      String label = rb.getText();
      RtpHandler.EncryptionMode mode = RtpHandler.EncryptionMode.NONE;

      switch (label) {
      case "SRTP":
        mode = RtpHandler.EncryptionMode.SRTP;
        break;
      case "JPEG":
        mode = RtpHandler.EncryptionMode.JPEG;
        break;
      default:
        break;
      }

      boolean encryptionSet = rtpHandler.setEncryption(mode);
      if (!encryptionSet) {
        Enumeration<AbstractButton> buttons = encryptionButtons.getElements();
        while (buttons.hasMoreElements()) {
          AbstractButton ab = buttons.nextElement();
          if (ab.getText().equals("keine")) {
            ab.setSelected(true);
          }
        }
      }
    }
  }

  // ------------------------------------
  // main
  // ------------------------------------
  public static void main(String[] argv) throws Exception {
    CustomLoggingHandler.prepareLogger(logger);
    /* set logging level
     * Level.CONFIG: default information (incl. RTSP requests)
     * Level.ALL: debugging information (headers, received packages and so on)
     */
    logger.setLevel(Level.FINER);
    //logger.setLevel(Level.ALL);

    printUsage();
    if (argv.length > 0) {
      RTSPport = Integer.parseInt(argv[0]);  // get RTSP socket port from the command line
    }

    if (argv.length > 2) {
      lossRate = Double.parseDouble(argv[1]);
      startGroupSize = Integer.parseInt(argv[2]);
    }

    Server theServer = new Server();  // create a Server object
    theServer.setSize(500, 200);
    theServer.setVisible(true);

    boolean connection = false;
    int request_type = 0;

    while (true) {   // loop to handle RTSP requests
      if (!connection) {
        connectClient(theServer);
        state = INIT;   // Initiate RTSPstate
        stateLabel.setText("INIT");
        connection = true;
      }

      try {
        request_type = theServer.rtsp.parse_RTSP_request(); // parse the request -> blocking
      } catch (IOException e) {
        logger.log(Level.INFO, "Server: Socket closed from client side");
        connection = false;
      }

      if (connection) switch (request_type) {
        case SETUP:
          state = READY;
          stateLabel.setText("READY");
          logger.log(Level.INFO, "New RTSP state: READY");
          // TODO
          if (theServer.videoMeta == null) {
            theServer.videoMeta = VideoMetadata.getVideoMetadata(VideoDir, theServer.rtsp.getVideoFileName() );
          }

          if (theServer.rtsp.getVideoFileName().endsWith("webcam")) {
            webcam = Webcam.getDefault();
            webcam.close();
            webcam.setViewSize(new Dimension(640,480));
            webcam.open();
          } else { // File
            theServer.video = new VideoReader( VideoDir + theServer.rtsp.getVideoFileName() );
          }

          // init Timer
          theServer.timer = new Timer(1000 / theServer.videoMeta.getFramerate(), theServer);
          theServer.timer.setInitialDelay(0);
          theServer.timer.setCoalesce(false); // Coalesce can lead to buffer underflow in client

          theServer.rtsp.send_RTSP_response(SETUP, RTSPsocket.getLocalPort() );
          imagenb = 0;
          break;

        case PLAY:
          if (state == READY) {
            theServer.rtsp.send_RTSP_response(PLAY);
            theServer.timer.start();
            state = PLAYING;
            stateLabel.setText("PLAY");
            logger.log(Level.INFO, "New RTSP state: PLAYING");
          }
          break;

        case PAUSE:
          if (state == PLAYING) {
            theServer.rtsp.send_RTSP_response(PAUSE);
            theServer.timer.stop();  // stop timer
            state = READY;
            stateLabel.setText("READY");
            logger.log(Level.INFO, "New RTSP state: READY");
          }
          break;

        case TEARDOWN:
          state = INIT;
          stateLabel.setText("INIT");
          theServer.rtsp.send_RTSP_response(TEARDOWN);
          theServer.timer.stop();
          theServer.videoMeta = null;
          theServer.rtpHandler.reset();
          if (theServer.rtsp.getVideoFileName().endsWith("webcam")) {
            webcam.close();
          }
          break;

        case OPTIONS:
          logger.log(Level.INFO, "Options request");
          theServer.rtsp.send_RTSP_response(OPTIONS);
          break;

        case DESCRIBE:
          logger.log(Level.INFO, "DESCRIBE Request");
          theServer.videoMeta = VideoMetadata.getVideoMetadata(VideoDir, theServer.rtsp.getVideoFileName() );
          theServer.rtsp.setVideoMeta(theServer.videoMeta);
          logger.log(Level.INFO, "Video Meta: " + theServer.videoMeta.toString());
          theServer.rtsp.send_RTSP_response(DESCRIBE);
          break;

        default:
          logger.log(Level.WARNING, "Wrong request");
      }
    }
  }

  private static void printUsage() {
    System.out.println("usage: java Server [RTSP listening port] [packet loss rate] [FEC group size]");
  }

  private static void connectClient(Server server) throws IOException {
    // Initiate TCP connection with the client for the RTSP session
    ServerSocket listenSocket = new ServerSocket(RTSPport);
    RTSPsocket = listenSocket.accept();
    logger.log(Level.INFO, "Client connected: " + RTSPsocket.getInetAddress());
    listenSocket.close();
    server.ClientIPAddr = RTSPsocket.getInetAddress();   // Get Client IP address

    // Set input and output stream filters:
    RTSPBufferedReader =
        new BufferedReader(new InputStreamReader(RTSPsocket.getInputStream()));
    RTSPBufferedWriter =
        new BufferedWriter(new OutputStreamWriter(RTSPsocket.getOutputStream()));
    server.rtsp = new RtspDemo(RTSPBufferedReader, RTSPBufferedWriter) {
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
    };
  }



  /**
   * Handler for timer, sends the next frame to the client.
   *
   * @param e ActionEvent
   */
  public void actionPerformed(ActionEvent e) {
    imagenb++; // image counter
    byte[] jpegFrame = null;

    try {
      if (rtsp.getVideoFileName().endsWith("webcam")) {
        BufferedImage image = webcam.getImage();
        jpegFrame = toByteArray(image, "jpg");  // convert BufferedImage to byte[]
      } else {
        jpegFrame = video.readNextImage();  // get next frame from file
      }
    } catch (Exception ex) {
      logger.log(Level.SEVERE, "Exception caught: " + ex);
      ex.printStackTrace();
    }

    if (jpegFrame != null) {
      logger.log(Level.FINE, "image nr.:" + imagenb + " size: " + jpegFrame.length);
      label.setText("Send frame #" + imagenb);   // update GUI

      rtpHandler.sendJpeg(jpegFrame, videoMeta.getFramerate(), ClientIPAddr,
          rtsp.getRTP_dest_port(), lossRate);
    } else {
      timer.stop();
    }
  }


  private void initGuiEncryption(JPanel panel) {
    GridBagConstraints gbc = new GridBagConstraints();
    JLabel encryptionLabel = new JLabel("Verschlüsselung:");
    gbc.gridx = 0;
    gbc.gridy = 2;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(0, 10, 0, 0);
    panel.add(encryptionLabel, gbc);

    encryptionButtons = new ButtonGroup();
    JRadioButton e_none = new JRadioButton("keine");
    e_none.addItemListener(this::radioButtonSelected);
    encryptionButtons.add(e_none);
    e_none.setSelected(true);
    gbc = new GridBagConstraints();
    gbc.gridx = 1;
    gbc.gridy = 2;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(e_none, gbc);

    JRadioButton e_srtp = new JRadioButton("SRTP");
    e_srtp.addItemListener(this::radioButtonSelected);
    encryptionButtons.add(e_srtp);
    gbc = new GridBagConstraints();
    gbc.gridx = 2;
    gbc.gridy = 2;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(e_srtp, gbc);

    JRadioButton e_jpeg = new JRadioButton("JPEG");
    e_jpeg.addItemListener(this::radioButtonSelected);
    encryptionButtons.add(e_jpeg);
    gbc = new GridBagConstraints();
    gbc.gridx = 3;
    gbc.gridy = 2;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(e_jpeg, gbc);
  }


  // convert BufferedImage to byte[]
  public static byte[] toByteArray(BufferedImage bi, String format) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(bi, format, baos);
    return baos.toByteArray();
  }
}
