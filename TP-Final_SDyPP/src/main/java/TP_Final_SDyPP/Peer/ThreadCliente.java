package TP_Final_SDyPP.Peer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import TP_Final_SDyPP.DB4O.SeedTable;
import TP_Final_SDyPP.Otros.ConexionTCP;
import TP_Final_SDyPP.Otros.KeysGenerator;
import TP_Final_SDyPP.Otros.Mensaje;
import TP_Final_SDyPP.Otros.TrackerManager;
import TP_Final_SDyPP.Peer.ParteArchivo.Estado;
import TP_Final_SDyPP.Peer.ThreadCliente.TipoError;

public class ThreadCliente implements Runnable {

	private Cliente cliente;
	private ArrayList<SeedTable> swarm = new ArrayList<SeedTable>();
	private ArrayList<SeedTable> peersDisponibles = new ArrayList<SeedTable>();
	private String pathPartes;//Path donde almaceno partes descargadas y JSONPartes
	private ArrayList<ParteArchivo> partesArchivo = new ArrayList<ParteArchivo>();
	private String name;//del archivo a descargar
	private int cantPartes;
	private String hash;
	private TrackerManager tm;
	private KeysGenerator kg;
	private boolean stop;
	private String descargaPendienteFileName;
	private String descargado;
	private String log;
	private final Object lockFileGraficos = new Object();
	private final Object lockFileDescargasPendientes = new Object();
	private final Object lockFilePartesPendientes = new Object();
	private final Object lockSwarm = new Object();
	private final Object lockLeechersUtilizados = new Object();
	private Integer[] misPartes;
	private int leechersUtilizados = 0;//Cantidad de leechers utilizados por el threadCliente (del total que posee el cliente)
	private boolean threadClienteSinLeecher;
	private PartesDisponibles partesDisponibles;
	public Logger logger;
	
	private ConexionTCP conexionTCP = null;
	private SeedTable seed;
	private Integer[] peerPartes;
	//Del seed
	private String ip;
	private String iplocal;
	
	//Getters & Setters//
	public boolean getThreadClienteSinLeecher() {
		return this.threadClienteSinLeecher;
	}
	
	public int getLeechersUtilizados() {
		return this.leechersUtilizados;
	}
	
	public void decrementarCantidadLeechersUtilizados() {
		synchronized(lockLeechersUtilizados) {
			this.leechersUtilizados--;
		}
	}
	
	public void aumentarCantidadLeechersUtilizados() {
		synchronized(lockLeechersUtilizados) {
			this.leechersUtilizados++;
		}
	}
	
	public Integer[] getMisPartes() {
		return misPartes;
	}
	
	public Object getLockSwarm() {
		return lockSwarm;
	}
	
	public Cliente getCliente() {
		return cliente;
	}
	
	public void setStop(boolean stop) {
		log = "Pausando descarga "+this.getName();
		logger.info(log);
		System.out.println(log);
		this.stop = stop;
	}
	
	public boolean getStop() {
		return this.stop;
	}
	
	public String getHash() {
		return hash;
	}
	
	public int getCantPartes() {
		return cantPartes;
	}

	public void setCantPartes(int cantPartes) {
		this.cantPartes = cantPartes;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	public String getDescargaPendienteFileName() {
		return descargaPendienteFileName;
	}
	
	public ArrayList<SeedTable> getPeersDisponibles() {
		return peersDisponibles;
	}

	public void setPeersDisponibles(ArrayList<SeedTable> swarm) {
		for(SeedTable s : swarm) {
			this.peersDisponibles.add(s);	
		}
	}
	
	public ArrayList<SeedTable> getSwarm() {
		return swarm;
	}

	public void setSwarm(ArrayList<SeedTable> swarm) {
		synchronized(lockSwarm) {
			this.swarm = swarm;
		}
	}
	
	public void RemovePeerDeSwarm(SeedTable seed, String ip, String iplocal) {
		synchronized(lockSwarm) {
			this.swarm.remove(seed);
			
			log = "ThreadCliente - Retiro peer de swarm permanentemente ("+ip+" - "+iplocal+")";
			this.logger.info(log);
			System.out.println(log);
		}
	}

	public String getPathPartes() {
		return pathPartes;
	}

	public void setPathPartes(String pathPartes) {
		this.pathPartes = pathPartes;
	}

	public ArrayList<ParteArchivo> getPartesArchivo() {
		synchronized(this.partesArchivo) {
			return partesArchivo;
		}
	}

	public void setPartesArchivo(ArrayList<ParteArchivo> partesArchivo) {
		this.partesArchivo = partesArchivo;
	}
	
	public void setDescargado(String descargado) {
		this.descargado = descargado;
	}
	
	public Object getLockFilePartesPendientes() {
		return this.lockFilePartesPendientes;
	}
	
	//Constructor//
	public ThreadCliente(ArrayList<SeedTable> swarm, String path, String name, int cantPartes, String hash, String descargaPendienteFileName, Cliente cliente, String descargado) {
		this.cliente = cliente;
		this.swarm = swarm;
		this.setPeersDisponibles(swarm);
		this.pathPartes = path;
		this.name = name;
		this.cantPartes = cantPartes;
		this.hash = hash;
		this.tm = new TrackerManager();
		this.kg = new KeysGenerator();
		this.stop = false;
		this.descargaPendienteFileName = descargaPendienteFileName;
		this.descargado = descargado;
		this.logger = this.cliente.logger;
		this.partesDisponibles = new PartesDisponibles(this);
	}
	
	public enum TipoError{
		CONEXION,
		TIEMPO,
		HASH
	}
	
	public void getNewSwarm() {
		this.partesDisponibles.start();
	}

	@Override
	public void run() {
		boolean exito = this.getPartesPendientes();
		long startTime = System.currentTimeMillis();
		
		if(exito) {
			log = "Descarga iniciada.";
			logger.info(log);
			System.out.println(log);
			
			//Inicio thread que pide swarm si este se vacía. Indica % del archivo disponible en swarm
			this.getNewSwarm();
			
			//Para no repetir mensaje
			boolean mostreMensajeNoHayLeechers = false;
			
			//Mientras falte recuperar partes del archivo y no se haya pausado la descarga
			while(!this.getStop() && this.getPartesArchivo().size()>0) {
				
				//Compruebo si existe ThreadCliente que no tiene a disposición ningún leecher
				threadClienteSinLeecher = this.preguntoThreadClienteSinLeecher();
				
				if(this.swarm.size()>0) {
					if((!threadClienteSinLeecher || this.leechersUtilizados==0) && 
							this.cliente.getLeechersDisponibles()>0 &&
							this.leechersUtilizados<this.swarm.size()) {
						//Si no hay otro threadCliente que necesite leechers y hay leechers disponibles --> creo leecher
						//Si no tengo leechers y hay leechers disponibles --> creo leecher
						//Si uso menos leechers que tamaño swarm (evita usar más de 1 leecher por peer-servidor)
						mostreMensajeNoHayLeechers = false;
						
						synchronized(this.cliente.getLockLeechersDisponibles()) {
							this.cliente.reducirLeechersDisponibles();
							this.aumentarCantidadLeechersUtilizados();
						}
						
						peerPartes = new Integer[0];
						boolean peerDisponible = false;
						seed = null;
						ip = "";
						iplocal = "";
						
						//Busco un peer disponible
						peerDisponible = this.getPeer(peerDisponible);
						
						//Recupero partes del peer disponible
						if(peerDisponible){
							try {
								ThreadLeecher tl = new ThreadLeecher(this, conexionTCP, seed, ip, iplocal, peerPartes);
								Thread t = new Thread(tl);
								t.start();
							} catch (Exception e) {
								log = "Fallo al iniciar thread leecher.";
								logger.error(log);
								System.out.println(log);
								
								//e.printStackTrace();
							}
							
						}else {//No obtuve peer, actualizar peers disponibles con swarm
							synchronized(this.cliente.getLockLeechersDisponibles()) {
								this.cliente.aumentarLeechersDisponibles();
								this.decrementarCantidadLeechersUtilizados();
							}
							this.actualizarPeersDisponibles();			
						}	
					} else {//No hay leechers disponibles o existen otros ThreadClientes sin leechers que 
							//necesitan uno y yo ya tengo al menos 1
							//o swarm es más chico que leechers disponibles
						if(!mostreMensajeNoHayLeechers) {
							mostreMensajeNoHayLeechers = true;
							log = "Leechers descargando...";
							logger.info(log);
							System.out.println(log);
						}
						try {	
							Thread.sleep(5000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				} else {
					this.actualizarPeersDisponibles();
				}
			}
			
			long endTime = System.currentTimeMillis();
			long time = endTime - startTime;
			
			//Saco a threadCliente del array
			this.cliente.eliminarThreadCliente(this);
			
			if(!this.stop) {//Si no se pauso la descarga
				//Armar archivo
				try {
					this.armarFile(this.pathPartes + "/" + this.name + ".0",this.pathPartes + "/" + this.name);
				} catch (IOException e1) {
					log = "Fallo armar archivo descargado.";
					logger.error(log);
					System.out.println(log);
					//e1.printStackTrace();
				}
				
				//Velocidad de descarga Promedio
				String velocidadPromedio = this.calcularVelocidadPromedio(this.pathPartes + "/" + this.name);
				log = "Velocidad de descarga promedio: "+velocidadPromedio;
				this.logger.info(log);
				System.out.println(log);
				
				//Soy seed ya que tengo el archivo completo. Se lo comunico al tracker
				ConexionTCP c = null;
				try {
					c = tm.getTracker(this.cliente.getIPExterna(), kg, this.cliente.getKpub(), this.cliente.getKpriv());	
					
					if(c!=null) {
						Mensaje m = new Mensaje(Mensaje.Tipo.NEW_SEED, hash, this.cliente.getIPExterna(), this.cliente.getIPInterna());
						m.enviarMensaje(c,m,kg);
						c.getSocket().close();
					}else {
						log = "No hay trackers disponibles. Fallo al notificar a tracker que soy un nuevo seed.";
						logger.error(log);
						System.out.println(log);
					}
					
				} catch (Exception e) {
					log = "Exception al notificar a tracker que soy un nuevo seed.";
					logger.error(log);
					System.out.println(log);
				}
											
				//Eliminar partes descargadas
				this.eliminarPartes(this.pathPartes + "/" + this.name + ".0");
				
				//Retiro a Descarga pendiente de array
				this.eliminarDescargaPendiente(time, velocidadPromedio);
				this.cliente.notifyObserver(6, hash);
			}else {//Si se pauso la descarga 
				this.detenerDescargaPendiente(time);
			}
		}
	}
	
	private boolean preguntoThreadClienteSinLeecher() {
		synchronized(this.cliente.getListaThreadsClientes()) {
			for(int i=0; i<this.cliente.getListaThreadsClientes().size(); i++) {
				if(!this.cliente.getListaThreadsClientes().get(i).equals(this))//Si no soy ese threadCliente
					if(this.cliente.getListaThreadsClientes().get(i).getLeechersUtilizados() == 0)
						return true;
			}
		}
		return false;
	}

	private void actualizarPeersDisponibles() {
		if(this.getSwarm().size()>0) {
			
			synchronized(this.getLockSwarm()) {
				this.setPeersDisponibles(this.getSwarm());
				try {//*
					Thread.sleep(1000);
				} catch (InterruptedException e) {}
			}
			
			log = "ThreadCliente - Actualizacion de peers disponibles con swarm";
			this.logger.info(log);
			System.out.println(log);
		}else{//swarm vacio, esperar a que sea renovado por threadPartesDisponibles
			this.getNewSwarm();
		}
	}
	
	private boolean getPeer(boolean peerDisponible) {
		int index = 0;
		
		//Mientras no encuentre un peer disponible (conectado y que tenga el archivo)
		while(!this.getStop() && !peerDisponible && index<this.getPeersDisponibles().size()) {
			seed = this.getPeersDisponibles().get(index);
			ip = seed.getIpPeer();
			iplocal = seed.getIpLocalPeer();
			try {
				//Pregunto si está despierto y obtengo clave simetrica
				System.out.println("Llamada conexionP2P desde ThreadCliente.");
				conexionTCP = this.conexionP2P(ip, iplocal);
				if(this.conexionTCP != null) {
					if(tm.getSecretKey(conexionTCP,true,this.getCliente().getKpub(),this.getCliente().getKpriv(),kg,this.logger)) {
						//Pregunto por su archivo con las partes que descargo
						String path = seed.getPath() + "/"+this.getName()+"Partes.json";
						Mensaje m = new Mensaje(Mensaje.Tipo.PIECES_AVAILABLE, path, seed.getHash());
						m.enviarMensaje(conexionTCP,m,kg);
						
						byte[] datosDesencriptados = m.recibirMensajeLarge(conexionTCP,kg);
						Mensaje response = (Mensaje) conexionTCP.convertFromBytes(datosDesencriptados);
						
						switch(response.tipo) {
							case PIECES_AVAILABLE:
								peerPartes = (Integer[]) response.lista;
								peerDisponible = true;
								
								log = "ThreadCliente - Conexion con peer disponible ("+ip+" - "+iplocal+") y obtencion de archivo de partes descargadas";
								this.logger.info(log);
								System.out.println(log);
								break;
								
							case FILE_UNAVAILABLE:
								log = "ThreadCliente - El peer servidor ("+ip+" - "+iplocal+") no posee el archivo de partes descargadas.";
								this.logger.error(log);
								System.out.println(log);
								conexionTCP.getSocket().close();
							
								this.RemovePeerDeSwarm(seed, ip, iplocal);
								
								break;
								
							case ERROR:
								log = "ThreadCliente - No pudo obtener archivo de partes descargadas del peer disponible ("+ip+" - "+iplocal+")";
								this.logger.error(log);
								System.out.println(log);
								conexionTCP.getSocket().close();
								break;
						}
					} else {//Peer con máxima cantidad de conexiones
						this.RemovePeerDeSwarm(seed, ip, iplocal);
					}
				} else {
					log = "ThreadCliente - threadCliente.conexionP2P devolvio una conexionTCP null";
					this.logger.error(log);
					System.out.println(log);
					
					this.RemovePeerDeSwarm(seed, ip, iplocal);//tracker no pudo conectarse a peerservidor
				}
			} catch (Exception e) {
				e.printStackTrace();//*s
				log = "ThreadCliente - Fallo conexion con peer disponible del swarm ("+ip+" - "+iplocal+")";
				this.logger.error(log);
				System.out.println(log);

				this.RemovePeerDeSwarm(seed, ip, iplocal);
				
				//Guardar error y cuál peer es el involucrado (en archivo de carpeta Graficos)
				this.almacenarErrorDescargaParte(TipoError.CONEXION, ip, iplocal);

			}
			log = "ThreadCliente - Retirar al peer ("+ip+" - "+iplocal+") de peers disponibles";
			this.logger.info(log);
			System.out.println(log);
			this.getPeersDisponibles().remove(index);//Lo saco del array, haya o no podido conectarme a él.
			//Aunque haya podido conectarme al peer, lo saco de la lista de peers disponibles para no utilizar un único peer.
		}
		
		return peerDisponible;
	}

	private boolean getPartesPendientes() {
		Object obj;
		
		try {
			FileReader fileReader = new FileReader(this.pathPartes+"/"+name+"Partes.json");
			obj = new JSONParser().parse(fileReader);
			fileReader.close();
			
			JSONArray ja = (JSONArray) obj;
			misPartes = new Integer[ja.size()];
			
	    	for(int i=0; i<ja.size(); i++) {
	    		JSONObject jo = (JSONObject) ja.get(i);
	    		if(jo.get("estado").equals("pendiente")) {
	    			misPartes[i] = 0;
	    			
	    			String parteS = (String) jo.get("parte");
	    			int parte = Integer.parseInt(parteS);
	               
		    		String hash = (String) jo.get("hash");
		    		
		    		String sizeS = (String) jo.get("size");
	    			int size = Integer.parseInt(sizeS);
		    		
		    		ParteArchivo pa = new ParteArchivo(parte,hash,Estado.PENDIENTE,size);
		    		this.partesArchivo.add(pa);	
	    		} else {
	    			misPartes[i] = 1;
	    		}
	    	}
	    	//mezclar partes en array
	    	Collections.shuffle(partesArchivo);
	    	return true;
		} catch (IOException | ParseException e) {
			log = "Fallo lectura json con partes faltantes.";
			logger.error(log);
			System.out.println(log);
			
			//e.printStackTrace();
			return false;
		}
				
	}
	
	private String calcularVelocidadPromedio(String pathArchivo) {
		File file = new File(pathArchivo);
		long size = file.length(); //en Bytes
		
		long time = this.sumaTiempoPartes();
		
		String velocidad = this.calcularVelocidad(time, size);
		return velocidad;
	}

	private long sumaTiempoPartes() {
		long time = 0;
		
		synchronized(this.lockFileDescargasPendientes) {
			Object obj;
			try {
				FileReader fileReader = new FileReader(this.cliente.getPathDescargasPendientes()+"/"+descargaPendienteFileName);
				obj = new JSONParser().parse(fileReader);
				fileReader.close();
				
				JSONObject jsonObject = (JSONObject) obj;
				JSONArray tiemposPartes = (JSONArray) jsonObject.get("tiemposPartes");
				//Sumo tiempo partes
				for(int i=0; i<tiemposPartes.size() ; i++) {
					JSONObject tiempoParte = (JSONObject) tiemposPartes.get(i);
					String tiempoS = (String) tiempoParte.get("tiempo");
					long tiempo = Long.parseLong(tiempoS);
					time += tiempo;
				}
			} catch (IOException | ParseException e) {
				log = "Fallo lectura json de descarga pendiente.";
				logger.error(log);
				System.out.println(log);
			}
		}
	
		return time;
	}

	private void eliminarDescargaPendiente(long time, String velocidadPromedio) {
		int i=0;
		synchronized(this.cliente.getListaDescargasPendientes()) {
			while(i<this.cliente.getListaDescargasPendientes().size()){
				if(this.cliente.getListaDescargasPendientes().get(i).getHash().equals(this.hash)) {
					
					Object obj;
					try {
						FileReader fileReader = new FileReader(this.cliente.getPathDescargasPendientes()+"/"+descargaPendienteFileName);
						obj = new JSONParser().parse(fileReader);
						fileReader.close();
						
						JSONObject jsonObject = (JSONObject) obj;
						
						//Tiempo promedio que llevó la descarga de las partes
						JSONArray tiemposPartes = (JSONArray) jsonObject.get("tiemposPartes");
						JSONObject tiempoParte = (JSONObject) tiemposPartes.get(0);
						String timeS = (String) tiempoParte.get("tiempo");
						long tiempo = Long.parseLong(timeS);
						long tiempoTotal = tiempo;
						long min = tiempo;
						long max = tiempo;
						long[] tiemposPartesArray = new long[tiemposPartes.size()];
						tiemposPartesArray[0]=tiempo;
						for(int j=1; j<tiemposPartes.size(); j++) {
							tiempoParte = (JSONObject) tiemposPartes.get(j);
							timeS = (String) tiempoParte.get("tiempo");
							tiempo = Long.parseLong(timeS);
							tiemposPartesArray[j]=tiempo;//Para calcular sd
							tiempoTotal += tiempo;
							if(tiempo<min)
								min = tiempo;
							if(tiempo>max)
								max = tiempo;
						}
						long tiempoPromedio = tiempoTotal / tiemposPartes.size();
						String tiempoMinimo = this.calcularTiempo(min);
						log ="Tiempo minimo para descargar parte: "+tiempoMinimo;
						this.logger.info(log);
						System.out.println(log);
						
						String tiempoMaximo = this.calcularTiempo(max);
						log = "Tiempo maximo para descargar parte: "+tiempoMaximo;
						this.logger.info(log);
						System.out.println(log);
						
						String tiempoPromedioS = this.calcularTiempo(tiempoPromedio);
						log = "Tiempo promedio por parte: "+tiempoPromedioS;
						this.logger.info(log);
						System.out.println(log);
						
						long sd = this.calcularSD(tiempoPromedio,tiemposPartesArray);
						String tiempoDesvioEstandar = this.calcularTiempo(sd);
						log = "Desvio estandar: "+tiempoDesvioEstandar;
						this.logger.info(log);
						System.out.println(log);
						
						//Calculo tiempo total que llevó la descarga
				        timeS = (String) jsonObject.get("time");
				        long timeParcial = Long.parseLong(timeS);//Tiempo parcial que llevaba la descarga
				        timeParcial += time;
				        String tiempoFinal = this.calcularTiempo(timeParcial);
				        
				        //Carga datos descarga terminada al archivo de graficos de la descarga
				        this.cargarDescargaTerminada(tiempoFinal, velocidadPromedio,
				        		tiempoMinimo, tiempoMaximo, tiempoPromedioS, tiempoDesvioEstandar, this.hash);			        
				        this.cliente.notifyObserver(7, this.hash);//Informa a vista que agregue boton "Ver" de graficos
				        
				        log = "Descarga finalizada. Tiempo total: "+tiempoFinal;
				        logger.info(log);
						System.out.println(log);
						
						//Guardo porcentaje descargado (100%)
				        jsonObject.put("descargado", "100%");
				        this.escribirArchivoDescargasPendientes(jsonObject);
					} catch (IOException | ParseException e) {
						log = "Fallo lectura json de descarga pendiente.";
						logger.error(log);
						System.out.println(log);
					}
				}
				i++;
			}
		}
	}

	private void cargarDescargaTerminada(String tiempoFinal, String velocidadPromedio, 
			String tiempoMinimo, String tiempoMaximo, String tiempoPromedioS, String tiempoDesvioEstandar, String hash) {
		
		String path = this.cliente.getPathGraficos()+"/"+hash+".json";
		Object obj;
		try {
			FileReader fileReader = new FileReader(path);
			obj = new JSONParser().parse(fileReader);
			fileReader.close();
			
			JSONObject json = (JSONObject) obj;
			json.put("tiempoFinal", tiempoFinal);
			json.put("velocidadPromedio", velocidadPromedio);
			json.put("tiempoMinimo", tiempoMinimo);
			json.put("tiempoMaximo", tiempoMaximo);
			json.put("tiempoPromedio", tiempoPromedioS);
			json.put("tiempoDesvioEstandar", tiempoDesvioEstandar);
			
			File file = new File(path);
			file.createNewFile();
			FileWriter fileW = new FileWriter(file);
			fileW.write(json.toJSONString());
			fileW.flush();
			fileW.close();
		} catch (IOException | ParseException e) {
			log = "Fallo update json tiempos descarga terminada.";
			logger.error(log);
			System.out.println(log);
		}		
	}

	private void detenerDescargaPendiente(long time) {
		
		synchronized(this.lockFileDescargasPendientes) {
			for(DescargaPendiente dp : this.cliente.getListaDescargasPendientes()) {
				if(dp.getHash().equals(this.hash)) {
					dp.setActivo(false);
					
					Object obj;
					try {
						FileReader fileReader = new FileReader(this.cliente.getPathDescargasPendientes()+"/"+descargaPendienteFileName);
						obj = new JSONParser().parse(fileReader);
						fileReader.close();
						
						JSONObject jsonObject = (JSONObject) obj;
						//Guardo tiempo parcial que lleva la descarga en archivo Descargas Pendientes
				        String timeS = (String) jsonObject.get("time");
				        long timeParcial = Long.parseLong(timeS);
				        timeParcial += time;
				        timeS = String.valueOf(timeParcial);
				        jsonObject.put("time", timeS);
				        
				        //Guardo porcentaje parcial descargado
				        jsonObject.put("descargado", this.descargado);
			        	
				        this.escribirArchivoDescargasPendientes(jsonObject);
					} catch (IOException | ParseException e) {
						e.printStackTrace();
					}
				}
			}
		}
		log = "Descarga "+this.name+" pausada";
		logger.info(log);
		System.out.println(log);
	}
	
	private long calcularSD(long tiempoPromedio, long[] tiemposPartesArray) {
		long sd = 0;
		long length = tiemposPartesArray.length;
		for(int j=0; j<length; j++) {
            sd += Math.pow(tiemposPartesArray[j] - tiempoPromedio, 2);
        }
        return (long) Math.sqrt(sd/length);
	}

	private String calcularTiempo(long time) {
		String horas = "00";
		String minutos = "00";
		String segundos = "00";
		
		long hora = 1000 * 60 * 60;//Milisegundos en una hora
		long n = time / hora;
		if(n>0) {
			if(n<10)
				horas = "0"+String.valueOf(n);
			else
				horas = String.valueOf(n);
			time -= n*hora; 
		}
		
		long minuto = 1000 * 60;
		n = time / minuto;
		if(n>0) {
			if(n<10)		
				minutos = "0"+String.valueOf(n);
			else
				minutos = String.valueOf(n);
			time -= n*minuto; 
		}
		
		long segundo = 1000;
		n = time / segundo;
		if(n>0) {
			if(n<10)
				segundos = "0"+String.valueOf(n);
			else
				segundos = String.valueOf(n);
			time -= n*segundo; 
		}
		
		String tiempo = String.format("%s.%03d",horas+":"+minutos+":"+segundos,time); 
		return tiempo;
	}
	
	public String calcularVelocidad(long time, long sizeParte) {
		String velocidad = "";
		double segundos = (double) time / 1000;//ms a seg
		String[] unidades = {"Bits","Kbps","Mbps","Gbps"};
		int pos = 0;
		
		double x = (sizeParte*8) / segundos;// (*8) Bytes a Bits
		while(x >= 1024) {
			x /= 1024;
			pos++;
		}
		
		velocidad = String.format("%.2f " + unidades[pos],x);
		return velocidad;
	}

	public void almacenarTiempoDescargaDeParte(long time, int nroParte) {
		synchronized(this.lockFileDescargasPendientes) {
			Object obj;
			try {
				FileReader fileReader = new FileReader(this.cliente.getPathDescargasPendientes()+"/"+descargaPendienteFileName);
				obj = new JSONParser().parse(fileReader);
				fileReader.close();
				
				JSONObject jsonObject = (JSONObject) obj;
				//Guardo tiempo que tomo descargar la parte
				JSONArray tiemposPartes = (JSONArray) jsonObject.get("tiemposPartes");
				JSONObject tiempoParte = (JSONObject) tiemposPartes.get(nroParte);
				String timeS = String.valueOf(time);
				tiempoParte.put("tiempo", timeS);
				
				this.escribirArchivoDescargasPendientes(jsonObject);
			} catch (IOException | ParseException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void escribirArchivoDescargasPendientes(JSONObject jsonObject) throws IOException {
		File file = new File(this.cliente.getPathDescargasPendientes()+"/"+descargaPendienteFileName);
		file.createNewFile();
		FileWriter fileW = new FileWriter(file);
		fileW.write(jsonObject.toJSONString());
		fileW.flush();
		fileW.close();
	}
		
	public void actualizarPartesPendientes(int nroParte) {//Pasa de pendiente a descargada
		Object obj;
		synchronized(this.lockFilePartesPendientes) {
			try {
				FileReader fileReader = new FileReader(this.pathPartes+"/"+name+"Partes.json");
				obj = new JSONParser().parse(fileReader);
				fileReader.close();
			
				JSONArray ja = (JSONArray) obj;		
				JSONObject parte = (JSONObject) ja.get(nroParte);
				parte.put("estado", "descargada");						
			
	    		File file = new File(this.pathPartes+"/"+name+"Partes.json");
	    		file.createNewFile();
				FileWriter fileW = new FileWriter(file);
				fileW.write(ja.toJSONString());
				fileW.flush();
				fileW.close();
	
			} catch (IOException | ParseException e) {
				log = "Fallo actualizar parte pendiente a descargada en archivo \""+name+"Partes.json\".";
				logger.error(log);
				System.out.println(log);
				e.printStackTrace();
			}
		}
	}
	
	//Recontruir archivo descargado
	private void armarFile(String oneOfFiles, String into) throws IOException{
    	armarFile(new File(oneOfFiles), new File(into));
    }
	
	private void armarFile(File oneOfFiles, File into) throws IOException {
    	armarFile(listOfFilesToMerge(oneOfFiles), into);
    }

	private List<File> listOfFilesToMerge(File oneOfFiles) {
        String tmpName = oneOfFiles.getName();
        final String destFileName = tmpName.substring(0, tmpName.lastIndexOf('.'));
        File[] files = oneOfFiles.getParentFile().listFiles(
                (File dir, String name) -> name.matches(destFileName + "[.]\\d+"));
        Arrays.sort(files);
        return Arrays.asList(files);
    }
	
	private void armarFile(List<File> files, File into) throws IOException {
	    try (FileOutputStream fos = new FileOutputStream(into);
	         BufferedOutputStream mergingStream = new BufferedOutputStream(fos)) {
	        for (File f : files) {
	            Files.copy(f.toPath(), mergingStream);
	        }
	        mergingStream.close();
	        fos.close();
	    }
	}
	
	private void eliminarPartes(String parte) {
		List<File> files = listOfFilesToMerge(new File(parte));
		for(File f : files) {
			f.delete();
		}
	}

	public void almacenarVelocidadDescargaParte(long time, int nroParte, String ip, String iplocal) {
		synchronized(this.lockFileGraficos) {//Lock para modificar archivo
			String path = this.cliente.getPathGraficos()+"/"+this.hash+".json";
			try {
				FileReader fileReader = new FileReader(path);
				Object obj = new JSONParser().parse(fileReader);
				fileReader.close();
			
				JSONObject json = (JSONObject) obj;
				
				//Agrego en orden la parte que se descargo y su tiempo
				JSONArray tiempoPartes = (JSONArray) json.get("tiempoPartes");
				JSONObject piece = new JSONObject();
				piece.put("tiempo", time);
				piece.put("nroParte", nroParte);
				tiempoPartes.add(piece);	
				
				/*
				 *Agrego el peer del que descargué parte (si es que no existe ya en el array). Sumo 1 en cantidad descargadas
				 * de él y sumo al tiempo parcial el tiempo que tomó descargar esta parte.
				*/
				JSONArray descargasPeers = (JSONArray) json.get("descargasPeers");
				//Busco si ya existe el peer según socket
				boolean existe = false;
				String socketPeer = ip+":"+iplocal;
				int i=0;
				while(i<descargasPeers.size() && !existe) {
					JSONObject peer = (JSONObject) descargasPeers.get(i);
					String socket = (String) peer.get("peer");
					if(socket.equals(socketPeer))
						existe = true;
					else
						i++;
				}
				
				if(existe) {
					JSONObject peer = (JSONObject) descargasPeers.get(i);
					long cantidad = (long) peer.get("cantidad");
					cantidad++;
					peer.put("cantidad",cantidad);
					
					long tiempo = (long) peer.get("tiempo");
					tiempo += time;
					peer.put("tiempo",tiempo);					
				} else {
					//Si no existía el peer
					JSONObject peer = new JSONObject();
					peer.put("peer", socketPeer);
					peer.put("cantidad", 1);
					peer.put("tiempo", time);
					descargasPeers.add(peer);					
				}
				
				File file = new File(path);
				file.createNewFile();
				FileWriter fileW = new FileWriter(file);
				fileW.write(json.toJSONString());
				fileW.flush();
				fileW.close();	
			} catch (IOException | ParseException e) {
				e.printStackTrace();
			}			
		}
	}

	public void almacenarErrorDescargaParte(TipoError tipoError, String ip, String iplocal) {
		synchronized(this.lockFileGraficos) {//Lock para modificar archivo
			String path = this.cliente.getPathGraficos()+"/"+this.hash+".json";
			try {
				FileReader fileReader = new FileReader(path);
				Object obj = new JSONParser().parse(fileReader);
				fileReader.close();
			
				JSONObject json = (JSONObject) obj;
				
				/*
				 *Agrego el peer del que descargué parte que falló (si es que no existe ya en el array). Sumo 1 
				 * al tipo de error que ocurrió en ese peer.
				*/
				JSONArray fallosPeers = (JSONArray) json.get("fallosPeers");
				//Busco si ya existe el peer según socket
				boolean existe = false;
				String socketPeer = ip+":"+iplocal;
				int i=0;
				while(i<fallosPeers.size() && !existe) {
					JSONObject peer = (JSONObject) fallosPeers.get(i);
					String socket = (String) peer.get("peer");
					if(socket.equals(socketPeer))
						existe = true;
					else
						i++;
				}
				
				if(existe) {
					String tipoErrorS;
					switch(tipoError) {
						case CONEXION:
							tipoErrorS = "errorConexion";
							break;
						case TIEMPO:
							tipoErrorS = "errorTiempo";
							break;
						default:
							tipoErrorS = "errorHash";
							break;
					}
					
					JSONObject peer = (JSONObject) fallosPeers.get(i);
					long error = (long) peer.get(tipoErrorS);
					error++;
					peer.put(tipoErrorS,error);					
				} else {
					//Si no existía el peer
					JSONObject peer = new JSONObject();
					peer.put("peer", socketPeer);
					peer.put("errorTiempo", 0);
					peer.put("errorConexion", 0);
					peer.put("errorHash", 0);
					fallosPeers.add(peer);					
				}
				
				File file = new File(path);
				file.createNewFile();
				FileWriter fileW = new FileWriter(file);
				fileW.write(json.toJSONString());
				fileW.flush();
				fileW.close();	
			} catch (IOException | ParseException e) {
				e.printStackTrace();
			}			
		}
	}
	
	public ConexionTCP conexionP2P(String ipPeer, String iplocalPeer) {
		ConexionTCP conexionTCP = null;
		try {
			conexionTCP = tm.getTracker(this.cliente.getIPExterna(), kg, this.cliente.getKpub(), this.cliente.getKpriv());//Obtengo tracker activo
			if(conexionTCP!=null) {
				Mensaje m = new Mensaje(Mensaje.Tipo.GET_PRIMARIO);
				m.enviarMensaje(conexionTCP,m, kg);
				byte[] datosDesencriptados = m.recibirMensaje(conexionTCP, kg);
				Mensaje response = (Mensaje) conexionTCP.convertFromBytes(datosDesencriptados);
	    		conexionTCP.getSocket().close();
	    		
	    		if (response.tipo == Mensaje.Tipo.TRACKER_PRIMARIO) {//Contenido: ipPrimario(string1), iplocalPrimario(string2), portPrimari(int1)
	    			log = "Peer obtiene ip:port tracker primario";
	    			logger.info(log);
	    			System.out.println(log);
	    			
	    			String ipTracker = !response.string1.equals(this.cliente.getIPExterna()) ? response.string1 : response.string2;
	    			conexionTCP = this.bindNewSocket(ipTracker, response.int1);
	    			
	    			if(conexionTCP != null) {
		    			int localPort = conexionTCP.getSocket().getLocalPort();
	    	    		tm.getSecretKey(conexionTCP, true, this.cliente.getKpub(), this.cliente.getKpriv(), kg, this.cliente.logger);//Obtengo clave simetrica
	    	    		m = new Mensaje(Mensaje.Tipo.PEDIDO_P2P, ipPeer, iplocalPeer, this.cliente.getIPExterna(), this.cliente.getIPInterna(), conexionTCP.getSocket().getLocalPort());
	    	    		System.out.println("Mi Address enviada a tracker: pub ("+this.cliente.getIPExterna()+":"+conexionTCP.getSocket().getLocalPort()+"), priv ("+this.cliente.getIPInterna()+":"+conexionTCP.getSocket().getLocalPort()+")");
	    	    		try {
							m.enviarMensaje(conexionTCP, m, kg);//threadtracker busca peer destino, le envia mi addr y peer destino envia su addr a tracker, luego tracker a este peer
							
							//Recibo Addr peer destino o error
					        byte[] msgDesencriptado = m.recibirMensaje(conexionTCP, kg);
					        response = (Mensaje) conexionTCP.convertFromBytes(msgDesencriptado);
					        
					        if(response.tipo == Mensaje.Tipo.RESPUESTA_P2P) {
					        	//desconexion de tracker
					        	try {
					        		conexionTCP.getSocket().close();
					        	} catch(Exception e) {}
					        	//conexion con peer destino
					        	if(this.cliente.getIPExterna().equals(response.string2)) {//misma ip publica, conecta a privada
									return bindSameSocket(localPort,response.string1,response.int1);
								} else {
									return bindSameSocket(localPort,response.string2,response.int2);
								}
					        }else {
					        	log = "Su tracker actual ("+conexionTCP.getSocket().getInetAddress().getCanonicalHostName()+":"+conexionTCP.getSocket().getPort()+") no respondio con un P2P.";
					        	logger.error(log);
			            		System.out.println(log);
			            	}
						} catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException
								| IllegalBlockSizeException | BadPaddingException e) {
							log = "Fallo enviar mensaje GET_PUBLIC_IP a tracker primario.";
							logger.error(log);
	                		System.out.println(log);
						}
	    			} else {
	    				log = "Fallo conexion con tracker primario ("+ipTracker+":"+response.int1+")";
		        		logger.error(log);
		        		System.err.println(log);	
	    			}
	        	}else {
	        		log = "Su tracker actual ("+conexionTCP.getSocket().getInetAddress().getCanonicalHostName()+":"+conexionTCP.getSocket().getPort()+") no pudo recuperar el tracker primario.";
	        		logger.error(log);
	        		System.out.println(log);
	        	}
			}else {
				log = "No hay trackers disponibles en este momento.";
				logger.error(log);
				System.out.println(log);
			}
		}catch(Exception ex) {
			log = "Fallo la conexion con el tracker."; 
			logger.error(log);
			System.out.println(log);
		}
		return null;
	}
	
	private ConexionTCP bindSameSocket(int puertoPeer, String ipPeerDestino, int puertoPeerDestino) {
		boolean exito = false;
		int i = 0;
		ConexionTCP conexionTCP = null;
		Socket socket = new Socket();
		
		try {
			socket.setReuseAddress(true);
		} catch (SocketException e1) {
			log = "bindSameSocket: Fallo set reuse address.";
			logger.error(log);
			System.out.println(log);
		}
		
		try {
			socket.bind(new InetSocketAddress(this.cliente.getIPInterna(), puertoPeer));
			log = "bindSameSocket: BIND exitoso ("+this.cliente.getIPInterna()+":"+puertoPeer+")";
			logger.info(log);
			System.out.println(log);
			do {
				try {
					socket.connect(new InetSocketAddress(ipPeerDestino, puertoPeerDestino));
					log = "bindSameSocket: CONNECT exitoso ("+ipPeerDestino+":"+puertoPeerDestino+"). Cree el socket contra peer.";
					logger.info(log);
					System.out.println(log);

					try {
						conexionTCP = new ConexionTCP(socket);
						exito = true;
					} catch (Exception e) {
						log = "Fallo creacion del ConexionTCP";
						logger.error(log);
						System.out.println(log);
					}
				} catch (IOException e) {
					log = "bindSameSocket: Fallo CONNECT ("+ipPeerDestino+":"+puertoPeerDestino+"). Intento nuevamente...";
					logger.error(log);
					System.out.println(log);
					try {
						Thread.sleep(50);
					} catch (InterruptedException e1) {
						System.out.println("Fallo el sleep");
					}
				}
				i++;
			}while(!exito && i<10);
			if(!exito) {
				socket.close();//si no pudo conectarse, libero el bind
			}
		} catch (IOException e) {
			log = "bindSameSocket: Fallo BIND ("+this.cliente.getIPInterna()+":"+puertoPeer+")";
			logger.error(log);
			System.out.println(log);
			System.out.println(e);
		}
		
		try {//*
			Thread.sleep(100);//duermo antes de devolver el socket a cliente para pruebas locales, por si escribe mensaje antes de que peer-servidor este listo para escuchar
		} catch (InterruptedException e1) {
			System.out.println("Fallo el sleep");
		}//*
		
		return conexionTCP;
	}

	private ConexionTCP bindNewSocket(String ipTracker, int puertoTracker) {
		int puerto = 7013;
		boolean exito = false;
		ConexionTCP conexionTCP = null;
		Socket socket = new Socket();
		
		do {
			try {
				socket = new Socket();
				
				try {
					socket.setReuseAddress(true);
				} catch (SocketException e1) {
					log = "bindNewSocket: Fallo set reuse address.";
					logger.error(log);
					System.out.println(log);
				}
				
				socket.bind(new InetSocketAddress(this.cliente.getIPInterna(), puerto));
				log = "bindNewSocket: BIND exitoso ("+this.cliente.getIPInterna()+":"+puerto+")";
				logger.info(log);
				System.out.println(log);
				
				try {
					socket.connect(new InetSocketAddress(ipTracker, puertoTracker));
					exito = true;
					log = "bindNewSocket: CONNECT exitoso ("+ipTracker+":"+puertoTracker+"). Cree el socket contra tracker.";
					logger.info(log);
					System.out.println(log);
				} catch (IOException e) {
					socket.close();
					log = "bindNewSocket: Fallo CONNECT ("+ipTracker+":"+puertoTracker+")";
					logger.error(log);
					System.out.println(log);
				}
			} catch (IOException e) {
				log = "bindNewSocket: Fallo BIND ("+this.cliente.getIPInterna()+":"+puerto+")";
				logger.error(log);
				System.out.println(log);
			}
			puerto++;
		}while(!exito && puerto<7017);

		try {
			return new ConexionTCP(socket);
		} catch (Exception e) {
			return null;
		}
	}
}
