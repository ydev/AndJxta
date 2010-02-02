package jxtaapp.service;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import jxtaapp.ui.JxtaApp;
import net.jxta.endpoint.Message;
import net.jxta.endpoint.MessageElement;
import net.jxta.endpoint.StringMessageElement;
import net.jxta.pipe.OutputPipe;
import android.util.Log;

/**
 * Responsible for managing text message transfers. The {@link Message} consists
 * of the following {@link MessageElement}s:
 * <ol>
 * <li>StringMessageElement: "Type" as name, for types see
 * {@link Jxta.MessageType}</li>
 * <li>StringMessageElement: "From" as name, the peer ID of the sender</li>
 * <li>StringMessageElement: "FromName" as name, the name of the sender</li>
 * <li>StringMessageElement: "Content" as name, the text</li>
 * </ol>
 */
public class TextTransfer {
	private String peerId;
	private String instanceName;

	/**
	 * Constructor for file transfer manager
	 * 
	 * @param peerId
	 *            ID of the peer in JXTAs format
	 * @param instanceName
	 *            Name of own peer
	 */
	public TextTransfer(String peerId, String instanceName) {
		this.peerId = peerId;
		this.instanceName = instanceName;
	}

	/**
	 * Build a JXTA message object and sends the text message over the given
	 * output pipe.
	 * 
	 * @param pipe
	 *            An output pipe for the message
	 * @param data
	 *            The message text
	 */
	public void sendText(OutputPipe pipe, String data) {
		Log.d(JxtaApp.TAG, "Try to send message now...");
		try {
			Message msg = new Message();
			MessageElement typeElem = new StringMessageElement("Type",
					Jxta.MessageType.TEXT.toString(), null);
			MessageElement fromElem = new StringMessageElement("From", peerId,
					null);
			MessageElement fromNameElem = new StringMessageElement("FromName",
					instanceName, null);
			MessageElement contentElem = new StringMessageElement("Content",
					data, null);

			msg.addMessageElement(typeElem);
			msg.addMessageElement(fromElem);
			msg.addMessageElement(fromNameElem);
			msg.addMessageElement(contentElem);
			pipe.send(msg);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		Log.d(JxtaApp.TAG, "Message was send");
	}

	/**
	 * Handles all receiving text messages, extracts the message parts and store
	 * the history of the communication in the corresponding peer object
	 * (managed by the discovery service).
	 * 
	 * @param jxtaService
	 *            {@link jxtaapp.service.Jxta} object for saving the message
	 *            history
	 * @param msg
	 *            The received message
	 */
	public void receiveText(final Jxta jxtaService, Message msg) {
		final String from = msg.getMessageElement("From").toString();
		final String fromName = msg.getMessageElement("FromName").toString();
		final String content = msg.getMessageElement("Content").toString();

		Log.d(JxtaApp.TAG, "MESSAGE FROM " + new String(fromName) + " ("
				+ new Date() + "): " + new String(content) + " (PeerID: "
				+ new String(from) + ")");

		JxtaApp.handler.post(new Runnable() {
			public void run() {
				Peer peer = jxtaService.getPeerByName(fromName);

				peer.addHistory("> " + fromName, new SimpleDateFormat(
						"dd.MM.yy HH:mm:ss").format(new Date()), content);

				// id view for this peer open display it
				if (JxtaApp.lstChatHistoryElements != null
						&& JxtaApp.lstChatHistoryAdapter != null) {
					Map<String, String> map = new HashMap<String, String>();
					map.put("name", "> " + peer.getName());
					map.put("time", new SimpleDateFormat("dd.MM.yy HH:mm:ss")
							.format(new Date()));
					map.put("text", content);
					JxtaApp.lstChatHistoryElements.add(map);
					JxtaApp.lstChatHistoryAdapter.notifyDataSetChanged();
				}
			}
		});
	}
}
