package edu.metrostate.Packet;

import java.io.Serializable;

public class Packet implements Serializable{
	
	private static final long serialVersionUID = 5227846699483507257L;
	short cksum; //16-bit 2-byte 
	short len;	//16-bit 2-byte 
	int ackno;	//32-bit 4-byte 
	int seqno; //32-bit 4-byte Data packet Only 
	byte[] data; //0-500 bytes. Data packet only. Variable
	
	public Packet (int ackno, int seqno, int byteSize) {
		this.len = (short) (byteSize+12);
		this.ackno = ackno;
		this.seqno = seqno;
	}
	
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
	
}
