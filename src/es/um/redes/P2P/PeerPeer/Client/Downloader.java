package es.um.redes.P2P.PeerPeer.Client;

import java.net.InetSocketAddress;
import java.text.DecimalFormat;
import java.util.ArrayList;

import es.um.redes.P2P.App.Peer;
import es.um.redes.P2P.App.PeerController;

import es.um.redes.P2P.PeerPeer.Server.Seeder;

import es.um.redes.P2P.util.FileInfo;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Clase encargada de la descarga de un fichero a partir de la lista de seeders
 * disponible
 * 
 * @author Valentin
 * @author Alejandro
 * @author rtitos
 * 
 */
public class Downloader implements DownloaderIface {

	/*
	 * Gestion de trozos, el downloaderThread comunica con esta clase Downloader
	 * tiene la informacion de los trozos (los que se tienen y faltan)
	 */

	private FileInfo fileDownloading = null;
	private String route = null;
	DownloaderThread[] tDownload = null;
	private int peeractive = 0;
	private volatile int totalChunks = 0;
	private int lastChunk = 0;
	private int receives = 0;
	private volatile boolean[][] chunksPedidos; // 2 filas: 0 los que se han
												// pedido, 1 file los que se han
												// recibido
	private volatile long nChunks[];
	PeerController peerC = null; // Comunicación con el tracker
	ArrayList<Integer>[] trozosPorHilo = null; // Por cada hilo, su lista de
												// trozos
	int[] ptdt = null; // punteros para cada lista de trozos para hacer
						// operaciones mas eficientes

	/* Variables concurrencia */
	private ReentrantLock bufferaccess = new ReentrantLock(); // variables para
																// el acceso al
																// buffer
	private int receiversactive = 0; // para las entregas/peticiones
	private Condition requestors = bufferaccess.newCondition(); // peticiones
	private boolean selectingidck = false;

	public Downloader(FileInfo file, PeerController peerC) {
		fileDownloading = file;
		this.peerC = peerC;
		route = new String(Peer.db.getSharedFolderPath());
	}

	/**
	 * Metodo que devuelve el fichero que se esta descargando
	 */
	@Override
	public FileInfo getTargetFile() {
		return fileDownloading;
	}

	/**
	 * Devuelve el total de chunks
	 */
	@Override
	public int getTotalChunks() {
		return totalChunks;
	}

	/**
	 * Metodo que devuelve la ruta/path del fichero
	 * 
	 * @return route
	 */
	public String getRoute() {
		return this.route;
	}

	/**
	 * Metodo encargado de la descarga de un fichero a partir de la lista de
	 * seeders Una vez que sabemos el total de chunks, creamos tantos hilos
	 * download como seeders hayan.
	 */
	@Override
	public boolean downloadFile(InetSocketAddress[] seedList) {

		FileInfo[] files = new FileInfo[1];
		files[0] = fileDownloading;

		totalChunks = (int) fileDownloading.fileSize / Peer.chunk_size;
		// Resto, limite superior del tamaño del archivo:
		if (fileDownloading.fileSize % Peer.chunk_size > 0)
			totalChunks++;

		receives = 0;
		lastChunk = totalChunks - 1;
		nChunks = new long[seedList.length];
		chunksPedidos = new boolean[2][totalChunks]; // recibidos
		tDownload = new DownloaderThread[seedList.length];
		trozosPorHilo = new ArrayList[seedList.length];
		ptdt = new int[seedList.length];

		int i = 0;
		for (InetSocketAddress seed : seedList) {
			tDownload[i] = new DownloaderThread(seed, fileDownloading, this, i);
			peeractive++; // seeder de los que se pueden descargar
			try {
				tDownload[i].start();
				i++;
			} catch (Exception e) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Metodo para saber que trozos hemos descargado ya. Usado para compartir
	 * ficheros que estan en proceso de descarga
	 */
	public int[] getChunksDownloadedFromSeeders() {
		bufferaccess.lock();
		try {
			int n = 0;
			int[] aux = new int[chunksPedidos.length];
			for (int i = 0; i < chunksPedidos.length; i++) {
				if (chunksPedidos[1][i] == true) { // si se ha pedido ese chunk
					aux[n] = i;
					n++;
				}
			}
			int[] ckdownloaded = new int[n];
			for (int i = 0; i < n; i++) {
				ckdownloaded[i] = aux[i];
			}
			return ckdownloaded;
		} finally {
			bufferaccess.unlock();
		}
	}

	@Override
	public boolean isDownloadComplete() {
		if (receives == totalChunks) {
			System.out.println("Download finished correctly");
			showPercentage();
			Peer.db.addDownloadedFile(fileDownloading); // a�adimos el fichero a
														// nuestra base de datos
			// peerC.publishSharedFilesToTracker(); //Informamos al tracker de
			// que compartimos el fichero
			return true;
		} else {
			if (peeractive == 0) {
				System.out.println("No seeders alive to download from, download failed");
				// TODO eliminar el fichero y enviar el removeseed del fichero
				// al tracker
				return false;
			} else {
				System.out.println("The download is not finished yet");
				return false;
			}
		}

	}

	/**
	 * Metodo que devuelve el ultimo chunk
	 * 
	 * @return lastChunk
	 */
	public int getLastChunk() {
		return lastChunk;
	}

	/**
	 * Metodo para que los downloader thread informen al downloader de que su
	 * descarga ha fallado -> su lista de trozos se libera
	 * 
	 * @param iddt
	 *            identificador del downloader thread
	 */
	public void connectionfailedfinished(int iddt) {
		System.out.println("Download MSG: DT_id= " + iddt + " has finished his connection");
		peeractive--;
		if (trozosPorHilo[iddt] != null && !trozosPorHilo[iddt].isEmpty())
			trozosPorHilo[iddt].clear();
		if (peeractive == 0 && receives != totalChunks) { // Si el numero de
															// seedersactivos es
															// 0, mostramos
			// si la descarga se ha completado correctamente
			isDownloadComplete();
			Seeder.finishDownload();
		}
	}

	/**
	 * Metodo que muestra el porcentaje de descarga de cada peer
	 */
	public void showPercentage() {
		System.out.println("file: " + fileDownloading.fileName);
		DecimalFormat df = new DecimalFormat("0.0");
		for (int i = 0; i < nChunks.length; i++) {
			double percent = (100 * nChunks[i]) / totalChunks;

			System.out.println("Peer " + (i + 1) + "Downloaded: " + df.format(percent) + "%" + "Chunks: " + nChunks[i]
					+ "/" + totalChunks);
		}
	}

	/**
	 * Metodo que actualiza los trozos recibidos Se realiza en exclusion mutua Y
	 * los elimina de la lista de trozos de cada dt que los tenga
	 * 
	 * @param chunk
	 *            id
	 * @param iddt
	 *            identificador del downloader thread
	 */
	public void receiveChunk(int chunk, int iddt) {

		receiversactive++; // variable para indicar que hay recepciones en
							// espera
		bufferaccess.lock();
		try {
			if (chunksPedidos[1][chunk] != true) { // Posible descargar//
													// compartida de un mismo//
													// trozo
				chunksPedidos[1][chunk] = true;
				receives++;

				nChunks[iddt]++; // se incrementan numero de chunks del
									// downloaderThread determinado
				if (receives == totalChunks) { // Descarga finalizada
					isDownloadComplete();
				}
				// eliminar el id_chunk de todas las listas
				for (int i = 0; i < trozosPorHilo.length; i++) {
					if (trozosPorHilo[i] != null && trozosPorHilo[i].contains((Object) chunk)) {
						if (trozosPorHilo[i].indexOf((Object) chunk) < ptdt[i]) {
							ptdt[i]--; // Decrementamos el puntero si el
										// elemento estaba en una posicion
										// inferior a la que apunta
						}
						if (trozosPorHilo[i].indexOf((Object) chunk) == ptdt[i] && 
								(trozosPorHilo[i].indexOf((Object) chunk) == trozosPorHilo[i].size()-1))
							ptdt[i] = 0;
						
						trozosPorHilo[i].remove((Object) chunk);
					}
				}
			}
			receiversactive--;
			if (receiversactive == 0) { // si no hay mas recepciones en espera
										// se procede a despertar a los hilos
				requestors.signalAll(); // que quieren solicitar id_chunks para
										// descargar
			}
		} finally {
			// TODO informar al tracker de que compartimos parte del fichero
			// Peer.db.addDownloadedFile(fileDownloading);
			// peerC.publishSharedFilesToTracker();
			FileInfo[] file = new FileInfo[1];
			file[0] = fileDownloading;
			peerC.updateFiles(file, Seeder.getPort());
			bufferaccess.unlock();
		}
	}

	/**
	 * Metodo para marcar como pedido un id_chunk
	 * 
	 * @param chunk
	 */
	public void requestChunk(int chunk) {
		chunksPedidos[0][chunk] = true;
	}

	/**
	 * Metodo para informar al downloader de la lista de trozos que puede
	 * descargar un hilo dt concreto, no hace falta synchronized porque acceden
	 * a posiciones diferentes
	 * 
	 * @param iddt
	 * @param listatrozos
	 */
	public void setmyIdChunklist(int iddt, int[] listatrozos) {
		trozosPorHilo[iddt] = new ArrayList<Integer>(); // Colocamos en la
														// posicion del iddt su
														// lista de trozos para
														// descargar
		for (int i = 0; i < listatrozos.length; i++) { // Rellenamos la lista
														// con los elementos del
														// array de id_chunks
			trozosPorHilo[iddt].add(listatrozos[i]);
		}
		ptdt[iddt] = 0; // Inicilizamos el iterador de la lista

		for (int i = 0; i < trozosPorHilo.length; i++) {
			if (trozosPorHilo[i] != null)
				for (int j = 0; j < trozosPorHilo[i].size(); j++)
					System.out.print(trozosPorHilo[i].get(j) + " ");
		}
	}

	/**
	 * Metodo para saber si un hilo dt ha terminado su descarga
	 * 
	 * @param iddt
	 */
	public boolean downloadfinisheddt(int iddt) {
		return trozosPorHilo[iddt].isEmpty();
	}

	/**
	 * Metodo para saber si todos los trozos de un downloader thread han sido
	 * pedidos ya
	 * 
	 * @param iddt
	 */
	public boolean allChunksRequestedFromDt(int iddt) {
		for (int i = 0; i < trozosPorHilo[iddt].size(); i++) {
			if (chunksPedidos[0][trozosPorHilo[iddt].get(i)] == false) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Metodo de consulta para los downloader thread que les devuelva el
	 * siguiente id_chunk que deben descargar
	 * 
	 * @param iddt
	 */
	public boolean nextChunk(int iddt) {
		bufferaccess.lock();
		try {
			while (receiversactive > 0 || selectingidck) {
				try {
					requestors.await();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			selectingidck = true;	// hay ya un hilo pidiendo trozos (este)
			if (!trozosPorHilo[iddt].isEmpty() && receives != totalChunks) {
				boolean chunkSelected = false;

				if (!allChunksRequestedFromDt(iddt)) { // Hay trozos sin pedir
														// por ningun hilo
					while (!chunkSelected) {
						int idck = trozosPorHilo[iddt].get(ptdt[iddt]); // id_chunk
						if (chunksPedidos[0][idck] == false) { // buscamos el
																// primer
																// id_chunk no
																// pedido
							requestChunk(idck); // marcamos el chunk como pedido
							ptdt[iddt]++; // incrementados el puntero, no hace
											// falta modulo porque es seguro
											// R(iddt,lista.size)
							// return idck; //Le asignamos el chunk al dt
							tDownload[iddt].setidchunk(idck);
							return true;
						}
						ptdt[iddt] = ((ptdt[iddt] + 1) % trozosPorHilo[iddt].size());
					}

				} else {
					int idChunk = 0;
					while (!chunkSelected) {
						if (ptdt[iddt] <= trozosPorHilo[iddt].size())
							idChunk = trozosPorHilo[iddt].get(ptdt[iddt]);
						if (chunksPedidos[1][idChunk] == false) { // trozos
																	// pedidos
																	// pero no
																	// recibidos
							ptdt[iddt] = ((ptdt[iddt] + 1) % trozosPorHilo[iddt].size());
							tDownload[iddt].setidchunk(idChunk);
							return true;
						}
						ptdt[iddt] = ((ptdt[iddt] + 1) % trozosPorHilo[iddt].size());
					}
				}
			}
			return false; // asignaci�n fallida, el dt no debe seguir
							// descargando
		} finally {
			if (receiversactive == 0) { // si no hay recepciones en espera,
										// despertarmos a los hilos que quieren
										// pedir trozo
				requestors.signalAll();
			}
			selectingidck = false;
			bufferaccess.unlock();
		}
	}
}
