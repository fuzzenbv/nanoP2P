package es.um.redes.P2P.PeerPeer.Server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Socket;
import es.um.redes.P2P.App.Peer;
import es.um.redes.P2P.PeerPeer.Message.MessageTCP;
import es.um.redes.P2P.util.FileInfo;

/**
 * Clase hilo encargada de enviar el fichero solicitado y la gestion del envio de trozos.
 * @author Valentin
 * @author Alejandro
 * @author valy rtitos
 *
 */
public class SeederThread extends Thread {
	private Socket socket = null;
	protected DataOutputStream dos = null;
	protected DataInputStream dis = null;
	private Seeder seeder = null;
	private boolean running = false;

	public SeederThread(Socket socket, short chunkSize, Seeder seeder) {
		this.socket = socket;
		this.seeder = seeder;
	}


	/**
	 * Este metodo a partir del opCode (type) devuelve el buffer con el tamaño
	 * adecuado al mensaje
	 * 
	 * @param opCode
	 * @return buffer el buffer con el tamaño exacto
	 */
	private byte[] processBuffer() {
		
		byte[] buffer = null;
		
		byte opCode = 0;
		try {
			opCode = (byte)dis.read();// lee primer byte
		} catch (IOException e) {
			System.out.println("Excepcion leyendo del canal (st) el OPcode");
			running = false;
			e.printStackTrace();
		} 
		
		switch (opCode) {
		case MessageTCP.OP_REQ_FILE:
			buffer = new byte[MessageTCP.mTAM_REQ_FILE];
			break;
		case MessageTCP.OP_REQ_CHUNK:
			buffer = new byte[MessageTCP.mTAM_REQ_CHUNK];
			break;
		case MessageTCP.OP_DISCONNECT:
			buffer = new byte[MessageTCP.mTAM_CONTROL];
			break;
		default:
			System.out.println("Could not process the message from dt because of invalid OpCode = " + opCode);
			buffer = new byte[MessageTCP.mTAM_CONTROL]; 
			opCode = MessageTCP.OP_INVALID;
			break;
		}
		
		buffer[0] = (byte) opCode;
		
		try {
			if((byte)opCode != MessageTCP.OP_DISCONNECT && (byte)opCode != MessageTCP.OP_INVALID){
				dis.readFully(buffer, 1, buffer.length - 1);	
			}
		} catch (IOException e) {
			running = false;
            System.out.println("Excepcion tratando de leer el resto del mensaje del canal (st)");
			e.printStackTrace();
		}
		return buffer;
	}
     
	/**
	 * Metodo principal que coordina la recepcion y envio de mensajes TCP
	 */
	public void run() {

		running = true;
		try {
			dis = new DataInputStream(socket.getInputStream());
			dos = new DataOutputStream(socket.getOutputStream());
		} catch (IOException e) {
			e.printStackTrace();
		}
		while (running) {		

			byte[] buffer = processBuffer(); //Procesamos el canal con el mensaje del dt

			if (running) {
				// Response for downloader:
				MessageTCP msg = new MessageTCP(buffer);
				System.out.println("RECIBIDO" + msg);
				if (running) { // Si el mensaje es de desconexion, el dt no espera respuesta
					try {
						msg = processMessage(msg);
						dos.write(msg.toByteArray());
						System.out.println("ENVIADO" + msg);
					} catch (Exception e) {
						running = false;
					}
				}
			}
		}
		seeder.finishCommunication(this);
	}
	
	/*
	 * Processes the request message from the downloader
	 */
	/**
	 * Metodo que procesa el mensaje recibido del Downloader
	 * @param msg mensaje recibido
	 * @return msg mensaje de respuesta
	 */
	public MessageTCP processMessage(MessageTCP msg) {
		switch (msg.getOpCode()) {

		case MessageTCP.OP_REQ_FILE:
			/*
			 * Preguntamos si el fichero pedido por dt esta en descarga o lo
			 * tenemos en la base de datos
			 */
			boolean gotFile = false;	// si tenemos el fichero
			int[] trozosFichero = null;	// trozos que tenemos del fichero
			int nChunks = 0;				// numero de trozos

			if (seeder.getCurrentDownloader() != null){
				System.out.println("entra2");
				if (msg.getHash().equals(seeder.getCurrentDownloader().getTargetFile().fileHash)) {
					trozosFichero = seeder.getCurrentDownloader().getChunksDownloadedFromSeeders();
					System.out.println("entra");
					nChunks = trozosFichero.length;
					gotFile = true;
				}
				} else {
					for (FileInfo files : Peer.db.getLocalSharedFiles()) {
						if (msg.getHash().equals(files.fileHash)) {
							nChunks = (int) (files.fileSize / (long) seeder.getChunkSize());
							if (files.fileSize % (long) seeder.getChunkSize() > 0) {
								nChunks++;
							}

							trozosFichero = new int[nChunks];
							for (int i = 0; i < nChunks; i++) {
								trozosFichero[i] = i;
							}
							gotFile = true;
						}
					}
				}
			if (gotFile) {
				System.out.println("got file");
				return new MessageTCP(MessageTCP.OP_GOT_CHUNKS, nChunks, trozosFichero);
				
			} else {
				System.out.println("got no file");
				return new MessageTCP(MessageTCP.OP_GOT_NO_CHUNKS);
			}

		case MessageTCP.OP_REQ_CHUNK:
                    
			int posChunk = msg.getChunk() * MessageTCP.TAM_CHUNK;
			File file = null;
			byte[] dataChunk = new byte[MessageTCP.TAM_CHUNK];
			for (FileInfo files : Peer.db.getLocalSharedFiles())
				if (msg.getHash().equals(files.fileHash)) {
					file = new File(Peer.db.sharedFolderPath, files.fileName);
				}
			try {
				RandomAccessFile raf = new RandomAccessFile(file, "r");
				raf.seek(posChunk); // Stand on posChunk
				raf.read(dataChunk);// Read bytes
				raf.close(); 		// Close the file
			} catch (IOException e) {
				System.out.println("The file " + msg.getHash() + "can't be read");
				e.printStackTrace();
				return new MessageTCP(MessageTCP.OP_INVALID);
			}
			return new MessageTCP(MessageTCP.OP_CHUNK, msg.getHash(), msg.getChunk(), dataChunk);

		case MessageTCP.OP_DISCONNECT:
			running = false;
			return null;

		default:
			System.out.println("Not valid opCode from downloaderThread, op_invalid message sent to dt");
			// Si el OpCode no ha sido valido mandamos un mensaje al download
			// con OP_INVALID y cerramos la conexion;
			return new MessageTCP(MessageTCP.OP_INVALID);
		}
	}

}
