/**
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.bluetooth.activities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.util.Enumeration;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.conn.util.InetAddressUtils;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.bluetooth.BluetoothActivity;
import com.bluetooth.BluetoothRemoteControlApp;
import com.bluetooth.R;

/**
 * This activity creates a server with the absolute minimum to make HTTP
 * requests. Once launched the user enters the IP that is shown on the phone in
 * a browser on a computer and as long as the phone and the computer are on the
 * same network the user will be able to remotely control the Bluetooth device
 * from the browser.
 * <p>
 * There are some issues with this activity though: there's a very noticeable
 * lag between issuing commands in the browser and the Bluetooth device
 * reacting. Some messages are lost.
 * <p>
 * This was originally made to stream the phone's camera preview feed to the
 * browser (basically an IP camera), but due to lack knowledge it was abandoned.
 * If you know enough about Java and Android I encourage you to add more
 * features to the front end.
 */
public class WiFiControl extends BluetoothActivity
{
	private static final String ALL_PATTERN = "*";

	private boolean isRunning = false;
	private String log;

	private BasicHttpProcessor httpproc = null;
	private BasicHttpContext httpContext = null;
	private HttpService httpService = null;
	private HttpRequestHandlerRegistry registry = null;

	private TextView tvIP;
	private LogView tvData;
	private Button bToggle;

	public static String SERVERIP = "192.168.0.42";
	public static final int SERVERPORT = 8080;

	private ServerSocket serverSocket;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.wifi_control);

		SERVERIP = getLocalIpAddress();

		httpContext = new BasicHttpContext();

		httpproc = new BasicHttpProcessor();
		httpproc.addInterceptor(new ResponseDate());
		httpproc.addInterceptor(new ResponseServer());
		httpproc.addInterceptor(new ResponseContent());
		httpproc.addInterceptor(new ResponseConnControl());

		httpService = new HttpService(httpproc, new DefaultConnectionReuseStrategy(), new DefaultHttpResponseFactory());

		registry = new HttpRequestHandlerRegistry();
		registry.register(ALL_PATTERN, new RequestHandler());

		httpService.setHandlerResolver(registry);

		tvData = (LogView) findViewById(R.id.tvData);
		tvIP = (TextView) findViewById(R.id.tvIP);
		bToggle = (Button) findViewById(R.id.bToggle);
		log = "";
	}

	public boolean handleMessage(Message msg)
	{
		// Update UI
		switch(msg.what)
		{
		case BluetoothRemoteControlApp.MSG_1:
			TextView tv = (TextView) findViewById(msg.arg1);
			tv.setText(msg.obj.toString());
			break;
		case BluetoothRemoteControlApp.MSG_READ:
			tvData.append(msg.obj.toString() + "\n");
			log += msg.obj + "<br />";
			break;
		case BluetoothRemoteControlApp.MSG_WRITE:
			tvData.append(msg.obj.toString() + "\n");
			log += msg.obj + "<br />";
			break;
		}
		return super.handleMessage(msg);
	}

	public void buttonClick(View v)
	{
		isRunning = !isRunning;
		if(isRunning)
		{
			Thread server = new Thread(new ServerThread(new Handler(this)));
			server.start();
			bToggle.setText(R.string.serverStop);
		}
		else
		{
			tvIP.setText("");
			bToggle.setText(R.string.serverStart);
		}
	}

	/**
	 * This thread makes the server, it handles all the requests.
	 */
	public class ServerThread implements Runnable
	{
		final private Handler handler;

		public ServerThread(Handler callbackHandler)
		{
			handler = callbackHandler;
		}

		public void run()
		{
			// Show server IP and port
			handler.obtainMessage(BluetoothRemoteControlApp.MSG_1, R.id.tvIP, 0, SERVERIP + ":" + SERVERPORT).sendToTarget();
			try
			{
				ServerSocket serverSocket = new ServerSocket(SERVERPORT);
				serverSocket.setReuseAddress(true);

				while(isRunning)
				{
					try
					{
						// This is a blocking function, it will wait for a client to connect
						Socket socket = serverSocket.accept();
						DefaultHttpServerConnection serverConnection = new DefaultHttpServerConnection();
						serverConnection.bind(socket, new BasicHttpParams());
						httpService.handleRequest(serverConnection, httpContext);
						serverConnection.shutdown();
					}
					catch(IOException e)
					{
						e.printStackTrace();
					}
					catch(HttpException e)
					{
						e.printStackTrace();
					}
				}

				serverSocket.close();
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	/**
	 * This method gets an IPv4 address for the user to enter in the browser.
	 * 
	 * @return The IP
	 */
	private String getLocalIpAddress()
	{
		try
		{
			for(Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();)
			{
				NetworkInterface intf = en.nextElement();
				for(Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();)
				{
					InetAddress inetAddress = enumIpAddr.nextElement();
					if(!inetAddress.isLoopbackAddress() && InetAddressUtils.isIPv4Address(inetAddress.getHostAddress()))
					{
						return inetAddress.getHostAddress().toString();
					}
				}
			}
		}
		catch(SocketException e)
		{
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * This is the handler for all HTTP requests, it can only do 3 things: serve
	 * index.htm file, execute commands that come in GET format in the URI with
	 * the parameter "cmd" or send back the log when the AJAX polls with the GET
	 * parameter "t".
	 */
	public class RequestHandler implements HttpRequestHandler
	{
		public void handle(HttpRequest request, HttpResponse response, HttpContext httpContext) throws HttpException, IOException
		{
			// This is for parsing GET requests
			String uriString = request.getRequestLine().getUri();
			final Uri uri = Uri.parse(uriString);

			HttpEntity entity = new EntityTemplate(new ContentProducer()
			{
				public void writeTo(final OutputStream outstream) throws IOException
				{
					OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8");
					String resp;

					// http://[IP]:[PORT]?cmd=[command]
					final String command;
					if((command = uri.getQueryParameter("cmd")) != null)
					{
						// Execute command
						write(URLDecoder.decode(command));
						// TODO: change this to HTTP_OK or something
						resp = log;
						log = "";
					}
					else if(uri.getQueryParameter("t") != null)
					{
						// Simple log update request
						resp = log;
						log = "";
					}
					else
					{
						// Convert an input stream to string and send
						resp = WiFiControl.openHTMLString(getApplicationContext(), R.raw.index);
					}

					writer.write(resp);
					writer.flush();
				}
			});
			
			// Only one type of data can be transmitted
			response.setHeader("Content-Type", "text/html");
			response.setEntity(entity);
		}
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		if(serverSocket != null)
		{
			try
			{
				serverSocket.close();
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	@Override
	public void onBackPressed()
	{
		isRunning = false;
		super.onBackPressed();
	}

	/**
	 * This function converts an InputStream in a String, this is needed to
	 * serve the .htm file to the client.
	 * <p>
	 * To convert the InputStream to String we use the Reader.read(char[]
	 * buffer) method. We iterate until the Reader return -1 which means there's
	 * no more data to read. We use the StringWriter class to produce the
	 * string.
	 */
	protected static String openHTMLString(Context context, int id)
	{
		InputStream is = context.getResources().openRawResource(id);
		if(is != null)
		{
			Writer writer = new StringWriter();

			char[] buffer = new char[1024];
			try
			{
				Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
				int n;
				while((n = reader.read(buffer)) != -1)
				{
					writer.write(buffer, 0, n);
				}
			}
			catch(UnsupportedEncodingException e)
			{
				e.printStackTrace();
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
			finally
			{
				try
				{
					is.close();
				}
				catch(IOException e)
				{
					e.printStackTrace();
				}
			}

			return writer.toString();
		}
		else
		{
			return "";
		}
	}
}
