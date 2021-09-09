package TP_Final_SDyPP.Peer;

import java.util.ArrayList;

import TP_Final_SDyPP.DB4O.SeedTable;
import TP_Final_SDyPP.Otros.ConexionTCP;
import TP_Final_SDyPP.Otros.KeysGenerator;
import TP_Final_SDyPP.Otros.Mensaje;
import TP_Final_SDyPP.Otros.TrackerManager;

public class PartesDisponibles {

	private ThreadCliente threadCliente;
	private ConexionTCP conexionTCP;
	private KeysGenerator kg;
	private String log;
	private TrackerManager tm;
	
	private Integer[] peersPartes = new Integer[0];
	
	private int tiene;
	private boolean cienPorciento;
	
	private float porcentajeDisponible;
	private float porcentajeDescargadoDelSwarm;
	private int partesDisponiblesEnPeers;
	private ArrayList<SeedTable> swarm = new ArrayList<>();
	
	public PartesDisponibles(ThreadCliente threadCliente) {
		this.threadCliente = threadCliente;
		this.tm = new TrackerManager();
		this.kg = new KeysGenerator();
	}
	
	public void start() {

		ArrayList<SeedTable> arraySwarm = new ArrayList<SeedTable>();
		for(SeedTable st : this.threadCliente.getSwarm()) {
			arraySwarm.add(st);
		}
		
		if(arraySwarm.size() != 0) {
			
			int length = this.threadCliente.getMisPartes().length;
			peersPartes = new Integer[length];
			for(int i=0; i<length; i++) {
	    		peersPartes[i] = 0;//inicializa en 0
	    	}
			
			//Compruebo porcentaje disponible del archivo a descargar en el swarm
			this.getPorcentajeDisponibleEnSwarm(arraySwarm);
			if(this.calcularPorcentajeDisponibleYDescargado())//Si ya descargué todo lo que el swarm me ofrece
				this.getNewSwarm();
		
		} else {
			log = "ThreadPartesDisponibles - se obtuvo un swarm vacio para este archivo. "
					+ "Espero 10 seg antes de preguntar nuevamente por swarm.";
			this.threadCliente.logger.info(log);
			System.out.println(log);
			
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			this.getNewSwarm();
		}
	}

	private void getNewSwarm() {
		try {
			log = "ThreadPartesDisponibles - Estableciendo conexion con tracker para obtener nuevo swarm.";
			this.threadCliente.logger.info(log);
			System.out.println(log);
			
			//Consigo tracker
			conexionTCP = tm.getTracker(this.threadCliente.getCliente().getIPExterna(), kg, this.threadCliente.getCliente().getKpub(), this.threadCliente.getCliente().getKpriv());
			if(conexionTCP!=null) {
				//Pido nuevo swarm a tracker
				Mensaje m = new Mensaje(Mensaje.Tipo.SWARM, this.threadCliente.getHash(), this.threadCliente.getCliente().getIPExterna(), this.threadCliente.getCliente().getIPInterna());
				m.enviarMensaje(conexionTCP, m, kg);
				
		        byte[] msgDesencriptado = m.recibirMensaje(conexionTCP, kg);
		        Mensaje response = (Mensaje) conexionTCP.convertFromBytes(msgDesencriptado);
		        
		        conexionTCP.getSocket().close();
				if(m.tipo == Mensaje.Tipo.SWARM) {					
					swarm = (ArrayList<SeedTable>) response.lista;
					if(swarm.size()>0) {
						log = "ThreadPartesDisponibles - Nuevo swarm obtenido.";
						this.threadCliente.logger.info(log);
						System.out.println(log);
						
						//asigno swarm a atributo swarm y peersdisponibles de threadcliente
						this.threadCliente.setSwarm(swarm);
						this.threadCliente.setPeersDisponibles(swarm);
					}
				}	
			}else {
	        	log = "No hay trackers disponibles en este momento.";
	        	this.threadCliente.logger.error(log);
	        	System.out.println(log);
	        }				
			
		} catch (Exception e) {
			log = "ThreadPartesDisponibles - Fallo comunicacion con tracker al pedir nuevo swarm.";
			this.threadCliente.logger.error(log);
			System.out.println(log);
			
			e.printStackTrace();
		}
	}

	private boolean calcularPorcentajeDisponibleYDescargado() {
		porcentajeDisponible = (float) (tiene * 100) / peersPartes.length;
		String porcentajeDisponibleS = String.format("%.2f", porcentajeDisponible);
		
		log = "ThreadPartesDisponibles - El swarm posee un "+porcentajeDisponibleS+"% del total del archivo a descargar."; 
		this.threadCliente.logger.info(log);
		System.out.println(log);
		
		//Actualizar porcentaje disponible en tableview
		String hashArchivo = this.threadCliente.getHash();//Para diferenciar row de table a actualizar
		this.threadCliente.getCliente().notifyObserver(5, porcentajeDisponibleS+"%"+"-"+hashArchivo);
		
		//comparo las partes que descargue yo hasta ahora con las de los peers. Si tengo el 100% de lo que ofrecen y no el 100%
		//del archivo, tengo que pedir otro swarm al tracker porque este ya no tiene nada más que ofrecerme.
		int coincidencias = 0;
		partesDisponiblesEnPeers = 0; 
		for(int i=0; i<peersPartes.length; i++) {
			if(peersPartes[i] == 1) {
				partesDisponiblesEnPeers++;
				if(this.threadCliente.getMisPartes()[i] == 1) {
					coincidencias++;
				}	
			}
		}

		if(partesDisponiblesEnPeers!=0)
			porcentajeDescargadoDelSwarm = (float) (coincidencias * 100) / partesDisponiblesEnPeers;
		else
			porcentajeDescargadoDelSwarm = 0;
		String porcentajeDescargadoDelSwarmS = String.format("%.2f", porcentajeDescargadoDelSwarm);
		
		log = "ThreadPartesDisponibles - Del "+porcentajeDisponibleS+"% disponible en el swarm, descargo "+porcentajeDescargadoDelSwarmS+"%";
		this.threadCliente.logger.info(log);
		System.out.println(log);
		
		if(porcentajeDescargadoDelSwarm == 100)//Si descargué el 100% de las partes ofrecidas por el swarm tengo que pedir uno nuevo.
			return true;
		else
			return false;
	}

	private void getPorcentajeDisponibleEnSwarm(ArrayList<SeedTable> arraySwarm) {
		int index = 0;
		tiene = 0;//contador partes que tienen los peers del swarm
		cienPorciento = false;

		//fin while por cienPorciento=true o repase todos los peers del swarm
		//calculo % total de archivo disponible en swarm
		while(!cienPorciento && index<arraySwarm.size()) {
			//recupera archivo del peer con las partes descargadas
			String path = arraySwarm.get(index).getPath() + "/"+this.threadCliente.getName()+"Partes.json";
			Mensaje m = new Mensaje(Mensaje.Tipo.PIECES_AVAILABLE_CLOSE, path);
			String ip = arraySwarm.get(index).getIpPeer();
			String iplocal = arraySwarm.get(index).getIpLocalPeer();
			
			try {
				System.out.println("Llamada conexionP2P desde PartesDisponibles.");
				this.conexionTCP = this.threadCliente.conexionP2P(ip, iplocal);
				if(this.conexionTCP != null) {
					//Mantener conexión false
					tm.getSecretKey(conexionTCP,false,this.threadCliente.getCliente().getKpub(),this.threadCliente.getCliente().getKpriv(),kg,this.threadCliente.logger);
					
					log = "ThreadPartesDisponibles - Consulta archivo partes pendientes de "+ip+" - "+iplocal;
					this.threadCliente.logger.info(log);
					System.out.println(log);
					m.enviarMensaje(conexionTCP, m, kg);
		        	
		        	byte[] msgDesencriptado = m.recibirMensaje(conexionTCP, kg);
			        m = (Mensaje) conexionTCP.convertFromBytes(msgDesencriptado);
			        
			        log = "ThreadPartesDisponibles - Recibo archivo partes pendientes de "+ip+" - "+iplocal;
		    		this.threadCliente.logger.info(log);
		    		System.out.println(log);
					
					conexionTCP.getSocket().close();
					
					if(m.tipo == Mensaje.Tipo.PIECES_AVAILABLE) {
						Integer[] partesArchivo = (Integer[]) m.lista;
						for(int i=0; i<partesArchivo.length; i++) {
							if(partesArchivo[i] == 1) {
								if(peersPartes[i] == 0) {
									peersPartes[i] = 1;
									tiene++;
								}
							}
						}
					} else if(m.tipo == Mensaje.Tipo.FILE_UNAVAILABLE) {
						log = "ThreadPartesDisponibles - Peer "+ip+" - "+iplocal+" ya no posee el archivo de partes descargadas.";
			    		this.threadCliente.logger.info(log);
			    		System.out.println(log);
					} else if(m.tipo == Mensaje.Tipo.ERROR) {
						log = "ThreadPartesDisponibles - Peer "+ip+" - "+iplocal+" fallo al leer archivo de partes descargadas.";
			    		this.threadCliente.logger.info(log);
			    		System.out.println(log);
					}
					
					//Si tiene==peersPartes.size -> los peers del swarm tienen todas las partes disponibles
					if(tiene == peersPartes.length) {
						cienPorciento = true;
					}	
				} else {
					log = "ThreadPartesDisponibles - threadCliente.conexionP2P devolvio una conexionTCP null";
					this.threadCliente.logger.error(log);
					System.out.println(log);
				}
			} catch (Exception e) {
				log = "ThreadPartesDisponibles - No se pudo conectar a peer ("+ip+" - "+iplocal+")";
				this.threadCliente.logger.error(log);
				System.out.println(log);
			}
			
			index++;
		}			
	}
	
}
