package com.bluetooth.activities;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.bluetooth.BluetoothActivity;
import com.bluetooth.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Pattern;

import static java.lang.Math.min;


public class Keyframes extends BluetoothActivity implements SeekBar.OnSeekBarChangeListener, RadioButton.OnCheckedChangeListener {
    private Button bPlay;
    private Button bPreview;
    private boolean play = false;
    private boolean preview = false;
    private ArrayList<SeekBar> seekBars;
    private ArrayList<TextView> textViews;
    private ArrayList<int[]> frames;
    private int numChannels;
    int currentFrame;
    private String fileName;

    private String[] mFileList;
    private String mChosenFile;
    private static final String FTYPE = ".txt";
    private Context c;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.keyframes);
        c = getApplicationContext();
        // populate seekBars and textViews arrays
        // channel 0 is time delay
        seekBars = new ArrayList<SeekBar>();
        seekBars.add((SeekBar) findViewById(R.id.sbTimeDelay));
        seekBars.get(0).setOnSeekBarChangeListener(this);
        textViews = new ArrayList<TextView>();
        textViews.add((TextView) findViewById(R.id.tvTimeDelay));
        // add rest of channels
        numChannels = 1;
        int seekBarId;
        while ( (seekBarId = getResources().getIdentifier("sb" + numChannels, "id", getPackageName())) != 0 ) {
            SeekBar seekBar = (SeekBar) findViewById(seekBarId);
            seekBar.setOnSeekBarChangeListener(this);
            seekBars.add(seekBar);
            TextView textView = (TextView) findViewById(getResources().getIdentifier("tv" + numChannels, "id", getPackageName()));
            textViews.add(textView);
            numChannels++;
        }
        frames = new ArrayList<int[]>();
        frames.add(new int[numChannels]);
        createFrameList();
        selectKeyframe(0);
        bPlay = (Button) findViewById(R.id.bPlay);
        bPreview = (Button) findViewById(R.id.bPreview);
    }
    public void saveKeyframes() {
        // prompt for file name
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        //builder.setTitle("File name");

        // set content of dialog to save_prompt.xml
        View promptView = LayoutInflater.from(c).inflate(R.layout.save_prompt, null);
        builder.setView(promptView);

        final EditText userInput = (EditText) promptView.findViewById(R.id.savePromptInput);
        userInput.setText(fileName);

        // Set up the buttons
        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // save file
                // check length of input
                String fileNameTmp = userInput.getText().toString();
                if (fileNameTmp.length() == 0) {
                    Toast.makeText(c, "Save failed. Name cannot be blank.", Toast.LENGTH_SHORT).show();
                    return;
                }
                // check if storage is available to read and write
                if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                    Toast.makeText(c, "Save failed. Storage not available.", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    // get output file and create if needed
                    File f = new File(Environment.getExternalStorageDirectory() + "/" + getString(R.string.storageFolder) + "/keyframes/" + fileNameTmp + ".txt");
                    if (!f.exists()) {
                        // create parent directory is needed
                        if (!f.getParentFile().exists() && !f.getParentFile().mkdirs()){
                            Toast.makeText(c, "Save failed. Couldn't make save directory.", Toast.LENGTH_SHORT).show();
                            Log.d("keyframes", "Attempted path failed: " + Environment.getExternalStorageDirectory() + "/" + getString(R.string.storageFolder) + "/keyframes/" + fileNameTmp + ".txt");
                            return;
                        }
                        if (!f.exists()) {
                            f.createNewFile();
                        }
                    }
                    // write file
                    FileOutputStream fOut = new FileOutputStream(f);
                    fOut.write(keyframesToStr().getBytes());
                }
                catch (IOException e) {
                    Toast.makeText(c, "Save failed. " + e.toString(), Toast.LENGTH_SHORT).show();
                    return;
                }
                fileName = fileNameTmp;
                Toast.makeText(c, getString(R.string.saveSuccess) + "/" + fileName + ".txt", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    private void loadFileList() {
        File mPath = new File(Environment.getExternalStorageDirectory() + "/" + getString(R.string.storageFolder) + "/keyframes/");
        if(mPath.exists()) {
            FilenameFilter filter = new FilenameFilter() {

                @Override
                public boolean accept(File dir, String filename) {
                    File sel = new File(dir, filename);
                    return filename.contains(FTYPE) || sel.isDirectory();
                }

            };
            mFileList = mPath.list(filter);
        }
        else {
            mFileList= new String[0];
        }
    }

    protected Dialog createLoadDialog() {
        Dialog dialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose your file");
        if(mFileList == null) {
            //Log.e(TAG, "Showing file picker before loading the file list");
            dialog = builder.create();
            return dialog;
        }
        builder.setItems(mFileList, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                mChosenFile = mFileList[which];
                loadKeyframes(mChosenFile);
            }
        });
        dialog = builder.show();
        return dialog;
    }

    private String keyframesToStr() {
        StringBuilder sb = new StringBuilder();
        int numFrames = frames.size();
        for (int i = 0; i < numFrames; i++) {
            int[] frame = frames.get(i);
            for (int j = 0; j < numChannels; j++) {
                sb.append(frame[j]);
                if (j < numChannels-1)
                    sb.append(',');
            }
            if (i < numFrames-1)
                sb.append('\n');
        }
        return sb.toString();
    }
    public void loadKeyframes(String fName) {
        Log.d("keyframes", "Opening keyframes at " + fName);
        frames.clear();
        File f = new File(Environment.getExternalStorageDirectory() + "/" + getString(R.string.storageFolder) + "/keyframes/" + fName);
        try {
            InputStream inputStream = new FileInputStream(f);

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                while ( (receiveString = bufferedReader.readLine()) != null && receiveString.length() > 0 ) {
                    frames.add(parseKeyframe(receiveString));
                }
                createFrameList();
                selectKeyframe(0);
                inputStream.close();
            } else {
                Toast.makeText(c, "Couldn't find and/or open file.", Toast.LENGTH_SHORT).show();
            }
        }
        catch (FileNotFoundException e) {
            Log.d("keyframes", "File not found: " + e.toString());
            Toast.makeText(c, "File not found.", Toast.LENGTH_SHORT).show();
            return;
        }
        catch (IOException e) {
            Log.d("keyframes", "Can't read file: " + e.toString());
            Toast.makeText(c, "Can't read file: " + e.toString(), Toast.LENGTH_SHORT).show();
            return;
        }
        catch (NumberFormatException e) {
            Log.d("keyframes", "Invalid keyframe format.");
            Toast.makeText(c, e.toString(), Toast.LENGTH_SHORT).show();
            return;
        }
        fileName = fName;
    }
    private int[] parseKeyframe(String frameStr) {
        // check if string is parsable
        Pattern doublePattern = Pattern.compile("^[0-9,]*$");
        if (!doublePattern.matcher(frameStr).matches() || frameStr.length() == 0) {
            Log.d("keyframes", frameStr);
            throw new NumberFormatException("Invalid keyframe format.");
        }

        String[] frameSplit = frameStr.split(",");
        int[] frame = new int[numChannels];
        for(int i = 0; i < numChannels; i++) {
            if (i < frameSplit.length && frameSplit[i].length() > 0)
                frame[i] = Integer.parseInt(frameSplit[i]);
            else
                frame[i] = 0;
        }
        return frame;
    }
    private void createFrameList() {
        ViewGroup linearLayout = (ViewGroup) findViewById(R.id.frameList);
        linearLayout.removeAllViews();
        for (int i = 0; i < frames.size(); i++) {
            RadioButton bt = new RadioButton(c);
            bt.setText(Integer.toString(i + 1));
            bt.setOnCheckedChangeListener(this);
            linearLayout.addView(bt);
        }
        selectKeyframe(currentFrame);
    }

    @Override
    public void onCheckedChanged(CompoundButton btn, boolean checked) {
        if (checked) {
            selectKeyframe(Integer.parseInt(btn.getText().toString()) - 1);
        }
    }

    private void selectKeyframe(int frameIndex) {
        currentFrame = frameIndex;
        int[] frame = frames.get(frameIndex);
        for (int i=0; i < min(numChannels, frame.length); i++) {
            seekBars.get(i).setProgress(frame[i]);
            textViews.get(i).setText(Integer.toString(frame[i]));
        }
        RadioGroup frameList = (RadioGroup) findViewById(R.id.frameList);
        frameList.clearCheck();
        RadioButton btn = (RadioButton) frameList.getChildAt(frameIndex);
        btn.setChecked(true);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
    {
        int channel;
        switch(seekBar.getId())
        {
            case R.id.sbTimeDelay:
                channel = 0;
                break;
            case R.id.sb1:
                channel = 1;
                break;
            case R.id.sb2:
                channel = 2;
                break;
            case R.id.sb3:
                channel = 3;
                break;
            case R.id.sb4:
                channel = 4;
                break;
            default:
                return;
        }
        textViews.get(channel).setText(Integer.toString(progress));
        frames.get(currentFrame)[channel] = progress;
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar)
    {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar)
    {

    }
    public void insertBtnClick(View v) {
        // ArrayList.add(int, arr) inserts to the left but add button should add to the right
        // so just add(arr) if adding next to last frame
        if (currentFrame == frames.size() - 1) {
            frames.add(new int[numChannels]);
        }
        else {
            frames.add(currentFrame + 1, new int[numChannels]);
        }
        currentFrame += 1;
        createFrameList();
    }
    public void deleteBtnClick(View v) {
        if (frames.size() > 1) {
            frames.remove(currentFrame);
            if (currentFrame != 0) currentFrame -= 1;
            createFrameList();
        }
    }
    public void playBtnClick(View v)
    {
        if(((ToggleButton) v).isChecked())
        {
            play = true;
        }
        else
        {
            play = false;
        }
    }
    public void previewBtnClick(View v)
    {
        if(((ToggleButton) v).isChecked())
        {
            preview = true;
        }
        else
        {
            preview = false;
        }
    }
    public void saveBtnClick(View v)
    {
        saveKeyframes();
    }
    public void loadBtnClick(View v)
    {
        loadFileList();
        createLoadDialog();
    }
}
