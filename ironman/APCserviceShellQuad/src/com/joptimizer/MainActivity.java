package com.joptimizer;

import junit.framework.TestResult;
import junit.framework.TestSuite;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {

	public static final String JOPTIMIZER_LOGTAG = "JOptimizer";// for use as the tag when logging

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
//		setContentView(R.layout.activity_main);

		// try{
		// ConfigureLog4J.configure();
		// }catch(Exception e){
		// Log.e(logtag, "There are a problem with Log4J configuration", e);
		// }

//		Button buttonStart = (Button) findViewById(R.id.button1);
//		buttonStart.setOnClickListener(optimizeButtonListener); // Register the
																// onClick
																// listener with
																// the
																// implementation
																// above

		Log.d(JOPTIMIZER_LOGTAG, "onCreate end");

	}

	// Create an anonymous implementation of OnClickListener
	private OnClickListener optimizeButtonListener = new OnClickListener() {
		public void onClick(View v) {
			Log.d(JOPTIMIZER_LOGTAG, "onClick() called - optimize button");
			Toast.makeText(MainActivity.this, "The optimize button was clicked...", Toast.LENGTH_LONG).show();
			Log.d(JOPTIMIZER_LOGTAG, "onClick() ended - optimize button");
			
			try {
				//new JOptimizerTester().doTest();
				
				TestSuite suite = (TestSuite) AllTests.suite();
				TestResult result = new TestResult();
				suite.run(result);
				Log.i(JOPTIMIZER_LOGTAG, "Was it successful? " + result.wasSuccessful());
				Log.i(JOPTIMIZER_LOGTAG, "How many tests were there? " + result.runCount());
				if(result.wasSuccessful()){
					Log.i(JOPTIMIZER_LOGTAG, "JOptimizer is working good!");
					Toast.makeText(MainActivity.this, "JOptimizer is working good!", Toast.LENGTH_LONG).show();
				}else{
					Log.i(JOPTIMIZER_LOGTAG, "JOptimizer is NOT working good!");
					Toast.makeText(MainActivity.this, "JOptimizer is NOT working good!", Toast.LENGTH_LONG).show();
				}
			} catch (Exception e) {
				Log.e(JOPTIMIZER_LOGTAG, "There are a problem with JOptimizer", e);
			}
		}
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
//		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
