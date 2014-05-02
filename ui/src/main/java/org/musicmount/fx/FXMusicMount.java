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
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import org.musicmount.MusicMount;
import org.musicmount.util.LoggingUtil;

public class FXMusicMount extends Application {
	static final Logger LOGGER = Logger.getLogger(FXMusicMount.class.getName());

	@Override
	public void start(final Stage primaryStage) {
		//		Platform.setImplicitExit(false);
		primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("favicon.png")));
		
		FXConsole console = new FXConsole();
		console.getTextArea().setId("console");
		console.getTextArea().setEditable(false);
		console.getTextArea().setPrefHeight(200);
		console.start();
		
		LoggingUtil.configure(FXMusicMount.class.getPackage().getName(), Level.FINE);
		String version = MusicMount.class.getPackage().getImplementationVersion();
		LOGGER.info("version " + (version != null ? version : "<unknown>") + " (java version " + System.getProperty("java.version") + ")");

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
		FXBuildController buildController = new FXBuildController(model);
		Pane buildPane = buildController.getPane();
		buildPane.setId("build-pane");

		FXTestController testController = new FXTestController(model);
		Pane testPane = testController.getPane();
		testPane.setId("test-pane");

		final TabPane tabPane = new TabPane();
		tabPane.setId("tab-pane");
		tabPane.setSide(Side.TOP);
		tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		final Tab buildTab = new Tab();
		buildTab.setText("Build");
		buildTab.setContent(buildPane);
		final Tab testTab = new Tab();
		testTab.setText("Test");
		testTab.setContent(testPane);
		tabPane.getTabs().addAll(buildTab, testTab);
		tabPane.getSelectionModel().select(buildTab);

		ChangeListener<Boolean> serviceRunningListener = new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				for (Tab tab : tabPane.getTabs()) {
					tab.setDisable(newValue && tab != tabPane.getSelectionModel().getSelectedItem());
				}
			}
		};		
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
	}

	/**
	 * @param args
	 *            the command line arguments
	 */
	public static void main(String[] args) {
		launch(args);
	}
}