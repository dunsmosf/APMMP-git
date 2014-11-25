package edu.virginia.dtc.MealActivity;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;


public class MealService extends Service{

	public void onCreate()
	{
		super.onCreate();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
