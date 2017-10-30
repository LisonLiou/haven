
/*
 * Copyright (c) 2017 Nathanial Freitas / Guardian Project
 *  * Licensed under the GPLv3 license.
 *
 * Copyright (c) 2013-2015 Marco Ziccardi, Luca Bonato
 * Licensed under the MIT license.
 */
package info.guardianproject.phoneypot;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Camera;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.text.InputType;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import info.guardianproject.phoneypot.service.MonitorService;
import info.guardianproject.phoneypot.ui.CameraFragment;
import info.guardianproject.phoneypot.ui.MicrophoneConfigureActivity;

public class MonitorActivity extends FragmentActivity {
	
	private PreferenceManager preferences = null;

    private TextView txtTimer;
    private View viewTimer;

    private CountDownTimer cTimer;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean permsNeeded = askForPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, 1);

        if (!permsNeeded)
            initLayout();
    }

    private void initLayout ()
    {
        preferences = new PreferenceManager(getApplicationContext());
        setContentView(R.layout.layout_running);

        txtTimer = (TextView)findViewById(R.id.timer_text);
        viewTimer = findViewById(R.id.timer_container);

        int timeM = preferences.getTimerDelay()*1000;
        String timerText = String.format(Locale.getDefault(), "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(timeM) % 60,
                TimeUnit.MILLISECONDS.toSeconds(timeM) % 60);

        txtTimer.setText(timerText);
        txtTimer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cTimer == null)
                    showNumberPicker();

            }
        });
        findViewById(R.id.timer_text_title).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cTimer == null)
                    showNumberPicker();

            }
        });

        findViewById(R.id.btnStartLater).setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View v) {
               doCancel();
           }
       });

       findViewById(R.id.btnStartNow).setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View v) {
               ((Button)findViewById(R.id.btnStartLater)).setText(R.string.action_cancel);
               findViewById(R.id.btnStartNow).setVisibility(View.INVISIBLE);
               findViewById(R.id.timer_text_title).setVisibility(View.INVISIBLE);
               initTimer();
           }
       });

       findViewById(R.id.btnMicSettings).setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View v) {
               startActivity(new Intent(MonitorActivity.this, MicrophoneConfigureActivity.class));
           }
       });

        findViewById(R.id.btnCameraSwitch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchCamera();
            }
        });



    }

	private void switchCamera ()
    {

        String camera = preferences.getCamera();
        if (camera.equals(PreferenceManager.FRONT))
            preferences.setCamera(PreferenceManager.BACK);
        else if (camera.equals(PreferenceManager.BACK))
            preferences.setCamera(PreferenceManager.FRONT);

        ((CameraFragment)getSupportFragmentManager().findFragmentById(R.id.fragment_camera)).resetCamera();

    }

	private void updateTimerValue (int val)
    {
        preferences.setTimerDelay(val);
        int valM = val * 1000;
        String timerText = String.format(Locale.getDefault(), "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(valM) % 60,
                TimeUnit.MILLISECONDS.toSeconds(valM) % 60);

        txtTimer.setText(timerText);
    }

	private void doCancel ()
    {

        if (cTimer != null) {
            cTimer.cancel();
            cTimer = null;
            stopService(new Intent(this, MonitorService.class));

            findViewById(R.id.btnStartNow).setVisibility(View.VISIBLE);
            findViewById(R.id.timer_text_title).setVisibility(View.VISIBLE);

            ((Button)findViewById(R.id.btnStartLater)).setText(R.string.start_later);

            int timeM = preferences.getTimerDelay()*1000;
            String timerText = String.format(Locale.getDefault(), "%02d:%02d",
                    TimeUnit.MILLISECONDS.toMinutes(timeM) % 60,
                    TimeUnit.MILLISECONDS.toSeconds(timeM) % 60);

            txtTimer.setText(timerText);
        }
        else {

            close();
        }
    }

	private void showSettings ()
    {

        if (cTimer != null) {
            cTimer.cancel();
            cTimer = null;
        }

        Intent i = new Intent(this,SettingsActivity.class);
        startActivityForResult(i,9999);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 9999)
        {
            initTimer();
        }
    }

    private void initTimer ()
    {
        txtTimer.setTextColor(getResources().getColor(R.color.colorAccent));
        cTimer = new CountDownTimer((preferences.getTimerDelay())*1000, 1000) {

            public void onTick(long millisUntilFinished) {
                String timerText = String.format(Locale.getDefault(), "%02d:%02d",
                        TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60,
                        TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60);

                txtTimer.setText(timerText);
            }

            public void onFinish() {

                viewTimer.setVisibility(View.GONE);
                initMonitor();
            }

        };

        cTimer.start();


    }

	private void initMonitor ()
    {

        //ensure folder exists and will not be scanned by the gallery app

        try {
            File fileImageDir = new File(Environment.getExternalStorageDirectory(), preferences.getImagePath());
            fileImageDir.mkdirs();
            new FileOutputStream(new File(fileImageDir, ".nomedia")).write(0);
        }
        catch (IOException e){
            Log.e("Monitor","unable to init media storage directory",e);
        }

        //Do something after 100ms
        startService(new Intent(MonitorActivity.this, MonitorService.class));

    }
    
    /**
     * Closes the monitor activity and unset session properties
     */
    private void close() {

  	  stopService(new Intent(this, MonitorService.class));
  	  if (preferences != null) {
          preferences.unsetAccessToken();
          preferences.unsetDelegatedAccessToken();
          preferences.unsetPhoneId();
      }
  	  finish();
    	
    }
    
    /**
     * When user closes the activity
     */
    @Override
    public void onBackPressed() {
		close();
    }

    private void showNumberPicker ()
    {
        final NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(1);
        picker.setMaxValue(60*30);
        picker.setValue(preferences.getTimerDelay());

        final FrameLayout layout = new FrameLayout(this);
        layout.addView(picker, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        new AlertDialog.Builder(this)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // do something with picker.getValue()
                        updateTimerValue (picker.getValue());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 1:
                askForPermission(Manifest.permission.CAMERA,2);
                break;
            case 2:
                initLayout();
                break;
        }

    }


    private boolean askForPermission(String permission, Integer requestCode) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {

                //This is called if user has denied the permission before
                //In this case I am just asking the permission again
                ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);

            } else {

                ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
            }
            return true;
        } else {
            return false;
        }
    }

}
