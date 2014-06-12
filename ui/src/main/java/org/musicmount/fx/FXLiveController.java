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
import java.util.prefs.Preferences;

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

import org.musicmount.live.LiveMount;
import org.musicmount.live.LiveMountBuilder;
import org.musicmount.live.MusicMountLive;

public class FXLiveController {
	private static final Preferences PREFERENCES = Preferences.userNodeForPackage(FXLiveController.class);
	private static final String PREFERENCE_KEY_PORT = "live.port";
	private static final String PREFERENCE_KEY_USER = "live.user";
	private static final String PREFERENCE_KEY_GROUPING = "live.grouping";
	private static final String PREFERENCE_KEY_NO_TRACK_INDEX = "live.noTrackIndex";
	private static final String PREFERENCE_KEY_NO_VARIOUS_ARTISTS = "live.noVariousArtists";
	private static final String PREFERENCE_KEY_RETINA = "live.retina";
	private static final String PREFERENCE_KEY_UNKNOWN_GENRE = "live.unknownGenre";

	private Pane pane;
	private TextField musicFolderTextField;
	private Button musicFolderChooseButton;
	private TextField portTextField;
	private TextField userTextField;
	private PasswordField passwordField;
	private CheckBox retinaCheckBox;
	private CheckBox groupingCheckBox;
	private CheckBox fullCheckBox;
	private CheckBox noTrackIndexCheckBox;
	private CheckBox noVariousArtistsCheckBox;
	private CheckBox unknownGenreCheckBox;
	private ProgressIndicator progressIndicator;
	private Button runButton;
	private Text statusText;
	
	LiveMount liveMount;
	
	private final FXCommandModel model;
	private Integer port;
	private final LiveMountBuilder builder = new LiveMountBuilder();
	private final MusicMountLive live = new MusicMountLive();
	private final Service<LiveMount> buildService = new Service<LiveMount>() {
		@Override
		protected Task<LiveMount> createTask() {
			return new Task<LiveMount>() {
				@Override
				protected LiveMount call() throws Exception {
					return builder.update(model.getMusicFolder(), live.getMusicPath());
				}
			};
		}
	};
	private final Service<Object> service = new Service<Object>() {
		@Override
		protected Task<Object> createTask() {
			return new Task<Object>() {
				@Override
				protected Object call() throws Exception {
					live.start(model.getMusicFolder(), liveMount, port.intValue(), getUser(), getPassword());
					live.await();
					return null;
				}
				@Override
				public boolean cancel(boolean mayInterruptIfRunning) {
					try {
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
		this.pane = createView(); 
		
		loadPreferences();
		
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
				File directory = directoryChooser.showDialog(null);
				if (directory != null) {
					musicFolderTextField.setText(directory.getAbsolutePath());
				}
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
				builder.setRetina(retinaCheckBox.isSelected());
			}
		});
		groupingCheckBox.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				builder.setGrouping(groupingCheckBox.isSelected());
			}
		});
		fullCheckBox.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				builder.setFull(fullCheckBox.isSelected());
			}
		});
		noTrackIndexCheckBox.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				builder.setNoTrackIndex(noTrackIndexCheckBox.isSelected());
			}
		});
		noVariousArtistsCheckBox.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				builder.setNoVariousArtists(noVariousArtistsCheckBox.isSelected());
			}
		});
		unknownGenreCheckBox.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				builder.setUnknownGenre(unknownGenreCheckBox.isSelected());
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
				liveMount = buildService.getValue();
				statusText.setText("Mount analysis done");
            	savePreferences();
				service.reset();
				service.start();
			}
		});
    	buildService.setOnFailed(new EventHandler<WorkerStateEvent>() {
			public void handle(WorkerStateEvent event) {
				liveMount = null;
				statusText.setText("Mount analysis failed");
				if (buildService.getException() != null) {
					buildService.getException().printStackTrace();
				}
				runButton.setText("Start Server");
				disableControls(false);
			}
		});

    	service.setOnRunning(new EventHandler<WorkerStateEvent>() {
			public void handle(WorkerStateEvent event) {
				try {
					statusText.setText("Server started - " + live.getSiteURL(live.getHostName("<hostname>") ,port));
				} catch (MalformedURLException e) {
					statusText.setText("Server started - Failed to determine site URL: " + e.getMessage());
				}
				runButton.setDisable(false);
				runButton.setText("Stop Server");
            	disableControls(true);
			}
		});
		service.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
			public void handle(WorkerStateEvent event) {
				statusText.setText("Server stopped");
				runButton.setText("Start Server");
				disableControls(false);
				savePreferences();
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
				runButton.setDisable(false);
				disableControls(false);
			}
		});
		runButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				if (service.isRunning()) {
					service.cancel();
				} else {
					buildService.reset();
					buildService.start();
				}
			}
		});
		
		updateAll();
	}

	private void loadPreferences() {
		port = Integer.valueOf(PREFERENCES.getInt(PREFERENCE_KEY_PORT, 8080));
		if (port == 0) {
			port = null;
		}
		userTextField.setText(PREFERENCES.get(PREFERENCE_KEY_USER, null));
		builder.setGrouping(PREFERENCES.getBoolean(PREFERENCE_KEY_GROUPING, false));
		builder.setNoTrackIndex(PREFERENCES.getBoolean(PREFERENCE_KEY_NO_TRACK_INDEX, false));
		builder.setNoVariousArtists(PREFERENCES.getBoolean(PREFERENCE_KEY_NO_VARIOUS_ARTISTS, false));
		builder.setRetina(PREFERENCES.getBoolean(PREFERENCE_KEY_RETINA, false));
		builder.setUnknownGenre(PREFERENCES.getBoolean(PREFERENCE_KEY_UNKNOWN_GENRE, false));
	}

	private void savePreferences() {
		if (port != null) {
			PREFERENCES.putInt(PREFERENCE_KEY_PORT, port.intValue());
		} else {
			PREFERENCES.remove(PREFERENCE_KEY_PORT);
		}
		if (getUser() != null) {
			PREFERENCES.put(PREFERENCE_KEY_USER, getUser());
		} else {
			PREFERENCES.remove(PREFERENCE_KEY_USER);
		}
		PREFERENCES.putBoolean(PREFERENCE_KEY_GROUPING, builder.isGrouping());
		PREFERENCES.putBoolean(PREFERENCE_KEY_NO_TRACK_INDEX, builder.isNoTrackIndex());
		PREFERENCES.putBoolean(PREFERENCE_KEY_NO_VARIOUS_ARTISTS, builder.isNoVariousArtists());
		PREFERENCES.putBoolean(PREFERENCE_KEY_RETINA, builder.isRetina());
		PREFERENCES.putBoolean(PREFERENCE_KEY_UNKNOWN_GENRE, builder.isUnknownGenre());				
	}
	
	void disableControls(boolean disable) {
		musicFolderTextField.setDisable(disable);
		musicFolderChooseButton.setDisable(disable);
		portTextField.setDisable(disable);
		userTextField.setDisable(disable);
		passwordField.setDisable(disable);
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

		grid.setGridLinesVisible(true);
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
		 * server port
		 */
		Label portLabel = new Label("Server Port");
		grid.add(portLabel, 0, 2);
		GridPane.setHalignment(portLabel, HPos.RIGHT);
		portTextField = new TextField();
		portTextField.setPromptText("Number");
		grid.add(portTextField, 1, 2);

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
		grid.add(runButtonHBox, 2, 7, 3, 1);
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
		updateUserAndPassword();
		updateOptions();
		updateRunButton();
	}

	void updateMusicFolder() {
		musicFolderTextField.setText(model.getMusicFolder() != null ? model.getMusicFolder().toString() : null);
	}
	
	void updatePort() {
		portTextField.setText(port != null ? port.toString() : null);
	}

	void updateUserAndPassword() {
		userTextField.setPromptText(getPassword() == null ? "Optional" : "Required");
		passwordField.setPromptText(getUser() == null ? "Optional" : "Required");
	}
	
	void updateOptions() {
		fullCheckBox.setSelected(builder.isFull());
		groupingCheckBox.setSelected(builder.isGrouping());
		noTrackIndexCheckBox.setSelected(builder.isNoTrackIndex());
		noVariousArtistsCheckBox.setSelected(builder.isNoVariousArtists());
		retinaCheckBox.setSelected(builder.isRetina());
		unknownGenreCheckBox.setSelected(builder.isUnknownGenre());
	}

	void updateRunButton() {
		runButton.setDisable(service.isRunning() || !model.isValidLiveModel());
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
