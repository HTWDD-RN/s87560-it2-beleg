/* ------------------
Client
usage: java Client [Server hostname] [Server RTSP listening port] [Video file requested]


---------------------- */

/* ------------------
Client
usage: java Client [Server hostname] [Server RTSP listening port] [Video file requested]


---------------------- */

import java.awt.image.BufferedImage;
import java.net.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;
import rtp.ReceptionStatistic;
import rtp.RtpHandler;
import rtsp.Rtsp;
import utils.CustomLoggingHandler;
import java.awt.Image;

public class Client  {
  int iteration = 0;  // for displaying statistics
  Timer timerPlay; // timer used to display the frames at the correct frame rate
  ClientView view;
  // ******************** RTP variables: *****************************
  private static RtpHandler rtpHandler;
  static int RTP_RCV_PORT = 25000; // port where the client will receive the RTP packets
  // static int FEC_RCV_PORT = 25002; // port where the client will receive the RTP packets
  final static Integer JITTER_BUFFER_SIZE = 10; // size of the buffer in frames

  // ********************** RTSP variables ************************
  private static Rtsp rtsp;
  private static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);


  public Client() {
    view = new ClientView( new ButtonListener()  );
  }

  /**
   * Initialization of the GUI
   *
   * @param argv host port file
   * @throws Exception stacktrace at console
   */
  public static void main(String[] argv) throws Exception {
    CustomLoggingHandler.prepareLogger(logger);
    /* set logging level
     * Level.CONFIG: default information (incl. RTSP requests)
     * Level.ALL: debugging information (headers, received packages and so on)
     */
    logger.setLevel(Level.FINER);

    URI url = new URI(argv[0]);
    rtpHandler = new RtpHandler(false);       // init RTP handler

    Client theClient = new Client();  // Create a Client object

    theClient.view.textField.setText(url.toString());
    theClient.view.pufferNumber.setText(JITTER_BUFFER_SIZE.toString());
    theClient.view.setRtpHandler(rtpHandler);

    rtsp = new Rtsp(url, RTP_RCV_PORT);
  }


  public class ButtonListener implements ActionListener {

    public void actionPerformed(ActionEvent e) {
      logger.log(Level.INFO, "Button: " + e.getActionCommand());

      String cmd = e.getActionCommand();

      if (cmd.contains("SRTP") || cmd.contains("JPEG") || cmd.contains("keine") ) {
        setEncrytionMode(cmd);
      }

      switch (cmd) { // depending on the pressed button
        case "Setup":
          rtsp.setUrl(view.textField.getText());

          if (rtsp.setup()) {
            // set jitter buffer size
            rtpHandler.setJitterBufferStartSize(Integer.parseInt(view.pufferNumber.getText()));
            rtpHandler.startReceiver(RTP_RCV_PORT);
            // TODO
            rtpHandler.setFecDecryptionEnabled(view.checkBoxFec.isSelected());
            // Init the play timer
            int timerDelay = 1000 / rtsp.getFramerate(); // delay in ms, default 40 ms for 25 fps
            timerPlay = new Timer(timerDelay, new timerPlayListener());
            timerPlay.setCoalesce(true); // combines events
            view.statusLabel.setText("READY");
            view.mainPanel.getRootPane().setDefaultButton(view.playButton);
            view.playButton.requestFocus();
          }
          break;
        case "Play":
          if (rtsp.play()) {
            view.statusLabel.setText("PLAY ");
            view.mainPanel.getRootPane().setDefaultButton(view.pauseButton);
            view.pauseButton.requestFocus();
            timerPlay.start();
          }
          break;
        case "Pause":
          if (rtsp.pause()) {
            view.statusLabel.setText("READY ");
            view.mainPanel.getRootPane().setDefaultButton(view.playButton);
            view.playButton.requestFocus();
            timerPlay.stop();
            timerPlay.setInitialDelay(0);
          }
          break;
        case "Teardown":
          if (rtsp.teardown()) {
            view.statusLabel.setText("INIT ");
            view.progressPosition.setValue(0);
            rtpHandler.reset();
            rtpHandler.stopReceiver();
            timerPlay.stop();
            //TODO RTPs löschen für Statistik
          }
          break;
        case "Options":
          rtsp.options();
          break;
        case "Describe":
          rtsp.setUrl(view.textField.getText());
          rtsp.describe();
          // set progress bar from duration and framerate from server data
          view.progressPosition.setMaximum((int) rtsp.getDuration() * rtsp.getFramerate());
          view.mainPanel.getRootPane().setDefaultButton(view.setupButton);
          view.setupButton.requestFocus();
          break;
      }
    }
  }



  /** Displays one frame if available */
  class timerPlayListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      ReceptionStatistic rs = rtpHandler.getReceptionStatistic();

      // update progress bar + jitter buffer size
      view.progressBuffer.setValue(rs.jitterBufferSize);
      view.progressPosition.setValue(rs.requestedFrames);  // Abspielbilder
      if (iteration % 5 == 0) {
        setStatistics(rs);
        iteration = 0;
      }
      iteration++;


      // check for end of display JPEGs
      if (rs.jitterBufferSize <= 0) { // buffer empty -> finish
        view.statusLabel.setText("End of Stream");
        return;
      }

      logger.log(Level.FINER, "----------------- Play timer --------------------");
      BufferedImage bi = jpegDisplay.nextPlaybackImage( rtpHandler, rs, view.checkBoxEco.isSelected());
      if (bi != null)  view.iconLabel.setIcon( new ImageIcon(bi) );
    }


    private void setStatistics(ReceptionStatistic rs) {
      DecimalFormat df = new DecimalFormat("0.000");
      view.pufferLabel.setText("Puffer (JPEG / RTP): " + rs.jitterBufferSize + " / " + (rs.latestSequenceNumber - rs.playbackIndex));

      view.receiveLabel.setText("empfangenes RTP mit SeqNr: " + rs.latestSequenceNumber);
      view.receiveLabel2.setText("Summe empfangene RTPs: " + rs.receivedPackets);

      view.statsLabel.setText("RTP: Index / verloren: "
              + rs.playbackIndex + " / " + rs.packetsLost);

      // <p> is for new line
      view.statsLabel2.setText("<html>Bilder (Nr. / frag. / fehl.): "
              +rs.requestedFrames + " / " + rs.framesPartLost + " / " + rs.framesLost  + "</html>");

      view.fecLabel.setText("FEC: korrigiert / nicht korrigiert: "
              + rs.correctedPackets
              + " / "
              + rs.notCorrectedPackets);

      // latestSequenceNumber is the last received RTP packet
      view.fecLabel2.setText("Ratio vor FEC: "
              + (df.format((double) rs.packetsLost / (double) rs.playbackIndex))
              + "    nach FEC: "
              + (df.format((double) rs.notCorrectedPackets / (double) rs.playbackIndex)));
    }
  }


  void setEncrytionMode(String label) {
    RtpHandler.EncryptionMode mode = RtpHandler.EncryptionMode.NONE;

    switch (label) {
      case "SRTP":
        mode = RtpHandler.EncryptionMode.SRTP;
        break;
      case "JPEG":
        mode = RtpHandler.EncryptionMode.JPEG;
        break;
      case "JPEG (Angriff)":
        mode = RtpHandler.EncryptionMode.JPEG_ATTACK;
        break;
      default:
        break;
    }

    boolean encryptionSet = rtpHandler.setEncryption(mode);
    if (!encryptionSet) {
      Enumeration<AbstractButton> buttons = view.encryptionButtons.getElements();
      while (buttons.hasMoreElements()) {
        AbstractButton ab = buttons.nextElement();
        if (ab.getText().equals("keine")) {
          ab.setSelected(true);
        }
      }
    }
  }

}