package edu.metrostate.Receiver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Base64;

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
	}
	
	public void receivePacket() {
		byte[] checkSumByte = new byte[2];
		byte[] lenByte = new byte[4];
		byte[] sentAckNo = new byte[4];
		byte[] sentSeqNo = new byte[4];
		int checkSum;
		int sentAckNoInt;
		while(true) {
			try {
				//Receive request and create a DatagramPacket. Then write it to file.
				DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);
				//TODO: ADD AN IF HERE TO BREAK OUT OF LOOP IF BUFFER LENTGTH IS 0
				datagramSocket.receive(requestPacket);
				if (requestPacket.getLength() == 0) {
					System.out.println("Flag packet:" + requestPacket.getData() + " " + requestPacket.getLength()); //TODO: DEBUG STATEMENT DELETE AFTER
					break;
				}
				ByteBuffer bb = ByteBuffer.wrap(requestPacket.getData());
				bb.get(checkSumByte, 0, checkSumByte.length); //grab checksum bytes
				bb.get(lenByte, 0, lenByte.length); //grab length bytes
				bb.get(sentAckNo, 0, sentAckNo.length); //grab ackNo bytes
				bb.get(sentSeqNo, 0, sentSeqNo.length); //grab seqNo bytes
				bb.get(buffer, 0, ByteBuffer.wrap(lenByte).getInt() - 12); //grab the rest of the data, which is len -12 in length
				
				checkSum = ByteBuffer.wrap(checkSumByte).getInt();
				sentAckNoInt = ByteBuffer.wrap(sentAckNo).getInt();
				
				if (checkSum == 1) { //if requestPacket is corrupted
					
				} else if (sentAckNoInt < ackNo) { //if duplicate seqNo packets are received
					
				} else { //if it is sent correctly
					Packet dataPacket = new Packet(ackNo);
					writeToFile(fileReceived, requestPacket);
					startOffset += requestPacket.getLength();
					ackNo++;
					DatagramPacket responsePacket = new DatagramPacket(dataPacket.turnIntoByteArray(), dataPacket.getLen(), inetAddress, port);
					long startTime = System.currentTimeMillis();
					datagramSocket.send(responsePacket);
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
		fileReceived = new File(safeFileName);	//Sets the new file to String of our decoded byte[]
	}
	
	/**
	 * Method used to concatenate new requestPacket byte data to the previous requestPacket. Also prints to console information
	 * about packet#, start offset, end offset.
	 * @param file
	 * @param request
	 */
	public void writeToFile(File file, DatagramPacket request) {
		System.out.println(String.format("[Packet%d] - [start byte offset]: %d - [end byte offset]: %d", 
				ackNo, startOffset, startOffset+request.getLength()-1));
		try {
			FileOutputStream writer = new FileOutputStream(file, true);
			writer.write(request.getData(), 0, request.getLength());
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) throws IOException {
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
