package com.teamx.lawyerbuddy;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.ibm.watson.developer_cloud.android.library.audio.MicrophoneInputStream;
import com.ibm.watson.developer_cloud.android.library.audio.StreamPlayer;
import com.ibm.watson.developer_cloud.android.library.audio.utils.ContentType;
import com.ibm.watson.developer_cloud.android.library.camera.CameraHelper;
import com.ibm.watson.developer_cloud.android.library.camera.GalleryHelper;
import com.ibm.watson.developer_cloud.conversation.v1.ConversationService;
import com.ibm.watson.developer_cloud.conversation.v1.model.MessageRequest;
import com.ibm.watson.developer_cloud.conversation.v1.model.MessageResponse;
import com.ibm.watson.developer_cloud.http.ServiceCallback;
import com.ibm.watson.developer_cloud.language_translator.v2.LanguageTranslator;
import com.ibm.watson.developer_cloud.language_translator.v2.model.Language;
import com.ibm.watson.developer_cloud.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechResults;
import com.ibm.watson.developer_cloud.speech_to_text.v1.websocket.RecognizeCallback;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.Voice;

public class MainActivity extends AppCompatActivity {
    private final String LOG_TAG = MainActivity.class.getSimpleName();

    private RadioGroup targetLanguage;
    private EditText input;
    private ImageButton mic;
    private Button translate;
    private ImageButton play;
    private TextView translatedText;
    private Button convoSend;
    private TextView watsonResponse;

    private SpeechToText speechService;
    private TextToSpeech textService;
    private LanguageTranslator translationService;
    private Language selectedTargetLanguage = Language.SPANISH;

    private StreamPlayer player = new StreamPlayer();
    private CameraHelper cameraHelper;
    private GalleryHelper galleryHelper;

    private MicrophoneInputStream capture;
    private boolean listening = false;

    private String watsonResponseString;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraHelper = new CameraHelper(this);
        galleryHelper = new GalleryHelper(this);

        speechService = initSpeechToTextService();
        textService = initTextToSpeechService();
        translationService = initLanguageTranslatorService();

        targetLanguage = (RadioGroup) findViewById(R.id.target_language);
        input = (EditText) findViewById(R.id.input);
        mic = (ImageButton) findViewById(R.id.mic);
        translate = (Button) findViewById(R.id.translate);
        play = (ImageButton) findViewById(R.id.play);
        translatedText = (TextView) findViewById(R.id.translated_text);
        convoSend = (Button) findViewById(R.id.convo_send);
        watsonResponse = (TextView) findViewById(R.id.watson_response);

        targetLanguage.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.spanish:
                        selectedTargetLanguage = Language.VIETNAMESE;
                        break;
                    case R.id.french:
                        selectedTargetLanguage = Language.FRENCH;
                        break;
                    case R.id.italian:
                        selectedTargetLanguage = Language.ITALIAN;
                        break;
                }
            }
        });

        input.addTextChangedListener(new EmptyTextWatcher() {
            @Override public void onEmpty(boolean empty) {
                if (empty) {
                    translate.setEnabled(false);
                } else {
                    translate.setEnabled(true);
                }
            }
        });

        mic.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                //mic.setEnabled(false);

                if(listening != true) {
                    capture = new MicrophoneInputStream(true);
                    new Thread(new Runnable() {
                        @Override public void run() {
                            try {
                                speechService.recognizeUsingWebSocket(capture, getRecognizeOptions(), new MicrophoneRecognizeDelegate());
                            } catch (Exception e) {
                                showError(e);
                            }
                        }
                    }).start();
                    listening = true;
                } else {
                    try  {
                        capture.close();
                        listening = false;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }
        });

        translate.setOnClickListener(new View.OnClickListener() {

            @Override public void onClick(View v) {
                new TranslationTask().execute(input.getText().toString());
            }
        });

        translatedText.addTextChangedListener(new EmptyTextWatcher() {
            @Override public void onEmpty(boolean empty) {
                if (empty) {
                    play.setEnabled(false);
                } else {
                    play.setEnabled(true);
                }
            }
        });

        play.setEnabled(false);

        play.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new SynthesisTask().execute(translatedText.getText().toString());
            }
        });

        convoSend.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Log.v(LOG_TAG, input.getText().toString());
                sendConvo(input.getText().toString());
            }
        });
    }


    private void showTranslation(final String translation) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                translatedText.setText(translation);
            }
        });
    }

    private void showError(final Exception e) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        });
    }

    private void showMicText(final String text) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                input.setText(text);
            }
        });
    }

    private void enableMicButton() {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                mic.setEnabled(true);
            }
        });
    }

    private SpeechToText initSpeechToTextService() {
        SpeechToText service = new SpeechToText();
        String username = getString(R.string.speech_text_username);
        String password = getString(R.string.speech_text_password);
        service.setUsernameAndPassword(username, password);
        service.setEndPoint("https://stream.watsonplatform.net/speech-to-text/api");
        return service;
    }

    private TextToSpeech initTextToSpeechService() {
        TextToSpeech service = new TextToSpeech();
        String username = getString(R.string.text_speech_username);
        String password = getString(R.string.text_speech_password);
        service.setUsernameAndPassword(username, password);
        return service;
    }

    private LanguageTranslator initLanguageTranslatorService() {
        LanguageTranslator service = new LanguageTranslator();
        String username = getString(R.string.language_translation_username);
        String password = getString(R.string.language_translation_password);
        service.setUsernameAndPassword(username, password);
        return service;
    }

    private RecognizeOptions getRecognizeOptions() {
        return new RecognizeOptions.Builder()
                .continuous(true)
                .contentType(ContentType.OPUS.toString())
                .model("en-US_BroadbandModel")
                .interimResults(true)
                .inactivityTimeout(2000)
                .build();
    }

    private abstract class EmptyTextWatcher implements TextWatcher {
        private boolean isEmpty = true; // assumes text is initially empty

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (s.length() == 0) {
                isEmpty = true;
                onEmpty(true);
            } else if (isEmpty) {
                isEmpty = false;
                onEmpty(false);
            }
        }

        @Override public void afterTextChanged(Editable s) {}

        public abstract void onEmpty(boolean empty);
    }

    private class MicrophoneRecognizeDelegate implements RecognizeCallback {

        @Override
        public void onTranscription(SpeechResults speechResults) {
            System.out.println(speechResults);
            if(speechResults.getResults() != null && !speechResults.getResults().isEmpty()) {
                String text = speechResults.getResults().get(0).getAlternatives().get(0).getTranscript();
                showMicText(text);
            }
        }

        @Override public void onConnected() {

        }

        @Override public void onError(Exception e) {
            showError(e);
            enableMicButton();
        }

        @Override public void onDisconnected() {
            enableMicButton();
        }

        @Override
        public void onInactivityTimeout(RuntimeException runtimeException) {

        }

        @Override
        public void onListening() {

        }
    }

    private class TranslationTask extends AsyncTask<String, Void, String> {

        @Override protected String doInBackground(String... params) {
            showTranslation(translationService.translate(params[0], Language.ENGLISH, selectedTargetLanguage).execute().getFirstTranslation());
            return "Did translate";
        }
    }

    private class SynthesisTask extends AsyncTask<String, Void, String> {

        @Override protected String doInBackground(String... params) {
            player.playStream(textService.synthesize(params[0], Voice.EN_LISA).execute());
            return "Did synthesize";
        }

        @Override
        protected void onPostExecute(String result) {
            watsonResponse.setText(watsonResponseString);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case CameraHelper.REQUEST_PERMISSION: {
                // permission granted
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    cameraHelper.dispatchTakePictureIntent();
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

    }

    private void sendConvo(String input) {
        ConversationService service = new ConversationService(ConversationService.VERSION_DATE_2017_02_03);
        service.setUsernameAndPassword("e840d782-1596-4482-bbc0-05c8425d4db5", "bI4jfspXJPDk");

        MessageRequest newMessage = new MessageRequest.Builder().inputText(input).build();

        service.message("0fbc2538-d8bd-4049-b6ff-6327bc873035", newMessage).enqueue(new ServiceCallback<MessageResponse>() {

            @Override

            public void onResponse(MessageResponse response) {
                String theresponse = response.getText().get(0);
                watsonResponseString = theresponse;
                new SynthesisTask().execute(theresponse);
            }

            @Override

            public void onFailure(Exception e) { }

        });
    }
}
