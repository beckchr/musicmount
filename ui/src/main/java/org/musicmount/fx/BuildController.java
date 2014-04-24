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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
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

public class BuildController {
	static {
		LoggingUtil.configure(MusicMountBuilder.class.getPackage().getName(), Level.FINE);
	}
	
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
	private ProgressIndicator progressIndicator;
	private Button runButton;
	private Text statusText;
	
	private final MusicMountBuilder builder;
	private final FXCommandModel model;
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

	public BuildController(final FXCommandModel model) {
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

		builder = new MusicMountBuilder();
		builder.setProgressHandler(new FXProgressHandler(statusText, progressIndicator));
		
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

    	service.setOnRunning(new EventHandler<WorkerStateEvent>() {
			public void handle(WorkerStateEvent event) {
				statusText.setText(null);
            	progressIndicator.setVisible(true);
            	disableControls(true);
			}
		});
		service.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
			public void handle(WorkerStateEvent event) {
				statusText.setText("Site generation succeeded");
            	progressIndicator.setVisible(false);
            	disableControls(false);
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
		runButton.setDisable(disable || !model.isValid());
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

		Text titleText = new Text("Generate MusicMount Site");
		titleText.setId("build-title");
		titleText.getStyleClass().add("tool-title");
		grid.add(titleText, 0, 0, 2, 1);

		/*
		 * music folder
		 */
		Label musicFolderLabel = new Label("Music Folder");
		musicFolderTextField = new TextField();
		musicFolderTextField.setPromptText("Input directory, containing your music collection");
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
		mountFolderTextField.setPromptText("Output directory, containing your generated site");
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
		musicPathTextField.setPromptText("Web path to music, absolute or relative to site URL");
		musicPathChoiceBox = new ChoiceBox<String>(FXCollections.observableArrayList("Auto", "Custom"));
		grid.add(musicPathLabel, 0, 3);
		GridPane.setHalignment(musicPathLabel, HPos.RIGHT);
		HBox.setHgrow(musicPathTextField, Priority.ALWAYS);
        HBox musicPathHBox = new HBox(10);
        musicPathHBox.getChildren().addAll(musicPathChoiceBox, musicPathTextField);
		grid.add(musicPathHBox, 1, 3);

		/*
		 * options
		 */
		Label optionsLabel = new Label("Options");
		grid.add(optionsLabel, 0, 4);
		GridPane.setHalignment(optionsLabel, HPos.RIGHT);
		retinaCheckBox = new CheckBox("Retina Images");
		retinaCheckBox.setTooltip(new Tooltip("Hello Dolly!\nThis is a pretty long tooltip text..."));
		grid.add(retinaCheckBox, 1, 4);
		groupingCheckBox = new CheckBox("Track Grouping");
		grid.add(groupingCheckBox, 1, 5);
		fullCheckBox = new CheckBox("Force Full Build");
		grid.add(fullCheckBox, 1, 6);

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
		grid.add(progressBox, 0, 4, 1, 4);

		/*
		 * run button
		 */
		runButton = new Button("Build Site");
		runButton.setId("build-button");
		runButton.getStyleClass().add("run-button");
		HBox runButtonHBox = new HBox(10);
		runButtonHBox.setAlignment(Pos.BOTTOM_RIGHT);
		runButtonHBox.getChildren().add(runButton);
		grid.add(runButtonHBox, 1, 7, 2, 1);
		GridPane.setVgrow(runButtonHBox, Priority.ALWAYS);

		/*
		 * status
		 */
		statusText = new Text();
		statusText.setId("build-status");
		statusText.getStyleClass().add("status-text");
		grid.add(statusText, 0, 8, 3, 1);
		
		return grid;
	}

	void updateAll() {
		updateMusicFolder();
		updateMountFolder();
		updateMusicPath();
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
