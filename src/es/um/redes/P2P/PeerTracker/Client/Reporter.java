package es.um.redes.P2P.PeerTracker.Client;


import java.net.*;

import es.um.redes.P2P.PeerTracker.Message.Message;
import es.um.redes.P2P.util.FileInfo;

public class Reporter implements ReporterIface {

	private static int TIMEOUT = 20;
	private boolean received = false;

	/**
	 * Tracker hostname, used for establishing connection
	 */
	private String trackerHostname;
	
	/**
	 * Tracker port para poder usar el puerto del tracker en otra maquina
	 */
	private int trackerPort = 4450;

	/**
	 * UDP socket for communication with tracker
	 */
	private DatagramSocket peerTrackerSocket;

	/***
	 * 
	 * @param tracker
	 *            Tracker hostname or IP
	 */
	public Reporter(String tracker) {
		trackerHostname = tracker;
		try {
			peerTrackerSocket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
			System.err.println("Reporter cannot create datagram socket for communication with tracker");
			System.exit(-1);
		}
	}

	public void end() {
		peerTrackerSocket.close();
	}

	@Override
	public boolean sendMessageToTracker(DatagramSocket socket, Message request, InetSocketAddress trackerAddress) {

		DatagramPacket pck = new DatagramPacket(request.toByteArray(), request.toByteArray().length, trackerAddress);

		// Receive the request message
		try {
			socket.send(pck);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	@Override
	public Message receiveMessageFromTracker(DatagramSocket socket) {
		byte[] buf = new byte[Message.MAX_UDP_PACKET_LENGTH];
		DatagramPacket pck = new DatagramPacket(buf, buf.length);
		try {
			socket.setSoTimeout(TIMEOUT);
			socket.receive(pck);
			received = true;
		} catch (Exception e) {
			received = false;
		}
		if (received)
			return Message.parseRequest(buf);
		return null;
	}

	/**
	 * Envio y recepcion de mensajes con el tracker
	 */
	@Override
	public Message conversationWithTracker(Message request) {
		Message msg = null;
		// Send
		InetSocketAddress trackerAddr = new InetSocketAddress(trackerHostname,
				trackerPort);
		
		sendMessageToTracker(peerTrackerSocket, request, trackerAddr);

		// Receive
		msg = receiveMessageFromTracker(peerTrackerSocket);
		while (msg == null){
			sendMessageToTracker(peerTrackerSocket, request, trackerAddr);
			msg = receiveMessageFromTracker(peerTrackerSocket);
		}
		return msg;

	}

	/**
	 * Metodo que informa al tracker de que se comparte el fichero (no necesariamente entero)
	 * @param files fichero a compartir
	 * @param seederPort puerto del seeder
	 * @return
	 */
	public Message updateFilesTracker(FileInfo[] files, int seederPort) {
		Message msg = Message.makeAddSeedRequest(seederPort, files);
		Message response = conversationWithTracker(msg);

		return response;

	}

}
