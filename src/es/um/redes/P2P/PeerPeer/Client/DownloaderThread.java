package es.um.redes.P2P.PeerPeer.Client;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

import java.io.RandomAccessFile;
import java.io.DataInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import java.nio.ByteBuffer;

import es.um.redes.P2P.App.Peer;
import es.um.redes.P2P.PeerPeer.Message.MessageTCP;

import es.um.redes.P2P.util.FileInfo;

/**
 * Clase hilo de descarga encargado de pedir un fichero y gestionar los trozos recibidos.
 * @author Valentin
 * @author Alejandro
 * @author valy rtitos
 *
 */
public class DownloaderThread extends Thread {
	private Downloader downloader;
	private Socket downloadSocket;
	private DataOutputStream dos = null;
	private DataInputStream dis = null;
	private int numChunksDownloaded;
	private int id = 0;
	private FileInfo file = null;
	private boolean request = false; // peticiones
	private int idchunkreq = 0; // id_chunk que pedimos

	public DownloaderThread(InetSocketAddress seed, FileInfo file, Downloader d, int id) {
		downloader = d;
		this.id = id;
		try {
			downloadSocket = new Socket(seed.getAddress(), seed.getPort());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		this.file = file;
	}
	/*
	// It receives a message containing a chunk and it is stored in the file
	private void receiveAndWriteChunk() {
	}

	// It receives a message containing a chunk and it is stored in the file
	private void receiveAndProcessChunkList() {
	}
	*/
	// Number of chunks already downloaded by this thread
	public int getNumChunksDownloaded() {
		return numChunksDownloaded;
	}

	public void setidchunk(int _idchunkreq) {
		idchunkreq = _idchunkreq;
	}

	private MessageTCP processMessageVariable() {

		byte opCode = 0;
		try {
			opCode = (byte) dis.read();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		byte[] buffer = null;
		switch (opCode) {
		case MessageTCP.OP_REQ_FILE:
			buffer = new byte[MessageTCP.mTAM_REQ_FILE];
			break;
		case MessageTCP.OP_CHUNK:
			buffer = new byte[MessageTCP.mTAM_CHUNK];
			break;
		case MessageTCP.OP_DISCONNECT:
			buffer = new byte[MessageTCP.mTAM_CONTROL];
			break;
		case MessageTCP.OP_GOT_CHUNKS:
			int n = 0;
			try {
				// necesitamos saber la longitud de la lista de trozos que es el
				// siguiente campo
				n = dis.readInt();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			// calculamos el tamaño a partir del numero de trozos
			buffer = new byte[MessageTCP.calculateGotChunksSize(n)];
			// PAsamos el entero del numero de trozos a formato byte[]
			ByteBuffer bf = ByteBuffer.allocate(Integer.BYTES);
			bf.putInt(n);
			byte[] ntrozos = bf.array();
			buffer[1] = ntrozos[0];
			buffer[2] = ntrozos[1];
			buffer[3] = ntrozos[2];
			buffer[4] = ntrozos[3];
			break;
		case MessageTCP.OP_GOT_NO_CHUNKS:
			buffer = new byte[MessageTCP.mTAM_CONTROL];
			break;
		case MessageTCP.OP_REQ_CHUNK:
			buffer = new byte[MessageTCP.mTAM_REQ_CHUNK];
			break;
		default:
			System.out.println("Could not process the message from st because of invalid OpCode");
			return new MessageTCP(MessageTCP.OP_INVALID);
		}
		/**
		 * La posicion 0 corresponde al opCode, en caso de ser un mensaje
		 * got_Chunks, su siguiente campo contendra el entero en bytes, con
		 * readFully leemos a partir de uno de los 2 casos anteriores una
		 * longitud variable en funcion de lo anterior
		 */
		buffer[0] = opCode;
		int gcm = 0;
		if (opCode == MessageTCP.OP_GOT_CHUNKS)
			gcm = 4;
		try {
			dis.readFully(buffer, gcm + 1, buffer.length - 1 - gcm);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return new MessageTCP(buffer);
	}

	// Main code to request chunk lists and chunks
	public void run() {

		try {
			dis = new DataInputStream(downloadSocket.getInputStream());
			dos = new DataOutputStream(downloadSocket.getOutputStream());
		} catch (IOException e) {
			e.printStackTrace();
		}

		MessageTCP msgSender = new MessageTCP(MessageTCP.OP_REQ_FILE, file.fileHash); // Request																// chunk
		request = true;
		System.out.println("Inicio de la comunicacion");
		System.out.println(msgSender);
                
		while (request) {

			byte[] send = msgSender.toByteArray();
			try {
				dos.write(send);
			} catch (IOException e1) {
				request = false;
				e1.printStackTrace();
			}

			if (msgSender.getOpCode() == MessageTCP.OP_DISCONNECT){
				request = false;
			}
			else {
				MessageTCP msgResponse = processMessageVariable();
				System.out.println("recibido: "+msgResponse);
				System.out.println("");                
				msgSender = processMessage(msgResponse);
			}

		}
		try {
			downloadSocket.close();
			downloader.connectionfailedfinished(id); // avisar al downloader de que se cierra esta conexion
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/*
	 * Processes the response message from the seeder
	 */
	public MessageTCP processMessage(MessageTCP msg) {
		switch (msg.getOpCode()) {
		// Si llega un mensaje del chunk pedido, creamos el fichero a partir del
		// chunk
		// Respuesta del seeder:
		case MessageTCP.OP_CHUNK:

			int posChunk = msg.getChunk() * MessageTCP.TAM_CHUNK;
			int lastChunk = downloader.getLastChunk();
			int length = 0;
			byte[] dataChunk = msg.getDataChunk();
			if (msg.getChunk() == lastChunk)
				length = (int) (file.fileSize - posChunk);
			else
				length = MessageTCP.TAM_CHUNK;

			File f = new File(downloader.getRoute(), file.fileName);

			try {
				if (!f.exists())
					f.createNewFile();
				RandomAccessFile raf = new RandomAccessFile(f, "rw");
				raf.seek(posChunk);
				raf.write(dataChunk, 0, length);
				raf.close();
			} catch (Exception e) {
				System.out.println("Error writing the file " + f.getName());
				e.printStackTrace();
			}

			System.out.println(msg.getChunk() + "/" + downloader.getTotalChunks() + " chunks");
			numChunksDownloaded++;
			downloader.receiveChunk(msg.getChunk(), id);
			/*
			try {
				sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			*/
			if (downloader.nextChunk(id)) {
				return new MessageTCP(MessageTCP.OP_REQ_CHUNK, msg.getHash(), idchunkreq);
			} else {
				System.out.println("Download finished: " + f.getName() + " no more chunks to request iddt=" + id);
				return new MessageTCP(MessageTCP.OP_DISCONNECT);
			}

		case MessageTCP.OP_GOT_CHUNKS:

			int[] listaTrozos = msg.getArrayTrozos();
			downloader.setmyIdChunklist(id, listaTrozos); // Le indicamos al// downloader que// lsita de trozos// podemos descargar
			if (downloader.nextChunk(id)) { // Creamos la primera peticion de // trozo
				return new MessageTCP(MessageTCP.OP_REQ_CHUNK, downloader.getTargetFile().fileHash, idchunkreq);
			} else {
				System.out.println("Fail doing the first request, no chunks left to request");
				return new MessageTCP(MessageTCP.OP_DISCONNECT);
			}
			// return new MessageTCP(MessageTCP.OP_REQ_CHUNK,
			// downloader.getTargetFile().fileHash, listaTrozos[0]);

		case MessageTCP.OP_GOT_NO_CHUNKS:
			return new MessageTCP(MessageTCP.OP_DISCONNECT);

		default:
			return new MessageTCP(MessageTCP.OP_DISCONNECT); // Un mensaje con // formato// incorrecto// implica// cerrar la conexion
		}

	}

}
