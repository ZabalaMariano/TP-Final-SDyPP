package TP_Final_SDyPP.Otros;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

public class Mensaje implements Serializable {

	private static final long serialVersionUID = -6185343867071731137L;

	public enum Tipo{ 
		FIND_FILE, //Enviado por el peer cuando solicita un archivo al master (puede ser un substring del nombre de un archivo)
		REQUEST,//Enviado por el peer cuando ya consiguió el nombre completo del archivo y espera por los peer servidores para iniciar la descarga
		ERROR,//Devuelto por Master o peer con un mensaje string que indica que paso
		ACK,
		SEND_FILE,//Peer servidor envia el archivo (Master no participa)
		FILES_AVAILABLE, //Lo envia el master en respuesta a una busqueda de un archivo
		FILE_UNAVAILABLE,//Enviado por peer servidor cuando no posee el archivo a descargar. Ya no es seed.
		CHECK_AVAILABLE,
		SET_PRIMARY, //Enviado por un master cuando el se define como primario
		CHANGE_PRIMARY, //Es utilizado cuando un master detecta que se cayó el primario y encuentra su reemplazo
		TRACKER_REGISTER, //Luego de que un tracker se configura, envía este mensaje al primario para registrarse y recibir actualizaciones
		TRACKER_UPDATE, //Utilizado para inicializar un tracker, o para hacer una replicación ante una actualización
		TRACKER_PRIMARIO,//Tracker responde a GET_PRIMARIO
		GET_PRIMARIO, //Peer pide primario a su tracker para: enviarle JSON, pedir IP privada y publica de si mismo.
		REPLICATE_FILE, //Primario envía nuevo JSON a todos los trackers
		FILE,
		GET_FILE,
		DOWNLOAD, //Pide archivo a peer
		SEND_SEED, //envia tracker a primario, luego de recibir un DOWNLOAD
		SWARM, //enviado por tracker a peer luego de DOWNLOAD. Enviado por peer al pedir nuevo swarm
		EXIT, //El cliente escribe salir
		PIECES_AVAILABLE, //pide array del peer que indica que partes del archivo descargó
		PIECES_AVAILABLE_CLOSE,
		LOADDOWN, //peer cliente le envia a peer servidor cuando no tiene más partes para descargar de él
		GET_PIECE, //indico a peer servidor parte que necesito que me envíes
		QUIT_SWARM, //Retira a peer de un swarm segun socket y hash.
		SEND_QUIT_SWARM, //enviado por tracker a primario para replicar el quitar un peer de la tabla de seeds.
		COMPLETE, //enviado por peer a tracker para no aparecer en swarms futuros
		FREE,//enviado por peer a tracker para seguir apareciendo en swarms futuros
		SEND_COMPLETE,//enviado por tracker a primario, luego de recibir "complete"
		SEND_FREE,//enviado por tracker a primario, luego de recibir "free"
		NEW_SEED,//peer a tracker cuando finaliza descarga
		SEND_NEW_SEED,//enviado por tracker a primario, luego de recibir "new_seed"
		ALIVE,
		GET_TRACKERS,
		GET_DB,
		GET_FILES_OFFERED,
		GET_PUBLIC_IP,
		PEER_PUBLIC_IP, 
		KEEP_SOCKET,
		PEDIDO_P2P,
		RESPUESTA_P2P
	}
	
	public Tipo tipo;
	public String string1;
	public String string2;
	public String string3;
	public String string4;
	public int int1;
	public int int2;
	public boolean bool1;
	public File file;
	public TrackerInfo tracker;
	public ArrayList<String> hashes;
	public ArrayList<TrackerInfo> listaTrackers;
	public Object lista;//lista swarm(seedtable) y filesAvailable(filetable)
	public PublicKey kpub;
	public SecretKey key;
	public byte[] keyEncriptada;
	public byte[] datosEncriptados;
	
	//Constructores
	public Mensaje() {}
	
	public Mensaje(Tipo tipo) {//LOADDOWN, ALIVE, ERROR, GET_PRIMARIO, GET_PUBLIC_IP
		this.tipo = tipo;
	}
	
	public Mensaje(Tipo tipo, String string1) {//GET_FILE (string1 = hash) 
		this.tipo = tipo;                     //REQUEST (string1 = nombreArchivo)
		this.string1 = string1;				  //ERROR (string1 = Mensaje error)
	}										  //FIND_FILE (string1 = file name)	
											  //PEER_PUBLIC_IP (string1 = ip)										  
	
	public Mensaje(Tipo tipo, String string1, String string2) {//PIECES_AVAILABLE
		this.tipo = tipo;
		this.string1 = string1;
		this.string2 = string2;
	}
	
	public Mensaje(Tipo tipo, String string1, String string2, String string3) {//SEND_QUIT_SWARM y SEND_NEW_SEED
		this.tipo = tipo;										   
		this.string1 = string1;										
		this.string2 = string2;
		this.string3 = string3;
	}
	
	public Mensaje(Tipo tipo, String string1, String string2, String string3, String string4) {//DOWNLOAD y SEND_SEED
		this.tipo = tipo;										   
		this.string1 = string1;										
		this.string2 = string2;
		this.string3 = string3;
		this.string4 = string4;
	}
	
	public Mensaje(Tipo tipo, String string1, String string2, int int1) {//TRACKER_PRIMARIO y ACK (en ThreadTracker-getSocketPeer)
		this.tipo = tipo;												//REPLICATE_FILE (string = nombreJSON) o sea su hashFinal
		this.string1 = string1;											//SEND_FILE (string=hashJSON)
		this.string2 = string2;											//QUIT_SWARM (string = hash)
		this.int1 = int1;												//COMPLETE string=hash
	}																	//NEW_SEED string = hash
																		//SWARM string hash

	public Mensaje(Tipo tipo, String string1, String string2, String string3, String string4, int int1) {//P2P peerOrigen (threadcliente) a tracker
		this.tipo = tipo;										   						
		this.string1 = string1;//ipExterna peer destino														
		this.string2 = string2;//ipInterna peer destino
		this.string3 = string3;//ipExterna peer origen
		this.string4 = string4;//ipInterna peer origen
		this.int1 = int1;//portInterno peer origen
	}
	
	public Mensaje(Tipo tipo, TrackerInfo tracker) {//SET_PRIMARY , CHANGE_PRIMARY
		this.tipo = tipo;
		this.tracker = tracker;
	}
	
	public Mensaje(Tipo tipo, String string1, TrackerInfo tracker) {//SEND_COMPLETE
		this.tipo = tipo;										   										
		this.string1 = string1;
		this.tracker = tracker;
	}
	
	public Mensaje(Tipo tipo, String string1, String string2, String string3, TrackerInfo tracker) {//SEND_QUIT_SWARM, SEND_NEW_SEED
		this.tipo = tipo;										   
		this.string1 = string1;										
		this.string2 = string2;
		this.string3 = string3;
		this.tracker = tracker;
	}
	
	public Mensaje(Tipo tipo, String string1, String string2, String string3, String string4, TrackerInfo tracker) {//SEND_SEED
		this.tipo = tipo;										   
		this.string1 = string1;										
		this.string2 = string2;
		this.string3 = string3;
		this.string4 = string4;
		this.tracker = tracker;
	}

	public Mensaje(Tipo tipo, String string1, String string2, int int1, int int2) {//P2P tracker a peerDestino (threadservidor)
		this.tipo = tipo;
		this.string1 = string1;
		this.string2 = string2;
		this.int1 = int1;
		this.int2 = int2;
	}
	
	public Mensaje(Tipo tipo, String string1, String string2, int int1, int int2, boolean bool1) {//GET_PIECE
		this.tipo = tipo;
		this.string1 = string1;
		this.string2 = string2;
		this.int1 = int1;
		this.int2 = int2;
		this.bool1 = bool1;
	}
	
	public Mensaje(Tipo tipo, PublicKey kpub) {//CHECK_AVAILABLE
		this.tipo = tipo;
		this.kpub = kpub;
	}
	
	public Mensaje(Tipo tipo, SecretKey key) {
		this.tipo = tipo;
		this.key = key;
	}
	
	public Mensaje(Tipo tipo, PublicKey kpub, boolean bool1) {//CHECK_AVAILABLE
		this.tipo = tipo;
		this.kpub = kpub;
		this.bool1 = bool1;
	}

	public Mensaje(Tipo tipo, Object lista) {//SWARM, FILES_AVAILABLE, GET_FILES_OFFERED
		this.tipo = tipo;	
		this.lista = lista;
	}
	
	public Mensaje(Tipo tipo, ArrayList<TrackerInfo> listaTrackers, ArrayList<String> hashes) {//TRACKER_UPDATE
		this.tipo = tipo;	
		this.listaTrackers = listaTrackers;
		this.hashes = hashes; 
	}
	
	public Mensaje(Tipo tipo, ArrayList<String> hashes, TrackerInfo tracker, boolean changePrimary) {//TRACKER_REGISTER
		this.tipo = tipo;
		this.tracker = tracker;
		this.hashes = hashes;
		this.bool1 = changePrimary;
	}
	
	public Mensaje(Tipo tipo, TrackerInfo tracker, ArrayList<TrackerInfo> listaTrackers) {//TRACKER_REPLICATE
		this.tipo = tipo;
		this.tracker = tracker;	
		this.listaTrackers = listaTrackers;
	}
	
	public Mensaje(Tipo tipo, ArrayList<TrackerInfo> listaTrackers) {//GET_TRACKERS
		this.tipo = tipo;	
		this.listaTrackers = listaTrackers;
	}
		
	public Mensaje(Tipo tipo, byte[] keyEncriptada) {//ACK DEL PEER SERVIDOR
		this.tipo = tipo;
		this.keyEncriptada = keyEncriptada;
	}
	
	public Mensaje(Tipo tipo, byte[] keyEncriptada, byte[] datosEncriptados) {//GET_TRACKERS
		this.tipo = tipo;
		this.keyEncriptada = keyEncriptada;
		this.datosEncriptados = datosEncriptados;
	}
	
	//Envío mensajes
	public void enviarMensaje(ConexionTCP c, Mensaje m, KeysGenerator kg) throws IOException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
		//encripto mensaje con la clave simetrica
		byte[] datosAEncriptar = c.convertToBytes(m);
		byte[] mensajeEncriptado = kg.encriptarSimetrico(c.getKey(), datosAEncriptar);
		c.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
		c.getOutBuff().flush();	
	}
	
	//Usado en run de tracker y peer-servidor 
	public byte[] recibirMensaje(ConexionTCP c, KeysGenerator kg) throws IOException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {	
		int msgSize = 1024*1024;//1MB
        byte[] buffer = new byte[msgSize];
        int byteread = c.getInBuff().read(buffer, 0, msgSize);
        //desencripto con la clave simetrica
        byte[] datosEncriptados = Arrays.copyOfRange(buffer, 0, byteread);
        byte[] msgDesencriptado = kg.desencriptarSimetrico(c.getKey(),datosEncriptados);
        
        return msgDesencriptado;
	}
	
	public byte[] recibirMensajeLarge(ConexionTCP c, KeysGenerator kg) throws IOException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {	
		int msgSize = 1024*1024;//1MB
        byte[] buffer = new byte[msgSize];
        int byteLeido = 0;
        int byteLeo = 0;
        int available = 0;

		while(byteLeido==0 || c.getInBuff().available()>0) {
			byteLeo = c.getInBuff().available();
			available += c.getInBuff().read(buffer, byteLeido, byteLeo);
			if(available>0)
				byteLeido+=byteLeo;
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				System.out.println("fallo sleep");
			}
		}
		
        //desencripto con la clave simetrica
        byte[] datosEncriptados = Arrays.copyOfRange(buffer, 0, byteLeido);
        byte[] msgDesencriptado = kg.desencriptarSimetrico(c.getKey(),datosEncriptados);
        return msgDesencriptado;
	}


}
