package com.bluetooth.activities;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
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
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import static java.lang.Math.min;

/**
 * This activity allows creation of keyframe animations.
 *
 * Sends commands in the format of \"k,time,ch1,ch2,ch\3" and so on, where time and ch values are integers as displayed next to the sliders.
 * Time is milliseconds before next command should be expected.
 * Enabling "Play" plays the keyframes in a loop.
 * "Live preview" sends the current keyframe as changes are made. time is set to 0.
 * Commands are sent at 20hz, keyframes are linearly interpolated.
 */
public class Keyframes extends BluetoothActivity implements SeekBar.OnSeekBarChangeListener, RadioButton.OnCheckedChangeListener, AdapterView.OnItemSelectedListener {
    private final int animLimit = 1000; // used for interpolating
    private final int minDelay = 50; // min keyframe length
    private int defaultDelay = 500; // default keyframe length
    private int delayMillis = 50; // time between commands
    private int numChannels;
    private int currentFrame; // index to frames
    private int nextFrame;
    private boolean play = false;
    private boolean preview = false;
    private ProgressBar pbar;
    private Spinner interpolateSpinner;
    private ArrayList<SeekBar> seekBars;
    private ArrayList<TextView> textViews;
    private ArrayList<int[]> frames;
    private List<Integer> framesInterpolate; // 0=linear, 1=accel, 2=decel, 3=accel+decel
    private String fileName;
    private String[] mFileList;
    private String mChosenFile;
    private final String FTYPE = ".txt"; // extension of saved files
    private Context c;
    private Timer t = new Timer(); // for sending updates in preview mode
    private TimerTask tt;
    private ValueAnimator animation;
    private ToggleButton bPlay;
    private ToggleButton bPreview;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.keyframes);
        c = getApplicationContext();
        interpolateSpinner = (Spinner) findViewById(R.id.interpolateSpinner);
        interpolateSpinner.setOnItemSelectedListener(this);
        framesInterpolate = new ArrayList<Integer>();
        // populate seekBars and textViews arrays
        pbar = (ProgressBar) findViewById(R.id.progressBar);
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
        // set up frames lists and default 1st frame
        frames = new ArrayList<int[]>();
        frames.add(new int[numChannels]);
        frames.get(0)[0] = defaultDelay;
        framesInterpolate.add(0);
        createFrameList();
        selectKeyframe(0);

        bPlay = (ToggleButton) findViewById(R.id.bPlay);
        bPreview = (ToggleButton) findViewById(R.id.bPreview);
    }

    // set up menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.keyframes_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle menu button clicks
        switch (item.getItemId()) {
            case R.id.bSave:
                saveKeyframes();
                return true;
            case R.id.bLoad:
                if (play && animation != null) {
                    animation.end();
                    bPlay.setChecked(false);
                }
                loadFileList();
                createLoadDialog();
                return true;
            case R.id.bInfo:
                createInfoDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // handle activity losing focus
    @Override
    protected void onPause()
    {
        if (animation != null) animation.end();
        handleTimer(false);
        super.onPause();
    }

    /**
     * Opens an input dialog and saves keyframes in a text file in the external storage root /BlueberryRemote/keyframes
     */
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

    /**
     * Convert keyframes to string.
     * Delimits frames by a newline, delimits values in each frame by comma.
     * First is interpolate method, second value is time, next values are channels in ascending order.
     * @return Keyframes converted to string format.
     */
    private String keyframesToStr() {
        StringBuilder sb = new StringBuilder();
        int numFrames = frames.size();
        for (int i = 0; i < numFrames; i++) {
            sb.append(framesInterpolate.get(i));
            sb.append(',');
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

    /**
     * Populate a list of available keyframe files
     */
    protected void loadFileList() {
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
        builder.setTitle(getString(R.string.pickFile));
        if(mFileList == null) {
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

    // Create the menu info button dialog
    protected Dialog createInfoDialog() {
        Dialog dialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.info));
        builder.setMessage(R.string.keyframesInfo);
        dialog = builder.show();
        return dialog;
    }

    /**
     * Parses a keyframes file and populates the frames ArrayList.
     * @param fName File name not including path of file to open.
     */
    public void loadKeyframes(String fName) {
        Log.d("keyframes", "Opening keyframes at " + fName);
        frames.clear();
        framesInterpolate.clear();
        File f = new File(Environment.getExternalStorageDirectory() + "/" + getString(R.string.storageFolder) + "/keyframes/" + fName);
        try {
            InputStream inputStream = new FileInputStream(f);

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                while ( (receiveString = bufferedReader.readLine()) != null && receiveString.length() > 0 ) {
                    parseKeyframe(receiveString);
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
        // delete .txt extension
        fileName = fName.substring(0, fName.length() - 4);;
    }

    /**
     * Parses frame string
     * @param frameStr The string in format time,ch1,ch2..., all integers.
     * @return int[] with values in same order as input string
     */
    private void parseKeyframe(String frameStr) {
        // check if string is parsable
        Pattern doublePattern = Pattern.compile("^[0-9,]*$");
        if (!doublePattern.matcher(frameStr).matches() || frameStr.length() == 0) {
            Log.d("keyframes", frameStr);
            throw new NumberFormatException("Invalid keyframe format.");
        }

        String[] frameSplit = frameStr.split(",");

        int[] frame = new int[numChannels];
        for(int i = 0; i < numChannels; i++) {
            if (i < frameSplit.length-1 && frameSplit[i+1].length() > 0)
                frame[i] = Integer.parseInt(frameSplit[i+1]);
            else
                frame[i] = 0;
        }
        framesInterpolate.add(Integer.parseInt(frameSplit[0]));
        frames.add(frame);
    }

    /**
     * Populate the keyframe RadioButtons
     */
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

    /**
     * Updates currentFrame, checks appropriate RadioButton and populates sliders
     * @param frameIndex Index of frame in frames ArrayList
     */
    private void selectKeyframe(int frameIndex) {
        currentFrame = frameIndex;
        setSliderValues(frames.get(frameIndex));
        interpolateSpinner.setSelection(framesInterpolate.get(frameIndex));
        // update radio buttons
        RadioGroup frameList = (RadioGroup) findViewById(R.id.frameList);
        frameList.clearCheck();
        RadioButton btn = (RadioButton) frameList.getChildAt(frameIndex);
        btn.setChecked(true);
    }

    /**
     * Update slider values and text.
     * @param frame Array of values. frame[0] is time.
     */
    private void setSliderValues(int[] frame) {
        seekBars.get(0).setProgress(frame[0] - minDelay);
        textViews.get(0).setText(Integer.toString(frame[0]));
        for (int i=1; i < min(numChannels, frame.length); i++) {
            seekBars.get(i).setProgress(frame[i]);
            textViews.get(i).setText(Integer.toString(frame[i]));
        }
    }

    /**
     * Set up ValueAnimator animation and listeners to interpolate between keyframes.
     * Call animation.start() to start animation, animation.end() to end.
     * Animation plays in an infinite loop until stopped. Animation is modified on each repeat to play next frame.
     */
    private void setupAnimation() {
        animation = ValueAnimator.ofInt(0, animLimit);
        animation.setFrameDelay(delayMillis);
        animation.setRepeatCount(animation.INFINITE);
        animation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator anim) {
                int val = (int)anim.getAnimatedValue();
                int[] currentFrameArr = frames.get(currentFrame);
                int[] nextFrameArr = frames.get(nextFrame);
                int[] tmpFrameArr = new int[numChannels];
                // time
                tmpFrameArr[0] = delayMillis;
                // Interpolate between frames
                for (int i = 1; i < numChannels; i++) {
                    tmpFrameArr[i] = currentFrameArr[i] * (animLimit - val) / animLimit + nextFrameArr[i] * val / animLimit;
                }
                // sometimes the animation gets messed up after a repeat, skip writing frame if that happens
                if (animation.getCurrentPlayTime() < delayMillis/2 && val > animLimit/2) return;
                writeKeyframe(tmpFrameArr);
                tmpFrameArr[0] = currentFrameArr[0];
                setSliderValues(tmpFrameArr);
                pbar.setProgress(val * 100 / animLimit);
            }
        });
        animation.addListener(new ValueAnimator.AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator anim) {
                play = false;
                selectKeyframe(currentFrame);
                pbar.setProgress(0);
                handleTimer(true);
            }
            @Override
            public void onAnimationCancel(Animator anim) {
            }
            @Override
            public void onAnimationRepeat(Animator anim) {
                currentFrame = nextFrame;
                selectKeyframe(currentFrame);
                updateAnimation();
            }
            @Override
            public void onAnimationStart(Animator anim) {
                play = true;
                handleTimer(false);
                updateAnimation();
            }
        });
    }

    /**
     * Helper function for animation listeners. Sets up animation for next frame.
     */
    private void updateAnimation() {
        // if next index > last index, restart
        nextFrame = (currentFrame + 1 > frames.size() - 1) ? 0 : currentFrame + 1;
        animation.setCurrentPlayTime(0);
        animation.setDuration(frames.get(currentFrame)[0]);
        switch(framesInterpolate.get(currentFrame)) {
            case 0:
                animation.setInterpolator(new LinearInterpolator());
                break;
            case 1:
                animation.setInterpolator(new AccelerateInterpolator());
                break;
            case 2:
                animation.setInterpolator(new DecelerateInterpolator());
                break;
            case 3:
                animation.setInterpolator(new AccelerateDecelerateInterpolator());
                break;
            default:
                animation.setInterpolator(new LinearInterpolator());
        }
    }

    /**
     * Turns live preview command sending on and off.
     * Turns on only when not playing animation and in preview mode, and okToStart is true.
     * Sends commands at 20hz when timer is running.
     * @param okToStart Boolean. Gives timer permission to run. False always turns off timer.
     */
    private void handleTimer(boolean okToStart) {
        if (tt != null && (play || !preview || !okToStart)) {
            tt.cancel();
            t.purge();
        } else if (preview && !play && okToStart) {
            tt = new TimerTask() {
                @Override
                public void run() {
                    int[] tmpFrame = new int[numChannels];
                    System.arraycopy(frames.get(currentFrame), 0, tmpFrame, 0, frames.get(currentFrame).length);
                    tmpFrame[0] = delayMillis;
                    writeKeyframe(tmpFrame);
                }
            };
            t.schedule(tt, 0, delayMillis); //turn on timer to send command every 0.05 seconds
        }
    }

    /**
     * Send frame command via bluetooth. Format k,time,values...
     * @param frame int[] of values consisting of time and all channel values
     * @return Boolean success
     */
    private boolean writeKeyframe(int[] frame) {
        StringBuilder sb = new StringBuilder();
        sb.append("k,");
        for (int j = 0; j < numChannels; j++) {
            sb.append(frame[j]);
            if (j < numChannels-1)
                sb.append(',');
        }
        return write(sb.toString());
    }

    // Handle slider changes
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
    {
        if (play || !fromUser) return;
        int channel;
        switch(seekBar.getId()) {
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
            case R.id.sb5:
                channel = 5;
                break;
            case R.id.sb6:
                channel = 6;
                break;
            default:
                return;
        }
        textViews.get(channel).setText(Integer.toString(progress + (channel==0 ? minDelay : 0)));
        frames.get(currentFrame)[channel] = progress + (channel==0 ? minDelay : 0);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar)
    {
        // While slider is being changed, enable preview timer
        handleTimer(true);
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar)
    {
        // When slider has stopped being changed, disable preview timer
        handleTimer(false);
    }

    // handle interpolate method change
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        if (parent.getId() == R.id.interpolateSpinner) {
            framesInterpolate.set(currentFrame, pos);
        }
    }

    public void onNothingSelected(AdapterView<?> parent) {
        if (parent.getId() == R.id.interpolateSpinner) {
            framesInterpolate.set(currentFrame, 0);
        }
    }

    // Handle keyframe RadioButton clicks
    @Override
    public void onCheckedChanged(CompoundButton btn, boolean checked) {
        if (checked && !play) {
            selectKeyframe(Integer.parseInt(btn.getText().toString()) - 1);
        }
    }

    public void playBtnClick(View v)
    {
        if(((ToggleButton) v).isChecked())
        {
            if (animation == null) {
                setupAnimation();
            }
            animation.start();
        }
        else
        {
            animation.end();
        }
        bPreview.setEnabled(!play);
        Button btn = (Button) findViewById(R.id.insertBtn);
        btn.setEnabled(!play);
        btn = (Button) findViewById(R.id.deleteBtn);
        btn.setEnabled(!play);
        interpolateSpinner.setEnabled(!play);
    }

    public void insertBtnClick(View v) {
        int[] frame = new int[numChannels];
        frame[0] = defaultDelay;
        // ArrayList.add(int, arr) inserts to the left but add button should add to the right
        // so just add(arr) if adding next to last frame
        if (currentFrame == frames.size() - 1) {
            frames.add(frame);
            framesInterpolate.add(0);
        }
        else {
            frames.add(currentFrame + 1, frame);
            framesInterpolate.add(currentFrame + 1, 0);
        }
        currentFrame += 1;
        createFrameList();
    }

    public void deleteBtnClick(View v) {
        // delete current frame
        if (frames.size() > 1) {
            frames.remove(currentFrame);
            framesInterpolate.remove(currentFrame);
            if (currentFrame != 0) currentFrame -= 1;
            createFrameList();
        }
    }

    public void previewBtnClick(View v)
    {
        preview = ((ToggleButton) v).isChecked();
    }
}
