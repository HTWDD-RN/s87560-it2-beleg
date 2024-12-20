
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import rtp.RtpHandler;

public class ClientView  {
  JFrame f = new JFrame("Client");
  JButton setupButton = new JButton("Setup");
  JButton playButton = new JButton("Play");
  JButton pauseButton = new JButton("Pause");
  JButton tearButton = new JButton("Teardown");
  JButton optionsButton = new JButton("Options");
  JButton describeButton = new JButton("Describe");
  JButton connectButton = new JButton("Connect");
  JPanel mainPanel = new JPanel(); // Container
  JPanel buttonPanel = new JPanel(); // Buttons
  JPanel statsPanel = new JPanel();
  JPanel inputPanel = new JPanel();
  JPanel pufferPanel = new JPanel();
  JPanel fecPanel = new JPanel();
  JLabel iconLabel = new JLabel(); // Image
  JLabel statusLabel = new JLabel("Status: "); // Statistics
  JLabel pufferLabel = new JLabel("Puffer: "); // Statistics
  JLabel receiveLabel = new JLabel("Receive: "); // Statistics
  JLabel receiveLabel2 = new JLabel("Receive2: "); // Statistics
  JLabel statsLabel = new JLabel("Statistics: "); // Statistics
  JLabel statsLabel2 = new JLabel("Statistics2: "); // Statistics
  JLabel fecLabel = new JLabel("FEC: "); // Statistics
  JLabel fecLabel2 = new JLabel("FEC2: "); // Statistics
  JTextField textField = new JTextField("URL", 30);
  JTextField pufferNumber = new JTextField(3);
  JProgressBar progressBuffer = new JProgressBar(0, 50);
  JProgressBar progressPosition = new JProgressBar();
  JCheckBox checkBoxFec = new JCheckBox("nutze FEC");
  JCheckBox checkBoxEco = new JCheckBox("nutze ECO");
  ButtonGroup encryptionButtons = null;

  Client.setupButtonListener client;
  RtpHandler rtpHandler;

  public void setRtpHandler(RtpHandler rtpHandler) {
    this.rtpHandler = rtpHandler;
  }
  public ClientView(Client.setupButtonListener client) {
     this.client = client;
    initView();
  }
  private void initView (){
    f.addWindowListener(
        new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            System.exit(0);
          }
        });
    // ***************** Buttons ***************
    buttonPanel.setLayout(new GridLayout(1, 0));
    buttonPanel.add(connectButton);
    buttonPanel.add(optionsButton);
    buttonPanel.add(describeButton);
    buttonPanel.add(setupButton);
    buttonPanel.add(playButton);
    buttonPanel.add(pauseButton);
    buttonPanel.add(tearButton);
    setupButton.addActionListener( client);
    playButton.addActionListener( client);
    pauseButton.addActionListener( client);
    tearButton.addActionListener( client);
    optionsButton.addActionListener( client);
    describeButton.addActionListener( client);
    iconLabel.setIcon(null);     // Image display label

    // ***************** Statistic Panel ***************
    statsPanel.setLayout(new GridLayout(5, 2));
    statsPanel.add(statusLabel);
    statsPanel.add(pufferLabel);
    statsPanel.add(receiveLabel);
    statsPanel.add(receiveLabel2);
    statsPanel.add(statsLabel);
    statsPanel.add(statsLabel2);
    statsPanel.add(fecLabel);
    statsPanel.add(fecLabel2);
    fecPanel.add(checkBoxFec);
    fecPanel.add(checkBoxEco);
    //statsPanel.add(checkBoxFec);
    statsPanel.add(fecPanel);
    pufferPanel.add(new JLabel("Puffergröße: "));
    pufferPanel.add(pufferNumber);
    statsPanel.add(pufferPanel);

    inputPanel.setLayout(new BorderLayout());
    inputPanel.add(textField, BorderLayout.SOUTH);

    JPanel encryptionPanel = initEncryptionPanel();

    // frame layout
    mainPanel.setLayout(null);
    mainPanel.add(iconLabel);
    mainPanel.add(buttonPanel);
    mainPanel.add(encryptionPanel);
    mainPanel.add(statsPanel);      // Statistics
    mainPanel.add(progressBuffer);
    mainPanel.add(progressPosition);
    mainPanel.add(inputPanel);
    iconLabel.setBounds(0, 0, 640, 480);
    buttonPanel.setBounds(0, 480, 640, 50);
    encryptionPanel.setBounds(10, 530, 640, 30);
    statsPanel.setBounds(10, 560, 620, 160);
    progressBuffer.setBounds(10, 720, 620, 20);
    progressPosition.setBounds(10, 750, 620, 20);
    inputPanel.setBounds(10, 770, 620, 30);
    // inputPanel.setSize(620,50);

    pufferNumber.setToolTipText("Puffergröße");

    f.getContentPane().add(mainPanel, BorderLayout.CENTER);
    f.setSize(new Dimension(640, 860));
    f.setVisible(true);

    mainPanel.getRootPane().setDefaultButton(describeButton);
    describeButton.requestFocus();
  }


  void setPufferNumber(int puffer) {
    pufferNumber.setText(Integer.toString(puffer));
  }


  private JPanel initEncryptionPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridLayout(1, 0));

    JLabel encryptionLabel = new JLabel("Verschlüsselung:");
    panel.add(encryptionLabel);

    encryptionButtons = new ButtonGroup();
    JRadioButton e_none = new JRadioButton("keine");
    // TODO Handler für RadioButtons prüfen
    //e_none.addItemListener(this::radioButtonSelected);
    e_none.addActionListener( client );
    encryptionButtons.add(e_none);
    e_none.setSelected(true);
    panel.add(e_none);

    JRadioButton e_srtp = new JRadioButton("SRTP");
    e_srtp.addActionListener(client);
    encryptionButtons.add(e_srtp);
    panel.add(e_srtp);

    JRadioButton e_jpeg = new JRadioButton("JPEG");
    e_jpeg.addActionListener(client);
    encryptionButtons.add(e_jpeg);
    panel.add(e_jpeg);

    JRadioButton a_jpeg = new JRadioButton("JPEG (Angriff)");
    a_jpeg.addActionListener(client );
    encryptionButtons.add(a_jpeg);
    panel.add(a_jpeg);

    return panel;
  }

}
