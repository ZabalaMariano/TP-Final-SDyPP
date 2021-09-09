package TP_Final_SDyPP.Peer;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Scanner;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.logging.log4j.Logger;

import TP_Final_SDyPP.Otros.ConexionTCP;
import TP_Final_SDyPP.Otros.KeysGenerator;
import TP_Final_SDyPP.Otros.Mensaje;
import TP_Final_SDyPP.Otros.TrackerManager;

public class Servidor {
	
	private String ipExterna;
	private String ipInterna;
	private PublicKey kPub;
	private PrivateKey kPriv;
	private KeysGenerator kg;
	private final int conexionesMaximas = 5; 
	private int conexionesSalientes = 0;
	private TrackerManager tm;
	private String log;
	private ServerSocket socketServidor = null;
	public Logger logger;
	private boolean continuar = true;
	private ArrayList<ThreadServer> listaThreadServer;
		
	//Constructor por defecto
	public Servidor(String ipInterna, String ipExterna, PublicKey publicKey, 
			PrivateKey privateKey, Logger logger) {
		this.logger = logger;
		this.ipInterna = ipInterna;
		this.ipExterna = ipExterna;
		this.kPub = publicKey;
		this.kPriv = privateKey;
		this.tm = new TrackerManager();
		this.kg = new KeysGenerator();
		this.listaThreadServer = new ArrayList<ThreadServer>();
	}
	
	public ArrayList<ThreadServer> getListaThreadServer() {
		return listaThreadServer;
	}
	
	public boolean getContinuar() {
		return continuar;
	}
	
	public KeysGenerator getKG() {
		return kg;
	}
	
	public PublicKey getKpub() {
		return kPub;
	}
	
	public PrivateKey getKpriv() {
		return kPriv;
	}
	
	public String getIpInterna() {
		return ipInterna;
	}

	public String getIpExterna() {
		return ipExterna;
	}

	public void setIpExterna(String ipExterna) {
		this.ipExterna = ipExterna;
	}
	
	//Conexiones salientes
	public int getNumConexiones() {
		synchronized(this) {
			return this.conexionesSalientes;
		}
	}
	
	public int getNumConexionesMaximas() {
		return this.conexionesMaximas;
	}
	
	public void aumentarConexiones() {
		synchronized(this) {
			this.conexionesSalientes++;
			System.err.println("Numero Conexiones: "+ this.conexionesSalientes);
			if(this.conexionesSalientes >= this.conexionesMaximas) {
				//envio mi peer ID a tracker para que me agregue a su array de peers que no pueden ser devueltos en un swarm.
				System.err.println("Envio max_conexiones ocupadas");
				this.mensajeTracker(Mensaje.Tipo.COMPLETE);
			}
		}
	}
	
	public void decrementarConexiones() {
		synchronized(this) {
			if(this.conexionesSalientes == this.conexionesMaximas) {
				//envio mi peer ID a tracker para que me retire de su array de peers que no pueden ser devueltos en un swarm.
				System.err.println("Envio max_conexiones desocupadas");
				this.mensajeTracker(Mensaje.Tipo.FREE);
			}
			this.conexionesSalientes--;
			System.err.println("Numero Conexiones: "+ this.conexionesSalientes);
		}
	}
	
	public void mensajeTracker(Mensaje.Tipo tipoMensaje) {
		ConexionTCP c = null;
		
		try {
			c = tm.getTracker(this.ipExterna, kg, kPub, kPriv);
			
			if(c!=null) {
				Mensaje m = new Mensaje(tipoMensaje, this.ipExterna, this.ipInterna);
				m.enviarMensaje(c, m, kg);
				c.getSocket().close();
			} else {
				log = "No hay trackers disponibles.";
				logger.error(log);
				System.err.println(log);
			}
			
		} catch (Exception e) {
			log = "Fallo obtención de tracker.";
			this.logger.error(log);
			System.err.println(log);
		}
	}
	
	public void StartServer() {
		ThreadServer ts = new ThreadServer(this);
		Thread t = new Thread (ts);
		t.start();
		this.listaThreadServer.add(ts);
	}

	public void setStop() {
		this.continuar = false;
		for(ThreadServer ts: this.listaThreadServer) {
			if(ts != null) {
				if(ts.getConexionTCP() != null) {
					if(ts.getConexionTCP().getSocket() != null) {
						try {
							ts.getConexionTCP().getSocket().close();
						} catch (IOException e) {}
					}
				}
			}
		}
	}	
}