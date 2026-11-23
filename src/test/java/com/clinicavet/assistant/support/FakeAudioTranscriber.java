package com.clinicavet.assistant.support;

import com.clinicavet.assistant.channel.AudioTranscriber;

import java.util.Optional;

/** Transcrição falsa: devolve o texto que o teste pediu, sem chamar provedor. */
public final class FakeAudioTranscriber implements AudioTranscriber {

    private volatile Transcription next;

    public void reset() {
        next = null;
    }

    public FakeAudioTranscriber thenText(String text, double seconds) {
        next = new Transcription(text, seconds);
        return this;
    }

    @Override
    public Optional<Transcription> transcribe(byte[] audio, String mimeType) {
        Transcription result = next;
        next = null;
        return Optional.ofNullable(result);
    }
}
