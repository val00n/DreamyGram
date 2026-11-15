package com.val00n.dmpg.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.val00n.dmpg.App;
import com.val00n.dmpg.model.Instance;
import java.util.ArrayList;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import static android.content.ContentValues.TAG;

public class InstanceManager {

    private final String INSTANCES_FILENAME = "instances.json";

    private SharedPreferences prefs;
    private ArrayList<Instance> instances;
    private int selectedInstanceIndex;
    private FileHelper fileHelper;
    private Context context;

    public InstanceManager(Context context) {
        this.context = context;
        this.fileHelper = new FileHelper();
        this.prefs = context.getSharedPreferences("instances", 0);

        selectedInstanceIndex = prefs.getInt("selected_index", 0);
        Log.d(TAG, "InstanceManager: sel index: " + selectedInstanceIndex);
    }

    public ArrayList<Instance> getInstances() {
        return instances;
    }

    public Instance getSelectedInstance() {
        return instances.get(selectedInstanceIndex);
    }

    public Instance getInstance(int index) {
        return instances.get(index);
    }

    public String getSelectedInstanceBaseUrl() {
        return instances.get(selectedInstanceIndex).baseURL;
    }

    public String getInstanceBaseUrl(int index) {
        return instances.get(index).baseURL;
    }

    public int getSelectedInstanceIndex() {
        return selectedInstanceIndex;
    }

    public boolean hasInstanceWithUrl(String url, int excludeIndex) {
        for (int i = 0; i < instances.size(); i++) {
            if (i != excludeIndex && instances.get(i).baseURL.equals(url)) {
                return true;
            }
        }
        return false;
    }

    public void selectInstance(int index) {
        if (instances == null || index < 0 || index >= instances.size()) return;

        selectedInstanceIndex = index;
        saveSelectedIndex();

        App.getMPGramService().refreshInstance();
    }

    public void addInstance(Instance instance) {
        instances.add(instance);
        saveInstances(instances);
    }

    public void editInstance(Instance instance, int index) {
        instances.set(index, instance);
        saveInstances(instances);
    }
    
    public void deleteInstance(int index) {
        if (instances == null || index < 0 || index >= instances.size()) {
            return;
        }

        if (instances.size() <= 1) {
            Log.d(TAG, "deleteInstance: At least one instance is required");
            return;
        }

        instances.remove(index);

        if (index < selectedInstanceIndex) {
            selectedInstanceIndex--;
        } else if (index == selectedInstanceIndex) {
            selectedInstanceIndex = Math.max(0, instances.size() - 1);
        }

        saveInstances(instances);
        saveSelectedIndex();

        App.getMPGramService().refreshInstance();
    }

    public void loadInstances() {
        if (fileHelper.isFileExist(INSTANCES_FILENAME, true)) {
            String json = fileHelper.loadFileText(INSTANCES_FILENAME, true);

            if (json == null || json.length() == 0) {
                addDefaultInstances();
            } else {
                try {
                    JSONArray arr = new JSONArray(json);
                    if (arr != null && arr.size() > 0) {
                        instances = new ArrayList<Instance>();

                        for (int i = 0; i < arr.size(); i++) {
                            JSONObject obj = (JSONObject) arr.get(i);
                            if (obj.getNullable("baseURL") == null) {
                                continue;
                            }

                            Instance instance = new Instance(obj.getNullable("baseURL").toString());

                            instance.apiEndpoint = obj.getNullable("apiEndpoint") != null ? obj.getNullable("apiEndpoint").toString() : "api.php";
                            instance.avatarEndpoint = obj.getNullable("avatarEndpoint") != null ? obj.getNullable("avatarEndpoint").toString() : "ava.php";
                            instance.fileEndpoint = obj.getNullable("fileEndpoint") != null ? obj.getNullable("fileEndpoint").toString() : "file.php";
                            instance.voiceEndpoint = obj.getNullable("voiceEndpoint") != null ? obj.getNullable("voiceEndpoint").toString() : "voice.php";
                            instance.qrcodeEndpoint = obj.getNullable("qrcodeEndpoint") != null ? obj.getNullable("qrcodeEndpoint").toString() : "qrcode.php";
                            instance.apiVersion = obj.getNullable("apiVersion") != null ? obj.getNullable("apiVersion").toString() : "12";
                            instance.instancePassword = obj.getNullable("instancePassword") != null ? obj.getNullable("instancePassword").toString() : "";

                            instances.add(instance);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    instances = null;
                }
            }
        } else {
            addDefaultInstances();
        }

        if (instances == null || instances.isEmpty()) {
            addDefaultInstances();
        }

        if (selectedInstanceIndex < 0 || selectedInstanceIndex >= instances.size()) {
            selectedInstanceIndex = 0;
            saveSelectedIndex();
        }
    }

    private void saveInstances(ArrayList<Instance> list) {
        JSONArray arr = new JSONArray();
        for (Instance inst : list) {
            JSONObject obj = new JSONObject();
            obj.put("baseURL", inst.baseURL);
            obj.put("apiEndpoint", inst.apiEndpoint);
            obj.put("avatarEndpoint", inst.avatarEndpoint);
            obj.put("fileEndpoint", inst.fileEndpoint);
            obj.put("voiceEndpoint", inst.voiceEndpoint);
            obj.put("qrcodeEndpoint", inst.qrcodeEndpoint);
            obj.put("apiVersion", inst.apiVersion);
            obj.put("instancePassword", inst.instancePassword);
            obj.put("disableCertCheck", inst.disableCertCheck);
            arr.add(obj);
        }

        fileHelper.saveFileText(INSTANCES_FILENAME, arr.toString(), true);
    }

    private void saveSelectedIndex() {
        prefs.edit().putInt("selected_index", selectedInstanceIndex).commit();
    }

    public void addDefaultInstances() {
        // TODO: i don't like it, should think about it more later
        // should be called once on first startup
        instances = new ArrayList<Instance>();

        Instance wunder = new Instance("http://mp.wunderwungiel.pl");
        wunder.apiEndpoint = "api.php";
        wunder.avatarEndpoint = "ava.php";
        wunder.fileEndpoint = "file.php";
        wunder.voiceEndpoint = "voice.php";
        wunder.qrcodeEndpoint = "qrcode.php";
        wunder.apiVersion = "12";
        wunder.disableCertCheck = false;
        wunder.instancePassword = "";
        instances.add(wunder);

        Instance nnchan = new Instance("http://mp.nnproject.cc");
        nnchan.apiEndpoint = "api.php";
        nnchan.avatarEndpoint = "ava.php";
        nnchan.fileEndpoint = "file.php";
        nnchan.voiceEndpoint = "voice.php";
        nnchan.qrcodeEndpoint = "qrcode.php";
        nnchan.apiVersion = "12";
        nnchan.disableCertCheck = false;
        nnchan.instancePassword = "";
        instances.add(nnchan);

        Instance nnchan2 = new Instance("http://10.0.2.2");
        nnchan2.apiEndpoint = "api.php";
        nnchan2.avatarEndpoint = "ava.php";
        nnchan2.fileEndpoint = "file.php";
        nnchan2.voiceEndpoint = "voice.php";
        nnchan2.qrcodeEndpoint = "qrcode.php";
        nnchan2.apiVersion = "12";
        nnchan2.disableCertCheck = false;
        nnchan2.instancePassword = "";
        instances.add(nnchan2);

        saveInstances(instances);
    }
}
