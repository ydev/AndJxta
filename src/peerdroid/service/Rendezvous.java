package peerdroid.service;

import java.util.Date;

import net.jxta.peergroup.PeerGroup;
import net.jxta.rendezvous.RendezVousService;
import net.jxta.rendezvous.RendezvousEvent;
import net.jxta.rendezvous.RendezvousListener;
import peerdroid.jxta4android.JxtaApp;
import android.util.Log;

public class Rendezvous implements RendezvousListener {
	private boolean connected;
	private String rdvlock = new String("rdvlock");
	private RendezVousService peerGroupRendezvous;

	public Rendezvous(PeerGroup peerGroup) {
		peerGroupRendezvous = peerGroup.getRendezVousService();
		// myPeerID = peerGroup.getPeerID().toString();

		peerGroupRendezvous.addListener(this);
	}

	@Override
	public void rendezvousEvent(RendezvousEvent event) {
		String eventDescription;
		int eventType = event.getType();
		switch (eventType) {
		case RendezvousEvent.RDVCONNECT:
			eventDescription = "RDVCONNECT";
			setConnected(true);
			break;
		case RendezvousEvent.RDVRECONNECT:
			eventDescription = "RDVRECONNECT";
			setConnected(true);
			break;
		case RendezvousEvent.RDVDISCONNECT:
			eventDescription = "RDVDISCONNECT";
			break;
		case RendezvousEvent.RDVFAILED:
			eventDescription = "RDVFAILED";
			break;
		case RendezvousEvent.CLIENTCONNECT:
			eventDescription = "CLIENTCONNECT";
			break;
		case RendezvousEvent.CLIENTRECONNECT:
			eventDescription = "CLIENTRECONNECT";
			break;
		case RendezvousEvent.CLIENTDISCONNECT:
			eventDescription = "CLIENTDISCONNECT";
			break;
		case RendezvousEvent.CLIENTFAILED:
			eventDescription = "CLIENTFAILED";
			break;
		case RendezvousEvent.BECAMERDV:
			eventDescription = "BECAMERDV";
			setConnected(true);
			break;
		case RendezvousEvent.BECAMEEDGE:
			eventDescription = "BECAMEEDGE";
			break;
		default:
			eventDescription = "UNKNOWN RENDEZVOUS EVENT";
		}
		Log.d(JxtaApp.TAG, new Date().toString() + "  Rdv: event="
				+ eventDescription + " from peer = " + event.getPeer());

		synchronized (rdvlock) {
			if (connected) {
				rdvlock.notify();
			}
		}
	}

	public void waitForRdv() {
		Log.d(JxtaApp.TAG, "Try connecting to rendezvous peer...");
		synchronized (rdvlock) {
			while (!peerGroupRendezvous.isConnectedToRendezVous()) {
				Log.d(JxtaApp.TAG, "Awaiting rendezvous connection...");
				try {
					if (!peerGroupRendezvous.isConnectedToRendezVous()) {
						rdvlock.wait();
					}
				} catch (InterruptedException e) {
					;
				}
			}
		}
		Log.d(JxtaApp.TAG, "Connected to rendezvous peer");
	}

	public void setConnected(boolean connected) {
		this.connected = connected;
	}

	public boolean isConnected() {
		return connected;
	}

}
