package jxtaapp.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jxtaapp.ui.JxtaApp;
import net.jxta.discovery.DiscoveryEvent;
import net.jxta.discovery.DiscoveryListener;
import net.jxta.discovery.DiscoveryService;
import net.jxta.document.Advertisement;
import net.jxta.document.AdvertisementFactory;
import net.jxta.id.ID;
import net.jxta.id.IDFactory;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.pipe.PipeMsgListener;
import net.jxta.pipe.PipeService;
import net.jxta.platform.NetworkManager;
import net.jxta.protocol.DiscoveryResponseMsg;
import net.jxta.protocol.PeerAdvertisement;
import net.jxta.protocol.PipeAdvertisement;
import android.util.Log;

/**
 * Responsible for discover the peer-to-peer network to find other peers and
 * secondly to publish the own peer advertisement. The discovery service wrapper
 * runs in this own thread, creates own pipe advertisement, handles received
 * discovery messages, manages a list of peers in the network and sends a
 * discovery message every {@link #DISCOVERY_WAITTIME} milliseconds.
 * 
 * @see net.jxta.discovery.DiscoveryService
 */
public class Discovery implements Runnable, DiscoveryListener {
	private NetworkManager manager;
	private String instanceName;
	private PipeMsgListener pipeMsgListener;
	private DiscoveryService discoveryService;
	private PipeService pipeService;
	private List<Peer> peerList;

	private final static long ADVERTISEMENT_LIFETIME = 60 * 60 * 1000;
	private final static long ADVERTISEMENT_EXPIRATION = 60 * 60 * 1000;
	private final static long DISCOVERY_WAITTIME = 1 * 60 * 1000;

	private PipeAdvertisement advertisement;
	private ID advertisementPipeId;
	private String advertisementType;
	private String advertisementName;

	/**
	 * Constructor for discovery service wrapper, creates also the own pipe
	 * advertisement.
	 * 
	 * @param manager
	 * @param instanceName
	 *            Name of own peer
	 * @param pipeMsgListener
	 *            Listener for messages incoming at the input-pipe
	 */
	public Discovery(NetworkManager manager, String instanceName,
			PipeMsgListener pipeMsgListener) {
		this.manager = manager;
		this.instanceName = instanceName;
		this.pipeMsgListener = pipeMsgListener;

		peerList = Collections.synchronizedList(new ArrayList<Peer>());

		PeerGroup netPeerGroup = manager.getNetPeerGroup();
		discoveryService = netPeerGroup.getDiscoveryService();
		pipeService = netPeerGroup.getPipeService();

		advertisementPipeId = IDFactory
				.newPipeID(PeerGroupID.defaultNetPeerGroupID);
		advertisementType = PipeService.UnicastType;
		advertisementName = instanceName;

		createPipeAdvertisement();
	}

	/**
	 * Starts the discovery service wrapper
	 */
	public void run() {
		// setup discovery server
		try {
			pipeService
					.createInputPipe(getPipeAdvertisement(), pipeMsgListener);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// setup discovery client
		discoveryService.addDiscoveryListener(this);

		while (true) {
			// Discovery Server: publish own pipe advertisement
			try {
				Log.d(JxtaApp.TAG,
						"Discovery service publish pipe advertisement (lifetime: "
								+ ADVERTISEMENT_LIFETIME + "; expiration: "
								+ ADVERTISEMENT_EXPIRATION + ")...");
				discoveryService.publish(getPipeAdvertisement(),
						ADVERTISEMENT_LIFETIME, ADVERTISEMENT_EXPIRATION);
				discoveryService.remotePublish(getPipeAdvertisement(),
						ADVERTISEMENT_EXPIRATION);

			} catch (Exception e) {
				Log
						.d(JxtaApp.TAG,
								"Discovery service failed to publish pipe advertisement");
				e.printStackTrace();
			}

			// Discovery Client: search for other peers
			try {
				discoveryService.addDiscoveryListener(this);

				Log.d(JxtaApp.TAG, "Discovery service sends discovery message");

				discoveryService.getRemoteAdvertisements(
				// no specific peer (propagate)
						null,
						// Adv type
						DiscoveryService.ADV,
						// Attribute = name
						null, // "Name",
						// Value = the tutorial
						null, // "Discovery tutorial",
						// 50 advertisement response is all we are looking
						// for
						50,
						// no query specific listener. we are using a global
						// listener
						null);

				// discoveryService.getLocalAdvertisements(DiscoveryService.ADV,
				// null, null);
			} catch (Exception e) {
				// Log.d(JxtaApp.TAG,
				// "Failed to load local pipe advertisements");
				e.printStackTrace();
			}

			try {
				Log.d(JxtaApp.TAG, "Discovery service sleeps for: "
						+ DISCOVERY_WAITTIME);
				Thread.sleep(DISCOVERY_WAITTIME);
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}

	/**
	 * Creates own pipe advertisement for publishing
	 */
	public void createPipeAdvertisement() {
		PipeAdvertisement adv = (PipeAdvertisement) AdvertisementFactory
				.newAdvertisement(PipeAdvertisement.getAdvertisementType());
		adv.setPipeID(advertisementPipeId);
		adv.setType(advertisementType);
		adv.setName(advertisementName);

		advertisement = adv;
	}

	/**
	 * @return Own pipe advertisement
	 */
	public PipeAdvertisement getPipeAdvertisement() {
		return advertisement;
	}

	/**
	 * This method is called whenever a discovery response is received, which
	 * are either in response to a query we sent, or a remote publish by another
	 * node
	 * 
	 * @param ev
	 *            the discovery event
	 */
	public synchronized void discoveryEvent(DiscoveryEvent ev) {
		DiscoveryResponseMsg res = ev.getResponse();
		String name = "unknown";

		// Get the responding peer's advertisement
		PeerAdvertisement peerAdv = res.getPeerAdvertisement();

		// some peers may not respond with their peerAdv name
		if (peerAdv != null)
			peerAdv.getName();
		name = ev.getSource().toString();

		Log
				.d(
						JxtaApp.TAG,
						"###############################################################################################");
		Log.d(JxtaApp.TAG, "Got a Discovery Response ["
				+ res.getResponseCount() + " elements] from peer: " + name);

		Advertisement adv = null;
		Enumeration en = res.getAdvertisements();

		if (en != null) {
			while (en.hasMoreElements()) {
				adv = (Advertisement) en.nextElement();

				// Log.d(JXTA4JSE.TAG, "   Type: " + adv.getClass().toString());
				// + adv.toString());
				if (adv instanceof PipeAdvertisement) {
					PipeAdvertisement pipeAdv = (PipeAdvertisement) adv;

					// change the list only when the new peer is not the
					// current peer itself
					if (pipeAdv.getName() != null
							&& !pipeAdv.getName().equals(instanceName)) {
						Peer newPeer = new Peer(pipeAdv);

						addPeerListItem(newPeer);
					}
				}
			}
		}

		for (int i = 0; i < peerList.size(); i++)
			Log.d(JxtaApp.TAG, "   PeerAdv name: " + peerList.get(i).getName()
					+ "; PeerAdv ID: "
					+ peerList.get(i).getPipeAdvertisement().getID());

		Log
				.d(
						JxtaApp.TAG,
						"###############################################################################################");

	}

	/**
	 * Add a discovered peer to the local list and also checks if it is already
	 * included there.
	 * 
	 * @param peer
	 */
	private synchronized void addPeerListItem(Peer peer) {
		if (peerList.contains(peer)) {
			Peer peerInList = peerList.get(peerList.indexOf(peer));
			peerInList.setPipeAdvertisement(peer.getPipeAdvertisement());
			peerInList.setLastUpdate(System.currentTimeMillis());
		} else {
			peerList.add(peer);
		}

		JxtaApp.handler.post(new Runnable() {
			public void run() {
				synchronized (peerList) {
					JxtaApp.lstPeerListElements.clear();

					for (Peer peer : peerList) {
						Map<String, String> map = new HashMap<String, String>();
						map.put("name", peer.getName());
						map.put("desc", peer.getPipeAdvertisement()
								.getDescription());
						map.put("adv", peer.getPipeAdvertisement().getPipeID()
								.toString());
						JxtaApp.lstPeerListElements.add(map);
					}
				}

				JxtaApp.lstPeerListAdapter.notifyDataSetChanged();
			}
		});
	}

	/**
	 * @return List of all discovery peers
	 */
	public synchronized List<Peer> getPeerList() {
		return peerList;
	}

}