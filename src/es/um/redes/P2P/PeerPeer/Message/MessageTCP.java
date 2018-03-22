package es.um.redes.P2P.PeerPeer.Message;

import java.nio.ByteBuffer;

import es.um.redes.P2P.App.Peer;

import es.um.redes.P2P.util.FileDigest;
/**
 * Clase que contiene el tipo de mensaje que se intercambian entre peers (mediante protocolo TCP)
 * @author Valentin
 * @author Alejandro
 * @author rtitos
 *
 */
public class MessageTCP {
	
	public static final byte OP_INVALID = 0;
	public static final byte OP_REQ_FILE = 1;
	public static final byte OP_GOT_CHUNKS = 2;
	public static final byte OP_GOT_NO_CHUNKS = 3;
	public static final byte OP_REQ_CHUNK = 4;
	public static final byte OP_CHUNK = 5;
	public static final byte OP_DISCONNECT = 6;
	
	
	public static final int FIELD_TYPE_BYTES = 1;
	public static final int FIELD_HASH_BYTES = 20;
	public static final int FIELD_CHUNKS_BYTES = 4;
	public static final int FIELD_ID_CHUNK_BYTES = 4;
	public static final int TAM_CHUNK = Peer.chunk_size;
	
	private int tamMensaje = 0;
	
	private byte opCode = 0;
	private int chunk = 0;
	private byte[] dataChunk = null;
	private String hash = null;
	private int nChunks = 0;
	private int [] arrayTrozos = null;
	
	// TOTALES:
	
	//MENSAJE CHUNK
	public static final int mTAM_CHUNK = FIELD_TYPE_BYTES+FIELD_HASH_BYTES+FIELD_ID_CHUNK_BYTES+TAM_CHUNK;
	
	// MENSAJE CONTROL
	public static final int mTAM_CONTROL = FIELD_TYPE_BYTES;
	
	// MENSAJE REQ_CHUNK
	public static final int mTAM_REQ_CHUNK = FIELD_TYPE_BYTES + FIELD_HASH_BYTES + FIELD_ID_CHUNK_BYTES;
	
	// MENSAJE REQ_FILE
	public static final int mTAM_REQ_FILE = FIELD_TYPE_BYTES + FIELD_HASH_BYTES;

		
	public MessageTCP(byte[] buffer){
		fromByteArray(buffer);
	}
	
	//MENSAJE CHUNK
	public MessageTCP(byte opCode, String hash, int chunk, byte[] dataChunk){
		tamMensaje = FIELD_TYPE_BYTES+FIELD_HASH_BYTES+FIELD_ID_CHUNK_BYTES+TAM_CHUNK;
		this.opCode = opCode;
		this.hash = hash;
		this.chunk = chunk;
		this.dataChunk = dataChunk;
	}
	// INFORMACION -> GOT_NO_CHUNKS Y DISCONNECT
	public MessageTCP(byte opCode){
		tamMensaje = FIELD_TYPE_BYTES;
		this.opCode = opCode;
	}
	
	// REQ_CHUNK
	public MessageTCP(byte opCode, String hash, int id_chunk){
		tamMensaje = FIELD_TYPE_BYTES + FIELD_HASH_BYTES + FIELD_ID_CHUNK_BYTES;
		this.opCode = opCode;
		this.hash = hash;
		chunk = id_chunk;
	}
	
	// GOT_CHUNKS
	public MessageTCP(byte opCode, int nChunks, int [] arrayTrozos){
		tamMensaje = FIELD_TYPE_BYTES + FIELD_CHUNKS_BYTES + nChunks*FIELD_ID_CHUNK_BYTES;
		this.opCode = opCode;
		this.nChunks = nChunks;
		this.arrayTrozos = arrayTrozos;
	}
	
	// REQ FILE
	public MessageTCP(byte opCode, String hash){
		tamMensaje = FIELD_TYPE_BYTES + FIELD_HASH_BYTES;
		this.opCode = opCode;
		this.hash = hash;
	}
	
	
	public byte[] getDataChunk(){
		return dataChunk;
	}
	public byte getOpCode() {
		return opCode;
	}
	public int getChunk(){
		return chunk;
	}
	public String getHash(){
		return hash;
	}
	public int[] getArrayTrozos(){
		return arrayTrozos;
	}
	
	/**
	 * Metodo que devuelve la longitud de un mensaje GOT_CHUNKS con una lista de n trozos 
	 * @param n trozos
	 */
	public static int calculateGotChunksSize(int n){
		return (FIELD_TYPE_BYTES + FIELD_CHUNKS_BYTES + n*FIELD_ID_CHUNK_BYTES);
	}
	
	public byte[] toByteArray()
	{
		ByteBuffer buf = ByteBuffer.allocate(tamMensaje);
		
		switch (opCode) {
		case OP_DISCONNECT:
			buf.put((byte)getOpCode());
			break;
		case OP_GOT_NO_CHUNKS:
			buf.put((byte)getOpCode());
			break;
		case OP_CHUNK:
			buf.put((byte)getOpCode());
			buf.put(FileDigest.getDigestFromHexString(hash));
			buf.putInt(chunk);
			buf.put(dataChunk);
			break;
		case OP_GOT_CHUNKS:
			buf.put(opCode);
			buf.putInt(nChunks);
			for (int i = 0; i<nChunks; i++){
				buf.putInt(arrayTrozos[i]);
			}
			break;
		case OP_REQ_CHUNK:
			buf.put(opCode);
			buf.put(FileDigest.getDigestFromHexString(hash));
			buf.putInt(chunk);
			break;
		case OP_REQ_FILE:
			buf.put(opCode);
			buf.put(FileDigest.getDigestFromHexString(hash));
			break;
		case OP_INVALID:
			buf.put((byte) getOpCode());
			break;
		default:
			System.out.println("Not valid opCode $[trying to convert into byte array]$");
			break;
		}


		return buf.array();
	}
	
	public void fromByteArray(byte[] array) {
		
		ByteBuffer buf = ByteBuffer.wrap(array);
		byte bOpCode = buf.get();
		
		switch (bOpCode) {
		case OP_CHUNK:		
			opCode = bOpCode;			
			byte[] hashArray = new byte[FIELD_HASH_BYTES];
			buf.get(hashArray, 0, FIELD_HASH_BYTES);
			hash = new String(FileDigest.getChecksumHexString(hashArray));			
			chunk = buf.getInt();
			byte[] chunkArray = new byte[TAM_CHUNK];
			buf.get(chunkArray, 0, TAM_CHUNK);
			dataChunk = chunkArray;
			break;		
		case OP_DISCONNECT:			
			opCode = bOpCode;
			break;			
		case OP_GOT_CHUNKS:			
			opCode = bOpCode;
			nChunks = buf.getInt();
			arrayTrozos = new int[nChunks];
			for (int i = 0; i<nChunks; i++)
				arrayTrozos[i] = buf.getInt();
			break;			
		case OP_GOT_NO_CHUNKS:			
			opCode = bOpCode;
			break;		
		case OP_REQ_CHUNK:			
			opCode = bOpCode;
			byte[] hashArray2 = new byte[FIELD_HASH_BYTES];
			buf.get(hashArray2, 0, FIELD_HASH_BYTES);
			hash = new String(FileDigest.getChecksumHexString(hashArray2));
			chunk = buf.getInt();
			break;			
		case OP_REQ_FILE:		
			opCode = bOpCode;
			byte[] hashArray3 = new byte[FIELD_HASH_BYTES];
			buf.get(hashArray3, 0, FIELD_HASH_BYTES);
			hash = new String(FileDigest.getChecksumHexString(hashArray3));
			break;			
		case OP_INVALID:		
			opCode = bOpCode;
			break;			
		default:			
			System.out.println("TYPE: "+bOpCode);
			System.out.println("Cannot convert with this opCode $[trying to convert into messagetcp from byte array]$");
			break;			
		}	
	}	
	
	private String opCodestring(){
		switch(opCode){
		case OP_REQ_FILE:
			return "REQ_FILE";
		case OP_GOT_CHUNKS:
			return "GOT_CHUNKS";
		case OP_GOT_NO_CHUNKS:
			return "GOT_NO_CHUNKS";
		case OP_REQ_CHUNK:
			return "REQ_CHUNK";
		case OP_CHUNK:
			return "CHUNK";
		case OP_DISCONNECT:
			return "DISCONNECT";
	    default:
	    	return "INVALID";
		}
	}
	
	public String toString(){		
		StringBuffer sb = new StringBuffer();
		sb.append("Type: "+opCodestring());
		sb.append(" Hash: "+getHash());
		sb.append(" Id chunk: "+getChunk());
		sb.append(" Data: "+getDataChunk());	
		if (arrayTrozos != null){
			sb.append(" array trozos: ");
			for (int i = 0; i<arrayTrozos.length; i++)
				sb.append(arrayTrozos[i]);
				sb.append(" ");
		}	
		return sb.toString();
	}
	

}
