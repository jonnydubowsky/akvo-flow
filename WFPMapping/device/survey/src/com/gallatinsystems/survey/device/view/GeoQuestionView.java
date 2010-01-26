package com.gallatinsystems.survey.device.view;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.gallatinsystems.survey.device.R;
import com.gallatinsystems.survey.device.domain.Question;

/**
 * Question that can handle geographic location input. This question can also
 * listen to location updates from the GPS sensor on the device.
 * 
 * 
 * @author Christopher Fagiani
 * 
 */
public class GeoQuestionView extends QuestionView implements OnClickListener,
		LocationListener {

	private static final int DEFAULT_WIDTH = 200;
	private Button geoButton;
	private TextView latLabel;
	private EditText latField;
	private TextView lonLabel;
	private EditText lonField;
	private TextView elevationLabel;
	private EditText elevationField;

	public GeoQuestionView(Context context, Question q) {
		super(context, q);
		init();
	}

	protected void init() {
		Context context = getContext();
		TableRow tr = new TableRow(context);

		TableLayout innerTable = new TableLayout(context);
		TableRow innerRow = new TableRow(context);

		latField = new EditText(context);
		latField.setWidth(DEFAULT_WIDTH);
		latLabel = new TextView(context);
		latLabel.setText(R.string.lat);

		innerRow.addView(latLabel);
		innerRow.addView(latField);

		innerTable.addView(innerRow);
		innerRow = new TableRow(context);

		lonField = new EditText(context);
		lonField.setWidth(DEFAULT_WIDTH);
		lonLabel = new TextView(context);
		lonLabel.setText(R.string.lon);

		innerRow.addView(lonLabel);
		innerRow.addView(lonField);

		innerTable.addView(innerRow);
		innerRow = new TableRow(context);

		elevationField = new EditText(context);
		elevationField.setWidth(DEFAULT_WIDTH);
		elevationLabel = new TextView(context);
		elevationLabel.setText(R.string.elevation);

		innerRow.addView(elevationLabel);
		innerRow.addView(elevationField);

		innerTable.addView(innerRow);

		tr.addView(innerTable);
		addView(tr);
		tr = new TableRow(context);

		geoButton = new Button(context);
		geoButton.setText(R.string.getgeo);
		geoButton.setOnClickListener(this);
		tr.addView(geoButton);
		addView(tr);

	}

	public void onClick(View v) {
		LocationManager locMgr = (LocationManager) getContext()
				.getSystemService(Context.LOCATION_SERVICE);
		if (locMgr.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			Location location = locMgr
					.getLastKnownLocation(LocationManager.GPS_PROVIDER);

			if (location != null) {
				populateLocation(location);
			} else {
				locMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER,
						1000, 0, this);
			}
		} else {
			// we can't turn GPS on directly, the best we can do is launch the
			// settings page
			AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
			builder.setMessage(R.string.geodialog).setCancelable(true)
					.setPositiveButton("Ok",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									getContext()
											.startActivity(
													new Intent(
															"android.settings.LOCATION_SOURCE_SETTINGS"));
								}
							}).setNegativeButton("Cancel",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.cancel();
								}
							});
			builder.show();
		}

	}

	private void populateLocation(Location loc) {
		latField.setText(loc.getLatitude() + "");
		lonField.setText(loc.getLongitude() + "");
		elevationField.setText(loc.getAltitude() + "");
	}

	public void resetQuestion() {
		super.resetQuestion();
		latField.setText("");
		lonField.setText("");
		elevationField.setText("");
	}

	@Override
	public void questionComplete() {
		// completeIcon.setVisibility(View.VISIBLE);
	}

	public void onLocationChanged(Location location) {
		populateLocation(location);
		LocationManager locMgr = (LocationManager) getContext()
				.getSystemService(Context.LOCATION_SERVICE);
		locMgr.removeUpdates(this);
	}

	public void onProviderDisabled(String provider) {
		// no op. needed for LocationListener interface

	}

	public void onProviderEnabled(String provider) {
		// no op. needed for LocationListener interface

	}

	public void onStatusChanged(String provider, int status, Bundle extras) {
		// no op. needed for LocationListener interface
	}
}
