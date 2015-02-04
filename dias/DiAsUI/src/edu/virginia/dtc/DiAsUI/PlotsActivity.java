package edu.virginia.dtc.DiAsUI;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;

import com.androidplot.Plot;
import com.androidplot.series.XYSeries;
import com.androidplot.xy.BarFormatter;
import com.androidplot.xy.BarRenderer;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.RectRegion;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepFormatter;
import com.androidplot.xy.XLayoutStyle;
import com.androidplot.xy.XPositionMetric;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYRegionFormatter;
import com.androidplot.xy.XYStepMode;
import com.androidplot.xy.YValueMarker;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.State;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.Time;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class PlotsActivity extends Activity{
	
	public static final String TAG = "PlotsActivity";
	public static final boolean DEBUG = true;
	
	private int DIAS_STATE;
	
	// Variables needed to support the Plots Screen
    private long MAX_PLOT_STORAGE_DURATION_SECONDS = 24*3600;					// Store only 24 hours of plots data
	private Vector<Number> cgmTimes, cgmValues, insulinTimes, insulinValues,
		insulinTimes1, insulinValues1, mealTimes, mealValues, corrTimes, corrValues,
		deliveredTimes, deliveredValues, bolusTimes, bolusValues, stateValues,
		mealBolusTimes, mealBolusValues, corrBolusTimes, corrBolusValues;
	
	private int cgmCount, insulinCount;
	private XYSeries seriesCGM, seriesInsulin, seriesBasal, seriesMeal, seriesCorr, seriesDeliveredInsulin, seriesMealBolus, seriesCorrBolus;
	private LineAndPointFormatter seriesCGMFormat, seriesBasalFormat, seriesMealBolusFormat, seriesCorrBolusFormat;
	private BarFormatter seriesInsulinFormat, seriesMealFormat, seriesCorrFormat, seriesDeliveredInsulinFormat;
	
	private XYRegionFormatter 
		regionStopped = new XYRegionFormatter(Color.argb(100, 255, 0, 0)),
		regionOpen = new XYRegionFormatter(Color.argb(100, 0, 100, 255)), 
		regionSafetyOnly = new XYRegionFormatter(Color.argb(100, Color.red(COLOR_SAFETY), Color.green(COLOR_SAFETY), Color.blue(COLOR_SAFETY))),
		regionClosed = new XYRegionFormatter(Color.argb(100, 0, 200, 40)), 
		regionSensorOnly = new XYRegionFormatter(Color.argb(150, 255, 255, 0));
	
	private final boolean COLOR_REGIONS_BY_DIAS_STATE = true;
	
	// Static final indices to refer to data in the above arrays
	public static final int LOWER = 0, UPPER = 1, LEFT = 2, RIGHT = 3, VALUEOFMINY = 4, VALUEOFMINX = 5, VALUEOFMAXY = 6, VALUEOFMAXX = 7, WEIGHT = 8;
	
	// Colors for Regions
	private static final int COLOR_SAFETY = Color.rgb(186, 85, 211);
	
	// Plot manipulation data required that is not available in the XYPlot class
	private double[] cgmPlotBounds = new double[9];
	private double[] insulinPlotBounds = new double[9];
	private double[] highInsulinPlotBounds = new double[9];
	
	private int activePlot = R.id.insulinPlot;							// ID of the currently active (maximized) plot
	
	private static int DEFAULT_CYCLE_TIME = 5;							// The default length of a cycle
	public long TIME_REGION_IN_SECONDS = 3 * 60 * 60;					// Change the first factor to modify time values by the hour
	private boolean snapPlotsToRight = true;								// Should plots shift all the way to the right on each update?
	
	// Makes plots always rebuild on first loading program
	private	Number currentStateValue = -1;
	private	Number prevStateTime = 0;
	
	// Plot edit dialogs
	private static final int DIALOG_PLOTS_EDIT = 0;
	private static final int DIALOG_PLOTS_TIME_REGION = 1;
	
	private long currentTime;					// The current time in UTC seconds - may be real or simulated depending upon the SupervisorService mode
	
	private boolean plotsShown = false;
	
	private BroadcastReceiver TickReceiver;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		final String FUNC_TAG = "onCreate";
		
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.plotscreen);
		
		Debug.i(TAG, FUNC_TAG, "");
		
		TickReceiver = new BroadcastReceiver() {
			final String FUNC_TAG = "TickReceiver";
			
            @Override
            public void onReceive(Context context, Intent intent) {
            	
            	if(plotsShown)
            	{
            		snapPlotsToRight = true;
            		Debug.i(TAG, FUNC_TAG, "Updating plots on TICK!");
            		updatePlotData();
            		plotsBuild();
            	}
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("edu.virginia.dtc.intent.action.SUPERVISOR_TIME_TICK");
        registerReceiver(TickReceiver, filter);
		
		setResult(RESULT_CANCELED);		//Set the result to cancelled unless we actually send a bolus with the UI
		
		//Gather data passed with startup intent
		DIAS_STATE = getIntent().getIntExtra("state", State.DIAS_STATE_STOPPED);
		
		Debug.i(TAG, FUNC_TAG, "STATE: "+DIAS_STATE);
		
		initScreen();
		
		snapPlotsToRight = true;
		
		plotsShow(true);
		
		plotsShown = true;
		
		updatePlotData();
	}
	
	@Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
		final String FUNC_TAG = "onWindowFocusChanged";
    	super.onWindowFocusChanged(hasFocus);
    	
    	if(hasFocus)
    	{
    		Debug.i(TAG, FUNC_TAG, "M_HEIGHT: "+this.findViewById(R.id.plotLayout).getHeight()+" M_WIDTH: "+this.findViewById(R.id.plotLayout).getWidth());
    	}
    }
	
	@Override
	public void onStart()
	{
		super.onStart();
	}
	
	@Override
	public void onDestroy()
	{
		final String FUNC_TAG = "onDestroy";
		super.onDestroy();
		
		unregisterReceiver(TickReceiver);
	}
	
	// Creates a menu each time the menu button is pressed, different depending on current UI state
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	menu.clear();
        MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.plots, menu);
		return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) 
        {
    		case R.id.menuPlotsTimeRegion:
    			showDialog(DIALOG_PLOTS_TIME_REGION);
    			return true;
    		case R.id.menuPlotsCancel:
    			finish();
    			return true;
    		default:
    			return super.onOptionsItemSelected(item);
        }
    }
    
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        switch(id) 
        {
        	case DIALOG_PLOTS_EDIT:
	        	CharSequence[] items = {"Time region", "Plots", "Rebuild", "Close"};
	
	        	AlertDialog.Builder pEdit = new AlertDialog.Builder(getApplicationContext());
	        	pEdit.setTitle("Edit Plot Parameters");
	        	pEdit.setItems(items, new DialogInterface.OnClickListener() {
	        	    public void onClick(DialogInterface dialog, int item) {
	        	    	showDialog(item+1);
	        	    	if (item == 2){
	        	    		plotsShow(true);
	        	    	}
	        	    	dismissDialog(DIALOG_PLOTS_EDIT);
	        	    }
	        	});
	        	dialog = pEdit.create();
	        	break;
	        case DIALOG_PLOTS_TIME_REGION:
	        	final int[] timeRegions = {30*60, 60*60, 2*60*60, 3*60*60, 4*60*60, 6*60*60, 8*60*60, 12*60*60, 24*60*60};
	        	final CharSequence[] trItems = {"30 Minutes", "1 Hour", "2 Hours", "3 Hours", "4 Hours", "6 Hours", "8 Hours", "12 Hours", "24 Hours"};
	
	        	AlertDialog.Builder trEdit = new AlertDialog.Builder(this);
	        	trEdit.setTitle("Edit Time Region");
	        	trEdit.setItems(trItems, new DialogInterface.OnClickListener() {
	        	    public void onClick(DialogInterface dialog, int item) {
	        	    	setTimeRegionInSeconds(timeRegions[item]);
	        	    	updatePlots(true);
	        	    }
	        	});
	        	dialog = trEdit.create();
	        	break;
	        default:
	            dialog = null;
        }
        return dialog;
    }

	private void initScreen()
	{
		final String FUNC_TAG = "initScreen";
		
		WindowManager.LayoutParams params = getWindow().getAttributes();
		params.height = WindowManager.LayoutParams.MATCH_PARENT;
		params.width = getIntent().getIntExtra("width", 100);
		params.height = getIntent().getIntExtra("height", 100);
		params.height -= (0.03*params.height);		//Have to take off the stupid 3% for sizing to work
		this.getWindow().setAttributes(params);
		
		// Make plots invisible to start
	 	XYPlot cgmPlot = (XYPlot) findViewById(R.id.cgmPlot);
	 	cgmPlot.setVisibility(View.INVISIBLE);
	 	XYPlot insulinPlot = (XYPlot) findViewById(R.id.insulinPlot);
	 	insulinPlot.setVisibility(View.INVISIBLE);
	 	XYPlot highInsulinPlot = (XYPlot) findViewById(R.id.highInsulinPlot);
	 	highInsulinPlot.setVisibility(View.INVISIBLE);
	 	
		// Set initial bounds for plots
		cgmPlotBounds[LOWER] = 0;
		
		if(Params.getInt(getContentResolver(), "blood_glucose_display_units", 0) == CGM.BG_UNITS_MMOL_PER_L)
		{
			cgmPlotBounds[UPPER] = (double)350/CGM.MGDL_PER_MMOLL;
			cgmPlot.setTitle("CGM mmol/l");
		}
		else
		{
			cgmPlotBounds[UPPER] = 350;
			cgmPlot.setTitle("CGM mg/dl");
		}
		
		cgmPlotBounds[RIGHT] = getCurrentTimeSeconds();
		cgmPlotBounds[LEFT] = cgmPlotBounds[RIGHT] - TIME_REGION_IN_SECONDS;
		
		insulinPlotBounds[LOWER] = 0;
		insulinPlotBounds[UPPER] = 12.0f;
		insulinPlotBounds[LEFT] = cgmPlotBounds[LEFT];
		insulinPlotBounds[RIGHT] = cgmPlotBounds[RIGHT];
		
		highInsulinPlotBounds[LOWER] = 0;
		highInsulinPlotBounds[UPPER] = 6;
		highInsulinPlotBounds[LEFT] = cgmPlotBounds[LEFT];
		highInsulinPlotBounds[RIGHT] = cgmPlotBounds[RIGHT];
		
		// Set touch listeners for plots
		cgmPlot.setLongClickable(true);
		insulinPlot.setLongClickable(true);
		highInsulinPlot.setLongClickable(true);
		
		ZoomScrollListener cgmListener = new ZoomScrollListener(this, true, true, false);
		ZoomScrollListener insListener = new ZoomScrollListener(this, true, false, true);
		ZoomScrollListener highInsListener = new ZoomScrollListener(this, true, false, true);
		
		cgmPlot.setOnTouchListener(cgmListener);
		cgmPlot.setOnLongClickListener(cgmListener);
		
		insulinPlot.setOnTouchListener(insListener);
		insulinPlot.setOnLongClickListener(insListener);
		
		highInsulinPlot.setOnTouchListener(highInsListener);
		highInsulinPlot.setOnLongClickListener(highInsListener);
		
		// Create blank containers for plot data
		cgmTimes = new Vector<Number>();
		cgmValues = new Vector<Number>();
		stateValues = new Vector<Number>();
		insulinTimes = new Vector<Number>();
		insulinValues = new Vector<Number>();
		insulinTimes1 = new Vector<Number>();
		insulinValues1 = new Vector<Number>();
		mealTimes = new Vector<Number>();
		mealValues = new Vector<Number>();
		corrTimes = new Vector<Number>();
		corrValues = new Vector<Number>();
		deliveredTimes = new Vector<Number>();
		deliveredValues = new Vector<Number>();
		bolusTimes = new Vector<Number>();
		bolusValues = new Vector<Number>();
		mealBolusTimes = new Vector<Number>();
		mealBolusValues = new Vector<Number>();
		corrBolusTimes = new Vector<Number>();
		corrBolusValues = new Vector<Number>();
	}
	
	public void plotsBuild() 
	{
		final String FUNC_TAG = "plotsBuild";
		
   	 	// Move CGM plot for sensor-only mode
   	 	switch (DIAS_STATE) 
   	 	{
			case State.DIAS_STATE_SENSOR_ONLY:
				break;
			default:
				break;
   	 	}
	 	
   	 	// Fetch CGM data
   	 	cgmTimes = new Vector<Number>();
   	 	cgmValues = new Vector<Number>();
   	 	stateValues = new Vector<Number>();
   	 	
   	 	Debug.i(TAG, FUNC_TAG, "Get CGM data with " + Biometrics.CGM_URI);
   	 	
   	 	String[] projCGM = {"time", "cgm", "diasState"};
   	 	Long earliest_plot_time = getCurrentTimeSeconds()-MAX_PLOT_STORAGE_DURATION_SECONDS;
   	 	Cursor c=getContentResolver().query(Biometrics.CGM_URI, projCGM, "time > "+earliest_plot_time.toString(), null, null);
   	 	
		Debug.i(TAG, FUNC_TAG, "CGM > c.getCount="+c.getCount());
		
		cgmCount = c.getCount();
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
		int ii = 0;
		prevStateTime = 0;
		
		seriesCGMFormat = new LineAndPointFormatter(Color.rgb(80, 80, 80), 		// Line color
			Color.rgb(255, 255, 255), 											// Point color
			Color.argb(100, 240, 240, 240)); 									// White fill
	   	 
	   	 if (c.moveToFirst()) 
	   	 {
	   		 do 
	   		 {
	 			// Do not display CGM points with value indicating Noise or No Antenna
 				currentTime = c.getLong(c.getColumnIndex("time"));
 				cgmTimes.addElement(currentTime);
 				
 				double value = c.getDouble(c.getColumnIndex("cgm"));
 				if(Params.getInt(getContentResolver(), "blood_glucose_display_units", 0) == CGM.BG_UNITS_MMOL_PER_L)
 					value = (double)value/CGM.MGDL_PER_MMOLL;
 				
 				cgmValues.addElement(value);
 				currentStateValue = c.getInt(c.getColumnIndex("diasState"));
 				stateValues.addElement(currentStateValue);
 				if (COLOR_REGIONS_BY_DIAS_STATE) {
// 	  				Debug.i(TAG, FUNC_TAG, "seriesCGMFormat="+seriesCGMFormat+", prevStateTime="+prevStateTime+", currentTime="+currentTime);
 	  				RectRegion region = new RectRegion(prevStateTime, currentTime, 0, 600);
					switch (currentStateValue.intValue()) 
					{
						case State.DIAS_STATE_STOPPED:
							seriesCGMFormat.addRegion(region, regionStopped);
							break;
						case State.DIAS_STATE_OPEN_LOOP:
							seriesCGMFormat.addRegion(region, regionOpen);
							break;
						case State.DIAS_STATE_CLOSED_LOOP:
							seriesCGMFormat.addRegion(region, regionClosed);
							break;
						case State.DIAS_STATE_SAFETY_ONLY:
							seriesCGMFormat.addRegion(region, regionSafetyOnly);
							break;
						case State.DIAS_STATE_SENSOR_ONLY:
							seriesCGMFormat.addRegion(region, regionSensorOnly);
							break;
					}
					prevStateTime = currentTime;
 				}
 	 			ii++;
	   		 } 
	   		 while (c.moveToNext());
 		}
 		c.close();
 		cgmCount = ii;
	 		
 		Debug.i(TAG, FUNC_TAG, "cgmCount: "+cgmCount);
 		
 		// Fetch Insulin data
		Debug.i(TAG, FUNC_TAG, "Get Insulin data with " + Biometrics.INSULIN_URI);
		
		insulinTimes = new Vector<Number>();
		insulinValues = new Vector<Number>();
		String[] projIns = {"deliv_time", "deliv_basal"};
		Cursor c1 = getContentResolver().query(Biometrics.INSULIN_URI, projIns, "deliv_time > "+earliest_plot_time.toString(), null, null);
		
		Debug.i(TAG, FUNC_TAG, "INSULIN > c1.getCount=" + c1.getCount());
		
		insulinCount = c1.getCount();
		ii = 0;
		if(c1.moveToFirst()) {
			do{ 
				// Incoming time in seconds
				insulinTimes.addElement(c1.getLong(c1.getColumnIndex("deliv_time"))+150);
				insulinValues.addElement(c1.getDouble(c1.getColumnIndex("deliv_basal"))*12);
				ii++;
			} while (c1.moveToNext());
		}
		c1.close();
		insulinCount = ii;
		
		// Handle various cases
		int jj = 0;
		int kk = 0;
		if (insulinCount == 0 && cgmCount == 0) {
			double tLeft = cgmPlotBounds[LEFT];
			double tRight = cgmPlotBounds[RIGHT];
			double t = tLeft;
			insulinTimes1 = new Vector<Number>();
			insulinValues1 = new Vector<Number>();
			while (t < tRight) {
				cgmTimes.addElement((long)t);
				insulinTimes.addElement((long)t);
				insulinTimes1.addElement((long)t);
				cgmValues.addElement(0);
				insulinValues.addElement(0);
				insulinValues1.addElement(0);
				t=t+DEFAULT_CYCLE_TIME;
			}
		}
		else if (insulinCount == 0 && cgmCount > 0) {
			insulinTimes = (Vector)cgmTimes.clone();
			insulinValues = new Vector<Number>();
			insulinTimes1 = (Vector)cgmTimes.clone();
			insulinValues1 = new Vector<Number>();
			jj = 0;
			while (jj < insulinTimes.size()) {
				insulinValues.addElement(0);
				insulinValues1.addElement(0);
				jj++;
			}
		}
		else if (insulinCount > 0 && cgmCount == 0) {
			Debug.i(TAG, FUNC_TAG, "NO CGM DATA, but there is insulin data!");
			
			cgmTimes = (Vector)insulinTimes.clone();
			cgmValues = new Vector<Number>();
			jj = 0;
			while (jj < cgmTimes.size()) {
				cgmValues.addElement(0);
				jj++;
			}
			
			jj = 0;
			kk = 0;
			for (kk = 0; jj + kk < insulinCount; kk++) {
				insulinTimes1.add(insulinTimes.get(kk));
				Debug.i(TAG, FUNC_TAG, "jj=" + jj + ", kk=" + kk + ", cgmCount=" + cgmCount+ ", insulinCount=" + insulinCount);
				insulinValues1.add(insulinValues.get(kk));
			}
		}
		else {
			while (jj < cgmCount && cgmTimes.get(jj).longValue() < insulinTimes.get(0).longValue())
				jj++;
			insulinCount = insulinCount + jj;
			insulinTimes1 = new Vector<Number>();
			insulinValues1 = new Vector<Number>();
			jj = 0;
			
			// If there is more CGM data than insulin data left pad the insulin data with CGM times and zero values
			while (jj < cgmCount && cgmTimes.get(jj).longValue() < insulinTimes.get(0).longValue()) {
				Debug.i(TAG, FUNC_TAG, "cgmTimes[" + jj + "].longValue()="
						+ cgmTimes.get(jj).longValue()
						+ ", insulinTimes[0].longValue()="
						+ insulinTimes.get(0).longValue());
				insulinTimes1.addElement(cgmTimes.get(jj));
				insulinValues1.addElement(0);
				jj++;
			}
			c1.close();
			insulinCount = ii;
			
			jj = 0;
			kk = 0;
			if (insulinCount > 0)
			{
				while (jj < cgmCount && cgmTimes.get(jj).longValue() < insulinTimes.get(0).longValue())
					jj++;
				insulinCount = insulinCount + jj;
				insulinTimes1 = new Vector<Number>();
				insulinValues1 = new Vector<Number>();
				jj = 0;
				while (jj < cgmCount && cgmTimes.get(jj).longValue() < insulinTimes.get(0).longValue()) {
					Debug.i(TAG, FUNC_TAG, "cgmTimes[" + jj + "].longValue()="+ cgmTimes.get(jj).longValue()+ ", insulinTimes[0].longValue()="+ insulinTimes.get(0).longValue());
					insulinTimes1.addElement(cgmTimes.get(jj));
					insulinValues1.addElement(0);
					jj++;
				}
				for (kk = 0; jj + kk < insulinCount; kk++) {
					insulinTimes1.add(insulinTimes.get(kk));
					Debug.i(TAG, FUNC_TAG, "jj=" + jj + ", kk=" + kk + ", cgmCount=" + cgmCount+ ", insulinCount=" + insulinCount);
					insulinValues1.add(insulinValues.get(kk));
				}
			}			
		}
			
		  	// Fetch Meal bolus data
			Debug.i(TAG, FUNC_TAG, "Get Meal bolus data with " + Biometrics.INSULIN_URI);
			mealTimes = new Vector<Number>();
			mealValues = new Vector<Number>();
		 	String[] projMeal = {"deliv_time","deliv_meal"};
			Cursor c2 = getContentResolver().query(Biometrics.INSULIN_URI, projMeal, "deliv_time > "+earliest_plot_time.toString(), null, null);
			Debug.i(TAG, FUNC_TAG, "MEAL BOLUS > c2.getCount=" + c2.getCount());
			ii = 0;
			if(c2.moveToFirst()) 
			{
				do{ 
					// Incoming time in seconds
					double val = c2.getDouble(c2.getColumnIndex("deliv_meal"));
					mealTimes.addElement(c2.getLong(c2.getColumnIndex("deliv_time")));
					mealValues.addElement(c2.getDouble(c2.getColumnIndex("deliv_meal")));
					ii++;
				} while (c2.moveToNext());
			}
			c2.close();
				
			// Fetch Correction bolus data
			Debug.i(TAG, FUNC_TAG, "Get Corr bolus data with " + Biometrics.INSULIN_URI);
			corrTimes = new Vector<Number>();
			corrValues = new Vector<Number>();
		 	String[] projCorr = {"deliv_time","deliv_corr"};
			Cursor c3 = getContentResolver().query(Biometrics.INSULIN_URI, projCorr, "deliv_time > "+earliest_plot_time.toString(), null, null);
			Debug.i(TAG, FUNC_TAG, "CORR BOLUS > c3.getCount=" + c3.getCount());
			ii = 0;
			if(c3.moveToFirst()) {
				do{ 
					// Incoming time in seconds
					corrTimes.addElement(c3.getLong(c3.getColumnIndex("deliv_time")));
					corrValues.addElement(c3.getDouble(c3.getColumnIndex("deliv_corr")));
					Debug.i(TAG, FUNC_TAG, "CORR: "+c3.getDouble(c3.getColumnIndex("deliv_corr")));
					ii++;
				} while (c3.moveToNext());
			}
			c3.close();
				
			bolusTimes = new Vector<Number>();
			bolusValues = new Vector<Number>();
			mealBolusTimes = new Vector<Number>();
			mealBolusValues = new Vector<Number>();
			corrBolusTimes = new Vector<Number>();
			corrBolusValues = new Vector<Number>();
			for (int nn=0; nn<mealTimes.size(); nn++) {
				if (mealValues.get(nn).doubleValue() > 0) {
					double bolus_marker_initial = Math.min(mealValues.get(nn).doubleValue(), 12.0);
					double bolus_marker = 0.0;
					while (bolus_marker < 12) 
					{
						mealBolusTimes.addElement(mealTimes.get(nn));
						mealBolusValues.addElement(bolus_marker);
						
						if(bolus_marker < bolus_marker_initial-0.1)
							bolus_marker+=0.1;
						else
							bolus_marker+=0.8;
						
						if(bolus_marker > 12)
							bolus_marker = 12;
					}
				}
			}
			for (int nn=0; nn<corrTimes.size(); nn++) {
				if (corrValues.get(nn).doubleValue() > 0) {
					double bolus_marker_initial = Math.min(corrValues.get(nn).doubleValue(), 12.0);
					double bolus_marker = 0.0;
					while (bolus_marker < 12) 
					{
						corrBolusTimes.addElement(corrTimes.get(nn));
						corrBolusValues.addElement(bolus_marker);
						
						if(bolus_marker < bolus_marker_initial-0.1)
							bolus_marker+=0.1;
						else
							bolus_marker+=0.8;
						
						if(bolus_marker > 12)
							bolus_marker = 12;
					}
				}
			}
    }
	
	public void plotsShow(boolean rebuild) 
	{
		final String FUNC_TAG = "plotsShow";
		
		if (rebuild)
			plotsBuild();
		
		if (cgmValues.size() > 0 || insulinValues.size() > 0) 
		{
			Debug.i(TAG, FUNC_TAG, "plotsShow");
			Debug.i(TAG, FUNC_TAG, "Rebuild plots=" + rebuild);
			
			if (cgmValues.size() > 0) 
			{
				// ***** CGM Plot *****
				seriesCGM = new SimpleXYSeries(cgmTimes, cgmValues, "");
				XYPlot cgmPlot = (XYPlot) findViewById(R.id.cgmPlot);
				cgmPlot.setClickable(true);

				cgmPlot.getGraphWidget().getGridBackgroundPaint().setColor(Color.BLACK);
				cgmPlot.getGraphWidget().getGridLinePaint().setColor(0x6000ff80);
				cgmPlot.getGraphWidget().getGridLinePaint().setAlpha(128);
				cgmPlot.getGraphWidget().getDomainOriginLinePaint().setColor(Color.BLACK);
				cgmPlot.getGraphWidget().getRangeOriginLinePaint().setColor(Color.BLACK);
				cgmPlot.getGraphWidget().setPaddingRight(5);

				cgmPlot.setBorderStyle(Plot.BorderStyle.SQUARE, null, null);
    			cgmPlot.getBorderPaint().setStrokeWidth(1);
    			cgmPlot.getBorderPaint().setAntiAlias(false);
    			cgmPlot.getBorderPaint().setColor(Color.WHITE);

    			// Setup our line fill paint to be a slightly transparent gradient:
    			Paint lineFill = new Paint();
    			lineFill.setAlpha(200);
    			lineFill.setShader(new LinearGradient(0, 0, 0, 250, Color.WHITE, Color.GREEN, Shader.TileMode.MIRROR));

    			// cgmPlot.addSeries(seriesCGM, seriesCGMFormat);
    			// Draw a domain tick for each hour interval:
    			cgmPlot.setDomainStep(XYStepMode.SUBDIVIDE, 7);

    			// Customize our domain/range labels
    			cgmPlot.getDomainLabelWidget().setVisible(false);
    			cgmPlot.getRangeLabelWidget().setVisible(false);

    			// Get rid of decimal points in our range labels:
    			cgmPlot.setRangeValueFormat(new DecimalFormat("0"));
    			cgmPlot.setDomainValueFormat(new MyDateFormat());
    			cgmPlot.setRangeStep(XYStepMode.SUBDIVIDE, 7);
    			
    			// Deal with legend
    			cgmPlot.getLegendWidget().setVisible(false);

    			// Get values at the extremities of the plot to prevent removing points during scaling
    			Debug.i(TAG, FUNC_TAG, "cgmValues.size()="+cgmValues.size()+", cgmTimes.size()="+cgmTimes.size()+", cgmCount="+cgmCount);
    			
    			double min = cgmValues.get(0).doubleValue();
    			double max = cgmValues.get(cgmValues.size() - 1).doubleValue();
    			if(Params.getInt(getContentResolver(), "blood_glucose_display_units", 0) == CGM.BG_UNITS_MMOL_PER_L)
    			{
    				min = (double)min/CGM.MGDL_PER_MMOLL;
    				max = (double)max/CGM.MGDL_PER_MMOLL;
    			}
    			
    			cgmPlotBounds[VALUEOFMINX] = min;
    			cgmPlotBounds[VALUEOFMAXX] = max;
    			
    			int divisor = 50;
    			if(Params.getInt(getContentResolver(), "blood_glucose_display_units", 0) == CGM.BG_UNITS_MMOL_PER_L)
    				divisor = 3;
    			
    			// Set CGM upper bound and subdivisions
    			int cgmPlotSteps = (int)(Math.floor(minmax(cgmValues, true).doubleValue()/divisor)+2);
    			int cgmPlotStepsMAX = (DIAS_STATE == State.DIAS_STATE_SENSOR_ONLY) ? 9 : 6;
    			if (cgmPlotSteps < cgmPlotStepsMAX)
    				cgmPlotSteps = cgmPlotStepsMAX;
    			
    			cgmPlotBounds[VALUEOFMAXY] = (double)(divisor*(cgmPlotSteps-1));
    			cgmPlot.setRangeStep(XYStepMode.SUBDIVIDE, cgmPlotSteps);
    			cgmPlotBounds[UPPER] = (double)(divisor*(cgmPlotSteps-1));
    			              
    			// Set original time bounds
    			if (snapPlotsToRight){
    				cgmPlotBounds[RIGHT] = getCurrentTimeSeconds();
    				cgmPlotBounds[LEFT] = cgmPlotBounds[RIGHT] - TIME_REGION_IN_SECONDS;
    			}
    			
    			// Set bounds values
    			cgmPlot.setRangeBoundaries(cgmPlotBounds[LOWER], cgmPlotBounds[UPPER],BoundaryMode.FIXED);
    			cgmPlot.setDomainBoundaries(cgmPlotBounds[LEFT], cgmPlotBounds[RIGHT],BoundaryMode.FIXED);

    			// By default, AndroidPlot displays developer guides to aid in laying out your plot.
    			// To get rid of them call disableAllMarkup():
    			cgmPlot.disableAllMarkup();
    			cgmPlot.setVisibility(View.VISIBLE);
    			cgmPlot.addMarker(new YValueMarker(70, // y-val to mark
    					"", 							// marker label
    					new XPositionMetric( 			// object instance to set text positioning on the marker
    							3, 						// 3 pixel positioning offset
    							XLayoutStyle.ABSOLUTE_FROM_LEFT // how/where the positioning offset is applied
    					), Color.WHITE, // line paint color
    					Color.BLACK // text paint color
    			));
    			cgmPlot.addMarker(new YValueMarker(180, // y-val to mark
    					"", // marker label
    					new XPositionMetric( // object instance to set text positioning on the marker
    							3, // 3 pixel positioning offset
    							XLayoutStyle.ABSOLUTE_FROM_LEFT // how/where the positioning offset is applied
    					), Color.WHITE, // line paint color
    					Color.BLACK // text paint color
    			));

	    	}
    	    if (insulinValues.size() > 0) 
    	    {
	    		Debug.i(TAG, FUNC_TAG, "ADDING INSULIN");
    	    	
    			// ***** Insulin Plot *****
    			// Get profile basal data
    			Vector<Number> basalTimes = new Vector<Number>();
    			Vector<Number> basalValues = new Vector<Number>();
    			TimeZone tz = TimeZone.getDefault();
    			int UTC_offset_secs = tz.getOffset(currentTime*1000)/1000;
    			int timeNowSecs = (int)(currentTime+UTC_offset_secs)%86400;
    			long currentDaySecs = currentTime - timeNowSecs;
    			Cursor c = getContentResolver().query(Biometrics.BASAL_PROFILE_URI, null, null, null,	null);
    			int ii = 0;
    			if(c.moveToFirst()) {
    				do
    				{ // Incoming time in seconds
    					// Set imaginary values at 0 to extend stepline "infinitely"
    					if (ii == 0)
    						basalTimes.add(0);
    					basalTimes.add(currentDaySecs + 60*c.getLong(c.getColumnIndex("time"))+1);
    					basalValues.add(c.getDouble(c.getColumnIndex("value")));
    					if (ii == 0){
    						basalValues.add(basalValues.get(0));
    						ii++;
    					}
    					Debug.i(TAG, FUNC_TAG,"currentTime="+currentTime+", time["+ii+"]="+basalTimes.get(ii)+", basal["+ii+"]="+basalValues.get(ii));
    					ii++;
    				} while (c.moveToNext());
    				// Set imaginary values at Long.MAX to extend stepline "infinitely"
    				basalTimes.add(Long.MAX_VALUE);
    				basalValues.add(basalValues.get(ii-1));
    			}
    			c.close();
    			seriesInsulin = new SimpleXYSeries(insulinTimes1, insulinValues1, "");
    			seriesMealBolus = new SimpleXYSeries(mealBolusTimes, mealBolusValues, "");
    			seriesCorrBolus = new SimpleXYSeries(corrBolusTimes, corrBolusValues, "");
    			seriesBasal = new SimpleXYSeries(basalTimes, basalValues, "");
    			XYPlot insulinPlot = (XYPlot) findViewById(R.id.insulinPlot);
				insulinPlot.setClickable(true);
				insulinPlot.getGraphWidget().getGridBackgroundPaint().setColor(Color.BLACK);
				insulinPlot.getGraphWidget().getGridLinePaint().setColor(0x6000ff80);
				insulinPlot.getGraphWidget().getGridLinePaint().setAlpha(128);
				insulinPlot.getGraphWidget().getDomainOriginLinePaint().setColor(Color.BLACK);
				insulinPlot.getGraphWidget().getRangeOriginLinePaint().setColor(Color.BLACK);
				insulinPlot.getGraphWidget().setPaddingRight(5);

				insulinPlot.setBorderStyle(Plot.BorderStyle.SQUARE, null, null);
				insulinPlot.getBorderPaint().setStrokeWidth(1);
				insulinPlot.getBorderPaint().setAntiAlias(false);
				insulinPlot.getBorderPaint().setColor(Color.WHITE);

				// Create a formatter to use for drawing a series using
				// BarFormatter:
				seriesInsulinFormat = new BarFormatter(Color.argb(255, 50, 150, 255), Color.argb(255, 50, 150, 255));
				seriesBasalFormat = new StepFormatter(Color.argb(255, 150, 150, 255), Color.argb(0, 150, 150, 255));
				seriesBasalFormat.setVertexPaint(new Paint(Color.argb(255, 100, 100, 100)));
				
				// bolus dot color
				seriesMealBolusFormat = new LineAndPointFormatter(Color.argb(0, 0, 0,0), Color.argb(255, 0, 255, 0), Color.argb(0, 0, 0, 0));
				seriesCorrBolusFormat = new LineAndPointFormatter(Color.argb(0, 0, 0,0), Color.argb(255, 255, 100, 0), Color.argb(0, 0, 0, 0));
				insulinPlot.removeSeries(seriesBasal);
				insulinPlot.addSeries(seriesInsulin, seriesInsulinFormat);
				insulinPlot.addSeries(seriesMealBolus, seriesMealBolusFormat);
				insulinPlot.addSeries(seriesCorrBolus, seriesCorrBolusFormat);
				insulinPlot.addSeries(seriesBasal, seriesBasalFormat);
				// Scale basal insulin bars to time region
				((BarRenderer) insulinPlot.getRenderer(BarRenderer.class)).setBarWidth((float)(11*(double)10800/TIME_REGION_IN_SECONDS));
				// Draw a domain tick for each hour interval
				insulinPlot.setDomainStep(XYStepMode.SUBDIVIDE, 7);

				// Customize our domain/range labels
				insulinPlot.getDomainLabelWidget().setVisible(false);
				insulinPlot.getRangeLabelWidget().setVisible(false);

				// Set up range and labels:
				insulinPlot.setRangeValueFormat(new DecimalFormat("0.00"));
				insulinPlot.setDomainValueFormat(new MyDateFormat());
				insulinPlot.setRangeStep(XYStepMode.SUBDIVIDE, 7);
				
				// Deal with legend
				insulinPlot.getLegendWidget().setVisible(false);

				// Get values at the extremities of the plot to prevent removing points
				// During scaling
				if (insulinCount > 0){
					insulinPlotBounds[VALUEOFMINX] = insulinValues.get(0).doubleValue();
					insulinPlotBounds[VALUEOFMAXX] = insulinValues.get(insulinValues.size() - 1).doubleValue();
				} else {
					insulinPlotBounds[VALUEOFMINX] = 0;
					insulinPlotBounds[VALUEOFMAXX] = 0;			
				}
				insulinPlotBounds[VALUEOFMAXY] = minmax(insulinValues, true).doubleValue();
				// Set plot bounds; time bounds should be the same on both cgm and insulin plots
				insulinPlotBounds[RIGHT] = cgmPlotBounds[RIGHT];
				insulinPlotBounds[LEFT] = cgmPlotBounds[LEFT];
				insulinPlot.setRangeBoundaries(insulinPlotBounds[LOWER], insulinPlotBounds[UPPER], BoundaryMode.FIXED);
				insulinPlot.setDomainBoundaries(insulinPlotBounds[LEFT], insulinPlotBounds[RIGHT], BoundaryMode.FIXED);

				// By default, AndroidPlot displays developer guides to aid in laying out your plot
				// To get rid of them call disableAllMarkup()
				insulinPlot.disableAllMarkup();
				insulinPlot.setVisibility(View.VISIBLE);

				XYPlot highInsulinPlot = (XYPlot) findViewById(R.id.highInsulinPlot);
				highInsulinPlot.setClickable(true);
				highInsulinPlot.getGraphWidget().getGridBackgroundPaint().setColor(Color.BLACK);
				highInsulinPlot.getGraphWidget().getGridLinePaint().setColor(0x6000ff80);
				highInsulinPlot.getGraphWidget().getGridLinePaint().setAlpha(128);
				highInsulinPlot.getGraphWidget().getDomainOriginLinePaint().setColor(Color.BLACK);
				highInsulinPlot.getGraphWidget().getRangeOriginLinePaint().setColor(Color.BLACK);
				highInsulinPlot.getGraphWidget().setPaddingRight(5);			

				highInsulinPlot.setBorderStyle(Plot.BorderStyle.SQUARE, null, null);
				highInsulinPlot.getBorderPaint().setStrokeWidth(1);
				highInsulinPlot.getBorderPaint().setAntiAlias(false);
				highInsulinPlot.getBorderPaint().setColor(Color.WHITE);

				// Use meal bolus data and correction bolus data separately
				seriesMeal = new SimpleXYSeries(mealTimes, mealValues, "");
				seriesCorr = new SimpleXYSeries(corrTimes, corrValues, "");
				seriesDeliveredInsulin = new SimpleXYSeries(deliveredTimes, deliveredValues, "");
				// Meal bolus color
				seriesMealFormat = new BarFormatter(Color.argb(200, 150, 50, 255), Color.argb(200, 150, 50, 255));
				// Corr bolus color
				seriesCorrFormat = new BarFormatter(Color.argb(200, 0, 255, 127), Color.argb(200, 0, 255, 127));
				// Delivered insulin color
				seriesDeliveredInsulinFormat = new BarFormatter(Color.argb(200, 200, 100, 20), Color.argb(200, 200, 100, 20));
				highInsulinPlot.addSeries(seriesMeal, seriesMealFormat);
				highInsulinPlot.addSeries(seriesCorr, seriesCorrFormat);
				highInsulinPlot.addSeries(seriesDeliveredInsulin, seriesDeliveredInsulinFormat);
				if (TIME_REGION_IN_SECONDS > 10800)
					((BarRenderer) highInsulinPlot.getRenderer(BarRenderer.class)).setBarWidth((float)(11*(double)10800/TIME_REGION_IN_SECONDS));
				else
					((BarRenderer) highInsulinPlot.getRenderer(BarRenderer.class)).setBarWidth(11f);

				// Draw a domain tick for each hour interval
				highInsulinPlot.setDomainStep(XYStepMode.SUBDIVIDE, 7);	

				// Customize our domain/range labels
				highInsulinPlot.getDomainLabelWidget().setVisible(false);
				highInsulinPlot.getRangeLabelWidget().setVisible(false);
		
				// Get rid of decimal points in our range labels:
				highInsulinPlot.setRangeValueFormat(new DecimalFormat("0.00"));
				highInsulinPlot.setDomainValueFormat(new MyDateFormat());
				highInsulinPlot.setRangeStep(XYStepMode.SUBDIVIDE, 6);
				
				// Deal with legend
				highInsulinPlot.getLegendWidget().setVisible(false);
		
				// Get values at the extremities of the plot from regular insulin plot
				highInsulinPlotBounds[VALUEOFMINX] = insulinPlotBounds[VALUEOFMINX];
				highInsulinPlotBounds[VALUEOFMAXX] = insulinPlotBounds[VALUEOFMAXX];
				highInsulinPlotBounds[VALUEOFMAXY] = insulinPlotBounds[VALUEOFMAXY];
				// Set plot bounds; time bounds should be the same on all plots
				highInsulinPlotBounds[RIGHT] = cgmPlotBounds[RIGHT];
				highInsulinPlotBounds[LEFT] = cgmPlotBounds[LEFT];
				highInsulinPlot.setRangeBoundaries(highInsulinPlotBounds[LOWER], highInsulinPlotBounds[UPPER], BoundaryMode.FIXED);
				highInsulinPlot.setDomainBoundaries(highInsulinPlotBounds[LEFT], highInsulinPlotBounds[RIGHT], BoundaryMode.FIXED);
				// By default, AndroidPlot displays developer guides to aid in laying out your plot.
				// To get rid of them call disableAllMarkup()
				highInsulinPlot.disableAllMarkup();
				highInsulinPlot.setVisibility(View.VISIBLE);
				// Make plot screens visible
				plotExpand(activePlot);    		
	    	}
			snapPlotsToRight = true;
			updatePlots(true);
    	}
		// Disable insulin plots in sensor-only mode if no insulin history
		if (DIAS_STATE == State.DIAS_STATE_SENSOR_ONLY && insulinCount == 0) {
			//Hide the insulin plots
    		(this.findViewById(R.id.insPlotLayout)).setVisibility(View.GONE);		
    		
    		//Double the size of the CGM height to fill the space
    		ViewGroup.LayoutParams plotParams = this.findViewById(R.id.cgmPlotLayout).getLayoutParams();
    		plotParams.height *= 2;
    		(this.findViewById(R.id.cgmPlotLayout)).setLayoutParams(plotParams);
		}
    }    	
	
	// Checks latest data in the database and updates plot
 	public void updatePlotData() 
 	{
 		final String FUNC_TAG = "updatePlotData";
 		
 		try 
 		{
			String[] projCGM = {"time", "cgm", "diasState"};
			String[] projIns = {"deliv_time", "deliv_basal"};
			String[] projMea = {"deliv_time", "deliv_meal"};
			String[] projCor = {"deliv_time", "deliv_corr"};
			
			Cursor cgm = getContentResolver().query(Biometrics.CGM_URI, projCGM,null, null, null);
			Cursor ins = getContentResolver().query(Biometrics.INSULIN_URI, projIns, null, null,	null);
			Cursor mea = getContentResolver().query(Biometrics.INSULIN_URI, projMea, null, null,	null);
			Cursor cor = getContentResolver().query(Biometrics.INSULIN_URI, projCor, null, null,	null);
			
			try {
				cgm.moveToLast();
			} catch (NullPointerException e) {
				Debug.i(TAG, FUNC_TAG, "CGM content resolver null error");
				cgm.close();
				ins.close();
				mea.close();
				cor.close();
				return;
			}
			try {
				ins.moveToLast();
			} catch (NullPointerException e) {
				Debug.e(TAG, FUNC_TAG, "Insulin content resolver null error");
				cgm.close();
				ins.close();
				mea.close();
				cor.close();
				return;
			}
			try {
				mea.moveToLast();
			} catch (NullPointerException e) {
				Debug.e(TAG, FUNC_TAG, "Meal bolus content resolver null error");
				cgm.close();
				ins.close();
				mea.close();
				cor.close();
				return;
			}
			try {
				cor.moveToLast();
			} catch (NullPointerException e) {
				Debug.e(TAG, FUNC_TAG, "Corr bolus content resolver null error");
				cgm.close();
				ins.close();
				mea.close();
				cor.close();
				return;
			}
			
			Number cgmT = null, cgmD = null, deliveredT = null, deliveredD = null, stateD = null, insT = null, insD = null, meaT = null, meaD = null, corT = null, corD = null;
			try {
				cgmT = cgm.getLong(cgm.getColumnIndex("time"));
			} catch (CursorIndexOutOfBoundsException e){
			}
			try {
				cgmD = cgm.getDouble(cgm.getColumnIndex("cgm"));
				stateD = cgm.getInt(cgm.getColumnIndex("diasState"));
			} catch (CursorIndexOutOfBoundsException e){
			}
			try {
				insT = 150 + ins.getLong(ins.getColumnIndex("deliv_time"));
			} catch (CursorIndexOutOfBoundsException e){
			}
			try {
				insD = ins.getDouble(ins.getColumnIndex("deliv_basal"))*12;
			} catch (CursorIndexOutOfBoundsException e){
			} 
			try {
				meaT = mea.getLong(mea.getColumnIndex("deliv_time"));
			} catch (CursorIndexOutOfBoundsException e){
			}
			try {
				meaD = mea.getDouble(mea.getColumnIndex("deliv_meal"));
			} catch (CursorIndexOutOfBoundsException e){
			} 
			try {
				corT = cor.getLong(cor.getColumnIndex("deliv_time"));
			} catch (CursorIndexOutOfBoundsException e){
			}
			try {
				corD = cor.getDouble(cor.getColumnIndex("deliv_corr"));
				Debug.i(TAG, FUNC_TAG, "CORR INSULIN: "+corD);
			} catch (CursorIndexOutOfBoundsException e){
			} 
			
			cgm.close();
			ins.close();
			mea.close();
			cor.close();
			
			updatePlotData(cgmT, cgmD, stateD, insT, insD, meaT, meaD, corT, corD, deliveredT, deliveredD);
		} catch (ArrayIndexOutOfBoundsException e) {
			e.printStackTrace();
		}
 	}

 	// method used to manually add new data points to the existing data, then redraw plots
 	public void updatePlotData(Number cgmTime, Number cgmData, Number stateData, Number insulinTime, Number insulinData,
 			Number mealTime, Number mealData, Number corrTime, Number corrData, Number deliveredTime, Number deliveredData) 
 	{
 		final String FUNC_TAG = "updatePlotData";
 		
 		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
 		if (cgmTime != null && cgmData != null) 
 		{
 	 		if (!cgmTimes.contains(cgmTime)) 
 	 		{
 				Debug.i(TAG, FUNC_TAG, "New CGM=("+sdf.format(new Date(cgmTime.longValue()))+","+cgmData+")");
 				
  				// Do not display CGM points with value indicating Noise or No Antenna
				cgmTimes.addElement(cgmTime);
				
				if(Params.getInt(getContentResolver(), "blood_glucose_display_units", 0) == CGM.BG_UNITS_MMOL_PER_L)
					cgmData = (double)cgmData.doubleValue()/CGM.MGDL_PER_MMOLL;
				
 				cgmValues.addElement(cgmData);
 				stateValues.addElement(DIAS_STATE);		// Add latest DIAS_STATE value
 				
 				if (COLOR_REGIONS_BY_DIAS_STATE) {
 	 		 		// Add a colored region to the CGM plot based upon the DIAS_STATE for the new plot point
 	 		 		// if we are currently displaying plots
					if (seriesCGMFormat != null) 
					{
						RectRegion region = new RectRegion(prevStateTime, currentTime, 0, 600);
						switch (stateData.intValue()) {
							case State.DIAS_STATE_STOPPED:
								seriesCGMFormat.addRegion(region, regionStopped);
								break;
							case State.DIAS_STATE_OPEN_LOOP:
								seriesCGMFormat.addRegion(region, regionOpen);
								break;
							case State.DIAS_STATE_CLOSED_LOOP:
								seriesCGMFormat.addRegion(region, regionClosed);
								break;
							case State.DIAS_STATE_SAFETY_ONLY:
								seriesCGMFormat.addRegion(region, regionSafetyOnly);
								break;
							case State.DIAS_STATE_SENSOR_ONLY:
								seriesCGMFormat.addRegion(region, regionSensorOnly);
								break;
						}
						prevStateTime = cgmTime;
					}
				}
			}
 		}
 		
 		// Remove old elements
 		Debug.i(TAG, FUNC_TAG, "cgmTimes.size() before="+cgmTimes.size());
		while (cgmTimes.get(0).longValue() < getCurrentTimeSeconds() - MAX_PLOT_STORAGE_DURATION_SECONDS) 
		{
			cgmValues.removeElementAt(0);
			cgmTimes.removeElementAt(0);
			stateValues.removeElementAt(0);
		}
 		Debug.i(TAG, FUNC_TAG, "cgmTimes.size() after="+cgmTimes.size());
 		
 		if (insulinTime != null && insulinData != null) 
 		{
 	 		if (!insulinTimes1.contains(insulinTime))
 	 		{
 				Debug.i(TAG, FUNC_TAG, "New INS=("+sdf.format(new Date(insulinTime.longValue()))+","+insulinData+")");
 				insulinTimes1.addElement(insulinTime);
 				insulinValues1.addElement(insulinData);
 			}
 		}
 		
 		// Remove old elements
 		Debug.i(TAG, FUNC_TAG, "insulinTimes1.size() before="+insulinTimes1.size());
		while (insulinTimes1.get(0).longValue() < getCurrentTimeSeconds() - MAX_PLOT_STORAGE_DURATION_SECONDS) 
		{
			insulinValues1.removeElementAt(0);
			insulinTimes1.removeElementAt(0);
		}
 		Debug.i(TAG, FUNC_TAG, "insulinTimes1.size() after="+insulinTimes1.size());
		
 		if (mealTime != null && mealData != null) 
 		{
 			if (!mealTimes.contains(mealTime))
 			{
 				Debug.i(TAG, FUNC_TAG, "New MEAL=("+sdf.format(new Date(mealTime.longValue()))+","+mealData+")");
 				mealTimes.addElement(mealTime);
 				mealValues.addElement(mealData);
 				if (mealData.doubleValue() > 0)
 				{
 					Debug.i(TAG, FUNC_TAG, "New Bolus DOT");
					double bolus_marker_initial = Math.min(mealData.doubleValue(), 12.0);
					double bolus_marker = 0.0;
					while (bolus_marker < 12) 
					{
						bolusTimes.addElement(mealTime);
						bolusValues.addElement(bolus_marker);
						
						if(bolus_marker < bolus_marker_initial-0.1)
							bolus_marker+=0.1;
						else
							bolus_marker+=0.8;
						
						if(bolus_marker > 12)
							bolus_marker = 12;
					}
 				}
 			}
 		}
 		
 		// Remove old elements
 		Debug.i(TAG, FUNC_TAG, "mealTimes.size() before="+mealTimes.size());
		while (mealTimes.get(0).longValue() < getCurrentTimeSeconds() - MAX_PLOT_STORAGE_DURATION_SECONDS) 
		{
			mealValues.removeElementAt(0);
			mealTimes.removeElementAt(0);
		}
 		Debug.i(TAG, FUNC_TAG, "mealTimes.size() after="+mealTimes.size());
		
 		if (corrTime != null && corrData != null) 
 		{
 			if (!corrTimes.contains(corrTime))
 			{
 				Debug.i(TAG, FUNC_TAG, "New CORR=("+sdf.format(new Date(corrTime.longValue()))+","+corrData+")");
 				corrTimes.addElement(corrTime);
 				corrValues.addElement(corrData);
 				if (corrData.doubleValue() > 0)
 				{
 					Debug.i(TAG, FUNC_TAG, "New Bolus DOT");
					double bolus_marker_initial = Math.min(corrData.doubleValue(), 12.0);
					double bolus_marker = 0.0;
					while (bolus_marker < 12) 
					{
						bolusTimes.addElement(corrTime);
						bolusValues.addElement(bolus_marker);
						
						if(bolus_marker < bolus_marker_initial-0.1)
							bolus_marker+=0.1;
						else
							bolus_marker+=0.8;
						
						if(bolus_marker > 12)
							bolus_marker = 12;
					}
 				}
 			}
 		}
 		
 		// Remove old elements
 		Debug.i(TAG, FUNC_TAG, "corrTimes.size() before="+corrTimes.size());
		while (corrTimes.get(0).longValue() < getCurrentTimeSeconds() - MAX_PLOT_STORAGE_DURATION_SECONDS) 
		{
			corrValues.removeElementAt(0);
			corrTimes.removeElementAt(0);
		}
 		Debug.i(TAG, FUNC_TAG, "corrTimes.size() after="+corrTimes.size());
 		
 		// Remove bolusTimes and bolusValues elements that are too old
 		if (bolusTimes.size() != 0)
 			while (bolusTimes.get(0).longValue() < getCurrentTimeSeconds() - MAX_PLOT_STORAGE_DURATION_SECONDS) 
 			{
 				bolusValues.removeElementAt(0);
 				bolusTimes.removeElementAt(0);
 			}
 		
 		cgmCount = cgmTimes.size();
 		insulinCount = insulinTimes1.size();

 		updatePlots(snapPlotsToRight);
 	}

 	// Method used to rebuild and redraw the plots will shift X-axis scroll position all the way to the right if arg is true
 	public void updatePlots(boolean snapPlotsToRight) 
 	{
 		final String FUNC_TAG = "updatePlots";
 		
 		// Fetch CGM data
 		Debug.i(TAG, FUNC_TAG, "snapPlotsToRight="+snapPlotsToRight);
 		XYPlot cgmPlot = (XYPlot) findViewById(R.id.cgmPlot);
 		cgmPlot.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
 		
 		// Removes/reads existing series from plot}
 		cgmPlot.removeSeries(seriesCGM);
 		seriesCGM = new SimpleXYSeries(cgmTimes, cgmValues, "CGM");
 		cgmPlot.addSeries(seriesCGM, seriesCGMFormat);

 		// If true, moves plot view all the way to the latest CGM value
 		if (snapPlotsToRight) 
 		{
			cgmPlotBounds[RIGHT] = getCurrentTimeSeconds();
			long currentTimeSeconds = (long)cgmPlotBounds[RIGHT];
			Debug.i(TAG, FUNC_TAG, "cgmPlotBounds[RIGHT]="+currentTimeSeconds);
 			cgmPlotBounds[LEFT] = cgmPlotBounds[RIGHT] - TIME_REGION_IN_SECONDS;
 		}
 		
 		cgmPlot.setRangeBoundaries(cgmPlotBounds[LOWER], cgmPlotBounds[UPPER],BoundaryMode.FIXED);
 		cgmPlot.setDomainBoundaries(cgmPlotBounds[LEFT], cgmPlotBounds[RIGHT],BoundaryMode.FIXED);
 		
 		// Disable insulin plots in sensor-only mode
    	if (DIAS_STATE != State.DIAS_STATE_SENSOR_ONLY)
    	{
	 		// Same thing over again with the insulin plot
	 		XYPlot insulinPlot = (XYPlot) findViewById(R.id.insulinPlot);
	 		insulinPlot.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
	 		
	 		insulinPlot.removeSeries(seriesInsulin);
	 		insulinPlot.removeSeries(seriesMealBolus);
	 		insulinPlot.removeSeries(seriesCorrBolus);
	 		seriesInsulin = new SimpleXYSeries(insulinTimes1, insulinValues1, "Basal U/hr");
	 		seriesMealBolus = new SimpleXYSeries(mealBolusTimes, mealBolusValues, ""); 
	 		seriesCorrBolus = new SimpleXYSeries(corrBolusTimes, corrBolusValues, ""); 
	 		insulinPlot.addSeries(seriesInsulin, seriesInsulinFormat);
	 		insulinPlot.addSeries(seriesMealBolus, seriesMealBolusFormat);
	 		insulinPlot.addSeries(seriesCorrBolus, seriesCorrBolusFormat);
	
	 		// All plot X-axis time bounds should be the same
	 		if (snapPlotsToRight) 
	 		{
	 			insulinPlotBounds[RIGHT] = cgmPlotBounds[RIGHT];
	 			insulinPlotBounds[LEFT] = cgmPlotBounds[LEFT];
	 		}
	 		insulinPlot.setRangeBoundaries(insulinPlotBounds[LOWER], insulinPlotBounds[UPPER], BoundaryMode.FIXED);
	 		insulinPlot.setDomainBoundaries(insulinPlotBounds[LEFT], insulinPlotBounds[RIGHT], BoundaryMode.FIXED);
	 		
	 		// Same thing over again with the high insulin plot
	 		XYPlot highInsulinPlot = (XYPlot) findViewById(R.id.highInsulinPlot);
	 		highInsulinPlot.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
	 		
	 		highInsulinPlot.removeSeries(seriesMeal);
	 		highInsulinPlot.removeSeries(seriesCorr);
	 		highInsulinPlot.removeSeries(seriesDeliveredInsulin);
	 		seriesMeal = new SimpleXYSeries(mealTimes, mealValues, "Meal U");
	 		seriesCorr = new SimpleXYSeries(corrTimes, corrValues, "Correction U");
	 		seriesDeliveredInsulin = new SimpleXYSeries(deliveredTimes, deliveredValues, "");
	 		highInsulinPlot.addSeries(seriesMeal, seriesMealFormat);
	 		highInsulinPlot.addSeries(seriesCorr, seriesCorrFormat);
			highInsulinPlot.addSeries(seriesDeliveredInsulin, seriesDeliveredInsulinFormat);
	
	 		// All plot X-axis time bounds should be the same
	 		if (snapPlotsToRight) 
	 		{
	 			highInsulinPlotBounds[RIGHT] = cgmPlotBounds[RIGHT];
	 			highInsulinPlotBounds[LEFT] = cgmPlotBounds[LEFT];
	 		}
	 		
	 		// Scale bolus plot to the highest bolus value, if it's greater than 2.0
	 		if (!mealValues.isEmpty() || !corrValues.isEmpty())
	 			// Set the upper bound of the bolus plot to 10% higher than the max value, rounded to the nearest 0.20, with a minimum of 2
	 			highInsulinPlotBounds[UPPER] = (double) Math.max(2f, Math.ceil((1.1*(Math.max(minmax(mealValues, true).doubleValue(), minmax(corrValues, true).doubleValue())))*5)/5);
	 		
	 		highInsulinPlot.setRangeBoundaries(highInsulinPlotBounds[LOWER], highInsulinPlotBounds[UPPER], BoundaryMode.FIXED);
	 		highInsulinPlot.setDomainBoundaries(highInsulinPlotBounds[LEFT], highInsulinPlotBounds[RIGHT], BoundaryMode.FIXED);
	 		
	 		// Scale the insulin/bolus plot bars depending on the time region
	 		((BarRenderer) insulinPlot.getRenderer(BarRenderer.class)).setBarWidth((float)(11*(double)10800/TIME_REGION_IN_SECONDS));
			if (TIME_REGION_IN_SECONDS > 10800){
				((BarRenderer) highInsulinPlot.getRenderer(BarRenderer.class)).setBarWidth((float)(11*(double)10800/TIME_REGION_IN_SECONDS));
			} else {
				((BarRenderer) highInsulinPlot.getRenderer(BarRenderer.class)).setBarWidth(11f);
			}
			
	 		insulinPlot.invalidate();
	 		highInsulinPlot.invalidate();
    	}
 		cgmPlot.invalidate();
 	}
	 	
	// Will modify either cgmPlot or insulinPlot bounds with a new value,
 	// cropping extreme values and updating stored values
 	// returns true if the input newBound was cropped
 	public boolean updatePlotBoundary(XYPlot plot, int boundIndex, double newBound) 
 	{
 		boolean over = false;
 		int id = plot.getId();
 		XYPlot plot2 = null, plot3 = null;
 		double[] bounds = null, bounds2 = null, bounds3 = null;
 		
 		// Set up local variables depending on the input plot
 		if (id == R.id.cgmPlot) 
 		{
 			bounds = cgmPlotBounds;
 			bounds2 = insulinPlotBounds;
 			bounds3 = highInsulinPlotBounds;
 			plot2 = (XYPlot) findViewById(R.id.insulinPlot);
 			plot3 = (XYPlot) findViewById(R.id.highInsulinPlot);
 		} 
 		else if (id == R.id.insulinPlot) 
 		{
 			bounds = insulinPlotBounds;
 			bounds2 = cgmPlotBounds;
 			bounds3 = highInsulinPlotBounds;
 			plot2 = (XYPlot) findViewById(R.id.cgmPlot);
 			plot3 = (XYPlot) findViewById(R.id.highInsulinPlot);
 		} 
 		else if (id == R.id.highInsulinPlot)
 		{
 			bounds = highInsulinPlotBounds;
 			bounds2 = insulinPlotBounds;
 			bounds3 = cgmPlotBounds;
 			plot2 = (XYPlot) findViewById(R.id.insulinPlot);
 			plot3 = (XYPlot) findViewById(R.id.cgmPlot); 			
 		}
 		
 		// Perform different actions depending on which bound is to be changed
 		switch (boundIndex) 
 		{
	 		case LOWER: // Unused for now
	 			bounds[boundIndex] = newBound;
	 			plot.setRangeLowerBoundary(newBound, BoundaryMode.FIXED);
	 			break;
	 		case UPPER: // Used for scaling
	 			if (newBound <= bounds[VALUEOFMINX] || newBound <= bounds[VALUEOFMAXX]){
	 				newBound = Math.max(bounds[VALUEOFMINX], bounds[VALUEOFMAXX]); // sets the zoom to the maximum safe value if it is too much
	 				over = true;
	 			} else if (newBound > bounds[VALUEOFMAXY]*5){
	 				newBound = Math.max(bounds[VALUEOFMAXY], bounds[VALUEOFMAXX])*5; // prevent zooming in too much
	 				over = true;				
	 			}
	 			bounds[boundIndex] = newBound;
	 			plot.setRangeUpperBoundary(newBound, BoundaryMode.FIXED);
	 			break;
	 		case LEFT: // Unused for now
	 			bounds[boundIndex] = newBound;
	 			plot.setDomainLowerBoundary(newBound, BoundaryMode.FIXED);
	 			break;
	 		case RIGHT:
	 			if (newBound >= cgmTimes.get(cgmTimes.size()-1).doubleValue()) {
	 				newBound = getCurrentTimeSeconds();
	 				snapPlotsToRight = true;
	 				over = true;
	 			}
	 			else if (newBound <= cgmTimes.get(0).doubleValue()){
	 				newBound = cgmTimes.get(0).doubleValue();
	 				snapPlotsToRight = false;
	 			} else {
	 				snapPlotsToRight = false;	
	 			}
	 			
	 			// Both cgm and insulin plots will have the same X-axis time bounds
	 			bounds[RIGHT] = newBound;
	 			bounds[LEFT] = newBound - TIME_REGION_IN_SECONDS;
	 			bounds2[RIGHT] = newBound;
	 			bounds2[LEFT] = newBound - TIME_REGION_IN_SECONDS;
	 			bounds3[RIGHT] = newBound;
	 			bounds3[LEFT] = newBound - TIME_REGION_IN_SECONDS;
	 			
	 			plot.setDomainBoundaries(bounds[LEFT], bounds[RIGHT], BoundaryMode.FIXED);
	 			plot2.setDomainBoundaries(bounds2[LEFT], bounds2[RIGHT], BoundaryMode.FIXED);
	 			plot3.setDomainBoundaries(bounds3[LEFT], bounds3[RIGHT], BoundaryMode.FIXED);
	 			break;
 		}
 		
 		// Show snap-to-right indicators if true
		FrameLayout snapCGM = (FrameLayout)findViewById(R.id.snapBarCGM);
		FrameLayout snapInsulin = (FrameLayout)findViewById(R.id.snapBarInsulin);
		if (snapPlotsToRight){
			snapCGM.setVisibility(FrameLayout.VISIBLE);
			snapInsulin.setVisibility(FrameLayout.VISIBLE);
		} else {
			snapCGM.setVisibility(FrameLayout.INVISIBLE);
			snapInsulin.setVisibility(FrameLayout.INVISIBLE);	
		}
		
 		// Tell Android to redraw plots
 		plot.invalidate();
 		plot2.invalidate();
 		plot3.invalidate();
 		return over;
 	}
	 	
 	// Function to snap to nearest round number when scrolling
 	public void snapToNearestPlotStep(XYPlot plot) 
 	{
 		final String FUNC_TAG = "snapToNearestPlotStep";
 		
 		int id = plot.getId();
 		XYPlot plot2 = null, plot3 = null;
 		double[] bounds = null, bounds2 = null, bounds3 = null;
 		
 		// Set up local variables depending on the input plot
 		if (id == R.id.cgmPlot) 
 		{
 			bounds = cgmPlotBounds;
 			bounds2 = insulinPlotBounds;
 			bounds3 = highInsulinPlotBounds;
 			plot2 = (XYPlot) findViewById(R.id.insulinPlot);
 			plot3 = (XYPlot) findViewById(R.id.highInsulinPlot);
 		} 
 		else if (id == R.id.insulinPlot) 
 		{
 			bounds = insulinPlotBounds;
 			bounds2 = cgmPlotBounds;
 			bounds3 = highInsulinPlotBounds;
 			plot2 = (XYPlot) findViewById(R.id.cgmPlot);
 			plot3 = (XYPlot) findViewById(R.id.highInsulinPlot);
 		} 
 		else if (id == R.id.highInsulinPlot)
 		{
 			bounds = highInsulinPlotBounds;
 			bounds2 = insulinPlotBounds;
 			bounds3 = cgmPlotBounds;
 			plot2 = (XYPlot) findViewById(R.id.insulinPlot);
 			plot3 = (XYPlot) findViewById(R.id.cgmPlot); 			
 		}
 		
		double newBound = bounds[RIGHT];
		
		if (newBound >= cgmTimes.get(cgmTimes.size() - 1).doubleValue()) 
		{
			newBound = getCurrentTimeSeconds();
		} 
		else if (newBound <= cgmTimes.get(0).doubleValue()) 
		{
			newBound = cgmTimes.get(0).doubleValue();
		} 
		else 
		{
			int step = (int) (TIME_REGION_IN_SECONDS / 12);
			newBound -= newBound % step;
		}
		
		// Both cgm and insulin plots will have the same X-axis time bounds
		bounds[RIGHT] = newBound;
		bounds[LEFT] = newBound - TIME_REGION_IN_SECONDS;
		bounds2[RIGHT] = newBound;
		bounds2[LEFT] = newBound - TIME_REGION_IN_SECONDS;
		bounds3[RIGHT] = newBound;
		bounds3[LEFT] = newBound - TIME_REGION_IN_SECONDS;
		
		plot.setDomainBoundaries(bounds[LEFT], bounds[RIGHT], BoundaryMode.FIXED);
		plot2.setDomainBoundaries(bounds2[LEFT], bounds2[RIGHT], BoundaryMode.FIXED);
		plot3.setDomainBoundaries(bounds3[LEFT], bounds3[RIGHT], BoundaryMode.FIXED);
		
 		// Tell Android to redraw plots
 		plot.invalidate();
 		plot2.invalidate();
 		plot3.invalidate();
 		
 		Debug.i(TAG, FUNC_TAG, "SNAPTOSTEP > R=("+bounds[RIGHT]+")L=("+bounds[LEFT]+")NEW="+newBound);
 	}
 	

 	// Sets the current time region for the plots and crops extreme bounds
 	// Returns true if limits on the bounds have been over-stepped
 	public boolean setTimeRegionInSeconds(long secs) 
 	{
 		long MIN_MINUTES_REGION = 30;
 		long MAX_HOURS_REGION = 24; 
 		if (secs < MIN_MINUTES_REGION * 60)
 		{
 			TIME_REGION_IN_SECONDS = MIN_MINUTES_REGION * 60;
 			return true;
 		}
 		else if (secs > MAX_HOURS_REGION * 60 * 60)
 		{
 			TIME_REGION_IN_SECONDS = MAX_HOURS_REGION * 60 * 60;
 			return true;
 		}
 		else
 		{
 			TIME_REGION_IN_SECONDS = secs;
 			return false;
 		}
 	}

 	// Sets the time region; it is recommended to use the above function instead
 	public void setTimeRegionInHours(double hours) 
 	{
 		TIME_REGION_IN_SECONDS = (long) (hours * 60 * 60);
 	}

 	// Get functions for ZoomScrollListener to use
 	public double[] getCGMPlotBounds() 
 	{
 		return cgmPlotBounds;
 	}

 	public double[] getInsulinPlotBounds() 
 	{
 		return insulinPlotBounds;
 	}
 	
 	public double[] getHighInsulinPlotBounds()
 	{
 		return highInsulinPlotBounds;
 	}

    public void plotExpand(int ViewID)	// Function used to display the XYPlot (passed as ViewID) over other plots
    {
    	final String FUNC_TAG = "plotExpand";
    	
    	activePlot = ViewID; 	
    	
    	XYPlot insulinPlot = (XYPlot) findViewById(R.id.insulinPlot);
	 	XYPlot highInsulinPlot = (XYPlot) findViewById(R.id.highInsulinPlot);
	 	
	 	if (activePlot == R.id.insulinPlot)
	 	{
	 		Debug.i(TAG, FUNC_TAG, "Hiding High Insulin Plot");
	 		insulinPlot.setVisibility(LinearLayout.VISIBLE);
	 		highInsulinPlot.setVisibility(LinearLayout.GONE);
	 	}
	 	else if (activePlot == R.id.highInsulinPlot)
	 	{
	 		Debug.i(TAG, FUNC_TAG, "Hiding Insulin Plot");
	 		highInsulinPlot.setVisibility(LinearLayout.VISIBLE);
	 		insulinPlot.setVisibility(LinearLayout.GONE);
	 	}
    }
    
	/************************************************************************************
	* Misc Functions
	************************************************************************************/
	
 	// Convenience function to get max or min value from a Set 
    Number minmax(Collection<Number> setOfNumbers, boolean getMax)
    {
    	Number res = null;
    	if (getMax)
    	{
    		res = 0;
    		for (Number x : setOfNumbers)
    			if (x.doubleValue() > res.doubleValue())
    				res = x;
    	}
    	else if (!getMax)
    	{
    		res = Double.MAX_VALUE;
    		for (Number x : setOfNumbers)
    			if (x.doubleValue() < res.doubleValue())
    				res = x;
    	}
    	return res;
    }
 	
	public long getCurrentTimeSeconds() {
			return (long)(System.currentTimeMillis()/1000);		// Seconds since 1/1/1970 in UTC
	}
	
	private class MyDateFormat extends Format 
	{
        // Create a simple date format that draws on the year portion of our timestamp.
        // see http://download.oracle.com/javase/1.4.2/docs/api/java/text/SimpleDateFormat.html
        // for a full description of SimpleDateFormat.
		
        private SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");
        
        @Override
        public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) 
        {
            long timestamp = ((Number) obj).longValue()*1000;
            Time t = new Time(TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT, Locale.getDefault()));
            t.set(timestamp);
            Date date = new Date(t.toMillis(false));
            return dateFormat.format(date, toAppendTo, pos);
        }
        
        @Override
        public Object parseObject(String source, ParsePosition pos) 
        {
            return null;
        }
    }
	
	/************************************************************************************
	* Log Messaging Functions
	************************************************************************************/
	
	public void log_action(String tag, String message)
	{
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", tag);
        i.putExtra("Status", message);
        i.putExtra("time", (long)getCurrentTimeSeconds());
        sendBroadcast(i);
	}
}
