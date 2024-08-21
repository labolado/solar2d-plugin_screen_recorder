//
//  LuaLoader.java
//  TemplateApp
//
//  Copyright (c) 2012 __MyCompanyName__. All rights reserved.
//

// This corresponds to the name of the Lua library,
// e.g. [Lua] require "plugin.library"
package plugin.screenRecorder;

import static android.app.Activity.RESULT_OK;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.ansca.corona.CoronaActivity;
import com.ansca.corona.CoronaEnvironment;
import com.ansca.corona.CoronaLua;
import com.ansca.corona.CoronaRuntime;
import com.ansca.corona.CoronaRuntimeListener;
import com.ansca.corona.CoronaRuntimeTask;
import com.naef.jnlua.JavaFunction;
import com.naef.jnlua.LuaState;
import com.naef.jnlua.NamedJavaFunction;

import com.hbisoft.hbrecorder.HBRecorder;
import com.hbisoft.hbrecorder.HBRecorderListener;
import com.hbisoft.hbrecorder.Constants;

import java.io.File;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;


/**
 * Implements the Lua interface for a Corona plugin.
 * <p>
 * Only one instance of this class will be created by Corona for the lifetime of the application.
 * This instance will be re-used for every new Corona activity that gets created.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class LuaLoader implements JavaFunction, CoronaRuntimeListener, HBRecorderListener, CoronaActivity.OnActivityResultHandler, CoronaActivity.OnRequestPermissionsResultHandler {
	/** Lua registry ID to the Lua function to be called when the ad request finishes. */
	private int fListener;
	private HBRecorder fRecorder;
	private boolean fHasPermissions = false;
	private int fSCREEN_RECORD_REQUEST_CODE = -1;
	private int fPERMISSION_REQ_ID_RECORD_AUDIO = 22;
	private int fPERMISSION_REQ_POST_NOTIFICATIONS = 33;
	private int fPERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE = 34;

	/** This corresponds to the event name, e.g. [Lua] event.name */
	private static final String EVENT_NAME = "screenRecorder";

	// private static final int SCREEN_RECORD_REQUEST_CODE = 2;
	// private static final int PERMISSION_REQ_ID_RECORD_AUDIO = 22;
	// private static final int PERMISSION_REQ_POST_NOTIFICATIONS = 33;
	// private static final int PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE = PERMISSION_REQ_ID_RECORD_AUDIO + 1;

	/**
	 * Creates a new Lua interface to this plugin.
	 * <p>
	 * Note that a new LuaLoader instance will not be created for every CoronaActivity instance.
	 * That is, only one instance of this class will be created for the lifetime of the application process.
	 * This gives a plugin the option to do operations in the background while the CoronaActivity is destroyed.
	 */
	@SuppressWarnings("unused")
	public LuaLoader() {
		// Initialize member variables.
		fListener = CoronaLua.REFNIL;

		// Set up this plugin to listen for Corona runtime events to be received by methods
		// onLoaded(), onStarted(), onSuspended(), onResumed(), and onExiting().
		CoronaEnvironment.addRuntimeListener(this);
	}

	/**
	 * Called when this plugin is being loaded via the Lua require() function.
	 * <p>
	 * Note that this method will be called every time a new CoronaActivity has been launched.
	 * This means that you'll need to re-initialize this plugin here.
	 * <p>
	 * Warning! This method is not called on the main UI thread.
	 * @param L Reference to the Lua state that the require() function was called from.
	 * @return Returns the number of values that the require() function will return.
	 *         <p>
	 *         Expected to return 1, the library that the require() function is loading.
	 */
	@Override
	public int invoke(LuaState L) {
		// Register this plugin into Lua with the following functions.
		NamedJavaFunction[] luaFunctions = new NamedJavaFunction[] {
			new InitWrapper(),
			new StartWrapper(),
			new StopWrapper(),
		};
		String libName = L.toString( 1 );
		L.register(libName, luaFunctions);

		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();

		if (activity != null) {
			fRecorder = new HBRecorder(activity, this);
			fSCREEN_RECORD_REQUEST_CODE = activity.registerActivityResultHandler(this);
			int requestCodeOffset = activity.registerRequestPermissionsResultHandler(this, 3);
			if (requestCodeOffset > 0) {
				fPERMISSION_REQ_ID_RECORD_AUDIO = requestCodeOffset;
				fPERMISSION_REQ_POST_NOTIFICATIONS = requestCodeOffset + 1;
				fPERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE = requestCodeOffset + 2;
			}
			Log.d("Corona", "requestCode1: " + fSCREEN_RECORD_REQUEST_CODE);
			Log.d("Corona", "requestCode2: " + fPERMISSION_REQ_ID_RECORD_AUDIO);
			Log.d("Corona", "requestCode3: " + fPERMISSION_REQ_POST_NOTIFICATIONS);
			Log.d("Corona", "requestCode4: " + fPERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE);
		}

		// Returning 1 indicates that the Lua require() function will return the above Lua library.
		return 1;
	}

	private void quickSettings() {
		fRecorder.setAudioBitrate(128000);
		fRecorder.setAudioSamplingRate(44100);
		// fRecorder.recordHDVideo(true);
		fRecorder.isAudioEnabled(true);
	}

	@Override
	public void onHandleActivityResult(CoronaActivity coronaActivity, int requestCode, int resultCode, Intent data) {
		Log.d("Corona", "onHandleActivityResult");
		if (requestCode == fSCREEN_RECORD_REQUEST_CODE) {
			if (resultCode == RESULT_OK) {
				//Start screen recording
				setOutputPath();
				fRecorder.startScreenRecording(data, resultCode);
			}
		}
	}

    @Override
	public void onHandleRequestPermissionsResult(CoronaActivity coronaActivity, int requestCode, String[] permissions, int[] grantResults) {
		Log.d("Corona", "onHandleRequestPermissionsResult: requestCode = " + requestCode);
		// if (fPERMISSION_REQ_POST_NOTIFICATIONS == requestCode) {
		// 	if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
		// 		checkSelfPermission(Manifest.permission.RECORD_AUDIO, fPERMISSION_REQ_ID_RECORD_AUDIO);
		// 	} else {
		// 		fHasPermissions = false;
		// 		showLongToast("No permission for " + Manifest.permission.POST_NOTIFICATIONS);
		// 	}
		if (fPERMISSION_REQ_ID_RECORD_AUDIO == requestCode) {
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, fPERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE)) {
					fHasPermissions = true;
					startRecordingScreen();
				}
			} else {
				fHasPermissions = false;
				showLongToast("No permission for " + Manifest.permission.RECORD_AUDIO);
			}
		} else if (fPERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE == requestCode) {
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				fHasPermissions = true;
				startRecordingScreen();
			} else {
				fHasPermissions = false;
				showLongToast("No permission for " + Manifest.permission.WRITE_EXTERNAL_STORAGE);
			}
		}
	}

	/**
	 * Called after the Corona runtime has been created and just before executing the "main.lua" file.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that has just been loaded/initialized.
	 *                Provides a LuaState object that allows the application to extend the Lua API.
	 */
	@Override
	public void onLoaded(CoronaRuntime runtime) {
		// Note that this method will not be called the first time a Corona activity has been launched.
		// This is because this listener cannot be added to the CoronaEnvironment until after
		// this plugin has been required-in by Lua, which occurs after the onLoaded() event.
		// However, this method will be called when a 2nd Corona activity has been created.

	}

	/**
	 * Called just after the Corona runtime has executed the "main.lua" file.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that has just been started.
	 */
	@Override
	public void onStarted(CoronaRuntime runtime) {
	}

	/**
	 * Called just after the Corona runtime has been suspended which pauses all rendering, audio, timers,
	 * and other Corona related operations. This can happen when another Android activity (ie: window) has
	 * been displayed, when the screen has been powered off, or when the screen lock is shown.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that has just been suspended.
	 */
	@Override
	public void onSuspended(CoronaRuntime runtime) {
	}

	/**
	 * Called just after the Corona runtime has been resumed after a suspend.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that has just been resumed.
	 */
	@Override
	public void onResumed(CoronaRuntime runtime) {
	}

	/**
	 * Called just before the Corona runtime terminates.
	 * <p>
	 * This happens when the Corona activity is being destroyed which happens when the user presses the Back button
	 * on the activity, when the native.requestExit() method is called in Lua, or when the activity's finish()
	 * method is called. This does not mean that the application is exiting.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that is being terminated.
	 */
	@Override
	public void onExiting(CoronaRuntime runtime) {
		// Remove the Lua listener reference.
		CoronaLua.deleteRef( runtime.getLuaState(), fListener );
		fListener = CoronaLua.REFNIL;
	}

	@Override
	public void HBRecorderOnStart() {
		Log.d("Corona", "HBRecorderOnStart");
		dispatchEvent(false, "");
	}

	@Override
	public void HBRecorderOnComplete() {
		Log.d("Corona", "HBRecorderOnComplete");
		//Update gallery depending on SDK Level
		if (fRecorder.wasUriSet()) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
				updateGalleryUri();
			} else {
				refreshGalleryFile();
			}
		}else{
			refreshGalleryFile();
		}
	}

	@Override
	public void HBRecorderOnError(int errorCode, String reason) {
		Log.d("Corona", "HBRecorderOnError: code = " + errorCode + " reason = " + reason);
		dispatchEvent(true, reason );
	}

	@Override
	public void HBRecorderOnPause() {
		Log.d("Corona", "HBRecorderOnPause");
	}

	@Override
	public void HBRecorderOnResume() {
		Log.d("Corona", "HBRecorderOnResume");
	}

	private void refreshGalleryFile() {
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity == null) {
			return;
		}
		MediaScannerConnection.scanFile(activity,
				new String[]{fRecorder.getFilePath()}, null,
				new MediaScannerConnection.OnScanCompletedListener() {
					public void onScanCompleted(String path, Uri uri) {
						Log.i("Corona", "Scanned " + path + ":");
						Log.i("Corona", "-> uri=" + uri);
					}
				});
	}

	//Generate a timestamp to be used as a file name
	private String generateFileName() {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());
		Date curDate = new Date(System.currentTimeMillis());
		return formatter.format(curDate).replace(" ", "");
	}

	ContentResolver resolver;
	ContentValues contentValues;
	Uri mUri;
	private void setOutputPath() {
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity == null) {
			return;
		}
		String filename = generateFileName();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			resolver = activity.getContentResolver();
			contentValues = new ContentValues();
			contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies");
			contentValues.put(MediaStore.Video.Media.TITLE, filename);
			contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
			contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
			mUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues);
			//FILE NAME SHOULD BE THE SAME
			fRecorder.setFileName(filename);
			fRecorder.setOutputUri(mUri);
		}else{
			// createFolder();
			fRecorder.setOutputPath(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getPath());
		}
	}

	@RequiresApi(api = Build.VERSION_CODES.Q)
	private void updateGalleryUri(){
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity == null) {
			return;
		}
		contentValues.clear();
		contentValues.put(MediaStore.Video.Media.IS_PENDING, 0);
		activity.getContentResolver().update(mUri, contentValues, null, null);
	}

	/**
	 * Simple example on how to dispatch events to Lua. Note that events are dispatched with
	 * Runtime dispatcher. It ensures that Lua is accessed on it's thread to avoid race conditions
	 * @param message simple string to sent to Lua in 'message' field.
	 */
	@SuppressWarnings("unused")
	public void dispatchEvent(boolean isError, final String message) {
		CoronaEnvironment.getCoronaActivity().getRuntimeTaskDispatcher().send( new CoronaRuntimeTask() {
			@Override
			public void executeUsing(CoronaRuntime runtime) {
				LuaState L = runtime.getLuaState();

				CoronaLua.newEvent( L, EVENT_NAME );

				L.pushBoolean(isError);
				L.setField(-2, "isError");
				L.pushString(message);
				L.setField(-2, "errorMessage");

				try {
					CoronaLua.dispatchEvent( L, fListener, 0 );
				} catch (Exception ignored) {
				}
			}
		} );
	}

	/**
	 * The following Lua function has been called:  library.init( listener )
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param L Reference to the Lua state that the Lua function was called from.
	 * @return Returns the number of values to be returned by the library.init() function.
	 */
	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int init(LuaState L) {
		int listenerIndex = 1;

		if ( CoronaLua.isListener( L, listenerIndex, EVENT_NAME ) ) {
			fListener = CoronaLua.newRef( L, listenerIndex );
		}

		return 0;
	}

	//Show Toast
	private void showLongToast(final String msg) {
		Log.d("Corona", "showLongToast: " + msg);
		Toast.makeText(CoronaEnvironment.getApplicationContext(), msg, Toast.LENGTH_LONG).show();
	}

	private boolean checkSelfPermission(String permission, int requestCode) {
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity == null) {
			return false;
		}
		if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(activity, new String[]{permission}, requestCode);
			return false;
		}
		return true;
	}

	private void startRecordingScreen() {
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity == null) {
			return;
		}
		quickSettings();
		MediaProjectionManager mediaProjectionManager = (MediaProjectionManager)activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
		Intent permissionIntent = mediaProjectionManager != null ? mediaProjectionManager.createScreenCaptureIntent() : null;
		activity.startActivityForResult(permissionIntent, fSCREEN_RECORD_REQUEST_CODE);
	}

	@SuppressWarnings("WeakerAccess")
	public int start(LuaState L) {
		int listenerIndex = 1;
		if ( CoronaLua.isListener( L, listenerIndex, EVENT_NAME ) ) {
			fListener = CoronaLua.newRef( L, listenerIndex );
		}

		//first check if permissions was granted
		// if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
		// 	if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS, fPERMISSION_REQ_POST_NOTIFICATIONS) && checkSelfPermission(Manifest.permission.RECORD_AUDIO, fPERMISSION_REQ_ID_RECORD_AUDIO)) {
		// 		fHasPermissions = true;
		// 	}
		// }
		// else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			if (checkSelfPermission(Manifest.permission.RECORD_AUDIO, fPERMISSION_REQ_ID_RECORD_AUDIO)) {
				fHasPermissions = true;
			}
		} else {
			if (checkSelfPermission(Manifest.permission.RECORD_AUDIO, fPERMISSION_REQ_ID_RECORD_AUDIO) && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, fPERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE)) {
				fHasPermissions = true;
			}
		}

		Log.d("Corona", "start:hasPermissions = " + fHasPermissions);
		if (fHasPermissions) {
			//check if recording is in progress
			//and stop it if it is
			if (fRecorder.isBusyRecording()) {
				Log.d("Corona", "start:isBusyRecording");
				fRecorder.stopScreenRecording();
			}
			//else start recording
			else {
				startRecordingScreen();
			}
		}

		return 0;
	}

	@SuppressWarnings("WeakerAccess")
	public int stop(LuaState L) {
		fRecorder.stopScreenRecording();

		return 0;
	}

	/** Implements the library.init() Lua function. */
	@SuppressWarnings("unused")
	private class InitWrapper implements NamedJavaFunction {
		/**
		 * Gets the name of the Lua function as it would appear in the Lua script.
		 * @return Returns the name of the custom Lua function.
		 */
		@Override
		public String getName() {
			return "init";
		}
		
		/**
		 * This method is called when the Lua function is called.
		 * <p>
		 * Warning! This method is not called on the main UI thread.
		 * @param L Reference to the Lua state.
		 *                 Needed to retrieve the Lua function's parameters and to return values back to Lua.
		 * @return Returns the number of values to be returned by the Lua function.
		 */
		@Override
		public int invoke(LuaState L) {
			return init(L);
		}
	}

	@SuppressWarnings("unused")
	private class StartWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "start";
		}

		@Override
		public int invoke(LuaState L) {
			return start(L);
		}
	}

	@SuppressWarnings("unused")
	private class StopWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "stop";
		}

		@Override
		public int invoke(LuaState L) {
			return stop(L);
		}
	}
}
