package edu.metrostate.Receiver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
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
		int seqnoRec;
		long startTime = System.currentTimeMillis();
		while (true) {
			try {
				// Receive request and create a DatagramPacket. Then write it to file.
				DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);
				datagramSocket.receive(requestPacket);
				if (requestPacket.getLength() == 0) {
					System.out.println("Flag packet:" + requestPacket.getData() + " " + requestPacket.getLength()); //TODO:Delete
					break;
				}
				Packet newPacket = deserializeByteArray(requestPacket);
				checkSum = newPacket.getCksum();
				seqnoRec = newPacket.getSeqno();
				DatagramPacket responsePacket;
//				System.out.println(checkSum); //TODO:Delete
//				System.out.println("len is " + newPacket.getLen());
//				System.out.println(sentAckNoInt);
//				System.out.println(deserializeByteArray(requestPacket).getSeqno());
//				System.out.println("data length is " + newPacket.getData().length);
				if (checkSum == 1 || newPacket.getLen() != (newPacket.getData().length + 12)) { 
					//DataPacket is corrupted. Don't create and send AckPacket back.
					printDataPacket(startTime, 2, seqnoRec);
				} else if (seqnoRec < ackNo) { 
					// Got a non corrupted duplicate seqNo packet.
					printDataPacket(startTime, 1, seqnoRec);
					
					//Resend AckPacket with seqnoRec to inform Client to increment seqno.
					sendAckPacket(requestPacket, seqnoRec, startTime);
//					Packet dataPacket = createAckPacket(seqnoRec);
//					
//					int statusIdentifier = dataPacket.getStatus(corruptChance);
//					if (statusIdentifier == 1) {
//						printAckStatus(startTime, statusIdentifier, dataPacket.getAckno());
//						continue;
//					} else {
//						printAckStatus(startTime, statusIdentifier, dataPacket.getAckno());
//					}
//					responsePacket = new DatagramPacket(serializeByteArray(dataPacket),
//							serializeByteArray(dataPacket).length, requestPacket.getAddress(), requestPacket.getPort());
//					datagramSocket.send(responsePacket);
				} else { // got new good packet. increment ackNo by one. so we can check if next packet
							// is dupe (ackNo > seqNoReceived)
					printDataPacket(startTime, 0, seqnoRec);
					if (checkSum == 0 && newPacket.getLen() == (newPacket.getData().length + 12) && seqnoRec == ackNo) {
						System.out.println("Good packet received");
					}
					Packet dataPacket = createAckPacket(seqnoRec); // Create dataPacket with ackNo == seqNo we received
																	// to acknowledge pack on client side
					writeToFile(fileReceived, newPacket.getData());
					ackNo++; // Increment ackNo no matter what before doing switch case.
					
					//Send AckPacket.
					sendAckPacket(requestPacket, seqnoRec, startTime);
//					int statusIdentifier = dataPacket.getStatus(corruptChance);
//					
//					if (statusIdentifier == 1) {
//						printAckStatus(startTime, statusIdentifier, dataPacket.getAckno());
//						continue;// got dropped. dont send
//					} else {
//						printAckStatus(startTime, statusIdentifier, dataPacket.getAckno()); // Corrupted or good still
//																							// send. no continue
//					}
//					responsePacket = new DatagramPacket(serializeByteArray(dataPacket),
//							serializeByteArray(dataPacket).length, requestPacket.getAddress(), requestPacket.getPort());
//					datagramSocket.send(responsePacket);
				}
			} catch (IOException e) {
				e.printStackTrace();
				break;
			}
		}
	}

	private synchronized void sendAckPacket(DatagramPacket requestPacket, int seqnoRec, long startTime) throws IOException {
		Packet dataPacket = createAckPacket(seqnoRec);
		
		int statusIdentifier = dataPacket.getStatus(corruptChance);
		if (statusIdentifier == 1) {
			printAckStatus(startTime, statusIdentifier, dataPacket.getAckno());
			return;
		} else {
			printAckStatus(startTime, statusIdentifier, dataPacket.getAckno());
		}
		DatagramPacket responsePacket = new DatagramPacket(serializeByteArray(dataPacket),
				serializeByteArray(dataPacket).length, requestPacket.getAddress(), requestPacket.getPort());
		datagramSocket.send(responsePacket);
	}
	
	/**
	 * Gets the file name and then creates a new file with that name.
	 */
	public synchronized void createFile() {
		DatagramPacket fileNamePacket = new DatagramPacket(buffer, buffer.length);
		try {
			datagramSocket.receive(fileNamePacket);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// byte[] to base64 string
		String encodedB64FileName = Base64.getEncoder().encodeToString(fileNamePacket.getData());
		// base64 string to byte[]
		byte[] decodeFileName = Base64.getDecoder().decode(encodedB64FileName);
		String safeFileName = new String(decodeFileName); // Build string of decoded.
		fileReceived = new File("image.jpg"); // Sets the new file to String of our decoded byte[]
	}

	/**
	 * 
	 * @param dp
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private synchronized Packet deserializeByteArray(DatagramPacket dp) throws IOException, ClassNotFoundException {
		ByteArrayInputStream bis = new ByteArrayInputStream(dp.getData());
		ObjectInputStream ois = new ObjectInputStream(bis);
		Packet deserializedPacket = (Packet) ois.readObject();
		return deserializedPacket;
	}

	private synchronized byte[] serializeByteArray(Packet packet) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bos);
		oos.writeObject(packet);
		oos.flush();
		byte[] dataWithHeader = bos.toByteArray();
		return dataWithHeader;
	}

	/**
	 * Creates a Packet that represents an ackPacket.
	 * 
	 * @return Packet: The Packet object that represents an ackPacket.
	 */
	private synchronized Packet createAckPacket(int ackNo) {
		Packet newPacket = new Packet(ackNo);
		return newPacket;
	}

	/**
	 * Method used to concatenate new DataPacket byte[] to the file.
	 * 
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

	private synchronized void printDataPacket(long startTimer, int receivedCode, int seqnoRec) {
		String firstCode = "", errorCode = "";
		long timeReceived = System.currentTimeMillis() - startTimer;
		System.out.println(receivedCode);
		if (receivedCode == 1) {
			firstCode += "DUPL";
			errorCode += "!Seq";
		} else if (receivedCode == 2) {
			firstCode += "RECV";
			errorCode += "CRPT";
		} else {
			firstCode += "RECV";
			errorCode += "RECV";
		}
		System.out.println(String.format("%s %d %d %s", firstCode, timeReceived, seqnoRec, errorCode));
	}

	private synchronized void printAckStatus(long startTimer, int sentCode, int ackPackNo) {
		String errorCode = "";
		long timeSent = System.currentTimeMillis() - startTimer;
		if (sentCode == 1) {
			errorCode += "DROP";
		} else if (sentCode == 2) {
			errorCode += "ERR";
		} else {
			errorCode += "SENT";
		}
		System.out.println(String.format("%s %s %d %d %s", "SENDing", "ACK", ackPackNo, timeSent, errorCode));
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
