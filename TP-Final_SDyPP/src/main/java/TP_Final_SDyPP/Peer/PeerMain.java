package TP_Final_SDyPP.Peer;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Scanner;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import TP_Final_SDyPP.Observable.Observable;
import TP_Final_SDyPP.Observable.Observer;
import TP_Final_SDyPP.Otros.ConexionTCP;
import TP_Final_SDyPP.Otros.KeysGenerator;
import TP_Final_SDyPP.Otros.Mensaje;
import TP_Final_SDyPP.Otros.TrackerManager;
import TP_Final_SDyPP.Tracker.Tracker;

public class PeerMain implements Observable {

	private ArrayList<Observer> observadores = new ArrayList<Observer>();
	private Cliente cliente;
	private Servidor servidor;
	private String error;
	private String pathLogs = "Logs";//Con Logs de peer (cliente y servidor) y tracker
	private String pathJSONs = "JSONs";//Con jsons descargados y creados
	private String ipPubManual = "";
	private String ipPrivManual = "";
	
	private final static PeerMain instance = new PeerMain();
    public static PeerMain getInstance() {
        return instance;
    }
    
    public String getIpPubManual() {
    	return this.ipPubManual;
    }
    
    public void setIpPubManual(String ip) {
    	this.ipPubManual = ip;
    	System.out.println("IP publica actualizada: "+ip);
    }
    
    public String getIpPrivManual() {
    	return this.ipPrivManual;
    }
    
    public void setIpPrivManual(String ip) {
    	this.ipPrivManual = ip;
    	System.out.println("IP privada actualizada: "+ip);
    }
	
	public Cliente getCliente() {
		return this.cliente;
	}

	public Servidor getServidor() {
		return this.servidor;
	}
	
	public String getMensaje() {
		return this.error;
	}
	
	public void peerMain() {    	
		TrackerManager tm = new TrackerManager();
		KeysGenerator kg = new KeysGenerator();
		
		//Generar clave pública y privada
    	KeysGenerator ppg = new KeysGenerator();
    	ppg.generarLlavesAsimetricas();
    	PublicKey publicKey = ppg.publicKey; 
    	PrivateKey privateKey = ppg.privateKey;
    	
    	//Ubicación logs
    	this.crearCarpetaLogs();
		if(System.getProperty("APP_LOG_ROOT") == null) {
			System.setProperty("APP_LOG_ROOT", pathLogs);	
		}
		
		//Nombre archivo log servidor
    	String filename = "peerServidor";
		System.setProperty("logServidor", filename);
		
		//Nombre archivo log cliente
    	filename = "peerCliente";
		System.setProperty("logCliente", filename);
		
		//Get loggers
		Logger loggerCliente = LogManager.getLogger(Cliente.class.getName());
		Logger loggerServidor = LogManager.getLogger(Servidor.class.getName());
		String log;
    	
		if(this.ipPubManual.equals("") || this.ipPrivManual.equals("")) {
			//conexion con tracker, me indica mi ip publica
	    	Socket socket = new Socket();
	    	try {
	    		ConexionTCP conexionTCP = null;
				conexionTCP = tm.getTracker("", kg, publicKey, privateKey);//Obtengo tracker activo
				if(conexionTCP!=null) {
	    			Mensaje m = new Mensaje(Mensaje.Tipo.GET_PUBLIC_IP);//pido a tracker primario que me indique la ip publica que utiliza el peer
	    			m.enviarMensaje(conexionTCP,m, kg);
	    			
	    			//Recibo IP
	    			byte[] datosDesencriptados = m.recibirMensaje(conexionTCP, kg);
	    			Mensaje response = (Mensaje) conexionTCP.convertFromBytes(datosDesencriptados);
						        
			        if(response.tipo == Mensaje.Tipo.PEER_PUBLIC_IP) {//Contenido: ipPublica(string1)
	    	    		log = "Recibi mi IP publica por parte de tracker.";
	    	    		loggerCliente.info(log);
	    	    		System.out.println(log);

	    	        	String ipInterna = this.ObtenerIPInterna();
	    	    		String ipExterna = response.string1;
	    	    		
	    	    		if (ipInterna.charAt(0)!='0') {
		    	    		log = "Mi IP privada: "+ipInterna+" | Mi IP publica: "+ipExterna;
		    	    		loggerCliente.info(log);
		    	    		System.out.println(log);
		    	    		
		    	    		//Path donde guardar JSON creados y descargados
		    	    		File f = new File(this.pathJSONs);
		    	    		if(!f.exists())
		    	    			new File(this.pathJSONs).mkdirs();
		    	        	
		    	    		//Creacion peer servidor
		    	    		this.servidor = new Servidor(ipInterna,ipExterna,publicKey,privateKey,loggerServidor);
		    	    		this.servidor.StartServer();//inicia threadservidor
		    	    		//Creacion peer cliente		
		    	    		this.cliente = new Cliente(pathJSONs,ipInterna,ipExterna,publicKey,privateKey,loggerCliente,tm,kg);
		    	    		
		    	    		this.notifyObserver(8, null);//set cliente a PeerController	
	    	    		} else {
	    	    			log = "Mi IP publica: "+ipExterna;
		    	    		loggerCliente.info(log);
		    	    		System.out.println(log);
		    	    		
		    	    		log = "Fallo obtencion IP privada. Presione el boton Definir IPs para definir la IP privada de forma manual.";
		    	    		loggerCliente.info(log);
		    	    		System.out.println(log);
		    	    		
		    	    		this.notifyObserver(9, null);//enable btn inicio y btn Definir IPs
	    	    		}
			        }else {
			        	this.notifyObserver(9, null);//enable btn inicio y btn Definir IPs
			        	log = "Su tracker actual ("+conexionTCP.getSocket().getInetAddress().getCanonicalHostName()+":"+conexionTCP.getSocket().getPort()+") no respondio con PEER_PUBLIC_IP.";
			        	loggerCliente.error(log);
	            		System.out.println(log);
	            	}
	    		}else {
	    			this.notifyObserver(9, null);//enable btn inicio y btn Definir IPs
	    			log = "No hay trackers disponibles en este momento o todos pertenecen a la misma LAN que el peer.";
	    			loggerCliente.error(log);
	    			System.out.println(log);
	    		}
				try {
					conexionTCP.getSocket().close();
				} catch(Exception e) {}
			}catch(Exception ex) {
				this.notifyObserver(9, null);//enable btn inicio y btn Definir IPs
				log = "Fallo la conexion con el tracker."; 
				loggerCliente.error(log);
				System.out.println(log);
			}
		} else {//Las IPs fueron definidas de forma manual dado que todos los trackers pertenecen a la misma LAN que el peer.
			String ipInterna = this.ipPrivManual;
    		String ipExterna = this.ipPubManual;
    		
    		log = "Mi IP privada: "+ipInterna+" | Mi IP publica: "+ipExterna;
    		loggerCliente.info(log);
    		System.out.println(log);
    		
    		//Path donde guardar JSON creados y descargados
    		File f = new File(this.pathJSONs);
    		if(!f.exists())
    			new File(this.pathJSONs).mkdirs();
        	
    		//Creacion peer servidor
    		this.servidor = new Servidor(ipInterna,ipExterna,publicKey,privateKey,loggerServidor);
    		this.servidor.StartServer();//inicia threadservidor
    		//Creacion peer cliente		
    		this.cliente = new Cliente(pathJSONs,ipInterna,ipExterna,publicKey,privateKey,loggerCliente,tm,kg);
    		
    		this.notifyObserver(8, null);//set cliente a PeerController
		}
	}
	
	private String ObtenerIPInterna() {
		String ip = "0";
		
		Enumeration e;
		try {
			e = NetworkInterface.getNetworkInterfaces();
			while(e.hasMoreElements())
			{
			    NetworkInterface n = (NetworkInterface) e.nextElement();
			    Enumeration ee = n.getInetAddresses();
			    while (ee.hasMoreElements())
			    {
			        InetAddress i = (InetAddress) ee.nextElement();
			        if(!i.getHostAddress().equals("127.0.0.1") &&
			        		i.getHostAddress().charAt(0)!='0' &&
			        		i.getHostAddress().charAt(4)!=':') {
			        	ip = i.getHostAddress();
			        }
			    }
			}
		} catch (SocketException e1) {
			return "0";
		}
		
		return ip;
	}

	private void crearCarpetaLogs() {
		File f = new File(this.pathLogs);
		if(!f.exists())
			new File(this.pathLogs).mkdirs();
	}

	//Metodos Observable
	@Override
	public void addObserver(Object o) {
		if (o instanceof Observer)
		{
			Observer ob = (Observer) o;
			observadores.add(ob);
		}
	}

	@Override
	public void notifyObserver(int op, String log) {
		for (Observer o: observadores)
		{
			//o.update(this, op, log);
			o.update(op, log);
		}
	}

	public void salir() {
		this.servidor.setStop();
	}
}
