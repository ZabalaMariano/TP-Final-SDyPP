package TP_Final_SDyPP.Peer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import TP_Final_SDyPP.Otros.ConexionTCP;
import TP_Final_SDyPP.Otros.KeysGenerator;
import TP_Final_SDyPP.Otros.Mensaje;
import TP_Final_SDyPP.Otros.TrackerManager;
import javafx.application.Platform;

public class ThreadServer implements Runnable {
	private ConexionTCP conexionTCP;
	private Servidor servidor;
	private TrackerManager tm;
	private KeysGenerator kg;
	private String log;
	private boolean continuarThread = true;
	private boolean p2p = false;
	
	public ConexionTCP getConexionTCP() {
		return this.conexionTCP;
	}
	
	public ThreadServer(Servidor servidor) {
		this.servidor = servidor;
		this.tm = new TrackerManager();
		this.kg = new KeysGenerator();
	}
	
	public void run() {
		this.startServer();
	}
	
	public void startServer() {
		while(this.servidor.getContinuar() && this.continuarThread) {
			//conexion con tracker, me indica mi ip publica
			try {
				this.conexionTCP = null;
				conexionTCP = tm.getTracker(this.servidor.getIpExterna(), kg, this.servidor.getKpub(), this.servidor.getKpriv());//Obtengo tracker activo
				if(conexionTCP!=null) {
					Mensaje m = new Mensaje(Mensaje.Tipo.GET_PRIMARIO);
					m.enviarMensaje(conexionTCP,m, kg);
					byte[] datosDesencriptados = m.recibirMensaje(conexionTCP, kg);
					Mensaje response = (Mensaje) conexionTCP.convertFromBytes(datosDesencriptados);
		    		conexionTCP.getSocket().close();
		    		
		    		if (response.tipo == Mensaje.Tipo.TRACKER_PRIMARIO) {//Contenido: ipPrimario(string1), iplocalPrimario(string2), portPrimari(int1)
		    			log = "Peer obtiene ip:port tracker primario";
		    			this.servidor.logger.error(log);
		    			System.err.println(log);
		    			
		    			String ipTracker = !response.string1.equals(this.servidor.getIpExterna()) ? response.string1 : response.string2;
		    			conexionTCP = this.bindNewSocket(ipTracker, response.int1);
			    		
		    			if(conexionTCP != null) {
		    	    		tm.getSecretKey(conexionTCP, true, this.servidor.getKpub(), this.servidor.getKpriv(), kg, this.servidor.logger);//Obtengo clave simetrica
		    	    		m = new Mensaje(Mensaje.Tipo.KEEP_SOCKET, this.servidor.getIpExterna(), this.servidor.getIpInterna());//pido a tracker primario que guarde socket en lista
		    	    		
		    	    		try {
								m.enviarMensaje(conexionTCP, m, kg);
								
								//Recibo ACK
						        byte[] msgDesencriptado = m.recibirMensaje(conexionTCP, kg);
						        response = (Mensaje) conexionTCP.convertFromBytes(msgDesencriptado);
						        
						        if(response.tipo == Mensaje.Tipo.ACK) {//Contenido: ACK
						        	this.p2p = true;//Al ser p2p=true, si cae tracker con el que conecto, busco nuevo tracker
						        	this.continuarThread = false;
				    	    		log = "Peer servidor espera conexiones...";
				    	    		this.servidor.logger.error(log);
				    	    		System.err.println(log);
				    	    		
				    	    		this.encriptados();//Dado que socket con tracker ya tiene key simetrica
						        }else {
						        	log = "Su tracker actual ("+conexionTCP.getSocket().getInetAddress().getCanonicalHostName()+":"+conexionTCP.getSocket().getPort()+") no respondio con un ACK. No se envio el archivo.";
						        	this.servidor.logger.error(log);
				            		System.err.println(log);
				            	}
							} catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException
									| IllegalBlockSizeException | BadPaddingException e) {
								log = "Fallo enviar mensaje GET_PUBLIC_IP a tracker primario.";
								this.servidor.logger.error(log);
		                		System.err.println(log);
							}
		    			} else {
		    				log = "Fallo conexion con tracker primario ("+ipTracker+":"+response.int1+")";
			        		this.servidor.logger.error(log);
			        		System.err.println(log);
		    			}
		        	}else {
		        		log = "Su tracker actual ("+conexionTCP.getSocket().getInetAddress().getCanonicalHostName()+":"+conexionTCP.getSocket().getPort()+") no pudo recuperar el tracker primario. No se envio el archivo.";
		        		this.servidor.logger.error(log);
		        		System.err.println(log);
		        	}
				}else {
					log = "No hay trackers disponibles en este momento.";
					this.servidor.logger.error(log);
					System.err.println(log);
				}
			}catch(Exception ex) {
				log = "Fallo la conexion con el tracker."; 
				this.servidor.logger.error(log);
				System.err.println(log);
			}
			this.closeSocket();
			if(this.servidor.getContinuar() && this.continuarThread) {
				try {
					System.err.println("No pudo conectarse a un tracker. No podra compartir archivos. Intento nuevamente en 30 segundos.");
					Thread.sleep(15000);//espera 15seg antes de intentar nuevamente conexion con tracker
				} catch (InterruptedException e) {}
			}
		}
	}

	private void closeSocket() {
		if(this.conexionTCP != null) {
			if(this.conexionTCP.getSocket() != null) {
				try {
					this.conexionTCP.getSocket().close();
				} catch (IOException e) {}
			}
		}
	}

	private ConexionTCP bindNewSocket(String ipTracker, int puertoTracker) {
		int puerto = 7007;
		boolean exito = false;
		Socket socket = new Socket();
		
		do {
			try {
				socket = new Socket();
				
				try {
					socket.setReuseAddress(true);
				} catch (SocketException e1) {
					log = "bindNewSocket: Fallo set reuse address.";
					this.servidor.logger.error(log);
					System.err.println(log);
				}
				
				socket.bind(new InetSocketAddress(this.servidor.getIpInterna(), puerto));
				log = "bindNewSocket: BIND exitoso ("+this.servidor.getIpInterna()+":"+puerto+")";
				this.servidor.logger.info(log);
				System.err.println(log);
				
				try {
					socket.connect(new InetSocketAddress(ipTracker, puertoTracker));
					exito = true;
					log = "bindNewSocket: CONNECT exitoso ("+ipTracker+":"+puertoTracker+"). Cree el socket contra tracker.";
					this.servidor.logger.info(log);
					System.err.println(log);
				} catch (IOException e) {
					socket.close();//permite nuevo BIND
					log = "bindNewSocket: Fallo CONNECT ("+ipTracker+":"+puertoTracker+")";
					this.servidor.logger.error(log);
					System.err.println(log);
				}
			} catch (IOException e) {
				log = "bindNewSocket: Fallo BIND ("+this.servidor.getIpInterna()+":"+puerto+")";
				this.servidor.logger.error(log);
				System.err.println(log);
			}
			puerto++;
		}while(!exito && puerto<7012);

		try {
			return new ConexionTCP(socket);
		} catch (Exception e) {
			return null;
		}
	}
	
	private void bindSameSocket(int puertoPeer, String ipPeerDestino, int puertoPeerDestino) {
		boolean exito = false;
		Socket socket = new Socket();
		int i = 0;
		this.conexionTCP = null;
		
		try {
			socket.setReuseAddress(true);
		} catch (SocketException e1) {
			log = "bindSameSocket: Fallo set reuse address.";
			this.servidor.logger.error(log);
			System.err.println(log);
		}
		
		try {
			socket.bind(new InetSocketAddress(this.servidor.getIpInterna(), puertoPeer));
			log = "bindSameSocket: BIND exitoso ("+this.servidor.getIpInterna()+":"+puertoPeer+")";
			this.servidor.logger.info(log);
			System.err.println(log);
			do {
				try {
					socket.connect(new InetSocketAddress(ipPeerDestino, puertoPeerDestino));
					log = "bindSameSocket: CONNECT exitoso ("+ipPeerDestino+":"+puertoPeerDestino+"). Cree el socket contra peer.";
					this.servidor.logger.info(log);
					System.err.println(log);
					try {
						this.conexionTCP = new ConexionTCP(socket);
						exito = true;
						log = "Conexion establecida con cliente: ("+socket.getInetAddress().getCanonicalHostName()+":"+socket.getPort()+")";
						this.servidor.logger.info(log);
						System.err.println(log);
					} catch (Exception e) {
						System.err.println("Fallo creacion del ConexionTCP");
					}
				} catch (IOException e) {
					log = "bindSameSocket: Fallo CONNECT ("+ipPeerDestino+":"+puertoPeerDestino+"). Intento nuevamente...";
					this.servidor.logger.error(log);
					System.err.println(log);
					try {
						Thread.sleep(50);
					} catch (InterruptedException e1) {
						System.err.println("Fallo el sleep");
					}
				}
				i++;
			}while(!exito && i<10);
			if(!exito) {
				socket.close();//si no pudo conectarse, libero el bind
			}
		} catch (IOException e) {
			log = "bindSameSocket: Fallo BIND ("+this.servidor.getIpInterna()+":"+puertoPeer+")";
			this.servidor.logger.error(log);
			System.err.println(log);
		}
	}

	public void sinEncriptar() {
		try {
			Object o = conexionTCP.getInObj().readObject();//Recibo mensaje de peer cliente o Tracker
			Mensaje response = null;
			if (o instanceof Mensaje)
			{
				Mensaje m = (Mensaje) o;
				
				switch(m.tipo) {
					case CHECK_AVAILABLE: 
						log = "Recibi mensaje CHECK AVAILABLE";
						this.servidor.logger.info(log);
						System.err.println(log);
						if(!this.enviarACK(m, response))
							this.encriptados();
						break;
				}					
			}		
		} catch (IOException e) {
			System.err.println("Fallo conexion con tracker.");
		} catch (ClassNotFoundException e){
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void encriptados() {//recibo mensajes encriptados
		boolean salir = false;
		while(!salir && this.servidor.getContinuar()) {
			try {
				Mensaje m = new Mensaje();
				byte[] msgDesencriptado = m.recibirMensaje(conexionTCP, kg);
		        Object o = conexionTCP.convertFromBytes(msgDesencriptado);
				
		        Mensaje response = null;
				if (o instanceof Mensaje)
				{
					m = (Mensaje) o;
					
					switch(m.tipo) {
					
						case PEDIDO_P2P:
							log = "Recibi mensaje P2P de tracker";
							this.servidor.logger.info(log);
							System.err.println(log);
							
		    	    		//servidor crea nueva conexionTCP contra tracker, dado que esta se usará para conectar con un peer
							ThreadServer ts = new ThreadServer(this.servidor);
							Thread t = new Thread (ts);
		    	    		t.start();
		    	    		this.servidor.getListaThreadServer().add(ts);
		    	    		this.p2p = false;//al crear nuevo ThreadServer que se encarga de comunicacion con tracker, si este threadServer cae no necesita llamar a StartServer
		    	    		
		    	    		//tracker envia Address de peer que desea conectarse
		    	    		//iplocalPeerOrigen, ipExternaPeerOrigen, portlocalPeerOrigen, portExternoPeerOrigen
							this.connectToPeer(response, m.string1,m.string2,m.int1,m.int2);
							salir = true;
							if(this.conexionTCP != null) {this.sinEncriptar();}//comunicacion con peer-cliente
							break;
					
						case PIECES_AVAILABLE_CLOSE:
							log = "Recibi mensaje PIECES_AVAILABLE_CLOSE";
							this.servidor.logger.info(log);
							System.err.println(log);
							
							this.getPiecesAvailable(conexionTCP, m, response); 
							conexionTCP.getSocket().close();
							salir = true;
							break;
					
						case PIECES_AVAILABLE:
							log = "Recibi mensaje PIECES_AVAILABLE";
							this.servidor.logger.info(log);
							System.err.println(log);
							
							switch(this.getPiecesAvailable(conexionTCP, m, response)) {
							case 1://Todo ok
								break;
								
							case 2://No posee archivo, se retira del swarm al peer
								this.servidor.decrementarConexiones();
								conexionTCP.getSocket().close();
								
								log = "Fallo al obtener archivo con piezas disponibles.";
								this.servidor.logger.error(log);
								System.err.println(log);
								
								//string1: hash
								this.retirarmeDeSwarm(m.string1, response);//no posee el archivo con las partes disponibles. No debo pertenecer más al swarm.
								salir = true;
								break;
								
							case 3://Error. No se retira del swarm al peer
								this.servidor.decrementarConexiones();
								conexionTCP.getSocket().close();
								
								log = "Fallo al obtener archivo con piezas disponibles.";
								this.servidor.logger.error(log);
								System.err.println(log);
								
								salir = true;
								break;
							}
							break;
							
						case GET_PIECE:
							log = "Recibi mensaje GET_PIECE";
							this.servidor.logger.info(log);
							
							switch(this.getPiece(conexionTCP, m)) {
							case 1://Todo ok
								break;
								
							case 2://No posee archivo, se retira del swarm al peer
								this.servidor.decrementarConexiones();
								conexionTCP.getSocket().close();
								
								log = "No posee el archivo con la pieza.";
								this.servidor.logger.error(log);
								System.err.println(log);
								//string1: hash
								this.retirarmeDeSwarm(m.string1, response);//no posee el archivo con las partes disponibles. No debo pertenecer más al swarm.
								salir = true;
								break;
								
							case 3://Error. No se retira del swarm al peer
								this.servidor.decrementarConexiones();
								conexionTCP.getSocket().close();
								
								log = "Fallo al obtener pieza.";
								this.servidor.logger.error(log);
								System.err.println(log);
								
								salir = true;
								break;
							}
							break;
							
						case LOADDOWN:
							log = "Recibi mensaje LOADDOWN";
							this.servidor.logger.info(log);
							System.err.println(log);
							
							this.servidor.decrementarConexiones();
							conexionTCP.getSocket().close(); //Cierro la conexion
							salir = true;
							break;
					}					
				}		
			} catch (IOException e) {
				salir = true;
				System.err.println("Fallo conexion.");
				this.exception(e);
			} catch (ClassNotFoundException e){
				salir = true;
				System.err.println("Fallo conexion.");
				this.exception(e);
			} catch (Exception e) {
				salir = true;
				System.err.println("Fallo conexion.");
				this.exception(e);
			}	
		}
		this.closeSocket();
		if(p2p) {
			this.continuarThread = true;
			this.startServer();
		}
	}
	
	private void connectToPeer(Mensaje response, String ipInterna, String ipExterna, int portInterno, int portExterno) {
		// responder P2P y addr a tracker (tracker elimina socket de lista), desconectar socket, 
		//bind al mismo ip:port, connect a peer
		int localPort = this.conexionTCP.getSocket().getLocalPort();
		response = new Mensaje(Mensaje.Tipo.RESPUESTA_P2P, this.servidor.getIpInterna(), this.servidor.getIpExterna(), localPort);
		System.err.println("Mi Address enviada a tracker: pub ("+this.servidor.getIpExterna()+":"+localPort+"), priv ("+this.servidor.getIpInterna()+":"+localPort+")");
    	try {
			response.enviarMensaje(this.conexionTCP, response, kg);
			//desconexion de tracker
			this.closeSocket();
			//conexion a peer origen
			if(this.servidor.getIpExterna().equals(ipExterna)) {//misma ip publica, conecta a privada
				bindSameSocket(localPort,ipInterna,portInterno);
			} else {
				bindSameSocket(localPort,ipExterna,portExterno);
			}
		} catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException
				| BadPaddingException | IOException e) {
			log = "Fallo envio ACK a tracker (P2P)";
			this.servidor.logger.error(log);
			System.err.println(log);
			this.conexionTCP = null;
		}
	}

	private void exception(Exception e) {
		if(!this.p2p) {this.servidor.decrementarConexiones();}//Si el threadServer estaba conectado a un tracker no decremento conexiones
		
		try {
			this.conexionTCP.getSocket().close();
		} catch (IOException e1) {
			log = "Fallo por intentar cerrar socket";
			this.servidor.logger.error(log);
			System.err.println(log);
		}		
	}

	private int getPiece(ConexionTCP cliente, Mensaje m) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InterruptedException, ClassNotFoundException, IOException {	
		try {
			Thread.sleep(100);//Necesario en pruebas locales
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
			
	    try {
	    	log = "m.pathAParte: nombre del archivo buscado en peer servidor: "+m.string1;//string1: pathAParte
	    	this.servidor.logger.info(log);
			File archivo = new File(m.string1);//Busca la parte del archivo (si no es seed) o el archivo completo (si es seed)
			
			if(!archivo.exists()) {
				if(m.bool1) {//bool1: seed. Si soy seed y no encuentro el file significa que el peer servidor borró o movió el file. Dejara de ser seed
					return 2;
				} else {//Si no soy seed y no encuentra la parte que decía tener: puede ser porque este peer "no seed" terminó la
						//descarga del file, eliminando las partes y formando un file único. 
						//Pregunto si tiene el file
					String path = m.string1.substring(0, m.string1.lastIndexOf('.'));
					archivo = new File(m.string1);
					if(!archivo.exists()) {
						return 2;
					}
					//Ahora es seed
					m.bool1 = true;
				}
			} 
			
			int pieceSize = 1024*1024;
			byte[] piece = new byte[pieceSize];
			int tamañoParteBuscada = m.int1;//int1: sizeParte. 1MB, menos la última parte.
			int bytesread;
			
	        FileInputStream fis = new FileInputStream(archivo);
	        BufferedInputStream in = new BufferedInputStream(fis);
	        
	        if(m.bool1) {
	        	int parte = m.int2;//int2: nroParte.
	            while (parte>0 && (bytesread = in.read(piece,0,pieceSize)) != -1) {	    
	            	parte--;
	            }
				byte[] buffer = new byte[1024];
				int count;
				boolean continuar = true;
				while(tamañoParteBuscada>=1024 && continuar) {
					if((count = in.read(buffer)) > 0) {
						cliente.getOutBuff().write(buffer,0,count);
						tamañoParteBuscada-=count;	
					}else {
						continuar = false;
					}
				}
				count = in.read(buffer,0,tamañoParteBuscada);//Leo el resto que me falta
				cliente.getOutBuff().write(buffer,0,count);
	        }
	        else {
				byte[] buffer = new byte[1024];
				int count;
				while ((count = in.read(buffer)) > 0)
				{
					cliente.getOutBuff().write(buffer,0,count);
				}
	        }
			
	        cliente.getOutBuff().flush();
	        
	        in.close();//Cierro el buffer de lectura del JSON
	        fis.close();
	        
	        return 1;
	    }catch (IOException e) {
	    	return 3;
	    }
	}

	private int getPiecesAvailable(ConexionTCP cliente, Mensaje msg, Mensaje response) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
		Object obj;
		try {
			log = "Nombre del archivo buscado en peer servidor: "+msg.string1;
			this.servidor.logger.info(log);
			System.err.println(log);
			
			File file = new File(msg.string1);//path
			if(!file.exists()) {
				response = new Mensaje(Mensaje.Tipo.FILE_UNAVAILABLE);
		    	response.enviarMensaje(cliente, response, kg);
				
				return 2;
			}
			
			FileReader fileReader = new FileReader(msg.string1);
			obj = new JSONParser().parse(fileReader);
			fileReader.close();
			
			JSONArray ja = (JSONArray) obj;
			
			Integer[] misPartes = new Integer[ja.size()];
			
	    	for(int i=0; i<ja.size(); i++) {
	    		JSONObject jo = (JSONObject) ja.get(i);
	    		misPartes[i] = (jo.get("estado").equals("pendiente")) ? 0 : 1;//si tengo la parte es 1
	    	}
	    	
	    	obj = misPartes;
	    	response = new Mensaje(Mensaje.Tipo.PIECES_AVAILABLE, obj);
	    	response.enviarMensaje(cliente, response, kg);
	    	return 1;
			
		} catch (IOException | ParseException e1) {
			log = "Fallo al leer json con partes archivo en servidor.";
			this.servidor.logger.error(log);
			System.err.println(log);
			//e1.printStackTrace();
			
			response = new Mensaje(Mensaje.Tipo.ERROR);
	    	try {
	    		response.enviarMensaje(cliente, response, kg);
			} catch (IOException e) {
				log = "Fallo enviar error desde peer servidor.";
				this.servidor.logger.error(log);
				System.err.println(log);
				//e.printStackTrace();
			}
			
	    	return 3;
		}
	}

	private void retirarmeDeSwarm(String hash, Mensaje response) {
		//Obtener tracker
		try {
			//obtengo trakcer
			conexionTCP = tm.getTracker(this.servidor.getIpExterna(), kg, this.servidor.getKpub(), this.servidor.getKpriv());
    		
    		if(conexionTCP!=null) {
				//enviar mi peer socket y hash del archivo
				response = new Mensaje(Mensaje.Tipo.QUIT_SWARM, this.servidor.getIpExterna(), hash);
				response.enviarMensaje(conexionTCP, response, kg);
				conexionTCP.getSocket().close();
				
				log = "Peer servidor eliminado del swarm.";
				this.servidor.logger.info(log);
				System.err.println(log);
			}
			
		} catch (Exception e) {
			log = "Fallo al obtener tracker por peer servidor al intentar retirarme del swarm.";
			this.servidor.logger.error(log);
			System.err.println(log);
			//e.printStackTrace();
		}		
	}

	//Método que envía un ACK para confirmar que está activo
	private boolean enviarACK (Mensaje m, Mensaje response) throws Exception  {
		//Creo clave simetrica, usada para encriptar mensajes entre peer servidor y peer cliente
		SecretKey key = kg.generarLlaveSimetrica();
		conexionTCP.setKey(key);
		if(!m.bool1) {//bool1: mantenerConexion
			this.enviarMensaje(Mensaje.Tipo.ACK,m,response);
			return false;
		}else if(this.servidor.getNumConexiones() < this.servidor.getNumConexionesMaximas()) {
			this.servidor.aumentarConexiones();//Suma en 1 conexiones salientes del peer
			this.enviarMensaje(Mensaje.Tipo.ACK,m,response);
			return false;
		}else {
			this.enviarMensaje(Mensaje.Tipo.ERROR,m,response);
			return true;//salir == true
		}
	}
	
	private void enviarMensaje(Mensaje.Tipo tipoMensaje, Mensaje m, Mensaje response) throws IOException {
		switch(tipoMensaje) {
			case ACK:				
				byte[] datosAEncriptar = conexionTCP.convertToBytes(conexionTCP.getKey());
				byte[] mensajeEncriptado = kg.encriptarAsimetrico(datosAEncriptar, m.kpub);
				//Agrego key al mensaje
				response = new Mensaje(Mensaje.Tipo.ACK, mensajeEncriptado);
				conexionTCP.getOutObj().writeObject(response);
				log = "Respondi a mensaje CHECK AVAILABLE con ACK";
				this.servidor.logger.info(log);				
				break;
				
			case ERROR:
				response = new Mensaje(Mensaje.Tipo.ERROR);
				conexionTCP.getOutObj().writeObject(response);
				conexionTCP.getSocket().close();
				log = "Respondio a mensaje CHECK AVAILABLE con ERROR (peer servidor supera conexiones maximas)";
				this.servidor.logger.info(log);
				break;
		}
	}

}
