package edu.metrostate.Receiver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Base64;

public class Server {
	private final static int PORT = 12345;
	private DatagramSocket datagramSocket;
	private byte[] buffer = new byte[1024];
	private int startOffset = 0;
	private int packetCounter = 1;
	private File fileReceived;
	
	public Server(DatagramSocket datagramSocket){
		this.datagramSocket = datagramSocket;
	}
	
	public void receivePacket() {
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
				writeToFile(fileReceived, requestPacket);
				startOffset += requestPacket.getLength();
				packetCounter++;
				
				//Create responsePacket and send back to client.
				DatagramPacket responsePacket = new DatagramPacket(requestPacket.getData(), requestPacket.getLength(), 
						requestPacket.getAddress(), requestPacket.getPort());
				datagramSocket.send(responsePacket);
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
				packetCounter, startOffset, startOffset+request.getLength()-1));
		try {
			FileOutputStream writer = new FileOutputStream(file, true);
			writer.write(request.getData(), 0, request.getLength());
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		try (DatagramSocket datagramSocket = new DatagramSocket(PORT)) {
			Server receiver = new Server(datagramSocket);
			receiver.createFile();
			receiver.receivePacket();
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}
	
}
