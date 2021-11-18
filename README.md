# ICS_460_Team7_Project2 Stop and wait protocol over UDP

<h1> ToDo List </h1>

GUI/CMD Line
  - [ ] -s option completed, -t and -d are grabbed from command line but need to be implemented still

WindowFrames (Both Client/Server)
  - [ ] TBD NEEDS DISCUSSION OF WHAT IS TO BE DONE

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
