package com.gallatinsystems.device.domain;

public class Status {
	static public enum StatusCode{
		PROCESSED_NO_ERRORS,PROCESSED_WITH_ERRORS, IN_PROGRESS, ERROR_INFLATING_ZIP, REPROCESSING
	}

}