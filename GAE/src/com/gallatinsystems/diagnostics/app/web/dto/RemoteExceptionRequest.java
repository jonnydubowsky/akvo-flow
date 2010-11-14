package com.gallatinsystems.diagnostics.app.web.dto;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import com.gallatinsystems.framework.rest.RestError;
import com.gallatinsystems.framework.rest.RestRequest;

/**
 * data structure to encapsulate requests to the remote exception servlet
 * 
 * @author Christopher Fagiani
 * 
 */
public class RemoteExceptionRequest extends RestRequest {

	private static final long serialVersionUID = 8303938931927567747L;
	private static final String FORMAT_STRING = "yyyy-MM-dd HH:mm:ss";
	private static final DateFormat DATE_FMT = new SimpleDateFormat(
			FORMAT_STRING);

	public static final String SAVE_TRACE_ACTION = "saveTrace";
	
	public static final String PHONE_PARAM = "phoneNumber";
	public static final String DEV_ID_PARAM = "deviceIdentifier";
	public static final String VERSION_PARAM = "version";
	public static final String DATE_PARAM = "date";
	public static final String TRACE_PARAM = "trace";

	private String phoneNumber;
	private String deviceIdent;
	private String version;
	private Date date;
	private String stackTrace;

	public String getStackTrace() {
		return stackTrace;
	}

	public void setStackTrace(String stackTrace) {
		this.stackTrace = stackTrace;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public String getDeviceIdent() {
		return deviceIdent;
	}

	public void setDeviceIdent(String deviceIdent) {
		this.deviceIdent = deviceIdent;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	@Override
	protected void populateErrors() {
		if (phoneNumber == null || phoneNumber.trim().length() == 0) {
			addError(new RestError(RestError.MISSING_PARAM_ERROR_CODE,
					RestError.MISSING_PARAM_ERROR_MESSAGE, PHONE_PARAM
							+ " is required"));
		}
		if (date == null) {
			addError(new RestError(RestError.MISSING_PARAM_ERROR_CODE,
					RestError.MISSING_PARAM_ERROR_MESSAGE, DATE_PARAM
							+ " is required"));
		}
	}

	@Override
	protected void populateFields(HttpServletRequest req) throws Exception {
		phoneNumber = req.getParameter(PHONE_PARAM);
		deviceIdent = req.getParameter(DEV_ID_PARAM);
		version = req.getParameter(VERSION_PARAM);
		stackTrace = req.getParameter(TRACE_PARAM);
		if (req.getParameter(DATE_PARAM) != null) {
			try {
				date = DATE_FMT.parse(req.getParameter(DATE_PARAM));
			} catch (ParseException e) {
				addError(new RestError(RestError.BAD_DATATYPE_CODE,
						RestError.BAD_DATATYPE_MESSAGE, DATE_PARAM
								+ " must be in format: " + FORMAT_STRING));
			}
		}

	}
}