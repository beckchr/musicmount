/*
 * Copyright 2013-2014 Odysseus Software GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.musicmount.fx;

import java.io.File;
import java.net.MalformedURLException;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraintsBuilder;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;

import org.musicmount.tester.MusicMountTester;
import org.musicmount.util.BonjourService;

public class FXTestController {
	static final Logger LOGGER = Logger.getLogger(FXTestController.class.getName());

	private static final String STATUS_NO_RELATIVE_MUSIC_PATH = "Cannot calculate relative music path, custom path path required";

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
	private CheckBox bonjourCheckBox;
	private Button runButton;
	private Text statusText;
	
	private final BonjourService bonjourService;
	private final MusicMountTester tester;
	private final FXCommandModel model;
	private final Service<Object> service = new Service<Object>() {
		@Override
		protected Task<Object> createTask() {
			return new Task<Object>() {
				@Override
				protected Object call() throws Exception {
					tester.start(model.getMusicFolder(), model.getMountFolder(), model.getMusicPath(), model.getServerPort().intValue(), getUser(), getPassword());
					if (bonjourService != null && model.isBonjour()) {
						startBonjour();
					}
					tester.await();
					return null;
				}
				@Override
				public boolean cancel(boolean mayInterruptIfRunning) {
					try {
						if (bonjourService != null && model.isBonjour()) {
							stopBonjour();
						}
						tester.stop();
					} catch (Exception e) {
						return false;
					}
					return true;
				}
			};
		}
	};

	public FXTestController(final FXCommandModel model) {
		this.model = model;
		this.bonjourService = createBonjour();
		this.pane = createView();

		pane.visibleProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				if (newValue) {
					updateAll();
				}
			}
		});

		tester = new MusicMountTester();

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
				File directory = directoryChooser.showDialog(musicFolderChooseButton.getScene().getWindow());
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
				directoryChooser.setTitle("Site Folder");
				File directory = directoryChooser.showDialog(mountFolderChooseButton.getScene().getWindow());
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
					model.setServerPort(Integer.valueOf(portTextField.getText()));
				} catch (NumberFormatException e) {
					model.setServerPort(null);
				}
                updateRunButton();
			}
		});
		bonjourCheckBox.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				model.setBonjour(bonjourCheckBox.isSelected());
			}
		});
		userTextField.textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				updateUserAndPassword();
                updateRunButton();
			}
		});
		passwordField.textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				updateUserAndPassword();
                updateRunButton();
			}
		});

    	service.setOnRunning(new EventHandler<WorkerStateEvent>() {
			public void handle(WorkerStateEvent event) {
				try {
					statusText.setText("Server started - " + tester.getSiteURL(tester.getHostName("<hostname>"), model.getServerPort().intValue(), model.getMusicPath()));
				} catch (MalformedURLException e) {
					statusText.setText("Server started - Failed to determine site URL: " + e.getMessage());
				}
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

	private BonjourService createBonjour() {
		try {
			return new BonjourService(true);
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Failed to create Bonjour service", e);
			return null;
		}
	}
	
	private void startBonjour() {
		String host = bonjourService.getHostName();
		String name = String.format("Test @ %s", tester.getHostName(host));
		try {
//			bonjourService.start(name, model.getServerPort(), tester.getSitePath(model.getMusicPath()), getUser()); // bug in MM 1.5.2
			bonjourService.start(name, tester.getSiteURL(host, model.getServerPort().intValue(), model.getMusicPath()), getUser());
			LOGGER.info("Bonjour service started.");
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Failed to start Bonjour service", e);
		}
	}

	private void stopBonjour() {
		try {
			bonjourService.stop();
			LOGGER.info("Bonjour service stopped.");
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Failed to stop Bonjour service", e);
		}
	}

	void disableControls(boolean disable) {
		musicFolderTextField.setDisable(disable);
		musicFolderChooseButton.setDisable(disable);
		mountFolderTextField.setDisable(disable);
		mountFolderChooseButton.setDisable(disable);
		musicPathChoiceBox.setDisable(disable);
		musicPathTextField.setDisable(disable);
		portTextField.setDisable(disable);
		userTextField.setDisable(disable);
		passwordField.setDisable(disable);
	}
	
	Pane createView() {
		GridPane grid = new GridPane();
		grid.setAlignment(Pos.CENTER);
		grid.setHgap(5);
		grid.setVgap(10);

//		grid.setGridLinesVisible(true);
		grid.getColumnConstraints().add(0, ColumnConstraintsBuilder.create().hgrow(Priority.NEVER).build());
		grid.getColumnConstraints().add(1, ColumnConstraintsBuilder.create().hgrow(Priority.ALWAYS).build());
		grid.getColumnConstraints().add(2, ColumnConstraintsBuilder.create().hgrow(Priority.NEVER).build());
		grid.getColumnConstraints().add(3, ColumnConstraintsBuilder.create().hgrow(Priority.ALWAYS).build());
		grid.getColumnConstraints().add(4, ColumnConstraintsBuilder.create().hgrow(Priority.NEVER).build());

		/*
		 * music folder
		 */
		Label musicFolderLabel = new Label("Music Folder");
		musicFolderTextField = new TextField();
		musicFolderTextField.setPromptText("Input directory, containing your music collection");
		musicFolderChooseButton = new Button("...");
		grid.add(musicFolderLabel, 0, 1);
		GridPane.setHalignment(musicFolderLabel, HPos.RIGHT);
		grid.add(musicFolderTextField, 1, 1, 3, 1);
		grid.add(musicFolderChooseButton, 4, 1);

		/*
		 * mount folder
		 */
		Label mountFolderLabel = new Label("Site Folder");
		mountFolderTextField = new TextField();
		mountFolderTextField.setPromptText("Output directory, containing your generated site");
		mountFolderChooseButton = new Button("...");
		grid.add(mountFolderLabel, 0, 2);
		GridPane.setHalignment(mountFolderLabel, HPos.RIGHT);
		grid.add(mountFolderTextField, 1, 2, 3, 1);
		grid.add(mountFolderChooseButton, 4, 2);

		/*
		 * music path
		 */
		Label musicPathLabel = new Label("Music Path");
		musicPathTextField = new TextField();
		musicPathTextField.setPromptText("Web path to music, absolute or relative to site URL");
		musicPathChoiceBox = new ChoiceBox<String>(FXCollections.observableArrayList("Auto", "Custom"));
		grid.add(musicPathLabel, 0, 3);
		GridPane.setHalignment(musicPathLabel, HPos.RIGHT);
		HBox.setHgrow(musicPathTextField, Priority.ALWAYS);
        HBox musicPathHBox = new HBox(10);
        musicPathHBox.getChildren().addAll(musicPathChoiceBox, musicPathTextField);
		grid.add(musicPathHBox, 1, 3, 3, 1);

		/*
		 * server port
		 */
		Label portLabel = new Label("Server Port");
		grid.add(portLabel, 0, 4);
		GridPane.setHalignment(portLabel, HPos.RIGHT);
		portTextField = new TextField();
		portTextField.setPromptText("Number");
		grid.add(portTextField, 1, 4);

		/*
		 * bonjour
		 */
		Label bonjourLabel = new Label("Bonjour");
		grid.add(bonjourLabel, 2, 4);
		GridPane.setHalignment(bonjourLabel, HPos.RIGHT);
		bonjourCheckBox = new CheckBox("Publish Site");
		bonjourCheckBox.setDisable(bonjourService == null);
		bonjourCheckBox.setTooltip(new Tooltip("Register as local Bonjour service"));
		grid.add(bonjourCheckBox, 3, 4);

		/*
		 * user
		 */
		Label userLabel = new Label("User");
		grid.add(userLabel, 0, 5);
		GridPane.setHalignment(userLabel, HPos.RIGHT);
		userTextField = new TextField();
		userTextField.setPromptText("Optional");
		grid.add(userTextField, 1, 5);

		/*
		 * password
		 */
		Label passwordLabel = new Label("Password");
		grid.add(passwordLabel, 2, 5);
		GridPane.setHalignment(passwordLabel, HPos.RIGHT);
		passwordField = new PasswordField();
		passwordField.setPromptText("Optional");
		grid.add(passwordField, 3, 5);

		/*
		 * run button
		 */
		runButton = new Button("Start Server");
		runButton.setId("test-button");
		runButton.getStyleClass().add("run-button");
		HBox runButtonHBox = new HBox(10);
		runButtonHBox.setAlignment(Pos.BOTTOM_RIGHT);
		runButtonHBox.getChildren().add(runButton);
		grid.add(runButtonHBox, 2, 6, 3, 1);
		GridPane.setVgrow(runButtonHBox, Priority.ALWAYS);

		BorderPane borderPane = new BorderPane();
		borderPane.setCenter(grid);
		BorderPane.setMargin(grid, new Insets(10));
		
		Text titleText = new Text("Launch MusicMount Test Server");
		titleText.setId("test-title");
		titleText.getStyleClass().add("tool-title");
		borderPane.setTop(titleText);
		BorderPane.setMargin(titleText, new Insets(15, 10, 0, 10));

		statusText = new Text();
		statusText.setId("test-status");
		statusText.getStyleClass().add("status-text");
		borderPane.setBottom(statusText);
		BorderPane.setMargin(statusText, new Insets(5, 10, 10, 10));

		return borderPane;
	}
	
	void updateAll() {
		updateMusicFolder();
		updateMountFolder();
		updateMusicPath();
		updateRunButton();
		updatePort();
		updateBonjour();
		updateUserAndPassword();
	}

	void updateMusicFolder() {
		musicFolderTextField.setText(model.getMusicFolder() != null ? model.getMusicFolder().toString() : null);
	}

	void updateMountFolder() {
		mountFolderTextField.setText(model.getMountFolder() != null ? model.getMountFolder().toString() : null);
	}

	void updateMusicPath() {
        musicPathChoiceBox.getSelectionModel().select(model.getCustomMusicPath() == null ? 0 : 1);
        musicPathTextField.setEditable(model.getCustomMusicPath() != null);
		musicPathTextField.setText(model.getMusicPath());
		if (model.getCustomMusicPath() == null && model.getMusicFolder() != null && model.getMountFolder() != null
				&& !model.getMusicFolder().equals(model.getMountFolder()) && model.getMusicPath() == null) { // no relative path
			statusText.setText(STATUS_NO_RELATIVE_MUSIC_PATH);
		} else if (STATUS_NO_RELATIVE_MUSIC_PATH.equals(statusText.getText())) {
			statusText.setText(null);
		}
	}
	
	void updateRunButton() {
		runButton.setDisable(!model.isValidSiteModel() || !tester.checkMusicPath(model.getMusicPath()) || (getUser() == null) != (getPassword() == null));
	}
	
	void updatePort() {
		portTextField.setText(model.getServerPort() != null ? model.getServerPort().toString() : null);
	}

	void updateBonjour() {
		bonjourCheckBox.setSelected(model.isBonjour());
	}

	void updateUserAndPassword() {
		userTextField.setPromptText(getPassword() == null ? "Optional" : "Required");
		passwordField.setPromptText(getUser() == null ? "Optional" : "Required");
	}
	
	String getUser() {
		return userTextField.getText() == null || userTextField.getText().trim().isEmpty() ? null : userTextField.getText().trim();
	}
	
	String getPassword() {
		return passwordField.getText() == null || passwordField.getText().trim().isEmpty() ? null : passwordField.getText().trim();
	}

	public Pane getPane() {
		return pane;
	}
	
	public Service<Object> getService() {
		return service;
	}
}
