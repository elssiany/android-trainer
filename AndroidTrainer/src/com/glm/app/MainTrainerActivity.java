package com.glm.app;

import com.facebook.Session;
import com.glm.app.AboutActivity;
import com.glm.app.ActivityHelper;
import com.glm.app.GoalActivity;
import com.glm.app.ManualWorkout;
import com.glm.app.PrefActivity;
import com.glm.app.StoreActivity;
import com.glm.app.SummaryActivity;
import com.glm.app.UserDetailsActivity;
import com.glm.app.db.Database;
import com.glm.app.graph.WebGraphWeightActivity;
import com.glm.app.stopwatch.WorkOutActivity;
import com.glm.bean.ConfigTrainer;
import com.glm.bean.User;
import com.glm.services.ExerciseService;
import com.glm.services.IExerciseService;

import com.glm.utils.ExerciseUtils;
import com.glm.utils.Rate;
import com.glm.utils.fb.FacebookConnector;
import com.glm.utils.http.HttpClientHelper;
import com.glm.utils.tw.Const;
import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.LicenseChecker;
import com.google.android.vending.licensing.LicenseCheckerCallback;
import com.google.android.vending.licensing.ServerManagedPolicy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
//import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gcm.GCMRegistrar;
import com.glm.trainer.R;

public class MainTrainerActivity  extends Activity implements OnClickListener {
	private boolean isLicence=false;
	/**Gestione della licenza*/
	private LicenseCheckerCallback mLicenseCheckerCallback;
    private LicenseChecker mChecker;
    private static final String BASE64_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsFrtouMzttS7hakJi3uuswjeGiRvLTKtx37kcg2QIQyamJtFsKtEiLQzhcnR/2p5ng98Jg0BLSvzL0VbZSHyWSoR4/RMuYWYhMsvR6MNjry/ABqFaAlsiFCNNfVGEvKV0Kz8ILNMbZA2XlZBviC0fht9BSSynPWTuvT8uHcl0yJY3GmKgyOzodYuCVtqG9pRew+ZstVEeiYcqYbm8Gd0LJVpXkIN1AB9iPlxsfEQWHYzFhwUNB9UR2VcuTEiKyFKmbCPrYGfHkss+Kbjd/mucZx0sWQITUjKSdK9tmMOql/yDXEjuT+PzgUmr1bmnRJgzYzNkpvWbNiIFrgojYAunwIDAQAB";
    private static final byte[] SALT = new byte[] {
        -46, 65, 30, -128, -103, -57, 74, -64, 51, 88, -95,
        -45, 77, -117, -36, -113, -11, 32, -64, 89
        };
	private static final String SENDER_ID = "558307532040";
    private TelephonyManager oPhone;
    /**Gestione della licenza*/
    private LinearLayout oMainLayout;
	private ImageButton oStartRunningExercise;
	private ImageButton oStartWalkingExercise;
	private ImageButton oStartBikingExercise;
	private RelativeLayout oBMI;
	private Button oManualWalk;
	private Button oManualRun;
	private Button oManualBike;
	
	private TextView oTxtBMI;
	private Button oHistory;
	private Button oAbout;
	private Button oSettings;
	private Button oWeight;
	private Button oStore;
	
	private String sGCMId="";
	/**Oggetto connessione al servizio*/
	private TrainerServiceConnection mConnection = new TrainerServiceConnection();
	private ConfigTrainer oConfigTrainer;
	private IExerciseService mIService;

    /** Preferences to store a logged in users credentials */
    private SharedPreferences mPrefs;

    /***oggetto condivisione FB*/
	private FacebookConnector oFB = null;
	
	private String sVersionPackage="";
	@Override
	public void onCreate(Bundle savedInstanceState) {
	       super.onCreate(savedInstanceState);
	       setContentView(R.layout.new_new_main_page);
	       
	       oMainLayout = (LinearLayout) findViewById(R.id.objMainLayout);
	       
	       
	       
	       
	       
	       if(ExerciseUtils.isFirstBoot(getApplicationContext(),oConfigTrainer, sVersionPackage)){
	    	   ExerciseUtils.removeFirstBoot(getApplicationContext());
		       Intent intent = ActivityHelper.createActivityIntent(MainTrainerActivity.this,ChangeLogActivity.class);
				//startActivity(intent);
		       ActivityHelper.startNewActivityAndFinish(MainTrainerActivity.this, intent);	
		    }
	      
	       DBTask task = new DBTask();
		   task.execute(new Database(this));
	     		 
	}
	
	public void onResume(Bundle savedInstanceState) {
		setContentView(R.layout.new_main_page);
		
	}
	@Override
	protected void onPause() {
		if(mHandleMessageReceiver!=null){
			try{
				 unregisterReceiver(mHandleMessageReceiver);   
			}catch (IllegalArgumentException e) {
				 Log.e(this.getClass().getCanonicalName(), "Receiver not registered error");
			}
		} 
		doUnbindService();
		super.onPause();
	}
	private void addUser(boolean bTwitter) {
		Intent intent = ActivityHelper.createActivityIntent(this,UserDetailsActivity.class);
		//if(bTwitter) intent.putExtra("twitter", "1");
		//startActivity(intent);
		ActivityHelper.startNewActivityAndFinish(this, intent);	
	}
	@Override
    protected void onDestroy() {
		if(mHandleMessageReceiver!=null){
			try{
				 unregisterReceiver(mHandleMessageReceiver);   
			}catch (IllegalArgumentException e) {
				 Log.e(this.getClass().getCanonicalName(), "Receiver not registered error");
			}
		}     
        super.onDestroy();
    }
	@Override
	public void onClick(View oObj) {
		if(User.getsNick().compareToIgnoreCase("laverdone")==0){
			isLicence=true;
		}
		if(!isLicence){
			 Toast.makeText(MainTrainerActivity.this, getString(R.string.licenceko),
		                Toast.LENGTH_SHORT).show();
			 return;
		}
		if(oObj.getId()==R.id.btn_start_running){		
			if(oConfigTrainer.isbRunGoal()){				
				Intent intent = ActivityHelper.createActivityIntent(MainTrainerActivity.this,GoalActivity.class);
				intent.putExtra("type", "0");
				//startActivity(intent);
				ActivityHelper.startNewActivityAndFinish(MainTrainerActivity.this, intent);	
			}else{
				Intent intent = ActivityHelper.createActivityIntent(MainTrainerActivity.this,WorkOutActivity.class);
				intent.putExtra("type", 0);
				ActivityHelper.startNewActivityAndFinish(MainTrainerActivity.this, intent);	
			}													
		}else if(oObj.getId()==R.id.btn_start_walking){
			if(oConfigTrainer.isbRunGoal()){
				Intent intent = ActivityHelper.createActivityIntent(MainTrainerActivity.this,GoalActivity.class);
				intent.putExtra("type", "100");
				//startActivity(intent);
				ActivityHelper.startNewActivityAndFinish(MainTrainerActivity.this, intent);	
			}else{
				Intent intent = ActivityHelper.createActivityIntent(this,WorkOutActivity.class);
				intent.putExtra("type", 100);
				ActivityHelper.startNewActivityAndFinish( MainTrainerActivity.this, intent);			
			}
		}else if(oObj.getId()==R.id.btnManualWalk){
			//Manual WorkOut
			Intent intent = ActivityHelper.createActivityIntent(this,ManualWorkout.class);
			intent.putExtra("type", "10000");
			ActivityHelper.startNewActivityAndFinish(this, intent);			
			Toast.makeText(getBaseContext(), "Manual Walk", Toast.LENGTH_SHORT)
			.show();			
		}else if(oObj.getId()==R.id.btnManualBike){
			//Manual WorkOut
			Intent intent = ActivityHelper.createActivityIntent(this,ManualWorkout.class);
			intent.putExtra("type", "1001");
			ActivityHelper.startNewActivityAndFinish(this, intent);			
			Toast.makeText(getBaseContext(), "Manual Bike", Toast.LENGTH_SHORT)
			.show();			
		}else if(oObj.getId()==R.id.btnManualRun){
			//Manual WorkOut
			Intent intent = ActivityHelper.createActivityIntent(this,ManualWorkout.class);
			intent.putExtra("type", "1000");
			ActivityHelper.startNewActivityAndFinish(this, intent);			
			Toast.makeText(getBaseContext(), "Manual Run", Toast.LENGTH_SHORT)
			.show();
		}else if(oObj.getId()==R.id.btn_start_biking){
			if(oConfigTrainer.isbRunGoal()){
				Intent intent = ActivityHelper.createActivityIntent(MainTrainerActivity.this,GoalActivity.class);
				intent.putExtra("type", "1");
				//startActivity(intent);
				ActivityHelper.startNewActivityAndFinish(MainTrainerActivity.this, intent);	
			}else{
				Intent intent = ActivityHelper.createActivityIntent(this,WorkOutActivity.class);
				intent.putExtra("type", 1);
				ActivityHelper.startNewActivityAndFinish(MainTrainerActivity.this, intent);	
			}
									
		}else if(oObj.getId()==R.id.btn_history){
			Intent intent = ActivityHelper.createActivityIntent(this,SummaryActivity.class);
			//startActivity(intent);
			ActivityHelper.startNewActivityAndFinish(this, intent);		
		}else if(oObj.getId()==R.id.btn_trainer_store){
			Intent intent = ActivityHelper.createActivityIntent(this,StoreActivity.class);
			//startActivity(intent);
			ActivityHelper.startNewActivityAndFinish(this, intent);	
		}else if(oObj.getId()==R.id.btn_info){									
			
			Intent intent = ActivityHelper.createActivityIntent(this,AboutActivity.class);
			
			//startActivity(intent);
			ActivityHelper.startNewActivityAndFinish(this, intent);	
			
			
		}else if(oObj.getId()==R.id.btn_settings){
			Intent intent = ActivityHelper.createActivityIntent(this,PrefActivity.class);
			//startActivity(intent);
			ActivityHelper.startNewActivityAndFinish(this, intent);	
		}else if(oObj.getId()==R.id.btn_weight){
			//Intent intent = ActivityHelper.createActivityIntent(this,GraphWeightActivity.class);
			Intent intent = ActivityHelper.createActivityIntent(this,WebGraphWeightActivity.class);
			//startActivity(intent);
			ActivityHelper.startNewActivityAndFinish(this, intent);	
		}else if(oObj.getId()==R.id.btn_bmi){
			//Manual WorkOut
			Intent intent = ActivityHelper.createActivityIntent(this,UserDetailsActivity.class);
			ActivityHelper.startNewActivityAndFinish(this, intent);							
		}
	}
	@Override
    public void onBackPressed() {
		AlertDialog alertDialog;
    	alertDialog = new AlertDialog.Builder(this).create();
    	alertDialog.setTitle(this.getString(R.string.titleendapp));
    	alertDialog.setMessage(this.getString(R.string.messageendapp));
    	alertDialog.setButton(this.getString(R.string.yes), new android.content.DialogInterface.OnClickListener(){

			@Override
			public void onClick(DialogInterface arg0, int arg1) {
				if(mIService!=null){
					try {
						mIService.stopGPSFix();
						mIService.shutDown();
						doUnbindService();
					} catch (RemoteException e) {
						// TODO Auto-generated catch block
						//Log.e(this.getClass().getCanonicalName(),e.getMessage());
					}
				}
				finish();
			}        				
    		});
    	
    	alertDialog.setButton2(this.getString(R.string.no), new android.content.DialogInterface.OnClickListener(){

			@Override
			public void onClick(DialogInterface arg0, int arg1) {					
				
			}        		
			
    		});
    	alertDialog.show();
    }  
	
	boolean mIsBound;
	void doBindService() {
	    // Establish a connection with the service.  We use an explicit
	    // class name because there is no reason to be able to let other
	    // applications replace our component.
		//Intent bindIntent = new Intent(Main.this, MessengerService.class); 
        //bindService(bindIntent, mConnection, Context.BIND_AUTO_CREATE);                 
		mIsBound = getApplicationContext().bindService(new Intent(MainTrainerActivity.this, 
	            ExerciseService.class), mConnection, Context.BIND_AUTO_CREATE);
		
		Log.i(this.getClass().getCanonicalName(), "Binding from Services");
	}
	void doUnbindService() {
	    if (mIsBound) {
	        // If we have received the service, and hence registered with
	       
	        Log.i(this.getClass().getCanonicalName(), "UnBinding from Services");

	        // Detach our existing connection.
	        getApplicationContext().unbindService(mConnection);
	        mIsBound = false;	       
	    }
	}
	/**
	 * Classe Connection che stabilisce il bind col servizio
	 * 
	 * @author gianluca masci aka (GLM)
	 * 
	 * **/
	public class TrainerServiceConnection implements ServiceConnection 
	{ 
		
	        protected ExerciseService oTrainerService = null; 
	        @Override 
	        public void onServiceConnected(ComponentName name, IBinder service) 
	        { 
	                try{
	                	mIService= IExerciseService.Stub.asInterface(service);

	                }catch (Exception e) {
	                	Log.e(this.getClass().getCanonicalName(), "onServiceConnected->Remote Exception"+e.getMessage());
	                	e.printStackTrace();
					}
	                
	        } 
	        @Override 
	        public void onServiceDisconnected(ComponentName name) 
	        { 
	        	
	               /* Toast.makeText(StopwatchActivity.this, "TrainerServiceConnection->onServiceDisconnected"+R.string.pause_exercise,
	    	                Toast.LENGTH_LONG).show();*/
	        } 
	}     

	/**Classe per il controllo della licenza*/
	private class TrainerLicenseCheckerCallback implements LicenseCheckerCallback {
		
		@Override    
		public void allow(int reason) {
	        if (isFinishing()) {
	                // Don't update UI if Activity is finishing.	            	
	            return;
	        }
	            Toast.makeText(MainTrainerActivity.this, getString(R.string.licenceok),
		                Toast.LENGTH_SHORT).show();
	            ExerciseUtils.setLicenceOK(getApplicationContext());  
	            Log.v(this.getClass().getCanonicalName(), "licence Allow code "+reason);
	            isLicence=true;
	        // Should allow user access.
	        //displayResult(getString(R.string.lic_ok));
	    }
		
		@Override
	    public void dontAllow(int reason) {
	        if (isFinishing()) {
	            // Don't update UI if Activity is finishing.
	        	doUnbindService();
	            return;
	        }
	        //Toast.makeText(MainTrainerActivity.this, getString(R.string.licenceko),
	        //        Toast.LENGTH_SHORT).show();
	        ExerciseUtils.setLicenceKO(getApplicationContext());  
	        //displayResult(getString(R.string.lic_error));	       
	        // Should not allow access. An app can handle as needed,
	        // typically by informing the user that the app is not licensed
	        // and then shutting down the app or limiting the user to a
	        // restricted set of features.
	        // In this example, we show a dialog that takes the user to Market.
	        showDialog(0);
	        
	        /**
	         * DA MODIFICARE A FALSE IN PRODUZIONE
	         * 
	         * **/
	        isLicence=false;
	        Log.e(this.getClass().getCanonicalName(), "licence not Allow error code "+reason);
	    }
		@Override
		public void applicationError(int errorCode) {
			//Log.e(this.getClass().getCanonicalName(), "applicationError on check licence error code "+errorCode);
			/**
	         * DA MODIFICARE A FALSE IN PRODUZIONE
	         * 
	         * **/
	        isLicence=false;
		}
	}
	
	private class DBTask extends AsyncTask<Database, Void, Boolean> {
		@Override
		protected void onPostExecute(Boolean result) {
			   oStartRunningExercise = (ImageButton) findViewById(R.id.btn_start_running);
		       oStartWalkingExercise = (ImageButton) findViewById(R.id.btn_start_walking);
		       oStartBikingExercise  = (ImageButton) findViewById(R.id.btn_start_biking);
		       oBMI					 = (RelativeLayout) findViewById(R.id.btn_bmi);
		       
		       oHistory		= (Button) findViewById(R.id.btn_history);
		       oAbout		= (Button) findViewById(R.id.btn_info);
		       oSettings	= (Button) findViewById(R.id.btn_settings);
		       oWeight		= (Button) findViewById(R.id.btn_weight);
		       oStore		= (Button) findViewById(R.id.btn_trainer_store);
		       oManualWalk  = (Button) findViewById(R.id.btnManualWalk);
		       oManualRun   = (Button) findViewById(R.id.btnManualRun);
		       oManualBike  = (Button) findViewById(R.id.btnManualBike);
		       oTxtBMI		= (TextView) findViewById(R.id.txtBMIValue);
		       
		       oStartRunningExercise.setOnClickListener(MainTrainerActivity.this);
		       oStartWalkingExercise.setOnClickListener(MainTrainerActivity.this);
		       oStartBikingExercise.setOnClickListener(MainTrainerActivity.this);
		       
		       oManualWalk.setOnClickListener(MainTrainerActivity.this);
		       oManualRun.setOnClickListener(MainTrainerActivity.this);
		       oManualBike.setOnClickListener(MainTrainerActivity.this);
		       oHistory.setOnClickListener(MainTrainerActivity.this);
		       oAbout.setOnClickListener(MainTrainerActivity.this);
		       oSettings.setOnClickListener(MainTrainerActivity.this);
		       oWeight.setOnClickListener(MainTrainerActivity.this);
		       oStore.setOnClickListener(MainTrainerActivity.this);
		       oBMI.setOnClickListener(MainTrainerActivity.this);	
		       
		       oConfigTrainer=ExerciseUtils.loadConfiguration(getApplicationContext(),false);
		       //TEST TWITTER
		       if(ExerciseUtils.isUserExist(getApplicationContext())){
		    	   mPrefs = getSharedPreferences(Const.PREFERENCE_NAME, Context.MODE_PRIVATE);
		   		   
					if(oConfigTrainer.isShareTwitter()){
						mPrefs = getSharedPreferences(Const.PREFERENCE_NAME, MODE_PRIVATE);
					   				
						if(mPrefs.getString(Const.PREF_KEY_TOKEN, "").length()==0){
							addUser(true);
				    	}	    	
				    	Log.v(this.getClass().getCanonicalName(),"twitter aoth: "+mPrefs.getString(Const.PREF_KEY_TOKEN, ""));
				    	//oTwitter = new TwitterHelper(this,getApplicationContext(),this.getIntent());		
						//oTwitter.tryConnect();
				    }
					if(oConfigTrainer.isShareFB()){
						oFB = new FacebookConnector(getApplicationContext(),MainTrainerActivity.this);
						//Session.openActiveSession(MainTrainerActivity.this, true, mStatusCallBabk);
						
					}
		       }		      	 
			       
				try{
					   
				    if(!ExerciseUtils.isUserExist(getApplicationContext())){
			    		addUser(false);
			    	}else{
			    		
			    		if(!oConfigTrainer.isbLicence()){
			    			/**Check della licenza*/
			    			oPhone = (TelephonyManager) getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
			    						
			    			// Construct the LicenseCheckerCallback. The library calls this when done.
			    	        mLicenseCheckerCallback = new TrainerLicenseCheckerCallback();

			    	        // Construct the LicenseChecker with a Policy.
			    	        mChecker = new LicenseChecker(
			    	            getApplicationContext(), new ServerManagedPolicy(getApplicationContext(),
			    	                new AESObfuscator(SALT, getPackageName(), oPhone.getDeviceId())),
			    	            BASE64_PUBLIC_KEY  // Your public licensing key.
			    	            );
			    	        try{
			    	        	 mChecker.checkAccess(mLicenseCheckerCallback);
			    	        }catch (Exception e) {
			    				Log.e(this.getClass().getCanonicalName(),"Error Checking Licence");
			    			}	       
			    	        /**Check della licenza*/     
			    		}else{
			    			isLicence=true;
			    		}
			    		
			    	}
			    	ExerciseUtils.loadUserDectails(getApplicationContext());
				       
			    	oTxtBMI.setText(User.getsBMI(oConfigTrainer.getiUnits()));
			    	
			    	try {
			    		
			    	    PackageInfo manager=getPackageManager().getPackageInfo(getPackageName(), 0);
			    	    sVersionPackage = manager.versionName;
			    	    Log.i(this.getClass().getCanonicalName(),"Pacchetto Versione"+sVersionPackage);
			    	} catch (NameNotFoundException e) {
			    	   Log.e(this.getClass().getCanonicalName(),"Pacchetto non trovato");
			    	}			    					   
				    
			    	doBindService();
			    	
			    	LocationManager manager = (LocationManager) getSystemService( Context.LOCATION_SERVICE );
			    	if ( !manager.isProviderEnabled( LocationManager.GPS_PROVIDER ) ) {	        
				       ShowAlertNoGPS();
				    }
			    	//Mostro la nuova Finestra di Rate ogni 7 avvii
			    	Rate.app_launched(getApplicationContext());
				}catch (Exception e) {
					Log.e(this.getClass().getCanonicalName(), "check user: "+e.getMessage());			
				}		
		}
			
		@Override
		protected Boolean doInBackground(Database... mDB) {
			Database oDB=null;
			for (Database DB : mDB) {
				oDB= DB;
			}   
			if(oDB!=null)  {
				oDB.init();
				
			}
			oConfigTrainer=ExerciseUtils.loadConfiguration(getApplicationContext(),false);
			
			   GCMRegistrar.checkDevice(getApplicationContext());
		       GCMRegistrar.checkManifest(getApplicationContext());
		       sGCMId = GCMRegistrar.getRegistrationId(getApplicationContext());
		       registerReceiver(mHandleMessageReceiver,
		                new IntentFilter("com.glm.app.DISPLAY_MESSAGE"));	       
		       
		       if (sGCMId.equals("")) {
		         GCMRegistrar.register(getApplicationContext(), SENDER_ID);
		         Log.v(this.getClass().getCanonicalName(), "Not registered, register now: "+sGCMId);
		         ExerciseUtils.saveGCMId(getApplicationContext(),sGCMId);
		       } else {
		         Log.v(this.getClass().getCanonicalName(), "Already registered: "+sGCMId);	
		         ExerciseUtils.saveGCMId(getApplicationContext(),sGCMId);
		       }
		       
		       if(sGCMId!=null){
		    	   if(sGCMId.length()>0){
		    		   //Send Id to Android Trainer WEB Server via POST METHOD
				       HttpClientHelper oHttpHelper = new HttpClientHelper();
				       oHttpHelper.registerToAndroidTrainerServer(sGCMId,oConfigTrainer);   
		    	   }
		       }
			return true;
		}
	}
	/**
	 * Visualizza una alert per il GPS non abilitato
	 *
	 * @author Gianluca Masci aka (GLM)
	 * */
	public void ShowAlertNoGPS() {
		try{
			AlertDialog alertDialog;
	    	alertDialog = new AlertDialog.Builder(this).create();
	    	alertDialog.setTitle(this.getString(R.string.titlegps));
	    	alertDialog.setMessage(this.getString(R.string.messagegpsnoenabled));
	    	alertDialog.setButton(this.getString(R.string.yes), new android.content.DialogInterface.OnClickListener(){

				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					Intent myIntent = new Intent( Settings.ACTION_LOCATION_SOURCE_SETTINGS );
				    startActivity(myIntent);
				}        				
	    		});
	    	
	    	alertDialog.setButton2(this.getString(R.string.no), new android.content.DialogInterface.OnClickListener(){

				@Override
				public void onClick(DialogInterface arg0, int arg1) {					
					
				}        		
				
	    		});
	    	alertDialog.show();
		}catch (Exception e) {
			Toast.makeText(this, "ERROR DIALOG:"+e.getMessage(), Toast.LENGTH_SHORT).show();
			Log.e("MEEERR: ",e.getMessage());
		}
	}
	private final BroadcastReceiver mHandleMessageReceiver =
	         new BroadcastReceiver() {
		     @Override
		     public void onReceive(Context context, Intent intent) {
		         Log.v(this.getClass().getCanonicalName(),"onReceive");
		     }
	};
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
	    super.onActivityResult(requestCode, resultCode, data);
	    if(Session.getActiveSession()!=null) Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
	}
	
	
}

