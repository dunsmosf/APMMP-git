//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.DiAsUI;

import com.androidplot.xy.XYPlot;

import android.util.FloatMath;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;

/*
 * An OnTouchListener that links with a DiAsMain object and attaches to an XYPlot 
 * to allow for full touch interaction with XYPlots instead of just clicking.
 * Movement modifies boundaries in the XYPlot and lets the plot to the rest of the work
 * on a redraw.
 * One finger movement scrolls the plot left and right, two finger movement scales the plot.
 */
public class ZoomScrollListener implements OnTouchListener, OnLongClickListener{
	private static final String TAG = "ZoomScrollListener";
	private PlotsActivity main;
	// boolean options for touch controls
	private boolean canScaleX = false, canScaleY = false, canSwipe = false, swipe;
	// three different touching modes
	private static final int NOTOUCH = 0, ONEFINGER = 1, TWOFINGER = 2;
	private int mode;
	// saved values used to calculating transformations to new values
	private float oldX1, oldY1, oldX2, oldY2, olddX, olddY, dX, dY, oldDist;
	private double oldUpperBound, oldRightBound;
	private long oldTime, dT;
	private long oldTimeRegion;
	private long timeZoomCount = 0;
	private int moveCount = 0;
	
	// needs to link with a DiAsMain to obtain bounds information about the
	// XYPlots
	public ZoomScrollListener(PlotsActivity d, boolean canScaleX, boolean canScaleY, boolean canSwipe) {
		main = d;
		this.canScaleX = canScaleX;
		this.canScaleY = canScaleY;
		this.canSwipe = canSwipe;
	}

	public boolean onTouch(View v, MotionEvent event) {
		XYPlot plot = (XYPlot) v;
		double X1 = 0, Y1 = 0, X2 = 0, Y2 = 0;
		switch (event.getAction() & MotionEvent.ACTION_MASK) {
		// first finger down action
		case MotionEvent.ACTION_DOWN:
//			main.tracker.trackEvent("Touch", "down", null, 0);
			// save initial state of finger
			oldTime = event.getEventTime();
			oldX1 = event.getX();
			oldY1 = event.getY();
			swipe = canSwipe;
			moveCount = 0;
			switch (v.getId()) {
			case R.id.cgmPlot:
				oldUpperBound = main.getCGMPlotBounds()[PlotsActivity.UPPER];
				oldRightBound = main.getCGMPlotBounds()[PlotsActivity.RIGHT];
				break;
			case R.id.insulinPlot:
				oldUpperBound = main.getInsulinPlotBounds()[PlotsActivity.UPPER];
				oldRightBound = main.getInsulinPlotBounds()[PlotsActivity.RIGHT];
				break;
			case R.id.highInsulinPlot:
				oldUpperBound = main.getHighInsulinPlotBounds()[PlotsActivity.UPPER];
				oldRightBound = main.getHighInsulinPlotBounds()[PlotsActivity.RIGHT];
				break;			
			}
//			Log.d(TAG, "Down(" + mode + "," + oldX1 + "," + oldY1 + ")");
			mode = ONEFINGER; // finger movement will scroll now
			return false;
		// finger moving action
		case MotionEvent.ACTION_MOVE:
//			Log.d(TAG, "Move(" + mode + ")");
			++moveCount;
			if (mode == ONEFINGER) { // scrolling action
				X1 = event.getX();
				Y1 = event.getY();
				dT = event.getEventTime() - oldTime;
				dX = (float) (oldX1 - X1); // change in distance from initial finger position
				dY = (float) (oldY1 - Y1);
				if ((Math.abs(dY)-Math.abs(5*dX) > 0) && Math.abs(dY) > 10 && canSwipe){
					if (swipe){
//						if (dY > 0)
//							main.tracker.trackEvent("Touch", "swipe up", null, 0);
//						else 
//							main.tracker.trackEvent("Touch", "swipe down", null, 0);
						swipe = false;
						/* PKH TEST!!!
						if (main.activePlot == R.id.insulinPlot)
							main.plotExpand(R.id.highInsulinPlot);
						else if (main.activePlot == R.id.highInsulinPlot)
							main.plotExpand(R.id.insulinPlot);
						*/
					}
				} else {
//					if (main.updatePlotBoundary(plot, DiAsMain.RIGHT,(double) (oldRightBound * Math.exp((dX/40000000)*(double)main.TIME_REGION_IN_SECONDS / 7200)))){
					double result = oldRightBound + ((double)main.TIME_REGION_IN_SECONDS / (double)((XYPlot)main.findViewById(R.id.cgmPlot)).getMeasuredWidth()) * dX;
//					double result = (double) (oldRightBound * Math.exp((dX/40000000)*(double)main.TIME_REGION_IN_SECONDS / 7200));
					if (main.updatePlotBoundary(plot, PlotsActivity.RIGHT, result)){	
						//main.tracker.trackEvent("Touch", "scroll", "boundary", 0);
						oldX1 = (float) X1; // at a boundary, movement in the other direction immediately produces a resulting scroll
						oldRightBound = main.getCGMPlotBounds()[PlotsActivity.RIGHT];
					} else {
//						main.tracker.trackEvent("Touch", "scroll", "success", 0);
					}
				}
			} else if (mode == TWOFINGER){ // scaling action
				X1 = event.getX(0);
				Y1 = event.getY(0);
				X2 = event.getX(1);
				Y2 = event.getY(1);
				dX = (float) Math.abs(X1 - X2);
				dY = (float) Math.abs(Y1 - Y2);
				double dist = FloatMath.sqrt(dX * dX + dY * dY); // current distance between two fingers
				if (Math.abs(dY) >= Math.abs(dX) && canScaleY){ // indicates mostly vertical pinch movement
//					main.tracker.trackEvent("Touch", "scale Y", ""+canScaleY, 0);
					if (main.updatePlotBoundary(plot, 1, (double) (oldUpperBound * Math.exp((olddY - dY) / 500)))){ // sets X-axis time boundary based on change in distance between finger
						olddY = dY; // at a boundary, movement in the other direction immediately produces a resulting zoom
					if (v.getId() == R.id.cgmPlot)
						oldUpperBound = main.getCGMPlotBounds()[PlotsActivity.UPPER];
					else oldUpperBound = main.getInsulinPlotBounds()[PlotsActivity.UPPER];
					}
				} else if (Math.abs(dY) < Math.abs(dX) && canScaleX){ // mostly horizontal pinch movement
//					main.tracker.trackEvent("Touch", "scale X", ""+canScaleX, 0);
					if (main.setTimeRegionInSeconds((long)(oldTimeRegion * Math.exp((olddX - dX)/500)))){
						oldTimeRegion = main.TIME_REGION_IN_SECONDS;
						olddX = dX; // at a boundary, movement in the other direction immediately produces a resulting zoom
					}
//					if (++timeZoomCount % 3 == 0)
					main.updatePlots(true);
				}
			}
			return true;
		// first finger lifted action
		case MotionEvent.ACTION_UP:
//			main.tracker.trackEvent("Touch", "up", null, 0);
			X1 = event.getX();
			Y1 = event.getY();
			dX = (float) (X1 - oldX1);
			dY = (float) (Y1 - oldY1);
			dT = event.getEventTime() - oldTime;
			float dist = FloatMath.sqrt((float)dX * (float)dX + (float)dY * (float)dY); // distance between initial and final positions of finger
//			Log.d(TAG, "Up,dist=" + dist + ", time="+ dT);
			if (dist < 9 && dT < 300){ // fake finger click action based on short time and movement of touch
//				main.tracker.trackEvent("Touch", "close plots", "tap", 0);
/*
 * 				main.closePlotsClick(v);
*/				
//				if (main.DIAS_UI_STATE == DiAsMain.DIAS_UI_STATE_PLOTS)
//					main.showDialog(DiAsMain.DIALOG_PLOTS_EDIT);
//				main.plotsShow(true);
			}
			main.snapToNearestPlotStep(plot);
			mode = NOTOUCH;
			return false;
		// second finger down action
		case MotionEvent.ACTION_POINTER_DOWN:
//			main.tracker.trackEvent("Touch", "pointer down", null, 0);
			// prevent long click
			moveCount = 10;
			// save initial state of both fingers
			oldX1 = event.getX(0);
			oldY1 = event.getY(0);
			oldX2 = event.getX(1);
			oldY2 = event.getY(1);
			olddX = Math.abs(oldX1 - oldX2);
			olddY = Math.abs(oldY1 - oldY2);
			oldTimeRegion = main.TIME_REGION_IN_SECONDS;
			switch (v.getId()) {
			case R.id.cgmPlot:
				oldUpperBound = main.getCGMPlotBounds()[PlotsActivity.UPPER];
				oldRightBound = main.getCGMPlotBounds()[PlotsActivity.RIGHT];
				break;
			case R.id.insulinPlot:
				oldUpperBound = main.getInsulinPlotBounds()[PlotsActivity.UPPER];
				oldRightBound = main.getInsulinPlotBounds()[PlotsActivity.RIGHT];
				break;
			case R.id.highInsulinPlot:
				oldUpperBound = main.getHighInsulinPlotBounds()[PlotsActivity.UPPER];
				oldRightBound = main.getHighInsulinPlotBounds()[PlotsActivity.RIGHT];
				break;
			}
			oldDist = FloatMath.sqrt((oldY2-oldY1)*(oldY2-oldY1) + (oldX2-oldX1)*(oldX2-oldX1));
			mode = TWOFINGER; // finger movement will scale now
//			Log.d(TAG, "PointDown("+oldX1 + "," + oldY1 + ")("+oldX2+","+oldY2+") DIST="+oldDist);
			return true;
		// second finger lifted action
		case MotionEvent.ACTION_POINTER_UP:
//			main.tracker.trackEvent("Touch", "pointer up", null, 0);
			mode = NOTOUCH; // prevent screwed up scrolling after zooming
			return true;
		case MotionEvent.ACTION_CANCEL:
//			Log.e(TAG, "Motion canceled during mode="+mode);
			return true;
			}		
		return false;
	}

	public boolean onLongClick(View v) {
		if (moveCount > 2 || Math.abs(dX/dT) > 0.1)
			return false;
		return true;
	}
}
