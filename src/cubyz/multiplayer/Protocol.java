package cubyz.multiplayer;

public abstract class Protocol {
	public final byte id;

	public Protocol(byte id) {
		assert Protocols.list[id & 0xff] == null && id != Protocols.IMPORTANT_PACKET && id != Protocols.KEEP_ALIVE : "Protocols have duplicate id : " + this.getClass() + " " + Protocols.list[id & 0xff].getClass();
		this.id = id;
		Protocols.list[id & 0xff] = this;
	}

	public abstract void receive(UDPConnection conn, byte[] data, int offset, int length);
}
