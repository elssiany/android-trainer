package com.glm.utils;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Vector;

import com.glm.app.HistoryActivity;
import com.glm.app.db.Database;
import com.glm.bean.ConfigTrainer;
import com.glm.bean.DistancePerExercise;
import com.glm.bean.DistancePerMonth;
import com.glm.bean.Exercise;
import com.glm.bean.ExerciseManipulate;
import com.glm.bean.Music;
import com.glm.bean.NewExercise;
import com.glm.bean.Summary;
import com.glm.bean.User;
import com.glm.bean.WatchPoint;
import com.glm.trainer.R;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

/**
 * Contiene tutti i metodi che mi serviranno per calcolare tutte le metriche
 * durante un esercizio 
 * */
public class ExerciseUtils {
	private static final String FOLDER_PERSONAL_TRAINER="personal_trainer";
	private static final double GPSFIX=1;//0.88;
	public static String sExportFile="";
	private static Vector<Music> vListOfMusic = new Vector<Music>();
	private static int iTypeExercise=0;
	private final static double MILES_TO_KM=0.621371192;
	private final static double I_BURN_RUNNING=0.9;
	private final static double I_BURN_WALKING=0.45;
	private final static double I_BURN_BIKING=0.105;
	/***
	 * Calcola la distanza a partire da un paio di coordinate,
	 * la distanza pu essere calcolata in K (Kilometri) M (Miles) N (Nautical Miles)
	 * 
	 * @param double dLatStart
	 * @param double dLongStart
	 * @param double dLatEnd
	 * @param double dLongEnd
	 * @param String sUnit  K (Kilometri) M (Miles) N (Nautical Miles)
	 * 
	 * @author Gianluca Masci aka (GLM)
	 * */
	private static double distance(double dLatStart, double dLongStart, 
			double dLatEnd, double dLongEnd, String sUnit) {
		double dDistance=0;
		
		try{
			if(dLatStart==0.0 ||
					dLongStart==0.0){
				return 0;
			}
			/*=(
					ARCCOS(
							COS(RADIANTI(90-B2)) *
							COS(RADIANTI(90-B3)) +
							SEN(RADIANTI(90-B2)) *
							SEN(RADIANTI(90-B3)) *
							COS(RADIANTI(A2-A3))
							) 
							*6371)*/
			dDistance=(Math.acos(Math.cos(Math.toRadians(90-dLatStart)) *
				Math.cos(Math.toRadians(90-dLatEnd)) +
				Math.sin(Math.toRadians(90-dLatStart)) *
				Math.sin(Math.toRadians(90-dLatEnd)) *
				Math.cos(Math.toRadians(dLongStart-dLongEnd)))*6371)*1000;
			
			/*Location.distanceBetween(dLatStart, 
					dLongStart, 
					dLatEnd, 
					dLongEnd, fResult);
				*/
			//Distance in Meter
			//dDistance=Double.parseDouble(String.valueOf(fResult[0]));
			
			  if (sUnit == "M") { // Miglie
				  dDistance = (dDistance * 3.2808399)*0.000189393939;
			  }else if(sUnit=="K"){
				  dDistance=dDistance/1000; 
			  }
			  //writeCustomLoForDebug(dLatStart, dLongStart, 
			//			 dLatEnd,  dLongEnd, dDistance, sUnit);
		    return dDistance;
		}catch(NumberFormatException e){
			return 0;
		}
		
	}


		/**
		 * Crea nel DB il nuovo Esercizio per poi essere finalizzato dal servizio
		 * 
		 * @param context 
		 * @param int iTypeOfTrainer 
		 * */
		public int createNewExercise(Context context, ConfigTrainer oConfigTrainer, int iTypeOfTrainer, float fWeight) {
			Database oDB = new Database(context);
			oDB.open();
			SQLiteDatabase oDataBase = oDB.getOpenedDatabase();
			iTypeExercise=iTypeOfTrainer;
			if(Float.isInfinite(fWeight)){
				fWeight=0;
			}
			oDataBase.execSQL("INSERT INTO trainer_exercise (id_users, id_type_exercise, weight) VALUES ("+oConfigTrainer.getiUserID()+","+iTypeOfTrainer+","+fWeight+")");
			oDB.close();
			int iIDExercise=0;
			String sSQL_CURRENT_EXERCISE = "SELECT MAX(id_exercise) as exercise FROM trainer_exercise";
			
			//Log.d(ExerciseUtils.class.getCanonicalName(), sSQL_DISTANCE_EXERCISE);
			//Log.d(ExerciseUtils.class.getCanonicalName(), sSQL_END_DETT_EXERCISE);	
			
			oDB.open();
		    
			Cursor oCursor = oDB.rawQuery(sSQL_CURRENT_EXERCISE,null);
			if(oCursor!=null){      		
			   		int iCurrentExercise = oCursor.getColumnIndex("exercise");
		   			
		   			
			   		while(oCursor.moveToNext()){ 
			   			//Coordinate di avvio
			   			iIDExercise=oCursor.getInt(iCurrentExercise);	
			   				
			   		}
			   		oCursor.close();
			}
			
			oDB.close();
			
			oDB=null;
			return iIDExercise;
		}
		
		/**
		 * Salvo le Coordinate Long. e Lat. nel DB per l'esercizio corrente
		 * la tabella di riferimento e' trainer_exercise_dett
		 * @param lTime 
		 * @param fAccurancy  Accuratezza del FIX GPS
		 * @param fSpeed velocità in m/s 
		 * @param iHeartRate 
		 * @param string sSong canzone in ascolto
		 * 
		 * @see ExerciseService
		 * TODO Fisso l'utente e il tipo esercizio
		 * **/
		public synchronized static void saveCoordinates(Context oContext, ConfigTrainer oConfigTrainer,double pre_latitude, double pre_longitude, double latitude, double longitude, double altitude, String name, 
				String sSong, float fSpeed, float fAccurancy, long lTime, int iHeartRate){
			Database oDB = new Database(oContext);
			if(sSong==null) sSong="";
			try{
				//Aggiorno il record precedente salvando il current time del record che poi inseerirò
				/*String sSQL_UPDATE_PREV_WATCH_POINT="UPDATE trainer_exercise_dett SET watch_point_date_prev=datetime('now') WHERE " +
						"id_watch_point=(select max(id_watch_point) from TRAINER_EXERCISE_DETT WHERE id_exercise IN (SELECT MAX(id_exercise) FROM trainer_exercise))";
				07-17 18:49:14.180: ERROR/Database(13375): Failure 1 (9 values for 8 columns) on 0x24d948 when preparing 'INSERT INTO trainer_exercise_dett ( id_exercise, id_type_exercise, id_users, long, lat, alt, watch_point_name, watch_point_date_prev) VALUES ( (SELECT MAX(id_exercise) FROM trainer_exercise) , (SELECT id_type_exercise FROM trainer_exercise WHERE id_exercise IN (SELECT MAX(id_exercise) FROM trainer_exercise)), 1 ,41.87707722187042, 12.43702232837677, 99.0, '',(select watch_point_date from trainer_exercise_dett where id_watch_point IN (select max(id_watch_point) from TRAINER_EXERCISE_DETT WHERE id_exercise IN (SELECT MAX(id_exercise) FROM trainer_exercise))),'sex')'.

				oDB.open();
				oDB.getOpenedDatabase().execSQL(sSQL_UPDATE_PREV_WATCH_POINT);
				oDB.close();*/
				//Non salvo le coordinate con velocità a 0
								
				String sSQL_INSERT_DETT_EXERCISE = "INSERT INTO trainer_exercise_dett ( " +
					"id_exercise, id_type_exercise, id_users, lat, long, alt, distance, watch_point_name, watch_point_date_prev,watch_point_song,speed,accurancy,gpstime,bpm) VALUES (" +
					" (SELECT MAX(id_exercise) FROM trainer_exercise) ,"+ 
					" (SELECT id_type_exercise FROM trainer_exercise WHERE id_exercise IN (SELECT MAX(id_exercise) FROM trainer_exercise)),"+
				  	" (select max(id_users) from TRAINER_USERS) ,"+latitude+", "+longitude+", "+altitude+", "+
				  	distance(pre_latitude, pre_longitude, latitude, longitude, "K")+", '"+name+"',(select watch_point_date from trainer_exercise_dett where id_watch_point IN (select max(id_watch_point) from TRAINER_EXERCISE_DETT WHERE id_exercise IN (SELECT MAX(id_exercise) FROM trainer_exercise)))," +
				  	"'"+sSong.replaceAll("'", "''")+"',"+fSpeed+","+fAccurancy+","+lTime+","+iHeartRate+")";			 
				
				//Log.i(ExerciseUtils.class.getCanonicalName(), sSQL_INSERT_DETT_EXERCISE);				  	
				oDB.open();
				oDB.getOpenedDatabase().execSQL(sSQL_INSERT_DETT_EXERCISE);
				oDB.close();
				oDB=null;	
			}catch (Exception e) {
				Log.e(ExerciseUtils.class.getCanonicalName(),"Error saving dett");
				if(oDB!=null) oDB.close();
				oDB=null;
			}
                          
    }
	/**
	 * Ritorna la distanza totale di un esercizio a partire dal suo ID
	 * legge dalla tabella trainer_exercise_dett
	 * 
	 * @see trainer_exercise_dett
	 * @see HistoryActivity
	 * 
	 * @param boolean includeMetric include o no la metrica nel testo
	 * @param Context 
	 * @param String sExerciseID id dell'esercizio da cui prendere la distanza
	 * */	
	public synchronized static String getTotalDistanceFormattated(double fDistanceunFormatted, ConfigTrainer oConfigTrainer,boolean includeMetric){
		String sDistance="";
		NumberFormat oNFormat = NumberFormat.getNumberInstance();
		oNFormat.setMaximumFractionDigits(2);
		sDistance = oNFormat.format(fDistanceunFormatted);
		//Log.i(ExerciseUtils.class.getCanonicalName(), "Distance Formattated MEASURE: "+ sDistance);
		if(includeMetric){
			if(oConfigTrainer.getiUnits()==0){
				return sDistance+" Km";
			}else{
				return sDistance+" Miles";
			}	
		}else{
			return sDistance;
		}
			
	}
	/**
	 * Ritorna la distanza totale di un esercizio a partire dal suo ID
	 * legge dalla tabella trainer_exercise_dett
	 * 
	 * @see trainer_exercise_dett
	 * @see HistoryActivity
	 * 
	 * @param Context 
	 * @param String sExerciseID id dell'esercizio da cui prendere la distanza
	 * @return int distanza in metri
	 * */	
	public synchronized static float getTotalDistanceUnFormattated(Context oContext, ConfigTrainer oConfigTrainer, String sExerciseID, String sDefaultUnit){
		float fDistance=0;
		
		Database oDB = new Database(oContext);
		double dDistance=0;
		Cursor oCursor;
		try{
			String sSQL_DISTANCE_EXERCISE = "SELECT case when id_type_exercise < 100 then sum(distance)*"+GPSFIX+" else sum(distance) end as distance FROM trainer_exercise_dett WHERE id_exercise ='" +sExerciseID+"'";
			
			//Log.d(ExerciseUtils.class.getCanonicalName(), sSQL_DISTANCE_EXERCISE);
			//Log.d(ExerciseUtils.class.getCanonicalName(), sSQL_END_DETT_EXERCISE);	
			
			oDB.open();
		    
			oCursor = oDB.rawQuery(sSQL_DISTANCE_EXERCISE,null);
			if(oCursor!=null){      		
			   		int iSumDistance = oCursor.getColumnIndex("distance");
		   			
		   			
			   		while(oCursor.moveToNext()){ 
			   			//Coordinate di avvio
			   			fDistance=oCursor.getFloat(iSumDistance);	
			   			//Log.d(ExerciseUtils.class.getCanonicalName(), "fDistance= "+fDistance);	
			   		}
			   		oCursor.close();
			}
			
			oDB.close();
			oDB=null;
			
			if(oConfigTrainer==null){
				dDistance=fDistance;
			}else{
				if(oConfigTrainer.getiUnits()==1){			
					//Converto i KM in milies
					dDistance = (fDistance * MILES_TO_KM);
					//Log.d(ExerciseUtils.class.getCanonicalName(), "MILES: "+fDistance+" Post: "+dDistance);
				}else{
					dDistance=fDistance;
					//Log.d(ExerciseUtils.class.getCanonicalName(), "KILOMETERS: "+fDistance+" Post: "+dDistance);
				}
			}
			
			//Calcolo la distanza in Metri per la velocit media
			if(sDefaultUnit!=null 
					&& sDefaultUnit=="MT"){
				dDistance=fDistance*1000;
				//Log.d(ExerciseUtils.class.getCanonicalName(), "METRES:"+fDistance+" Post: "+dDistance);
			}
			//Log.d(ExerciseUtils.class.getCanonicalName(), "Distance Total MEASURE: "+fDistance);
		}catch (Exception e) {
			if(oDB!=null) oDB.close();
			oDB=null;
		}
		
		return (float) dDistance;
	}
	
	/**
	 * Ritorna la distanza parziale degli ultimi due watch_point di un esercizio a partire dal suo ID
	 * legge dalla tabella trainer_exercise_dett
	 * 
	 * @see trainer_exercise_dett
	 * @see HistoryActivity
	 * 
	 * @param Context 
	 * @param String sExerciseID id dell'esercizio da cui prendere la distanza
	 * @return int distanza in metri
	 * */	
	public synchronized static double getPartialDistanceUnFormattated(double dLongStart,double dLatStart,double dLongEnd,double dLatEnd){
		double dDistance=0;
		//Salvo sempre la distanza in KM
		String sUnits="K";
		/*Database oDB = new Database(oContext);
		double dLongStart=0,dLatStart=0,dLongEnd=0,dLatEnd=0;
		
		String sSQL_START_DETT_EXERCISE = "SELECT long, lat, alt, id_watch_point FROM trainer_exercise_dett WHERE id_exercise ='" +sExerciseID+"'" +
				" AND id_watch_point = (SELECT MAX(id_watch_point)-1 FROM trainer_exercise_dett WHERE id_exercise ='" +sExerciseID+"')";
		String sSQL_END_DETT_EXERCISE = "SELECT long, lat, alt, id_watch_point FROM trainer_exercise_dett WHERE id_exercise ='" +sExerciseID+"'" +
		" AND id_watch_point = (SELECT MAX(id_watch_point) FROM trainer_exercise_dett WHERE id_exercise ='" +sExerciseID+"')";
		
		Log.d(ExerciseUtils.class.getCanonicalName(), sSQL_START_DETT_EXERCISE);	
		Log.d(ExerciseUtils.class.getCanonicalName(), sSQL_END_DETT_EXERCISE);	
		
		oDB.open();
		Cursor oCursor = oDB.rawQuery(sSQL_START_DETT_EXERCISE,null);
		if(oCursor!=null){      		
		   		int iLong = oCursor.getColumnIndex("long");
	   			int iLat = oCursor.getColumnIndex("lat");
	   			
	   			int iWatchPointName = oCursor.getColumnIndex("id_watch_point");
	   			
		   		while(oCursor.moveToNext()){ 
		   			//Coordinate di avvio
		   			dLongStart=oCursor.getDouble(iLong);
		   			dLatStart=oCursor.getDouble(iLat);
		   			Log.d(ExerciseUtils.class.getCanonicalName(), oCursor.getDouble(iWatchPointName)+" Long/Lat START: "+ oCursor.getDouble(iLong) + " - "+oCursor.getDouble(iLat));
		   		}
		   		oCursor.close();
		}
		
		oCursor = oDB.rawQuery(sSQL_END_DETT_EXERCISE,null);
		if(oCursor!=null){      		
			
		   		int iLong = oCursor.getColumnIndex("long");
	   			int iLat = oCursor.getColumnIndex("lat");
	   			
	   			int iWatchPointName = oCursor.getColumnIndex("id_watch_point");
	   			
		   		while(oCursor.moveToNext()){ 
		   			//Coordinate di avvio
		   			dLongEnd=oCursor.getDouble(iLong);
		   			dLatEnd=oCursor.getDouble(iLat);
		   			Log.d(ExerciseUtils.class.getCanonicalName(), oCursor.getDouble(iWatchPointName)+" Long/Lat END: "+ oCursor.getDouble(iLong) + " - "+oCursor.getDouble(iLat));
		   		}
		   		oCursor.close();
		}
					  	
		
		
		oDB.close();
		oDB=null;
		*/
		
		//iDistance = Integer.parseInt(String.valueOf(Math.round(distance(dLongStart,dLatStart,dLongEnd,dLatEnd,"K")*1000)));
		if(dLongStart==0 && dLatStart==0){
			dDistance=0;
		}else{
			dDistance=distance(dLongStart,dLatStart,dLongEnd,dLatEnd,sUnits);
		}
		//Log.d(ExerciseUtils.class.getCanonicalName(), "Distance UnFormattated: "+ fDistance);
		
		//TODO WRITE DATA TI FILE FOR CHECK
		return dDistance;
	}
	
	/**
	 * Cancella un esercizio a partire dal suo ID
	 * legge dalla tabella trainer_exercise_dett
	 * 
	 * @see trainer_exercise_dett
	 * @see HistoryActivity
	 * 
	 * @param Context 
	 * @param String sExerciseID id dell'esercizio da cui prendere la distanza
	 * */	
	public synchronized static boolean deleteExercise(Context oContext, ConfigTrainer oConfigTrainer, String sExerciseID){
		Database oDB = new Database(oContext);
		try{			
			String sSQL_DELETE_DETT_EXERCISE = "DELETE FROM trainer_exercise_dett WHERE id_exercise ='" +sExerciseID+"'";
			String sSQL_DELETE_EXERCISE = "DELETE FROM trainer_exercise WHERE id_exercise ='" +sExerciseID+"'";
			oDB.open();
			oDB.getOpenedDatabase().execSQL(sSQL_DELETE_EXERCISE);
			oDB.getOpenedDatabase().execSQL(sSQL_DELETE_DETT_EXERCISE);
			oDB.close();
			oDB=null;			
		}catch (Exception e) {
			Log.e(ExerciseUtils.class.getCanonicalName(), e.getMessage());
			if(oDB!=null) oDB.close();
			oDB=null;
			return false;
		}
		
		return true;
	}
	/***
	 * salva l'esercizio inserendo la data di fine
	 * @param iStep 
	 * 
	 * **/
	public synchronized static boolean saveExercise(Context oContext, ConfigTrainer oConfigTrainer, int iStep) {		
		String sSQL_SAVE_EXERCISE ="";
		int iIDExercise=0;
		//Log.v(ExerciseUtils.class.getCanonicalName(), "Save EXERICE ");
		//Uso la populate per salvare le calorie e il tempo???
		//Prelevo l'id dell'esercizio che devo salvare
		Database oDB = new Database(oContext);
		try{
			
			
			sSQL_SAVE_EXERCISE = "UPDATE trainer_exercise SET end_date = CURRENT_TIMESTAMP, steps_count="+iStep+", distance=(select sum(distance) from trainer_exercise_dett WHERE id_exercise =(SELECT MAX(id_exercise) as exercise FROM trainer_exercise)),  " +
					"avg_speed='"+ExerciseUtils.getVelocitaMedia(oContext)+"'," +
					"kalories=(select ROUND((case when id_type_exercise = 0 then ("+I_BURN_RUNNING+"*sum(distance))*"+oConfigTrainer.getiWeight()+" when id_type_exercise = 1 then ("+I_BURN_BIKING+"*sum(distance))*"+oConfigTrainer.getiWeight()+" when id_type_exercise = 100 then ("+I_BURN_WALKING+"*sum(distance))*"+oConfigTrainer.getiWeight()+" end),2) as kalories from trainer_exercise_dett WHERE id_exercise =(SELECT MAX(id_exercise) as exercise FROM trainer_exercise)), " +
					"calorie_burn=(select ROUND((case when id_type_exercise = 0 then ("+I_BURN_RUNNING+"*sum(distance))*"+oConfigTrainer.getiWeight()+" when id_type_exercise = 1 then ("+I_BURN_BIKING+"*sum(distance))*"+oConfigTrainer.getiWeight()+" when id_type_exercise = 100 then ("+I_BURN_WALKING+"*sum(distance))*"+oConfigTrainer.getiWeight()+" end),2) as kalories from trainer_exercise_dett WHERE id_exercise =(SELECT MAX(id_exercise) as exercise FROM trainer_exercise))|| ' Kal', " +					
					"total_time=(select (strftime(\"%H\",strftime(\"%J\",CURRENT_TIMESTAMP)-strftime(\"%J\",start_date))-12)||strftime(\":%M:%S\",strftime(\"%J\",CURRENT_TIMESTAMP)-strftime(\"%J\",start_date)) from trainer_exercise where id_exercise=(SELECT MAX(id_exercise) as exercise FROM trainer_exercise)) " +				
					"  WHERE id_exercise =(SELECT MAX(id_exercise) as exercise FROM trainer_exercise)";
			
			oDB.open();
			oDB.getOpenedDatabase().execSQL(sSQL_SAVE_EXERCISE);
			oDB.close();
			
			/*iIDExercise=getLastExercise(oContext);
			//Popolo gli altri dettagli
			ExerciseUtils.populateExerciseDetails(oContext, oConfigTrainer, iIDExercise);
			
			sSQL_SAVE_EXERCISE = "UPDATE trainer_exercise SET total_time= '" +ExerciseManipulate.getsTotalTime() +
					" WHERE id_exercise =(SELECT MAX(id_exercise) as exercise FROM trainer_exercise)";
			
			oDB.open();
			oDB.getOpenedDatabase().execSQL(sSQL_SAVE_EXERCISE);
			oDB.close();*/
			//Cancello tutti gli esercizi sporchi in DB
			//String sSQL_PURGE_EXERCISE="delete from TRAINER_EXERCISE where (end_date is null) or (distance=0)";
			//oDB.open();
			//oDB.getOpenedDatabase().execSQL(sSQL_PURGE_EXERCISE);
			//oDB.close();
			
			oDB=null;			
			//Log.v(ExerciseUtils.class.getCanonicalName(), "Save SQL: "+sSQL_SAVE_EXERCISE);
		}catch (Exception e) {
			Toast.makeText(oContext, "Error Savind Exercise", Toast.LENGTH_LONG).show();
			Log.e(ExerciseUtils.class.getCanonicalName(), e.getMessage()+" - "+sSQL_SAVE_EXERCISE);
			if(oDB!=null) oDB.close();
			oDB=null;
			return false;
		}
		
		return true;

	}
	/**
	 * Carica la configurazione del Software da utilizzare in vari punti
	 * trainer_pref 
  	 *    id_pref 
     *    type VARCHAR(1)
     *    name VARCHAR(45) 
     *    desc VARCHAR(255)
     *    value VARCHAR(255) 
	 * TODO 
	 *    inserire le preferenze condivise così dovrebbe non leggere sempre dal DB
	 * 
	 * @see Stopwatchactivity, ExerciseService, PrefActivity
	 * @param Context oContext
	 * 
	 * @return ConfigTrainer
	 * */
	public synchronized static ConfigTrainer loadConfiguration(Context oContext, boolean bUsePrefs){
		ConfigTrainer oConfigTrainer = new ConfigTrainer();
		
		SharedPreferences oPrefs = oContext.getSharedPreferences("aTrainer",Context.MODE_PRIVATE);
		
		if(bUsePrefs){
			return loadConfigurationFromPrefs(oPrefs);
		}
		
		SharedPreferences.Editor editPrefs = oPrefs.edit();
		
				 
		try{
			Database oDB = new Database(oContext);
	    	oDB.open();       	
	    	Cursor oCursor = oDB.fetchAll("trainer_pref",null,null);
	    	if(oCursor!=null){        		
	    		int iKey = oCursor.getColumnIndex("name");
	            int iID_PREF =  oCursor.getColumnIndex("id_pref");
	    		/*int iType = oCursor.getColumnIndex("type");
	            int iName = oCursor.getColumnIndex("name");
	            int iDesc = oCursor.getColumnIndex("desc");*/
	            int iValue = oCursor.getColumnIndex("value");
	    		while(oCursor.moveToNext()){        			
	    			
	    				boolean bCheck=false;
	    				if(oCursor.getString(iValue).compareToIgnoreCase("1")==0) bCheck=true;
	    				
	    				if(oCursor.getInt(iID_PREF)==1){
	    					//1
	    					oConfigTrainer.setbDisplayMap(bCheck);
	    					editPrefs.putBoolean("maps", bCheck);    				       
	    					//Log.v(ExerciseUtils.class.getCanonicalName(),"setbDisplayMap: "+bCheck);
	    				}else if(oCursor.getInt(iID_PREF)==2){
	    					//2    					
	    					oConfigTrainer.setbDisplayNotification(bCheck);
	    					editPrefs.putBoolean("notification", bCheck);  
	    					//Log.v(ExerciseUtils.class.getCanonicalName(),"setbDisplayNotification: "+bCheck);
	    				}else if(oCursor.getInt(iID_PREF)==3){
	    					//3
	    					oConfigTrainer.setbPlayMusic(bCheck);
	    					editPrefs.putBoolean("music", bCheck);  
	    					//Log.v(ExerciseUtils.class.getCanonicalName(),"setbPlayMusic: "+bCheck);
	    				}else if(oCursor.getInt(iID_PREF)==4){
	    					//4
	    					oConfigTrainer.setbMotivator(bCheck);
	    					editPrefs.putBoolean("motivator", bCheck);  
	    					//Log.v(ExerciseUtils.class.getCanonicalName(),"setbMotivator: "+bCheck);
	    				}else if(oCursor.getInt(iID_PREF)==11){
	    					//11
	    					oConfigTrainer.setbAutoPause(bCheck);
	    					editPrefs.putBoolean("autopause", bCheck);  
	    					//Log.v(ExerciseUtils.class.getCanonicalName(),"setbAutoPause: "+bCheck);
	    				}else if(oCursor.getInt(iID_PREF)==13){
	    					//13
	    					oConfigTrainer.setbInteractiveExercise(bCheck);
	    					editPrefs.putBoolean("interactive", bCheck);  
	    					//Log.v(ExerciseUtils.class.getCanonicalName(),"setbInteractiveExercise: "+bCheck);
	    				}else if(oCursor.getInt(iID_PREF)==6){
	    					//6
	    					oConfigTrainer.setiMotivatorTime(Integer.parseInt(oCursor.getString(iValue)));
	    					editPrefs.putInt("motivator_time", Integer.parseInt(oCursor.getString(iValue)));  
	    				}else if(oCursor.getInt(iID_PREF)==16){
	    					//16
	    					oConfigTrainer.setiAutoPauseTime(Integer.parseInt(oCursor.getString(iValue)));
	    					editPrefs.putInt("autopause_time", Integer.parseInt(oCursor.getString(iValue)));  
	    				}else if(oCursor.getInt(iID_PREF)==7){
	    					//7
	    					oConfigTrainer.setSayDistance(bCheck);
	    					editPrefs.putBoolean("say_distance", bCheck); 
	    					//Log.v(ExerciseUtils.class.getCanonicalName(),"setSayDistance: "+bCheck);
	    				}else if(oCursor.getInt(iID_PREF)==8){
	    					//8
	    					oConfigTrainer.setSayTime(bCheck);
	    					editPrefs.putBoolean("say_time", bCheck); 
	    					//Log.v(ExerciseUtils.class.getCanonicalName(),"setSayTime: "+bCheck);
	    				}else if(oCursor.getInt(iID_PREF)==9){
	    					//9
	    					oConfigTrainer.setSayKalories(bCheck);
	    					editPrefs.putBoolean("say_kalories", bCheck); 
	    					//Log.v(ExerciseUtils.class.getCanonicalName(),"setSayKalories: "+bCheck);
	    				}else if(oCursor.getInt(iID_PREF)==10){
	    					//10
	    					oConfigTrainer.setSayPace(bCheck);
	    					editPrefs.putBoolean("say_pace", bCheck); 
	    					//Log.v(ExerciseUtils.class.getCanonicalName(),"setSayPace: "+bCheck);
	    				}else if(oCursor.getInt(iID_PREF)==17){
	    					//17
	    					oConfigTrainer.setSayInclination(bCheck);
	    					editPrefs.putBoolean("say_inclination", bCheck); 
	    					//Log.v(ExerciseUtils.class.getCanonicalName(),"setSayInclination: "+bCheck);
	    				}else if(oCursor.getInt(iID_PREF)==14){
	    					//14
	    					oConfigTrainer.setiUnits(Integer.parseInt(oCursor.getString(iValue)));
	    					editPrefs.putInt("units", Integer.parseInt(oCursor.getString(iValue)));  
	    				}else if(oCursor.getInt(iID_PREF)==15){
	    					//15
	    					oConfigTrainer.setbTrackExercise(bCheck);
	    					editPrefs.putBoolean("track_exercise", bCheck); 
	    					//Log.v(ExerciseUtils.class.getCanonicalName(),"setbTrackExercise: "+bCheck);
	    				}else if(oCursor.getInt(iID_PREF)==18){
	    					//18
	    					oConfigTrainer.setbRunGoal(bCheck);
	    					editPrefs.putBoolean("run_goal", bCheck); 
	    					//Log.v(ExerciseUtils.class.getCanonicalName(),"setbRunGoal: "+bCheck);
	    				}else if(oCursor.getInt(iID_PREF)==19){
	    					//19
	    					oConfigTrainer.setbUseCardio(bCheck);
	    					editPrefs.putBoolean("use_cardio", bCheck); 
	    					//Log.v(ExerciseUtils.class.getCanonicalName(),"setbRunGoal: "+bCheck);
	    				}else if(oCursor.getInt(iID_PREF)==20){
	    					//19
	    					oConfigTrainer.setVirtualRaceSupport(bCheck);
	    					editPrefs.putBoolean("virtualrace", bCheck); 
	    					//Log.v(ExerciseUtils.class.getCanonicalName(),"setbRunGoal: "+bCheck);
	    				}
	    				    				
	    			}              			
	    	}        		                 
	    	if(!oCursor.isClosed()) oCursor.close();
			//Carico anche i dati dello User
			oCursor = oDB.fetchAll("trainer_users",null,null);
	    	if(oCursor!=null){        		
	    	
	    		int iKey 	= oCursor.getColumnIndex("id_users");
	            int iNick 	= oCursor.getColumnIndex("nick");
	            int iName 	= oCursor.getColumnIndex("name");
	            int iWeight = oCursor.getColumnIndex("weight");
	            int iAge 	= oCursor.getColumnIndex("age");
	            int iFB 	= oCursor.getColumnIndex("facebook");
	            int iBuzz 	= oCursor.getColumnIndex("buzz");
	            int iTwitter = oCursor.getColumnIndex("twitter");
	            int iGender  = oCursor.getColumnIndex("gender");
	            int iHeight  = oCursor.getColumnIndex("height");
	            
	    		while(oCursor.moveToNext()){        			
	    			oConfigTrainer.setiUserID(oCursor.getInt(iKey));
	    			editPrefs.putInt("user_id", oCursor.getInt(iKey)); 
	    			
	    			oConfigTrainer.setsName(oCursor.getString(iName));
	    			editPrefs.putString("name", oCursor.getString(iName));
	    			
	    			oConfigTrainer.setsNick(oCursor.getString(iNick));
	    			editPrefs.putString("nick", oCursor.getString(iNick));
	    			
	    			oConfigTrainer.setiWeight(oCursor.getInt(iWeight));
	    			editPrefs.putInt("weight", oCursor.getInt(iWeight));
	    			
	    			oConfigTrainer.setiAge(oCursor.getInt(iAge));
	    			editPrefs.putInt("age", oCursor.getInt(iAge));
	    			
	    			oConfigTrainer.setiHeight(oCursor.getInt(iHeight));
	    			editPrefs.putInt("height", oCursor.getInt(iHeight));
	    			
	    			oConfigTrainer.setsGender(oCursor.getString(iGender));  
	    			editPrefs.putString("gender", oCursor.getString(iGender));
	    				if(oCursor.getString(iFB).compareToIgnoreCase("1")==0){
	    					oConfigTrainer.setShareFB(true);
	    					editPrefs.putBoolean("share_fb", true); 
	    				}else{    	
	    					oConfigTrainer.setShareFB(false);
	    					editPrefs.putBoolean("sharefb", false); 
	    				}
	    				if(oCursor.getString(iBuzz).compareToIgnoreCase("1")==0){
	    					oConfigTrainer.setShareBuzz(true);
	    					editPrefs.putBoolean("share_plus", true); 
	    				}else{
	    					oConfigTrainer.setShareBuzz(false);
	    					editPrefs.putBoolean("share_plus", false); 
	    				}
	    				if(oCursor.getString(iTwitter).compareToIgnoreCase("1")==0){
	    					oConfigTrainer.setShareTwitter(true);
	    					editPrefs.putBoolean("share_twitter", true); 
	    				}else{
	    					oConfigTrainer.setShareTwitter(false);
	    					editPrefs.putBoolean("share_twitter", false); 
	    				}
	    			}              			
	    	}    
	    	if(!oCursor.isClosed()) oCursor.close();
	    	oCursor = oDB.fetchAll("trainer_version",null,null);
	    	if(oCursor!=null){        	
	    		int iVersionNumber 	= oCursor.getColumnIndex("version_number");
	            int iVersionDesc 	= oCursor.getColumnIndex("version_desc");
	            int iLicence	 	= oCursor.getColumnIndex("licence");
	            
	    		while(oCursor.moveToNext()){   			
	    			if(oCursor.getString(iLicence).compareToIgnoreCase("S")==0){
	    				oConfigTrainer.setbLicence(true);
	    				editPrefs.putBoolean("licence", true); 
	    			}else{
	    				oConfigTrainer.setbLicence(false);
	    				editPrefs.putBoolean("licence", false); 
	    			}
	    			oConfigTrainer.setsVersionDesc(oCursor.getString(iVersionDesc));
	    			editPrefs.putString("version_desc", oCursor.getString(iVersionDesc));
	    			
	    			oConfigTrainer.setiVersionNumber(oCursor.getInt(iVersionNumber));
	    			editPrefs.putInt("version_number", oCursor.getInt(iVersionNumber));
	    		}
	    	}
	    	
	    	if(!oCursor.isClosed()) oCursor.close();
	    	oCursor = oDB.fetchAll("STORE_ORDERS"," product_id='polarcardio'",null);
	    	if(oCursor!=null){        	
	    		int iStoreID 	= oCursor.getColumnIndex("store_id");
	            
	    		while(oCursor.moveToNext()){   			
	    			oConfigTrainer.setbCardioPolarBuyed(true);
	    			editPrefs.putBoolean("cardio_polar_buyed", true); 
	    		}
	    	}
	    	
	    	editPrefs.commit();
	    	if(oCursor!=null) oCursor.close();
			if(oDB!=null) oDB.close();
			oCursor=null;
			oDB=null;
			return oConfigTrainer;
		}catch (NullPointerException e) {
			return null;
		}catch (RuntimeException e) {
			return null;
		}
		
		
	}
	/**
	 * Popolo l'oggetto ConfigTrainer dalle preferenze condivise, più veloce che sempre da SQLite
	 * @param oPrefs 
	 * */
	private static ConfigTrainer loadConfigurationFromPrefs(SharedPreferences oPrefs) {
		try{
			//Log.i(ExerciseUtils.class.getClass().getCanonicalName(),"Load Prefs from Shared");
			ConfigTrainer oConfigTrainer = new ConfigTrainer();
			oConfigTrainer.setbDisplayMap(oPrefs.getBoolean("maps", true));
			oConfigTrainer.setbDisplayNotification(oPrefs.getBoolean("notification", true));
			oConfigTrainer.setbPlayMusic(oPrefs.getBoolean("music", false));
			  
			oConfigTrainer.setbMotivator(oPrefs.getBoolean("motivator", true));
			  
			oConfigTrainer.setbAutoPause(oPrefs.getBoolean("autopause", false));
			  
			oConfigTrainer.setbInteractiveExercise(oPrefs.getBoolean("interactive", false));
			  
			oConfigTrainer.setiMotivatorTime(oPrefs.getInt("motivator_time", 1));
			 
			oConfigTrainer.setiAutoPauseTime(oPrefs.getInt("autopause_time", 1));
			  
			oConfigTrainer.setSayDistance(oPrefs.getBoolean("say_distance", true));
			 
			oConfigTrainer.setSayTime(oPrefs.getBoolean("say_time", true));
			 
			oConfigTrainer.setSayKalories(oPrefs.getBoolean("say_kalories", true));
			
			oConfigTrainer.setSayPace(oPrefs.getBoolean("say_pace", true));
			 
			oConfigTrainer.setSayInclination(oPrefs.getBoolean("say_inclination", true));
			 
			oConfigTrainer.setiUnits(oPrefs.getInt("units", 0));
			  
			oConfigTrainer.setbTrackExercise(oPrefs.getBoolean("track_exercise", false));
			 
			oConfigTrainer.setbRunGoal(oPrefs.getBoolean("run_goal", true));
			 
			oConfigTrainer.setbUseCardio(oPrefs.getBoolean("use_cardio", false));
			
			oConfigTrainer.setVirtualRaceSupport(oPrefs.getBoolean("virtualrace", false));
			

			oConfigTrainer.setiUserID(oPrefs.getInt("user_id", -2));
			

			oConfigTrainer.setsName(oPrefs.getString("name", ""));
			

			oConfigTrainer.setsNick(oPrefs.getString("nick", ""));

			oConfigTrainer.setiWeight(oPrefs.getInt("weight", 0));
			
			oConfigTrainer.setiHeight(oPrefs.getInt("height", 0));

			oConfigTrainer.setiAge(oPrefs.getInt("age", 0));

			oConfigTrainer.setsGender(oPrefs.getString("gender", "M"));  

			oConfigTrainer.setShareFB(oPrefs.getBoolean("share_fb", false));

			oConfigTrainer.setShareBuzz(oPrefs.getBoolean("share_plus", false));

			oConfigTrainer.setShareTwitter(oPrefs.getBoolean("share_twitter", false));


			oConfigTrainer.setbLicence(oPrefs.getBoolean("licence", false));


			oConfigTrainer.setsVersionDesc(oPrefs.getString("version_desc", ""));


			oConfigTrainer.setiVersionNumber(oPrefs.getInt("version_number", 1));


			oConfigTrainer.setbCardioPolarBuyed(oPrefs.getBoolean("cardio_polar_buyed", false));

			oConfigTrainer.setsGCMId(oPrefs.getString("GCMId", ""));
		    		
			return oConfigTrainer;
			
		}catch (NullPointerException e) {
			return null;
		}catch (RuntimeException e) {
			return null;
		}		
	}

	/**
	 * Aggiorna la preferenza SharedPreferences in vase all'id ricevuto
	 * */
	public synchronized static void updatePreference(Context oContext, int iID_Pref, int iSelected){
		SharedPreferences oPrefs = oContext.getSharedPreferences("aTrainer",Context.MODE_PRIVATE);
		SharedPreferences.Editor editPrefs = oPrefs.edit();
		boolean bCheck=false;
		if(iSelected==1) bCheck=true;
		//Log.v(ExerciseUtils.class.getCanonicalName(),"update share pref:"+iID_Pref+" select: "+iSelected);
		if(iID_Pref==1){
			//1			
			editPrefs.putBoolean("maps", bCheck);    				       
			//Log.v(ExerciseUtils.class.getCanonicalName(),"setbDisplayMap: "+bCheck);
		}else if(iID_Pref==2){
			//2    								
			editPrefs.putBoolean("notification", bCheck);  
			//Log.v(ExerciseUtils.class.getCanonicalName(),"setbDisplayNotification: "+bCheck);
		}else if(iID_Pref==3){
			//3			
			editPrefs.putBoolean("music", bCheck);  
			//Log.v(ExerciseUtils.class.getCanonicalName(),"setbPlayMusic: "+bCheck);
		}else if(iID_Pref==4){
			//4			
			editPrefs.putBoolean("motivator", bCheck);  
			//Log.v(ExerciseUtils.class.getCanonicalName(),"setbMotivator: "+bCheck);
		}else if(iID_Pref==11){
			//11			
			editPrefs.putBoolean("autopause", bCheck);  
			//Log.v(ExerciseUtils.class.getCanonicalName(),"setbAutoPause: "+bCheck);
		}else if(iID_Pref==13){
			//13			
			editPrefs.putBoolean("interactive", bCheck);  
			//Log.v(ExerciseUtils.class.getCanonicalName(),"setbInteractiveExercise: "+bCheck);
		}else if(iID_Pref==6){
			//6
			editPrefs.putInt("motivator_time", iSelected);  
			//Log.v(ExerciseUtils.class.getCanonicalName(),"motivator_time: "+iSelected);
		}else if(iID_Pref==16){
			//16			
			editPrefs.putInt("autopause_time", iSelected);  
		}else if(iID_Pref==7){
			//7			
			editPrefs.putBoolean("say_distance", bCheck); 
			//Log.v(ExerciseUtils.class.getCanonicalName(),"setSayDistance: "+bCheck);
		}else if(iID_Pref==8){
			//8			
			editPrefs.putBoolean("say_time", bCheck); 
			//Log.v(ExerciseUtils.class.getCanonicalName(),"setSayTime: "+bCheck);
		}else if(iID_Pref==9){
			//9			
			editPrefs.putBoolean("say_kalories", bCheck); 
			//Log.v(ExerciseUtils.class.getCanonicalName(),"setSayKalories: "+bCheck);
		}else if(iID_Pref==10){
			//10			
			editPrefs.putBoolean("say_pace", bCheck); 
			//Log.v(ExerciseUtils.class.getCanonicalName(),"setSayPace: "+bCheck);
		}else if(iID_Pref==17){
			//17		
			editPrefs.putBoolean("say_inclination", bCheck); 
			//Log.v(ExerciseUtils.class.getCanonicalName(),"setSayInclination: "+bCheck);
		}else if(iID_Pref==14){
			//14
			
			editPrefs.putInt("units", iSelected);  
		}else if(iID_Pref==15){
			//15
			
			editPrefs.putBoolean("track_exercise", bCheck); 
			//Log.v(ExerciseUtils.class.getCanonicalName(),"setbTrackExercise: "+bCheck);
		}else if(iID_Pref==18){
			//18
			
			editPrefs.putBoolean("run_goal", bCheck); 
			//Log.v(ExerciseUtils.class.getCanonicalName(),"setbRunGoal: "+bCheck);
		}else if(iID_Pref==19){
			//19			
			editPrefs.putBoolean("use_cardio", bCheck); 
			//Log.v(ExerciseUtils.class.getCanonicalName(),"setbRunGoal: "+bCheck);
		}
		editPrefs.commit();
	}
	/**
	 * Popola la classe statica ExercixeManipulate con i dati di dettaglio dell'esercizio 
	 * selezionato
	 * @param oContext 
	 * 
	 * @see ExercixeManipulate
	 * @TODO CALCOLARE LE CALORIE, LA VELOCITA' MEDIA E I PASSI
	 * */
	public synchronized static void populateExerciseDetails(Context oContext, ConfigTrainer oConfigTrainer,int iIDExercise) {
		ArrayList<WatchPoint> aWatchPoint = new ArrayList<WatchPoint>();
		
		ExerciseManipulate.setiIDExercise(iIDExercise);
		ExerciseManipulate.setsTotalDistance(
				getTotalDistanceFormattated(
						getTotalDistanceUnFormattated(oContext, oConfigTrainer, String.valueOf(iIDExercise),null), oConfigTrainer, true));
		
		ExerciseManipulate.setdTotalDistance(getTotalDistanceUnFormattated(oContext, oConfigTrainer, String.valueOf(iIDExercise),null));
		
		ExerciseManipulate.setfCurrentDistance(getTotalDistanceUnFormattated(oContext, oConfigTrainer, String.valueOf(iIDExercise),null));
		
		
		String sTotalTimeFormatted="";
		int iHours=0;
		int iMinutes=0;
		int iSeconds=0;
		int iTotSeconds=0;
		String sKalories="";
		String sUnit=" Km/h";
		String sMinutePerUnit=" Min/Km";
		if(oConfigTrainer.getiUnits()==1){
			sUnit=" Mi/h";
			sMinutePerUnit=" Min/Mi";
		}
		
		/**Cancello tutti i watch point i cui punti hanno come datediff <3**//*
		String sSQLPurge = "delete from TRAINER_EXERCISE_DETT where abs(((strftime('%S',watch_point_date)-strftime('%S',watch_point_date_prev))))<3";
		
		*//**
		 * Velocita' Media=Distanza/Tempo Trainer
		 * *//*
		
		Database oDB = new Database(oContext); 
		oDB.open();
		oDB.getOpenedDatabase().execSQL(sSQLPurge);		
		*//**Altra select di cancellazione watch_point errati*//*
		sSQLPurge = "DELETE FROM trainer_exercise_dett where (CASE WHEN abs(((strftime('%s',watch_point_date)-strftime('%s',watch_point_date_prev))/60))=60 then 0 else " +
			 " abs(((strftime('%s',watch_point_date)-strftime('%s',watch_point_date_prev))/60)) end) >0";	
		oDB.getOpenedDatabase().execSQL(sSQLPurge);		
		oDB.close();
		*/
		Database oDB = new Database(oContext);
		
		int iStepCount=0;
		String sSQLAVGSpeed = "SELECT steps_count, note ,id_type_exercise , abs(((strftime('%s',end_date)-strftime('%s',start_date))/3600)) as hours ,"+
												   "CASE WHEN abs(((strftime('%s',end_date)-strftime('%s',start_date))/60))>=60 then " +
												   " abs(((strftime('%s',end_date)-strftime('%s',start_date))/(60*(abs(((strftime('%s',end_date)-strftime('%s',start_date))/3600))))))-60 else "+
												   " abs(((strftime('%s',end_date)-strftime('%s',start_date))/60)) end as minutes, "+
												   "CASE WHEN abs(((strftime('%S',end_date)-strftime('%S',start_date))))>=60 then " +
												   " abs(((strftime('%S',end_date)-strftime('%S',start_date))))/60 else "+
												   " abs(((strftime('%S',end_date)-strftime('%S',start_date)))) end as seconds," +
												   " abs(((strftime('%s',end_date)-strftime('%s',start_date)))) as totSec, " +
												   " strftime('%d',start_date) as EDAY, " + 
												   " strftime('%m',start_date) as EMONTH, " + 
												   " strftime('%Y',start_date) as EYEAR, " + 
											       " strftime('%H',start_date) as EHOUR, " + 
												   " strftime('%M',start_date) as EMIN, " + 
												   " strftime('%S',start_date) as ESEC, " +
												   " kalories as kalories FROM trainer_exercise WHERE id_exercise='"+String.valueOf(iIDExercise)+"'";
		oDB.open();
		Cursor oCursor = oDB.rawQuery(sSQLAVGSpeed,null);
		if(oCursor!=null){      				   		
	   			int iTrainerHour 	= oCursor.getColumnIndex("hours");
	   			int iTrainerMinute 	= oCursor.getColumnIndex("minutes");
	   			int iTrainerSecond 	= oCursor.getColumnIndex("seconds");
	   			int iTotSecond 		= oCursor.getColumnIndex("totSec");
	   			int iSteps_Count 	= oCursor.getColumnIndex("steps_count");
	   			int iNote 			= oCursor.getColumnIndex("note");
	   			int iTypeTrainer 	= oCursor.getColumnIndex("id_type_exercise");
	   			int iKalories 		= oCursor.getColumnIndex("kalories");
	   			
	   			//DATA Avvio Esercizio
	   			int iEDay 		= oCursor.getColumnIndex("EDAY");
	   			int iEMonth 	= oCursor.getColumnIndex("EMONTH");	
	   			int iEYear 		= oCursor.getColumnIndex("EYEAR");
	   			
	   			int iEHour 		= oCursor.getColumnIndex("EHOUR");
	   			int iEMin 		= oCursor.getColumnIndex("EMIN");	
	   			int iESec 		= oCursor.getColumnIndex("ESEC");
	   			
	   			
		   		while(oCursor.moveToNext()){ 		   			
		   			
		   			iHours		=	oCursor.getInt(iTrainerHour);
		   			iMinutes	=	oCursor.getInt(iTrainerMinute);
		   			iSeconds	=	oCursor.getInt(iTrainerSecond);
		   			iTotSeconds =   oCursor.getInt(iTotSecond);
		   			sKalories	=	oCursor.getString(iKalories);
		   			
		   			sTotalTimeFormatted=String.format ("%d:%02d", oCursor.getInt(iTrainerHour), oCursor.getInt(iTrainerMinute))+String.format (":%02d", oCursor.getInt(iTrainerSecond));;
		   			ExerciseManipulate.setsNote(oCursor.getString(iNote));
		   			ExerciseManipulate.setiTypeExercise(oCursor.getInt(iTypeTrainer));
		   			ExerciseManipulate.setiTotalTimeInSeconds(iTotSeconds);
		   			////Log.v(ExerciseUtils.class.getCanonicalName(),"h: "+ oCursor.getInt(iTrainerHour)+" m: "+oCursor.getInt(iTrainerMinute)+" s:"+oCursor.getInt(iTrainerSecond));
		   			iStepCount=oCursor.getInt(iSteps_Count);
		   			
		   			try{
		   				DateFormat dfm = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
						Date oDateCurrent = dfm.parse(oCursor.getString(iEDay)+"-"+oCursor.getString(iEMonth)+"-"+oCursor.getString(iEYear)+" "+
											oCursor.getString(iEHour)+":"+oCursor.getString(iEMin)+":"+oCursor.getString(iESec));
						ExerciseManipulate.setdDateTimeStart(oDateCurrent);		
					
		   			}catch (ParseException e) {
		   				//Log.v(ExerciseUtils.class.getCanonicalName(),"Parse Date start error");
				   		
					}
		   			
		   		}
		   		oCursor.close();		   			 
		   		
		   		try{
		   			ExerciseManipulate.setsCurrentCalories(sKalories.substring(0, sKalories.indexOf(".")));
		   		}catch (StringIndexOutOfBoundsException e) {
		   			ExerciseManipulate.setsCurrentCalories(sKalories);
				}
		   		
		   		ExerciseManipulate.setsTotalTime(sTotalTimeFormatted);		   		
				try{
					NumberFormat oNFormat = NumberFormat.getNumberInstance();
					oNFormat.setMaximumFractionDigits(2);
					
					
					
					
					
					
					//double dDistance = getAVGSpeed(iHours,iMinutes,iSeconds, getTotalDistanceUnFormattated(oContext, 
					//		oConfigTrainer, 
					//		String.valueOf(iIDExercise),"MT"),  
					//		oConfigTrainer, oContext);
					
					double dSpeedKmHours = getAVGSpeed(iHours,iMinutes,iSeconds, getTotalDistanceUnFormattated(oContext, 
							null, 
							String.valueOf(iIDExercise),"MT"),  
							null, oContext);
					
					
					//6 minuti e 30 centesimi di minuto
					//double dDistanceMinMisure;
					//dDistanceMinMisure=(60/dDistance);
					
					oNFormat = NumberFormat.getNumberInstance();
					oNFormat.setMaximumFractionDigits(2);
					
					
					//centesimi di minuto
					/*double dMinute;
					dMinute=(dDistanceMinMisure-
							Integer.parseInt(String.valueOf(dDistanceMinMisure).substring(0,String.valueOf(dDistanceMinMisure).indexOf("."))))*60;
					*/
					//Log.v(ExerciseUtils.class.getCanonicalName(),"MINUS dDistanceMinMisure:" + dDistanceMinMisure+" - "+
							//Integer.parseInt(String.valueOf(dDistanceMinMisure).substring(0,String.valueOf(dDistanceMinMisure).indexOf("."))));
			   		
					/* 60: 9,52 = 6,30 min (cioe 6 minuti e 30 centesimi di minuto)

					                dal risultato si sottrae il numero intero dei minuti: 6,30  6 = 0,30 min;

					                si moltiplica per 60 il risultato: 0,30 x 60 = 18,0 sec (cioe 180)*/
					/*//Log.v("CAZZO: ", String.valueOf(getAVGSpeed(diffMill, getTotalDistanceUnFormattated(oContext, 
							oConfigTrainer, 
							String.valueOf(iIDExercise),"MT"),  
							oConfigTrainer, oContext)));*/
			
					
					/**
					 * Qua imposto le velocita medie massime e la medie e massime dei BPM
					 * 
					 * **/
					setMAXAVGOfSpeedBpm(oContext, oDB,iIDExercise,sUnit);
					
					ExerciseManipulate.setdAVGSpeed(dSpeedKmHours);
					
					ExerciseManipulate.setsMinutePerDistance(getAVGPace(oContext, oDB,iIDExercise)+sMinutePerUnit);
					ExerciseManipulate.setsMAXMinutePerDistance(getMAXPace(oContext, oDB,iIDExercise)+sMinutePerUnit);
					
					oNFormat = NumberFormat.getNumberInstance();
					oNFormat.setMaximumFractionDigits(0);
					oNFormat.setGroupingUsed(true);
					
					/**Aggiungo in numero dei passi**/
			   		ExerciseManipulate.setsStepCount(oNFormat.format(iStepCount));			   					   			   					   					   					   	
			   					   		
				}catch (Exception e) {
		   			ExerciseManipulate.setsAVGSpeed("n.d.");
		   			//e.printStackTrace();
		   			//Log.e(ExerciseUtils.class.getCanonicalName(),e.getMessage());
				}	
				
			/**SQL DI PULIZIA CON LA MEDIA CALCOLATA*/
			//sSQLPurge = "DELETE FROM trainer_exercise_dett where ((distance*3600)/abs(((strftime('%S',watch_point_date)-strftime('%S',watch_point_date_prev)))))>"
			//	+dMedia+" AND id_exercise='"+String.valueOf(iIDExercise)+"'";
			//select id_exercise,id_watch_point, min(watch_point_date), max(watch_point_date) from trainer_exercise_dett where id_exercise=9

			//oDB.getOpenedDatabase().execSQL(sSQLPurge);		
			
			/**Aggiugo tutti i watch point salvati all'esercizio*/
	   		/*String sSQLExerciseWatchPoint = "SELECT id_watch_point, long, lat, distance, alt, abs(((strftime('%s',watch_point_date)-strftime('%s',watch_point_date_prev))/3600)) as HOUR, " +
	   				"CASE WHEN abs(((strftime('%s',watch_point_date)-strftime('%s',watch_point_date_prev))/60))>=60 then " +
	   				"abs(((strftime('%s',watch_point_date)-strftime('%s',watch_point_date_prev))/60))-60 else " +
	   				" abs(((strftime('%s',watch_point_date)-strftime('%s',watch_point_date_prev))/60)) end as MIN ," +
	   				"CASE WHEN abs(((strftime('%S',watch_point_date)-strftime('%S',watch_point_date_prev))))=60 then 0 else "+
	   				" abs(((strftime('%S',watch_point_date)-strftime('%S',watch_point_date_prev)))) end as SEC, " +	   				
	   				" ((distance*3600)/abs(((strftime('%S',watch_point_date)-strftime('%S',watch_point_date_prev))))) as Vm, watch_point_song," +
	   				"strftime('%d',watch_point_date) as WDAY, " + 
					"strftime('%m',watch_point_date) as WMONTH, " + 
					"strftime('%Y',watch_point_date) as WYEAR, " + 
					"strftime('%H',watch_point_date) as WHOUR, " + 
					"strftime('%M',watch_point_date) as WMIN, " + 
					"strftime('%S',watch_point_date) as WSEC " +
	 		" FROM trainer_exercise_dett WHERE id_exercise='"+String.valueOf(iIDExercise)+"'";*/
	   		
			String sSQLExerciseWatchPoint = "SELECT id_watch_point, long, lat, distance, alt, abs(((strftime('%s',watch_point_date)-strftime('%s',watch_point_date_prev))/3600)) as HOUR, " +
	   				"CASE WHEN abs(((strftime('%s',watch_point_date)-strftime('%s',watch_point_date_prev))/60))>=60 then " +
	   				"abs(((strftime('%s',watch_point_date)-strftime('%s',watch_point_date_prev))/60))-60 else " +
	   				" abs(((strftime('%s',watch_point_date)-strftime('%s',watch_point_date_prev))/60)) end as MIN ," +
	   				"CASE WHEN abs(((strftime('%S',watch_point_date)-strftime('%S',watch_point_date_prev))))=60 then 0 else "+
	   				" abs(((strftime('%S',watch_point_date)-strftime('%S',watch_point_date_prev)))) end as SEC, " +	   				
	   				" (speed*3.6) as Vm, bpm, watch_point_song," +
	   				"strftime('%d',watch_point_date) as WDAY, " + 
					"strftime('%m',watch_point_date) as WMONTH, " + 
					"strftime('%Y',watch_point_date) as WYEAR, " + 
					"strftime('%H',watch_point_date) as WHOUR, " + 
					"strftime('%M',watch_point_date) as WMIN, " + 
					"strftime('%S',watch_point_date) as WSEC " +
	 		" FROM trainer_exercise_dett WHERE id_exercise='"+String.valueOf(iIDExercise)+"'";
	   		
			//oDB.open();
			oCursor = oDB.rawQuery(sSQLExerciseWatchPoint,null);

			NumberFormat oNFormat = NumberFormat.getNumberInstance();
			//TODO VERIFICARE CHE LE INFO SIANO CORRETTE
			
			if(oCursor!=null){  						
				double dPrevDistance=0.0;
				int iLong 		= oCursor.getColumnIndex("long");
	   			int iLat 		= oCursor.getColumnIndex("lat");
	   			int iAlt 		= oCursor.getColumnIndex("alt");
	   			int iDistance 	= oCursor.getColumnIndex("distance");
	   			//DATA WATCH POINT
	   			int iWDay 		= oCursor.getColumnIndex("WDAY");
	   			int iWMonth 	= oCursor.getColumnIndex("WMONTH");	
	   			int iWYear 		= oCursor.getColumnIndex("WYEAR");
	   			
	   			int iWHour 		= oCursor.getColumnIndex("WHOUR");
	   			int iWMin 		= oCursor.getColumnIndex("WMIN");	
	   			int iWSec 		= oCursor.getColumnIndex("WSEC");
	   			
	   			int iVm			= oCursor.getColumnIndex("Vm");
	   			int iBpm		= oCursor.getColumnIndex("bpm");
	   			int iSong		= oCursor.getColumnIndex("watch_point_song");
	   			
	   			double dCurrentDistance=0.0;
	   			////Log.v(ExerciseUtils.class.getCanonicalName(),"Exercise dett####################START "+ExerciseManipulate.getiIDExercise());
		   		while(oCursor.moveToNext()){ 			
		   			
		   			dCurrentDistance+=(oCursor.getDouble(iDistance)*1000);
		   			////Log.v(ExerciseUtils.class.getCanonicalName(),"Current Distance: \t"+Math.round(dCurrentDistance));
		   					   			
					oNFormat.setMaximumFractionDigits(2);					
					////Log.v(ExerciseUtils.class.getCanonicalName(),"Current Distance: \t"+oNFormat.format(dCurrentDistance).replace(",", ".")+"; - PrevDist: \t"+oNFormat.format(dPrevDistance).replace(",", ".")+"; = Sum: \t"+oNFormat.format((dCurrentDistance+dPrevDistance)).replace(",", ".")+";");		   			
		   			
					////Log.v(ExerciseUtils.class.getCanonicalName(),"Current Distance: \t"+Double.parseDouble(oNFormat.format(dCurrentDistance))+" - PrevDist: \t"+Double.parseDouble(oNFormat.format(dPrevDistance))+"= Sum: \t"+Double.parseDouble(oNFormat.format((dCurrentDistance+dPrevDistance))));		   			
		   			
					
		   			WatchPoint oWatchPoint = new WatchPoint();
		   			oWatchPoint.setdLong(oCursor.getDouble(iLong));
		   			oWatchPoint.setdLat(oCursor.getDouble(iLat));
		   			oWatchPoint.setdAlt(oCursor.getDouble(iAlt));
		   			oWatchPoint.setBpm(oCursor.getInt(iBpm));
		   			oWatchPoint.setsSong(oCursor.getString(iSong));
		   			//Metto la sistanza in metri
		   			//oWatchPoint.setdDistance(Double.parseDouble(oNFormat.format(dCurrentDistance).replace(",", ".")));
		   			oWatchPoint.setdDistance(Math.round(dCurrentDistance));
		   			//oWatchPoint.setdDateTime(new Date(oCursor.getString(iDate)));
		   			
		   			try {
		   				DateFormat dfm = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
						Date oDateCurrent = dfm.parse(oCursor.getString(iWDay)+"-"+oCursor.getString(iWMonth)+"-"+oCursor.getString(iWYear)+" "+
											oCursor.getString(iWHour)+":"+oCursor.getString(iWMin)+":"+oCursor.getString(iWSec));
						oWatchPoint.setdDateTime(oDateCurrent);		
						
						//oWatchPoint.setdPace(getAVGSpeed(diffMillCurrent, Float.valueOf(String.valueOf(oCursor.getDouble(iDistance)+dPrevDistance)),  
						//		oConfigTrainer, oContext));
						
						oWatchPoint.setdPace(oCursor.getDouble(iVm));									
										
						/*if(oCursor.getDouble(iVm)<dMedia){
							oWatchPoint.setdPace(oCursor.getDouble(iVm));	
						}else{
							oWatchPoint.setdPace(dMedia);
						}*/
						
						if(oConfigTrainer.getiUnits()==1){
							//Converto in Miglia
							oWatchPoint.setdPace(oWatchPoint.getdPace()*MILES_TO_KM);
						}
						
						////Log.v("PACE;",String.valueOf(dCurrentWatchPointDistance).replace(".", ",")+";"+oCursor.getInt(iHour)+":"+oCursor.getInt(iMin)+":"+oCursor.getInt(iSec)+";"+String.valueOf(dCurrentDistance).replace(".", ","));
						////Log.v(ExerciseUtils.class.getCanonicalName(),"diffMillCurrent "+oCursor.getString(iWP)+":"+diffMillCurrent+"; "+oWatchPoint.getdPace());
					} catch (ParseException e) {
						Log.e(ExerciseUtils.class.getCanonicalName(),"Error Parse"+e.getMessage());
						oWatchPoint.setdDateTime(null);
					}
		   			////Log.v(ExerciseUtils.class.getCanonicalName(),"Time WatchPoint: "+oCursor.getString(iDate));
		   			aWatchPoint.add(oWatchPoint);				   			
		   			//dPrevDistance=oWatchPoint.getdDistance();
		   			oWatchPoint=null;
	   			}
	   			dPrevDistance=dCurrentDistance+dPrevDistance;			   			
				//Log.v(ExerciseUtils.class.getCanonicalName(),"Exercise dett####################END "+ExerciseManipulate.getiIDExercise());
				oCursor.close();
		   		oDB.close();
		   		oCursor=null;
		   		oDB=null;
			}		
			ExerciseManipulate.setWatchPoint(aWatchPoint);
		}
		
	}
	
	/**
	 * Ritorna la velocita' media per un certo esercizio
	 * 
	 * @Context oContext
	 * @Database DB eventuale db aperto
	 * @int iIDExercise id dell'esercizio
	 * 
	 * */
	private static String getAVGSpeed(Context oContext, Database DB, int iIDExercise) {
		double dVM=0.0;
		Database oDB;
		if(DB==null){
			oDB = new Database(oContext);
		}else{
			oDB = DB;	
		}
		
		Cursor oCursor =null;
		NumberFormat oNFormat = NumberFormat.getNumberInstance();
		oNFormat.setMaximumFractionDigits(2);
		try{
			
			if(DB==null) oDB.open();
			
			String sSQL_CURRENT_VM = "select (sum(distance*1000)/(abs(((strftime('%s',min(watch_point_date))-strftime('%s',max(watch_point_date)))))))*3.6 as vm  from trainer_exercise_dett where id_exercise="+iIDExercise;
			oCursor = oDB.rawQuery(sSQL_CURRENT_VM,null);
			if(oCursor!=null){      		
			   		int iVM = oCursor.getColumnIndex("vm");
		   			
		   			
			   		while(oCursor.moveToNext()){ 
			   			//Coordinate di avvio
			   			dVM=oCursor.getDouble(iVM);	
			   			//Log.v(ExerciseUtils.class.getCanonicalName(),"Velocita' Media KM/H: "+dVM);
			   		}
			   		oCursor.close();
			}
			
			//Chiudo il DB solo se creato qua
			if(DB==null){
				oDB.close();				
				oDB=null;	
			}
			
			return oNFormat.format(dVM);
		}catch (SQLException e) {
			if(DB==null){
				if(oCursor!=null) oCursor.close();
				if(oDB!=null) oDB.close();
			}
			Log.e(ExerciseUtils.class.getCanonicalName(),"error calculate vm speed from DB");
			return oNFormat.format(0);
		}
	}

	/**
	 * Ritorna la velocita' media per un certo esercizio
	 * @param sUnit 
	 * 
	 * @Context oContext
	 * @Database DB eventuale db aperto
	 * @int iIDExercise id dell'esercizio
	 * 
	 * */
	private static void setMAXAVGOfSpeedBpm(Context oContext, Database DB, int iIDExercise, String sUnit) {
		double dMaxVM=0.0;
		double dAvgVM=0.0;
		int MaxBpm=0;
		int AvgBpm=0;
		Database oDB;
		if(DB==null){
			oDB = new Database(oContext);
		}else{
			oDB = DB;	
		}
		
		Cursor oCursor =null;
		NumberFormat oNFormat = NumberFormat.getNumberInstance();
		oNFormat.setMaximumFractionDigits(2);
		try{
			
			if(DB==null) oDB.open();
			
			String sSQL_CURRENT_VM = "select max(speed*3.6) as maxvm, (sum(distance*1000)/(abs(((strftime('%s',min(watch_point_date))-strftime('%s',max(watch_point_date)))))))*3.6 as avgvm, max(bpm) as maxbpm, avg(bpm) as avgbpm from trainer_exercise_dett where id_exercise="+iIDExercise;
			oCursor = oDB.rawQuery(sSQL_CURRENT_VM,null);
			if(oCursor!=null){      		
			   		int iMaxVM  = oCursor.getColumnIndex("maxvm");
			   		int iAvgVM  = oCursor.getColumnIndex("avgvm");
			   		int iMaxBpm = oCursor.getColumnIndex("maxbpm");
			   		int iAvgBpm = oCursor.getColumnIndex("avgbpm");
		   			
		   			
			   		while(oCursor.moveToNext()){ 
			   			//Coordinate di avvio
			   			dMaxVM=oCursor.getDouble(iMaxVM);	
			   			dAvgVM=oCursor.getDouble(iAvgVM);	
			   			MaxBpm=oCursor.getInt(iMaxBpm);	
			   			AvgBpm=oCursor.getInt(iAvgBpm);
			   			//Log.v(ExerciseUtils.class.getCanonicalName(),"Velocita' Media KM/H: "+dVM);
			   		}
			   		oCursor.close();
			}
			//Chiudo il DB solo se creato qua
			if(DB==null){
				oDB.close();				
				oDB=null;	
			}
			ExerciseManipulate.setsAVGSpeed(oNFormat.format(dAvgVM)+sUnit);
			ExerciseManipulate.setsMAXSpeed(oNFormat.format(dMaxVM)+sUnit);
			ExerciseManipulate.setiMAXBpm(MaxBpm);
			ExerciseManipulate.setiAVGBpm(AvgBpm);			
		}catch (SQLException e) {
			if(DB==null){
				if(oCursor!=null) oCursor.close();
				if(oDB!=null) oDB.close();
			}
			Log.e(ExerciseUtils.class.getCanonicalName(),"error calculate vm speed from DB");			
		}
	}
	/**
	 * Metodo privato che imposta le max avg di speed e bpm
	 * 
	 * **/
	private static String getMAXSpeed(Context oContext, Database DB, int iIDExercise){
		double dVM=0.0;
		Database oDB;
		if(DB==null){
			oDB = new Database(oContext);
		}else{
			oDB = DB;	
		}
		
		Cursor oCursor =null;
		NumberFormat oNFormat = NumberFormat.getNumberInstance();
		oNFormat.setMaximumFractionDigits(2);
		try{
			
			if(DB==null) oDB.open();
			
			String sSQL_CURRENT_VM = "select max(speed*3.6) as vm  from trainer_exercise_dett where id_exercise="+iIDExercise;
			oCursor = oDB.rawQuery(sSQL_CURRENT_VM,null);
			if(oCursor!=null){      		
			   		int iVM = oCursor.getColumnIndex("vm");
		   			
		   			
			   		while(oCursor.moveToNext()){ 
			   			//Coordinate di avvio
			   			dVM=oCursor.getDouble(iVM);	
			   			//Log.v(ExerciseUtils.class.getCanonicalName(),"Velocita' Media KM/H: "+dVM);
			   		}
			   		oCursor.close();
			}
			//Chiudo il DB solo se creato qua
			if(DB==null){
				oDB.close();				
				oDB=null;	
			}
			
			
			return oNFormat.format(dVM);
		}catch (SQLException e) {
			if(DB==null){
				if(oCursor!=null) oCursor.close();
				if(oDB!=null) oDB.close();
			}
			Log.e(ExerciseUtils.class.getCanonicalName(),"error calculate vm speed from DB");
			return oNFormat.format(0);
		}
		
	}
	
	
	/**
	 * Ritorna la velocita' media per un certo esercizio
	 * 
	 * @Context oContext
	 * @Database DB eventuale db aperto
	 * @int iIDExercise id dell'esercizio
	 * 
	 * */
	private static String getAVGPace(Context oContext, Database DB, int iIDExercise) {
		double dVM=0.0;
		Database oDB;
		if(DB==null){
			oDB = new Database(oContext);
		}else{
			oDB = DB;	
		}
		
		Cursor oCursor =null;
		NumberFormat oNFormat = NumberFormat.getNumberInstance();
		oNFormat.setMaximumFractionDigits(2);
		try{
			
			if(DB==null) oDB.open();
			
			String sSQL_CURRENT_VM = "select CAST((60/((sum(distance*1000)/(abs(((strftime('%s',min(watch_point_date))-strftime('%s',max(watch_point_date)))))))*3.6)) as INTEGER) ||\".\"|| CAST((((60/((sum(distance*1000)/(abs(((strftime('%s',min(watch_point_date))-strftime('%s',max(watch_point_date)))))))*3.6)) - CAST((60/((sum(distance*1000)/(abs(((strftime('%s',min(watch_point_date))-strftime('%s',max(watch_point_date)))))))*3.6)) as INTEGER))*60) as INTEGER) as pace  from trainer_exercise_dett where id_exercise="+iIDExercise;
			oCursor = oDB.rawQuery(sSQL_CURRENT_VM,null);
			if(oCursor!=null){      		
			   		int iPace = oCursor.getColumnIndex("pace");
		   			
		   			
			   		while(oCursor.moveToNext()){ 
			   			//Coordinate di avvio
			   			dVM=oCursor.getDouble(iPace);	
			   			//Log.v(ExerciseUtils.class.getCanonicalName(),"Velocita' Media KM/H: "+dVM);
			   		}
			   		oCursor.close();
			}
			//Chiudo il DB solo se creato qua
			if(DB==null){
				oDB.close();				
				oDB=null;	
			}
			
			
			return oNFormat.format(dVM);
		}catch (SQLException e) {
			if(DB==null){
				if(oCursor!=null) oCursor.close();
				if(oDB!=null) oDB.close();
			}
			Log.e(ExerciseUtils.class.getCanonicalName(),"error calculate vm speed from DB");
			return oNFormat.format(0);
		}
	}
	
	/**
	 * Ritorna la velocita' media per un certo esercizio
	 * 
	 * @Context oContext
	 * @Database DB eventuale db aperto
	 * @int iIDExercise id dell'esercizio
	 * 
	 * */
	private static String getMAXPace(Context oContext, Database DB, int iIDExercise) {
		double dVM=0.0;
		Database oDB;
		if(DB==null){
			oDB = new Database(oContext);
		}else{
			oDB = DB;	
		}
		
		Cursor oCursor =null;
		NumberFormat oNFormat = NumberFormat.getNumberInstance();
		oNFormat.setMaximumFractionDigits(2);
		try{
			
			if(DB==null) oDB.open();
			
			String sSQL_CURRENT_VM = "select CAST((60/MAX((speed*3.6))) as INTEGER) ||\".\"|| CAST((((60/MAX((speed*3.6))) - CAST((60/MAX((speed*3.6))) as INTEGER))*60) as INTEGER) as pace  from trainer_exercise_dett where id_exercise="+iIDExercise;
			oCursor = oDB.rawQuery(sSQL_CURRENT_VM,null);
			if(oCursor!=null){      		
			   		int iPace = oCursor.getColumnIndex("pace");
		   			
		   			
			   		while(oCursor.moveToNext()){ 
			   			//Coordinate di avvio
			   			dVM=oCursor.getDouble(iPace);	
			   			//Log.v(ExerciseUtils.class.getCanonicalName(),"Velocita' Media KM/H: "+dVM);
			   		}
			   		oCursor.close();
			}
			//Chiudo il DB solo se creato qua
			if(DB==null){
				oDB.close();				
				oDB=null;	
			}
			
			
			return oNFormat.format(dVM);
		}catch (SQLException e) {
			if(DB==null){
				if(oCursor!=null) oCursor.close();
				if(oDB!=null) oDB.close();
			}
			Log.e(ExerciseUtils.class.getCanonicalName(),"error calculate vm speed from DB");
			return oNFormat.format(0);
		}
	}
	
	/**
	 * Ritorna il testo formattato per con il passo dell'esercizio da mettere 
	 * sullo stopwatch del passo
	 * 
	 * TODO usare la colonna speed facendo la media per ottenere il passo = 60/Speed
	 * */
	public synchronized static String getPace(Context oContext, ConfigTrainer oConfigTrainer){
		
		String sPace="0.0";
		double dPace=0.0;
		
		Database oDB = new Database(oContext);
		Cursor oCursor=null;
		try{
			/*String s_SQL_PACE="SELECT "+
								" CASE WHEN abs(((strftime('%S',watch_point_date)-strftime('%S',watch_point_date_prev))))=60 " +
								" then 0 else (abs((strftime('%S',watch_point_date)-strftime('%S',watch_point_date_prev)))/distance)/60 end as pace " +
								"FROM trainer_exercise_dett WHERE id_watch_point=" +
								"(select max(id_watch_point)-1 from TRAINER_EXERCISE_DETT WHERE id_exercise=" +
								"(select max(id_exercise) from TRAINER_EXERCISE_DETT))";
		*/	////Log.v(ExerciseUtils.class.getCanonicalName(),"SQL Pace: "+s_SQL_PACE);
			String s_SQL_PACE="SELECT CAST((60/((sum(distance*1000)/(abs(((strftime('%s',min(watch_point_date))-strftime('%s',max(watch_point_date)))))))*3.6)) as INTEGER) ||\".\"|| CAST((((60/((sum(distance*1000)/(abs(((strftime('%s',min(watch_point_date))-strftime('%s',max(watch_point_date)))))))*3.6)) - CAST((60/((sum(distance*1000)/(abs(((strftime('%s',min(watch_point_date))-strftime('%s',max(watch_point_date)))))))*3.6)) as INTEGER))*60) as INTEGER) as pace from trainer_exercise_dett WHERE id_exercise=(select max(id_exercise) from TRAINER_EXERCISE_DETT)";
			oDB.open();
			oCursor = oDB.rawQuery(s_SQL_PACE,null);
			if(oCursor!=null){      			  
		   		int iPace = oCursor.getColumnIndex("pace");
		   		while(oCursor.moveToNext()){ 
		   			dPace=oCursor.getDouble(iPace);
		   		//	//Log.v(ExerciseUtils.class.getCanonicalName(),"dPace: "+dPace);		
		   		}
		   		NumberFormat oNFormat = NumberFormat.getNumberInstance();
				oNFormat.setMaximumFractionDigits(2);
				sPace=oNFormat.format(dPace);
				if(oCursor!=null) oCursor.close();
				oDB.close();
				oCursor=null;
				oDB=null;				
			}
			//Log.v(ExerciseUtils.class.getCanonicalName(),"sPace: "+sPace);
		}catch (Exception e) {
			//Log.v(ExerciseUtils.class.getCanonicalName(),"Error getting Pace: "+e.getMessage());
			if(oCursor!=null) oCursor.close();
			oDB.close();
			oCursor=null;
			oDB=null;			
		}		
		////Log.v(ExerciseUtils.class.getCanonicalName(), "Passo: "+sPace);
		return sPace;		
	}
	/**
	 * Ritorna il testo formattato per con il passo dell'esercizio da mettere 
	 * sullo stopwatch della velocità media
	 * */
	public synchronized static String getVM(Context oContext, ConfigTrainer oConfigTrainer){
		
		String sVm="0.0";
		double dVm=0.0;
		
		Database oDB = new Database(oContext);
		Cursor oCursor=null;
		try{
			/*String s_SQL_PACE="SELECT ((distance*3600)/abs(((strftime('%S',watch_point_date)-strftime('%S',watch_point_date_prev))))) as Vm " +
								"FROM trainer_exercise_dett WHERE id_watch_point=" +
								"(select max(id_watch_point)-1 from TRAINER_EXERCISE_DETT WHERE id_exercise=" +
								"(select max(id_exercise) from TRAINER_EXERCISE_DETT))";
			 */	
			String s_SQL_PACE="SELECT ((sum(distance*1000)/(abs(((strftime('%s',min(watch_point_date))-strftime('%s',max(watch_point_date)))))))*3.6) as Vm from trainer_exercise_dett WHERE id_exercise=(select max(id_exercise) from TRAINER_EXERCISE_DETT)";
			
			////Log.v(ExerciseUtils.class.getCanonicalName(),"SQL Pace: "+s_SQL_PACE);		
			oDB.open();
			oCursor = oDB.rawQuery(s_SQL_PACE,null);
			if(oCursor!=null){      		
		   		int iVm = oCursor.getColumnIndex("Vm");
		   		
		   		while(oCursor.moveToNext()){ 
		   			dVm=oCursor.getDouble(iVm);
		   		//	//Log.v(ExerciseUtils.class.getCanonicalName(),"dPace: "+dPace);		
		   		}
		   		NumberFormat oNFormat = NumberFormat.getNumberInstance();
				oNFormat.setMaximumFractionDigits(2);
				sVm=oNFormat.format(dVm);
				if(oCursor!=null) oCursor.close();
				oDB.close();
				oCursor=null;
				oDB=null;
				
			}
			//Log.v(ExerciseUtils.class.getCanonicalName(),"sPace: "+sVm);
		}catch (Exception e) {
			//Log.v(ExerciseUtils.class.getCanonicalName(),"Error getting Pace: "+e.getMessage());
			if(oCursor!=null) oCursor.close();
			oDB.close();
			oCursor=null;
			oDB=null;			
		}		
		////Log.v(ExerciseUtils.class.getCanonicalName(), "Passo: "+sPace);
		return sVm;		
	}
	
	/**
	 * Calcola il passo per l'esercizio
	 * se oConfigTrainer==null calcolo la distanza in MT per salvare in DB
	 * 
	 * TODO USARE IL DB
	 * */
	private static double getAVGSpeed(int iHours,int iMinutes, int iSeconds, float fDistance, ConfigTrainer oConfigTrainer, Context oContext) {
		
		
		double dSecond=(((iHours*60)+iMinutes)*60)+iSeconds;
		double dAVGSpeed=0;
		double F_MULT_SEC=3.6;		
   		
		if(oConfigTrainer==null){
			dAVGSpeed = ((fDistance/dSecond)*F_MULT_SEC);
		}else{
			if(oConfigTrainer.getiUnits()==1){
	   			//Miles
	   			dAVGSpeed = ((fDistance/dSecond)*F_MULT_SEC)*MILES_TO_KM;
	   		}else{
	   			dAVGSpeed = ((fDistance/dSecond)*F_MULT_SEC);
	   		}
		}
		
		 
		////Log.v(ExerciseUtils.class.getCanonicalName(),"Hour:"+iHours+" Minutes:"+iMinutes+" Seconds:"+iSeconds+" All Sec: ((("+iHours+"*60)+"+iMinutes+")*60)+"+iSeconds);		   
		////Log.v(ExerciseUtils.class.getCanonicalName(),"((fDistance/dSecond)*F_MULT_SEC) = (("+fDistance+"/"+dSecond+")*3.6) = dAVGSpeed:"+dAVGSpeed);
	   		   		
   		return dAVGSpeed;
		
	}

	/**
	 * Aggiunge uno step all'esercizio
	 * @param oContext 
	 * 
	 * 
	 * */
	public synchronized static void addStep(Context oContext, int iStep) {
		NumberFormat oNFormat = NumberFormat.getNumberInstance();
		oNFormat.setMaximumFractionDigits(0);
		
		String sSQL_SAVE_EXERCISE = "UPDATE trainer_exercise SET steps_count="+iStep+" WHERE id_exercise =(SELECT MAX(id_exercise) FROM trainer_exercise)";
		Database oDB = new Database(oContext);
		try{
			
			oDB.open();
			oDB.getOpenedDatabase().execSQL(sSQL_SAVE_EXERCISE);
			oDB.close();
			oDB=null;			
			////Log.v(ExerciseUtils.class.getCanonicalName(), "Save SQL: "+sSQL_SAVE_EXERCISE);
		}catch (Exception e) {
			Log.e(ExerciseUtils.class.getCanonicalName(), e.getMessage()+" - "+sSQL_SAVE_EXERCISE);
			if(oDB!=null) oDB.close();
			oDB=null;
		}	
	}
	
	/**
	 * @param oContext 
	 * @parameter: listFile, root
	 * @return: vector
	 * 
	 */
	public synchronized static Vector<Music> listRecursiveFile(Context oContext)
	{
		
		 ContentResolver resolver = oContext.getContentResolver();
	     
		 String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
	        
	        String[] projection = {
	                MediaStore.Audio.Media._ID,
	                MediaStore.Audio.Media.ARTIST,
	                MediaStore.Audio.Media.TITLE,
	                MediaStore.Audio.Media.DATA,
	                MediaStore.Audio.Media.DISPLAY_NAME,
	                MediaStore.Audio.Media.DURATION
	        };
	       
			 
	        Cursor cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null);
	        if(cursor!=null){
	        	while(cursor.moveToNext()) {
		        	Music oMusic = new Music();
		        	oMusic.setiID(cursor.getInt(0));
		        	oMusic.setsARTIST(cursor.getString(1));	
		        	oMusic.setsTITLE(cursor.getString(2));	
		        	oMusic.setsFileDATA(cursor.getString(3));	
		        	oMusic.setsDISPLAY_NAME(cursor.getString(4));	
		        	oMusic.setiDURATION(cursor.getInt(5));
		        	//Log.v(ExerciseUtils.class.getCanonicalName(), "Add Music: "+ cursor.getString(3));
		        	vListOfMusic.add(oMusic);
		        	oMusic=null;
		        	
		        }
	        	cursor.close();
	        }	        	        	        
			return vListOfMusic;
	}
	
	public synchronized static String getsIDCurrentExercise(Context oContext){
		String sSQL_CURRENT_EXERCISE = "SELECT MAX(id_exercise) as id_exercise FROM trainer_exercise";
		String sIDCurrentExercise=null;
		Database oDB = new Database(oContext);
		try{
			
			oDB.open();
			Cursor oCursor = oDB.rawQuery(sSQL_CURRENT_EXERCISE,null);
			if(oCursor!=null){      		
		   		int iIDCurrentExercise = oCursor.getColumnIndex("id_exercise");
	   			
	   			
		   		while(oCursor.moveToNext()){ 
		   			//Coordinate di avvio
		   			sIDCurrentExercise=oCursor.getString(iIDCurrentExercise);
		   			
		   		}
		   		oCursor.close();
			}		
			oDB.close();
			oDB=null;			
			////Log.v(ExerciseUtils.class.getCanonicalName(), "Save SQL: "+sSQL_SAVE_EXERCISE);
		}catch (Exception e) {
			Log.e(ExerciseUtils.class.getCanonicalName(), e.getMessage()+" - "+sSQL_CURRENT_EXERCISE);
			if(oDB!=null) oDB.close();
			oDB=null;
			return null;
		}
		return sIDCurrentExercise;
	}
	/**
	 * Salvo la distanza corrente nel watchpoint
	 * 
	 * @param oContext 
	 * 
	 * 
	 * **/

	public synchronized static void saveCurrentDistance(double CurrentDistance, Context oContext, ConfigTrainer oConfigTrainer) {
		
		//Setto il passo
		NewExercise.setPace(ExerciseUtils.getPace(oContext,oConfigTrainer));
		
		//Setto la velocità
		NewExercise.setsVm(ExerciseUtils.getVM(oContext,oConfigTrainer));
		
		String sSQL_SAVE_EXERCISE = "UPDATE trainer_exercise_dett SET distance="+CurrentDistance+
									" WHERE id_exercise =(SELECT MAX(id_exercise) FROM trainer_exercise) AND id_watch_point = " +
									" (SELECT MAX(id_watch_point) FROM trainer_exercise_dett)";
		Database oDB = new Database(oContext);
		try{
			
			oDB.open();
			oDB.getOpenedDatabase().execSQL(sSQL_SAVE_EXERCISE);
			oDB.close();
			oDB=null;			
			//Log.v(ExerciseUtils.class.getCanonicalName(), "saveCurrentDistance SQL: "+sSQL_SAVE_EXERCISE);
		}catch (Exception e) {
			Log.e(ExerciseUtils.class.getCanonicalName(), e.getMessage()+" - "+sSQL_SAVE_EXERCISE);
			if(oDB!=null) oDB.close();
			oDB=null;
		}	
	}
	/**
	 * Salvo i dati dell'utente nel DB
	 * */
	public synchronized static boolean saveUserData(Context oContext, String txtNick, String txtName,
			String txtWeight, String txtAge, String txtHeight, boolean FBShare,
			boolean BuzzShare, boolean TwitterShare, String sGender) {
		int iFB=0;
		int iBuzz=0;
		int iTwitter=0;
		Database oDB=null;
		try{
			oDB = new Database(oContext);
			if(oDB==null) return false;
			//Log.v(ExerciseUtils.class.getCanonicalName(), "TwitterShare:"+TwitterShare+" - FBShare:"+FBShare);
			
			if(FBShare) 	 iFB=1;
			if(BuzzShare) 	 iBuzz=1;
			if(TwitterShare) iTwitter=1;
			
			String sSQL_DELETE_USER = "DELETE FROM trainer_users";
			String sSQL_INSERT_USER = "INSERT INTO trainer_users (nick,name,weight,age,height,facebook,twitter,buzz,gender) VALUES ('"+
				txtNick+"','"+txtName+"',"+txtWeight+","+txtAge+","+txtHeight+",'"+iFB+"','"+iTwitter+"','"+iBuzz+"','"+sGender+"')";
			Log.v(ExerciseUtils.class.getCanonicalName(), sSQL_INSERT_USER);
			
			oDB.open();
			//Svuoto la tabella
			oDB.getOpenedDatabase().execSQL(sSQL_DELETE_USER);			
			//Inserisci l'utente
			oDB.getOpenedDatabase().execSQL(sSQL_INSERT_USER);
			
			oDB.close();
			
			oDB=null;			
		}catch (SQLException e) {
			//Log.e(ExerciseUtils.class.getCanonicalName(), e.getMessage());
			if(oDB!=null) {
				oDB.close();
				oDB=null;	
			}
			
			return false;
		}
		return true;
	}
	/**
	 * Controlla se e' stato definito un utente se no restituisce false e apre una
	 * popup di compilazione
	 * 
	 * @author gianluca masci aka (glm)
	 * **/
	public synchronized static boolean isUserExist(Context oContext) {
		boolean bUserExist=false;
		Database oDB=null;
		Cursor oCursor=null;
		try{
			oDB = new Database(oContext);
			oDB.open();
			oCursor = oDB.fetchAll("trainer_users",null,null);
			while(oCursor.moveToNext()){        
				bUserExist= true;
			}		
			oCursor.close();
			oDB.close();
			oDB=null;
		}catch (Exception e) {
			if(oCursor!=null) oCursor.close();
			if(oDB!=null) oDB.close();		
			oCursor=null;
			oDB=null;
			bUserExist= false;
		}		
		return bUserExist;
	}
	/**
	 * imòposta i parametri dell'oggetto statico User con tutti i dettagli
	 * 
	 * @param Context oContext
	 * */
	public synchronized static void loadUserDectails(Context oContext){
		
		Database oDB = new Database(oContext);
	   	oDB.open();       	
	   	Cursor oCursor = oDB.fetchAll("trainer_users", null,null);
	   	if(oCursor!=null){        		
	   		int iKey 	 = oCursor.getColumnIndex("id_users");   			
   			int iNick 	 = oCursor.getColumnIndex("nick");
   			int iName 	 = oCursor.getColumnIndex("name");
   			int iGender  = oCursor.getColumnIndex("gender");
   			int iAge  	 = oCursor.getColumnIndex("age");
   			int iWeight  = oCursor.getColumnIndex("weight");
   			int iFB 	 = oCursor.getColumnIndex("facebook");
   			int iBuzz 	 = oCursor.getColumnIndex("buzz");
   			int iTwitter = oCursor.getColumnIndex("twitter");   			   			   			
   			int iHeight  = oCursor.getColumnIndex("height");
   			
   			while(oCursor.moveToNext()){ 
   				User.setID(oCursor.getInt(iKey));
   				User.setiWeight(oCursor.getInt(iWeight));
   				User.setiAge(oCursor.getInt(iAge)); 
   				User.setiHeight(oCursor.getInt(iHeight));
   				User.setsNick(oCursor.getString(iNick));
   				User.setsName(oCursor.getString(iName));
   				
   				if(oCursor.getString(iGender).compareToIgnoreCase("M")==0){
   					User.setsGender("M");
   				}else{
   					User.setsGender("F");
   				}
   				
   				if(oCursor.getInt(iFB)==1){
   					User.setiFB(1);
   			    	
   				}
   				if(oCursor.getInt(iBuzz)==1){
   					User.setiBuzz(1);
   			    		
   				}
   				if(oCursor.getInt(iTwitter)==1){
   					User.setiTwitter(1);
   				}
   			}
   			
   			////Log.v(this.getClass().getCanonicalName(), "Exercise Max: "+fMaxDistance);	
   			////Log.v(this.getClass().getCanonicalName(), "Max Width: "+dm.widthPixels);	  			
   			oCursor.close();
   		   	oDB.close();
   		   	oCursor=null;
   		   	oDB=null;
	   	}
	}
	/**
	 * Calcolo delle Kalorie bruciate durante l'esercizio
	 * 
	 * Corsa 
	 * 	Spesa energetica (KCal) = 0.9 x km percorsi x kg di peso corporeo
	 * Cammonata
	 * 	Spesa energetica (KCal) = 0.45 -0.50 per km percorsi x kg di peso corporeo
	 * 
	 * @param ConfigTrainer oConfig
	 * @param NewExercise oNewExercise 
	 * */
	public synchronized static String getKaloriesBurn(ConfigTrainer oConfig, double dCurrentDistance){
		String sKalories="";
		double dKaloriesBurn=0.0;
		
		NumberFormat oNFormat = NumberFormat.getNumberInstance();
		oNFormat.setMaximumFractionDigits(2);
		if(iTypeExercise==0){
			//run
			dKaloriesBurn=I_BURN_RUNNING*dCurrentDistance*oConfig.getiWeight();		
		}else if (iTypeExercise==1){
			//bike
			dKaloriesBurn=I_BURN_BIKING*dCurrentDistance*oConfig.getiWeight();		
		}else if(iTypeExercise==100){
			//walking
			dKaloriesBurn=I_BURN_WALKING*dCurrentDistance*oConfig.getiWeight();		
		}
	
		
		sKalories = oNFormat.format(dKaloriesBurn);				
		////Log.v(ExerciseUtils.class.getCanonicalName(), "BURN="+I_BURN_RUNNING+"*"+fCurrentDistance+"*"+oConfig.getiWeight()+"getKaloriesBurn BURN: "+sKalories);
		
		return sKalories;
	}
	
	/**
	 * Popolo il bean dello user con i dati del DB
	 * */
	public synchronized static void populateUserFromDB(Context oContext) {		
		
		Database oDB = new Database(oContext);
	   	oDB.open();       	
	   	Cursor oCursor = oDB.fetchAll("trainer_users", null,null);
	   	
	   	   
	   	if(oCursor!=null){        		
	   		int iKey 	 = oCursor.getColumnIndex("id_users");   			
   			int iNick 	 = oCursor.getColumnIndex("nick");
   			int iName 	 = oCursor.getColumnIndex("name");
   			int iGender  = oCursor.getColumnIndex("gender");
   			int iAge  	 = oCursor.getColumnIndex("age");
   			int iWeight  = oCursor.getColumnIndex("weight");
   			int iFB 	 = oCursor.getColumnIndex("facebook");
   			int iBuzz 	 = oCursor.getColumnIndex("buzz");
   			int iTwitter = oCursor.getColumnIndex("twitter");   			   			   			
   			
   			while(oCursor.moveToNext()){ 
   				User.setID(oCursor.getInt(iKey));
   				User.setiWeight(oCursor.getInt(iWeight));
   				User.setiAge(oCursor.getInt(iAge));   	        
   				User.setsNick(oCursor.getString(iNick));
   				User.setsName(oCursor.getString(iName));
   				User.setsGender(oCursor.getString(iGender));
   				User.setiFB(oCursor.getInt(iFB));
   				User.setiBuzz(oCursor.getInt(iBuzz));
   				User.setiTwitter(oCursor.getInt(iTwitter));   				   		
   			}
   			
   			////Log.v(this.getClass().getCanonicalName(), "Exercise Max: "+fMaxDistance);	
   			////Log.v(this.getClass().getCanonicalName(), "Max Width: "+dm.widthPixels);	  			
   			oCursor.close();
   		   	oDB.close();
   		   	oCursor=null;
   		   	oDB=null;
	   	}	   	
	}
	/**
	 * Ritorna il tempo formattato in base ai Ms ricevuti 
	 * */
	public synchronized static String getFormattedTimeFromMs(double dMs) {
		String sFormattedTime="";

		float seconds = (float) ((((dMs / 1000) % 60))/60)/60;
   		float minutes = (float) (((dMs / 1000) / 60));
   		int hours   = (int) ((dMs / 1000) / 3600);
		
   		if(Math.floor(hours)!=0){
   			sFormattedTime+=Math.floor(hours)+"h "+Math.floor(minutes)+"m "+Math.floor(seconds)+"s";
   		}else{
   			sFormattedTime+=Math.floor(minutes)+"m "+Math.floor(seconds)+"s";
   		}
   		
		return sFormattedTime;
	}
	/***
	 * Ritorna la velocità media formattata con UM a partire da un valore già calcolato
	 * */
	public synchronized static String getFormattedAVGSpeedMT(double dDistance, ConfigTrainer oConfigTrainer) {
		String sUnit=" Km/h";
		String sAVGSpeed="";
		NumberFormat oNFormat = NumberFormat.getNumberInstance();
		oNFormat.setMaximumFractionDigits(2);
		sAVGSpeed = oNFormat.format(dDistance);
		
		if(oConfigTrainer.getiUnits()==1){
			sUnit=" Mi/h";
			
		}
		
		return sAVGSpeed+sUnit;
	}
	
	/**
	 * Ritorna un vettore di esercizi con data e peso associati da usare per il grafico
	 *  
	 * */
	public synchronized static Vector<Exercise> getWeightData(Context oContext){
		Vector<Exercise> oVector = new Vector<Exercise>();
		String s_SQL_WEIGHT="select strftime('%d/%m/%Y',creation_date) as data, strftime('%d',creation_date) as giorno, " +
				"strftime('%m',creation_date) as mese, strftime('%Y',creation_date) as anno," +
				"	weight from TRAINER_EXERCISE order by id_exercise";
		Database oDB = new Database(oContext);
		
		try{
			
			oDB.open();
			Cursor oCursor = oDB.rawQuery(s_SQL_WEIGHT,null);
			if(oCursor!=null){      		   
		   		int iData = oCursor.getColumnIndex("data");
		   		int iDay = oCursor.getColumnIndex("giorno");
		   		int iMonth = oCursor.getColumnIndex("mese");
		   		int iYear = oCursor.getColumnIndex("anno");
		   		int iWeight = oCursor.getColumnIndex("weight");
		   		
		   		while(oCursor.moveToNext()){ 
		   			//Date date = format.parse(oCursor.getString(iData));
		   			Calendar date = new GregorianCalendar(
		   					Integer.parseInt(oCursor.getString(iYear)), 
		   					Integer.parseInt(oCursor.getString(iMonth))-1, 
		   					Integer.parseInt(oCursor.getString(iDay)));
		   			//Log.v(ExerciseUtils.class.getCanonicalName(),"Date Utils Pre: "+
		   			//		Integer.parseInt(oCursor.getString(iDay))+"/"+
		   			//		Integer.parseInt(oCursor.getString(iMonth))+"/"+
		   			//		Integer.parseInt(oCursor.getString(iYear)));
		   			
		   			//Log.v(ExerciseUtils.class.getCanonicalName(),"Date Utils: "+date.get(Calendar.DAY_OF_MONTH)+"/"+date.get(Calendar.MONTH)+"/"+date.get(Calendar.YEAR));
		   			Exercise oExercise = new Exercise();
		   			oExercise.setsDateExercise(oCursor.getString(iData));
		   			
		   			oExercise.setdDateExercise(date);
		   			oExercise.setdWeight(oCursor.getDouble(iWeight));
		   			oVector.add(oExercise);
		   			oExercise=null;	   			
		   		}
		   		oCursor.close();
			}
			oDB.close();
			oDB=null;
		}catch (Exception e) {
			Log.e(Exercise.class.getCanonicalName(),"Errore nel prelevare i pesi");
			if(oDB!=null) oDB.close();
			oDB=null;
		}
		return oVector;
	}
	
	/**
	 * Imposta il tempo dell'esercizio
	 * 
	 * @param int TimePassed tempo trascorso
	 * */
	public synchronized static String getTimeHHMM(long TimeTotal){
		int minutes = (int) (TimeTotal / 60000 % 60);
        int hours = (int) (TimeTotal / 3600000);
        if(minutes>=60){
        	minutes=minutes/60;
        }
        try{
        	//Log.v(Exercise.class.getCanonicalName(),"TimeTotal: "+TimeTotal+" TimeHH:MM: "+String.format ("%d:%02d", hours, minutes));
        	//Tempo Formattato String.format ("%d:%02d:%02d.%d", hours, minutes, seconds, decSeconds);
            return String.format ("%d:%02d", hours, minutes);
        }catch (Exception e) {
        	 return "";
		}
        
	}
	
	/**
	 * Imposta il tempo dell'esercizio
	 * 
	 * @param int TimePassed tempo trascorso
	 * */
	public synchronized static String getTimeHHMMForSpeech(long TimeTotal, Context oContext){
		int minutes = (int) (TimeTotal / 60000 % 60);
        int hours = (int) (TimeTotal / 3600000);
        if(minutes>=60){
        	minutes=minutes/60;
        }
        try{
        	//Log.v(Exercise.class.getCanonicalName(),"TimeTotal: "+TimeTotal+" TimeHH:MM: "+String.format ("%d:%02d", hours, minutes));
        	if(hours>0){
        		//Tempo Formattato String.format ("%d:%02d:%02d.%d", hours, minutes, seconds, decSeconds);
            	return hours+oContext.getString(R.string.hours)+minutes+oContext.getString(R.string.minutes);
        	}else{
        		return minutes+oContext.getString(R.string.minutes);
        	}
        		
        	
            //return String.format ("%d:%02d", hours, minutes);
        }catch (Exception e) {
        	 return "";
		}
        
	}
	
	/**
	 * Imposta il tempo dell'esercizio
	 * 
	 * @param int TimePassed tempo trascorso
	 * */
	public synchronized static int getTimeHH(long TimeTotal, Context oContext){
		
        int hours = (int) (TimeTotal / 3600000);
        
        try{
        	if(hours>0){
        		//Tempo Formattato String.format ("%d:%02d:%02d.%d", hours, minutes, seconds, decSeconds);
            	return hours;
        	}    		
        	return 0;
            //return String.format ("%d:%02d", hours, minutes);
        }catch (Exception e) {
        	 return 0;
		}
        
	}
	
	/**
	 * Imposta il tempo dell'esercizio
	 * 
	 * @param int TimePassed tempo trascorso
	 * */
	public synchronized static int getTimeMM(long TimeTotal, Context oContext){
		int minutes = (int) (TimeTotal / 60000 % 60);
         if(minutes>=60){
        	minutes=minutes/60;
        }
        return minutes; 
	}
	
	/**
	 * Imposta il tempo totale dell'esercizio
	 * 
	 * @param int TimeTotal tempo totale trascorso
	 * */
	public synchronized static String getTimeSSdd(long TimeTotal){
         int seconds = (int) (TimeTotal / 1000 % 60);
         try{
        	 //Log.v(Exercise.class.getCanonicalName(),"TimeTotal: "+TimeTotal+" TimeSS: "+String.format (":%02d", seconds));
        	 return String.format (":%02d", seconds);
         }catch (Exception e) {
        	 return "";
 		 }
         
	}

	public synchronized static void saveNote(Context oContext, int iIDExercise, String sNote) {
		Database oDB = new Database(oContext);
		
		try{
			
			String sSQL_UPDATE_PREV_WATCH_POINT="UPDATE trainer_exercise SET note='"+sNote.replaceAll("'", "''")+"' WHERE " +
					"id_exercise="+iIDExercise;
		
			oDB.open();
			oDB.getOpenedDatabase().execSQL(sSQL_UPDATE_PREV_WATCH_POINT);
			oDB.close();
			oDB=null;
			
		}catch (Exception e) {
			// TODO: handle exception
		}
	}
	/**
	 * scrive un file GPX a partire da da un ExerciseManipulate impostato
	 * @param IDExercise 
	 * 
	 * 
	 * */
	public synchronized static boolean writeGPX(int IDExercise, Context oContext, ConfigTrainer oConfigTrainer){
		if(IDExercise>-1){
			ExerciseUtils.populateExerciseDetails(oContext, oConfigTrainer, IDExercise);
		}
		SimpleDateFormat oDate = new SimpleDateFormat("yyyy-MM-dd");       
		SimpleDateFormat oTime = new SimpleDateFormat("HH:mm:ss");   
		String sTime="";
		//Controllo cartella
		if(ExerciseUtils.createWorking()){			
			try {
				sExportFile=ExerciseUtils.getRootFolder()+"/exercise"+ExerciseManipulate.getiIDExercise()+".gpx";
				FileWriter outFile = new FileWriter(sExportFile);
				PrintWriter out = new PrintWriter(outFile);
				out.println("<gpx " +
						" xmlns=\"http://www.topografix.com/GPX/1/1\" " +
						" xmlns:gpxx=\"http://www.garmin.com/xmlschemas/GpxExtensions/v3\" " +
						" xmlns:wptx1=\"http://www.garmin.com/xmlschemas/WaypointExtension/v1\" " +
						" xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\" " +
						" creator=\"Android Trainer\" " +
						" version=\"1.1\" " +
						" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
						" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd http://www.garmin.com/xmlschemas/GpxExtensions/v3 http://www8.garmin.com/xmlschemas/GpxExtensionsv3.xsd http://www.garmin.com/xmlschemas/WaypointExtension/v1 http://www8.garmin.com/xmlschemas/WaypointExtensionv1.xsd http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd\">\n"+
				"<trk>" +
				"<name>" + ExerciseManipulate.getsNote() + "</name>" +
				"	<trkseg>");
				
				// Also could be written as follows on one line
				// Printwriter out = new PrintWriter(new FileWriter(args[0]));
				ArrayList<WatchPoint> aWatchPoint = ExerciseManipulate.getWatchPoint();
				int iWPSize=aWatchPoint.size();
				for(int i=0;i<iWPSize;i++){
					if(aWatchPoint.get(i).getdDateTime()!=null){
						sTime=oDate.format(aWatchPoint.get(i).getdDateTime().getTime())+"T"+
						oTime.format(aWatchPoint.get(i).getdDateTime().getTime())+"Z";
					}
					
					out.println("<trkpt lat=\""+String.valueOf(aWatchPoint.get(i).getdLat())+"\" lon=\""+String.valueOf(aWatchPoint.get(i).getdLong())+"\">\n"+
							"<ele>"+String.valueOf(aWatchPoint.get(i).getdAlt())+"</ele>\n"+ 
							"<time>"+sTime+"</time>\n" +
							"<speed>"+aWatchPoint.get(i).getdPace()+"</speed>\n"+
							"<name>TP"+(i+1)+"</name>\n"+
							"<fix>none</fix>\n"+
							"<cmt>"+aWatchPoint.get(i).getsSong()+"</cmt>\n" +
					"</trkpt>\n");
				}
				out.println("</trkseg>\n"+
						"</trk>\n"+
				"</gpx>\n");				
				out.close();
			} catch (IOException e){
				Log.e(Exercise.class.getCanonicalName(),"Export file "+sExportFile+" fail");
				return false;
			}			
		}	
		return true;
	}
	/**
	 * scrive un file TCS a partire da da un ExerciseManipulate impostato
	 * 
	 * @param IDExercise 
	 * 
	 * TODO mancano alcune info sul bean da riportare nel file.
	 * 
	 * */
	public synchronized static boolean writeTCX(int IDExercise, Context oContext, ConfigTrainer oConfigTrainer){
		if(IDExercise>-1){
			ExerciseUtils.populateExerciseDetails(oContext, oConfigTrainer, IDExercise);
		}
		SimpleDateFormat oDate = new SimpleDateFormat("yyyy-MM-dd");       
		SimpleDateFormat oTime = new SimpleDateFormat("HH:mm:ss");   
		String sTime="";
		String sTypeSport="";
		
		//Controllo cartella
		if(ExerciseUtils.createWorking()){			
			try {
				if(ExerciseManipulate.getiTypeExercise()==0){
					sTypeSport="Running";
				}else if (ExerciseManipulate.getiTypeExercise()==1){
					sTypeSport="Biking";
				}else if (ExerciseManipulate.getiTypeExercise()==100){
					sTypeSport="Walking";
				}
				
				sExportFile=ExerciseUtils.getRootFolder()+"/exercise"+ExerciseManipulate.getiIDExercise()+".tcx";
				FileWriter outFile = new FileWriter(sExportFile);
				PrintWriter out = new PrintWriter(outFile);
				out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
							" <TrainingCenterDatabase \n" +
							"   xsi:schemaLocation=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2 http://www.garmin.com/xmlschemas/TrainingCenterDatabasev2.xsd\" \n" +
							"   xmlns:ns5=\"http://www.garmin.com/xmlschemas/ActivityGoals/v1\" \n" +
							"   xmlns:ns3=\"http://www.garmin.com/xmlschemas/ActivityExtension/v2\" \n" +
							"   xmlns:ns2=\"http://www.garmin.com/xmlschemas/UserProfile/v2\" \n" +
							"   xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\" \n" +
							"   xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:ns4=\"http://www.garmin.com/xmlschemas/ProfileExtension/v1\">\n" +
				"<Activities>\n" +
				"	<Activity Sport=\""+sTypeSport+"\">\n");
				if(ExerciseManipulate.getdDateTimeStart()!=null){
					sTime=oDate.format(ExerciseManipulate.getdDateTimeStart().getTime())+"T"+
					oTime.format(ExerciseManipulate.getdDateTimeStart().getTime())+"Z";
				}
				out.println("<Id>"+sTime+"</Id>\n" +
						"<Lap StartTime=\""+sTime+"\">\n" +
						"	<TotalTimeSeconds>"+ExerciseManipulate.getiTotalTimeInSeconds()+"</TotalTimeSeconds>\n" +
						"	<DistanceMeters>"+(ExerciseManipulate.getdTotalDistance()*1000)+"</DistanceMeters>\n" +
						"	<MaximumSpeed>"+ExerciseManipulate.getdAVGSpeed()+"</MaximumSpeed>\n" +
						"	<Calories>"+ExerciseManipulate.getsCurrentCalories()+"</Calories>\n" +
						"	<Intensity>Active</Intensity>\n" +
						"	<TriggerMethod>Manual</TriggerMethod>\n" +
						"	<Track>\n");
				// Also could be written as follows on one line
				// Printwriter out = new PrintWriter(new FileWriter(args[0]));
				ArrayList<WatchPoint> aWatchPoint = ExerciseManipulate.getWatchPoint();
				int iWPSize=aWatchPoint.size();
				for(int i=0;i<iWPSize;i++){
					if(aWatchPoint.get(i).getdDateTime()!=null){
						sTime=oDate.format(aWatchPoint.get(i).getdDateTime().getTime())+"T"+
						oTime.format(aWatchPoint.get(i).getdDateTime().getTime())+"Z";
					}
					
					out.println("<Trackpoint>\n"+
								" <Time>"+sTime+"</Time>\n"+
								" <Position>\n"+
								"   <LatitudeDegrees>"+String.valueOf(aWatchPoint.get(i).getdLat())+"</LatitudeDegrees>\n"+
								"   <LongitudeDegrees>"+String.valueOf(aWatchPoint.get(i).getdLong())+"</LongitudeDegrees>\n"+
								" </Position>\n"+
								" <AltitudeMeters>"+String.valueOf(aWatchPoint.get(i).getdAlt())+"</AltitudeMeters>\n"+
								" <Extensions>\n"+
								"   <ns3:TPX>\n"+
								"     <ns3:Speed>"+aWatchPoint.get(i).getdPace()/3.6+"</ns3:Speed>\n"+
								"   </ns3:TPX>\n"+
								" </Extensions>\n"+
								"</Trackpoint>\n");
				}
				out.println("</Track>\n"+
						"</Lap>\n" +
					   "</Activity>" +
					"</Activities>\n"+
				"</TrainingCenterDatabase>\n");				
				out.close();
			} catch (IOException e){
				Log.e(Exercise.class.getCanonicalName(),"Export file "+sExportFile+" fail");
				return false;
			}			
		}	
		return true;
	}
	/**
	 * scrive un file GPX a partire da da un ExerciseManipulate impostato
	 * @param IDExercise 
	 * 
	 * 
	 * */
	public synchronized static boolean writeKML(int IDExercise, Context oContext, ConfigTrainer oConfigTrainer){
		if(IDExercise>-1){
			ExerciseUtils.populateExerciseDetails(oContext, oConfigTrainer, IDExercise);
		}
		String sTime = "";
		SimpleDateFormat oDate = new SimpleDateFormat("yyyy-MM-dd");       
		SimpleDateFormat oTime = new SimpleDateFormat("HH:mm:ss");   
		
		if(ExerciseUtils.createWorking()){			
			try {
				sExportFile=ExerciseUtils.getRootFolder()+"/exercise"+ExerciseManipulate.getiIDExercise()+".kml";
				FileWriter outFile = new FileWriter(sExportFile);
				PrintWriter out = new PrintWriter(outFile);
				out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
				out.println("  <kml xmlns=\"http://earth.google.com/kml/2.0\">");
				out.println("  	<Document>");
				out.println("   		<name>Personal Trainer "+ExerciseManipulate.getlStartTime()+"</name>");
				out.println("			<description>" + "Created by Personal Trainer for Android" + "</description>");
				out.println("   		<Style id=\"roadStyle\">");
				out.println("  			<LineStyle>");
				out.println("  				<color>7fcf0064</color>");
				out.println("   				<width>6</width>");
				out.println("    			</LineStyle>");
				out.println("  		</Style>");
				// Also could be written as follows on one line
				// Printwriter out = new PrintWriter(new FileWriter(args[0]));
				ArrayList<WatchPoint> aWatchPoint = ExerciseManipulate.getWatchPoint();
				int iWPSize=aWatchPoint.size();
				for(int i=0;i<iWPSize;i++){
					
					if(aWatchPoint.get(i).getdDateTime()!=null){
						sTime=oDate.format(aWatchPoint.get(i).getdDateTime().getTime())+"T"+
						oTime.format(aWatchPoint.get(i).getdDateTime().getTime())+"Z";
					}
					if(i==0){
						//Partenza
						out.println("<Placemark>");
						out.println("<name>Start</name>");
						out.println("<description>" + "Created by Personal Trainer for Android" + "</description>");
						out.println("<TimeStamp>");
						out.println("<when>"+sTime+"</when>");
						out.println("</TimeStamp>");
						out.println("<Point>"); 
						out.println("<coordinates>" + String.valueOf(aWatchPoint.get(i).getdLong()) + "," + String.valueOf(aWatchPoint.get(i).getdLat()) + "," + String.valueOf(aWatchPoint.get(i).getdAlt()) + "</coordinates>");
						out.println("</Point>");
						out.println("</Placemark>");
						
						out.println("<Placemark>");
						out.println("<name>Exercise</name>");
						out.println("<description><![CDATA[Distance: "+ExerciseManipulate.getsTotalDistance()+" ("+ExerciseManipulate.getsTotalTime()+") ]]></description>");
						out.println("<GeometryCollection>");
						out.println("	<LineString>");
						out.println("		<coordinates>");
					}else if(i==aWatchPoint.size()-1){						
						out.println(" </coordinates>");
						out.println("	</LineString>");
						out.println("		</GeometryCollection>");
						out.println("<styleUrl>#roadStyle</styleUrl>");
						out.println("</Placemark>");
						//Arrivo
						out.println("<Placemark>");
						out.println("<name>End</name>");
						out.println("<description>" + "Created by Personal Trainer for Android" + "</description>");
						out.println("<TimeStamp>");
						out.println("<when>"+sTime+"</when>");
						out.println("</TimeStamp>");
						out.println("<Point>"); 
						out.println("<coordinates>" + String.valueOf(aWatchPoint.get(i).getdLong()) + "," + String.valueOf(aWatchPoint.get(i).getdLat()) + "," + String.valueOf(aWatchPoint.get(i).getdAlt()) + "</coordinates>");
						out.println("</Point>");
						out.println("</Placemark>");
					}else{
						out.print(String.valueOf(aWatchPoint.get(i).getdLong()) + "," + 
								  String.valueOf(aWatchPoint.get(i).getdLat()) + "," + 
								  String.valueOf(aWatchPoint.get(i).getdAlt())+" ");
					}
				}
				
   
						
						/*"</Document>" + 
   "</kml>";*/ 
				// Write text to file
				out.println("</Document>");
				out.println("</kml>");				
				out.close();
			} catch (IOException e){
				Log.e(Exercise.class.getCanonicalName(),"Export file "+sExportFile+" fail");
				return false;
			}
		}

		return true;
	}

	/**
	 * scrive un file GPX a partire da da un ExerciseManipulate impostato
	 * @param dDistance 
	 * @param IDExercise 
	 * 
	 * 
	 * */
	private static void writeCustomLoForDebug(double dLatStart,
			double dLongStart, double dLatEnd, double dLongEnd, double dDistance, String sUnit) {
		
		if(ExerciseUtils.createWorking()){			
			try {
				sExportFile=ExerciseUtils.getRootFolder()+"/debug.log";
				FileWriter outFile = new FileWriter(sExportFile,true);
				PrintWriter out = new PrintWriter(outFile);
				out.println("Start Lat, "+dLatStart+", Start Long,"+dLongStart+", End Lat,"+dLatEnd+", End Long,"+dLongEnd+", Distance, "+dDistance+", Unit, "+sUnit);
						
				out.close();
				outFile.close();
			} catch (IOException e){
				Log.e(Exercise.class.getCanonicalName(),"Export file "+sExportFile+" fail");
				return;
			}
		}
		return;
	}
	private static boolean createWorking(){	
		File oRoot = Environment.getExternalStorageDirectory();
		try{
			if(oRoot.canRead() && oRoot.canWrite()){			
				//Controllo i privilegi
				File oRootPersonalTrainer = new File(oRoot.getAbsolutePath()+"/"+FOLDER_PERSONAL_TRAINER);
				
				if(oRootPersonalTrainer.exists()){
					//Log.v(Exercise.class.getCanonicalName(),"Folder PersonalTrainer Exists: "+oRoot.getAbsolutePath()+"/"+FOLDER_PERSONAL_TRAINER);
				}else{
					//Log.v(Exercise.class.getCanonicalName(),"Folder PersonalTrainer NOT Exists"+oRoot.getAbsolutePath()+"/"+FOLDER_PERSONAL_TRAINER);
					if(oRootPersonalTrainer.mkdir()){
						//Log.v(Exercise.class.getCanonicalName(),"Folder PersonalTrainer Create"+oRoot.getAbsolutePath()+"/"+FOLDER_PERSONAL_TRAINER);
					}
				}						
			}
		}catch (SecurityException e) {
			Log.e(Exercise.class.getCanonicalName(),"cretate Folder PersonalTrainer fail");
			return false;
		}
		
		return true;
	}
	private static String getRootFolder(){
		File oRoot = Environment.getExternalStorageDirectory();
		return oRoot.getAbsolutePath()+"/"+FOLDER_PERSONAL_TRAINER;
	}

	public synchronized static void setLicenceOK(Context oContext) {
		Database oDB = new Database(oContext);
		
		try{
			
			String sSQL_UPDATE_PREV_WATCH_POINT="UPDATE trainer_version SET licence='S'";
		
			oDB.open();
			oDB.getOpenedDatabase().execSQL(sSQL_UPDATE_PREV_WATCH_POINT);
			oDB.close();
			oDB=null;
			Log.i(Exercise.class.getCanonicalName(),"Register licence");
		}catch (Exception e) {
			Log.e(Exercise.class.getCanonicalName(),"Fail to register licence!");
		}
	}

	public synchronized static void setLicenceKO(Context oContext) {
		Database oDB = new Database(oContext);
		
		try{
			
			String sSQL_UPDATE_PREV_WATCH_POINT="UPDATE trainer_version SET licence='N'";
		
			oDB.open();
			oDB.getOpenedDatabase().execSQL(sSQL_UPDATE_PREV_WATCH_POINT);
			oDB.close();
			oDB=null;
			Log.i(Exercise.class.getCanonicalName(),"UnRegister licence");
		}catch (Exception e) {
			Log.e(Exercise.class.getCanonicalName(),"Fail to unregister licence!");
		}
	}
	/***
	 * Ritorna un Hash con la coppia Tipo Esercizio distanza totale
	 * @param oConfigTrainer 
	 * 
	 * */
	public synchronized static Vector<DistancePerExercise> getDistanceForType(ConfigTrainer oConfigTrainer, Context oContext){
		Vector<DistancePerExercise> table = new Vector<DistancePerExercise>();
		String s_SQL_DISTANCE="select case when id_type_exercise < 100 then sum(distance)*"+GPSFIX+" else sum(distance) end as somma, " +
				"					   (case when id_type_exercise=1000 then 0 WHEN id_type_exercise=1001 then 1 WHEN id_type_exercise=10000 then 100 else id_type_exercise end) as id_type_exercise " +
				"					  from TRAINER_EXERCISE_DETT where strftime('%Y',watch_point_date)=strftime('%Y','now') group by case when id_type_exercise=1000 then 0 WHEN id_type_exercise=1001 then 1 WHEN id_type_exercise=10000 then 100 else id_type_exercise end";
		Database oDB = new Database(oContext);
		
		try{
			
			oDB.open();
			Cursor oCursor = oDB.rawQuery(s_SQL_DISTANCE,null);
			if(oCursor!=null){      						   
		   		int iSomma = oCursor.getColumnIndex("somma");
		   		int i_TypeExercise = oCursor.getColumnIndex("id_type_exercise");
		   		
		   		NumberFormat oNFormat = NumberFormat.getNumberInstance();
				oNFormat.setMaximumFractionDigits(2);
				
		   		while(oCursor.moveToNext()){ 
		   			int iType = oCursor.getInt(i_TypeExercise);
		   			
		   			double dDistance = oCursor.getLong(iSomma);
		   			if(oConfigTrainer.getiUnits()!=0){
		   				dDistance = (int) (dDistance * MILES_TO_KM);
		   			}
		   			
		   			
		   			String sDistance =oNFormat.format(dDistance); 
		   			DistancePerExercise oDistancePerExercise = new DistancePerExercise();
		   			oDistancePerExercise.setiTypeExercise(iType);
		   			oDistancePerExercise.setsDistance(sDistance);
		   			table.add(oDistancePerExercise);		   			
		   			oDistancePerExercise=null;
		   		}
		   		oCursor.close();
			}
			oDB.close();
			String s_SQL_Kalories="select (case when id_type_exercise=1000 then 0 WHEN id_type_exercise=1001 then 1 WHEN id_type_exercise=10000 then 100 else id_type_exercise end) as id_type_exercise, sum(kalories) as kalories from trainer_exercise where strftime('%Y',start_date)=strftime('%Y','now') group by id_type_exercise";
			oDB.open();
			oCursor = oDB.rawQuery(s_SQL_Kalories,null);
			if(oCursor!=null){      						   
		   		int i_KaloriesExercise = oCursor.getColumnIndex("kalories");
		   		int i_TypeExercise = oCursor.getColumnIndex("id_type_exercise");
		   		int iIndex=0;
		   		NumberFormat oNFormat = NumberFormat.getNumberInstance();
				oNFormat.setMaximumFractionDigits(2);
				
		   		while(oCursor.moveToNext()){ 
		   			int iKalories = oCursor.getInt(i_KaloriesExercise);
		   			int iType = oCursor.getInt(i_TypeExercise);
		   				
		   			for(iIndex=0;iIndex<table.size();iIndex++){
		   				String sKalories =oNFormat.format(iKalories);
		   				if(table.get(iIndex).getiTypeExercise()==iType){
			   				table.get(iIndex).setsCalories(sKalories);
			   			}
		   			}
		   		}
		   		oCursor.close();
			}
			oDB.close();
			oDB=null;
		}catch (Exception e) {
			Log.e(Exercise.class.getCanonicalName(),"Errore nel prelevare i pesi");
			if(oDB!=null) oDB.close();
			oDB=null;
		}
		return table;		
	}
	/***
	 * Ritorna un Hash con la coppia Tipo Esercizio distanza totale
	 * 
	 * */
	public synchronized static Vector<DistancePerMonth> getDistanceForMonth(ConfigTrainer oConfigTrainer, Context oContext){
		Vector<DistancePerMonth> table = new Vector<DistancePerMonth>();
		String s_SQL_DISTANCE="select case when id_type_exercise < 100 then sum(distance)*"+GPSFIX+" else sum(distance) end as somma, " +
				"strftime('%m',watch_point_date) as mese from TRAINER_EXERCISE_DETT where strftime('%Y',watch_point_date)=strftime('%Y','now') group by strftime('%m',watch_point_date)" +
				" order by 2";
		Database oDB = new Database(oContext);
		
		try{
			
			for(int i=1;i<=12;i++){
				DistancePerMonth oDistance = new DistancePerMonth();
	   			oDistance.setsDistance("0");
	   			oDistance.setiMonth(i);
	   			table.add(oDistance);	
	   			////Log.v(Exercise.class.getCanonicalName(),"Add Month:"+i);
	   			oDistance=null;
			}
			
			oDB.open();
			Cursor oCursor = oDB.rawQuery(s_SQL_DISTANCE,null);
			if(oCursor!=null){      		
		   		int iSomma = oCursor.getColumnIndex("somma");
		   		int iMonthExercise = oCursor.getColumnIndex("mese");
		   		
		   		NumberFormat oNFormat = NumberFormat.getNumberInstance();
		   		oNFormat.setGroupingUsed(false);
		   		oNFormat.setMaximumFractionDigits(2);
				
		   		while(oCursor.moveToNext()){ 
		   			int iMonth = Integer.parseInt(oCursor.getString(iMonthExercise));
		   			double dDistance = oCursor.getLong(iSomma);
		   			if(oConfigTrainer.getiUnits()!=0){
		   				dDistance = (int) (dDistance * MILES_TO_KM);
		   				
		   			}
		   			
		   			
		   			String sDistance =oNFormat.format(dDistance); 
		   			
		   			for(int i=0;i<=11;i++){		   				
		   				if(table.get(i).getiMonth()==iMonth){		   					
		   					table.get(i).setsDistance(sDistance);
		   					Log.v(Exercise.class.getCanonicalName(),"Replace Value per Month:"+iMonth+" - "+sDistance);			   				
		   				}
		   			}		   			 			
		   		}
		   		oCursor.close();
			}
			oDB.close();
			oDB=null;
		}catch (Exception e) {
			Log.e(Exercise.class.getCanonicalName(),"getDistanceForMonth");
			e.printStackTrace();
			if(oDB!=null) oDB.close();
			oDB=null;
		}
		
		return table;	
	}
	/**
	 * Aggiunge un esercizio manuale
	 * 
	 * */
	public synchronized static boolean addManualWorkout(String Hours, String Minutes,String Weight,
			int Distance, String Kalories,double iKalories, String 
			Speed, String sNote, String sType, Context context,ConfigTrainer oConfigTrainer) {
		try{
			Database oDB = new Database(context);
			oDB.open();			
			
			//Log.v(Exercise.class.getCanonicalName(),"INSERT INTO trainer_exercise (id_users, id_type_exercise, end_date, " +
			//		"total_time, avg_speed, distance, weight, note) VALUES ("+oConfigTrainer.getiUserID()+","+sType+", current_date, '"+
			//		Hours+":"+Minutes+"','"+Speed+"','"+Distance+"',"+Weight+",'"+sNote.replaceAll("'", "''")+"')");
			if(oConfigTrainer.getiUnits()==1){
				//Insersce Miglia e le converto in KM
				Distance=(int) (Distance/MILES_TO_KM);
			}
			if(Hours.length()==1){
				Hours="0"+Hours;
			}
			if(Minutes.length()==1){
				Minutes="0"+Minutes;
			}
			oDB.getOpenedDatabase().execSQL("INSERT INTO trainer_exercise (id_users, id_type_exercise, end_date, " +
					"total_time, avg_speed, distance, calorie_burn,kalories, weight, note) VALUES ("+oConfigTrainer.getiUserID()+","+sType+", current_date, '"+
					Hours+":"+Minutes+":00','"+Speed+"','"+Distance+"','"+Kalories+" Kal',"+iKalories+","+Weight+",'"+sNote.replaceAll("'", "''")+"')");
					
			//Log.v(Exercise.class.getCanonicalName(),"INSERT INTO TRAINER_EXERCISE_DETT (id_exercise,id_users, id_type_exercise, distance,long,lat,alt,watch_point_date,watch_point_date_prev)  " +
			//		" VALUES ((select max(id_exercise) from TRAINER_EXERCISE),"+oConfigTrainer.getiUserID()+","+sType+",'"+Distance+"',0,0,0)");
			
			oDB.getOpenedDatabase().execSQL("INSERT INTO TRAINER_EXERCISE_DETT (id_exercise,id_users, id_type_exercise, distance,long, lat, alt,watch_point_date,watch_point_date_prev)  " +
					" VALUES ((select max(id_exercise) from TRAINER_EXERCISE) ,"+oConfigTrainer.getiUserID()+","+sType+",'"+Distance+"',0,0,0,current_timestamp, datetime('now','-1 minute'))");

			oDB.close();
			oDB=null;
		}catch (Exception e) {
			Log.e(Exercise.class.getCanonicalName(),"addManualWorkout Error. "+e.getMessage());
			return false;
		}
		return true;
	}

	public synchronized static int getInclination(Context oContext, String sIDExercise) {
		int iInclination=0;
		int iDistanceMT=0;
		int iPreAlt=0;
		int iAlt=0;
		String sSQL_Distance ="select (case when id_type_exercise < 100 then sum(distance)*"+GPSFIX+"*1000 else sum(distance)*1000 end) as meter from TRAINER_EXERCISE_DETT where id_exercise='"+sIDExercise+"' and id_watch_point >  "+ 
								" (select max(id_watch_point)-4 from TRAINER_EXERCISE_DETT where id_exercise='"+sIDExercise+"')";
	
		String sSQL_Distance_Pre_Alt ="select alt from TRAINER_EXERCISE_DETT where id_exercise='"+sIDExercise+"' and id_watch_point =  "+ 
				" (select max(id_watch_point)-4 from TRAINER_EXERCISE_DETT where id_exercise='"+sIDExercise+"')";

		String sSQL_Distance_Alt ="select alt from TRAINER_EXERCISE_DETT where id_exercise='"+sIDExercise+"' and id_watch_point =  "+ 
				" (select max(id_watch_point) from TRAINER_EXERCISE_DETT where id_exercise='"+sIDExercise+"')";

		try{
					
			Database oDB = new Database(oContext);
			
			oDB.open();
			Cursor oCursor = oDB.rawQuery(sSQL_Distance,null);
			if(oCursor!=null){      		
			   		int iMT = oCursor.getColumnIndex("meter");
		   			
		   			
			   		while(oCursor.moveToNext()){ 
			   			//Coordinate di avvio
			   			iDistanceMT=oCursor.getInt(iMT);
			   		}
			   		oCursor.close();
			}
			
			oCursor = oDB.rawQuery(sSQL_Distance_Pre_Alt,null);
			if(oCursor!=null){      		
		   			int icAlt = oCursor.getColumnIndex("alt");	   			
			   		while(oCursor.moveToNext()){ 
			   			//Coordinate di avvio
			   			iPreAlt=oCursor.getInt(icAlt);
			   		}
			   		oCursor.close();
			}
						  	
			oCursor = oDB.rawQuery(sSQL_Distance_Alt,null);
			if(oCursor!=null){      		
		   			int icAlt = oCursor.getColumnIndex("alt");	   			
			   		while(oCursor.moveToNext()){ 
			   			//Coordinate di avvio
			   			iAlt=oCursor.getInt(icAlt);
			   		}
			   		oCursor.close();
			}
			
			oDB.close();
		}catch(NullPointerException e){
			Log.e(Exercise.class.getCanonicalName(),"getInclination NullPointerException. "+e.getMessage());
			iInclination=0;
		}catch(SQLException e1){
			Log.e(Exercise.class.getCanonicalName(),"getInclination SQLException. "+e1.getMessage());
			iInclination=0;
		}
		try{
			iInclination=-(Math.round(((iPreAlt-iAlt)*100)/(iDistanceMT)));
		}catch (ArithmeticException e) {
			iInclination=-0;
		}

		return iInclination;
		
	}

	/**
	 * @param objActivity 
	 * 
	 * */
	public synchronized static void manualShare(Context oContext){
		Intent sharingIntent = new Intent(Intent.ACTION_SEND);
		sharingIntent.setType("text/plain");
		sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, "<p>This2 is the text that will be shared.</p>");
		sharingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		oContext.startActivity(Intent.createChooser(sharingIntent,"Share using"));
	}

	
	/**
	 * Aggiorna il flag del first boot per mostrare o nascondere la changelog
	 * 
	 * */
	public synchronized static void removeFirstBoot(Context oContext) {
		Database oDB = new Database(oContext);
		oDB.open();
		SQLiteDatabase oDataBase = oDB.getOpenedDatabase();
		oDataBase.execSQL("UPDATE TRAINER_CONFIG SET cfg_value=0 WHERE cfg_desc='first_boot'");
		oDB.close();
		oDB=null;
	}
	
	/**
	 * Aggiorna il flag del first boot per mostrare o nascondere la changelog
	 * 
	 * */
	public synchronized static void addFirstBoot(Context oContext) {
		Database oDB = new Database(oContext);
		oDB.open();
		SQLiteDatabase oDataBase = oDB.getOpenedDatabase();
		oDataBase.execSQL("UPDATE TRAINER_CONFIG SET cfg_value=1 WHERE cfg_desc='first_boot'");
		oDB.close();
		oDB=null;
	}
	
	public synchronized static boolean isFirstBoot(Context oContext){
		return isFirstBoot(oContext,null,null);
	}
	/**
	 * Controlla se il flag di firstboot e attivo o no
	 * 
	 * */
	public synchronized static boolean isFirstBoot(Context oContext, ConfigTrainer oConfigTrainer, String sVersionPackage) {
		int iFirstBootValue=0;
		Database oDB = new Database(oContext);
		Cursor oCursor =null;
		/*if(oConfigTrainer!=null){
			if(oConfigTrainer.getsVersionDesc().compareToIgnoreCase(sVersionPackage)!=0){
				return true;
			}
		}	*/		
		try{
			
			oDB.open();
			
			String sSQL_CURRENT_EXERCISE = "SELECT cfg_value FROM TRAINER_CONFIG WHERE cfg_desc='first_boot'";
			oCursor = oDB.rawQuery(sSQL_CURRENT_EXERCISE,null);
			if(oCursor!=null){      		
			   		int iFirstBoot = oCursor.getColumnIndex("cfg_value");
		   			
		   			
			   		while(oCursor.moveToNext()){ 
			   			//Coordinate di avvio
			   			iFirstBootValue=oCursor.getInt(iFirstBoot);	
			   			//Log.v(ExerciseUtils.class.getCanonicalName(),"First: "+iFirstBootValue);
			   		}
			   		oCursor.close();
			}
			
			oDB.close();
			
			oDB=null;
			if(iFirstBootValue==1){
				//Log.v(ExerciseUtils.class.getCanonicalName(),"First: true");
				return true;
			}else{
				//Log.v(ExerciseUtils.class.getCanonicalName(),"First: false");
				return false;
			}
		}catch (SQLException e) {
			if(oCursor!=null) oCursor.close();
			if(oDB!=null) oDB.close();
			
			Log.e(ExerciseUtils.class.getCanonicalName(),"isFirstBoot error");
			return false;
		}
		
	}

	public synchronized static double getVelocitaMedia(Context oContext) {
		double dVM=0.0;
		Database oDB = new Database(oContext);
		Cursor oCursor =null;
		try{
			
			oDB.open();
			
			String sSQL_CURRENT_VM = "select ((sum(distance*1000)/(abs(((strftime('%s',min(watch_point_date))-strftime('%s',max(watch_point_date)))))))*3.6) as vm  from trainer_exercise_dett where id_exercise=(select max(id_exercise) from trainer_exercise_dett)";
			oCursor = oDB.rawQuery(sSQL_CURRENT_VM,null);
			if(oCursor!=null){      		
			   		int iVM = oCursor.getColumnIndex("vm");
		   			
		   			
			   		while(oCursor.moveToNext()){ 
			   			//Coordinate di avvio
			   			dVM=oCursor.getDouble(iVM);	
			   			//Log.v(ExerciseUtils.class.getCanonicalName(),"Velocita' Media KM/H: "+dVM);
			   		}
			   		oCursor.close();
			}
			
			oDB.close();
			
			oDB=null;
			
			return dVM;
		}catch (SQLException e) {
			if(oCursor!=null) oCursor.close();
			if(oDB!=null) oDB.close();
			
			Log.e(ExerciseUtils.class.getCanonicalName(),"isFirstBoot error");
			return 0.0;
		}
	}

	
	public synchronized static Summary getTotalSummary(Context oContext) {
		ConfigTrainer oConfig = ExerciseUtils.loadConfiguration(oContext,true);
				
		Summary oSummary = new Summary();
		NumberFormat oNF = NumberFormat.getNumberInstance();
		oNF.setMaximumFractionDigits(2);
		
		double dKalories=0.0;
		double dDistance=0.0;
		int iStepsCount=0;
		Database oDB = new Database(oContext);
		Cursor oCursor =null;
		try{		
			oDB.open();
			
			String sSQL_CURRENT_VM = "select sum(kalories) as kal, case when id_type_exercise < 100 then sum(distance)*"+GPSFIX+" else sum(distance) end as dist, sum(steps_count) as steps from trainer_exercise";
			oCursor = oDB.rawQuery(sSQL_CURRENT_VM,null);
			if(oCursor!=null){      		
			   		int iKal = oCursor.getColumnIndex("kal");
			   		int iDist = oCursor.getColumnIndex("dist");
			   		int iSteps = oCursor.getColumnIndex("steps");
		   			
			   		while(oCursor.moveToNext()){ 
			   			//Coordinate di avvio
			   			dKalories=oCursor.getDouble(iKal);	
			   			dDistance=oCursor.getDouble(iDist);	
			   			iStepsCount=oCursor.getInt(iSteps);	
			   			//Log.v(ExerciseUtils.class.getCanonicalName(),"Velocita' Media KM/H: "+dVM);
			   		}
			   		oCursor.close();
			}
			
			oDB.close();
			
			oDB=null;
			oSummary.setsKalories(oNF.format(dKalories)+" Kcal");
			if(oConfig.getiUnits()==0){
				oSummary.setsDistance(oNF.format(dDistance)+" Km");
			}else{
				oSummary.setsDistance(oNF.format(dDistance)+" Mi");
			}
			oNF.setMaximumFractionDigits(0);
			
			oSummary.setdKalories(dKalories);
			oSummary.setdDistance(dDistance);
			oSummary.setiSteps(iStepsCount);
			oSummary.setsSteps(oNF.format(iStepsCount));
			return oSummary;
		}catch (SQLException e) {
			if(oCursor!=null) oCursor.close();
			if(oDB!=null) oDB.close();
			
			Log.e(ExerciseUtils.class.getCanonicalName(),"getTotalKalories error");
			return null;
		}catch (NullPointerException e) {
			if(oCursor!=null) oCursor.close();
			if(oDB!=null) oDB.close();
			Log.e(ExerciseUtils.class.getCanonicalName(),"getTotalKalories null error");
			return null;
		}catch (RuntimeException e) {
			if(oCursor!=null) oCursor.close();
			if(oDB!=null) oDB.close();
			Log.e(ExerciseUtils.class.getCanonicalName(),"RuntimeException RuntimeException error");
			return null;
		}
	}

	public synchronized static boolean shareTwitter(Context oContext,
			boolean TwitterShare) {
		SharedPreferences oPrefs = oContext.getSharedPreferences("aTrainer",Context.MODE_PRIVATE);
		SharedPreferences.Editor editPrefs = oPrefs.edit();
		editPrefs.putBoolean("share_twitter", true); 
		editPrefs.commit();
		Database oDB=null;
		try{
			int iTwitter=0;
			oDB = new Database(oContext);
				
			if(TwitterShare) iTwitter=1;
			
			String sSQL_UPDATE_USER = "UPDATE trainer_users SET twitter="+iTwitter;
			
			oDB.open();
			//Svuoto la tabella
			oDB.getOpenedDatabase().execSQL(sSQL_UPDATE_USER);			
			
			oDB.close();
			
			oDB=null;			
		}catch (Exception e) {
			Log.e(ExerciseUtils.class.getCanonicalName(), e.getMessage());
			if(oDB!=null) {
				oDB.close();
				oDB=null;	
			}			
			return false;
		}
		return true;
	}

	public synchronized static boolean shareFaceBook(Context oContext,
			boolean FaceBookShare) {
		Database oDB=null;
		SharedPreferences oPrefs = oContext.getSharedPreferences("aTrainer",Context.MODE_PRIVATE);
		SharedPreferences.Editor editPrefs = oPrefs.edit();
		editPrefs.putBoolean("share_fb", true); 
		editPrefs.commit();
		try{
			int iFaceBook=0;
			oDB = new Database(oContext);
				
			if(FaceBookShare) iFaceBook=1;
			
			String sSQL_UPDATE_USER = "UPDATE trainer_users SET facebook="+iFaceBook;
			
			oDB.open();
			//Svuoto la tabella
			oDB.getOpenedDatabase().execSQL(sSQL_UPDATE_USER);			
			
			oDB.close();
			
			oDB=null;			
		}catch (Exception e) {
			Log.e(ExerciseUtils.class.getCanonicalName(), e.getMessage());
			if(oDB!=null) {
				oDB.close();
				oDB=null;	
			}			
			return false;
		}
		return true;
	}
	
	/**
	 * //Esercizio presente il dettaglio ma non il sommario
		   //1) select distinct id_exercise from trainer_exercise_dett where id_exercise not in (select id_exercise from trainer_exercise)
		   //2) select sum(distance), id_exercise from trainer_exercise_dett where id_exercise in 
		   //    (select distinct id_exercise from trainer_exercise_dett where id_exercise not in (select id_exercise from trainer_exercise)) 
		   //    group by id_exercise
		   
		   //select id_exercise from TRAINER_EXERCISE where (end_date is null) or (distance=0)
	 * @param oConfigTrainer    
	 * */
	public synchronized static void checkIncompleteWorkout(Context oContext, ConfigTrainer oConfigTrainer) {
		Database oDB = new Database(oContext);
		boolean bRestoreWorkout=false;
    	try{  
    		oDB.open();
    		oDB.getOpenedDatabase().execSQL("delete from trainer_exercise_dett where distance=0");
    		oDB.getOpenedDatabase().execSQL("delete from trainer_exercise where end_date is null");
    		Cursor oCursor = oDB.rawQuery("select distinct id_exercise from trainer_exercise_dett where id_exercise not in (select id_exercise from trainer_exercise)",null);
			if(oCursor!=null){      			   			
			   		while(oCursor.moveToNext()){ 
			   			bRestoreWorkout=true;
			   		}
			   		oCursor.close();		
			}		
			if(bRestoreWorkout){
				oDB.getOpenedDatabase().execSQL("insert into trainer_exercise (id_exercise, id_users,id_type_exercise, creation_date,start_date,end_date,note,calorie_burn,distance,avg_speed,total_time,weight,kalories) " + 

		    			" select id_exercise, id_users,id_type_exercise, min(watch_point_date) as creation_date, min(watch_point_date) as start_date,max(watch_point_date) as end_date, 'restore workout' as note, (case when id_type_exercise=0 then (0.9*sum(distance)*"+oConfigTrainer.getiWeight()+") when id_type_exercise=1 then (0.105*sum(distance)*"+oConfigTrainer.getiWeight()+") else (0.45*sum(distance)*"+oConfigTrainer.getiWeight()+") end ) as calorie_burn, sum(distance) as distance,((sum(distance*1000)/(abs(((strftime('%s',min(watch_point_date))-strftime('%s',max(watch_point_date)))))))*3.6) as avg_speed, " +  
		    		
				    	" case when length(abs(((strftime('%s',max(watch_point_date))-strftime('%s',min(watch_point_date)))/3600))) = 1 then " + 
					    "     '0' || abs(((strftime('%s',max(watch_point_date))-strftime('%s',min(watch_point_date)))/3600)) else " + 
					    "     abs(((strftime('%s',max(watch_point_date))-strftime('%s',min(watch_point_date)))/3600)) end " + 
					    " || ':' || " + 
				        
		    			" case when length(CASE WHEN abs(((strftime('%s',max(watch_point_date))-strftime('%s',min(watch_point_date)))/60))>=60 then " + 
						"     abs(((strftime('%s',max(watch_point_date))-strftime('%s',min(watch_point_date)))/(60*(abs(((strftime('%s',max(watch_point_date))-strftime('%s',min(watch_point_date)))/3600))))))-60 else " + 
						"     abs(((strftime('%s',max(watch_point_date))-strftime('%s',min(watch_point_date)))/60)) end) = 1 then " + 
						"     '0' || CASE WHEN abs(((strftime('%s',max(watch_point_date))-strftime('%s',min(watch_point_date)))/60))>=60 then " + 
						"     abs(((strftime('%s',max(watch_point_date))-strftime('%s',min(watch_point_date)))/(60*(abs(((strftime('%s',max(watch_point_date))-strftime('%s',min(watch_point_date)))/3600))))))-60 else " + 
						"     abs(((strftime('%s',max(watch_point_date))-strftime('%s',min(watch_point_date)))/60)) end else " + 
						"     CASE WHEN abs(((strftime('%s',max(watch_point_date))-strftime('%s',min(watch_point_date)))/60))>=60 then " + 
						"     abs(((strftime('%s',max(watch_point_date))-strftime('%s',min(watch_point_date)))/(60*(abs(((strftime('%s',max(watch_point_date))-strftime('%s',min(watch_point_date)))/3600))))))-60 else " + 
						"     abs(((strftime('%s',max(watch_point_date))-strftime('%s',min(watch_point_date)))/60)) end end " + 
						" || ':' || " + 
					     
		    			" case when length(CASE WHEN abs(((strftime('%S',max(watch_point_date))-strftime('%S',min(watch_point_date)))))>=60 then " + 
						"     abs(((strftime('%S',max(watch_point_date))-strftime('%S',min(watch_point_date)))))/60 else " + 
						"     abs(((strftime('%S',max(watch_point_date))-strftime('%S',min(watch_point_date))))) end) = 1 then " + 
						"     '0' ||CASE WHEN abs(((strftime('%S',max(watch_point_date))-strftime('%S',min(watch_point_date)))))>=60 then " +  
						"     abs(((strftime('%S',max(watch_point_date))-strftime('%S',min(watch_point_date)))))/60 else " +  
						"     abs(((strftime('%S',max(watch_point_date))-strftime('%S',min(watch_point_date))))) end else " + 
						"     CASE WHEN abs(((strftime('%S',max(watch_point_date))-strftime('%S',min(watch_point_date)))))>=60 then " +  
						"     abs(((strftime('%S',max(watch_point_date))-strftime('%S',min(watch_point_date)))))/60 else " +  
						"     abs(((strftime('%S',max(watch_point_date))-strftime('%S',min(watch_point_date))))) end end as total_time, "+oConfigTrainer.getiWeight()+",(case when id_type_exercise=0 then (0.9*sum(distance)*"+oConfigTrainer.getiWeight()+") when id_type_exercise=1 then (0.105*sum(distance)*"+oConfigTrainer.getiWeight()+") else (0.45*sum(distance)*"+oConfigTrainer.getiWeight()+") end ) kalories from trainer_exercise_dett where id_exercise in " +  
		    			" 	      (select distinct id_exercise from trainer_exercise_dett where id_exercise not in " +  
		    			" 	      (select id_exercise from trainer_exercise)) " +  
		    			" 	       group by id_exercise, id_users,id_type_exercise");    
			}
			oDB.close();			
			oDB=null;
			
    						          		
    	}catch (SQLException e) {
    		Log.e(ExerciseUtils.class.getCanonicalName(),"Error restore workout");
    		oDB.close();   
        	oDB=null;
        	return;
		}   	
	}
	/**
	 * Ritorna il MAX id exercise 
	 * 
	 * */
	private static int getLastExercise(Context oContext){
		int iIDExercise=-2;
		Database oDB = new Database(oContext);
		try{
			
			oDB.open();
			
			String sSQL_CURRENT_EXERCISE="SELECT MAX(id_exercise) as exercise FROM trainer_exercise";
			Cursor oCursor = oDB.rawQuery(sSQL_CURRENT_EXERCISE, null);
			if(oCursor!=null){
				int iCurrentIDExercise = oCursor.getColumnIndex("exercise");
	            
	    		while(oCursor.moveToNext()){    
	    			iIDExercise=oCursor.getInt(iCurrentIDExercise);
	    			//Log.v(ExerciseUtils.class.getCanonicalName(), "ID EXERICE: "+iIDExercise);
	    		}
			}
			oCursor.close();
			oDB.close();
			oCursor=null;
			oDB=null;
			return iIDExercise;
		}catch (SQLException e) {
    		Log.e(ExerciseUtils.class.getCanonicalName(),"Error restore workout");
    		oDB.close();   
        	oDB=null;
        	return -2;
		}
	}

	/**
	 * Salve il GCM Id nelle shared preference per poi essere utilizzata dall'oggetto ConfigTrainer
	 * 
	 * 
	 * */
	public static void saveGCMId(Context oContext, String sGCMId) {
		SharedPreferences oPrefs = oContext.getSharedPreferences("aTrainer",Context.MODE_PRIVATE);
		SharedPreferences.Editor editPrefs = oPrefs.edit();
		editPrefs.putString("GCMId", sGCMId);
		editPrefs.commit();
	}
}
