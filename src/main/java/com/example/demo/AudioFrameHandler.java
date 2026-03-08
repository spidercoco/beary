package com.example.demo;

/**
 * 每帧音频处理回调接口（原始PCM16，单声道，小端序）
 */
public interface AudioFrameHandler {
	/**
	 * 处理一帧音频数据。
	 *
	 * @param pcmFrame  音频帧字节数组（复用同一缓冲区，若需持久化请自行拷贝）
	 * @param bytesRead 实际有效字节数
	 */
	void onAudioFrame(byte[] pcmFrame, int bytesRead);

	/** 可选：录音开始回调（默认空实现） */
	default void onStart() {}

	/** 可选：录音停止回调（默认空实现） */
	default void onStop() {}
}








