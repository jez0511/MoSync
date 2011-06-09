package com.mosync.internal.android;

import static com.mosync.internal.generated.MAAPI_consts.*;
import static com.mosync.internal.android.MoSyncHelpers.*;

import android.app.Activity;
import android.content.Context;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class MoSyncSensor implements SensorEventListener {
	
	/*
	 * Constants defining the event structure
	 */
	private static final int SEVENT_TYPE = 0;
	private static final int SEVENT_SENSOR_TYPE = 1;
	private static final int SEVENT_SENSOR_VALUES = 2;
	private static final int SEVENT_SIZE = 6;
	
	private static final int SENSOR_TYPE_ALL = -1;
	private static final int SENSOR_TYPE_ACCELEROMETER = 1;
	private static final int SENSOR_TYPES = 10;

	private static final int SENSOR_ERROR_NONE = 0;
	
	/*
	 * Error codes for maSensorStart
	 */
	private static final int SENSOR_ERROR_NOT_AVAILABLE = -2;
	private static final int SENSOR_ERROR_INTERVAL_NOT_SET = -3;
	private static final int SENSOR_ERROR_ALREADY_ENABLED = -4;
	
	/*
	 * Error codes for maSensorStop
	 */
	private static final int SENSOR_ERROR_NOT_ENABLED = -5;
	private static final int SENSOR_ERROR_CANNOT_DISABLE = -6;

	/*
	 * Sensor predefined rates
	 */
	private static final int SENSOR_DELAY_FASTEST = 0;
	private static final int SENSOR_DELAY_GAME = -1;
	private static final int SENSOR_DELAY_NORMAL = -2;
	private static final int SENSOR_DELAY_UI = -3;
	
	/*
	 * Used to convert the rate from milliseconds to microseconds
	 */
	private static final int SENSOR_DELAY_MULTIPLIER = 1000;
	
	/*
	 * Used to keep the values in [-1; 1] interval to be the same as on iOS
	 */
	private static final int ACCELEROMETER_ADJUSTMENT = -10;

	/**
	 * The MoSync thread object.
	 */
	MoSyncThread mMoSyncThread;
	
	/**
	 * A sensor manager to access the device's sensors.
	 */
	private SensorManager mSensorManager;
	
	/**
	 * A list of all available sensors on the device
	 */
    private Sensor[] mSensorList;
    private int[] mSensorRates;
	
	/**
	 * @return The Activity object.
	 */
	private Activity getActivity()
	{
		return mMoSyncThread.getActivity();
	}
    
	/**
	 * Constructor.
	 * @param thread The MoSync thread.
	 */
	public MoSyncSensor(MoSyncThread thread)
	{
		mMoSyncThread = thread;
		mSensorManager = (SensorManager)getActivity().getSystemService(Context.SENSOR_SERVICE);
		mSensorList = new Sensor[SENSOR_TYPES];
		mSensorRates = new int[SENSOR_TYPES];
		for (int i = 0; i<SENSOR_TYPES; i++ )
		{
			mSensorRates[i] = -1;
		}
	}
	
	/**
	 * Called when the accuracy of a sensor has changed.
	 */
	@Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

	/**
	 * Called when sensor values have changed.
	 */
	@Override
    public void onSensorChanged(SensorEvent arg0) {
		int[] event = new int[SEVENT_SIZE];
		
		try
		{
			event[SEVENT_TYPE] = EVENT_TYPE_SENSOR;
			event[SEVENT_SENSOR_TYPE] = arg0.sensor.getType();
			int len = arg0.values.length;
			for (int i=0; i<arg0.values.length; i++)
			{
				if (event[SEVENT_SENSOR_TYPE] == SENSOR_TYPE_ACCELEROMETER)
				{
					event[SEVENT_SENSOR_VALUES + i] = Float.floatToIntBits(arg0.values[i] / ACCELEROMETER_ADJUSTMENT);
				}
				else
				{
					event[SEVENT_SENSOR_VALUES + i] = Float.floatToIntBits(arg0.values[i]);
				}
	        }
		}
		catch (Exception e)
		{
			SYSLOG("Invalid event arguments!!!");
		}
		
		mMoSyncThread.postEvent(event);
    }

	/*
	 * Registers to the specified sensor
	 */
	int maSensorStart(int sensor, int interval)
	{
		int rate = getSensorRate(interval);
		if (sensor == SENSOR_TYPE_ALL)
		{
			// fleu TODO
			return SENSOR_ERROR_NOT_AVAILABLE;
		}
		
		if (mSensorList[sensor - 1] != null)
		{
			return SENSOR_ERROR_ALREADY_ENABLED;
		}
		mSensorList[sensor - 1] = mSensorManager.getDefaultSensor(sensor);
		if (mSensorList[sensor - 1] == null)
		{
			return SENSOR_ERROR_NOT_AVAILABLE;
		}
		mSensorRates[sensor - 1] = interval;
		if (!mSensorManager.registerListener(this, mSensorList[sensor - 1], rate))
		{
			return SENSOR_ERROR_INTERVAL_NOT_SET;
		}
		return SENSOR_ERROR_NONE;
	}

	/*
	 * Returns the Android rate starting from the MoSync one
	 */
	int getSensorRate(int interval)
	{
		switch (interval)
		{
			case SENSOR_DELAY_FASTEST:
				return SensorManager.SENSOR_DELAY_FASTEST;
			case SENSOR_DELAY_GAME:
				return SensorManager.SENSOR_DELAY_GAME;
			case SENSOR_DELAY_NORMAL:
				return SensorManager.SENSOR_DELAY_NORMAL;
			case SENSOR_DELAY_UI:
				return SensorManager.SENSOR_DELAY_UI;
			default:
				return (interval * SENSOR_DELAY_MULTIPLIER);
		}
	}
	
	/*
	 * Unregisters from the specified sensor.
	 */
	int maSensorStop(int sensor)
	{
		if (mSensorList[sensor - 1] == null)
		{
			return SENSOR_ERROR_NOT_ENABLED;
		}
		try
		{
			mSensorManager.unregisterListener(this, mSensorList[sensor - 1]);
			mSensorList[sensor - 1] = null;
			mSensorRates[sensor - 1] = -1;
		}
		catch (Exception e)
		{
			return SENSOR_ERROR_CANNOT_DISABLE;
		}
		
		if (mSensorList[sensor - 1] != null)
		{
			return SENSOR_ERROR_CANNOT_DISABLE;
		}
		
		return 0;
	}
	
	/*
	 * Interrupt handling: resume
	 */
	public void onResume()
	{
		for (int i=0; i<SENSOR_TYPES; i++)
		{
			if (mSensorList[i] != null)
			{
				mSensorManager.registerListener(this, mSensorList[i], mSensorRates[i]);
			}
		}
    }

	/*
	 * Interrupt handling: pause
	 */
	public void onPause()
	{
		mSensorManager.unregisterListener(this);
    }
}
