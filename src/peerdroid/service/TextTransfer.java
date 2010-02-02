package peerdroid.service;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import net.jxta.endpoint.Message;
import net.jxta.endpoint.MessageElement;
import net.jxta.endpoint.StringMessageElement;
import net.jxta.pipe.OutputPipe;
import peerdroid.jxta4android.JxtaApp;
import android.util.Log;

public class TextTransfer {
	private String peerId;
	private String instanceName;

	public TextTransfer(String peerId, String instanceName) {
		this.peerId = peerId;
		this.instanceName = instanceName;
	}

	public void sendText(OutputPipe pipe, String data) {
		Log.d(JxtaApp.TAG, "Try to send message now...");
		try {
			Message msg = new Message();
			MessageElement typeElem = new StringMessageElement("Type", String
					.valueOf(Jxta.MESSAGE_TYPE_TEXT), null);
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

				Map<String, String> map = new HashMap<String, String>();
				map.put("name", peer.getName());
				map.put("time", new SimpleDateFormat("dd.MM.yy HH:mm:ss")
						.format(new Date()));
				map.put("text", content);
				JxtaApp.lstChatHistoryElements.add(map);
				JxtaApp.lstChatHistoryAdapter.notifyDataSetChanged();

			}
		});
	}
}
