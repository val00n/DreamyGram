package com.val00n.dmpg.ui.settings.instances;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.Instance;

public class InstanceEditActivity extends Activity {

    private EditText baseURLEditText;
    private EditText APIEditText;
    private EditText avatarEditText;
    private EditText fileEditText;
    private EditText voiceEditText;
    private EditText QREditText;
    private EditText APIVersionEditText;
    private EditText passwordEditText;
    private CheckBox checkCertsCheckBox;
    private Button saveButton;

    private int instanceIndex;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.instance_edit);

        baseURLEditText = (EditText) findViewById(R.id.instanceEditBaseURLEditText);
        APIEditText = (EditText) findViewById(R.id.instanceEditAPIEditText);
        avatarEditText = (EditText) findViewById(R.id.instanceEditAvatarEditText);
        QREditText = (EditText) findViewById(R.id.instanceEditQREditText);
        fileEditText = (EditText) findViewById(R.id.instanceEditFileEditText);
        voiceEditText = (EditText) findViewById(R.id.instanceEditVoiceEditText);
        APIVersionEditText = (EditText) findViewById(R.id.instanceEditAPIVersionEditText);
        passwordEditText = (EditText) findViewById(R.id.instanceEditPasswordEditText);
        checkCertsCheckBox = (CheckBox) findViewById(R.id.instanceEditCheckCertsCheckBox);
        saveButton = (Button) findViewById(R.id.instanceEditSaveButton);

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveChanges();
            }
        });

        instanceIndex = getIntent().getIntExtra("INSTANCE_INDEX", -1);
        if (instanceIndex > -1) {
            loadInstance(App.getInstanceManager().getInstance(instanceIndex));
        }
    }

    private void loadInstance(Instance instance) {
        baseURLEditText.setText(instance.baseURL);
        APIEditText.setText(instance.apiEndpoint);
        avatarEditText.setText(instance.avatarEndpoint);
        fileEditText.setText(instance.fileEndpoint);
        voiceEditText.setText(instance.voiceEndpoint);
        QREditText.setText(instance.qrcodeEndpoint);
        APIVersionEditText.setText(instance.apiVersion);
        passwordEditText.setText(instance.instancePassword);
        checkCertsCheckBox.setChecked(instance.disableCertCheck);
    }

    private void saveChanges() {
        String baseURL = baseURLEditText.getText().toString();
        if (baseURL.length() < 1) {
            Toast.makeText(this, R.string.instance_edit_invalid_base_url, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!baseURL.startsWith("http://") && !baseURL.startsWith("https://")) {
            baseURL = "http://" + baseURL;
        }

        if (APIEditText.getText().toString().length() > 0 &&
                fileEditText.getText().toString().length() > 0 &&
                voiceEditText.getText().toString().length() > 0 &&
                QREditText.getText().toString().length() > 0 &&
                avatarEditText.getText().toString().length() > 0 &&
                APIVersionEditText.getText().toString().length() > 0) {

            Instance instance = new Instance(baseURL);
            instance.apiEndpoint = APIEditText.getText().toString();
            instance.avatarEndpoint = avatarEditText.getText().toString();
            instance.fileEndpoint = fileEditText.getText().toString();
            instance.voiceEndpoint = voiceEditText.getText().toString();
            instance.qrcodeEndpoint = QREditText.getText().toString();
            instance.apiVersion = APIVersionEditText.getText().toString();
            instance.disableCertCheck = !checkCertsCheckBox.isChecked();
            instance.instancePassword = passwordEditText.getText().toString();

            if (instanceIndex > -1) {
                if (App.getInstanceManager().hasInstanceWithUrl(baseURL, instanceIndex)) {
                    Toast.makeText(this, R.string.instance_edit_instance_exists, Toast.LENGTH_SHORT).show();
                    return;
                }

                App.getInstanceManager().editInstance(instance, instanceIndex);
                App.getInstanceManager().selectInstance(instanceIndex);
            } else {
                if (App.getInstanceManager().hasInstanceWithUrl(baseURL, -1)) {
                    Toast.makeText(this, R.string.instance_edit_instance_exists, Toast.LENGTH_SHORT).show();
                    return;
                }

                App.getInstanceManager().addInstance(instance);
                App.getInstanceManager().selectInstance(App.getInstanceManager().getInstances().size() - 1);
            }

            finish();
        } else {
            Toast.makeText(this, R.string.instance_edit_invalid_fields, Toast.LENGTH_SHORT).show();
        }
    }
}
