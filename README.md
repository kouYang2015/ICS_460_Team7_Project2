# ICS_460_Team7_Project2 Stop and wait protocol over UDP

<h1> ToDo List </h1>

GUI/CMD Line
  - [X] -s option completed (fully implemented)
  - [ ] -t grabbed from command line but need to be implemented still 
  - [ ] -d grabbed from command line but need to be implemented still

WindowFrames (Both Client/Server)
  - [X] Professor said its just the CMD windows. Ignore this. Also Frame size of 1 means we only send one packet at a time and then wait. If it was frame size 3, we would send 3 packets at a time and then wait.

AckPackets
  - [ ] implement ackPackets (will it be its own class? lets discuss this)

Client Side
  - [ ] Implement Data Corruption
  - [ ] Implement Data drops
  - [ ] implement stop-and-wait feature (force client to stop sending packet - probably change conditional in the while loop) Not sure if we need to have the Server stop and wait too

Server Side
  - [ ] Implement Data Corruption
  - [ ] Implement Data drops
  - [ ] implement stop-and-wait feature (TBD on ServerSide - Should server always be listening? Does/Will Server keep sending ackPackets while waiting?)
