import socket
import select
import time
import md5
import lightblue 

FLUID_NEXUS_PROTOCOL_VERSION = '01'

ownerHashLength = 32
timestampLength = 13

########################################################################
#  FluidNexusServer
########################################################################
class FluidNexusServer(object):
    numberConnections = 2
    connections = []
    currentlyAccepting = False

    def __init__(self, serviceName = u'FluidNexus' ):
        """Initialize the server be setting up the server socket and advertising the FluidNexus service."""

        # Save our service name
        self.serviceName = serviceName

        # Setup our server socket
        self.serverSocket = lightblue.socket()
        self.serverSocket.bind(("", 0))
        self.serverSocket.listen(self.numberConnections)
        lightblue.advertise(self.serviceName, self.serverSocket, lightblue.RFCOMM)

        # Remove security protections
        # @TODO@ Make sure this actually does what we want it to do!
        #lightblue.set_security(self.serverSocket, 0)

    def run(self):
        """Main loop with blocking accept."""

        clientData = self.serverSocket.accept()

        # Get client info
        clientSocket = clientData[0]
        clientAddress = clientData[1]
        
        #####################################################
        #  Read header information
        #  ASSUME BIG ENDIAN BYTE ORDER!
        #####################################################
        
        # VERSION: 1 byte
        version = clientSocket.recv(2)
        
        # @TODO@
        # In the future, split here based on different versions
        
        # TITLE LENGTH: 2 bytes
        titleLength = clientSocket.recv(3)
    
        # MESSAGE LENGTH: 4 bytes
        # Note: this is to eventually support unicode text
        messageLength = clientSocket.recv(6)
    
        #####################################################
        #  Start reading data!
        #  ASSUME BIG ENDIAN BYTE ORDER!
        #####################################################
        timestamp = clientSocket.recv(timestampLength)
        # Skip cellID for now
        #cellID = clientSocket.recv(cellIDLength)
        ownerHash = clientSocket.recv(ownerHashLength)
        title = clientSocket.recv(int(titleLength))
        message = clientSocket.recv(int(messageLength))

        self.sendData(title, message)

    def sendData(self, title, message):
        print "going through send process"
        messageTime = str(time.time())
        self.clientSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.clientSocket.connect(('localhost', 7010))
        self.clientSocket.send(FLUID_NEXUS_PROTOCOL_VERSION)
        self.clientSocket.send("%03d" % len(title))
        self.clientSocket.send("%06d" % len(message))
        self.clientSocket.send(str(messageTime))
        self.clientSocket.send(md5.md5(title + message).hexdigest())
        self.clientSocket.send(title)
        self.clientSocket.send(message)
        self.clientSocket.close()

class FluidNexusClient(object):
    def __init__(self):
        """Initialize the client."""
        self.serverSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.serverSocket.bind(('localhost', 7030))
        self.serverSocket.listen(1)

    def run(self):
        while 1:
            print "waiting to accept"
            (thisClient, address) = self.serverSocket.accept()
            print "got client socket"
    
            mode = thisClient.recv(2)
            print mode
    
            if int(mode) == 0:
                self.getDevices(thisClient)
            elif int(mode) == 1:
                self.getServices(thisClient)
            elif int(mode) == 2:
                self.sendData(thisClient)

    def sendData(self, thisClient):
        titleLength = thisClient.recv(3)
        dataLength = thisClient.recv(6)
        title = thisClient.recv(int(titleLength))
        print title
        data = thisClient.recv(int(dataLength))
        print data
        timeValue = thisClient.recv(15)
        print timeValue
        hash = thisClient.recv(32)
        print hash

        thisClient.close()

        self.sendDataToPhone(title, data, timeValue, hash)

    def sendDataToPhone(self, title, data, timeValue, hash):
        """Send our data to the other phone!"""
        clientSocket = lightblue.socket()

        newTimeValue = timeValue[0:10] + ".00"

        # Connect to the other phone; perhaps we should consider grabbing some sort of lock to ensure that the connection happens
        try:
            print "trying to use client socket to connect"
            clientSocket.connect((self.phone, self.port))
        except Exception, e:
            print "unable to open client socket"
            clientSocket.close()
            return

        try:
            print "going through send process"
            clientSocket.send(FLUID_NEXUS_PROTOCOL_VERSION)
            time.sleep(1)
            clientSocket.send("%03d" % len(title))
            time.sleep(1)
            clientSocket.send("%06d" % len(data))
            time.sleep(1)
            clientSocket.send(newTimeValue)
            time.sleep(1)
            clientSocket.send(hash)
            time.sleep(1)
            clientSocket.send(title)
            time.sleep(1)
            clientSocket.send(data)
            time.sleep(1)
            clientSocket.close()
        except Exception, e:
            print e
            print "unable to send to server"

    def getServices(self, thisClient):
        print "starting service discovery"

        phone = thisClient.recv(17)
        print "looking at ", phone
        services = lightblue.findservices(phone)
        print services

        for service in services:
            print service[2]
            if service[2] is not None and service[2] == u'FluidNexus':
                self.phone = phone
                self.port = service[1]
                break
            else:
                self.port = None
        print "at end of service search"

        if self.port is not None:
            serverMessageHashes = self.getServerMessageHashes(services)
            numHashes = len(serverMessageHashes)
            thisClient.send("%02d" % numHashes)

            for hash in serverMessageHashes:
                print hash
                thisClient.send(hash.upper())
        else:
            thisClient.send("00")
        
        
        thisClient.close()

    def getServerMessageHashes(self, services):
        serverMessageHashes = []

        for service in services:
            if service[2][0] == ":":
                serverMessageHashes.append(service[2][1:])

        return serverMessageHashes
    def getDevices(self, thisClient):
        print "starting device discovery"
        devices = lightblue.finddevices()
       
        numDevices = len(devices)
        thisClient.send("%02d" % numDevices)
        for device in devices:
            deviceName = device[0]
            deviceClass = lightblue.splitclass(device[2])[1]
            thisClient.send(deviceName)
            thisClient.send(str(deviceClass))
        thisClient.close()

        
class ServerTest(object):
    def __init__(self):
        self.serverSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.serverSocket.bind(('localhost', 7001))
        self.serverSocket.listen(1)

        self.clientSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

    def read(self):
        print "Accepting connection"
        (clientSocket, address) = self.serverSocket.accept()
        data = clientSocket.recv(1024)
        print data
        clientSocket.close()

    def sendData(self, title, message):
        print "going through send process"
        messageTime = str(time.time())
        self.clientSocket.connect(('localhost', 7010))
        self.clientSocket.send(FLUID_NEXUS_PROTOCOL_VERSION)
        self.clientSocket.send("%03d" % len(title))
        self.clientSocket.send("%06d" % len(message))
        self.clientSocket.send(str(messageTime))
        self.clientSocket.send(md5.md5(title + message).hexdigest())
        self.clientSocket.send(title)
        self.clientSocket.send(message)
        self.clientSocket.close()

    def write(self):
        print "writing to android"
        self.clientSocket.connect(('localhost', 7010))

        self.clientSocket.send("00000000000000000000000000000000")
        self.clientSocket.send(str(time.time()))
        self.clientSocket.close()

if __name__ == "__main__":
    #server = FluidNexusServer()
    #server.run()
    client = FluidNexusClient()
    client.run()
