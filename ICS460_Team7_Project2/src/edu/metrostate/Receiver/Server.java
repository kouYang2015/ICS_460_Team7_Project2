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

	/**
	 * Constructor. initializes the corruptChance, inetAddress, and port if given. If not, uses a DEFAULT value.
	 * @param corruptchance the decimal value of the percent chance of corrupting a packet. Default: 0
	 * @param inetAddress the InetAddress the user specified for the DatagramPackets to use. Default: localhost
	 * @param port the port number the user specified for the socket to listen on. Default: 12345
	 * @throws SocketException thrown if we cannot bind a DatagramSocket to the specific port.
	 */
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

	/**
	 * Method used to receive DataPackets, write to file byte[] received, and send ackPackets.
	 * @throws ClassNotFoundException thrown when we cannot properly deserialize a DatagramPacket into a Packet object.
	 * @throws IOException thrown when inputs do not match Socket/OutputStream param.
	 */
	public synchronized void receivePacket() throws ClassNotFoundException, IOException {
		int seqnoRec;
		long startTime = System.currentTimeMillis();
		while (true) {
			// Create a DatagramPacket and receive request.
			DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length, inetAddress, port);
			datagramSocket.receive(requestPacket);
			if (requestPacket.getLength() == 0) {
				// Got our flag packet. End of program.
				break;
			}
			// Deserialize DatagramPacket object's byte[] data to obtain Packet object.
			Packet dataPacket = deserializeByteArray(requestPacket);
			seqnoRec = dataPacket.getSeqno();
			//Validate DataPacket
			if (dataPacket.getCksum() == 1 || dataPacket.getLen() != (dataPacket.getData().length + 12)) {
				// DataPacket is corrupted. Don't create and send AckPacket back.
				printDataPacket(startTime, 2, seqnoRec);
			} else if (seqnoRec < ackNo) {
				// Got a non corrupted duplicate seqNo packet.
				printDataPacket(startTime, 1, seqnoRec);
				// Resend AckPacket with seqnoRec to inform Client to increment seqno.
				sendAckPacket(requestPacket, seqnoRec, startTime);
			} else { 
				// Got a New good DataPacket. Increment ackNo by one.
				printDataPacket(startTime, 0, seqnoRec);
				//Write dataPacket byte[] data to fileReceived.
				writeToFile(fileReceived, dataPacket.getData());
				ackNo++;
				// Send AckPacket to Client.
				sendAckPacket(requestPacket, seqnoRec, startTime);
			}
		}
	}

	/**
	 * Helper Method. Used to create and send Packets representing an AckPacket to the Client to notify Server is ready for the
	 * next frame.
	 * @param requestPacket the DatagramPacket that contains the serialized Packet object representing a DataPacket.
	 * @param seqnoRec the seqno from the DataPacket received.
	 * @param startTime the current time in ms.
	 * @throws IOException thrown when inputs do not match OutputStream param during serialization of a Packet object.
	 */
	private synchronized void sendAckPacket(DatagramPacket requestPacket, int seqnoRec, long startTime) throws IOException {
		// Create a Packet representing an AckPacket
		Packet ackPacket = createAckPacket(seqnoRec);

		// Obtain status of corruption for ackPacket. May corrupt ackPacket with chance = -d.
		int statusIdentifier = ackPacket.getStatus(corruptChance);
		if (statusIdentifier == 1) {
			//ackPacket will be dropped. Exit method before sending responsePacket to Client. Will timeout on Client.
			printAckStatus(startTime, statusIdentifier, ackPacket.getAckno());
			return;
		}
		printAckStatus(startTime, statusIdentifier, ackPacket.getAckno());
		//Serialize ackPacket and send as a DatagramPacket even if corrupted.
		DatagramPacket responsePacket = new DatagramPacket(serializeByteArray(ackPacket),
				serializeByteArray(ackPacket).length, requestPacket.getAddress(), requestPacket.getPort());
		datagramSocket.send(responsePacket);
	}
	
	/**
	 * Gets the file name and then creates a new file with that name.
	 */
	public synchronized void createFile() {
		DatagramPacket fileNamePacket = new DatagramPacket(buffer, buffer.length, inetAddress, port);
		try {
			datagramSocket.receive(fileNamePacket);
		} catch (IOException e) {
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
	 * Helper method. Streams a Packet object serializes it into a byte[] and returns it.
	 * @param packet
	 * @return A byte[] representing a Packet object with it's serialized number.
	 * @throws IOException Thrown when we stream Packet object.
	 */
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
	 * @param ackNo The ackNo that we want the ackPacket to contain for comparison on Client.java.
	 * @return Packet A Packet object that represents an ackPacket.
	 */
	private synchronized Packet createAckPacket(int ackNo) {
		Packet newPacket = new Packet(ackNo);
		return newPacket;
	}

	/**
	 * Method used to concatenate new DataPacket byte[] to the file.
	 * 
	 * @param file The file we want to add the new byte[] array to.
	 * @param data the byte[] data we will write to the file.
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

	/**
	 * Helper Method. Used to print information from a received DataPacket depending on the recStatus.
	 * @param startTimer The current time in ms.
	 * @param recStatus The DataPacket's validation status.
	 * @param seqnoRec the seqno from the DataPacket.
	 */
	private synchronized void printDataPacket(long startTimer, int recStatus, int seqnoRec) {
		String firstCode = "", errorCode = "";
		long timeReceived = System.currentTimeMillis() - startTimer;
		if (recStatus == 1) {
			// Received a non-corrupted duplicate DataPacket.
			firstCode += "DUPL";
			errorCode += "!Seq";
		} else if (recStatus == 2) {
			// Received a corrupted DataPacket.
			firstCode += "RECV";
			errorCode += "CRPT";
		} else {
			// Received a non-corrupted new DataPacket.
			firstCode += "RECV";
			errorCode += "RECV";
		}
		System.out.println(String.format("%s %d %d %s", firstCode, timeReceived, seqnoRec, errorCode));
	}

	/**
	 * Helper Method. Used to print information about the AckPacket to be sent depending on the ackStatus.
	 * @param startTimer The current time in ms.
	 * @param ackStatus The DataPacket's validation status.
	 * @param ackPackNo the ackno that was sent with the DataPacket.
	 */
	private synchronized void printAckStatus(long startTimer, int ackStatus, int ackPackNo) {
		String errorCode = "";
		long timeSent = System.currentTimeMillis() - startTimer;
		if (ackStatus == 1) {
			errorCode += "DROP";
		} else if (ackStatus == 2) {
			errorCode += "ERR";
		} else {
			errorCode += "SENT";
		}
		System.out.println(String.format("%s %s %d %d %s", "SENDing", "ACK", ackPackNo, timeSent, errorCode));
	}

	/**
	 * Main Method. Used to call methods from Server.java to implement stop-and-wait UDP protocol.
	 * @param args the user's input received from the cmd line.
	 * @throws IOException thrown when inputs do not match Socket/OutputStream param or if IP Address is illegal length.
	 * @throws ClassNotFoundException Thrown when Packet.java class cannot be found.
	 */
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

		Server receiver = new Server(corruptchance, inetAddress, port);
		receiver.createFile();
		receiver.receivePacket();
	}

}
