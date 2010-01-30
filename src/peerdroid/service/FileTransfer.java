package peerdroid.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;

import net.jxta.endpoint.ByteArrayMessageElement;
import net.jxta.endpoint.Message;
import net.jxta.endpoint.StringMessageElement;
import net.jxta.pipe.OutputPipe;
import peerdroid.jxta4android.JxtaApp;
import android.util.Log;

public class FileTransfer {
	private final int PACKAGE_SIZE = 100000;
	private String peerId;
	private String instanceName;

	public FileTransfer(String peerId, String instanceName) {
		this.peerId = peerId;
		this.instanceName = instanceName;
	}

	public void sendFile(OutputPipe op, String filename) {
		File file;
		FileInputStream fileStream;
		int length;
		int packageNo = 0;
		byte[] buffer = new byte[PACKAGE_SIZE];

		try {
			file = new File(filename);
			fileStream = new FileInputStream(filename);

			do {
				length = fileStream.read(buffer);

				Message msg = new Message();
				msg.addMessageElement(new StringMessageElement("Type", String
						.valueOf(Jxta.MESSAGE_TYPE_FILE), null));
				msg.addMessageElement(new StringMessageElement("From", peerId,
						null));
				msg.addMessageElement(new StringMessageElement("FromName",
						instanceName, null));

				msg.addMessageElement(new StringMessageElement("Filename",
						filename, null));
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

				op.send(msg);
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

	public void receiveFilePackage(Message msg) {
		String from = msg.getMessageElement("From").toString();
		String fromName = msg.getMessageElement("FromName").toString();

		String filename = msg.getMessageElement("Filename").toString();
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
			raf = new RandomAccessFile("/sdcard/received_" + filename, "rw");
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
				+ ") FROM " + new String(fromName) + " (" + new Date() + "): "
				+ new String(filename) + " (PeerID: " + new String(from) + ")");
	}
}
