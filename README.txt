# ChatFusion

by : Barroux Léo/Rieutord Robin

Implementation of a TCP network protocol on Java.


## Creating them

### jar

We created 2 jar files, so that the user of our program could test it easily, without creating a client and a server directly from the code.

The jars are present in the repository "out/artifacts".

The command to launch both of them are :

java -jar ChatFusionServer.jar <port> <name>
java -jar ChatFusionClient.jar <name> <ip address> <port>

Here's an example of how to use the jar :
"java -jar ChatFusionServer.jar 7777 ChatFusion"
"java -jar ChatFusionClient.jar toto localhost 7777"

The server needs to be launched before the client.


## Using them

### Client

To connect to the server you chose with the ip address, in order to send message :
"LOGIN"

Once connected, to send message you juste have to type your message :
"<message>"

example :
"LOGIN"
"Hello World!"


### Server

To see all the connected client to the server :
"INFO"
