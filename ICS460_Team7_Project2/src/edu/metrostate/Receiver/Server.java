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
		int checkSum;
		int sentAckNoInt;
		long startTime = System.currentTimeMillis();
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
				Packet newPacket = deserializeByteArray(requestPacket);
				checkSum = newPacket.getCksum();
				sentAckNoInt = newPacket.getAckno();
				DatagramPacket responsePacket;
//				System.out.println(checkSum);
//				System.out.println(deserializeByteArray(requestPacket).getLen());
//				System.out.println(sentAckNoInt);
//				System.out.println(deserializeByteArray(requestPacket).getSeqno());
//				System.out.println(deserializeByteArray(requestPacket).getData().length);
				if (checkSum == 1 || newPacket.getLen() != newPacket.getData().length + 12) { //if requestPacket is corrupted
					printWhatWasReceived(startTime, 2);
				} else if (sentAckNoInt < ackNo) { //if duplicate seqNo packets are received
					printWhatWasReceived(startTime, 1);
					Packet dataPacket = createAckPacket(ackNo);
					int statusIdentifier = dataPacket.getStatus(corruptChance);

					switch (statusIdentifier) { // Right now it is set to return 0 only -> default.
					case (1): printResponse(startTime, 1); continue;
					case (2): printResponse(startTime, 2);
					default: printResponse(startTime, 0);
					}
					responsePacket = new DatagramPacket(dataPacket.toByteArray(), dataPacket.toByteArray().length,
							requestPacket.getAddress(), requestPacket.getPort());
					datagramSocket.send(responsePacket);
				} else { //if it is sent correctly
					printWhatWasReceived(startTime, 0);
					Packet dataPacket = createAckPacket(this.ackNo);
					writeToFile(fileReceived, deserializeByteArray(requestPacket).getData());
					int statusIdentifier = dataPacket.getStatus(corruptChance);
					
					switch (statusIdentifier) { // Right now it is set to return 0 only -> default.
					case (1): printResponse(startTime, 1); continue;
					case (2): printResponse(startTime, 2);
					default: printResponse(startTime, 0);
					}
					ackNo++;
					responsePacket = new DatagramPacket(dataPacket.toByteArray(), dataPacket.toByteArray().length,
							requestPacket.getAddress(), requestPacket.getPort());
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
		fileReceived = new File("image.jpg");	//Sets the new file to String of our decoded byte[]
	}
	
	/**
	 * 
	 * @param dp
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private synchronized Packet deserializeByteArray (DatagramPacket dp) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(dp.getData());
        ObjectInputStream ois = new ObjectInputStream(bis);
        Packet deserializedPacket = (Packet) ois.readObject();
        return deserializedPacket;
    }
	
	/**
	 * Creates a Packet that represents an ackPacket. 
	 * @return Packet: The Packet object that represents an ackPacket.
	 */
	private synchronized Packet createAckPacket(int ackNo) {
		Packet newPacket = new Packet(ackNo);
		return newPacket;
	}
	
	/**
	 * Method used to concatenate new DataPacket byte[] to the file.
	 * @param file: The file we want to add the new byte[] array to.
	 * @param data: the byte[] data we will write to the file.
	 */
	private synchronized void writeToFile(File file, byte[] data) {
		try {
			FileOutputStream writer = new FileOutputStream(file, true);
			writer.write(data, 0, data.length);
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private int corrupt() {
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
	
	private void printWhatWasReceived(long startTimer, int receivedCode) {
		String firstCode, errorCode;
		long timeReceived = System.currentTimeMillis() - startTimer;
		switch (receivedCode) {
		case (1): firstCode = "DUPL"; errorCode = "!Seq";
		case (2): firstCode = "RECV"; errorCode = "CRPT";
		default: firstCode = "RECV"; errorCode = "RECV";
		}
		System.out.println(firstCode + " " + timeReceived + " " + ackNo + " " + errorCode);
	}
	
	private void printResponse(long startTimer, int sentCode) {
		String errorCode;
		long timeSent = System.currentTimeMillis() - startTimer;
		switch (sentCode) {
		case (1): errorCode = "DROP";
		case (2): errorCode = "ERR";
		default: errorCode = "SENT";
		}
		System.out.println(String.format("%s %s %d %d %s", "SENDing", "ACK", ackNo, timeSent, errorCode));
	}
	
	public static void main(String[] args) throws IOException, ClassNotFoundException {
		double corruptchance = -1;
		InetAddress inetAddress = null;
		int port = -1;
		int i = 0;
		while (i < args.length - 1) {
			if (args[i].equals("-d")) {
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
		
		Server receiver = new Server(corruptchance, inetAddress, port);
		receiver.createFile();
		receiver.receivePacket();
	}
	
}
