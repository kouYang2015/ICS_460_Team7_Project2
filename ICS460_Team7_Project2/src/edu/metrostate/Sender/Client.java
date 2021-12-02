package edu.metrostate.Sender;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;

import edu.metrostate.Packet.Packet;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class Client {
	private static final int DEFAULT_PORT = 12345;
	private static final int DEFAULT_PACKET_SIZE = 400;
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
	 * Turns the Packet object into a byte[].
	 * @param packet
	 * @return
	 * @throws IOException
	 */
	public byte[] turnIntoByteArrayClient(Packet packet) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
	    ObjectOutputStream oos = new ObjectOutputStream(bos);
	    oos.writeObject(packet);
	    oos.flush();
	    byte [] dataWithHeader = bos.toByteArray();
		return dataWithHeader;
	}
	
	/**
	 * Method used to send a requestPacket from Client to Server.
	 * @throws IOException 
	 * @throws ClassNotFoundException 
	 * 
	 * @throws SocketException
	 */
	public void sendPacket() throws IOException, ClassNotFoundException {
		boolean timedOut = false; //Use this boolean to determine if timed out? Resets to false after a successful AckPacket
		long startTime = System.currentTimeMillis();
		DatagramPacket requestPacket;
		while (true) {
			if (seqnoCounter > Math.ceil(fileContent.length / packetSize)) { //Check if we sent all necessary packets
				break;
			}
			if (fileContent.length - startOffset < packetSize) { //Checks if we can still send our packetSize. If not, reset packetSize to remaining fileContent length
				packetSize = fileContent.length - startOffset;
			}
			Packet dataPacket = createDataPacket(fileContent, startOffset, seqnoCounter, seqnoCounter, packetSize);
			int statusIdentifier = dataPacket.getStatus(corruptChance);
			if (statusIdentifier == 1) {
				try {
					Thread.sleep(timeout);
					timedOut = true;
					continue;
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			else {
				requestPacket = new DatagramPacket(turnIntoByteArrayClient(dataPacket), 
						turnIntoByteArrayClient(dataPacket).length, inetAddress, port);
				datagramSocket.send(requestPacket);
				printSendStatus(requestPacket, statusIdentifier, startTime, timedOut);
			}
			timedOut = receiveAckPacket();
		}
		DatagramPacket flagPacket = new DatagramPacket(new byte[0], 0, inetAddress, port);
		System.out.println("Sending empty packet: " + flagPacket.getLength()); //TODO: Debug print statement DELETE After
		datagramSocket.send(flagPacket); // Send an empty packet to denote no data left to send. (Our flag)
	}
	
	public boolean receiveAckPacket() throws SocketException, IOException, ClassNotFoundException {
		try {
			datagramSocket.setSoTimeout(timeout);  //Sets timeout for receive() method. If timeout is reached, we continue with code.
			DatagramPacket responsePacket = new DatagramPacket(new byte[1024], 1024);
			datagramSocket.receive(responsePacket);
			System.out.println(checkAckPacket(responsePacket));
			if (checkAckPacket(responsePacket)) {
				startOffset += packetSize;
				seqnoCounter++;
				return false;
			}
			else {
				return true;
			}
		} catch (SocketTimeoutException e) {
			return true;
		} 
	}
	
	public Packet deserializeByteArray (DatagramPacket dp) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(dp.getData());
        ObjectInputStream ois = new ObjectInputStream(bis);
        Packet deserializedPacket = (Packet) ois.readObject();
        return deserializedPacket;
    }
	
	/**
	 * This method checks if the AckPacket we received from the Server/Receiver is not corrupted, has the correct length of 8, 
	 * and makes sure the ackNo received is equal to the seqnoCounter+1 indicating that the server successfully received the correct
	 * seqNo in the previous packet.
	 * @param ackPacket: a DatagramPacket that contains the byte[] of data received from the Server/Receiver.
	 * @return	true: iff the checkSum is 0, len is 8, and ackNo is seqnoCounter+1.
	 * 			false: iff the checkSum is not 0 or len is not 8 or ackNo is not seqnoCounter+1. 
	 * @throws IOException
	 * @throws ClassNotFoundException 
	 */
	private synchronized boolean checkAckPacket(DatagramPacket responsePacket) throws ClassNotFoundException, IOException {
		Packet ackPacket = deserializeByteArray(responsePacket);
		if (ackPacket.getData() == null) {
			if (ackPacket.getCksum() == 0 && ackPacket.getLen() == 8) {
				if(ackPacket.getAckno() >= seqnoCounter) {
					return true;
				}
			}
			return false;
		}
		else {
			if (ackPacket.getCksum() == 0 && ackPacket.getLen() == ackPacket.toByteArray().length) {
				if(ackPacket.getAckno() >= seqnoCounter) {
					return true;
				}
			}
			return false;
		}
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
		int dataIndex = 0;
		byte[] data = new byte[byteSize];
		for (int i = offset; i < offset+byteSize; i++) {
			data[dataIndex] = file[i];
			dataIndex++;
		}
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
				status, seqnoCounter, startOffset, startOffset+packetSize-1, timeToSend, packetStatus));	//seqno, startOffset : endOffset, time sent in ms, status of packet (Sent, drop, error)
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
		System.out.println("Sent filepath: " + fileName);
	}
	
	/**
	 * Main method. Executes the rest of the program when user inputs the file from cmd line.
	 * @param args
	 * @throws IOException 
	 * @throws ClassNotFoundException 
	 */
	public static void main(String[] args) throws IOException, ClassNotFoundException {
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
					corruptchance = Double.parseDouble(args[i + 1]);
				} catch (NumberFormatException e) {
					System.out.println("Invalid packet corruption chance specified.");
					return;
				}
				i += 2;
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
