package peerdroid.service;

import java.util.HashMap;
import java.util.HashSet;

import net.jxta.protocol.PipeAdvertisement;

public class Peer {
	private String name = null;
	private PipeAdvertisement pipeAdvertisement = null;
	private long lastUpdate = 0;
	private HashSet<HashMap<String, String>> history;

	public Peer(PipeAdvertisement pipeAdvertisement) {
		super();
		this.name = pipeAdvertisement.getName();
		this.pipeAdvertisement = pipeAdvertisement;
		this.lastUpdate = System.currentTimeMillis();
		this.history = new HashSet<HashMap<String, String>>();
	}

	public boolean equals(Object obj) {
		Peer objPeer = (Peer) obj;

		return objPeer.name.equals(this.name);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public PipeAdvertisement getPipeAdvertisement() {
		return pipeAdvertisement;
	}

	public void setPipeAdvertisement(PipeAdvertisement pipeAdvertisement) {
		this.pipeAdvertisement = pipeAdvertisement;
	}

	public long getLastUpdate() {
		return lastUpdate;
	}

	public void setLastUpdate(long lastUpdate) {
		this.lastUpdate = lastUpdate;
	}

	public HashSet<HashMap<String, String>> getHistory() {
		return history;
	}

	public void addHistory(HashMap<String, String> item) {
		history.add(item);
	}

	public void addHistory(String name, String time, String text) {
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("name", name);
		map.put("time", time);
		map.put("text", text);
		history.add(map);
	}

	public String toString() {
		return name + " (" + pipeAdvertisement.getID().toString() + ")";
	}
}
