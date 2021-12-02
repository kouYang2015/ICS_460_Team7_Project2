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
	private static final int DEFAULT_TIMEOUT = 1000;
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
	
	/**
	 * Constructor used to initialize inetAddress, packetSize, timeout, corruptchance, port. If the param are null or = -1, then
	 * we use a DEFAULT value instead. 
	 * @param inetAddress the InetAddress the user specified for the DatagramPackets to use. Default: localhost
	 * @param packetSize the max size of the Packets we will send in each DatagramPackets.
	 * @param timeout the int representing in ms how long a DatagramSocket will wait during a receive() call.
	 * @param corruptchance the decimal value of the percent chance of corrupting a packet. Default: 0
	 * @param port the port number the user specified for the socket to listen on. Default: 12345
	 * @throws SocketException
	 */
	public Client(InetAddress inetAddress, int packetSize, int timeout, double corruptchance, int port)
			throws SocketException {
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
	 * @param file the file's name of the file to be sent to the Server.java.
	 */
	public synchronized void setFileContent(String file) {
		try {
			this.fileContent = Files.readAllBytes(new File(file).toPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Helper method. Streams a Packet object serializes it into a byte[] and returns it.
	 * @param packet the Packet object to be serialized.
	 * @return
	 * @throws IOException
	 */
	private synchronized byte[] serializeByteArray(Packet packet) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
	    ObjectOutputStream oos = new ObjectOutputStream(bos);
	    oos.writeObject(packet);
	    oos.flush();
	    byte [] dataWithHeader = bos.toByteArray();
		return dataWithHeader;
	}
	
	/**
	 * Helper method. Streams in a DatagramPacket object and deserializes it into a Packet object and returns it. 
	 * @param requestPacket The DatagramPacket containing the byte[] data to be deserialized into a Packet object.
	 * @return A Packet object that was sent in a DatagramPacket via a DatagramSocket.
	 * @throws IOException Thrown when we stream DatagramPacket object.
	 * @throws ClassNotFoundException Thrown when Packet.java class cannot be found.
	 */
	private synchronized Packet deserializeByteArray(DatagramPacket requestPacket) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(requestPacket.getData());
        ObjectInputStream ois = new ObjectInputStream(bis);
        Packet deserializedPacket = (Packet) ois.readObject();
        return deserializedPacket;
    }
	
	/**
	 * Method used to send a requestPacket from Client to Server. Use helper methods to create DataPackets, seriliaze them into
	 * byte[] to be sent in a DatagramPacket via a DatagramSocket.
	 * @throws IOException 
	 * @throws ClassNotFoundException 
	 * @throws SocketException
	 */
	public synchronized void sendPacket() throws IOException, ClassNotFoundException {
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
			if (statusIdentifier == 1) { //We drop. try to wait and receive. Should always go to timeout
					timedOut = receiveAckPacket(startTime);
					printSendStatus(statusIdentifier, startTime, timedOut);
					continue;
			}
			else if (statusIdentifier == 2) { // Corrupted but we still send
				requestPacket = new DatagramPacket(serializeByteArray(dataPacket), 
						serializeByteArray(dataPacket).length, inetAddress, port);
				datagramSocket.send(requestPacket);
				printSendStatus(statusIdentifier, startTime, timedOut);
			}
			else { //No corruption. Sending good packet
				requestPacket = new DatagramPacket(serializeByteArray(dataPacket), 
						serializeByteArray(dataPacket).length, inetAddress, port);
				datagramSocket.send(requestPacket);
				printSendStatus(statusIdentifier, startTime, timedOut);
			}
			//Check if we timedOut by validating AckPacket received.
			timedOut = receiveAckPacket(startTime);
		}
		 // Send an empty packet to denote no data left to send. (Our flag)
		DatagramPacket flagPacket = new DatagramPacket(new byte[0], 0, inetAddress, port);
		datagramSocket.send(flagPacket);
	}
	
	/**
	 * Helper Method. Sets the timeout for the datagramSocket call the receiver() to receive a DatagramPacket from the Server
	 * containing a serialized Packet representing the AckPacket.
	 * @param timerStartTime The current time in ms.
	 * @return a boolean value denoting if we received a corrupted or not AckPacket
	 * @throws SocketException
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private synchronized boolean receiveAckPacket(long timerStartTime) throws SocketException, IOException, ClassNotFoundException {
		try {
			datagramSocket.setSoTimeout(timeout);  //Sets timeout for receive() method. If timeout is reached, we continue with code.
			DatagramPacket responsePacket = new DatagramPacket(new byte[1024], 1024);
			datagramSocket.receive(responsePacket);
			if (checkAckPacket(responsePacket, timerStartTime)) { 
				startOffset += packetSize; 
				seqnoCounter++;
				return false; //Ackpacket pack is good. Timeout is false, increment seqnoCounter, incrementOffset
			}
			else {
				return true; //Ackpacket was corrupted or a dupe was found. Will timeout and resend same packet.
			}
		} catch (SocketTimeoutException e) { //Ackpacket was dropped on server side. Timeout and resend same packet
			System.out.println("Timedout " + seqnoCounter);
			return true;
		} 
	}
	
	/**
	 * This method checks if the AckPacket we received from the Server/Receiver is not corrupted, has the correct length of 8, 
	 * and makes sure the ackNo received is equal to the seqnoCounter+1 indicating that the server successfully received the 
	 * correct seqNo in the previous packet.
	 * @param ackPacket: a DatagramPacket that contains the byte[] of data received from the Server/Receiver.
	 * @return	true: iff the checkSum is 0, len is 8, and ackNo is seqnoCounter+1.
	 * 			false: iff the checkSum is not 0 or len is not 8 or ackNo is not seqnoCounter+1. 
	 * @throws IOException
	 * @throws ClassNotFoundException 
	 */
	private synchronized boolean checkAckPacket(DatagramPacket responsePacket, long timerStartTime) throws ClassNotFoundException, IOException {
		Packet ackPacket = deserializeByteArray(responsePacket);
		if (ackPacket.getData() == null && ackPacket.getCksum() == 0) { //Our len will be == 8
			if (ackPacket.getAckno() == seqnoCounter) {
				printAckReceiveStatus(ackPacket.getAckno() , 0);//Good AckPacket, move window
				return true;
			}
			else {
				printAckReceiveStatus(ackPacket.getAckno() , 2);// Non-Corrupted AckPacket but dupe. getAckNo < seqno
				return false;
			}
		}
		else { //Our packet was corrupted, extra data[] detected or checksum is not good or seqno != ackno return false
			printAckReceiveStatus(ackPacket.getAckno() , 1); //Corrupted AckPacket
			return false;
		}
	}
	
	/**
	 * Creates a Packet object using a file's byte[] beginning from an offset number. Then calls a constructor in Packet. 
	 * This Packet represents a DataPacket.
	 * @param file the whole byte[] of the file to be sent to the Server.
	 * @param offset the starting index of the file byte[].
	 * @param ackno initializes this.ackno.
	 * @param seqno initializes this.seqno.
	 * @param byteSize the size of this.data.
	 * @return the Packet representing a DataPacket.
	 */
	private synchronized Packet createDataPacket(byte[] file, int offset, int ackno, int seqno, int byteSize) {
		int dataIndex = 0; // Goes from 0 - byteSize
		byte[] data = new byte[byteSize];
		// Sets the byte beginning at file[offset] up to file[offset+byteSize] to data[dataIndex]
		for (int i = offset; i < offset+byteSize; i++) {
			data[dataIndex] = file[i]; 
			dataIndex++;
		}
		Packet newPacket = new Packet(ackno, seqno, data);
		return newPacket;
	}
	
	/**
	 * Helper Method. Used to print to console, what occurred to the DatagramPacket containing the serialized Packet object 
	 * representing a DataPacket.
	 * @param dataPackStatus the int value representing the status of the DataPacket after going through the corruption method.
	 * @param timerStartTime the current time in ms.
	 * @param timedOutStatus the status of if the timedOut was true or not. If true, we are resending. If false, we are sending
	 * 		a new seqno DataPacket.
	 */
	private synchronized void printSendStatus(int dataPackStatus, long timerStartTime, boolean timedOutStatus) {
		String packetStatus = "";
		long timeToSend = System.currentTimeMillis() - timerStartTime;
		if (dataPackStatus == 1) {
			packetStatus += "DROP";
		}
		else if (dataPackStatus == 2) {
			packetStatus += "ERR";
		}
		else if (dataPackStatus == 0) {
			packetStatus += "SENT";
		}
		String status = !timedOutStatus ? "SENDing" : "ReSend";
		System.out.println(String.format("%s %d %d : %d %d %s", 
				status, seqnoCounter, startOffset, startOffset+packetSize-1, timeToSend, packetStatus));	//seqno, startOffset : endOffset, time sent in ms, status of packet (Sent, drop, error)
	}
	
	/**
	 * Helper Method. Used to print information about the AckPacket received depending on the ackStatus.
	 * @param acknoRec the ackno from the AckPacket received.
	 * @param ackStatus the status of the validation of the AckPacket.
	 */
	private synchronized void printAckReceiveStatus(int acknoRec, int ackStatus) {
		String packetStatus = "";
		if (ackStatus == 0) {
			packetStatus += "MoveWnd"; //AckPacket accepted with no corruption.
		}
		else if (ackStatus == 1) { //AckPacket was corrupted.
			packetStatus += "ErrAck";
		}
		else if (ackStatus == 2) { //AckPacket dupe obtained.
			packetStatus += "DuplAck";
		}
		System.out.println(String.format("AckRcvd %d %s", acknoRec, packetStatus));	
	}
	
	/**
	 * Sends the filename obtain in a DatagramPacket to Server.java.
	 * @param fileName the filename obtain from user's input.
	 */
	private synchronized void sendFileName(String fileName) {
		DatagramPacket sendFileNamePacket = new DatagramPacket(fileName.getBytes(), fileName.getBytes().length,
				inetAddress, port);
		try {
			datagramSocket.send(sendFileNamePacket);
		} catch (IOException e) {
			System.out.println("FileNamePacket unsuccessful");
			e.printStackTrace();
		}
	}
	
	/**
	 * Main method. Used to call methods from Client.java to implement stop-and-wait UDP protocol.
	 * @param args the user's input received from the cmd line.
	 * @throws IOException thrown when inputs do not match Socket/OutputStream param or if IP Address is illegal length.
	 * @throws ClassNotFoundException Thrown when Packet.java class cannot be found.
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
					if (args[i].equals("localhost")) {
						inetAddress = InetAddress.getLocalHost();
					} else {
					inetAddress = InetAddress.getByAddress(args[i].getBytes());
					}
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
