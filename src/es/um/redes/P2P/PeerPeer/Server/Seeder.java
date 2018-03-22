
package es.um.redes.P2P.PeerPeer.Server;



import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedList;

import es.um.redes.P2P.PeerPeer.Server.SeederThread;
import es.um.redes.P2P.PeerPeer.Client.Downloader;
import es.um.redes.P2P.util.PeerDatabase;

/**
 * Servidor que se ejecuta en un hilo propio. Creará objetos
 * {@link SeederThread} cada vez que se conecte un cliente.
 */
public class Seeder implements Runnable {
	public static final int SEEDER_FIRST_PORT = 10000;
	public static final int SEEDER_LAST_PORT = 10100;
	private static Downloader currentDownloader;
	private ServerSocket serverSocket = null;
	private static int port = 0;
	private short chunkSize = 0;
	private LinkedList<SeederThread> currentList = null; // lista de seederThreads
	private boolean running = false;


	/**
	 * Base de datos de ficheros locales compartidos por este peer.
	 */
	protected PeerDatabase database;

	public Seeder(short chunkSize) {
		try {
			serverSocket = new ServerSocket();
		} catch (Exception e) {
			System.out.println("Seeder cant initialize, port exception");
		}
		
		getAvailablePort();
		this.chunkSize = chunkSize;
		currentList = new LinkedList<SeederThread>();
	}

	/**
	 * Pone la servidor a escuchar en un puerto libre del rango.
	 */
	public void getAvailablePort() {
		int trialPort = SEEDER_FIRST_PORT;
		boolean portAssign = false;
		
			while(!portAssign){
				try {
					serverSocket.bind(new InetSocketAddress(trialPort));
					portAssign = true;
				} catch (Exception e) {
						if (trialPort < SEEDER_LAST_PORT)
							trialPort++;
			}
		}
			
			Seeder.port = trialPort;
			System.out.println("Watching on PORT = "+port);
	}

	/**
	 * Función del hilo principal del servidor.
	 */
	public void run() {
		Socket clientSocket = null;
		running = true;
		while (running) {
			
			try {
				clientSocket = serverSocket.accept();
				System.out.println("Petición recibida");
			} catch (SocketException s) {
				System.out.println("Finalizando petición");
				break;
			} catch (IOException e) {
				e.printStackTrace();
			}
			currentList.add(new SeederThread(clientSocket, chunkSize,this));
			currentList.getLast().start();
	
		}	
		try {
			serverSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Metodo para que un hilo st informe al seeder de que ha terminado su 
	 * comunicacion con un hilo dt
	 */
	public synchronized void finishCommunication(SeederThread s){
		int n = currentList.indexOf((Object)s);
		currentList.remove(n);
	}
	/**
	 * Metodo para que el objeto downloader indique a su seeder que su descarga ha finalizado
	*/
	public static void finishDownload(){
		currentDownloader = null;
	}
	/**
	 * Inicio del hilo del servidor.
	 */
	public void start() {
		new Thread(this).start();
	}

	public static void setCurrentDownloader(Downloader downloader) {
		Seeder.currentDownloader = downloader;
	}
	public Downloader getCurrentDownloader(){
		return Seeder.currentDownloader;
	}

	public int getSeederPort() {
		return port;
	}
	public short getChunkSize(){
		return chunkSize;
	}
	public static int getPort(){
		return Seeder.port;
	}
	
	public void end(){
		running = false;
	}
}
