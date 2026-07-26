package com.treasure.pcboard;

import android.app.PendingIntent;
import android.content.Context;
import android.icu.util.ULocale;
import android.os.Build;
import android.util.SparseArray;
import android.view.translation.*;
import java.util.Collections;

public final class SystemTranslationController {
    public interface Listener {
        void onTranslationState(String message);
        void onTranslationResult(String original, String translated);
        void onTranslationError(String message);
    }

    private final Context context;
    private final Listener listener;
    private Translator translator;

    public SystemTranslationController(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public boolean isSupported() {
        return Build.VERSION.SDK_INT >= 31 && context.getSystemService(TranslationManager.class) != null;
    }

    public void translate(String text, String targetLanguageTag) {
        String sourceText = text == null ? "" : text.trim();
        if (sourceText.isEmpty()) {
            listener.onTranslationError("Select text, or type a sentence before translating.");
            return;
        }
        if (Build.VERSION.SDK_INT < 31) {
            listener.onTranslationError("System translation requires Android 12 or later.");
            return;
        }
        TranslationManager manager = context.getSystemService(TranslationManager.class);
        if (manager == null) {
            listener.onTranslationError("This device does not provide an Android translation service.");
            return;
        }
        destroyTranslator();
        listener.onTranslationState("Preparing translation…");
        TranslationSpec source = new TranslationSpec(ULocale.forLanguageTag("en"), TranslationSpec.DATA_FORMAT_TEXT);
        TranslationSpec target = new TranslationSpec(
                ULocale.forLanguageTag(targetLanguageTag == null ? "fr" : targetLanguageTag),
                TranslationSpec.DATA_FORMAT_TEXT);
        TranslationContext translationContext = new TranslationContext.Builder(source, target)
                .setTranslationFlags(TranslationContext.FLAG_LOW_LATENCY)
                .build();

        new Thread(() -> manager.createOnDeviceTranslator(
                translationContext,
                context.getMainExecutor(),
                created -> {
                    if (created == null) {
                        listener.onTranslationError("The selected translation pair is not available on this device.");
                        return;
                    }
                    translator = created;
                    TranslationRequest request = new TranslationRequest.Builder()
                            .setFlags(TranslationRequest.FLAG_TRANSLATION_RESULT)
                            .setTranslationRequestValues(Collections.singletonList(TranslationRequestValue.forText(sourceText)))
                            .build();
                    created.translate(request, null, context.getMainExecutor(), response -> {
                        if (response == null || response.getTranslationStatus() != TranslationResponse.TRANSLATION_STATUS_SUCCESS) {
                            listener.onTranslationError("The device translation service could not translate this text.");
                            return;
                        }
                        SparseArray<TranslationResponseValue> values = response.getTranslationResponseValues();
                        TranslationResponseValue value = values == null ? null : values.get(0);
                        CharSequence translated = value == null ? null : value.getText();
                        if (value == null || value.getStatusCode() != TranslationResponseValue.STATUS_SUCCESS || translated == null) {
                            listener.onTranslationError("No translation result was returned.");
                            return;
                        }
                        listener.onTranslationResult(sourceText, translated.toString());
                    });
                })).start();
    }

    public PendingIntent settingsIntent() {
        if (Build.VERSION.SDK_INT < 31) return null;
        TranslationManager manager = context.getSystemService(TranslationManager.class);
        return manager == null ? null : manager.getOnDeviceTranslationSettingsActivityIntent();
    }

    public void destroy() { destroyTranslator(); }

    private void destroyTranslator() {
        if (translator != null) {
            translator.destroy();
            translator = null;
        }
    }
}
