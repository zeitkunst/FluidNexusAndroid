import socket
import select

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

    def write(self):
        print "writing to android"
        self.clientSocket.connect(('localhost', 7000))

        self.clientSocket.send("this is from outside android")
        self.clientSocket.close()

if __name__ == "__main__":
    server = ServerTest()
    server.write()
