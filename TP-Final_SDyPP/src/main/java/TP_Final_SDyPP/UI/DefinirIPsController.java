package TP_Final_SDyPP.UI;

import java.io.IOException;

import TP_Final_SDyPP.Peer.Cliente;
import TP_Final_SDyPP.Peer.PeerMain;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class DefinirIPsController {

	//FXML cambiarSocket.fxml
	@FXML private TextField ipPriv;
	@FXML private TextField ipPub;
	@FXML private Button btnCancelar;
	@FXML private Button btnAceptar;
	@FXML private TextArea msg;
	
	private Cliente cliente;
	private ObservableList<DatosDescarga> data;
	
	public void setTextIPpriv(String ipPriv) {
		this.ipPriv.setText(ipPriv);
	}
	
	public void setTextIPpub(String ipPub) {
		this.ipPub.setText(ipPub);
	}
	
	public void setCliente(Cliente cliente) {
		this.cliente = cliente;
	}
	
	public void cancelar(ActionEvent event) throws Exception {
		this.cerrar();
	}
	
	public boolean validate(String ip) {
	    String PATTERN = "^((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)$";
	    return ip.matches(PATTERN);
	}
	
	public void aceptar(ActionEvent event) throws Exception {
		boolean ipPrivValido = true;
		boolean ipPubValido = true;
		String nuevaIPpriv = ipPriv.getText();
		String nuevaIPpub = ipPub.getText();
		ipPrivValido = validate(nuevaIPpriv);
		ipPubValido = validate(nuevaIPpub);
		
		if(ipPrivValido && ipPubValido) {
			
			Task<Void> task = new Task<Void>() {
				@Override
				protected Void call() throws Exception {				    
					PeerMain.getInstance().setIpPubManual(nuevaIPpub);					
					PeerMain.getInstance().setIpPrivManual(nuevaIPpriv);
					return null;
				}
			};
			new Thread(task).start();
			cerrar();
		} else {
			String error = "";
			if(!ipPrivValido) {
				error = "-IP privada inválida";	
			}
			if(!ipPubValido) {
				error = error == "" ? "-IP pública inválida" : error+"\n-IP pública inválida";
			}
			msg.setText(error);
			msg.setVisible(true);
		}
	}
	
	private void cerrar() {
		Stage stage = (Stage) btnCancelar.getScene().getWindow();
	    stage.close();//Cerrar ventana		
	}

	public void setBotones(ObservableList<DatosDescarga> data) {
		this.data = data;
	}
}
