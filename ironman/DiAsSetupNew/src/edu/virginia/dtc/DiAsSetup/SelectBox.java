//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.DiAsSetup;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;

public class SelectBox extends EditText implements OnClickListener {
	private ProfileFragment setup = null;
	public int index;
	
	public SelectBox(Context context, ProfileFragment setup, int index, String string) {
		super(context);
		this.setup = setup;
		this.index = index;
		this.setText(string);
		this.setFocusable(false);
		this.setTextColor(Color.WHITE);
		this.setOnClickListener(this);
		this.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
	}
	
	public SelectBox(Context context, AttributeSet attrs){
		super(context, attrs);
	}
	
	public SelectBox(Context context, AttributeSet attrs, int defStyle){
		super(context, attrs, defStyle);
	}

	public void onClick(View view) {
		setup.selectProfile(index);
	}
}
