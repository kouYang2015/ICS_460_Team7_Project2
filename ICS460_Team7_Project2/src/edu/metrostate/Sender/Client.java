package edu.metrostate.Sender;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class Client {
	private static final int DEFAULT_PORT = 12345;
	private static final int DEFAULT_PACKET_SIZE = 200;
	private static final int DEFAULT_TIMEOUT = 2000;
	private static final double DEFAULT_CORRUPTCHANCE = 0;
	private int port;
	private int packetSize; //command line argument for packet size
	private int timeout; //timeout interval to know when to re-send packets
	private double corruptChance; //chance to corrupt the datagrams to send
	private DatagramSocket datagramSocket;
	private InetAddress inetAddress;
	private int startOffset = 0;	//offset byte counter. Dynamic
	private int seqnoCounter = 1;
	private byte[] fileContent;	//total number of bytes in file
	
	
	public Client(InetAddress inetAddress, int packetSize, int timeout, double corruptchance, int port) throws SocketException {
		super();
		try {
			this.inetAddress = (inetAddress == null ? inetAddress = InetAddress.getLocalHost() : inetAddress);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		this.packetSize = (packetSize == -1 ? DEFAULT_PACKET_SIZE : packetSize > 500 ? 500 : packetSize);
		this.timeout = (timeout == -1 ? DEFAULT_TIMEOUT : timeout);
		this.corruptChance = (corruptchance == -1 ? DEFAULT_CORRUPTCHANCE : corruptchance);
		this.port = (port == -1 ? DEFAULT_PORT : port);
		this.datagramSocket = new DatagramSocket(0);
		System.out.println(this.inetAddress.getHostName());
		System.out.println(this.port);
//		if (packetSize == -1) {
//			packetSize = DEFAULT_PACKET_SIZE;
//		} else if (packetSize > 500) {
//			packetSize = 500;
//		}
//		if (timeout == -1) {
//			this.timeout = DEFAULT_TIMEOUT;
//		} else {
//			this.timeout = timeout;
//		}
//		if (corruptchance == -1) {
//			this.corruptChance = DEFAULT_CORRUPTCHANCE;
//		} else {
//			this.corruptChance = corruptchance;
//		}
//		if (inetAddress == null) {
//			try {
//				inetAddress = InetAddress.getLocalHost();
//			} catch (UnknownHostException e) {
//				e.printStackTrace();
//			}
//		}
//		if (port == -1) {
//			this.port = DEFAULT_PORT;
//		}
	}
	
	/**
	 * Sets the byte[] array to the File we wish to send via the readAllBytes method.
	 * @param args
	 */
	public void setFileContent(String file) {
		try {
			this.fileContent = Files.readAllBytes(new File(file).toPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Method used to send a requestPacket from Client to Server.
	 * @throws IOException 
	 * 
	 * @throws SocketException
	 */
	public void sendPacket() throws IOException {
		boolean timedOut = false; //Use this boolean to determine if timed out? Resets to false after a successful AckPacket
		System.out.println("Content Length:" + fileContent.length + "\nbuffer Length: " + packetSize); // TODO: DEBUG STATEMENT DELETE AFTER
		while (true) {
			if (seqnoCounter > Math.ceil(fileContent.length / packetSize)) { //Check if we sent all necessary packets
				break;
			}
			if (fileContent.length - startOffset < packetSize) { //Checks if we can still send our packetSize. If not, reset packetSize to remaining fileContent length
				packetSize = fileContent.length - startOffset;
			}
			Packet dataPacket = createDataPacket(fileContent, startOffset, seqnoCounter, seqnoCounter, packetSize);
			System.out.println(dataPacket.turnIntoByteArrayClient().length);
			DatagramPacket requestPacket = new DatagramPacket(dataPacket.turnIntoByteArrayClient(), dataPacket.getLen(), inetAddress,
					port);
			System.out.println(requestPacket.getLength());
			int statusIdentifier = dataPacket.getStatus(corruptChance);

/*			switch (statusIdentifier) { // Right now it is set to return 0 only -> default.
			case (1): // TODO: Do nothing. Packet got dropped. Wait out timer and Resend same packet.
			case (2): // TODO: Send packet but it got corrupted. Use method printCorruptStatus
			default: // TODO: Send packet with no corruption.
			}*/

			long startTime = System.currentTimeMillis();
			datagramSocket.send(requestPacket);
			printSendStatus(requestPacket, statusIdentifier, startTime, timedOut);
			startOffset += packetSize;
			try {
				datagramSocket.setSoTimeout(timeout);  //Sets timeout for receive() method. If timeout is reached, we continue with code.
				DatagramPacket responsePacket = new DatagramPacket(new byte[packetSize], packetSize);
				datagramSocket.receive(responsePacket);
				if (checkAckPacket(responsePacket)) {
					seqnoCounter++;
					timedOut = false;
				}
				else {
					timedOut = true;
				}
			} catch (SocketException e) {
				timedOut = true;
				continue;
			} 
			// TODO: See if checkAckPacket method works before deleting.
//				ByteBuffer bb = ByteBuffer.wrap(responsePacket.getData());
//				byte[] checksumByte = new byte[2];
//				byte[] lenByte = new byte[2];
//				bb.get(checksumByte, 0, checksumByte.length);	//Gets the first bit then move buffer location to 2. (beginning of len)
//				bb.get(lenByte, 0, lenByte.length);	//Gets the 3rd,4th bit then move buffer location to 4. (beginning of ackNo)
//				if (ByteBuffer.wrap(checksumByte).getInt() == 0 && ByteBuffer.wrap(lenByte).getInt() == 8) {
//					//Get ackNo from AckPacket. Check against seqNo. if Equal, increment by one both seqnoCounter and acknoCounter
//					byte[] acknoByte = new byte[4];
//					bb.get(acknoByte, 0, acknoByte.length);
//					if(ByteBuffer.wrap(acknoByte).getInt() == seqnoCounter) { //TODO: Check if this is correct from https://mkyong.com/java/java-convert-byte-to-int-and-vice-versa/
//						seqnoCounter++;
//						acknoCounter = ByteBuffer.wrap(acknoByte).getInt() + 1;
//					}
//				}
		}
		DatagramPacket flagPacket = new DatagramPacket(new byte[0], 0, inetAddress, port);
		System.out.println("Sending empty packet: " + flagPacket.getLength()); //TODO: Debug print statement DELETE After
		datagramSocket.send(flagPacket); // Send an empty packet to denote no data left to send. (Our flag)
	}
	
	/**
	 * This method checks if the AckPacket we received from the Server/Receiver is not corrupted, has the correct length of 8, 
	 * and makes sure the ackNo received is equal to the seqnoCounter+1 indicating that the server successfully received the correct
	 * seqNo in the previous packet.
	 * @param ackPacket: a DatagramPacket that contains the byte[] of data received from the Server/Receiver.
	 * @return	true: iff the checkSum is 0, len is 8, and ackNo is seqnoCounter+1.
	 * 			false: iff the checkSum is not 0 or len is not 8 or ackNo is not seqnoCounter+1. 
	 */
	private synchronized boolean checkAckPacket(DatagramPacket ackPacket) {
		//TODO: Turn this into a method? Check the checksum of AckPacket and len == 8. If good, then check ackNo from packet against seqnoCounter.
		ByteBuffer bb = ByteBuffer.wrap(ackPacket.getData());
		byte[] checksumByte = new byte[2];
		byte[] lenByte = new byte[2];
		bb.get(checksumByte, 0, checksumByte.length);	//Gets the first bit then move buffer location to 2. (beginning of len)
		bb.get(lenByte, 0, lenByte.length);	//Gets the 3rd,4th bit then move buffer location to 4. (beginning of ackNo)
		if (ByteBuffer.wrap(checksumByte).getInt() == 0 && ByteBuffer.wrap(lenByte).getInt() == 8) {
			//Get ackNo from AckPacket. Check against seqNo. if Equal, increment by one both seqnoCounter and acknoCounter
			byte[] acknoByte = new byte[4]; //Last 4 bit are ackno
			bb.get(acknoByte, 0, acknoByte.length);
			if(ByteBuffer.wrap(acknoByte).getInt() == seqnoCounter) { //TODO: Check if this is correct from https://mkyong.com/java/java-convert-byte-to-int-and-vice-versa/
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Creates a Packet object using a file's byte[] beginning from an offset number.
	 * @param file
	 * @param offset
	 * @param ackno
	 * @param seqno
	 * @param byteSize
	 * @return
	 */
	private synchronized Packet createDataPacket(byte[] file, int offset, int ackno, int seqno, int byteSize) {
		ByteBuffer bb = ByteBuffer.wrap(file);
		byte[] data = new byte[byteSize];
		bb.get(data, 0, data.length);
		Packet newPacket = new Packet(ackno, seqno, data);
		return newPacket;
	}
	
	/**
	 * Method used to print to console, what occurred to the DatagramPacket.
	 * @param request: the DatagramPacket whose information we are printing.
	 * @param num: 
	 */
	private void printSendStatus(DatagramPacket request, int statusN, long timerStartTime, boolean timedOutStatus) {
		String packetStatus;
		long timeToSend = System.currentTimeMillis() - timerStartTime;
		switch(statusN){
		case(1): packetStatus = "DROP";
		case(2): packetStatus = "ERR";
		default: packetStatus = "SENT";
		}
		String status = !timedOutStatus ? "SENDing" : "ReSend";
		System.out.println(String.format("%s %d %d : %d %d %s", 
				status, seqnoCounter, startOffset, startOffset+request.getLength()-1, timeToSend, packetStatus));	//seqno, startOffset : endOffset, time sent in ms, status of packet (Sent, drop, error)
	}
	
	// TODO: IMPLEMENT ON SERVER SIDE send the file name string to server there first. May not need
	private void sendFileName(String fileName) {
		DatagramPacket sendFileNamePacket = new DatagramPacket(fileName.getBytes(), fileName.getBytes().length,
				inetAddress, DEFAULT_PORT);
		try {
			datagramSocket.send(sendFileNamePacket);
		} catch (IOException e) {
			System.out.println("FileNamePacket unsuccessful");
			e.printStackTrace();
		}
		System.out.println("Sent file: " + fileName);
	}
	
	/**
	 * Main method. Executes the rest of the program when user inputs the file from cmd line.
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		int packetSize = -1;
		int timeout = -1;
		double corruptchance = -1;
		int i = 0;
		InetAddress inetAddress = null;
		int port = -1;
		while (i < args.length - 1) {
			if (args[i].equals("-s")) {
				try {
					packetSize = Integer.parseInt(args[i + 1]);
				} catch (NumberFormatException e) {
					System.out.println("Invalid packet length specified.");
					return;
				}
				//TODO: Make an if statement to make sure packet length is between 0-500. Specified in documents. Throw exception if outside range.
				i += 2;
			} else if (args[i].equals("-t")) {
				try {
					timeout = Integer.parseInt(args[i + 1]);
				} catch (NumberFormatException e) {
					System.out.println("Invalid timeout length specified.");
					return;
				}
				i += 2;
			} else if (args[i].equals("-d")) {
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
		
			Client sender = new Client(inetAddress, packetSize, timeout, corruptchance, port);
			sender.setFileContent(args[i]);
			sender.sendFileName(args[i]);
			sender.sendPacket();
	}
	
}
