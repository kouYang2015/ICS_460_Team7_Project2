package edu.metrostate.Packet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class Packet implements Serializable{
	
	private static final long serialVersionUID = 5227846699483507257L;
	short cksum; //16-bit 2-byte 
	short len;	//16-bit 2-byte 
	int ackno;	//32-bit 4-byte 
	int seqno ; //32-bit 4-byte Data packet Only 
	byte[] data = new byte[500]; //0-500 bytes. Data packet only. Variable
	
	public short getCksum() {
		return cksum;
	}
	public void setCksum(short cksum) {
		this.cksum = cksum;
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
	
	public byte[] turnIntoByteArray (Packet packet) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
	    ObjectOutputStream oos = new ObjectOutputStream(bos);
	    oos.writeObject(packet);
	    oos.flush();
	    byte [] dataWithHeader = bos.toByteArray();
		return dataWithHeader;
	}
}
