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

import javafx.application.Platform;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.text.Text;

import org.musicmount.util.ProgressHandler;

public class FXProgressHandler implements ProgressHandler {
	private final Text statusText;
	private final ProgressIndicator progressIndicator;
	
	private double taskWork;
	private String taskTitle;
	
	public FXProgressHandler(Text statusText, ProgressIndicator progressIndicator) {
		this.statusText = statusText;
		this.progressIndicator = progressIndicator;
	}

	@Override
	public void beginTask(final int totalWork, final String title) {
		Platform.runLater(new Runnable() {
            @Override public void run() {
            	taskTitle = title;
            	taskWork = totalWork;
				statusText.setText(title);
				progressIndicator.setProgress(totalWork > 0 ? 0 : -1);
            }
        });
	}

	@Override
	public void progress(final int work, final String message) {
		Platform.runLater(new Runnable() {
            @Override public void run() {
				if (taskWork > 0) {
					progressIndicator.setProgress(work / taskWork);
				} else if (message != null) {
					statusText.setText(taskTitle != null ? taskTitle + " - " + message : message);
				}
            }
        });
	}

	@Override
	public void endTask() {
		Platform.runLater(new Runnable() {
            @Override public void run() {
				statusText.setText(null);
				progressIndicator.setProgress(1.0);
            }
        });
	}
}
