package com.deepinmind.bear.core;

import javax.sound.sampled.LineUnavailableException;

import com.deepinmind.bear.session.Session;

public interface AudioService {

        public void play(String text);

        public void play(String text, String role);

        public Player getNewPlayer(Session session) throws LineUnavailableException, InterruptedException;

}
