package cubyz.multiplayer;

import cubyz.Constants;
import cubyz.client.Cubyz;
import cubyz.multiplayer.server.Server;
import cubyz.multiplayer.server.User;
import cubyz.utils.Logger;
import cubyz.utils.datastructures.IntSimpleList;
import cubyz.utils.datastructures.SimpleList;
import cubyz.utils.math.Bits;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;

public class UDPConnection {
	private static final int MAX_PACKET_SIZE = 65507; // max udp packet size
	private static final int IMPORTANT_HEADER_SIZE = 5;
	private static final int MAX_IMPORTANT_PACKET_SIZE = 1500 - 20 - 8; // Ethernet MTU minus IP header minus udp header

	public static int packets_sent = 0;
	public static int packets_resent = 0;

	private final UDPConnectionManager manager;

	final InetAddress remoteAddress;
	int remotePort;
	boolean bruteforcingPort;
	private int bruteForcedPortRange = 0;

	private final byte[] streamBuffer = new byte[MAX_IMPORTANT_PACKET_SIZE];
	private int streamPosition = IMPORTANT_HEADER_SIZE;
	private int messageID = 0;
	private final SimpleList<UnconfirmedPackage> unconfirmedPackets = new SimpleList<>(new UnconfirmedPackage[1024]);
	private final IntSimpleList[] receivedPackets = new IntSimpleList[]{new IntSimpleList(), new IntSimpleList(), new IntSimpleList()}; // Resend the confirmation 3 times, to make sure the server doesn't need to resend stuff.
	private final byte[][] lastReceivedPackets = new byte[65536][];
	private int lastIndex = 0;

	int lastIncompletePacket = 0;


	int lastKeepAliveSent = 0, lastKeepAliveReceived = 0, otherKeepAliveReceived = 0;

	protected boolean disconnected = false;
	public boolean handShakeComplete = false;

	public long lastConnection = System.currentTimeMillis();

	public UDPConnection(UDPConnectionManager manager, String ipPort) {
		if(ipPort.contains("?")) {
			bruteforcingPort = true;
			ipPort = ipPort.replaceAll("\\?", "");
		} else {
			bruteforcingPort = false;
		}
		String[] ipPortSplit = ipPort.split(":");
		String ipOnly = ipPortSplit[0];
		if(ipPortSplit.length == 2) {
			remotePort = Integer.parseInt(ipPortSplit[1]);
		} else {
			remotePort = Constants.DEFAULT_PORT;
		}

		Logger.debug(ipOnly+":"+remotePort);
		this.manager = manager;
		manager.addConnection(this);

		//connect
		InetAddress remoteAddress = null;
		try {
			remoteAddress = InetAddress.getByName(ipOnly);
		} catch (IOException e) {
			Logger.error(e);
		}
		this.remoteAddress = remoteAddress;
	}

	private void flush() {
		synchronized(streamBuffer) {
			if(streamPosition == IMPORTANT_HEADER_SIZE) return; // Don't send empty packets.
			// Fill the header:
			streamBuffer[0] = (byte)0xff;
			int ID = messageID++;
			if(ID == -1) { // :
				Logger.crash("Well you managed to stay online for too long. Terabytes of data were sent. That's beyond what Cubyz was designed to handle.");
				disconnect();
			}
			Bits.putInt(streamBuffer, 1, ID);

			DatagramPacket packet = new DatagramPacket(Arrays.copyOf(streamBuffer, streamPosition), streamPosition, remoteAddress, remotePort);
			synchronized(unconfirmedPackets) {
				unconfirmedPackets.add(new UnconfirmedPackage(packet, lastKeepAliveSent, ID));
			}
			packets_sent++;
			manager.send(packet);

			streamPosition = IMPORTANT_HEADER_SIZE;
		}
	}

	private void writeByteToStream(byte data) {
		streamBuffer[streamPosition++] = data;
		if(streamPosition == streamBuffer.length) {
			flush();
		}
	}

	public void sendImportant(Protocol source, byte[] data) {
		sendImportant(source, data, 0, data.length);
	}

	public void sendImportant(Protocol source, byte[] data, int offset, int length) {
		if(disconnected) return;
		synchronized(streamBuffer) {
			writeByteToStream(source.id);
			int processedLength = length;
			while(processedLength > 0x7f) {
				writeByteToStream((byte)((processedLength & 0x7f) | 0x80));
				processedLength >>>= 7;
			}
			writeByteToStream((byte)processedLength);

			while(length != 0) {
				int copyableSize = Math.min(length, streamBuffer.length - streamPosition);
				System.arraycopy(data, offset, streamBuffer, streamPosition, copyableSize);
				streamPosition += copyableSize;
				length -= copyableSize;
				offset += copyableSize;
				if(streamPosition == streamBuffer.length) {
					flush();
				}
			}
		}
	}

	public void sendUnimportant(Protocol source, byte[] data) {
		sendUnimportant(source, data, 0, data.length);
	}

	public void sendUnimportant(Protocol source, byte[] data, int offset, int length) {
		if(disconnected) return;
		assert(length + 1 < MAX_PACKET_SIZE) : "Package is too big. Please split it into smaller packages.";
		byte[] fullData = new byte[length + 1];
		fullData[0] = source.id;
		System.arraycopy(data, offset, fullData, 1, length);
		manager.send(new DatagramPacket(fullData, fullData.length, remoteAddress, remotePort));
	}

	private void receiveKeepAlive(byte[] data, int offset, int length) {
		otherKeepAliveReceived = Bits.getInt(data, offset);
		lastKeepAliveReceived = Bits.getInt(data, offset + 4);
		for(int i = offset + 8; i + 8 <= offset + length; i += 8) {
			int start = Bits.getInt(data, i);
			int len = Bits.getInt(data, i + 4);
			synchronized(unconfirmedPackets) {
				for(int j = 0; j < unconfirmedPackets.size; j++) {
					int diff = unconfirmedPackets.array[j].id - start;
					if(diff >= 0 && diff < len) {
						unconfirmedPackets.remove(j);
						j--;
					}
				}
			}
		}
	}

	void sendKeepAlive() {
		byte[] data;
		synchronized(receivedPackets) {
			IntSimpleList runLengthEncodingStarts = new IntSimpleList();
			IntSimpleList runLengthEncodingLengths = new IntSimpleList();
			for(var packets : receivedPackets) {
				outer:
				for(int i = 0; i < packets.size; i++) {
					int value = packets.array[i];
					int leftRegion = -1;
					int rightRegion = -1;
					for(int reg = 0; reg < runLengthEncodingStarts.size; reg++) {
						int diff = value - runLengthEncodingStarts.array[reg];
						if(diff >= 0 && diff < runLengthEncodingLengths.array[reg]) {
							continue outer; // Value is already in the list.
						}
						if(diff == runLengthEncodingLengths.array[reg]) {
							leftRegion = reg;
						}
						if(diff == -1) {
							rightRegion = reg;
						}
					}
					if(leftRegion == -1) {
						if(rightRegion == -1) {
							runLengthEncodingStarts.add(value);
							runLengthEncodingLengths.add(1);
						} else {
							runLengthEncodingStarts.array[rightRegion]--;
							runLengthEncodingLengths.array[rightRegion]++;
						}
					} else if(rightRegion == -1) {
						runLengthEncodingLengths.array[leftRegion]++;
					} else {
						// Needs to combine the regions:
						runLengthEncodingLengths.array[leftRegion] += runLengthEncodingLengths.array[rightRegion] + 1;
						runLengthEncodingStarts.removeIndex(rightRegion);
						runLengthEncodingLengths.removeIndex(rightRegion);
					}
				}
			}
			IntSimpleList putBackToFront = receivedPackets[receivedPackets.length - 1];
			System.arraycopy(receivedPackets, 0, receivedPackets, 1, receivedPackets.length - 1);
			receivedPackets[0] = putBackToFront;
			receivedPackets[0].clear();
			data = new byte[runLengthEncodingStarts.size*8 + 9];
			data[0] = Protocols.KEEP_ALIVE;
			Bits.putInt(data, 1, lastKeepAliveSent++);
			Bits.putInt(data, 5, otherKeepAliveReceived);
			int cur = 9;
			for(int i = 0; i < runLengthEncodingStarts.size; i++) {
				Bits.putInt(data, cur, runLengthEncodingStarts.array[i]);
				cur += 4;
				Bits.putInt(data, cur, runLengthEncodingLengths.array[i]);
				cur += 4;
			}
			assert(cur == data.length);
		}
		manager.send(new DatagramPacket(data, data.length, remoteAddress, remotePort));
		synchronized(unconfirmedPackets) {
			// Resend packets that didn't receive confirmation within the last 2 keep-alive signals.
			for(int i = 0; i < unconfirmedPackets.size; i++) {
				if(lastKeepAliveReceived - unconfirmedPackets.array[i].lastKeepAliveSentBefore >= 2) {
					packets_sent++;
					packets_resent++;
					manager.send(unconfirmedPackets.array[i].packet);
					unconfirmedPackets.array[i].lastKeepAliveSentBefore = lastKeepAliveSent;
				}
			}
		}
		flush();
		if(bruteforcingPort) { // Brute force through some ports.
			// This is called every 100 ms, so if I send 10 requests it shouldn't be too bad.
			for(int i = 0; i < 5; i++) {
				byte[] fullData = new byte[0];
				//fullData[0] = Protocols.KEEP_ALIVE.id;
				if(((remotePort + bruteForcedPortRange) & 65535) != 0) {
					manager.send(new DatagramPacket(fullData, fullData.length, remoteAddress, (remotePort + bruteForcedPortRange) & 65535));
				}
				if(((remotePort - bruteForcedPortRange) & 65535) != 0) {
					manager.send(new DatagramPacket(fullData, fullData.length, remoteAddress, (remotePort - bruteForcedPortRange) & 65535));
				}
				bruteForcedPortRange++;
			}
		}
	}

	public boolean isConnected() {
		return otherKeepAliveReceived != 0;
	}

	private void collectPackets() {
		byte[] data;
		byte protocol;
		while(true) {
			synchronized(lastReceivedPackets) {
				int id = lastIncompletePacket;
				if(lastReceivedPackets[id & 65535] == null)
					return;
				int newIndex = lastIndex;
				protocol = lastReceivedPackets[id & 65535][newIndex++];
				if(Cubyz.world == null && protocol != Protocols.HANDSHAKE.id)
					return;
				// Determine the next packet length:
				int len = 0;
				int shift = 0;
				while(true) {
					if(newIndex == lastReceivedPackets[id & 65535].length) {
						newIndex = 0;
						id++;
						if(lastReceivedPackets[id & 65535] == null)
							return;
					}
					byte nextByte = lastReceivedPackets[id & 65535][newIndex++];
					len |= (nextByte & 0x7f) << shift;
					if((nextByte & 0x80) != 0) {
						shift += 7;
					} else {
						break;
					}
				}

				// Check if there is enough data available to fill the packets needs:
				int dataAvailable = lastReceivedPackets[id & 65535].length - newIndex;
				for(int idd = id + 1; dataAvailable < len; idd++) {
					if(lastReceivedPackets[idd & 65535] == null) return;
					dataAvailable += lastReceivedPackets[idd & 65535].length;
				}

				// Copy the data to an array:
				data = new byte[len];
				int offset = 0;
				do {
					dataAvailable = Math.min(lastReceivedPackets[id & 65535].length - newIndex, len - offset);
					System.arraycopy(lastReceivedPackets[id & 65535], newIndex, data, offset, dataAvailable);
					newIndex += dataAvailable;
					offset += dataAvailable;
					if(newIndex == lastReceivedPackets[id & 65535].length) {
						id++;
						newIndex = 0;
					}
				} while(offset != len);
				for(; lastIncompletePacket != id; lastIncompletePacket++) {
					lastReceivedPackets[lastIncompletePacket & 65535] = null;
				}
				lastIndex = newIndex;
			}
			Protocols.bytesReceived[protocol & 0xff] += data.length + 1;
			Protocols.list[protocol].receive(this, data, 0, data.length);
		}
	}

	public void receive(byte[] data, int len) {
		byte protocol = data[0];
		if(!handShakeComplete && protocol != Protocols.HANDSHAKE.id && protocol != Protocols.KEEP_ALIVE && protocol != (byte)0xff) {
			return; // Reject all non-handshake packets until the handshake is done.
		}
		lastConnection = System.currentTimeMillis();
		Protocols.bytesReceived[protocol & 0xff] += len + 20 + 8; // Including IP header and udp header
		Protocols.packetsReceived[protocol & 0xff]++;
		if(protocol == Protocols.IMPORTANT_PACKET) {
			int id = Bits.getInt(data, 1);
			if(handShakeComplete && id == 0) { // Got a new "first" packet from client. So the client tries to reconnect, but we still think it's connected.
				if(this instanceof User) {
					Server.disconnect((User)this);
					disconnected = true;
					manager.removeConnection(this);
					new Thread(() -> {
						try {
							Server.connect(new User(manager, remoteAddress.getHostAddress() + ":" + remotePort));
						} catch(Throwable e) {
							Logger.error(e);
						}
					}).start();
					return;
				} else {
					Logger.error("Server 'reconnected'? This makes no sense and the game can't handle that.");
				}
			}
			if(id - lastIncompletePacket >= 65536) {
				Logger.warning("Many incomplete packages. Cannot process any more packages for now.");
				return;
			}
			synchronized(receivedPackets) {
				receivedPackets[0].add(id);
			}
			synchronized(lastReceivedPackets) {
				if(id - lastIncompletePacket < 0 || lastReceivedPackets[id & 65535] != null) {
					return; // Already received the package in the past.
				}
				lastReceivedPackets[id & 65535] = Arrays.copyOfRange(data, IMPORTANT_HEADER_SIZE, len);
				// Check if a message got completed:
				collectPackets();
			}
		} else if(protocol == Protocols.KEEP_ALIVE) {
			receiveKeepAlive(data, 1, len - 1);
		} else {
			Protocols.list[protocol & 0xff].receive(this, data, 1, len - 1);
		}
	}

	public void disconnect() {
		// Send 3 disconnect packages to the other side, just to be sure.
		// If all of them don't get through then there is probably a network issue anyways which would lead to a timeout.
		Protocols.DISCONNECT.disconnect(this);
		try {Thread.sleep(10);} catch(Exception e) {}
		Protocols.DISCONNECT.disconnect(this);
		try {Thread.sleep(10);} catch(Exception e) {}
		Protocols.DISCONNECT.disconnect(this);
		disconnected = true;
		manager.removeConnection(this);
		Logger.info("Disconnected");
	}

	private static final class UnconfirmedPackage {
		private final DatagramPacket packet;
		private int lastKeepAliveSentBefore;
		private final int id;

		private UnconfirmedPackage(DatagramPacket packet, int lastKeepAliveSentBefore, int id) {
			this.packet = packet;
			this.lastKeepAliveSentBefore = lastKeepAliveSentBefore;
			this.id = id;
		}
	}
}
