package rtp;

import static utils.ByteTasks.bytesToHex;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Kapselt die JPEG-Logik zum Kodieren und Dekodieren der JPEG-Bilder gemäß RFC-2435.
 *
 * @author Elisa Zschorlich (s70342)
 */


/*
   JPEG header
    0                   1                   2                   3
    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   | Type-specific |              Fragment Offset                  |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |      Type     |       Q       |     Width     |     Height    |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

    Restart Marker header
    0                   1                   2                   3
    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |       Restart Interval        |F|L|       Restart Count       |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

    Quantization Table header
    0                   1                   2                   3
    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |      MBZ      |   Precision   |             Length            |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                    Quantization Table Data                    |
   |                              ...                              |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */

public class JpegFrame {
  public static final int PAYLOAD_TYPE_ID = 26;
  public static int MTU = 1300;
  /* JPEG-Marker. */
  public static final byte MARKER_TAG_START = (byte) 0xFF;
  public static final byte[] SOF0_MARKER = new byte[] { MARKER_TAG_START, (byte) 0xC0 };
  public static final byte[] SOI_MARKER = new byte[] { MARKER_TAG_START, (byte) 0xD8 };
  public static final byte[] EOI_MARKER = new byte[] { MARKER_TAG_START, (byte) 0xD9 };
  public static final byte[] SOS_MARKER = new byte[] { MARKER_TAG_START, (byte) 0xDA };
  public static final byte[] DQT_MARKER = new byte[] { MARKER_TAG_START, (byte) 0xDB };
  public static final byte[] DRI_MARKER = new byte[] { MARKER_TAG_START, (byte) 0xDD };

  /* Codelens und Symbol-Tabellen zur BErechnung der Huffmann-Tabellen, entnommen aus RFC-2435. */
  private static byte[] LUM_DC_CODELENS = {
      (byte) 0x00, (byte) 0x01, (byte) 0x05, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01,
      (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 };

  private static byte[] LUM_DC_SYMBOLS = {
      (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
      (byte) 0x08, (byte) 0x09, (byte) 0x0a, (byte) 0x0b };

  private static byte[] LUM_AC_CODELENS = {
      (byte) 0x00, (byte) 0x02, (byte) 0x01, (byte) 0x03, (byte) 0x03, (byte) 0x02, (byte) 0x04, (byte) 0x03,
      (byte) 0x05, (byte) 0x05, (byte) 0x04, (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x7d };

  private static byte[] LUM_AC_SYMBOLS = {
      (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x00, (byte) 0x04, (byte) 0x11, (byte) 0x05, (byte) 0x12,
      (byte) 0x21, (byte) 0x31, (byte) 0x41, (byte) 0x06, (byte) 0x13, (byte) 0x51, (byte) 0x61, (byte) 0x07,
      (byte) 0x22, (byte) 0x71, (byte) 0x14, (byte) 0x32, (byte) 0x81, (byte) 0x91, (byte) 0xa1, (byte) 0x08,
      (byte) 0x23, (byte) 0x42, (byte) 0xb1, (byte) 0xc1, (byte) 0x15, (byte) 0x52, (byte) 0xd1, (byte) 0xf0,
      (byte) 0x24, (byte) 0x33, (byte) 0x62, (byte) 0x72, (byte) 0x82, (byte) 0x09, (byte) 0x0a, (byte) 0x16,
      (byte) 0x17, (byte) 0x18, (byte) 0x19, (byte) 0x1a, (byte) 0x25, (byte) 0x26, (byte) 0x27, (byte) 0x28,
      (byte) 0x29, (byte) 0x2a, (byte) 0x34, (byte) 0x35, (byte) 0x36, (byte) 0x37, (byte) 0x38, (byte) 0x39,
      (byte) 0x3a, (byte) 0x43, (byte) 0x44, (byte) 0x45, (byte) 0x46, (byte) 0x47, (byte) 0x48, (byte) 0x49,
      (byte) 0x4a, (byte) 0x53, (byte) 0x54, (byte) 0x55, (byte) 0x56, (byte) 0x57, (byte) 0x58, (byte) 0x59,
      (byte) 0x5a, (byte) 0x63, (byte) 0x64, (byte) 0x65, (byte) 0x66, (byte) 0x67, (byte) 0x68, (byte) 0x69,
      (byte) 0x6a, (byte) 0x73, (byte) 0x74, (byte) 0x75, (byte) 0x76, (byte) 0x77, (byte) 0x78, (byte) 0x79,
      (byte) 0x7a, (byte) 0x83, (byte) 0x84, (byte) 0x85, (byte) 0x86, (byte) 0x87, (byte) 0x88, (byte) 0x89,
      (byte) 0x8a, (byte) 0x92, (byte) 0x93, (byte) 0x94, (byte) 0x95, (byte) 0x96, (byte) 0x97, (byte) 0x98,
      (byte) 0x99, (byte) 0x9a, (byte) 0xa2, (byte) 0xa3, (byte) 0xa4, (byte) 0xa5, (byte) 0xa6, (byte) 0xa7,
      (byte) 0xa8, (byte) 0xa9, (byte) 0xaa, (byte) 0xb2, (byte) 0xb3, (byte) 0xb4, (byte) 0xb5, (byte) 0xb6,
      (byte) 0xb7, (byte) 0xb8, (byte) 0xb9, (byte) 0xba, (byte) 0xc2, (byte) 0xc3, (byte) 0xc4, (byte) 0xc5,
      (byte) 0xc6, (byte) 0xc7, (byte) 0xc8, (byte) 0xc9, (byte) 0xca, (byte) 0xd2, (byte) 0xd3, (byte) 0xd4,
      (byte) 0xd5, (byte) 0xd6, (byte) 0xd7, (byte) 0xd8, (byte) 0xd9, (byte) 0xda, (byte) 0xe1, (byte) 0xe2,
      (byte) 0xe3, (byte) 0xe4, (byte) 0xe5, (byte) 0xe6, (byte) 0xe7, (byte) 0xe8, (byte) 0xe9, (byte) 0xea,
      (byte) 0xf1, (byte) 0xf2, (byte) 0xf3, (byte) 0xf4, (byte) 0xf5, (byte) 0xf6, (byte) 0xf7, (byte) 0xf8,
      (byte) 0xf9, (byte) 0xfa };

  private static byte[] CHM_DC_CODELENS = {
      (byte) 0x00, (byte) 0x03, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01,
      (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 };

  private static byte[] CHM_DC_SYMBOLS = {
      (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
      (byte) 0x08, (byte) 0x09, (byte) 0x0a, (byte) 0x0b };

  private static byte[] CHM_AC_CODELENS = {
      (byte) 0x00, (byte) 0x02, (byte) 0x01, (byte) 0x02, (byte) 0x04, (byte) 0x04, (byte) 0x03, (byte) 0x04,
      (byte) 0x07, (byte) 0x05, (byte) 0x04, (byte) 0x04, (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x77 };

  private static byte[] CHM_AC_SYMBOLS = {
      (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x11, (byte) 0x04, (byte) 0x05, (byte) 0x21,
      (byte) 0x31, (byte) 0x06, (byte) 0x12, (byte) 0x41, (byte) 0x51, (byte) 0x07, (byte) 0x61, (byte) 0x71,
      (byte) 0x13, (byte) 0x22, (byte) 0x32, (byte) 0x81, (byte) 0x08, (byte) 0x14, (byte) 0x42, (byte) 0x91,
      (byte) 0xa1, (byte) 0xb1, (byte) 0xc1, (byte) 0x09, (byte) 0x23, (byte) 0x33, (byte) 0x52, (byte) 0xf0,
      (byte) 0x15, (byte) 0x62, (byte) 0x72, (byte) 0xd1, (byte) 0x0a, (byte) 0x16, (byte) 0x24, (byte) 0x34,
      (byte) 0xe1, (byte) 0x25, (byte) 0xf1, (byte) 0x17, (byte) 0x18, (byte) 0x19, (byte) 0x1a, (byte) 0x26,
      (byte) 0x27, (byte) 0x28, (byte) 0x29, (byte) 0x2a, (byte) 0x35, (byte) 0x36, (byte) 0x37, (byte) 0x38,
      (byte) 0x39, (byte) 0x3a, (byte) 0x43, (byte) 0x44, (byte) 0x45, (byte) 0x46, (byte) 0x47, (byte) 0x48,
      (byte) 0x49, (byte) 0x4a, (byte) 0x53, (byte) 0x54, (byte) 0x55, (byte) 0x56, (byte) 0x57, (byte) 0x58,
      (byte) 0x59, (byte) 0x5a, (byte) 0x63, (byte) 0x64, (byte) 0x65, (byte) 0x66, (byte) 0x67, (byte) 0x68,
      (byte) 0x69, (byte) 0x6a, (byte) 0x73, (byte) 0x74, (byte) 0x75, (byte) 0x76, (byte) 0x77, (byte) 0x78,
      (byte) 0x79, (byte) 0x7a, (byte) 0x82, (byte) 0x83, (byte) 0x84, (byte) 0x85, (byte) 0x86, (byte) 0x87,
      (byte) 0x88, (byte) 0x89, (byte) 0x8a, (byte) 0x92, (byte) 0x93, (byte) 0x94, (byte) 0x95, (byte) 0x96,
      (byte) 0x97, (byte) 0x98, (byte) 0x99, (byte) 0x9a, (byte) 0xa2, (byte) 0xa3, (byte) 0xa4, (byte) 0xa5,
      (byte) 0xa6, (byte) 0xa7, (byte) 0xa8, (byte) 0xa9, (byte) 0xaa, (byte) 0xb2, (byte) 0xb3, (byte) 0xb4,
      (byte) 0xb5, (byte) 0xb6, (byte) 0xb7, (byte) 0xb8, (byte) 0xb9, (byte) 0xba, (byte) 0xc2, (byte) 0xc3,
      (byte) 0xc4, (byte) 0xc5, (byte) 0xc6, (byte) 0xc7, (byte) 0xc8, (byte) 0xc9, (byte) 0xca, (byte) 0xd2,
      (byte) 0xd3, (byte) 0xd4, (byte) 0xd5, (byte) 0xd6, (byte) 0xd7, (byte) 0xd8, (byte) 0xd9, (byte) 0xda,
      (byte) 0xe2, (byte) 0xe3, (byte) 0xe4, (byte) 0xe5, (byte) 0xe6, (byte) 0xe7, (byte) 0xe8, (byte) 0xe9,
      (byte) 0xea, (byte) 0xf2, (byte) 0xf3, (byte) 0xf4, (byte) 0xf5, (byte) 0xf6, (byte) 0xf7, (byte) 0xf8,
      (byte) 0xf9, (byte) 0xfa };

  private int offset;
  private int ySamplingFactor;
  private int nbQTables;
  private byte[] qTables;
  private int height;
  private int width;
  private boolean dri;
  private int restartInterval;
  private byte[] payload;

  static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

  private JpegFrame() {
  }

  /**
   * Analysiert ein eingehendes JPEG-Bild und extrahiert die relevanten Informationen
   * für die Erstellung der RFC-2435 konformen RTP-Payload Struktur.
   * DQT, SOF0 (Baseline DCT) und DRI werden dabei ausgewertet, der Rest ignoriert.
   * Payload nach SOS-Marker wird als Payload gespeichert.
   * @param jpegBytes JPEG-Bild als Byte Array (inklusive SOI und EOI)
   * @return {@link JpegFrame}
   */
  public static JpegFrame getFromJpegBytes(final byte[] jpegBytes) {

    // Prüfe ob SOI vorhanden ist.
    final byte[] start = Arrays.copyOf(jpegBytes, 2);
    if (!Arrays.equals(start, SOI_MARKER)) {
      throw new IllegalStateException("SOI Marker nicht gefunden.");
    }

    // Überspringe SOI
    byte[] data = Arrays.copyOfRange(jpegBytes, 2, jpegBytes.length);
    final JpegFrame jpegFrame = new JpegFrame();
    boolean headerFinish = false;

    while (!headerFinish && data.length > 4) {
      // Ermittle das nächste Header-Segment
      final byte[] marker = Arrays.copyOf(data, 2); // Segment-Marker
      final byte[] section = Arrays.copyOfRange(data, 2, data.length); // Segmentdaten
      final int section_size = byteArrayToInt(Arrays.copyOf(section, 2)); // Segmentlänge
      final byte[] section_body = Arrays.copyOfRange(data, 4, data.length); // Segment Body

      // Prüfe, welcher Marker gelesen wurde.
      if (Arrays.equals(marker, DQT_MARKER)) {
        if (section_body[0] != 0x00) {
          logger.log(Level.WARNING,"RFC 2435 supports only 8-bit precision");
        }

        /* Quantisierungstabelle (QT) ist 64 Byte lang. */
        jpegFrame.setNbQTables(section_size / 65); // Anzahl QT
        jpegFrame.setQTables(Arrays.copyOfRange(section_body, 1, jpegFrame.nbQTables * 65)); // Daten der (QT)

      } else if (Arrays.equals(marker, SOF0_MARKER)) {
        // Höhe und Breite des Bildes.
        final int height = byteArrayToInt(Arrays.copyOfRange(section_body, 1, 3));
        jpegFrame.setHeight(height);
        final int width = byteArrayToInt(Arrays.copyOfRange(section_body, 3, 5));
        jpegFrame.setWidth(width);

        if (width > 2040 || height > 2040) {
          // Länger als das unterstützte Limit aus RFC 2435.
          throw new IllegalStateException("Höhe oder Breite ist größer als es vom RFC-2435 unterstützt wird (2040px).");
        }

        // Get components sampling to determine type
        // Y has component ID 1
        // Possible configurations of sampling factors:
        // Y - 0x22, Cb - 0x11, Cr - 0x11 => yuvj420p
        // Y - 0x21, Cb - 0x11, Cr - 0x11 => yuvj422p

        // Only 3 components are supported by RFC 2435
        final int numComponents = byteArrayToInt(Arrays.copyOfRange(section_body, 5, 6));
        if (numComponents != 3) {
          logger.log(Level.WARNING, "RFC-2435 supports only  3 sampling-components: " + numComponents);
        }
        for (int j = 0; j < 3; j++) {
          final int idx = 6 + j * 3;
          final int integer = byteArrayToInt(Arrays.copyOfRange(section_body, idx, idx + 1));

          if (integer == 1) {
            jpegFrame.setYSamplingFactor(byteArrayToInt(Arrays.copyOfRange(section_body, idx + 1, idx + 2)));
          } else if (!Arrays.equals(new byte[] { (byte) 0x11 }, Arrays.copyOfRange(section_body, idx + 1, idx + 2))) {
            throw new IllegalStateException("Sampling Faktor ist nit unterstützt durch RFC-2435.");
          }
        }
      } else if (Arrays.equals(marker, DRI_MARKER)) {
        jpegFrame.setDri(true);
        jpegFrame.setRestartInterval(byteArrayToInt( Arrays.copyOfRange(  data, 4, 6)));
        logger.log(Level.FINEST, "JPEG: Restart-Interval: " + bytesToHex(data) );
        logger.log(Level.FINE, "JPEG: Restart-Interval: " + jpegFrame.restartInterval );

      } else if (Arrays.equals(marker, SOS_MARKER)) {
        headerFinish = true;
      }

      data = Arrays.copyOfRange(data, 2 + section_size, data.length);
    }

    jpegFrame.setPayload(data);
    return jpegFrame;
  }

  /**
   * Erstellt aud den Payloaddaten eines RTP-Paketes eine neue Instanz des rtp.JpegFrame.
   *
   * @param payload payload des RTP-Pakets
   * @return erstelltes rtp.JpegFrame
   */
  public static JpegFrame getFromRtpPayload(final byte[] payload) {
    final JpegFrame jpegFrame = new JpegFrame();

    jpegFrame.offset = byteArrayToInt(Arrays.copyOfRange(payload, 1, 4));

    final int type = byteArrayToInt(Arrays.copyOfRange(payload, 4, 5));
    // Setze y_sampling_factor und dri entsprechend
    jpegFrame.setSamplingFactorAndDriFromType(type);

    final int q = byteArrayToInt(Arrays.copyOfRange(payload, 5, 6));
    jpegFrame.width = byteArrayToInt(Arrays.copyOfRange(payload, 6, 7)) * 8;
    jpegFrame.height = byteArrayToInt(Arrays.copyOfRange(payload, 7, 8)) * 8;
    if (jpegFrame.dri) { // Restart-Header is present
      jpegFrame.restartInterval = byteArrayToInt(Arrays.copyOfRange(payload, 8, 10));
      logger.log(Level.FINE, "JPEG: Restart-Interval: " + jpegFrame.restartInterval);
    }

    final int offsetToQuantizationHeader = 8 + (jpegFrame.dri ? 4 : 0);
    if (q >= 127 && jpegFrame.offset == 0) { // Analyse der Quantization Table erfolgt
      // nur im ersten Paket eines Frames.
      final int length = byteArrayToInt(Arrays.copyOfRange(payload, offsetToQuantizationHeader + 2, offsetToQuantizationHeader + 4));
      final byte[] quantizationTableData = Arrays.copyOfRange(payload, offsetToQuantizationHeader + 4, offsetToQuantizationHeader + 4 + length);

      jpegFrame.nbQTables = length / 64; // Quantisierungstabelle ist stets 64 Byte lang
      jpegFrame.qTables = new byte[(jpegFrame.nbQTables * 65) - 1];
      for (int i = 0; i < jpegFrame.nbQTables; i++) {
        System.arraycopy(quantizationTableData, 64 * i, jpegFrame.qTables, 65 * i, 64);
        if (i > 0) {
          jpegFrame.qTables[65 * i + 64] = 0x00;
        }
      }
    }

    /* Quantisierungstabelle ist nur im ersten Paket enthalten. Länge ergibt sich aus vier fixen Bytes sowie je Tabelle 64 Byte. */
    final int quantizationTableHeaderLength = jpegFrame.offset == 0 ? 4 + jpegFrame.nbQTables * 64 : 0;
    final int offsetToPayload = offsetToQuantizationHeader + quantizationTableHeaderLength;
    jpegFrame.setPayload(Arrays.copyOfRange(payload, offsetToPayload, payload.length));
    return jpegFrame;
  }

  /**
   * Kombiniere Liste von RTP-Paketen zu einem JPEG
   * @param list der RTP-Pakete
   * @return JPEG
   */
  public static byte[] combineToOneImage(final List<RTPpacket> list) {
    ArrayList<JpegFrame> jpeg = new ArrayList<>();
    list.forEach( rtp -> jpeg.add( JpegFrame.getFromRtpPayload( rtp.getpayload()) ) );

    JpegFrame jpegs = JpegFrame.combineToOneFrame( jpeg );
    return jpegs.getJpeg();
  }



  /**
   * Setzt mehrere Frames, welche jeweils nur einen Teil zu einem zusammen
   *
   * @param frames
   * @return
   */
  public static JpegFrame combineToOneFrame(final List<JpegFrame> frames) {
    if (frames == null || frames.isEmpty()) {
      return null; // Nichts zu kombinieren.
    }

    final JpegFrame result = frames.get(0);
    if (frames.size() == 1) {
      return result;
    }

    // Add all Bytes of each payload part to a list

    final JpegFrame lastJpeg = frames.get(frames.size() - 1);
    final int length = lastJpeg.getPayload().length + lastJpeg.getOffset();
    byte[] jpeg = new byte[length];

    for (final JpegFrame frame : frames) {
      System.arraycopy(frame.getPayload(), 0, jpeg, frame.getOffset(), frame.getPayload().length);
    }

    result.setPayload(jpeg);
    return result;
  }

  /**
   * Liefert ein Byte-Array, mit Headern aus RFC-2435
   * * RFC-Header, Restart-Header, Quantisierungs-Header
   * * Quantisierungstabellen und
   * * JPEG-Payload
   *
   * @return Array von bytes
   */
  public byte[] getAsRfc2435Bytes() {
    int idx = 0; // Offset ist in diesem Fall immer null
    int headerLength = 8;     // Headerlänge 8 oder 12 Byte
    if (this.dri) {
      headerLength = headerLength + 4;
    }
    final int nb_qtables = this.nbQTables;
    if (nb_qtables > 0 && offset == 0) {
      headerLength = headerLength + 4 + (nb_qtables * 64);
    }
    final byte[] rfcHeader = new byte[headerLength + payload.length];
    rfcHeader[idx++] = 0; // type-specfic
    rfcHeader[idx++] = (byte) (offset >> 16);
    rfcHeader[idx++] = (byte) (offset >> 8);
    rfcHeader[idx++] = (byte) (offset & 0xff);
    rfcHeader[idx++] = (byte) (getType() & 0xff);  // Type
    rfcHeader[idx++] = (byte) (255 & 0xff);        // Q
    // Breite aufgerundet auf 8ter Kompliment und geteilt durch 8
    rfcHeader[idx++] = (byte) (((width + 7) & ~7) >> 3);
    // Höhe aufgerundet auf 8ter Kompliment und geteilt durch 8
    rfcHeader[idx++] = (byte) (((height + 7) & ~7) >> 3);

    // wenn DRI, dass Restartintervall hinzufügen
    if (this.dri) {
      rfcHeader[idx++] = (byte) (this.restartInterval >> 8);
      rfcHeader[idx++] = (byte) (this.restartInterval & 0xff);
      rfcHeader[idx++] = (byte) (0xff);
      rfcHeader[idx++] = (byte) (0xff);
    }
    // wenn Quantisationstabellen vorhanden sind, dann alle hinzufügen
    if (offset == 0 && nb_qtables != 0) {
      rfcHeader[idx++] = 0;
      rfcHeader[idx++] = 0;
      rfcHeader[idx++] = (byte) (64 * nb_qtables >> 8);
      rfcHeader[idx++] = (byte) (64 * nb_qtables & 0xff);

      for (int i = 0; i < nb_qtables; i++) {
        System.arraycopy(this.qTables, 65 * i, rfcHeader, idx, 64);
        idx += 64;
      }
    }
    // Fragmentlänge berechnen im Fall des letzten Pakets
    int length = (offset+MTU > payload.length) ? payload.length-offset : MTU;
   // JPEG-Payload-Fragment hinzufügen
    System.arraycopy(payload, offset, rfcHeader, idx, length);
    logger.log(Level.FINEST, "RFC-2435 Header: " + idx  + " Payload: " + length + " Bytes");
    return truncate(rfcHeader, idx + length);
  }


  /**
   * Liefert Liste mit RTP-Paketen, welche die RTP-Payload gemäß RFC-2435 enthalten.
   *
   * @return Liste der RTP-Pakete
   */
  public List<byte[]> getRtpPayload() {
    List<byte[]> rtpPayload = new ArrayList<>();  // Liste der Pakete erstellen
    offset = 0;

    do {
      // MTU berechnen im Fall von Restart-Header und mehreren Paketen mit ganzzahligen Intervallen
      if (restartInterval > 0) {
        MTU = 1400;
        for (int i = offset+2; i < payload.length-2; i += 1) {
          if (  payload[i] == -1 && Byte.toUnsignedInt(payload[i+1]) >= 0xD0 &&
                        Byte.toUnsignedInt(payload[i+1]) <= 0xD7) {
            MTU = i - offset;
            logger.log(Level.FINER, "JPEG: MTU: " + MTU);
            break;
          }
        }
      }

      rtpPayload.add( getAsRfc2435Bytes() );
      offset += MTU;
    } while (offset < payload.length);
    return rtpPayload;
  }


  private byte[] getPayload() {
    return payload;
  }

  public int getOffset() {
    return offset;
  }

  public byte[] getJpeg() {
    final List<Byte> result = new ArrayList<>();
    result.add(SOI_MARKER[0]);
    result.add(SOI_MARKER[1]);

    if (restartInterval > 0) {
      result.add(DRI_MARKER[0]);
      result.add(DRI_MARKER[1]);
      result.add((byte) 0x00);
      result.add((byte) 0x04);
      result.add((byte) (restartInterval >> 8));
      result.add((byte) (restartInterval));
    }

    result.add(DQT_MARKER[0]);
    result.add(DQT_MARKER[1]);
    final int qTableLength = (nbQTables * 65) + 2;
    result.add((byte) (qTableLength >> 8));
    result.add((byte) (qTableLength));
    result.add((byte) 0x00);
    for (final byte b : qTables) {
      result.add(b);
    }

    // Huffman Tables
    result.addAll(createHuffmanTable(LUM_DC_CODELENS, LUM_DC_SYMBOLS, 0, 0));
    result.addAll(createHuffmanTable(LUM_AC_CODELENS, LUM_AC_SYMBOLS, 0, 1));
    result.addAll(createHuffmanTable(CHM_DC_CODELENS, CHM_DC_SYMBOLS, 1, 0));
    result.addAll(createHuffmanTable(CHM_AC_CODELENS, CHM_AC_SYMBOLS, 1, 1));

    result.add(SOF0_MARKER[0]);
    result.add(SOF0_MARKER[1]);// SOF
    result.add((byte) 0x00);
    result.add((byte) 0x11);
    result.add((byte) 0x08);
    result.add((byte) (height >> 8));
    result.add((byte) height);
    result.add((byte) (width >> 8));
    result.add((byte) width);

    result.add((byte) 0x03);
    result.add((byte) 0x01);
    result.add((byte) (ySamplingFactor));
    result.add((byte) 0x00);

    result.add((byte) 0x02);
    result.add((byte) 0x11);
    result.add((byte) 0x00);

    result.add((byte) 0x03);
    result.add((byte) 0x11);
    result.add((byte) 0x00);

    result.add(SOS_MARKER[0]);
    result.add(SOS_MARKER[1]);// Marker SOS
    result.add((byte) 0x00); // Length
    result.add((byte) 0x0c); // Length - 12
    result.add((byte) 0x03); // Number of components
    result.add((byte) 0x01); // Component Number
    result.add((byte) 0x00); // Matrix Number
    result.add((byte) 0x02); // Component Number
    result.add((byte) 0x11); // Horizontal or Vertical Sample
    result.add((byte) 0x03); // Component Number
    result.add((byte) 0x11); // Horizontal or Vertical Sample
    result.add((byte) 0x00); // Start of spectral
    result.add((byte) 0x3f); // End of spectral (63)
    result.add((byte) 0x00); // Successive approximation bit position (high, low)

    boolean marker = false;
    for (final byte b : payload) {
      if (b == MARKER_TAG_START) {
        marker = true;
      } else if (marker && b == EOI_MARKER[1]) {
        result.add(b);
        break;
      } else {
        marker = false;
      }
      result.add(b);
    }

    // Transformiere List in ein byte Array
    final byte[] bytes = new byte[result.size()];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = result.get(i);
    }

    return bytes;
  }

  public void setYSamplingFactor(final int ySamplingFactor) {
    this.ySamplingFactor = ySamplingFactor;
  }

  public void setNbQTables(final int nbQTables) {
    this.nbQTables = nbQTables;
  }

  public void setQTables(final byte[] qTables) {
    this.qTables = qTables;
  }

  public void setHeight(final int height) {
    this.height = height;
  }

  public void setWidth(final int width) {
    this.width = width;
  }

  public void setDri(final boolean dri) {
    this.dri = dri;
  }

  public void setRestartInterval(final int restartInterval) {
    this.restartInterval = restartInterval;
  }

  public void setPayload(final byte[] payload) {
    this.payload = payload;
  }

  /**
   * Liefert den Typ, welcher anhand des Y-Smaplingfaktors sowie dem DRI-Flag bestimmt wird gemäß RFC-2435.
   *
   * @return Typ als Integer
   */
  private int getType() {
    int type;
    switch (ySamplingFactor) {
      case 0x22: // yuvj420p
        type = 1;
        break;
      case 0x21: // yuvj422p
        type = 0;
        break;
      default:
        throw new IllegalStateException("Sampling Format nicht durch RFC-2435 unterstützt.");
    }

    if (dri) {
      type += 64;
    }

    return type;
  }

  /**
   * Setzt den Y-Samplingfaktor anhand des Typs gemäß RFC-2435.
   *
   * @param type Type
   */
  private void setSamplingFactorAndDriFromType(final int type) {
    switch (type) {
      case 64: // yuvj420p
        this.dri = true;
        // gewollter Fall-Through
      case 0: // yuvj420p
        this.ySamplingFactor = 0x21;
        break;
      case 65: // yuvj422p
        this.dri = true;
      case 1: // yuvj422p
        this.ySamplingFactor = 0x22;
        break;
      default:
        throw new IllegalStateException("Sampling Format durch RFC-2435 nicht unterstützt.");
    }
  }

  /**
   * Transformiert ein byte Array (4, 2 oder 1 stellig) in einen Integer (int).
   *
   * @param bytes byte Array zur Transformation
   *
   * @return transformierten int
   */
  private static int byteArrayToInt(final byte[] bytes) {
    if (bytes.length == 3) {
      return (bytes[0] & 0xFF) << 16 | (bytes[1] & 0xFF) << 8 | (bytes[2] & 0xFF);
    } else if (bytes.length == 2) {
      return bytes[1] & 0xFF | (bytes[0] & 0xFF) << 8;
    } else if (bytes.length == 1) {
      return bytes[0] & 0xFF;
    } else {
      throw new UnsupportedOperationException("Bisher nicht implementiert, da nicht unterstützt.");
    }
  }

  /**
   * Erstellt die Huffmann Tabellen gemäß RFC-2435.
   *
   * @param codeLens CodeLens Tabelle
   * @param symbols Symboltabelle
   * @param tableNo Tabellennummer
   * @param tableClass Klasse
   * @return Liste von Bytes, welche die Huffman-Tabellen repräsentieren (inkl. Marker)
   */
  private List<Byte> createHuffmanTable(final byte[] codeLens, final byte[] symbols, final int tableNo, final int tableClass) {
    final List<Byte> result = new ArrayList<>();
    result.add((byte) 0xff);
    result.add((byte) 0xc4);
    result.add((byte) (0));
    result.add((byte) (3 + codeLens.length + symbols.length));
    result.add((byte) ((tableClass << 4) | tableNo));
    for (final byte b : codeLens) {
      result.add(b);
    }
    for (final byte b : symbols) {
      result.add(b);
    }
    return result;
  }

  public static byte[] truncate(byte[] array, int newLength) {
    if (array.length < newLength) {
      return array;
    } else {
      byte[] truncated = new byte[newLength];
      System.arraycopy(array, 0, truncated, 0, newLength);

      return truncated;
    }
  }

}
