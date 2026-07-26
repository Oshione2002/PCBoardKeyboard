package com.treasure.pcboard;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;

public final class VoiceTypingController implements RecognitionListener {
    public interface Listener {
        void onVoiceState(String state, boolean listening);
        void onVoiceText(String text, boolean isFinal);
        void onVoiceError(String message);
    }

    private final Context context;
    private final Listener listener;
    private SpeechRecognizer recognizer;
    private boolean listening;

    public VoiceTypingController(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public boolean isListening() { return listening; }

    public void toggle(String languageTag) {
        if (listening) stop(); else start(languageTag);
    }

    public void start(String languageTag) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener.onVoiceError("Microphone permission is required. Open PCBoard settings to grant it.");
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onVoiceError("No speech recognition service is available on this device.");
            return;
        }
        destroyRecognizer();
        try {
            if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            } else {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            }
        } catch (RuntimeException error) {
            listener.onVoiceError("Voice typing could not start: " + error.getMessage());
            return;
        }
        recognizer.setRecognitionListener(this);
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag == null ? "en-NG" : languageTag);
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context));
        listening = true;
        listener.onVoiceState("Listening…", true);
        recognizer.startListening(intent);
    }

    public void stop() {
        if (recognizer != null && listening) {
            try { recognizer.stopListening(); } catch (RuntimeException ignored) {}
        }
        listening = false;
        listener.onVoiceState("Voice stopped", false);
    }

    public void destroy() {
        listening = false;
        destroyRecognizer();
    }

    private void destroyRecognizer() {
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (RuntimeException ignored) {}
            recognizer.destroy();
            recognizer = null;
        }
    }

    private String firstResult(Bundle bundle) {
        if (bundle == null) return "";
        ArrayList<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return results == null || results.isEmpty() ? "" : results.get(0);
    }

    @Override public void onReadyForSpeech(Bundle params) { listener.onVoiceState("Speak now", true); }
    @Override public void onBeginningOfSpeech() { listener.onVoiceState("Listening…", true); }
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() { listener.onVoiceState("Processing…", true); }

    @Override public void onError(int error) {
        listening = false;
        String message;
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: message = "Audio input error"; break;
            case SpeechRecognizer.ERROR_CLIENT: message = "Voice typing was cancelled"; break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: message = "Microphone permission was denied"; break;
            case SpeechRecognizer.ERROR_NETWORK: case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: message = "Speech service network error"; break;
            case SpeechRecognizer.ERROR_NO_MATCH: message = "No speech was recognised"; break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: message = "Speech recogniser is busy"; break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: message = "No speech was detected"; break;
            default: message = "Voice typing error " + error; break;
        }
        listener.onVoiceError(message);
        listener.onVoiceState(message, false);
    }

    @Override public void onResults(Bundle results) {
        listening = false;
        String text = firstResult(results);
        if (!text.isEmpty()) listener.onVoiceText(text, true);
        listener.onVoiceState(text.isEmpty() ? "No speech recognised" : "Voice inserted", false);
    }

    @Override public void onPartialResults(Bundle partialResults) {
        String text = firstResult(partialResults);
        if (!text.isEmpty()) listener.onVoiceText(text, false);
    }

    @Override public void onEvent(int eventType, Bundle params) {}
}
