package jxtaapp.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;

import jxtaapp.ui.JxtaApp;
import net.jxta.endpoint.ByteArrayMessageElement;
import net.jxta.endpoint.Message;
import net.jxta.endpoint.MessageElement;
import net.jxta.endpoint.StringMessageElement;
import net.jxta.pipe.OutputPipe;
import android.util.Log;

/**
 * Responsible for managing file transfers. The {@link Message} consists of the
 * following {@link MessageElement}s:
 * <ol>
 * <li>StringMessageElement: "Type" as name, for types see
 * {@link Jxta.MessageType}</li>
 * <li>StringMessageElement: "From" as name, the peer ID of the sender</li>
 * <li>StringMessageElement: "FromName" as name, the name of the sender</li>
 * <li>StringMessageElement: "Content" as name, a file part</li>
 * </ol>
 */
public class FileTransfer {
	private final int PACKAGE_SIZE = 100000;
	private final String DIRECTORY_FOR_RECEIVED = "/sdcard";
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
	public FileTransfer(String peerId, String instanceName) {
		this.peerId = peerId;
		this.instanceName = instanceName;
	}

	/**
	 * Build a JXTA message object, splits the file into packets of size
	 * {@link #PACKAGE_SIZE} in bytes and sends this parts over the given output
	 * pipe.
	 * 
	 * @param pipe
	 *            An output pipe for the message
	 * @param filepath
	 *            Path of the file to send
	 */
	public void sendFile(OutputPipe pipe, String filepath) {
		File file;
		FileInputStream fileStream;
		int length;
		int packageNo = 0;
		byte[] buffer = new byte[PACKAGE_SIZE];

		try {
			file = new File(filepath);
			fileStream = new FileInputStream(filepath);

			do {
				length = fileStream.read(buffer);

				Message msg = new Message();
				msg.addMessageElement(new StringMessageElement("Type",
						Jxta.MessageType.FILE.toString(), null));
				msg.addMessageElement(new StringMessageElement("From", peerId,
						null));
				msg.addMessageElement(new StringMessageElement("FromName",
						instanceName, null));

				msg.addMessageElement(new StringMessageElement("Filename", file
						.getName(), null));
				msg.addMessageElement(new StringMessageElement(
						"FilePackageSize", String.valueOf((Math.round((file
								.length() / PACKAGE_SIZE)) + 2)), null));
				msg.addMessageElement(new StringMessageElement("PackageSize",
						String.valueOf(length), null));
				msg.addMessageElement(new StringMessageElement("PackageNo",
						String.valueOf(++packageNo), null));

				if (length != -1)
					msg.addMessageElement(new ByteArrayMessageElement(
							"Content", null, buffer.clone(), null));
				else
					msg.addMessageElement(new ByteArrayMessageElement(
							"Content", null, new byte[0], null));

				// System.out.println("send content (" + String.valueOf(length)
				// + "; " + String.valueOf(count) + "): " + new String(buffer));

				pipe.send(msg);
			} while (length != -1);
			fileStream.close();

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Handles all receiving file messages (respectively the parts), extracts
	 * the parts and combine it to the full file. It stores all parts directly
	 * in the predefined directory {@link #DIRECTORY_FOR_RECEIVED}
	 * 
	 * @param jxtaService
	 *            {@link jxtaapp.service.Jxta} object
	 * @param msg
	 *            The received message
	 */
	public void receiveFilePackage(final Jxta jxtaService, Message msg) {
		String from = msg.getMessageElement("From").toString();
		final String fromName = msg.getMessageElement("FromName").toString();

		final String filename = msg.getMessageElement("Filename").toString();
		int filePackageSize = Integer.valueOf(
				msg.getMessageElement("FilePackageSize").toString()).intValue();
		int packageSize = Integer.valueOf(
				msg.getMessageElement("PackageSize").toString()).intValue();
		int packageNo = Integer.valueOf(
				msg.getMessageElement("PackageNo").toString()).intValue();
		byte[] content = msg.getMessageElement("Content").getBytes(true);

		// System.out.println("receive content (" + packageSize + "; "
		// + packageNo + "): " + new String(content));

		RandomAccessFile raf;
		try {
			raf = new RandomAccessFile(DIRECTORY_FOR_RECEIVED + "/received_"
					+ filename, "rw");
			if (packageSize != -1) {
				raf.seek((packageNo - 1) * PACKAGE_SIZE);
				raf.write(content, 0, packageSize);
			} else {
				System.out.println("FILE: seems last package");
				// raf.close();
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Log.d(JxtaApp.TAG, "FILE-PACKAGE (" + packageNo + "/" + filePackageSize
				+ ") FROM " + new String(fromName) + " ("
				+ new SimpleDateFormat("dd.MM.yy HH:mm:ss").format(new Date())
				+ "): " + new String(filename) + " (PeerID: "
				+ new String(from) + ")");

		JxtaApp.handler.post(new Runnable() {
			public void run() {
				Peer peer = jxtaService.getPeerByName(fromName);

				peer.addHistory("> " + fromName, new SimpleDateFormat(
						"dd.MM.yy HH:mm:ss").format(new Date()),
						"Receive file " + filename);
			}
		});
	}
}
