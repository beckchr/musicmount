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
import java.util.logging.Level;
import java.util.prefs.Preferences;

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

import org.musicmount.builder.MusicMountBuilder;
import org.musicmount.util.LoggingUtil;

public class FXBuildController {
	static {
		LoggingUtil.configure(MusicMountBuilder.class.getPackage().getName(), Level.FINE);
	}

	private static final String STATUS_NO_RELATIVE_MUSIC_PATH = "Cannot calculate relative music path, custom path path required";

	private static final Preferences PREFERENCES = Preferences.userNodeForPackage(FXBuildController.class);
	private static final String PREFERENCE_KEY_GROUPING = "builder.grouping";
	private static final String PREFERENCE_KEY_NO_TRACK_INDEX = "builder.noTrackIndex";
	private static final String PREFERENCE_KEY_NO_VARIOUS_ARTISTS = "builder.noVariousArtists";
	private static final String PREFERENCE_KEY_RETINA = "builder.retina";
	private static final String PREFERENCE_KEY_UNKNOWN_GENRE = "builder.unknownGenre";

	private Pane pane;
	private TextField musicFolderTextField;
	private Button musicFolderChooseButton;
	private TextField mountFolderTextField;
	private Button mountFolderChooseButton;
	private TextField musicPathTextField;
	private ChoiceBox<String> musicPathChoiceBox;
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
	private final MusicMountBuilder builder = new MusicMountBuilder();
	private final Service<Object> service = new Service<Object>() {
		@Override
		protected Task<Object> createTask() {
			return new Task<Object>() {
				@Override
				protected Object call() throws Exception {
					builder.build(model.getMusicFolder(), model.getMountFolder(), model.getMusicPath());
					return null;
				}
			};
		}
	};

	public FXBuildController(final FXCommandModel model) {
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

    	service.setOnRunning(new EventHandler<WorkerStateEvent>() {
			public void handle(WorkerStateEvent event) {
				statusText.setText(null);
            	disableControls(true);
			}
		});
		service.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
			public void handle(WorkerStateEvent event) {
				statusText.setText("Site generation succeeded");
            	disableControls(false);
            	savePreferences();
			}
		});
		service.setOnFailed(new EventHandler<WorkerStateEvent>() {
			public void handle(WorkerStateEvent event) {
				statusText.setText("Site generation failed");
				if (service.getException() != null) {
					service.getException().printStackTrace();
				}
				disableControls(false);
			}
		});
		runButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				service.reset();
				service.start();
			}
		});
		
		updateAll();
	}

	private void loadPreferences() {
		builder.setGrouping(PREFERENCES.getBoolean(PREFERENCE_KEY_GROUPING, false));
		builder.setNoTrackIndex(PREFERENCES.getBoolean(PREFERENCE_KEY_NO_TRACK_INDEX, false));
		builder.setNoVariousArtists(PREFERENCES.getBoolean(PREFERENCE_KEY_NO_VARIOUS_ARTISTS, false));
		builder.setRetina(PREFERENCES.getBoolean(PREFERENCE_KEY_RETINA, false));
		builder.setUnknownGenre(PREFERENCES.getBoolean(PREFERENCE_KEY_UNKNOWN_GENRE, false));
	}

	private void savePreferences() {
		PREFERENCES.putBoolean(PREFERENCE_KEY_GROUPING, builder.isGrouping());
		PREFERENCES.putBoolean(PREFERENCE_KEY_NO_TRACK_INDEX, builder.isNoTrackIndex());
		PREFERENCES.putBoolean(PREFERENCE_KEY_NO_VARIOUS_ARTISTS, builder.isNoVariousArtists());
		PREFERENCES.putBoolean(PREFERENCE_KEY_RETINA, builder.isRetina());
		PREFERENCES.putBoolean(PREFERENCE_KEY_UNKNOWN_GENRE, builder.isUnknownGenre());				
	}
	
	void disableControls(boolean disable) {
		musicFolderTextField.setDisable(disable);
		musicFolderChooseButton.setDisable(disable);
		mountFolderTextField.setDisable(disable);
		mountFolderChooseButton.setDisable(disable);
		musicFolderTextField.setDisable(disable);
		musicPathChoiceBox.setDisable(disable);
		musicPathTextField.setDisable(disable);
		retinaCheckBox.setDisable(disable);
		groupingCheckBox.setDisable(disable);
		fullCheckBox.setDisable(disable);
		noTrackIndexCheckBox.setDisable(disable);
		noVariousArtistsCheckBox.setDisable(disable);
		unknownGenreCheckBox.setDisable(disable);
		runButton.setDisable(disable || !model.isValid());
	}
	
	Pane createView() {
		GridPane grid = new GridPane();
		grid.setAlignment(Pos.CENTER);
		grid.setHgap(5);
		grid.setVgap(10);

//		grid.setGridLinesVisible(true);
		grid.getColumnConstraints().add(0, ColumnConstraintsBuilder.create().hgrow(Priority.NEVER).build());
		grid.getColumnConstraints().add(1, ColumnConstraintsBuilder.create().hgrow(Priority.ALWAYS).build());
		grid.getColumnConstraints().add(2, ColumnConstraintsBuilder.create().hgrow(Priority.ALWAYS).build());
		grid.getColumnConstraints().add(3, ColumnConstraintsBuilder.create().hgrow(Priority.NEVER).build());

		/*
		 * music folder
		 */
		Label musicFolderLabel = new Label("Music Folder");
		musicFolderTextField = new TextField();
		musicFolderTextField.setPromptText("Input directory, containing your music collection");
		musicFolderChooseButton = new Button("...");
		grid.add(musicFolderLabel, 0, 1);
		GridPane.setHalignment(musicFolderLabel, HPos.RIGHT);
		grid.add(musicFolderTextField, 1, 1, 2, 1);
		grid.add(musicFolderChooseButton, 3, 1);

		/*
		 * mount folder
		 */
		Label mountFolderLabel = new Label("Mount Folder");
		mountFolderTextField = new TextField();
		mountFolderTextField.setPromptText("Output directory, containing your generated site");
		mountFolderChooseButton = new Button("...");
		grid.add(mountFolderLabel, 0, 2);
		GridPane.setHalignment(mountFolderLabel, HPos.RIGHT);
		grid.add(mountFolderTextField, 1, 2, 2, 1);
		grid.add(mountFolderChooseButton, 3, 2);

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
		grid.add(musicPathHBox, 1, 3, 2, 1);

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
		fullCheckBox = new CheckBox("Full Parse/Build");
		fullCheckBox.setTooltip(new Tooltip("Force full parse/build, don't use asset store"));
		grid.add(fullCheckBox, 1, 6);
		noTrackIndexCheckBox = new CheckBox("No Track Index");
		noTrackIndexCheckBox.setTooltip(new Tooltip("Do not generate a track index"));
		grid.add(noTrackIndexCheckBox, 2, 4);
		noVariousArtistsCheckBox = new CheckBox("No 'Various Artists' Item");
		noVariousArtistsCheckBox.setTooltip(new Tooltip("Exclude 'Various Artists' from album artist index"));
		grid.add(noVariousArtistsCheckBox, 2, 5);
		unknownGenreCheckBox = new CheckBox("Add 'Unknown' Genre");
		unknownGenreCheckBox.setTooltip(new Tooltip("Report missing genre as 'Unknown'"));
		grid.add(unknownGenreCheckBox, 2, 6);

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
		runButton = new Button("Build Site");
		runButton.setId("build-button");
		runButton.getStyleClass().add("run-button");
		HBox runButtonHBox = new HBox(10);
		runButtonHBox.setAlignment(Pos.BOTTOM_RIGHT);
		runButtonHBox.getChildren().add(runButton);
		grid.add(runButtonHBox, 2, 7, 2, 1);
		GridPane.setVgrow(runButtonHBox, Priority.ALWAYS);

		BorderPane borderPane = new BorderPane();
		borderPane.setCenter(grid);
		BorderPane.setMargin(grid, new Insets(10));

		Text titleText = new Text("Generate MusicMount Site");
		titleText.setId("build-title");
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
		updateMountFolder();
		updateMusicPath();
		updateOptions();
		updateRunButton();
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

	void updateOptions() {
		fullCheckBox.setSelected(builder.isFull());
		groupingCheckBox.setSelected(builder.isGrouping());
		noTrackIndexCheckBox.setSelected(builder.isNoTrackIndex());
		noVariousArtistsCheckBox.setSelected(builder.isNoVariousArtists());
		retinaCheckBox.setSelected(builder.isRetina());
		unknownGenreCheckBox.setSelected(builder.isUnknownGenre());
	}

	void updateRunButton() {
		runButton.setDisable(service.isRunning() || !model.isValid());
	}

	public Pane getPane() {
		return pane;
	}
	
	public Service<Object> getService() {
		return service;
	}
}
