package rtp;

import rtp.FecPacket;
import rtp.RTPpacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FecHandler {
    RTPpacket rtp;
    FecPacket fec;
    HashMap<Integer, FecPacket> fecStack = new HashMap();
    HashMap<Integer, Integer> fecNr = new HashMap();
    HashMap<Integer, List<Integer>> fecList = new HashMap();
    int playCounter = 0;
    static final int MJPEG = 26;
    int FEC_PT = 127;
    int fecSeqNr;
    int lastReceivedSeqNr;
    static final int maxGroupSize = 48;
    int fecGroupSize;
    int fecGroupCounter;
    boolean useFec;
    byte[] lastPayload = new byte[]{1};
    int nrReceived;
    int nrLost;
    int nrCorrected;
    int nrNotCorrected;
    int nrFramesRequested;
    int nrFramesLost;

    public FecHandler(int var1) {
        this.fecGroupSize = var1;
    }

    public FecHandler(boolean var1) {
        this.useFec = var1;
    }

    public void setRtp(RTPpacket var1) {
        if (this.fec == null) {
            this.fec = new FecPacket(this.FEC_PT, this.fecSeqNr, var1.gettimestamp(), this.fecGroupSize, var1.getsequencenumber());
            this.fec.setUlpLevelHeader(0, 0, this.fecGroupSize);
        }

        ++this.fecGroupCounter;
        this.fec.TimeStamp = var1.gettimestamp();
        this.fec.addRtp(var1);
    }

    public boolean isReady() {
        return this.fecGroupCounter == this.fecGroupSize;
    }

    public byte[] getPacket() {
        this.fec.printHeaders();
        ++this.fecSeqNr;
        this.fecGroupCounter = 0;
        byte[] var1 = this.fec.getpacket();
        this.fec = null;
        return var1;
    }

    private void clearSendGroup() {
    }

    public void setFecGroupSize(int var1) {
        this.fecGroupSize = var1;
    }

    public void rcvFecPacket(RTPpacket var1) {
        Logger var2 = Logger.getLogger("global");
        this.fec = new FecPacket(var1.getpacket(), var1.getpacket().length);
        this.fec.printHeaders();
        int var3 = this.fec.getsequencenumber();
        this.fecSeqNr = var3;
        this.fecStack.put(var3, this.fec);
        ArrayList var4 = this.fec.getRtpList();
        var2.log(Level.FINER, "FEC: set list: " + var3 + " " + var4.toString());
        var4.forEach((var2x) -> {
            this.fecNr.put((Integer) var2x, var3);
        });
        var4.forEach((var2x) -> {
            this.fecList.put((Integer) var2x, var4);
        });
    }

    /**
     * *** Sender *** Posibility to set the group at run time
     *
     */

    public int getFecGroupSize() {
        return fecGroupSize;
    }

    public boolean checkCorrection(int var1, HashMap<Integer, RTPpacket> var2) {
        if (this.fecStack.get(this.fecNr.get(var1)) == null) {
            return false;
        } else {
            Iterator var3 = ((List)this.fecList.get(var1)).iterator();

            Integer var4;
            do {
                if (!var3.hasNext()) {
                    return true;
                }

                var4 = (Integer)var3.next();
            } while(var4 == var1 || var2.get(var4) != null);

            return false;
        }
    }

    public RTPpacket correctRtp(int var1, HashMap<Integer, RTPpacket> var2) {
        FecPacket var3 = (FecPacket)this.fecStack.get(this.fecNr.get(var1));
        Iterator var4 = ((List)this.fecList.get(var1)).iterator();

        while(var4.hasNext()) {
            Integer var5 = (Integer)var4.next();
            if (var5 != var1) {
                var3.addRtp((RTPpacket)var2.get(var5));
            }
        }

        return var3.getLostRtp(var1);
    }

    private void clearStack(int var1) {
        this.fecStack.entrySet().removeIf((var1x) -> {
            return (Integer)var1x.getKey() < var1;
        });
        this.fecNr.entrySet().removeIf((var1x) -> {
            return (Integer)var1x.getKey() < var1;
        });
        this.fecList.entrySet().removeIf((var1x) -> {
            return (Integer)var1x.getKey() < var1;
        });
    }

    public int getSeqNr() {
        return this.lastReceivedSeqNr;
    }

    public int getNrReceived() {
        return this.nrReceived;
    }

    public int getPlayCounter() {
        return this.playCounter;
    }

    public int getNrLost() {
        return this.nrLost;
    }

    public int getNrCorrected() {
        return this.nrCorrected;
    }

    public int getNrNotCorrected() {
        return this.nrNotCorrected;
    }

    public int getNrFramesLost() {
        return this.nrFramesLost;
    }

    public int getNrFramesRequested() {
        return this.nrFramesRequested;
    }
}
