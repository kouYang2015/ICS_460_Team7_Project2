package edu.metrostate.Packet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class Packet implements Serializable{
	private static final long serialVersionUID = 5064345598182630522L;
	short cksum; //16-bit 2-byte 
	short len;	//16-bit 2-byte 
	int ackno;	//32-bit 4-byte 
	int seqno; //32-bit 4-byte Data packet Only 
	byte[] data; //0-500 bytes. Data packet only. Variable
	
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
		this.cksum = (short) (this.cksum == 0 ? 1 : 0);
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
	 * Turns the Packet object into a byte[].
	 * @param packet
	 * @return
	 * @throws IOException
	 */
	public byte[] toByteArray() throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
	    ObjectOutputStream oos = new ObjectOutputStream(bos);
	    oos.writeObject(this);
	    oos.flush();
	    byte [] dataWithHeader = bos.toByteArray();
		return dataWithHeader;
	}

	public int getStatus(double corruptChance) {
		int statusNum = corrupt(corruptChance);
		switch (statusNum) {
		case (1):
			return 1; // Packet will get dropped
		case (2):
			if (Math.random() < .5) { // 50-50 chance to corrupt checksum or data length.
				setCksum(); // Corrupt Cksum.
			} else {
				if (data == null) { // Used to corrupte AckPackets
					data = new byte[2];
				} else { // Used to corrupt DataPackets
					byte[] badData = new byte[this.data.length + 2];
					setData(badData);
				}
			}
			return 2; // Packet got corrupted. Checksum changed to 1 or len changed (len now > 512 for
						// DataPacket or len now > 8 for AckPacket
		default:
			return 0; // No drop or corruption. Packet will be sent successfully
		}
	}
	
	public int corrupt(double corruptChance) {
		if (Math.random() < corruptChance) {
			if (Math.random() < .5) {
				return 1;
			} else {
				return 2;
			}
		} else {
			return 0;
		}
	}
}