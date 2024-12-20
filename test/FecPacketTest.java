import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rtp.FECpacket;


class FecPacketTest {
private static FECpacket fecPacket;

  @BeforeAll
  public static void initRTP() {
    fecPacket = new FECpacket(26, 42, 0, 48, 0);
  }
  @AfterAll
  static void afterAll() {
  }

  @Test
  void setFecHeader() {}

  @Test
  void setUlpLevelHeader() {}

  @Test
  void getRtpList() {}

  @Test
  void addRtp() {}




}