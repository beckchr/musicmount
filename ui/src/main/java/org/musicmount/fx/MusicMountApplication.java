/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.musicmount.fx;

import java.util.logging.Level;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import org.musicmount.builder.MusicMountBuilder;
import org.musicmount.util.LoggingUtil;

/**
 * 
 * @author christoph
 */
public class MusicMountApplication extends Application {
	static {
		LoggingUtil.configure(MusicMountBuilder.class.getPackage().getName(), Level.FINE);
	}

	@Override
	public void start(Stage primaryStage) {
//		System.out.println("java version: " + System.getProperty("java.version"));

		FXCommandModel model = new FXCommandModel();
		BuildController buildController = new BuildController(model);
		Pane buildPane = buildController.getPane();
		buildPane.setId("build-pane");

		TestController testController = new TestController(model);
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

		BorderPane borderPane = new BorderPane();
		borderPane.setId("border-pane");
		borderPane.setCenter(tabPane);
		
		Scene scene = new Scene(borderPane, 600, borderPane.getPrefHeight());
		scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
		primaryStage.setTitle("MusicMount");
		primaryStage.setScene(scene);
		primaryStage.show();
		primaryStage.setResizable(false);
	}

	/**
	 * @param args
	 *            the command line arguments
	 */
	public static void main(String[] args) {
		launch(args);
	}

}
