package es.um.redes.P2P.App;


/**
 * Clase shell encargada de la consola de comandos
 * @author Valentin
 * @author Alejandro
 * @author valy rtitos
 *
 */
public class PeerShell implements PeerShellIface {
	
	private java.util.Scanner reader;
	byte command = PeerCommands.COM_INVALID;
	/* Dos posibles valores en el fyltertype del query*/
	String [] commandArgs = new String[2];
	
	public PeerShell() {
		reader = new java.util.Scanner(System.in);
		}

	@Override
	public byte getCommand() {
		return command;
	}

	@Override
	public String[] getCommandArguments() {
		return commandArgs;
	}

	@Override
	public void readCommand() {
		boolean opcion = false;
		while (!opcion) {
			System.out.print("> ");
			String[] st = new String(reader.nextLine()).split(" ");
			if (st.length == 0) {
				continue;	
			}
			
			command = PeerCommands.stringToCommand(st[0]);
			for(int i = 0; i < commandArgs.length; i++){
				commandArgs[i] = "";
			}
			if (st.length == 2){ //comando download
				commandArgs[0] = st[1];
			}else if(st.length == 3){ //comando query con filtros
				commandArgs[0] = st[1];
				commandArgs[1] = st[2];
			}
			
			opcion = true;
			switch (command) {
			
			case PeerCommands.COM_INVALID:
				System.out.println("Invalid command");
				opcion = false;
				continue;
			case PeerCommands.COM_QUERY:
				System.out.println("Query: ");
				break;
			case PeerCommands.COM_DOWNLOAD:
				System.out.println("Download: ");
				
				break;
			case PeerCommands.COM_GET_SEEDS:
				System.out.println("Get seeds: ");
				break;
			case PeerCommands.COM_HELP:
				PeerCommands.printCommandsHelp();
				continue;
			case PeerCommands.COM_QUIT:
				return;
			
			}
			
		}
		
	}

}
