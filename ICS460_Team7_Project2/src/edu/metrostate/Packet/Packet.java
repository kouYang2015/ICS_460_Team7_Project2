package edu.metrostate.Packet;

import java.io.Serializable;

public class Packet implements Serializable{
	private static final long serialVersionUID = 5064345598182630522L;
	short cksum; //16-bit 2-byte 
	short len;	//16-bit 2-byte 
	int ackno;	//32-bit 4-byte 
	int seqno; //32-bit 4-byte Data packet Only 
	byte[] data = null; //0-500 bytes. Data packet only. Variable
	
	/**
	 * Used by Client to create DataPackets. len determined by byte[] data size + 12.
	 * @param ackno
	 * @param seqno
	 * @param byteSize
	 */
	public Packet (int ackno, int seqno, byte[] data) {
		this.cksum = 0; //default value.
		this.ackno = ackno;
		this.seqno = seqno;
		this.data = data;
		this.len = (short) (data.length+12);
	}
	
	/**
	 * Used by Server to create AckPackets. Contains only cksum, len, and ackno.
	 */
	public Packet (int ackno) {
		this.cksum = 0; //default value.
		this.len = 8; //default value.
		this.ackno = ackno;
	}
	
	public short getCksum() {
		return cksum;
	}
	public void setCksum() {
		if (cksum == 0){
			cksum = 1;
		}
	}
	public short getLen() {
		return len;
	}
	public void setLen(short len) {
		this.len = len;
	}
	public int getAckno() {
		return ackno;
	}
	public void setAckno(int ackno) {
		this.ackno = ackno;
	}
	public int getSeqno() {
		return seqno;
	}
	public void setSeqno(int seqno) {
		this.seqno = seqno;
	}
	public byte[] getData() {
		return data;
	}
	public void setData(byte[] data) {
		this.data = data;
	}

	/**
	 * 
	 * @param corruptChance the double value representing the percent chance of corrupting a packet.
	 * @return 	0: iff no corruption occurred to the Packet's properties
	 * 			1: iff no corruption occurred but the Packet will be drop and not sent via a DatagramSocket.
	 * 			2: iff Packet corrupted. Either the chksum or the byte[] data property will be appended some extra values.
	 */
	public synchronized int getStatus(double corruptChance) {
		int statusNum = corrupt(corruptChance);
		switch (statusNum) {
		case (1):
			return 1; // Packet will get dropped
		case (2):
			if (Math.random() <= .5) { // 50-50 chance to corrupt checksum or data length.
				setCksum(); // Corrupt Cksum.
			} 
			else {
				byte[] extraData = {1, 1};
				if (data == null) { // Used to corrupt AckPackets
					setData(extraData);
				} else { // Used to corrupt DataPackets
					//Add to end two extra bad data
					byte[] badData = new byte[data.length+2];
					badData[data.length] = extraData[0];
					badData[data.length+1] = extraData[1];
					setData(badData);
				}
			}
			return 2; // Packet got corrupted. Checksum changed to 1 or len changed (len now > 512 for
						// DataPacket or len now > 8 for AckPacket
		default:
			return 0; // No drop or corruption. Packet will be sent successfully
		}
	}
	
	/**
	 * Generates an int value to denote what corruption step is to be done on the Packet if any based on corruptChance.
	 * @param corruptChance the double value representing the percent chance of corrupting a packet. 
	 * @return 	0: iff no corruption occurred to the Packet's properties nor will Packet drop.
	 * 			1: iff no corruption occurred but the Packet will be drop and not sent via a DatagramSocket.
	 * 			2: iff Packet properties will get corrupted..
	 */
	public synchronized int corrupt(double corruptChance) {
		if (Math.random() < corruptChance) {
			if (Math.random() < .5) { 	// 50-50 chance to drop or corrupt data.
				return 1; 	// Packet will drop.
			} else {
				return 2;	// Packet will get corrupted.
			}
		} else {
			return 0;	// No corruption to Packet nor drop.
		}
	}
}