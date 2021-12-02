package edu.metrostate.Receiver;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Base64;

import edu.metrostate.Packet.Packet;

public class Server {
	private static final int DEFAULT_PORT = 12345;
	private static final double DEFAULT_CORRUPTCHANCE = 0;
	private InetAddress inetAddress;
	private int port;
	private DatagramSocket datagramSocket;
	private byte[] buffer = new byte[1024];
	private int startOffset = 0;
	private int ackNo = 1;
	private File fileReceived;
	private double corruptChance;
	
	public Server(double corruptchance, InetAddress inetAddress, int port) throws SocketException {
		super();
		try {
			this.inetAddress = (inetAddress == null ? inetAddress = InetAddress.getLocalHost() : inetAddress);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		this.corruptChance = (corruptchance == -1 ? DEFAULT_CORRUPTCHANCE : corruptchance);
		this.port = (port == -1 ? DEFAULT_PORT : port);
		this.datagramSocket = new DatagramSocket(this.port);
		System.out.println(this.inetAddress.getHostName());
		System.out.println(this.port);
	}
	
	public void receivePacket() throws ClassNotFoundException {
		byte[] checkSumByte = new byte[2];
		byte[] lenByte = new byte[2];
		byte[] sentAckNo = new byte[4];
		byte[] sentSeqNo = new byte[4];
		int checkSum;
		int sentAckNoInt;
		byte[] dataReceived;
		while(true) {
			try {
				//Receive request and create a DatagramPacket. Then write it to file.
				DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);
				//TODO: ADD AN IF HERE TO BREAK OUT OF LOOP IF BUFFER LENTGTH IS 0
				System.out.println("Waiting to receive");
				datagramSocket.receive(requestPacket);
				System.out.println("Received Packet");
				if (requestPacket.getLength() == 0) {
					System.out.println("Flag packet:" + requestPacket.getData() + " " + requestPacket.getLength()); //TODO: DEBUG STATEMENT DELETE AFTER
					break;
				}
//				ByteBuffer bb = ByteBuffer.wrap(requestPacket.getData());
//				System.out.println(bb.remaining());
//                bb.get(checkSumByte, 0, checkSumByte.length); //grab checksum bytes
//                System.out.println(lenByte);
//                bb.get(lenByte, 0, lenByte.length); //grab length bytes
//                bb.get(sentAckNo, 0, sentAckNo.length); //grab ackNo bytes
//                bb.get(sentSeqNo, 0, sentSeqNo.length); //grab seqNo bytes
//                int convertLen = lenByte[1];
//                System.out.println(convertLen);
//                dataReceived = new byte[convertLen - 12];
//				System.out.println(dataReceived.length);
//                bb.get(dataReceived, 0, dataReceived.length); //grab the rest of the data, which is len -12 in
//				
//				checkSum = ByteBuffer.wrap(checkSumByte).getInt();
//				sentAckNoInt = ByteBuffer.wrap(sentAckNo).getInt();
				
				checkSum = deserializeByteArray(requestPacket).getCksum();
				sentAckNoInt = deserializeByteArray(requestPacket).getAckno();
				System.out.println(checkSum);
				System.out.println(deserializeByteArray(requestPacket).getLen());
				System.out.println(sentAckNoInt);
				System.out.println(deserializeByteArray(requestPacket).getSeqno());
				System.out.println(deserializeByteArray(requestPacket).getData().length);
				if (checkSum == 1) { //if requestPacket is corrupted
					
				} else if (sentAckNoInt < ackNo) { //if duplicate seqNo packets are received
					
				} else { //if it is sent correctly
					System.out.println("Sending response packet");
					Packet dataPacket = new Packet(ackNo);
					writeToFile(fileReceived, deserializeByteArray(requestPacket).getData());
					startOffset += requestPacket.getLength();
					ackNo++;
					DatagramPacket responsePacket = new DatagramPacket(dataPacket.turnIntoByteArrayServer(), dataPacket.getLen(), inetAddress, port);
					long startTime = System.currentTimeMillis();
					datagramSocket.send(responsePacket);
					System.out.println("Sent response packet");
				}
				
			} catch (IOException e) {
				e.printStackTrace();
				break;
			}
		}
	}
	
	/**
	 * Gets the file name and then creates a new file with that name.
	 */
	public void createFile() {
		DatagramPacket fileNamePacket = new DatagramPacket(buffer, buffer.length);
		try {
			datagramSocket.receive(fileNamePacket);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//byte[] to base64 string
		String encodedB64FileName = Base64.getEncoder().encodeToString(fileNamePacket.getData());
        // base64 string to byte[]
        byte[] decodeFileName = Base64.getDecoder().decode(encodedB64FileName);
        String safeFileName = new String(decodeFileName); //Build string of decoded.
		fileReceived = new File("new"+safeFileName);	//Sets the new file to String of our decoded byte[]
	}
	
	public Packet deserializeByteArray (DatagramPacket dp) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(dp.getData());
        ObjectInputStream ois = new ObjectInputStream(bis);
        Packet deserializedPacket = (Packet) ois.readObject();
        return deserializedPacket;
    }
	
	/**
	 * Method used to concatenate new requestPacket byte data to the previous requestPacket. Also prints to console information
	 * about packet#, start offset, end offset.
	 * @param file
	 * @param request
	 */
	public void writeToFile(File file, byte[] data) {
		System.out.println(String.format("[Packet%d] - [start byte offset]: %d - [end byte offset]: %d", 
				ackNo, startOffset, startOffset+data.length-1));
		try {
			FileOutputStream writer = new FileOutputStream(file, true);
			writer.write(data, 0, data.length);
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) throws IOException, ClassNotFoundException {
		double corruptchance = -1;
		InetAddress inetAddress = null;
		int port = -1;
		int i = 0;
		while (i < args.length - 1) {
			if (args[i].equals("-d")) {
				try {
					corruptchance = Integer.parseInt(args[i + 1]);
				} catch (NumberFormatException e) {
					System.out.println("Invalid packet corruption chance specified.");
					return;
				}
			} else {
				if (inetAddress == null) {
					inetAddress = InetAddress.getByAddress(args[i].getBytes());
				} else {
					port = Integer.parseInt(args[i]);
				}
				i++;
			}
		}
		
		Server receiver = new Server(corruptchance, inetAddress, port);
		receiver.createFile();
		receiver.receivePacket();
	}
	
}
