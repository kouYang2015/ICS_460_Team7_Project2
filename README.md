# ICS_460_Team7_Project2 Stop and wait protocol over UDP

<h1> ToDo List </h1>

GUI/CMD Line
  - [X] -s option completed (fully implemented)
  - [ ] -t grabbed from command line and implemented but needs checking (sendPacket() method. datagramSocket.setSoTimout(timeout))
  - [ ] -d grabbed from command line but need to be implemented still
  - [X] inetAddress fully implemented (grabbed from cmd and variable is used in client)
  - [X] port fully implemented (grabbed from cmd and variable is used in client)

WindowFrames (Both Client/Server)
  - [X] Professor said its just the CMD windows. Ignore this. Also Frame size of 1 means we only send one packet at a time and then wait. If it was frame size 3, we would send 3 packets at a time and then wait.

Packets
  - [ ] implement ackPackets (On server side we send only chksum, len, and ackno with total size of 8)
  - [X] class created. getters/setters for all properties
  - [X] chksum is default to 0 (in constructor or?)
  - [X] setChksum (make it just flip our chksum instead of taking in a parameter? chksum = chksum == 0 ? 1 : 0;
  - [X] Check if our constructor for the client side is complete.
  - [X] Create a constructor for the server side to use to create a "ackpacket" Takes in only chksum, len, ackno (chksum default to 0, len always 8, ackno = class variable ackno from Server class)
  - [ ] Implement corruption so getStatus can return correct status.

Client Side
  - [ ] Implement Data Corruption called from Packet class
  - [ ] Implement Data drops called from Packet class
  - [ ] implement stop-and-wait feature (force client to stop sending packet - probably change conditional in the while loop) Not sure if we need to have the Server stop and wait too
  - [X] Method used to create a Packet given a file byte[], offset, seqno, ackno, and packet size
  - [X] Method used to turn Packet created into a byte[] with chksum, len, ackno, seqno, data. Using ByteArrayOutputStream, ObjectOutputStream (https://www.tutorialspoint.com/How-to-convert-an-object-to-byte-array-in-java)
  - [X] Increment seqno class variable by one when we receive a packet (ackPacket with length 8) from the server side
  - [ ] Send file name to server (In progress)
  - [ ] Implement all print statements

Server Side
  - [X] Receive file name from client and create a new file with that name
  - [ ] Implement Data Corruption
  - [ ] Implement Data drops
  - [ ] implement stop-and-wait feature (TBD on ServerSide - Should server always be listening? Does/Will Server keep sending ackPackets while waiting?)
  Checks
  - [ ] check the chksum for 0 (use ByteBuffer)
  - [ ] check the len and only append to outputfile the size of len from the received datagrampacket - (minus) the first 12 bytes (the header)
  - [ ] check if ackno and seqno received are the same? If not, this means our ack packet did not successfully go from server to client. 
  When Checks are good
  - [ ] remove first 12 byte from datagram packet received, then add to outputfile the size of len. Ensures we do not add more or less of what is the actual data (protects against corruption of data)
  - [ ] when successfully received and appended, increment ackno class variable by 1 and set it to a new packet to send back to client
  - [ ] Send Packet back with chksum, len, and ackno ONLY. ackno = ackno (from class variable)
  - [ ] Method used to create a Packet given a file byte[], offset, seqno, ackno, and packet size
  - [ ] Method used to turn Packet created into a byte[] with chksum, len, ackno, seqno, data. Using ByteArrayOutputStream, ObjectOutputStream (https://www.tutorialspoint.com/How-to-convert-an-object-to-byte-array-in-java)
  - [ ] Implement all print statements
