package TP_Final_SDyPP.Tracker;

import TP_Final_SDyPP.Otros.ConexionTCP;

public class AddressPeer {
	private ConexionTCP conexionTCP;
	private String ipPub;
	private String ipPriv;
	
	public AddressPeer(ConexionTCP conexionTCP, String ipPub, String ipPriv) {
		this.conexionTCP = conexionTCP;
		this.ipPub = ipPub;
		this.ipPriv = ipPriv;
	}

	public ConexionTCP getConexionTCP() {
		return conexionTCP;
	}

	public void setConexionTCP(ConexionTCP conexionTCP) {
		this.conexionTCP = conexionTCP;
	}

	public String getIpPub() {
		return ipPub;
	}

	public void setIpPub(String ipPub) {
		this.ipPub = ipPub;
	}

	public String getIpPriv() {
		return ipPriv;
	}

	public void setIpPriv(String ipPriv) {
		this.ipPriv = ipPriv;
	}
}
