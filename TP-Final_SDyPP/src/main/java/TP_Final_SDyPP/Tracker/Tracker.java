package TP_Final_SDyPP.Tracker;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import TP_Final_SDyPP.DB4O.Database;
import TP_Final_SDyPP.DB4O.FileTable;
import TP_Final_SDyPP.DB4O.SeedTable;
import TP_Final_SDyPP.Otros.ConexionTCP;
import TP_Final_SDyPP.Otros.DatosArchivo;
import TP_Final_SDyPP.Otros.KeysGenerator;
import TP_Final_SDyPP.Otros.Mensaje;
import TP_Final_SDyPP.Otros.TrackerInfo;
import TP_Final_SDyPP.Otros.TrackerManager;

public class Tracker {
	//Atributos//
	private int id;
	private String ip;
	private String iplocal;
	private int port;
	private ArrayList<TrackerInfo> listaTrackers;
	private TrackerInfo trackerPrimario;
	private ConexionTCP conexionTCP;
	private Database db4o;
	private String path;
	private final Object lock = new Object();
	private final Object lockdb = new Object();
	private final Object lockListaConexionesTCP = new Object();
	private ArrayList<AddressPeer> listaConexionesTCP;
	private PrivateKey kPriv;
	private PublicKey kPub;
	private KeysGenerator kg;	
	private TrackerManager tm;
	private String trackersJSON = "trackers.json";
	public Logger logger;
	
	//Constructor//
	public Tracker(int id, String path, PublicKey publicKey, PrivateKey privateKey, Logger logger) throws Exception {
		this.logger = logger;
		this.tm = new TrackerManager();
		this.kg = new KeysGenerator();
		this.setPath(path);
		this.setId(id);
		this.setkPriv(privateKey);
		this.setkPub(publicKey);
		this.db4o = new Database(this.id, this.logger);
		//Inicializo el array trackers vacío
		this.setListaTrackers(new ArrayList<TrackerInfo>());
		//Inicializo el array de conexionesTCP vacío
		this.setListaConexionesTCP(new ArrayList<AddressPeer>());
		this.configurarme(); //Me configuro en base a mi Id
		
		logger.info(
				"Mi ID es: "+this.getId()+"\n"+
				"Mi IP es: "+this.getIp()+"\n"+
				"Mi IP privada es: "+this.getIpLocal()+"\n"+
				"Mi PUERTO es: "+this.getPort()+"\n"+
				"Mi Tracker Primario es: "+this.getTrackerPrimario().getId()+", Pub ("+this.getTrackerPrimario().getIp()+":"+this.getTrackerPrimario().getPort()+"), Priv ("+this.getTrackerPrimario().getIpLocal()+":"+this.getTrackerPrimario().getPort()+")"
		);
		
		if (this.getListaTrackers().isEmpty()) {
			logger.info("Mi lista de trackers es: VACIA");
		}else {
			String listaTrackers = "";
			for(int i=0;i<this.getListaTrackers().size();i++) { //Muestro mi lista de Trackers
				listaTrackers+=("\n- "+this.getListaTrackers().get(i).getId()+", Pub ("+this.getListaTrackers().get(i).getIp()+":"+this.getListaTrackers().get(i).getPort()+"), Priv ("+this.getListaTrackers().get(i).getIpLocal()+":"+this.getListaTrackers().get(i).getPort()+")");
			}				
			logger.info("Mi lista de trackers es: "+listaTrackers);
		}
		System.out.println("------------------------------------------------------\n");
		
		this.StartServer(); //Luego de la configuración, me pongo en escucha para atender peticiones
	}

	//-------------------------Getters and Setters------------------------//	
	public KeysGenerator getKG() {
		return kg;
	}

	public void setKG(KeysGenerator kg) {
		this.kg = kg;
	}
	
	public PrivateKey getkPriv() {
		return kPriv;
	}

	public void setkPriv(PrivateKey kPriv) {
		this.kPriv = kPriv;
	}

	public PublicKey getkPub() {
		return kPub;
	}

	public void setkPub(PublicKey kPub) {
		this.kPub = kPub;
	}
	
	public int getId() {
		return this.id;
	}

	private void setId(int id) {
		this.id = id;
	}
	
	public String getIpLocal() {
		return this.iplocal;
	}

	private void setIpLocal(String iplocal) {
		this.iplocal = iplocal;
	}

	public String getIp() {
		return this.ip;
	}

	private void setIp(String ip) {
		this.ip = ip;
	}

	public int getPort() {
		return this.port;
	}
	
	private void setPort(int port) {
		this.port = port;
	}

	public ArrayList<TrackerInfo> getListaTrackers() {
		return this.listaTrackers;
	}

	public void setListaTrackers(ArrayList<TrackerInfo> listaTrackers) {
		this.listaTrackers = listaTrackers;
	}
	
	public TrackerInfo getTrackerPrimario() {
		return this.trackerPrimario;
	}

	public void setTrackerPrimario(TrackerInfo trackerPrimario) {
		this.trackerPrimario = trackerPrimario;
	}
	
	public Database getDatabase() {
		return this.db4o;
	}

	public void setDatabase(Database db4o) {
		this.db4o = db4o;
	}
	
	public String getPath() {
		return this.path;
	}

	public void setPath(String path) {
		this.path = path;
	}
	
	public Object getLock() {
		return this.lock;
	}
	
	public Object getLockListaConexionesTCP() {
		return this.lockListaConexionesTCP;
	}
	
	public ArrayList<AddressPeer> getListaConexionesTCP() {
		return this.listaConexionesTCP;
	}
	
	public void setListaConexionesTCP(ArrayList<AddressPeer> listaConexionesTCP) {
		this.listaConexionesTCP = listaConexionesTCP;
	}
	
    //---------------------------------------------------------------------//
	
	//El tracker establece su Ip, Puerto, su Tracker Primario, la lista de trackers, lista de archivos, y BD
	private void configurarme() throws Exception {
		
		FileReader fileReader = new FileReader(this.trackersJSON);
		Object obj = new JSONParser().parse(fileReader);
		fileReader.close();		
		
		JSONArray ja = (JSONArray) obj;
		
		//Array donde almaceno los trackers que estan activos al momento de iniciarme
		ArrayList<TrackerInfo> trackersActivos = new ArrayList<TrackerInfo>();
		
		//Leo el JSON de configuración y me configuro como Tracker
		for(int i=0; i<ja.size(); i++){
        	
        	JSONObject jsonObject = (JSONObject) ja.get(i);
        	int id = Integer.parseInt((String) jsonObject.get("id")); //id de tracker
            
            //Si encontré mi configuración (Mi Id) en la lista de trackers, establezco mi Ip y mi Puerto
            if (id == this.getId()) {
                String ip = (String) jsonObject.get("ip"); //Ip de tracker
                String iplocal = (String) jsonObject.get("iplocal"); //Ip privada de tracker
                int port = Integer.parseInt((String) jsonObject.get("puerto")); //Puerto de tracker
                
            	this.setIp(ip); //Seteo mi Ip
            	this.setIpLocal(iplocal); //Seteo mi Ip privado
            	this.setPort(port); //Seteo mi Puerto
            	TrackerInfo m = new TrackerInfo(id,ip,iplocal,port); 
            	trackersActivos.add(m); //Me agrego a la lista de trackers activos
            	this.getListaTrackers().add(m);//Me agrego a mi lista de trackers
            }
		}
		for(int i=0; i<ja.size(); i++){
        	
        	JSONObject jsonObject = (JSONObject) ja.get(i);
        	int id = Integer.parseInt((String) jsonObject.get("id")); //id de tracker
            
            //Armar lista de Trackers Activos            
            if (id != this.getId()) { //Si es otro tracker,
                String ip = (String) jsonObject.get("ip"); //Ip de tracker
                String iplocal = (String) jsonObject.get("iplocal"); //Ip privada de tracker
                int port = Integer.parseInt((String) jsonObject.get("puerto")); //Puerto de tracker
            	
            	if(!ip.equals(this.ip) ? nodeAvailable(ip,port) : nodeAvailable(iplocal,port)) {//y se encuentra activo
            		TrackerInfo m = new TrackerInfo(id,ip,iplocal,port);  //lo agrego a la lista de trackers activos
            		trackersActivos.add(m);
            	}
            }
		}
		//Obtengo el tracker activo con id mas chico (primario).
		TrackerInfo primario = null;
		TrackerInfo yo = new TrackerInfo(this.getId(),this.getIp(),this.getIpLocal(),this.getPort()); //Creo una instancia con mi Info
		if (trackersActivos.size()>1) {
			primario = this.findTrackerPrimarioActual(trackersActivos,this.getId());
		} else {
			primario = yo;
		}
		
		logger.info("id primario: "+primario.getId()+", Mi id: "+this.getId());
		
		if (primario.getId() < this.getId()) { //Si no tengo el id más chico, 
			this.trackerPrimario = primario;
			this.registrarme(trackerPrimario,yo,true); //Me registro ante el tracker primario, y el me envía sus datos (además avisa a los demás trackers que me inicié)
			//true: change_primary, informa que es primario al resto (si no lo hizo ya)
		}else{ //Si tengo el id más chico
			this.trackerPrimario = yo;//Me defino como primario
			if (trackersActivos.size()>1) { //Si hay algún tracker activo además de mi
				this.registrarme(primario,yo,false); //Me registro ante el primario actual, y el me envía sus datos (además avisa a los demás trackers que me inicié)
				//false: no hace change_primary
				this.informarSoyPrimario(yo); //Informo a los demás trackers que soy el nuevo primario
			}
		}
	}

	//Busco id más chico que no sea el mío
	private TrackerInfo findTrackerPrimarioActual(ArrayList<TrackerInfo> trackersActivos, int myid) {
		int menorID = -1;
		int pos = -1;
		for(int i=0; i<trackersActivos.size(); i++){
			int id = trackersActivos.get(i).getId();
			if(id != myid) {
				if(menorID==-1){
					menorID = id;
					pos = i;
				}else if(id<menorID) {
					menorID = id;
					pos = i;
				}
			}	
		}
		return trackersActivos.get(pos);
	}

	//Compruebo que el nodo destino	este disponible
	public boolean nodeAvailable(String ip, int port) throws Exception {   
        try {						
        	System.out.println("creando conexion contra "+ip+":"+port);
        	Socket s = new Socket(ip, port);
        	conexionTCP = new ConexionTCP(s);
        	Mensaje msj = new Mensaje(Mensaje.Tipo.ALIVE);
        	conexionTCP.getOutObj().writeObject(msj);
        	
        	Mensaje response = (Mensaje) conexionTCP.getInObj().readObject();
        	if (response.tipo == Mensaje.Tipo.ALIVE) {
				conexionTCP.getSocket().close();
        		return true;
        	}else {
				conexionTCP.getSocket().close();
        		return false;
        	}   
        	
        } catch (IOException | ClassNotFoundException ex) {
        	 return false;
        }
    }
	
	//Se realiza un ping a todos los trackers de mi lista para ver si están activos. El que no esté activo, lo elimino
	public void pingTrackers(TrackerInfo trackerNoPing) throws Exception {
		synchronized(this) {
			for (int i=0; i<this.getListaTrackers().size(); i++) {
				TrackerInfo t = this.getListaTrackers().get(i);
				
				if (trackerNoPing == null) { //Si le tengo que hacer ping a todos los trackers de la lista
					if(t.getId() != this.getId() ) { //Si no soy yo
						if(!t.getIp().equals(this.ip) ? !nodeAvailable(t.getIp(),t.getPort()) : !nodeAvailable(t.getIpLocal(),t.getPort())) {
							logger.info("Se elimino al tracker con ID N°"+ t.getId() + " por inactividad.");
							this.getListaTrackers().remove(i); //Elimino el tracker de mi lista
						}
					}
				}else { //Si le tengo que hacer ping a todos menos al tracker recibido como parámetro
					//Si no soy yo y tampoco el tracker al que no debo hacerle ping, compruebo conectividad
					if((t.getId() != this.getId()) && (t.getId() != trackerNoPing.getId())) { 
						if(!t.getIp().equals(this.ip) ? !nodeAvailable(t.getIp(),t.getPort()) : !nodeAvailable(t.getIpLocal(),t.getPort())) {
							logger.info("Se elimino al tracker con ID N°" + t.getId() + " por inactividad.");
							this.getListaTrackers().remove(i); //Elimino el tracker de mi lista
						}
					}	
				}		
			}
		}
	}
	
	//Método que permite registrarme ante el tracker primario
	private void registrarme (TrackerInfo primarioActual, TrackerInfo yo, boolean changePrimary) {
		//Busco hashes de archivos en mi bd, se los envío al primario para que compare con los suyos y me devuelva los que me falten.
		ArrayList<String> misHashes = this.getHashes();
		 
		conexionTCP = null;
		logger.info("socket mio: Pub ("+this.getIp()+":"+this.getPort()+"), Priv ("+this.getIpLocal()+":"+this.getPort()+")");
		logger.info("socket primario actual: Pub ("+primarioActual.getIp()+":"+primarioActual.getPort()+"), Priv ("+primarioActual.getIpLocal()+":"+primarioActual.getPort()+")");
		String ip = !primarioActual.getIp().equals(this.ip) ? primarioActual.getIp() : primarioActual.getIpLocal();
		Socket s;
		try {
			s = new Socket(ip,primarioActual.getPort());
			
			try {
				conexionTCP = new ConexionTCP(s);
				
				//Inicio una conexion contra el primario actual
				tm.getSecretKey(conexionTCP, true, this.kPub, this.kPriv, kg, this.logger);//Obtengo clave simetrica
				//Solicito registrarme ante el primario actual
				Mensaje m = new Mensaje(Mensaje.Tipo.TRACKER_REGISTER, misHashes, yo, changePrimary);
				m.enviarMensaje(conexionTCP, m, kg);
				
		        byte[] msgDesencriptado = m.recibirMensaje(conexionTCP, kg);
		        Mensaje respuesta = (Mensaje) conexionTCP.convertFromBytes(msgDesencriptado);
				
				conexionTCP.getSocket().close();
				if (respuesta.tipo == Mensaje.Tipo.TRACKER_UPDATE) { //Si el primario recibió la petición de registro debe contestar 
																	//con su información así puedo actualizarme			
					this.setListaTrackers(respuesta.listaTrackers); //Actualizo la lista de trackers enviada por el primario
					
					//Actualizo JSON de trackers
					this.actualizarJSONTrackers();
					
					//if(respuesta.hashes!=null) {
						//Inicio thread que pide todos los jsons faltantes al primario. Mientras, el tracker inicia un serversocket para 
						//igualmente ir recibiendo actualizaciones de nuevos json enviados por el primario.
						ThreadTrackerUpdate ttu = new ThreadTrackerUpdate(respuesta.hashes, this, ip, primarioActual.getPort());
						Thread t = new Thread (ttu);
						t.start(); //inicio el thread
					//}
					
					logger.info("ME REGISTRE Y ACTUALICE CON EXITO");
				}else { //Si se recibe otro mensaje de respuesta, cierro la conexión
					logger.error("NO PUDE REGISTRARME");
				}
			} catch (Exception e) {
				logger.error("NO PUDE REGISTRARME. Fallo creacion conexionTCP con tracker primario");
			} 
		} catch (IOException e) {
			logger.error("NO PUDE REGISTRAME. Fallo creacion socket con tracker primario");
		}
	}
		
	public void actualizarJSONTrackers() {
		synchronized(this.listaTrackers) 
		{
			tm.actualizarJSONTrackers(this.listaTrackers, this.trackersJSON, this.logger);
		}
	}

	//Método que permite informar a los demás trackers que soy el nuevo tracker primario
	public void informarSoyPrimario(TrackerInfo yo) throws Exception {
		logger.info("Soy el Nuevo Tracker Primario!");
		Mensaje response = new Mensaje(Mensaje.Tipo.SET_PRIMARY,yo);
		
		this.pingTrackers(null); //Compruebo los trackers que están activos
		for (int i=0; i<this.getListaTrackers().size(); i++) {
			if (this.getListaTrackers().get(i).getId()!=this.getId()) { //Le comunico a los demás trackers que soy el nuevo primario
				String ipTracker = !this.getListaTrackers().get(i).getIp().equals(this.getIp()) ? this.getListaTrackers().get(i).getIp() : this.getListaTrackers().get(i).getIpLocal();
				conexionTCP = new ConexionTCP(ipTracker,this.getListaTrackers().get(i).getPort()); //Abro una conexion contra el tracker
				tm.getSecretKey(conexionTCP, true, this.kPub, this.kPriv, kg, this.logger);//Obtengo clave simetrica
				
				response.enviarMensaje(conexionTCP, response, kg);
				conexionTCP.getSocket().close(); //Cierro la conexion
			}
		}
	}
	
	public void almacenarEnBD(String pathJSON, String ipPeer, String iplocalPeer) throws FileNotFoundException, IOException, ParseException {
		//Obtengo informacion JSON (nombre, ID, tamaño) y lo almaceno en la BD tabla files
		FileReader fileReader = new FileReader(pathJSON);
		Object obj = new JSONParser().parse(fileReader);
		fileReader.close();
		
		JSONObject json = (JSONObject) obj;
		String name = (String) json.get("name");
		long size = (long) json.get("fileSize");
		String hash = (String) json.get("ID");
		String pathArchivo = (String) json.get("path");//donde almacena el seed al archivo
		String pathArchivosCompartidos = (String) json.get("pathArchivosCompartidos");//donde almacena el seed al archivo
	 
		this.almacenarFile(name, hash, size);
		
		//Nueva tupla en tabla seeds para ese JSON (ID, Seed = true, path donde está almacenado en seed, ip y port)
		this.almacenarSeed(hash, pathArchivo, ipPeer, iplocalPeer, true);
	}
	
	public void almacenarFile(String name, String hash, long size) {
		synchronized(this.lockdb) {
			this.getDatabase().insertarFile(name, hash, size);
		}
	}
	
	public void almacenarSeed(String hash, String path, String ip, String iplocal, boolean seed) {
		synchronized(this.lockdb) {
			this.getDatabase().insertarSeed(hash, path, ip, iplocal, seed);
		}
	}
	
	public void eliminarSeed(String hash, String ip, String iplocal) {
		synchronized(this.lockdb) {
			this.getDatabase().eliminarSeed(hash, ip, iplocal);
		}
	}
	
	public ArrayList<DatosArchivo> getFilesByName(String nombreBuscado) {
		synchronized(this.lockdb) {
			return this.getDatabase().getFilesByName(nombreBuscado);
		}
	}
	
	public ArrayList<String> getHashes() {
		synchronized(this.lockdb) {
			return this.getDatabase().getHashes();
		}
	}
	
	public ArrayList<String> compareHashes(ArrayList<String> hashes) {
		synchronized(this.lockdb) {
			return this.getDatabase().compareHashes(hashes);
		}
	}
	
	public SeedTable getSocketPeer(String hash) {
		synchronized(this.lockdb) {
			return this.getDatabase().getSocketPeer(hash);
		}
	}
	
	public ArrayList<SeedTable> getSwarm(String hash, String ip, String iplocal) {
		synchronized(this.lockdb) {
			return this.getDatabase().getSwarm(hash,ip,iplocal);
		}
	}
	
	public void deshabilitarSeed(String ip, String iplocal) {	
		synchronized(this.lockdb) {
			this.getDatabase().deshabilitarSeed(ip,iplocal);
		}
	}
	
	public void habilitarSeed(String ip, String iplocal) {
		synchronized(this.lockdb) {
			this.getDatabase().habilitarSeed(ip,iplocal);
		}
	}
	
	public void nuevoSeed(String hash, String ip, String iplocal) {
		synchronized(this.lockdb) {
			this.getDatabase().nuevoSeed(ip,iplocal,hash);
		}
	}
	
	public ArrayList<FileTable> getArchivosOfrecidos(String ip, String iplocal) {
		synchronized(this.lockdb) {
			return this.getDatabase().getArchivosOfrecidos(ip,iplocal);
		}
	}

	public boolean guardarArchivoBuffer(ConexionTCP ctcp, String path) throws InterruptedException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
		this.logger.info("guardarArchivoBuffer tracker");
		synchronized(this) {
			int byteread;
		    int current = 0;
		
		    try {	    	
		        File archivo = new File(path);//Al JSON le pongo como nombre su ID para evitar repetidos
		        archivo.createNewFile();//Almaceno JSON en carpeta del tracker donde van todos los JSON
		        FileOutputStream fos = new FileOutputStream(archivo);
		        BufferedOutputStream out = new BufferedOutputStream(fos);
		        
		        Mensaje m = new Mensaje();
				byte[] datosDesencriptados = m.recibirMensajeLarge(ctcp, kg);
		        logger.info("Tracker-->guardarArchivoBuffer: byteread al recibir JSON: "+datosDesencriptados.length);

		        out.write(datosDesencriptados, 0, datosDesencriptados.length);
		        out.flush();
	
		        out.close();
		        fos.close();
		      
		        return true;
		    }catch(IOException ex) {
		    	logger.error("Fallo guardar JSON.");
		    	//ex.printStackTrace();
		    	return false;
		    }
		}
	}
	
	public String hashUnico(String hashJSON) {
		synchronized(this) {
			//Verifico que no haya otro json con ese nombre (el del hash dado). Si lo hay, modifico el hash.
			String path = "";
			String hashFinal = hashJSON;
			boolean yaExiste = false;
			int index = -1;
			do {
				File f = null;
				if(index == -1) {
					path = this.getPath()+ "/" + hashJSON +".json";
					f = new File(path);
					index++;
				}else {//Ya fallo el primer nombre. Pongo un número al final
					hashFinal = hashJSON + index;
					path = this.getPath()+ "/" + hashFinal + ".json";
					f = new File(path);
					index++;
				}	
				yaExiste = f.exists();
			}while(yaExiste);//Mientras que el nombre exista no sale del loop
			
			return hashFinal;
		}
	}
	
	//Primero verifico que este levantado el primario. Si no, tengo que buscar otro.
	//Si está levantado, le paso la ip:port del primario al peer
	public void devolverPrimario(ConexionTCP conexTCP) throws IOException, Exception {
		synchronized(this) {
			String ipPrimario = this.getTrackerPrimario().getIp();
			String iplocalPrimario = this.getTrackerPrimario().getIpLocal();
			int portPrimario = this.getTrackerPrimario().getPort();
			int idPrimario = this.getTrackerPrimario().getId();
			if(this.getId() == idPrimario) {//Si soy el primario
				Mensaje m = new Mensaje(Mensaje.Tipo.TRACKER_PRIMARIO, ipPrimario, iplocalPrimario, portPrimario);//Envio a peer socket del primario para que le envie en json  
				m.enviarMensaje(conexTCP, m, kg);
				
			}else if (!ipPrimario.equals(this.getIp()) ? this.nodeAvailable(ipPrimario, portPrimario) : this.nodeAvailable(iplocalPrimario, portPrimario)) {//No soy el primario, pregunto si el primario está vivo
				Mensaje m = new Mensaje(Mensaje.Tipo.TRACKER_PRIMARIO, ipPrimario, iplocalPrimario, portPrimario);//Envio a peer socket del primario para que le envie en json o para que se conecte a este
				m.enviarMensaje(conexTCP, m, kg);
				
			}else {
				//Si se cayo el primario, elegir uno nuevo
				this.getNuevoPrimario();
				this.devolverPrimario(conexTCP);
			}
		}
	}
	
	public void getNuevoPrimario() throws Exception {
		synchronized(this) {
			this.pingTrackers(null); //Actualizo mi lista de trackers (si alguno no contesta lo elimino de la lista)
			logger.info("Se cayo el Tracker Primario... Buscando su reemplazo...");
			this.elegirNuevoPrimario(); //Se selecciona el nuevo tracker primario
		}
	}
	
	//Permite definir al nuevo tracker primario ante la caída del tracker primario actual
	public void elegirNuevoPrimario() throws Exception {
		int idMenor = this.getListaTrackers().get(0).getId(); 
		int posIdMenor = 0;
		//obtengo la posición en mi array de trackers, del tracker que tiene el ID más pequeño (Nuevo Tracker Primario)
		for (int i=0; i<this.getListaTrackers().size();i++) { 
			if (this.getListaTrackers().get(i).getId() < idMenor) {
				idMenor = this.getListaTrackers().get(i).getId();
				posIdMenor = i;
			}
		}
		logger.info("El nuevo candidato a Tracker primario es : ID N°"+this.getListaTrackers().get(posIdMenor).getId());
		
		TrackerInfo yo = new TrackerInfo(this.getId(),this.getIp(),this.getIpLocal(),this.getPort());
		if (idMenor == this.getId()) { //Si soy el más pequeño de la lista, me defino como primario y aviso a todos que soy el nuevo primario
			this.setTrackerPrimario(yo); //Me defino como primario
			this.informarSoyPrimario(yo); //informo a los demás trackers que soy el nuevo primario
			this.replicar(null); //Replico mi información a los demás trackers activos
			
		}else { //Si no soy el más pequeño, le solicito al tracker de menor Id que se convierta en el nuevo primario
			TrackerInfo nuevoPrimario = new TrackerInfo (this.getListaTrackers().get(posIdMenor).getId(), this.getListaTrackers().get(posIdMenor).getIp(), this.getListaTrackers().get(posIdMenor).getIpLocal(), this.getListaTrackers().get(posIdMenor).getPort());
			String ipNuevoPrimario = !this.getListaTrackers().get(posIdMenor).getIp().equals(this.ip) ? this.getListaTrackers().get(posIdMenor).getIp() : this.getListaTrackers().get(posIdMenor).getIpLocal();
			conexionTCP = new ConexionTCP(ipNuevoPrimario, nuevoPrimario.getPort()); //Abro una conexion contra el nuevo primario
			this.setTrackerPrimario(nuevoPrimario); //Defino mi nuevo primario
			tm.getSecretKey(conexionTCP, true, this.kPub, this.kPriv, kg, this.logger);//Obtengo clave simetrica

			//Le solicito al nuevo primario que se defina como nuevo primario y avise al resto
			Mensaje m = new Mensaje(Mensaje.Tipo.CHANGE_PRIMARY,yo);
			m.enviarMensaje(conexionTCP, m, kg);

			conexionTCP.getSocket().close(); //Cierro la conexión
		}
	}
	
	//Permite replicar los datos del tracker primario en los demás trackers activos
	public void replicar(TrackerInfo m) throws Exception {
		this.pingTrackers(m); //Compruebo cuales son los trackers vivos en este momento. Si alguno no responde, lo elimino de la lista
		ArrayList<TrackerInfo> lisTrackers = this.getListaTrackers();
		
		if (lisTrackers.size()>1) { //Si hay más de un tracker en la lista (Alguien además de mí) , les replico la información
			int idMasterNoReplica = -1;
			if (m!=null) {
				idMasterNoReplica = m.getId(); //obtengo el id del tracker que no debe ser replicado
			}
			
			for (int i=0; i<lisTrackers.size(); i++) {
				if ((m == null) && (lisTrackers.get(i).getId()!=this.getId())) { //Si m == null significa que debo replicar a todos
					String ipTracker = !lisTrackers.get(i).getIp().equals(this.ip) ? lisTrackers.get(i).getIp() : lisTrackers.get(i).getIpLocal();
					ConexionTCP ctcp = new ConexionTCP(ipTracker,lisTrackers.get(i).getPort()); //Abro una conexion contra el tracker
					tm.getSecretKey(ctcp, true, this.kPub, this.kPriv, kg, this.logger);//Obtengo clave simetrica
					this.actualizarTracker(ctcp, lisTrackers, null); //Actualizo el tracker
				}else { //Si m != null, debo replicar a todos excepto a m
					if ((lisTrackers.get(i).getId()!=this.getId())&&(lisTrackers.get(i).getId()!=idMasterNoReplica)) { //Si no soy yo y tampoco m,
						String ipTracker = !lisTrackers.get(i).getIp().equals(this.ip) ? lisTrackers.get(i).getIp() : lisTrackers.get(i).getIpLocal();
						ConexionTCP ctcp = new ConexionTCP(ipTracker,lisTrackers.get(i).getPort()); //Abro una conexion contra el tracker
						tm.getSecretKey(ctcp, true, this.kPub, this.kPriv, kg, this.logger);//Obtengo clave simetrica
						this.actualizarTracker(ctcp, lisTrackers, null); //Actualizo el tracker
					}
				}	
			}
		}
	}
	
	//Permite enviarle un mensaje a un tracker para que actualice su información (lista de trackers)
	public void actualizarTracker (ConexionTCP ctcp, ArrayList<TrackerInfo> lTrackers, ArrayList<String> hashesFaltantes) throws Exception {
		Mensaje m = null;
		
		m = new Mensaje(Mensaje.Tipo.TRACKER_UPDATE,lTrackers,hashesFaltantes);
		//Le pido al tracker que se actualice con mis datos
		m.enviarMensaje(ctcp, m, kg);
		
		ctcp.getSocket().close(); //Cierro la conexión 
	}
	
	public void replicarSeed(Mensaje m, String tipo) throws Exception {
		this.pingTrackers(m.tracker); //Compruebo cuales son los trackers vivos en este momento. Si alguno no responde, lo elimino de la lista
		ArrayList<TrackerInfo> lisTrackers = this.getListaTrackers();
		
		if (lisTrackers.size()>1) { //Si hay más de un tracker en la lista (Alguien además de mí) , les replico la información
			int idMasterNoReplica = -1;
			if(m.tracker != null)
				idMasterNoReplica = m.tracker.getId(); //obtengo el id del tracker que no debe ser replicado
				
			for (int i=0; i<lisTrackers.size(); i++) {
				if ((lisTrackers.get(i).getId()!=this.getId())&&(lisTrackers.get(i).getId()!=idMasterNoReplica)) { //Si no soy yo y tampoco m,
					String ipTracker = !lisTrackers.get(i).getIp().equals(this.ip) ? lisTrackers.get(i).getIp() : lisTrackers.get(i).getIpLocal();
					ConexionTCP ctcp = new ConexionTCP(ipTracker,lisTrackers.get(i).getPort()); //Abro una conexion contra el tracker
					
					tm.getSecretKey(ctcp, true, this.kPub, this.kPriv, kg, this.logger);//Obtengo clave simetrica
					Mensaje msg = null;
					
					switch(tipo) {
						case "replicar":
						msg = new Mensaje(Mensaje.Tipo.SEND_SEED, m.string1, m.string2, m.string3, m.string4);
						break;//m.hash, m.path, m.ip, m.iplocal
						
						case "replicarDelete":
						msg = new Mensaje(Mensaje.Tipo.SEND_QUIT_SWARM, m.string1, m.string2, m.string3);
						break;//m.ip, m.iplocal, m.hash
						
						case "replicarDeshabilitar":
						msg = new Mensaje(Mensaje.Tipo.SEND_COMPLETE, m.string1, m.string2, m.int1);
						break;//m.ip, m.iplocal, m.port
						
						case "replicarHabilitar":
						msg = new Mensaje(Mensaje.Tipo.SEND_COMPLETE, m.string1, m.string2, m.int1);
						break;//m.ip, m.iplocal, m.port
						
						case "replicarNuevo":
						msg = new Mensaje(Mensaje.Tipo.SEND_NEW_SEED, m.string1, m.string2, m.string3);
						break;//m.ip, m.iplocal, m.hash
					}
					
					msg.enviarMensaje(ctcp, msg, kg);
					
					ctcp.getSocket().close(); //Cierro la conexión
				}	
			}
		}
	}
	
	//Método para Iniciar el Tracker en modo escucha
	private void StartServer() {
		try {
			//Abrimos el socket principal en el cual el tracker escuchará tanto a los peers como a los demás trackers
			ServerSocket socketTracker = new ServerSocket (this.getPort());
			
			while (true) {
				//Quedamos a la espera de conexiones por algún peer o tracker
				Socket socketConexion = socketTracker.accept();
				logger.info("Conexion establecida con: ("+socketConexion.getInetAddress().getCanonicalHostName()+":"+socketConexion.getPort()+")");
				ThreadTracker tt = new ThreadTracker(socketConexion, this);
				Thread t = new Thread (tt); //Atiendo la petición en un nuevo thread
				t.start(); //inicio el thread	
			}
		} catch (IOException e) {
			logger.error("Error al iniciar el Tracker.");
		}
		
	}

	public void removeTracker(int id) {
		for(int i=0; i<this.getListaTrackers().size(); i++) {
			if(this.getListaTrackers().get(i).getId() == id) {
				logger.info("Elimino tracker ya existente");
				this.getListaTrackers().remove(i);
			}
		}
	}

}
