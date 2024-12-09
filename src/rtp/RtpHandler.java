package rtp;


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RtpHandler {
    public static final int RTP_PAYLOAD_FEC = 127;
    public static final int RTP_PAYLOAD_JPEG = 26;
    private static byte[] defaultKey = new byte[]{-31, -7, 122, 13, 62, 1, -117, -32, -42, 79, -93, 44, 6, -34, 65, 57};
    private static byte[] defaultSalt = new byte[]{14, -58, 117, -83, 73, -118, -2, -21, -74, -106, 11, 58, -85, -26};
    private EncryptionMode encryptionMode;
    private FecHandler fecHandler = null;
    private JpegEncryptionHandler jpegEncryptionHandler = null;
    private SrtpHandler srtpHandler = null;
    private int currentSeqNb = 0;
    private boolean fecEncodingEnabled = false;
    static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    int dropCounter = 0;
    private Boolean isServer;
    int fecGroupSize;
    private int currentTS = 0;    // timestamp of a set of frames
    Random random = new Random(123456); // Channel loss - fixed seed for debugging


    private boolean fecDecodingEnabled = false; // client side
    private HashMap<Integer, RTPpacket> mediaPackets = null;
    private int playbackIndex = -1;  // iteration index fo rtps for playback
    private int startIndex;         // first RTP of a stream
    private int fecIndex;            // iteration index for fec correction
    private int tsReceive;           // Timestamp of last received media packet
    private int tsIndex;            // Timestamp index for rtps for playback
    private int tsStart;            // Timestamp start for rtps for playback
    private int tsAdd = 0;          // Timestamp add for rtps for playback
    private int jitterBufferStartSize = 25;      // size of the input buffer => start delay
    DatagramSocket RTPsocket; // socket to be used to send and receive UDP packets

    private List<Integer> lostJpegSlices = null;
    private HashMap<Integer, List<Integer>> sameTimestamps = null;
    private HashMap<Integer, Integer> firstRtp= null; // first RTP of a jpeg-frame
    private HashMap<Integer, Integer> lastRtp= null; // last RTP of a jpeg-frame
    private ReceptionStatistic statistics = null;
    private Receiver receiver = null;           // RTP receiver thread






    public void setJitterBufferStartSize(int jitterBufferStartSize) {
        this.jitterBufferStartSize = jitterBufferStartSize;
    }

    public RtpHandler(int var1) {
        if (var1 > 0) {
            this.fecEncodingEnabled = true;
            this.fecHandler = new FecHandler(var1);
        }

    }

    public RtpHandler(boolean var1) {
        this.fecDecodingEnabled = var1;
        this.fecHandler = new FecHandler(var1);
        this.mediaPackets = new HashMap();
        this.sameTimestamps = new HashMap();
        this.statistics = new ReceptionStatistic();
    }

    public byte[] createFecPacket() {
        if (!this.isFecPacketAvailable()) {
            return null;
        } else {
            byte[] fecPacket = this.fecHandler.getPacket();
            Object var2 = null;
            switch (this.encryptionMode.ordinal()) {
                case 1:
                    byte[] var3 = this.srtpHandler.transformToSrtp(new RTPpacket(fecPacket, fecPacket.length));
                    if (var3 != null) {
                        fecPacket = var3;
                    }
                    break;
                    //CASE 2 JPEG
                case 2:
                    // CASE 3 JPEG_attack:
                case 3:
                default:
                     break;
            }
            return fecPacket;
        }
    }



    public boolean isFecPacketAvailable() {
        return this.fecEncodingEnabled ? this.fecHandler.isReady() : false;
    }

    public List<RTPpacket> jpegToRtpPackets(final byte[] jpegImage, int framerate) {
        byte[] image = switch (encryptionMode) {
            case JPEG -> jpegEncryptionHandler.encrypt(jpegImage);
            default -> jpegImage;
        };

        JpegFrame frame = JpegFrame.getFromJpegBytes(image); // convert JPEG to RTP payload

        List<RTPpacket> rtpPackets = new ArrayList<>();
        currentTS += (90000 / framerate); // TS is the same for all fragments
        int Mark = 0; // Marker bit is 0 for all fragments except the last one

        // iterieren über die JPEG-Fragmente und RTPs bauen
        ListIterator<byte[]> iter = frame.getRtpPayload().listIterator();
        while (iter.hasNext()) {
            byte[] frag = iter.next();
            if (!iter.hasNext()) {
                Mark = 1; // last segment -> set Marker bit
            }
            currentSeqNb++;

            // Build an RTPpacket object containing the image
            // time has to be in scale with 90000 Hz (RFC 2435, 3.)
            RTPpacket packet = new RTPpacket(
                    RTP_PAYLOAD_JPEG, currentSeqNb, currentTS, Mark, frag, frag.length);

            rtpPackets.add(packet);
        }

        return rtpPackets;
    }

    public void sendJpeg(final byte[] jpegImage, int framerate, InetAddress clientIp, int clientPort, double lossRate) {
        List<RTPpacket> rtpPackets;    // fragmented RTP packets
        DatagramPacket sendDp;      // UDP packet containing the video frames

        rtpPackets = jpegToRtpPackets(jpegImage, framerate); // gets the fragmented RTP packets

        for (RTPpacket rtpPacket : rtpPackets) {    // Liste der RTP-Pakete

            byte[] packetData;
            if (encryptionMode == EncryptionMode.SRTP) {
                packetData = srtpHandler.transformToSrtp(rtpPacket);
            } else {
                packetData = rtpPacket.getpacket();
            }

            sendDp = new DatagramPacket(packetData, packetData.length, clientIp, clientPort);
            sendPacketWithError(sendDp,lossRate, false); // Send with packet loss

            if (fecEncodingEnabled) fecHandler.setRtp(rtpPacket);
            if (isFecPacketAvailable()) {
                logger.log(Level.FINE, "FEC-Encoder ready...");
                byte[] fecPacket = createFecPacket();
                // send to the FEC dest_port
                sendDp = new DatagramPacket(fecPacket, fecPacket.length, clientIp, clientPort);
                sendPacketWithError(sendDp, lossRate, true);
            }
        }
    }

    /**
     * @param senddp Datagram to send
     */
    private void sendPacketWithError(DatagramPacket senddp, double lossRate, boolean fec) {
        String label;
        if (fec) label = " rtp ";
        else label = " media ";
        if (random.nextDouble() > lossRate) {
            logger.log(Level.FINE, "Send frame: " + label + " size: " + senddp.getLength());
            try {
                RTPsocket.send(senddp);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            if (!fec) dropCounter++;
            logger.log(Level.INFO, "Dropped frame: " + label + "Counter: " + dropCounter);
        }
    }

    public ReceptionStatistic getReceptionStatistic() {
        // update values which are used internally and that are not just statistic
        statistics.playbackIndex = playbackIndex;

        return statistics;
    }


    /**
     * Get next image for playback.
     * This method is the main interface for continuously getting images
     * for the purpose of displaying them.
     *
     * @return Image as byte array
     */


    public byte[] nextPlaybackImage() {
        // Check if jitter buffer is filled
        if (tsReceive <= tsStart + jitterBufferStartSize * tsAdd ) {
            logger.log(Level.FINE, "PLAY: jitter buffer not filled: " + tsReceive + " " + tsStart);
            return null;
        }

        statistics.requestedFrames++;
        //playbackIndex++;   // TODO: check if this is correct
        tsIndex += tsAdd;  // set  TS for next image
        statistics.jitterBufferSize = (tsReceive - tsIndex) / tsAdd;


        ArrayList<RTPpacket> packetList = packetsForNextImage();
        if (packetList == null) {
            logger.log(Level.FINE, "PLAY: no RTPs for playback  TS : " + tsIndex + " " + playbackIndex);
            return null;
        } else logger.log(Level.FINE, "PLAY: RTP list size for rtp: " +playbackIndex + " " + packetList.size());

        // TODO sometimes nullpointer exception
        logger.log(Level.FINER, "PLAY: get RTPs from " + packetList.get(0).getsequencenumber()
                + " to " + packetList.get(packetList.size()-1).getsequencenumber());

        // Combine RTP packets to one JPEG image
        //byte[] image = rtp.JpegFrame.combineToOneImage(packetList);
        ArrayList<JpegFrame> jpeg = new ArrayList<>();
        packetList.forEach( rtp -> jpeg.add( JpegFrame.getFromRtpPayload( rtp.getpayload()) ) );
        JpegFrame jpegs = JpegFrame.combineToOneFrame( jpeg );
        byte[] image =  jpegs.getJpeg();


        logger.log(Level.FINER, "Display TS: "
                + (packetList.get(0).gettimestamp() & 0xFFFFFFFFL)
                + " size: " + image.length);

        byte[] decryptedImage;
        switch (encryptionMode) {
            case JPEG:
                decryptedImage = jpegEncryptionHandler.decrypt(image);
                if (decryptedImage != null) {
                    image = decryptedImage;
                }
                break;
            case JPEG_ATTACK:
                decryptedImage = jpegEncryptionHandler.replaceAttackDecryption(image);
                if (decryptedImage != null) {
                    image = decryptedImage;
                }
                break;
            case SRTP:
            default:
                break;
        }

        return image;
    }

    public void reset() {
        currentSeqNb = 0;
        //fecHandler.reset();
        playbackIndex = -1;  // Client
        dropCounter = 0;

        if (!isServer) {
            //mediaPackets.clear();
            //sameTimestamps.clear();
            statistics = new ReceptionStatistic();
        }
    }


    public void startReceiver(int port) {
        receiver = new Receiver(port);
    }

    public void stopReceiver() {
        logger.log(Level.FINE, "RTP-Receiver try stopping...");
        receiver.interrupt();
        receiver.rtpSocket.close();
    }

    public List<Integer> getLostJpegSlices() {
        return lostJpegSlices;
    }

    public void setLostJpegSlices(List<Integer> lostJpegSlices) {
        this.lostJpegSlices = lostJpegSlices;
    }

    private class Receiver extends Thread {
        DatagramSocket rtpSocket;
        byte[] buffer = new byte[65356];
        int lastSeqNr;

        public Receiver(int port) {
            super("RTP-Receiver");
            try {
                rtpSocket = new DatagramSocket( port );
                logger.log(Level.FINE, "Socket receive buffer: " + rtpSocket.getReceiveBufferSize());
            } catch (SocketException e) {
                throw new RuntimeException(e);
            }
            start();
        }

        public void run() {
            DatagramPacket rcvdp = new DatagramPacket(buffer, buffer.length);
            while (!interrupted()) {
                try {
                    rtpSocket.receive(rcvdp);
                    RTPpacket packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());;
                    if (packet.getpayloadtype() == RTP_PAYLOAD_JPEG) {
                        lastSeqNr = packet.getsequencenumber();
                    }
                    logger.log(Level.FINER, "RTP: received: " + lastSeqNr);
                    processRtpPacket(packet, false);

                    // look for missing RTPs
                    for (int i = fecIndex; i < lastSeqNr - fecHandler.getFecGroupSize()-5; i++) {
                        if (mediaPackets.get(i) == null) {
                            //statistics.packetsLost++;
                            //logger.log(Level.FINER, "RTP: Media lost: " + i);
                            RTPpacket rtp = checkMediaPacket(i);
                            if (rtp != null) {
                                //logger.log(Level.FINER, "RTP: Media corrected: " + i);
                                processRtpPacket(rtp, true);
                            }

                        }
                    }
                    // TODO: check if this is correct
                    int newfec = lastSeqNr - fecHandler.getFecGroupSize()-5;
                    if (newfec > fecIndex) fecIndex = newfec;
                } catch (IOException e) {    // SocketException is part of IOException
                    if (interrupted()) {
                        logger.log(Level.FINE, "RTP-Receiver interrupted");
                        break;
                    } else {
                        throw new RuntimeException(e);
                    }
                }
            }
            logger.log(Level.FINE, "RTP-Receiver stopped");
        }
    }


    public void processRtpPacket(RTPpacket packet, boolean fec) {
        int seqNr = packet.getsequencenumber();
        RTPpacket decryptedPacket;
        switch (encryptionMode) {
            case SRTP:
                decryptedPacket = srtpHandler.retrieveFromSrtp(packet.getpacket());
                if (decryptedPacket != null) {
                    packet = decryptedPacket;
                }
                break;
            case JPEG:
            case JPEG_ATTACK:
            default:
                break;
        }

        // store the first Timestamp
        if (playbackIndex == -1) {
            playbackIndex = seqNr - 1;
            //startIndex = seqNr;
            fecIndex = seqNr;
            tsStart = packet.gettimestamp();
            tsIndex = tsStart;
            tsAdd = 0;
        }
        // evaluate the correct TS-Offset at the beginning
        if (packet.gettimestamp()  > tsStart  && tsAdd == 0) {
            tsAdd  = packet.gettimestamp() - tsStart;
            logger.log(Level.FINER, "RTP: set tsAdd: " + tsAdd);
        }


        logger.log(Level.FINER,
                "---------------- Receiver RTP-Handler --------------------"
                        + "\r\n"
                        + "Got RTP packet with SeqNum # "
                        + packet.getsequencenumber()
                        + " TimeStamp: "
                        + (0xFFFFFFFFL & packet.gettimestamp()) // cast to long
                        + " ms, of type "
                        + packet.getpayloadtype()
                        + " Size: " + packet.getlength());

        switch (packet.getpayloadtype()) {
            case RTP_PAYLOAD_JPEG:
                if (!fec) {
                    statistics.receivedPackets++;
                    statistics.latestSequenceNumber = seqNr;
                }
                mediaPackets.put(seqNr, packet);
                // set first RTP of a jpeg-frame
                if (packet.getJpegOffset() == 0) {
                    logger.log(Level.FINER, "got first Paket: " + seqNr);
                    firstRtp.put(packet.gettimestamp(), seqNr);
                }
                // set the last RTP of a jpeg-frame
                if (packet.getMarker() == 1) {
                    logger.log(Level.FINER, "got last Paket: " + seqNr);
                    lastRtp.put(packet.gettimestamp(), seqNr);
                }
                logger.log(Level.FINER, "JPEG-Offset + Marker: "
                        + packet.getJpegOffset() + " " + packet.getMarker());

                // set list of same timestamps
                int ts = packet.gettimestamp();
                tsReceive = ts;

                if (!fec && tsAdd != 0) statistics.jitterBufferSize = (tsReceive - tsIndex) / tsAdd;

                List<Integer> tmpTimestamps = sameTimestamps.get(ts);
                if (tmpTimestamps == null) {
                    tmpTimestamps = new ArrayList<>();
                }
                tmpTimestamps.add(seqNr);
                sameTimestamps.put(ts, tmpTimestamps);
                logger.log(Level.FINER, "RTP: set sameTimestamps: " + (0xFFFFFFFFL & ts)
                        + " " + tmpTimestamps);
                break;


            case RTP_PAYLOAD_FEC:
                fecHandler.rcvFecPacket(packet);
                break;

            default:  // ignore unknown packet
        }

        // TASK remove comment for debugging
        packet.printheader(); // print rtp header bitstream for debugging
    }



    /**
     * Set packet encryption.
     *
     * @param mode The encryption mode.
     * @return true if successful, false otherwise
     */
    public boolean setEncryption(EncryptionMode mode) {
        if (currentSeqNb > 0 || (statistics != null && statistics.latestSequenceNumber > 0)) {
            // Do not change encryption when already started.
            return false;
        }

        encryptionMode = mode;
        switch (encryptionMode) {
            case SRTP:
                /* Use pre-shared key and salt to avoid key management and
                 * session initialization with a protocol.
                 */
                try {
                    srtpHandler = new SrtpHandler(
                            SrtpHandler.EncryptionAlgorithm.AES_CTR,
                            SrtpHandler.MacAlgorithm.NONE,
                            defaultKey, defaultSalt, 0);
                } catch (InvalidKeyException | InvalidAlgorithmParameterException ikex) {
                    System.out.println(ikex);
                }
                if (srtpHandler == null) {
                    return false;
                }
                break;
            case JPEG:
            case JPEG_ATTACK:
                /* Use pre-shared key and salt to avoid key management and
                 * session initialization with a protocol.
                 */
                jpegEncryptionHandler = new JpegEncryptionHandler(
                        defaultKey, defaultSalt);
                break;
            case NONE:
            default:
                break;
        }

        return true;
    }

    /**
     * Get the RTP packet with the given sequence number.
     * This is the main method for getting RTP packets. It currently
     * includes error correction via FEC, but can be extended in the future.
     *
     * @param number Sequence number of the RTP packet
     * @return RTP packet, null if not available and not correctable
     */
    private RTPpacket checkMediaPacket(final int number) {
        int index = number % 0x10000; // account overflow of SNr (16 Bit)
        RTPpacket packet = mediaPackets.get(index);
        logger.log(Level.FINER, "RTP: try get RTP nr: " + index);

        if (packet == null) {
            statistics.packetsLost++;
            logger.log(Level.WARNING, "RTP: Media lost: " + index);

            boolean fecCorrectable = fecHandler.checkCorrection(index, mediaPackets);
            if (fecDecodingEnabled && fecCorrectable) {
                packet = fecHandler.correctRtp(index, mediaPackets);
                statistics.correctedPackets++;
                logger.log(Level.INFO, "---> FEC: correctable: " + index);
            } else {
                statistics.notCorrectedPackets++;
                logger.log(Level.INFO, "---> FEC: not correctable: " + index);
                return null;
            }
        }
        return packet;
    }



    public void setFecDecryptionEnabled(boolean var1) {
        this.fecDecodingEnabled = var1;
    }

    public void setFecGroupSize(int size) {
        fecGroupSize = size;
    }
    public int getFecGroupSize() {
        return fecGroupSize;
    }


    private RTPpacket obtainMediaPacket(int var1) {
        Logger var2 = Logger.getLogger("global");
        int var3 = var1 % 65536;
        RTPpacket var4 = (RTPpacket)this.mediaPackets.get(var3);
        var2.log(Level.FINE, "FEC: get RTP nu: " + var3);
        if (var4 == null) {
            ++this.statistics.packetsLost;
            var2.log(Level.WARNING, "FEC: Media lost: " + var3);
            boolean var5 = this.fecHandler.checkCorrection(var3, this.mediaPackets);
            if (!this.fecDecodingEnabled || !var5) {
                ++this.statistics.notCorrectedPackets;
                var2.log(Level.INFO, "---> FEC: not correctable: " + var3);
                return null;
            }

            var4 = this.fecHandler.correctRtp(var3, this.mediaPackets);
            ++this.statistics.correctedPackets;
            var2.log(Level.INFO, "---> FEC: correctable: " + var3);
        }

        return var4;
    }

    private ArrayList<RTPpacket> packetsForNextImage() {
        Logger var1 = Logger.getLogger("global");
        ArrayList var2 = new ArrayList();
        RTPpacket var3 = this.obtainMediaPacket(this.playbackIndex);
        if (var3 == null) {
            ++this.statistics.framesLost;
            return null;
        } else {
            var2.add(var3);
            int var4 = var3.gettimestamp();
            List var5 = (List)this.sameTimestamps.get(var4);
            if (var5 == null) {
                return var2;
            } else {
                for(int var6 = 1; var6 < var5.size(); ++var6) {
                    var2.add(this.obtainMediaPacket((Integer)var5.get(var6)));
                }

                this.playbackIndex += var5.size() - 1;
                Level var10001 = Level.FINER;
                int var10002 = var2.size();
                var1.log(var10001, "-> Get list of " + var10002 + " RTPs with TS: " + (4294967295L & (long)var4));
                return var2;
            }
        }
    }

    public static enum EncryptionMode {
        NONE,
        SRTP,
        JPEG,
        JPEG_ATTACK;

        private EncryptionMode() {
        }
    }
}
