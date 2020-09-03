package cyou.joiplay.rpgm;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.android.vending.expansion.zipfile.APKExpansionSupport;
import com.android.vending.expansion.zipfile.ZipResourceFile;
import com.joiplay.joipad.JoiPad;

import java.io.*;

public class MainActivity extends SDLActivity {

    private static final boolean OBB_MODE = true;
    private static final String GAME_FILE_NAME = "Game.zip";
    private static final String INI_FILE_NAME = "Game.ini";
    private static final String CONF_FILE_NAME = "mkxp.conf";
    private static final String VERSION_FILE_NAME = "VERSION.txt";
    private static String GAME_FOLDER;
    private static String OBB_FOLDER;
    private static String CONF_FILE_PATH;

    private JoiPad mJoiPad;

    public void onCreate(Bundle savedInstanceState) {
        GAME_FOLDER = getExternalFilesDir(null).getAbsolutePath();
        OBB_FOLDER = getObbDir().getAbsolutePath();
        CONF_FILE_PATH = String.format("%s%s%s", GAME_FOLDER, File.separator, CONF_FILE_NAME);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFormat(PixelFormat.TRANSLUCENT);

        if (Build.VERSION.SDK_INT > 20){
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
        super.onCreate(savedInstanceState);

        if (OBB_MODE)
        {
            PackageInfo info = null;
            try {
                info = getPackageManager().getPackageInfo(getPackageName(), 0);
            }
            catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            File obbFolder = new File(OBB_FOLDER);
            obbFolder.mkdirs();

            File obbFile = new File(obbFolder, getMainObbFileName(info.versionCode));
            try {
                //ZipResourceFile zipFile = APKExpansionSupport.getAPKExpansionZipFile(getContext(), info.versionCode, 0);
                ZipResourceFile zipFile = new ZipResourceFile(obbFile.getAbsolutePath());

                File versionFile = new File(GAME_FOLDER, VERSION_FILE_NAME);
                if (!versionFile.exists()) {
                    unzipFile(zipFile.getInputStream(GAME_FILE_NAME), GAME_FILE_NAME);
                } else {
                    BufferedReader br = new BufferedReader(new FileReader(GAME_FOLDER + File.separator + VERSION_FILE_NAME));
                    String line = br.readLine();
                    if (line == null || !line.trim().equals(String.valueOf(info.versionCode))) {
                        unzipFile(zipFile.getInputStream(GAME_FILE_NAME), GAME_FILE_NAME);
                    }
                    br.close();
                }
                unzipFile(zipFile.getInputStream(VERSION_FILE_NAME), VERSION_FILE_NAME);

            } catch (FileNotFoundException ex) {
                ex.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            File targetIni = new File(GAME_FOLDER, INI_FILE_NAME);
            if (targetIni.exists()) {
                targetIni.delete();
            }

            InputStream iniFis = getAssets().open(INI_FILE_NAME);
            OutputStream os = new FileOutputStream(targetIni);
            int len;
            byte[] buffer = new byte[1024];

            while ((len = iniFis.read(buffer)) > 0) {
                os.write(buffer,0, len);
            }

            iniFis.close();
            os.close();

            File new_conf = new File(CONF_FILE_PATH);
            new_conf.delete();
            new_conf.createNewFile();
            FileOutputStream fos = new FileOutputStream(new_conf);
            BufferedReader br = new BufferedReader(new InputStreamReader(getAssets().open(CONF_FILE_NAME)));

            String line;
            while ((line = br.readLine()) != null) {
                fos.write((line
                        .replace("GAME_FOLDER", GAME_FOLDER))
                        .getBytes());
                fos.write('\n');
            }
            fos.close();
            br.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        mJoiPad = new JoiPad(this, mLayout, (Integer key) -> {
            SDLActivity.onNativeKeyDown(key);
            return null;

        }, (Integer key) -> {
            SDLActivity.onNativeKeyUp(key);
            return null;

        }, () -> {
            android.os.Process.killProcess(android.os.Process.myPid());
            if (SDLActivity.mBrokenLibraries) {
                super.onDestroy();
                // Reset everything in case the user re opens the app
                SDLActivity.initialize();
                return null;
            }

            mNextNativeState = NativeState.PAUSED;
            SDLActivity.handleNativeState();

            // Send a quit message to the application
            SDLActivity.mExitCalledFromJava = true;
            SDLActivity.nativeQuit();

            // Now wait for the SDL thread to quit
            if (SDLActivity.mSDLThread != null) {
                try {
                    SDLActivity.mSDLThread.join();
                } catch(Exception e) {
                    Log.v("mkxp", "Problem stopping thread: " + e);
                }
                SDLActivity.mSDLThread = null;

                //Log.v(TAG, "Finished waiting for SDL thread");
            }

            super.onDestroy();
            // Reset everything in case the user re opens the app
            SDLActivity.initialize();
            return null;
        });

        mJoiPad.init();
    }

    public static String getConfPath(){
        return CONF_FILE_PATH;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mJoiPad != null){
            if (mJoiPad.processGamepadEvent(event)){
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void unzipFile(InputStream inputStream, String dest) {
        File file = new File(GAME_FOLDER, dest);
        try {
            OutputStream outputStream = new FileOutputStream(file);
            int len = 0;
            byte[] buf = new byte[1024];

            while ((len = inputStream.read(buf)) > 0) {
                outputStream.write(buf, 0, len);
            }
            
            outputStream.close();
            inputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getMainObbFileName(final int version) {
        return String.format("main.%d.%s.obb", version, getPackageName());
    }

    private String getPatchObbFileName(final int version) {
        return String.format("patch.%d.%s.obb", version, getPackageName());
    }
}
