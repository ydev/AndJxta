package peerdroid.jxta4android;

import peerdroid.service.Jxta;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class JxtaApp extends Activity {
	public static String TAG = "JXTA4Android";
	private Jxta peer;
	public static final Handler handler = new Handler();
	public static TextView txtChat = null;
	public static EditText txtMessage = null;
	public static EditText txtPeer = null;
	public static Button btnSend = null;

	/** Called when the activity is first created. */
	 protected  void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.chat);

		txtChat = (TextView) findViewById(R.id.txtChat);
		txtMessage = (EditText) findViewById(R.id.txtMessage);
		txtPeer = (EditText) findViewById(R.id.txtPeer);
		btnSend = (Button) findViewById(R.id.btnSend);
		
		View.OnClickListener btnSend_OnClickListener =  new View.OnClickListener() {
			public void onClick(View view) {
				if (peer != null)
					peer.sendMsgToPeer(txtPeer.getText().toString(), txtMessage.getText().toString());
			}
		};
		btnSend.setOnClickListener(btnSend_OnClickListener);


		Thread jxtaThread = new Thread() {
			public void run() {
				peer = new Jxta();
				peer.start("mirko", getFileStreamPath("jxta"), "mirko", "");
			}
		};
		jxtaThread.start();
	}
	
	 protected void onDestroy() {
		if (peer != null)
			peer.stop();
	}
}