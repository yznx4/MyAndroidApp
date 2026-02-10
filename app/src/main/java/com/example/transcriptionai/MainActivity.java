package com.example.transcriptionai;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String KEY_TRANSCRIPT = "key_transcript";
    private static final String KEY_SUMMARY = "key_summary";
    private static final String KEY_STATUS_RES = "key_status_res";
    private static final String KEY_UI_STATE = "key_ui_state";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1001;

    private Button buttonStart;
    private Button buttonStop;
    private Button buttonSummarize;
    private ProgressBar progressSummarizing;
    private TextView textStatus;
    private TextView textTranscript;
    private TextView textSummary;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable summarizeRunnable;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;

    private boolean shouldStartAfterPermission;

    private String transcript = "";
    private String summary = "";
    @StringRes
    private int statusResId = R.string.status_idle;
    private UiState uiState = UiState.IDLE;

    private enum UiState {
        IDLE,
        RECORDING,
        SUMMARIZING
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bindViews();
        bindActions();
        setupSpeechRecognizer();

        if (savedInstanceState != null) {
            transcript = savedInstanceState.getString(KEY_TRANSCRIPT, "");
            summary = savedInstanceState.getString(KEY_SUMMARY, "");
            statusResId = savedInstanceState.getInt(KEY_STATUS_RES, R.string.status_idle);
            String savedState = savedInstanceState.getString(KEY_UI_STATE, UiState.IDLE.name());
            uiState = UiState.valueOf(savedState);
        }

        renderText();

        if (uiState == UiState.SUMMARIZING) {
            beginSummarization();
        } else {
            applyUiState();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_TRANSCRIPT, transcript);
        outState.putString(KEY_SUMMARY, summary);
        outState.putInt(KEY_STATUS_RES, statusResId);
        outState.putString(KEY_UI_STATE, uiState.name());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (summarizeRunnable != null) {
            handler.removeCallbacks(summarizeRunnable);
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }

    private void bindViews() {
        buttonStart = findViewById(R.id.buttonStart);
        buttonStop = findViewById(R.id.buttonStop);
        buttonSummarize = findViewById(R.id.buttonSummarize);
        progressSummarizing = findViewById(R.id.progressSummarizing);
        textStatus = findViewById(R.id.textStatus);
        textTranscript = findViewById(R.id.textTranscript);
        textSummary = findViewById(R.id.textSummary);
    }

    private void bindActions() {
        buttonStart.setOnClickListener(v -> startRecording());
        buttonStop.setOnClickListener(v -> stopRecording());
        buttonSummarize.setOnClickListener(v -> {
            if (transcript.trim().isEmpty()) {
                setStatus(R.string.summary_failed);
                applyUiState();
                return;
            }
            uiState = UiState.SUMMARIZING;
            beginSummarization();
        });
    }

    private void startRecording() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus(R.string.speech_not_available);
            applyUiState();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            shouldStartAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }

        uiState = UiState.RECORDING;
        setStatus(R.string.listening);
        transcript = "";
        summary = "";
        renderText();
        applyUiState();
        if (speechRecognizer != null) {
            speechRecognizer.startListening(recognizerIntent);
        }
    }

    private void stopRecording() {
        if (uiState != UiState.RECORDING) {
            return;
        }
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
        uiState = UiState.IDLE;
        setStatus(R.string.status_recording_stopped);
        renderText();
        applyUiState();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO_PERMISSION) {
            return;
        }

        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            if (shouldStartAfterPermission) {
                shouldStartAfterPermission = false;
                startRecording();
            }
            return;
        }

        shouldStartAfterPermission = false;
        setStatus(R.string.permission_denied);
        Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
        applyUiState();
    }

    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return;
        }

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                setStatus(R.string.listening);
                applyUiState();
            }

            @Override
            public void onBeginningOfSpeech() {
                setStatus(R.string.listening);
                applyUiState();
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                if (uiState == UiState.RECORDING) {
                    setStatus(R.string.status_processing_transcript);
                    applyUiState();
                }
            }

            @Override
            public void onError(int error) {
                uiState = UiState.IDLE;
                setStatus(mapRecognizerErrorToStatus(error));
                applyUiState();
            }

            @Override
            public void onResults(Bundle results) {
                transcript = getBestResult(results, transcript);
                uiState = UiState.IDLE;
                setStatus(R.string.status_recording_stopped);
                renderText();
                applyUiState();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                transcript = getBestResult(partialResults, transcript);
                renderText();
                applyUiState();
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });
    }

    private String getBestResult(Bundle results, String fallbackValue) {
        if (results == null) {
            return fallbackValue;
        }
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) {
            return fallbackValue;
        }
        return matches.get(0);
    }

    @StringRes
    private int mapRecognizerErrorToStatus(int error) {
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            return R.string.transcription_no_match;
        }
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            return R.string.permission_denied;
        }
        if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                || error == SpeechRecognizer.ERROR_SERVER) {
            return R.string.transcription_network_error;
        }
        return R.string.transcription_generic_error;
    }

    private void beginSummarization() {
        if (summarizeRunnable != null) {
            handler.removeCallbacks(summarizeRunnable);
        }

        uiState = UiState.SUMMARIZING;
        setStatus(R.string.status_summarizing);
        applyUiState();

        summarizeRunnable = () -> {
            if (transcript.trim().isEmpty()) {
                setStatus(R.string.summary_failed);
            } else {
                summary = getString(R.string.summary_result_format, transcript);
                setStatus(R.string.status_summary_ready);
            }
            uiState = UiState.IDLE;
            renderText();
            applyUiState();
        };
        handler.postDelayed(summarizeRunnable, 1200L);
    }

    private void applyUiState() {
        boolean hasTranscript = !transcript.trim().isEmpty();

        if (uiState == UiState.RECORDING) {
            buttonStart.setEnabled(false);
            buttonStop.setEnabled(true);
            buttonSummarize.setEnabled(false);
            progressSummarizing.setVisibility(View.GONE);
        } else if (uiState == UiState.SUMMARIZING) {
            buttonStart.setEnabled(false);
            buttonStop.setEnabled(false);
            buttonSummarize.setEnabled(false);
            progressSummarizing.setVisibility(View.VISIBLE);
        } else {
            buttonStart.setEnabled(true);
            buttonStop.setEnabled(false);
            buttonSummarize.setEnabled(hasTranscript);
            progressSummarizing.setVisibility(View.GONE);
        }
    }

    private void renderText() {
        textStatus.setText(statusResId);
        textTranscript.setText(transcript.trim().isEmpty() ? getString(R.string.transcript_empty) : transcript);
        textSummary.setText(summary.trim().isEmpty() ? getString(R.string.summary_empty) : summary);
    }

    private void setStatus(@StringRes int newStatus) {
        statusResId = newStatus;
        textStatus.setText(statusResId);
    }
}
