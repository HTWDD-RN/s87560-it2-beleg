import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.imageio.ImageIO;
import rtp.ReceptionStatistic;
import rtp.RtpHandler;

abstract class JpegDisplayDemo {
  static BufferedImage lastImage;


/*
             3          2          1          0
  bitpos    10987654 32109876 54321098 76543210
  ------   +--------+--------+--------+--------+
  bits     |AAAAAAAA|RRRRRRRR|GGGGGGGG|BBBBBBBB|
*/

  static BufferedImage textImage(String text) {
    BufferedImage startImage = new BufferedImage(640, 480, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = startImage.createGraphics();
    g.setPaint ( new Color( 150, 0, 50 ) );
    g.setFont(new Font("Arial", Font.PLAIN, 45));
    g.drawString(text, 250, 200);
    g.dispose();
    return startImage;
  }

  /**
   * Set foreground image transparent for the slices given in the list
   * @param back background image, will be updated to new background
   * @param foreground  foreground image
   * @param list list if missed slices in the foreground image
   * @return image
   */
  abstract BufferedImage setTransparency(BufferedImage back, BufferedImage foreground, List<Integer> list);
  //TASK implement method in class JpegDisplay


  /**
   * Get the next image from RTP-Handler and make error concealment using transparency
   * @param rH RtpHandler to get image payload and lost slices
   * @param rs Statistics
   * @param eco sets error concealment on or off
   * @return BufferedImage
   */
  public static BufferedImage nextPlaybackImage(RtpHandler rH, ReceptionStatistic rs, boolean eco) {
    BufferedImage newImage, combined;

    byte[] payload = rH.nextPlaybackImage();  // get next frame to display, as byte array
    List<Integer> list = rH.getLostJpegSlices(); // get list of lost slices

    if (payload == null) {
      if (lastImage == null) {
        return textImage("Start");
      } else if (!eco) {
        return textImage("Lost image");
      } else {
        return lastImage;
      }
    }

    InputStream is = new ByteArrayInputStream(payload); // read newImage from payload
    try {
      newImage = ImageIO.read(is);
//      newImage = ImageIO.read(new File("videos/htw-restart-1-error.jpeg")); // Test
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
    if (lastImage == null) lastImage = newImage;    // save first image
    // set transparency in case of error concealment
    JpegDisplayDemo js = new jpegDisplay();
    combined = (eco) ? js.setTransparency(lastImage, newImage, list) : newImage;
    lastImage = combined;  // update
    return setText(combined, rs.requestedFrames, list.toString());
  }


private static BufferedImage setText(BufferedImage image, int nr, String text) {
  Graphics2D g;
  g = image.createGraphics();      // set Text
  g.setPaint(new Color(150, 50, 50));
  g.setFont(new Font("Arial", Font.BOLD, 14)); // PLAIN, BOLD, ITALIC
  g.drawString(String.valueOf(nr), 10, 15);
  g.setFont(new Font("Arial", Font.PLAIN, 12)); // PLAIN, BOLD, ITALIC
  g.drawString(text, 50, 15); // missing slices
  //g.setFont(new Font("Arial", Font.BOLD, 20)); // PLAIN, BOLD, ITALIC
  //g.drawString("Frame lost!", 200, 15);
  g.dispose();
  return image;
}


}
