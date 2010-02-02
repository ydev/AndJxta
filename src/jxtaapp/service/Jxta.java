package jxtaapp.service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;

import jxtaapp.ui.JxtaApp;
import net.jxta.endpoint.Message;
import net.jxta.exception.PeerGroupException;
import net.jxta.id.IDFactory;
import net.jxta.peer.PeerID;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.pipe.OutputPipe;
import net.jxta.pipe.PipeMsgEvent;
import net.jxta.pipe.PipeMsgListener;
import net.jxta.pipe.PipeService;
import net.jxta.platform.ConfigurationFactory;
import net.jxta.platform.NetworkConfigurator;
import net.jxta.platform.NetworkManager;
import net.jxta.protocol.PipeAdvertisement;
import android.util.Log;

/**
 * This is the main manager for the following tasks:
 * <ol>
 * <li>configuration (own name and description, seeding server URL)</li>
 * <li>creating own peer ID</li>
 * <li>establishing and stopping the connection to the network</li>
 * <li>creating pipes</li>
 * <li>processing received messages</li>
 * <li>...</li>
 * </ol>
 */
public class Jxta implements PipeMsgListener {
	private NetworkConfigurator configurator;
	private NetworkManager networkManager;
	private PeerGroup netPeerGroup;
	private String exitlock = new String("exitlock");
	private static String rdvlist = "http://192.168.178.74/seeds.txt";
	private boolean actAsRendezvous = false;
	private final static long PIPE_ESTABLISHING_TIMEOUT = 1 * 60 * 1000;

	private Discovery discovery;
	private HashMap<String, OutputPipe> establishedPipes;
	private FileTransfer fileTransferService;
	private TextTransfer textTransferService;

	private String instanceName;
	private File cacheHome;
	private String username;
	private String password;
	private String description;

	public enum MessageType {
		TEXT, FILE;
		public String toString() {
			return name().toString();
		}
	}

	public Jxta(String name, File cache, String user, String pass,
			String rendezvousServerListURI) {
		instanceName = name;
		cacheHome = cache;
		username = user;
		password = pass;
		rdvlist = rendezvousServerListURI;
	}

	/**
	 * Stops the network connection
	 */
	public void stop() {
		for (OutputPipe pipe : establishedPipes.values()) {
			if (!pipe.isClosed())
				pipe.close();
		}

		netPeerGroup.stopApp();
	}

	/**
	 * Configuration based on {@link} ConfigurationFactory}, e.g. type of peer,
	 * local cache path, name, seeding server URL
	 */
	public void configureJXTA() {
		try {
			networkManager = new NetworkManager(NetworkManager.EDGE,
					instanceName, new File(cacheHome, instanceName).toURI());

			configurator = networkManager.getConfigurator();
		} catch (IOException e) {
			e.printStackTrace();
		}

		PeerID peerId = IDFactory.newPeerID(PeerGroupID.defaultNetPeerGroupID);

		{ // for Android
			ConfigurationFactory.setHome(new File(cacheHome, instanceName));
			ConfigurationFactory.setPeerID(peerId);
			ConfigurationFactory.setName(instanceName);
			ConfigurationFactory.setUseMulticast(true);
			try {
				ConfigurationFactory.setRdvSeedingURI(new URI(rdvlist));
				ConfigurationFactory.setRelaySeedingURI(new URI(rdvlist));
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
			ConfigurationFactory factory = ConfigurationFactory.newInstance();
			factory.setUseOnlyRendezvousSeeds(true);
			factory.setUseOnlyRelaySeeds(true);
		}

		/*
		 * try { configurator.save(); } catch (IOException e) {
		 * e.printStackTrace(); System.exit(1); }
		 */
		Log.d(JxtaApp.TAG, "Platform configured and saved");
	}

	/**
	 * Creates and starts the JXTA infrastructure peer group (aka NetPeerGroup)
	 * based on the specified mode template. Secondly it connects to a
	 * rendezvous peer. Hint: Establishing an own rendezvous peer is not
	 * possible with PeerDroid library.
	 * 
	 * @throws PeerGroupException
	 *             if the group fails to initialize
	 * @throws IOException
	 *             if an io error occurs
	 */
	public void startJXTA() throws PeerGroupException, IOException {
		Log.d(JxtaApp.TAG, "Starting network...");
		netPeerGroup = networkManager.startNetwork();
		Log.d(JxtaApp.TAG, "Network started");

		if (actAsRendezvous) {
			netPeerGroup.getRendezVousService().startRendezVous();
			Log.d(JxtaApp.TAG, "Rendezvous peer started");
		} else {
			Rendezvous rendezvousService = new Rendezvous(netPeerGroup);
			rendezvousService.waitForRdv();
		}

		establishedPipes = new HashMap<String, OutputPipe>();

		fileTransferService = new FileTransfer(netPeerGroup.getPeerID()
				.toString(), instanceName);
		textTransferService = new TextTransfer(netPeerGroup.getPeerID()
				.toString(), instanceName);
	}

	/**
	 * Start the discovery server {@link Discovery}
	 */
	public void startDiscovery() {
		Thread discoveryServerThread = new Thread(discovery = new Discovery(
				networkManager, instanceName, this), "Discovery Thread");
		discoveryServerThread.start();
	}

	/**
	 * Sends a message of any {@link MessageType}.
	 * 
	 * @param peername
	 *            Name of the receiving peer
	 * @param message
	 *            The message content
	 * @param messageType
	 *            The {@link MessageType}
	 * @return true if the message was send successfully
	 */
	public boolean sendMsgToPeer(String peername, String message,
			MessageType messageType) {
		PipeAdvertisement peer = getPipeAdvertisementByName(peername);

		if (peer == null) {
			Log.d(JxtaApp.TAG, "Peer not found while discovery");
			return false;
		}

		OutputPipe pipe = setupPipe(peer);

		if (pipe == null) {
			Log.d(JxtaApp.TAG, "Cannot setup pipe to this peer");
			return false;
		}

		if (messageType.equals(MessageType.FILE))
			fileTransferService.sendFile(pipe, message);
		else
			// if (messageType.equals(MessageType.TEXT))
			textTransferService.sendText(pipe, message);

		// don't close pipe, hold all pipes open for later use
		// closePipe(pipe, peer);

		return true;
	}

	/**
	 * Returns pipe advertisement by a given peer name (pipe advertisement list
	 * is generated by the {@link Discovery}.
	 * 
	 * @param peername
	 * @return pipe advertisement
	 */
	public PipeAdvertisement getPipeAdvertisementByName(String peername) {
		PipeAdvertisement peer = null;
		return getPeerByName(peername).getPipeAdvertisement();
	}

	/**
	 * Returns the a {@link Peer} object by a given peer name (peer list is
	 * generated by the {@link Discovery}.
	 * 
	 * @param peername
	 * @return peer
	 */
	public Peer getPeerByName(String peername) {
		Peer peer = null;

		Log.d(JxtaApp.TAG, "Try to find peer in discovery list...");

		synchronized (discovery.getPeerList()) {
			for (int i = 0; i < discovery.getPeerList().size(); i++) {
				// Log.d(JXTA4JSE.TAG,
				// discoveryClient.getPeerList().get(i).getName() + " == " +
				// peername);
				if (discovery.getPeerList().get(i).getName() != null
						&& discovery.getPeerList().get(i).getName().equals(
								peername)) {
					peer = discovery.getPeerList().get(i);
				}
			}
		}

		if (peer != null) {
			Log.d(JxtaApp.TAG, "Find peer " + peername + " in discovery list");
		}

		return peer;
	}

	/**
	 * Tries establishing a pipe to a given peer, identified by a pipe
	 * advertisement. If a pipe to an advertisement is already established, this
	 * will used and no new one is created. The timeout for this try is set by
	 * {@link #PIPE_ESTABLISHING_TIMEOUT}.
	 * 
	 * @param peerAdv
	 * @return The established {@link OutputPipe}, on error null
	 */
	private OutputPipe setupPipe(PipeAdvertisement peerAdv) {
		if (establishedPipes.containsKey(peerAdv.getName())) {
			OutputPipe pipe = establishedPipes.get(peerAdv.getName());
			if (!pipe.isClosed()) {
				Log.d(JxtaApp.TAG, "Pipe already exist, use established pipe");
				return pipe;
			}
		}

		Log.d(JxtaApp.TAG, "Try to establish pipe to peer...");

		PipeService pipeService = netPeerGroup.getPipeService();
		OutputPipe outputPipe = null;
		try {
			outputPipe = pipeService.createOutputPipe(peerAdv,
					PIPE_ESTABLISHING_TIMEOUT);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

		if (outputPipe != null) {
			establishedPipes.put(peerAdv.getName(), outputPipe);
			Log.d(JxtaApp.TAG, "Pipe to peer " + peerAdv.getName()
					+ " established");
		}

		return outputPipe;
	}

	/**
	 * Close a pipe (For reusing hold it open if possible).
	 * 
	 * @param pipe
	 * @param peerAdv
	 */
	private void closePipe(OutputPipe pipe, PipeAdvertisement peerAdv) {
		pipe.close();

		// establishedPipes.remove(peerAdv.getName());
		Log.d(JxtaApp.TAG, "Pipe to peer closed.");
	}

	/**
	 * Called for each pipe message event that occurs.
	 * 
	 * @param event
	 *            The event being received.
	 */
	public void pipeMsgEvent(PipeMsgEvent event) {
		Message msg = event.getMessage();

		if (msg.getMessageElement("Type").toString().equals(
				MessageType.TEXT.toString())) {
			textTransferService.receiveText(this, msg);
		} else if (msg.getMessageElement("Type").toString().equals(
				MessageType.FILE.toString())) {
			fileTransferService.receiveFilePackage(this, msg);
		} else {
			Log.d(JxtaApp.TAG, "No Service for this message type \""
					+ msg.getMessageElement("Type").toString() + "\"!");
		}
	}

	private void waitForQuit() {
		synchronized (exitlock) {
			try {
				Log.d(JxtaApp.TAG, "waiting for quit");
				exitlock.wait();
			} catch (InterruptedException e) {
				;
			}
		}
	}

	/**
	 * Returns the {@link Discovery} instance created by
	 * {@link #startDiscovery()}.
	 * 
	 * @return {@link Discovery}
	 */
	public Discovery getDiscovery() {
		return discovery;
	}
}
