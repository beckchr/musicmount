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

import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import org.musicmount.util.LoggingUtil;
import org.musicmount.util.VersionUtil;

public class FXMusicMount extends Application {
	static final Logger LOGGER = Logger.getLogger(FXMusicMount.class.getName());

	static {
		LoggingUtil.configure("org.musicmount", Level.FINE);
	}
	
	@Override
	public void start(final Stage primaryStage) {
		//		Platform.setImplicitExit(false);
		
		final FXConsole console = new FXConsole();
		console.getTextArea().setId("console");
		console.getTextArea().setEditable(false);
		console.getTextArea().setPrefHeight(200);

		TitledPane consolePane = new TitledPane("Console", console.getTextArea());
		consolePane.setExpanded(false);
		consolePane.setAnimated(false);
		consolePane.heightProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				primaryStage.setHeight(primaryStage.getHeight() + newValue.doubleValue() - oldValue.doubleValue());
			}
		});

		FXCommandModel model = new FXCommandModel();

		final FXLiveController liveController = new FXLiveController(model);
		Pane livePane = liveController.getPane();
		livePane.setId("live-pane");
		
		final FXBuildController buildController = new FXBuildController(model);
		Pane buildPane = buildController.getPane();
		buildPane.setId("build-pane");

		final FXTestController testController = new FXTestController(model);
		Pane testPane = testController.getPane();
		testPane.setId("test-pane");

		primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("favicon.png")));
		primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
			@Override
			public void handle(WindowEvent event) {
				if (liveController.getService().isRunning()) {
					liveController.getService().cancel();
				}
				if (buildController.getService().isRunning()) {
					buildController.getService().cancel();
				}
				if (testController.getService().isRunning()) {
					testController.getService().cancel();
				}
				console.stop();
			}
		});

		final TabPane tabPane = new TabPane();
		tabPane.setId("tab-pane");
		tabPane.setSide(Side.TOP);
		tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		final Tab liveTab = new Tab();
		liveTab.setText("Live");
		liveTab.setContent(livePane);
		final Tab buildTab = new Tab();
		buildTab.setText("Build");
		buildTab.setContent(buildPane);
		final Tab testTab = new Tab();
		testTab.setText("Test");
		testTab.setContent(testPane);
		tabPane.getTabs().addAll(liveTab, buildTab, testTab);
		tabPane.getSelectionModel().select(liveTab);

		ChangeListener<Boolean> serviceRunningListener = new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				for (Tab tab : tabPane.getTabs()) {
					tab.setDisable(newValue && tab != tabPane.getSelectionModel().getSelectedItem());
				}
			}
		};		
		liveController.getService().runningProperty().addListener(serviceRunningListener);
		buildController.getService().runningProperty().addListener(serviceRunningListener);
		testController.getService().runningProperty().addListener(serviceRunningListener);

		final BorderPane borderPane = new BorderPane();
		borderPane.setId("border-pane");
		borderPane.setCenter(tabPane);
		borderPane.setBottom(consolePane);

		Scene scene = new Scene(borderPane, 600, borderPane.getPrefHeight());
		scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
		primaryStage.setTitle("MusicMount");
		primaryStage.setScene(scene);
		primaryStage.show();
//		primaryStage.setResizable(false);

		console.start();
		
		LOGGER.info("version " + VersionUtil.getImplementationVersion() + " (java version " + System.getProperty("java.version") + ")");
	}

	/**
	 * @param args
	 *            the command line arguments
	 */
	public static void main(String[] args) {
		launch(args);
	}
}
