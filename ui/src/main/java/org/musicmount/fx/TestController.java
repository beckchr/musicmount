package org.musicmount.fx;

import java.io.File;
import java.util.logging.Level;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraintsBuilder;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;

import org.musicmount.server.MusicMountTestServer;
import org.musicmount.util.LoggingUtil;

public class TestController {
	static {
		LoggingUtil.configure(MusicMountTestServer.class.getPackage().getName(), Level.FINE);
	}
	
	private Pane pane;
	private TextField musicFolderTextField;
	private Button musicFolderChooseButton;
	private TextField mountFolderTextField;
	private Button mountFolderChooseButton;
	private TextField musicPathTextField;
	private ChoiceBox<String> musicPathChoiceBox;
	private TextField portTextField;
	private TextField userTextField;
	private PasswordField passwordField;
	private Button runButton;
	private Text statusText;
	
	private final MusicMountTestServer server;
	private final FXCommandModel model;
	private Integer port = 8080;
	private final Service<Object> service = new Service<Object>() {
		@Override
		protected Task<Object> createTask() {
			return new Task<Object>() {
				@Override
				protected Object call() throws Exception {
					String user = userTextField == null || userTextField.getText().trim().isEmpty() ? null : userTextField.getText().trim();
					String pass = passwordField == null || passwordField.getText().trim().isEmpty() ? null : passwordField.getText().trim();
					server.start(model.getMusicFolder(), model.getMountFolder(), model.getMusicPath(), port, user, pass);
					return null;
				}
				@Override
				public boolean cancel(boolean mayInterruptIfRunning) {
					try {
						server.stop();
					} catch (Exception e) {
						return false;
					}
					return true;
				}
			};
		}
	};

	public TestController(final FXCommandModel model) {
		this.model = model;
		this.pane = createView();

		pane.visibleProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				if (newValue) {
					updateAll();
				}
			}
		});

		server = new MusicMountTestServer();

		musicFolderTextField.textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				model.setMusicFolder(model.toFolder(newValue));
				if (model.getCustomMusicPath() == null) {
					updateMusicPath();
				}
				updateRunButton();
			}
		});
		musicFolderChooseButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				DirectoryChooser directoryChooser = new DirectoryChooser();
				directoryChooser.setTitle("Music Folder");
				File directory = directoryChooser.showDialog(null);
				if (directory != null) {
					musicFolderTextField.setText(directory.getAbsolutePath());
				}
			}
		});
		mountFolderTextField.textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				model.setMountFolder(model.toFolder(newValue));
				if (model.getCustomMusicPath() == null) {
					updateMusicPath();
				}
                updateRunButton();
			}
		});
		mountFolderChooseButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				DirectoryChooser directoryChooser = new DirectoryChooser();
				directoryChooser.setTitle("Mount Folder");
				File directory = directoryChooser.showDialog(null);
				if (directory != null) {
					mountFolderTextField.setText(directory.getAbsolutePath());
				}
			}
		});
		musicPathTextField.textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				if (model.getCustomMusicPath() != null) {
					model.setCustomMusicPath(newValue);
				}
                updateRunButton();
			}
		});
        musicPathChoiceBox.getSelectionModel().selectedIndexProperty().addListener(new ChangeListener<Number>() {
        	@Override
        	public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                if(newValue.intValue() == 0) { // auto
                	model.setCustomMusicPath(null);
                } else if (model.getCustomMusicPath() == null) { // custom
                	model.setCustomMusicPath(FXCommandModel.DEFAULT_CUSTOM_MUSIC_PATH);
                }
                updateMusicPath();
                updateRunButton();
        	}
		});
		portTextField.textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				try {
					port = Integer.valueOf(portTextField.getText());
				} catch (NumberFormatException e) {
					port = null;
				}
                updateRunButton();
			}
		});

    	service.setOnRunning(new EventHandler<WorkerStateEvent>() {
			public void handle(WorkerStateEvent event) {
				statusText.setText(server.getSiteURL(model.getMusicPath(), port));
				runButton.setText("Stop Server");
            	disableControls(true);
			}
		});
		service.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
			public void handle(WorkerStateEvent event) {
				statusText.setText("Server stopped");
				runButton.setText("Start Server");
				disableControls(false);
			}
		});
		service.setOnFailed(new EventHandler<WorkerStateEvent>() {
			public void handle(WorkerStateEvent event) {
				if (service.getException() != null) {
					service.getException().printStackTrace();
					statusText.setText("Server failed: " + service.getException().getMessage());
				} else {
					statusText.setText("Server failed");
				}
				runButton.setText("Start Server");
				disableControls(false);
			}
		});
		runButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				if (service.isRunning()) {
					service.cancel();
				} else {
					service.reset();
					service.start();
				}
			}
		});
		
		updateAll();
	}

	void disableControls(boolean disable) {
		musicFolderTextField.setDisable(disable);
		musicFolderChooseButton.setDisable(disable);
		mountFolderTextField.setDisable(disable);
		mountFolderChooseButton.setDisable(disable);
		musicFolderTextField.setDisable(disable);
		musicPathChoiceBox.setDisable(disable);
		musicPathTextField.setDisable(disable || model.getCustomMusicPath() == null);
		portTextField.setDisable(disable);
		userTextField.setDisable(disable);
		passwordField.setDisable(disable);
	}
	
	Pane createView() {
		GridPane grid = new GridPane();
		grid.setAlignment(Pos.CENTER);
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(15));

//		grid.setGridLinesVisible(true);
		grid.getColumnConstraints().add(0, ColumnConstraintsBuilder.create().hgrow(Priority.NEVER).build());
		grid.getColumnConstraints().add(1, ColumnConstraintsBuilder.create().hgrow(Priority.ALWAYS).build());
		grid.getColumnConstraints().add(2, ColumnConstraintsBuilder.create().hgrow(Priority.NEVER).build());

		Text titleText = new Text("Launch MusicMount Test Server");
		titleText.setId("test-title");
		titleText.getStyleClass().add("tool-title");
		grid.add(titleText, 0, 0, 2, 1);

		/*
		 * music folder
		 */
		Label musicFolderLabel = new Label("Music Folder");
		musicFolderTextField = new TextField();
		musicFolderChooseButton = new Button("...");
		grid.add(musicFolderLabel, 0, 1);
		GridPane.setHalignment(musicFolderLabel, HPos.RIGHT);
		grid.add(musicFolderTextField, 1, 1);
		grid.add(musicFolderChooseButton, 2, 1);

		/*
		 * mount folder
		 */
		Label mountFolderLabel = new Label("Mount Folder");
		mountFolderTextField = new TextField();
		mountFolderChooseButton = new Button("...");
		grid.add(mountFolderLabel, 0, 2);
		GridPane.setHalignment(mountFolderLabel, HPos.RIGHT);
		grid.add(mountFolderTextField, 1, 2);
		grid.add(mountFolderChooseButton, 2, 2);

		/*
		 * music path
		 */
		Label musicPathLabel = new Label("Music Path");
		musicPathTextField = new TextField();
		musicPathChoiceBox = new ChoiceBox<String>(FXCollections.observableArrayList("Auto", "Custom"));
		grid.add(musicPathLabel, 0, 3);
		GridPane.setHalignment(musicPathLabel, HPos.RIGHT);
		HBox.setHgrow(musicPathTextField, Priority.ALWAYS);
        HBox musicPathHBox = new HBox(10);
        musicPathHBox.getChildren().addAll(musicPathChoiceBox, musicPathTextField);
		grid.add(musicPathHBox, 1, 3);

		/*
		 * server port
		 */
		Label portLabel = new Label("Server Port");
		grid.add(portLabel, 0, 4);
		GridPane.setHalignment(portLabel, HPos.RIGHT);
		portTextField = new TextField();
		grid.add(portTextField, 1, 4);

		/*
		 * user
		 */
		Label userLabel = new Label("User");
		grid.add(userLabel, 0, 5);
		GridPane.setHalignment(userLabel, HPos.RIGHT);
		userTextField = new TextField();
		grid.add(userTextField, 1, 5);

		/*
		 * password
		 */
		Label passwordLabel = new Label("Password");
		grid.add(passwordLabel, 0, 6);
		GridPane.setHalignment(passwordLabel, HPos.RIGHT);
		passwordField = new PasswordField();
		grid.add(passwordField, 1, 6);

		/*
		 * run button
		 */
		runButton = new Button("Start Server");
		runButton.setId("test-button");
		runButton.getStyleClass().add("run-button");
		HBox runButtonHBox = new HBox(10);
		runButtonHBox.setAlignment(Pos.BOTTOM_RIGHT);
		runButtonHBox.getChildren().add(runButton);
		grid.add(runButtonHBox, 1, 7, 2, 1);
		GridPane.setVgrow(runButtonHBox, Priority.ALWAYS);

		statusText = new Text();
		statusText.setId("test-status");
		statusText.getStyleClass().add("status-text");
		grid.add(statusText, 0, 8, 3, 1);
		
		return grid;
	}
	
	void updateAll() {
		updateMusicFolder();
		updateMountFolder();
		updateMusicPath();
		updateRunButton();
		updatePort();
	}

	void updateMusicFolder() {
		musicFolderTextField.setText(model.getMusicFolder() != null ? model.getMusicFolder().toString() : null);
	}

	void updateMountFolder() {
		mountFolderTextField.setText(model.getMountFolder() != null ? model.getMountFolder().toString() : null);
	}

	void updateMusicPath() {
        musicPathChoiceBox.getSelectionModel().select(model.getCustomMusicPath() == null ? 0 : 1);
        musicPathTextField.setDisable(model.getCustomMusicPath() == null);
		musicPathTextField.setText(model.getMusicPath());
	}
	
	void updateRunButton() {
		runButton.setDisable(!model.isSite() || port == null || !server.checkMusicPath(model.getMusicPath()));
	}
	
	void updatePort() {
		portTextField.setText(port != null ? port.toString() : null);
	}

	public Pane getPane() {
		return pane;
	}
	
	public Service<Object> getService() {
		return service;
	}
}
