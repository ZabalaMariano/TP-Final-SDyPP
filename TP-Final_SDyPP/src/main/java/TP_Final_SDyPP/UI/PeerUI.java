package TP_Final_SDyPP.UI;

import java.io.IOException;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;

public class PeerUI extends Application{

	public static void main(String[] args) {	
		launch(args);
	}

	@Override
	public void start(Stage stage) {		
		FXMLLoader loader = new FXMLLoader(getClass().getResource("principalPeer.fxml"));
		Parent root;
		try {
			root = loader.load();
			PrincipalPeerController controller = loader.getController();
			Scene scene = new Scene(root);
			scene.getStylesheets().add(getClass().getResource("peer.css").toExternalForm());
			
			stage.setOnHidden(e -> {
				try {
					controller.cerrarClienteYServidor();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			});
			
			stage.setTitle("Peer");
			stage.setResizable(false);
			stage.setScene(scene);
			stage.show();
		} catch (IOException e2) {
			e2.printStackTrace();
		}
	}

}
