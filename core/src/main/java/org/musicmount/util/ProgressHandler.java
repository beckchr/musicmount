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
package org.musicmount.util;

public interface ProgressHandler {
	public static final ProgressHandler NOOP = new ProgressHandler() {
		@Override
		public void beginTask(int totalWork, String title) {
		}
		@Override
		public void progress(int work, String message) {
		}
		@Override
		public void endTask() {
		}
	};

	public void beginTask(int totalWork, String title);
	public void progress(int work, String message);
	public void endTask();
}
