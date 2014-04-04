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
