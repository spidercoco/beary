package com.example.demo;

import javax.sound.sampled.*;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 简单的麦克风录音器：不落盘，按帧回调。
 *
 * PCM16 / 单声道 / 小端序。
 */
public class AudioRecorder implements AutoCloseable {
	private final float sampleRateHz;
	private final int frameMillis;
	private final int bufferBytes;

	private final AudioFormat format;
	private TargetDataLine micLine;
	private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
	private final AtomicBoolean running = new AtomicBoolean(false);

	/**
 	 * @param sampleRateHz 采样率（如 16000）
 	 * @param frameMillis  每帧毫秒数（如 20ms -> 640字节）
 	 */
	public AudioRecorder(float sampleRateHz, int frameMillis) {
		this.sampleRateHz = sampleRateHz;
		this.frameMillis = frameMillis;
		this.format = new AudioFormat(sampleRateHz, 16, 1, true, false);
		int bytesPerSecond = (int) (sampleRateHz * 2); // 16bit mono -> 2 bytes/sample
		this.bufferBytes = Math.max(320, (bytesPerSecond * frameMillis) / 1000);
	}

	/**
 	 * 使用默认 16kHz / 20ms 帧。
 	 */
	public AudioRecorder() {
		this(16000f, 20);
	}

	/**
 	 * 启动录音并把每帧交给 handler 处理。
 	 */
	public synchronized void start(AudioFrameHandler handler) throws LineUnavailableException {
		Objects.requireNonNull(handler, "handler cannot be null");
		if (running.get()) return;

		DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
		if (!AudioSystem.isLineSupported(info)) {
			throw new LineUnavailableException("Unsupported audio format: " + format);
		}
		micLine = (TargetDataLine) AudioSystem.getLine(info);
		micLine.open(format);
		micLine.start();

		running.set(true);
		handler.onStart();

		ioExecutor.submit(() -> {
			byte[] buffer = new byte[bufferBytes];
			try {
				while (running.get()) {
					int read = micLine.read(buffer, 0, buffer.length);
					if (read > 0) {
						handler.onAudioFrame(buffer, read);
					}
				}
			} catch (Exception ignored) {
				// 交由调用方感知停止，不抛出打断异常
			} finally {
				try { micLine.stop(); } catch (Exception ignored2) {}
				try { micLine.flush(); } catch (Exception ignored3) {}
				handler.onStop();
			}
		});
	}

	/** 停止录音（可重复调用） */
	public synchronized void stop() {
		if (!running.getAndSet(false)) return;
		// 让读取循环自然退出
	}

	@Override
	public synchronized void close() {
		stop();
		try {
			if (micLine != null) {
				try { micLine.stop(); } catch (Exception ignored) {}
				try { micLine.close(); } catch (Exception ignored) {}
				micLine = null;
			}
		} finally {
			ioExecutor.shutdownNow();
		}
	}
}








