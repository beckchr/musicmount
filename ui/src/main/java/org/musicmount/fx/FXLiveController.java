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
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraintsBuilder;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;

import org.musicmount.live.LiveMountBuilder;
import org.musicmount.live.MusicMountLive;
import org.musicmount.util.BonjourService;

public class FXLiveController {
	static final Logger LOGGER = Logger.getLogger(FXLiveController.class.getName());

	private Pane pane;
	private TextField musicFolderTextField;
	private Button musicFolderChooseButton;
	private TextField portTextField;
	private TextField userTextField;
	private PasswordField passwordField;
	private CheckBox bonjourCheckBox;
	private CheckBox retinaCheckBox;
	private CheckBox groupingCheckBox;
	private CheckBox fullCheckBox;
	private CheckBox noTrackIndexCheckBox;
	private CheckBox noVariousArtistsCheckBox;
	private CheckBox unknownGenreCheckBox;
	private ProgressIndicator progressIndicator;
	private Button runButton;
	private Text statusText;
	
	private final FXCommandModel model;
	private final BonjourService bonjourService;
	private final LiveMountBuilder builder;
	private final MusicMountLive live = new MusicMountLive();
	private final Service<Object> buildService = new Service<Object>() {
		@Override
		protected Task<Object> createTask() {
			return new Task<Object>() {
				@Override
				protected Object call() throws Exception {
					live.start(model.getMusicFolder(), builder, model.getServerPort().intValue(), getUser(), getPassword());
					return null;
				}
			};
		}
	};
	private final Service<Object> liveService = new Service<Object>() {
		@Override
		protected Task<Object> createTask() {
			return new Task<Object>() {
				@Override
				protected Object call() throws Exception {
					if (bonjourService != null && model.isBonjour()) {
						startBonjour();
					}
					live.await();
					return null;
				}
				@Override
				public boolean cancel(boolean mayInterruptIfRunning) {
					try {
						if (bonjourService != null && model.isBonjour()) {
							stopBonjour();
						}
						live.stop();
					} catch (Exception e) {
						return false;
					}
					return true;
				}
			};
		}
	};

	public FXLiveController(final FXCommandModel model) {
		this.model = model;
		this.bonjourService = createBonjour();
		this.builder = new LiveMountBuilder(model.getBuildConfig());
		this.pane = createView(); 
		
		pane.visibleProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				if (newValue) {
					updateAll();
				}
			}
		});

		builder.setProgressHandler(new FXProgressHandler(statusText, progressIndicator, builder.getProgressHandler()));
		
		musicFolderTextField.textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				model.setMusicFolder(model.toFolder(newValue));
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

		retinaCheckBox.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				builder.getConfig().setRetina(retinaCheckBox.isSelected());
			}
		});
		groupingCheckBox.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				builder.getConfig().setGrouping(groupingCheckBox.isSelected());
			}
		});
		fullCheckBox.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				builder.getConfig().setFull(fullCheckBox.isSelected());
			}
		});
		noTrackIndexCheckBox.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				builder.getConfig().setNoTrackIndex(noTrackIndexCheckBox.isSelected());
			}
		});
		noVariousArtistsCheckBox.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				builder.getConfig().setNoVariousArtists(noVariousArtistsCheckBox.isSelected());
			}
		});
		unknownGenreCheckBox.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				builder.getConfig().setUnknownGenre(unknownGenreCheckBox.isSelected());
			}
		});

    	buildService.setOnRunning(new EventHandler<WorkerStateEvent>() {
			public void handle(WorkerStateEvent event) {
				statusText.setText(null);
				runButton.setDisable(true);
				runButton.setText("Analyzing...");
            	disableControls(true);
			}
		});
    	buildService.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
			public void handle(WorkerStateEvent event) {
				statusText.setText("Mount analysis done");
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						liveService.reset();
						liveService.start();
					}
				});
			}
		});
    	buildService.setOnFailed(new EventHandler<WorkerStateEvent>() {
			public void handle(WorkerStateEvent event) {
				builder.getProgressHandler().endTask();
				statusText.setText("Mount analysis failed");
				if (buildService.getException() != null) {
					buildService.getException().printStackTrace();
				}
				runButton.setText("Start Server");
				runButton.setDisable(false);
				disableControls(false);
			}
		});

    	liveService.setOnRunning(new EventHandler<WorkerStateEvent>() {
			public void handle(WorkerStateEvent event) {
				try {
					statusText.setText("Server started - " + live.getSiteURL(live.getHostName("<hostname>"), model.getServerPort().intValue()));
				} catch (MalformedURLException e) {
					statusText.setText("Server started - Failed to determine site URL: " + e.getMessage());
				}
				runButton.setDisable(false);
				runButton.setText("Stop Server");
            	disableControls(true);
			}
		});
		liveService.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
			public void handle(WorkerStateEvent event) {
				statusText.setText("Server stopped");
				runButton.setDisable(false);
				runButton.setText("Start Server");
				disableControls(false);
			}
		});
		liveService.setOnFailed(new EventHandler<WorkerStateEvent>() {
			public void handle(WorkerStateEvent event) {
				if (liveService.getException() != null) {
					liveService.getException().printStackTrace();
					statusText.setText("Server failed: " + liveService.getException().getMessage());
				} else {
					statusText.setText("Server failed");
				}
				runButton.setText("Start Server");
				runButton.setDisable(false);
				disableControls(false);
			}
		});
		runButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				if (liveService.isRunning()) {
					liveService.cancel();
				} else if (!buildService.isRunning()) {
					buildService.reset();
					buildService.start();
				}
			}
		});
		
		updateAll();
	}
	
	private BonjourService createBonjour() {
		try {
			return new BonjourService(true);
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Failed to create Bonjour service", e);
			return null;
		}
	}
	
	private void startBonjour() {
		LOGGER.info("Starting Bonjour service...");
		String host = live.getHostName(bonjourService.getHostName());
		try {
			bonjourService.start(String.format("Live @ %s", host), live.getSiteURL(host, model.getServerPort().intValue()), getUser());
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Failed to start Bonjour service", e);
		}
	}

	private void stopBonjour() {
		LOGGER.info("Stopping Bonjour service...");
		try {
			bonjourService.stop();
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Failed to stop Bonjour service", e);
		}
	}
	
	void disableControls(boolean disable) {
		musicFolderTextField.setDisable(disable);
		musicFolderChooseButton.setDisable(disable);
		portTextField.setDisable(disable);
		userTextField.setDisable(disable);
		passwordField.setDisable(disable);
		bonjourCheckBox.setDisable(disable || bonjourService == null);
		retinaCheckBox.setDisable(disable);
		groupingCheckBox.setDisable(disable);
		fullCheckBox.setDisable(disable);
		noTrackIndexCheckBox.setDisable(disable);
		noVariousArtistsCheckBox.setDisable(disable);
		unknownGenreCheckBox.setDisable(disable);
	}
	
	Pane createView() {
		GridPane grid = new GridPane();
		grid.setAlignment(Pos.CENTER);
		grid.setHgap(5);
		grid.setVgap(10);

//		grid.setGridLinesVisible(true);
		grid.getColumnConstraints().add(0, ColumnConstraintsBuilder.create().hgrow(Priority.NEVER).build());
		grid.getColumnConstraints().add(1, ColumnConstraintsBuilder.create().hgrow(Priority.ALWAYS).percentWidth(33).build());
		grid.getColumnConstraints().add(2, ColumnConstraintsBuilder.create().hgrow(Priority.ALWAYS).build());
		grid.getColumnConstraints().add(3, ColumnConstraintsBuilder.create().hgrow(Priority.ALWAYS).percentWidth(33).build());
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
		 * server port
		 */
		Label portLabel = new Label("Server Port");
		grid.add(portLabel, 0, 2);
		GridPane.setHalignment(portLabel, HPos.RIGHT);
		portTextField = new TextField();
		portTextField.setPromptText("Number");
		grid.add(portTextField, 1, 2);

		/*
		 * bonjour
		 */
		Label bonjourLabel = new Label("Bonjour");
		grid.add(bonjourLabel, 2, 2);
		GridPane.setHalignment(bonjourLabel, HPos.RIGHT);
		bonjourCheckBox = new CheckBox("Publish Site");
		bonjourCheckBox.setDisable(bonjourService == null);
		bonjourCheckBox.setTooltip(new Tooltip("Register as local Bonjour service"));
		grid.add(bonjourCheckBox, 3, 2);

		/*
		 * user
		 */
		Label userLabel = new Label("User");
		grid.add(userLabel, 0, 3);
		GridPane.setHalignment(userLabel, HPos.RIGHT);
		userTextField = new TextField();
		userTextField.setPromptText("Optional");
		grid.add(userTextField, 1, 3);

		/*
		 * password
		 */
		Label passwordLabel = new Label("Password");
		grid.add(passwordLabel, 2, 3);
		GridPane.setHalignment(passwordLabel, HPos.RIGHT);
		passwordField = new PasswordField();
		passwordField.setPromptText("Optional");
		grid.add(passwordField, 3, 3);

		/*
		 * options
		 */
		Label optionsLabel = new Label("Options");
		grid.add(optionsLabel, 0, 4);
		GridPane.setHalignment(optionsLabel, HPos.RIGHT);
		retinaCheckBox = new CheckBox("Retina Images");
		retinaCheckBox.setTooltip(new Tooltip("Double image resolution, better for tables"));
		grid.add(retinaCheckBox, 1, 4);
		groupingCheckBox = new CheckBox("Track Grouping");
		groupingCheckBox.setTooltip(new Tooltip("Use grouping tag to group album tracks"));
		grid.add(groupingCheckBox, 1, 5);
		fullCheckBox = new CheckBox("Full Parse");
		fullCheckBox.setTooltip(new Tooltip("Force full parse, don't use asset store"));
		grid.add(fullCheckBox, 1, 6);
		noTrackIndexCheckBox = new CheckBox("No Track Index");
		noTrackIndexCheckBox.setTooltip(new Tooltip("Do not generate a track index"));
		grid.add(noTrackIndexCheckBox, 3, 4);
		noVariousArtistsCheckBox = new CheckBox("No 'Various Artists' Item");
		noVariousArtistsCheckBox.setTooltip(new Tooltip("Exclude 'Various Artists' from album artist index"));
		grid.add(noVariousArtistsCheckBox, 3, 5);
		unknownGenreCheckBox = new CheckBox("Add 'Unknown' Genre");
		unknownGenreCheckBox.setTooltip(new Tooltip("Report missing genre as 'Unknown'"));
		grid.add(unknownGenreCheckBox, 3, 6);

		/*
		 * progress
		 */
		progressIndicator = new ProgressIndicator();
		progressIndicator.setPrefWidth(30);
		progressIndicator.setVisible(false);
		VBox progressBox = new VBox();
		progressBox.setFillWidth(false);
		progressBox.setAlignment(Pos.BOTTOM_CENTER);
		progressBox.getChildren().add(progressIndicator);
		grid.add(progressBox, 0, 5, 1, 3);

		/*
		 * run button
		 */
		runButton = new Button("Start Server");
		runButton.setId("live-button");
		runButton.getStyleClass().add("run-button");
		HBox runButtonHBox = new HBox(10);
		runButtonHBox.setAlignment(Pos.BOTTOM_RIGHT);
		runButtonHBox.getChildren().add(runButton);
		grid.add(runButtonHBox, 3, 7, 2, 1);
		GridPane.setVgrow(runButtonHBox, Priority.ALWAYS);

		BorderPane borderPane = new BorderPane();
		borderPane.setCenter(grid);
		BorderPane.setMargin(grid, new Insets(10));

		Text titleText = new Text("Run MusicMount Live Server");
		titleText.setId("live-title");
		titleText.getStyleClass().add("tool-title");
		borderPane.setTop(titleText);
		BorderPane.setMargin(titleText, new Insets(15, 10, 0, 10));

		statusText = new Text();
		statusText.setId("build-status");
		statusText.getStyleClass().add("status-text");
		borderPane.setBottom(statusText);
		BorderPane.setMargin(statusText, new Insets(5, 10, 10, 10));

		return borderPane;
	}

	void updateAll() {
		updateMusicFolder();
		updatePort();
		updateBonjour();
		updateUserAndPassword();
		updateOptions();
		updateRunButton();
	}

	void updateMusicFolder() {
		musicFolderTextField.setText(model.getMusicFolder() != null ? model.getMusicFolder().toString() : null);
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
	
	void updateOptions() {
		fullCheckBox.setSelected(builder.getConfig().isFull());
		groupingCheckBox.setSelected(builder.getConfig().isGrouping());
		noTrackIndexCheckBox.setSelected(builder.getConfig().isNoTrackIndex());
		noVariousArtistsCheckBox.setSelected(builder.getConfig().isNoVariousArtists());
		retinaCheckBox.setSelected(builder.getConfig().isRetina());
		unknownGenreCheckBox.setSelected(builder.getConfig().isUnknownGenre());
	}

	void updateRunButton() {
		runButton.setDisable(buildService.isRunning() || liveService.isRunning() || !model.isValidLiveModel());
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
	
	public Service<?> getLiveService() {
		return liveService;
	}

	public Service<?> getBuildService() {
		return buildService;
	}
}
