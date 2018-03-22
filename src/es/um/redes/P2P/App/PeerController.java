package es.um.redes.P2P.App;

import java.net.InetSocketAddress;

import es.um.redes.P2P.PeerPeer.Client.Downloader;
import es.um.redes.P2P.PeerPeer.Server.Seeder;
import es.um.redes.P2P.PeerTracker.Client.Reporter;
import es.um.redes.P2P.PeerTracker.Message.Message;
import es.um.redes.P2P.PeerTracker.Message.MessageConf;
import es.um.redes.P2P.PeerTracker.Message.MessageFileInfo;
import es.um.redes.P2P.PeerTracker.Message.MessageQuery;
import es.um.redes.P2P.PeerTracker.Message.MessageSeedInfo;
import es.um.redes.P2P.util.FileInfo;
/**
 * Clase encargada del control del peer y sus mensajes
 * @author Valentin
 * @author Alejandro
 * @author rtitos
 *
 */
public class PeerController implements PeerControllerIface {
	/**
	 * The shell associated to this controller.
	 * Los peers interactuan con esta clase para decidir el rol (downloader, seeder, reporter)
	 */
	private PeerShellIface shell;
	private Reporter client;
	private byte currentCommand;
	private String[] currentCommandArgs = new String[1];
	private FileInfo[] file2Download = null;
	private FileInfo fichero = null;

    public PeerController(Reporter client) {
    	shell = new PeerShell();
    	this.client = client;
    }
    
	
	public byte getCurrentCommand() {
		return currentCommand;
	}

	public void setCurrentCommand(byte command) {
		currentCommand = command;
	}

	public void readCommandFromShell() {
		try {
			shell.readCommand();
		} catch (Exception e) {
			System.out.println("No se ha escrito ninguna línea");
		}
		
		setCurrentCommand(shell.getCommand());
		setCurrentCommandArguments(shell.getCommandArguments());
	}
	
	public void publishSharedFilesToTracker() {
		setCurrentCommand(PeerCommands.COM_ADDSEED);
		processCurrentCommand();
	}

	public void removeSharedFilesFromTracker() {
		setCurrentCommand(PeerCommands.COM_QUIT);
		processCurrentCommand();
	}
	
	public void getConfigFromTracker() {
		setCurrentCommand(PeerCommands.COM_CONFIG);
		processCurrentCommand();
	}
	
	public void getSeedsFromTracker() {
		setCurrentCommand(PeerCommands.COM_DOWNLOAD);
		processCurrentCommand();
	}

	public boolean shouldQuit() {
		return currentCommand == PeerCommands.COM_QUIT;
	}

	@Override
	public void setCurrentCommandArguments(String[] args) {
		currentCommandArgs = args;
	}

	@Override
	public void processCurrentCommand() {
		
		Message request = null;
		Message recept = null;
		
		switch (currentCommand) {
		case PeerCommands.COM_CONFIG:
			request = createMessageFromCurrentCommand();
			break;
			
		case PeerCommands.COM_ADDSEED:
			request = createMessageFromCurrentCommand();
			break;
		case PeerCommands.COM_QUERY:
			request = createMessageFromCurrentCommand();
			break;
		case PeerCommands.COM_DOWNLOAD:
			request = createMessageFromCurrentCommand();
			break;
		case PeerCommands.COM_QUIT:
			request = createMessageFromCurrentCommand();
			break;
		case PeerCommands.COM_SHOW:
			request = createMessageFromCurrentCommand();
			break;
			
		default:
			break;
			
		}
		if (request != null)
			System.out.println(request);
		if (request != null)
			recept = client.conversationWithTracker(request);
		if (recept != null)
			processMessageFromTracker(recept);
	}

	@Override
	public Message createMessageFromCurrentCommand() {
		
		switch (currentCommand) {
		
		case PeerCommands.COM_SHOW:
			for (FileInfo file : Peer.db.getLocalSharedFiles()) {
				System.out.println(file);
			}

		break;
		case PeerCommands.COM_CONFIG:
			return Message.makeGetConfRequest();

		case PeerCommands.COM_ADDSEED:
			return Message.makeAddSeedRequest(Peer.port, Peer.db.getLocalSharedFiles());
					
		case PeerCommands.COM_QUERY:
			boolean validFilter = false;
			byte filterType = MessageQuery.FILTERTYPE_ALL;
			String filterValue = "";
			int n = 0;
			long value = 0;
			if(currentCommandArgs[0] != null && currentCommandArgs[0] != "" ){
				if(currentCommandArgs[1] != null && currentCommandArgs[1] != "" ){
					
					if(currentCommandArgs[1].endsWith("KB")){ //para comprobar si se ha introducido una medida
						n = 1;
						currentCommandArgs[1] = String.valueOf(currentCommandArgs[1].subSequence(0, currentCommandArgs[1].length()-2));
					}else if(currentCommandArgs[1].endsWith("MB")){
						n = 2;
						currentCommandArgs[1] = String.valueOf(currentCommandArgs[1].subSequence(0, currentCommandArgs[1].length()-2));
					}else if(currentCommandArgs[1].endsWith("GB")){
						n = 3;
						currentCommandArgs[1] = String.valueOf(currentCommandArgs[1].subSequence(0, currentCommandArgs[1].length()-2));
					}
					
					value = Long.parseLong(currentCommandArgs[1]); //para actualizar el valor en bytes del filtro
					for(int i = 0; i < n; i++){
						value *= 1024;
					}
					currentCommandArgs[1] = String.valueOf(value);
					
					switch(currentCommandArgs[0]){ //Type de filtro
					case "-n":
						validFilter = MessageQuery.isValidQueryFilterValue(MessageQuery.FILTERTYPE_NAME,currentCommandArgs[1]);
						if (validFilter){
							filterType = MessageQuery.FILTERTYPE_NAME;
							filterValue = currentCommandArgs[1];
						}
						break;
					case "-lt":
						validFilter = MessageQuery.isValidQueryFilterValue(MessageQuery.FILTERTYPE_MAXSIZE,currentCommandArgs[1]);
						if (validFilter){
							filterType = MessageQuery.FILTERTYPE_MAXSIZE;
							filterValue = currentCommandArgs[1];
						}
						break;
					case "-ge":
						validFilter = MessageQuery.isValidQueryFilterValue(MessageQuery.FILTERTYPE_MINSIZE,currentCommandArgs[1]);
						if (validFilter){
							filterType = MessageQuery.FILTERTYPE_MINSIZE;
							filterValue = currentCommandArgs[1];
						}
						break;
					}
				}			
			}
			return Message.makeQueryFilesRequest(filterType, filterValue);	
			
		case PeerCommands.COM_DOWNLOAD:
			int matches = 0;
			String hash = "";
			if (file2Download != null && currentCommandArgs[0] != null){
				for (FileInfo file : file2Download) 
					if (file.fileHash.contains(currentCommandArgs[0])){
						hash = file.fileHash;//Guardamos el hash en un string, para mandarlo
						fichero = file;//Ficheros que deseamso descargar
						matches++;
					}
			}
			if (matches == 1)
				return Message.makeGetSeedsRequest(hash);
			else {
				System.out.println("La subcadena del hash introducido conincide con el hash de más de 1 fichero");
				System.out.println("Introduce otro hash: ");
			}
			break;
		
		case PeerCommands.COM_QUIT:
			return Message.makeRemoveSeedRequest(Peer.port, Peer.db.getLocalSharedFiles());			
		}
		return null;
	}

	@Override
	public void processMessageFromTracker(Message response) {	
		
		switch (response.getOpCode()) {
		case Message.OP_SEND_CONF:
			Peer.chunk_size = ((MessageConf) response).getChunkSize();
			System.out.println(response);
			break;
		case Message.OP_ADD_SEED_ACK:
			System.out.println(response);
			break;
		case Message.OP_FILE_LIST:
			recordQueryResult(((MessageFileInfo) response).getFileList());
			break;
		case Message.OP_SEED_LIST:
			
			InetSocketAddress[] array = ((MessageSeedInfo)response).getSeedList();
			System.out.println("Seeds sharing the file: " + array.length);
			System.out.println("IP of the seeders sharing the file: ");
			for (InetSocketAddress it : array) {
				System.out.println(it);
			}
			downloadFileFromSeeds(array, fichero);
			break;
		case Message.OP_REMOVE_SEED_ACK:
			System.out.println(response);
			break;
			
		default:
			break;
		}
		
	}

	/**
	 * Metodo que imprime los ficheros del tracker (no se incluyen los propios)
	 */
	@Override
	public void recordQueryResult(FileInfo[] fileList) {
		
		FileInfo[] ficherosPropios = Peer.db.getLocalSharedFiles();
		file2Download = new FileInfo[fileList.length - ficherosPropios.length];
		int i = 0;
		
	    System.out.println("Files of the tracker: ");
	    boolean encontrado = false;
		for (FileInfo file : fileList) {
			for (FileInfo myFile : ficherosPropios){
				if (file.fileHash.equals(myFile.fileHash))
					encontrado = true;
			}
			if (!encontrado){
				System.out.println(file);
				file2Download[i] = file;
				i++;
			}
			encontrado = false;
			
		}
		
	}

	@Override
	public void printQueryResult() {
		setCurrentCommand(PeerCommands.COM_QUERY);
		processCurrentCommand();
	}
	
	/**
	 * Metodo que descarga el fichero a partir de la lista de seeders
	 */
	@Override
	public void downloadFileFromSeeds(InetSocketAddress[] seedList, FileInfo file) {
		Downloader downloader = new Downloader(file, this);
		Seeder.setCurrentDownloader(downloader);
		downloader.downloadFile(seedList);
	}
	
	/**
	 * Metodo que actualiza/añade el fichero al tracker
	 * @param files fichero a actualizar
	 * @param seederPort puerto del seeder
	 */
	public void updateFiles(FileInfo []files, int seederPort){
		client.updateFilesTracker(files, seederPort);
	}
	

}