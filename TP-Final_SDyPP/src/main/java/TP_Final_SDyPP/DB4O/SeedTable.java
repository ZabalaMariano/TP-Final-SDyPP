package TP_Final_SDyPP.DB4O;

import java.io.Serializable;

public class SeedTable implements Serializable{

	private static final long serialVersionUID = -5446326028328677238L;
	private String hash;
	private boolean seed;
	private String path;
	private String ipPeer;
	private String ipLocalPeer;
	private boolean disponible;
	
	public SeedTable() {
		
	}
	
	public SeedTable(String hash, boolean seed, String path, String ipPeer, String ipLocalPeer) {
		this.hash = hash;
		this.seed = seed;
		this.path = path;
		this.ipPeer = ipPeer;
		this.ipLocalPeer = ipLocalPeer;
		this.disponible = true;
	}
	
	public SeedTable(String hash, String ipPeer, String ipLocalPeer) {//Eliminar seed, nuevo seed
		this.hash = hash;
		this.ipPeer = ipPeer;
		this.ipLocalPeer = ipLocalPeer;
	}
	
	public SeedTable(String ipPeer, String ipLocalPeer) {//Deshabilitar seed
		this.ipPeer = ipPeer;
		this.ipLocalPeer = ipLocalPeer;
	}

	public SeedTable(String hash) {//Get seedtable by hash
		this.hash = hash;
	}
	
	public SeedTable(String hash, boolean b) {//Swarm - get seeds by hash y disponibilidad
		this.hash = hash;
		this.disponible = b;
	}
	
	public SeedTable(String hash, boolean isSeed, boolean b) {//Swarm - get seeds by hash, isSeed y disponibilidad
		this.hash = hash;
		this.disponible = b;
		this.seed = isSeed;
	}
	
	public boolean getDisponible() {
		return disponible;
	}
	
	public void setDisponible(boolean b) {
		this.disponible = b;
	}

	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}

	public boolean isSeed() {
		return seed;
	}

	public void setSeed(boolean seed) {
		this.seed = seed;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getIpPeer() {
		return ipPeer;
	}

	public void setIpPeer(String ipPeer) {
		this.ipPeer = ipPeer;
	}

	public String getIpLocalPeer() {
		return ipLocalPeer;
	}
	
	public void setIpLocalPeer(String ipLocalPeer) {
		this.ipLocalPeer = ipLocalPeer;
	}
}
