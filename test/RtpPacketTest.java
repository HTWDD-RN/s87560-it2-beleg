import static org.junit.jupiter.api.Assertions.*;
import static rtp.RtpHandler.RTP_PAYLOAD_JPEG;

import org.junit.jupiter.api.*;
import rtp.RTPpacket;


class RtpPacketTest {

  private static RTPpacket rtpPacket;

  @BeforeAll
  public static void initRTP() {
    rtpPacket = new RTPpacket(26, 42, 0, 1, new byte[0], 0);
  }

  @Test
  public void testSetRtpHeader() {
    assertEquals(42, rtpPacket.getsequencenumber());
  }

  @AfterAll
  static void afterAll() {

  }
}