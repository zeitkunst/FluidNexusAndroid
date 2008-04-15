import socket
import select
import time
import md5
import lightblue 

from BridgeBluetooth import FluidNexusServer

if __name__ == "__main__":
    server = FluidNexusServer()
    server.run()
