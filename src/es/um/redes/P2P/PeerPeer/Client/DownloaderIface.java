package es.um.redes.P2P.PeerPeer.Client;

import java.net.InetSocketAddress;

import es.um.redes.P2P.util.FileInfo;

public interface DownloaderIface {

	//IMPORTANTE
	//En esta interfaz no están definidos los métodos necesarios para gestionar la lista de trozos
	//que se están disponibles o que se están descargando. Deberán ser definidos en la clase que la instancia.
	
	//Devuelve la información sobre el archivo que se está descargando
	FileInfo getTargetFile();

	//Obtiene la lista de Seeds desde los cuales se está descargando el archivo
	//InetSocketAddress[] getSeeds();

	//Devuelve el número total de Chunks en los que está compuesto el archivo
	int getTotalChunks();

	//Inicia el proceso de descarga del archivo a partir de la lista de Seeds
	boolean downloadFile(InetSocketAddress[] seedList);

	//Devuelve el número de chunks que han sido descargados de cada uno de los Seeders
	int[] getChunksDownloadedFromSeeders();

	//Informa si la descarga del fichero ya se ha completado
	boolean isDownloadComplete();
	

}