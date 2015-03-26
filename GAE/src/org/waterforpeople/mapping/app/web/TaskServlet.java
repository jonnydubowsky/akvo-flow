/*
 *  Copyright (C) 2010-2015 Stichting Akvo (Akvo Foundation)
 *
 *  This file is part of Akvo FLOW.
 *
 *  Akvo FLOW is free software: you can redistribute it and modify it under the terms of
 *  the GNU Affero General Public License (AGPL) as published by the Free Software Foundation,
 *  either version 3 of the License or any later version.
 *
 *  Akvo FLOW is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Affero General Public License included below for more details.
 *
 *  The full license text can also be seen at <http://www.gnu.org/licenses/agpl.html>.
 */

package org.waterforpeople.mapping.app.web;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;

import org.waterforpeople.mapping.app.web.dto.TaskRequest;
import org.waterforpeople.mapping.dao.DeviceFilesDao;
import org.waterforpeople.mapping.dao.SurveyInstanceDAO;
import org.waterforpeople.mapping.domain.ProcessingAction;
import org.waterforpeople.mapping.domain.Status.StatusCode;
import org.waterforpeople.mapping.domain.SurveyInstance;
import org.waterforpeople.mapping.helper.AccessPointHelper;
import org.waterforpeople.mapping.helper.GeoRegionHelper;
import org.waterforpeople.mapping.helper.SurveyEventHelper;

import services.S3Driver;

import com.gallatinsystems.common.Constants;
import com.gallatinsystems.common.util.MailUtil;
import com.gallatinsystems.common.util.PropertyUtil;
import com.gallatinsystems.common.util.S3Util;
import com.gallatinsystems.device.domain.DeviceFiles;
import com.gallatinsystems.framework.exceptions.SignedDataException;
import com.gallatinsystems.framework.rest.AbstractRestApiServlet;
import com.gallatinsystems.framework.rest.RestRequest;
import com.gallatinsystems.framework.rest.RestResponse;
import com.gallatinsystems.image.GAEImageAdapter;
import com.gallatinsystems.messaging.dao.MessageDao;
import com.gallatinsystems.messaging.domain.Message;
import com.gallatinsystems.survey.dao.SurveyDAO;
import com.gallatinsystems.survey.dao.SurveyUtils;
import com.gallatinsystems.survey.domain.Survey;
import com.gallatinsystems.surveyal.app.web.SurveyalRestRequest;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;

public class TaskServlet extends AbstractRestApiServlet {

    private static final String ALLOW_UNSIGNED = "allowUnsignedData";
    private static final String SIGNING_KEY = "signingKey";
    private static final String SIGNING_ALGORITHM = "HmacSHA1";
    private static String DEVICE_FILE_PATH;
    private static String FROM_ADDRESS;
    private static String BUCKET_NAME;
    private static final String REGION_FLAG = "regionFlag=true";
    private static final long serialVersionUID = -2607990749512391457L;
    private static final Logger log = Logger.getLogger(TaskServlet.class
            .getName());
    private AccessPointHelper aph;
    private SurveyInstanceDAO siDao;
    private final static String EMAIL_FROM_ADDRESS_KEY = "emailFromAddress";
    private TreeMap<String, String> recepientList = null;
    private static final String OBJECTKEY_PREFIX = "devicezip/";

    public TaskServlet() {
        DEVICE_FILE_PATH = com.gallatinsystems.common.util.PropertyUtil
                .getProperty("deviceZipPath");
        FROM_ADDRESS = com.gallatinsystems.common.util.PropertyUtil
                .getProperty(EMAIL_FROM_ADDRESS_KEY);
        BUCKET_NAME = com.gallatinsystems.common.util.PropertyUtil.getProperty("s3bucket");
        aph = new AccessPointHelper();
        siDao = new SurveyInstanceDAO();
        recepientList = MailUtil.loadRecipientList();
    }

    /**
     * Retrieve the file from S3 storage and persist the data to the data store
     *
     * @param fileProcessTaskRequest
     */
    private ArrayList<SurveyInstance> processFile(TaskRequest fileProcessTaskRequest) {
        String fileName = fileProcessTaskRequest.getFileName();
        String phoneNumber = fileProcessTaskRequest.getPhoneNumber();
        String imei = fileProcessTaskRequest.getImei();
        String checksum = fileProcessTaskRequest.getChecksum();
        Integer offset = fileProcessTaskRequest.getOffset();
        String url = DEVICE_FILE_PATH + fileName;

        // add private ACL to zip file
        try {
            S3Util.putObjectAcl(BUCKET_NAME, OBJECTKEY_PREFIX + fileName, S3Util.ACL.PRIVATE);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Error trying to secure zip file: " + e.getMessage(), e);
        }

        // attempt retrieve and extract zip file
        URLConnection conn = null;
        BufferedInputStream deviceZipFileInputStream = null;
        ZipInputStream deviceFilesStream = null;
        ArrayList<String> unparsedLines = null;
        final ArrayList<SurveyInstance> emptyList = new ArrayList<SurveyInstance>();

        try {
            conn = S3Util.getConnection(BUCKET_NAME, OBJECTKEY_PREFIX + fileName);
            deviceZipFileInputStream = new BufferedInputStream(conn.getInputStream());
            deviceFilesStream = new ZipInputStream(deviceZipFileInputStream);
            unparsedLines = extractDataFromZip(deviceFilesStream);
        } catch (Exception e) {
            // catchall
            int retry = fileProcessTaskRequest.getRetry();
            if (++retry > Constants.MAX_TASK_RETRIES) {
                String message = String.format("Failed to process file (%s) after (%s) retries.",
                        url, Constants.MAX_TASK_RETRIES);
                sendMail(fileProcessTaskRequest, message);
                log.severe(message + "\n\n" + e.getMessage());
                return emptyList;
            }

            // retry processing
            fileProcessTaskRequest.setRetry(retry);
            rescheduleTask(fileProcessTaskRequest);
            log.log(Level.WARNING,
                    "Failed to process zip file: Rescheduling... " + url + " : " + e.getMessage());
            return emptyList;
        } finally {
            try {
                deviceFilesStream.close();
            } catch (IOException e) {
                log.log(Level.WARNING, "Failed to close zip input stream");
            }
        }

        // create device file entity
        DeviceFilesDao dfDao = new DeviceFilesDao();

        List<DeviceFiles> dfList = null;
        DeviceFiles deviceFile = null;
        dfList = dfDao.listByUri(url);
        if (dfList != null && dfList.size() > 0) {
            deviceFile = dfList.get(0);
        }
        if (deviceFile == null) {
            deviceFile = new DeviceFiles();
        }
        deviceFile.setProcessDate(getNowDateTimeFormatted());
        deviceFile.setProcessedStatus(StatusCode.IN_PROGRESS);
        deviceFile.setURI(url);
        deviceFile.setPhoneNumber(phoneNumber);
        deviceFile.setImei(imei);
        deviceFile.setChecksum(checksum);
        deviceFile.setUploadDateTime(new Date());
        Date collectionDate = new Date();

        // process file lines
        ArrayList<SurveyInstance> surveyInstances = new ArrayList<SurveyInstance>();
        if (unparsedLines != null && unparsedLines.size() > 0) {
            if (REGION_FLAG.equals(unparsedLines.get(0))) {
                unparsedLines.remove(0);
                GeoRegionHelper grh = new GeoRegionHelper();
                grh.processRegionsSurvey(unparsedLines);
            } else {

                int lineNum = offset;
                String curId = null;
                while (lineNum < unparsedLines.size()) {
                    String[] parts = unparsedLines.get(lineNum).split("\t");
                    if (parts.length < 5) {
                        parts = unparsedLines.get(lineNum).split(",");
                    }
                    if (parts.length >= 2) {
                        if (curId == null) {
                            curId = parts[1];
                        } else {
                            // if this isn't the first time through and
                            // we are seeing a new id, break since we'll
                            // process that in another call
                            if (!curId.equals(parts[1])) {
                                break;
                            }
                        }
                    }
                    lineNum++;
                }

                Long userID = 1L;
                dfDao.save(deviceFile);
                SurveyInstance inst = siDao.save(collectionDate,
                        deviceFile, userID,
                        unparsedLines.subList(offset, lineNum));
                if (inst != null) {
                    // fire a survey event
                    SurveyEventHelper.fireEvent(
                            SurveyEventHelper.SUBMISSION_EVENT,
                            inst.getSurveyId(), inst.getKey().getId());
                    surveyInstances.add(inst);
                    // TODO: HACK because we were saving so many duplicate
                    // device files this way they all get the same status
                    if (dfList != null) {
                        for (DeviceFiles dfitem : dfList) {
                            dfitem.setProcessedStatus(inst.getDeviceFile()
                                    .getProcessedStatus());
                        }
                    }
                }
                if (lineNum < unparsedLines.size()) {
                    if (inst != null) {
                        StatusCode processingStatus = inst.getDeviceFile()
                                .getProcessedStatus();
                        if (processingStatus
                                .equals(StatusCode.PROCESSED_WITH_ERRORS)) {
                            String message = "Error in file during first processing step. Continuing to next part";
                            deviceFile.addProcessingMessage(message);
                            deviceFile
                                    .setProcessedStatus(StatusCode.IN_PROGRESS);
                        } else {
                            deviceFile.addProcessingMessage("Processed "
                                    + lineNum
                                    + " lines spawning queue call");
                            deviceFile
                                    .setProcessedStatus(StatusCode.IN_PROGRESS);
                        }
                    }
                    // if we haven't processed everything yet, invoke a
                    // new service
                    Queue queue = QueueFactory.getDefaultQueue();
                    queue.add(TaskOptions.Builder.withUrl("/app_worker/task")
                            .param("action", "processFile")
                            .param("fileName", fileName)
                            .param("offset", lineNum + ""));
                } else {
                    StatusCode status = StatusCode.PROCESSED_NO_ERRORS;
                    if (deviceFile.getProcessedStatus() != null) {
                        status = deviceFile.getProcessedStatus();
                    }
                    deviceFile.setProcessedStatus(status);
                    if (dfList != null) {
                        for (DeviceFiles dfitem : dfList) {
                            dfitem.setProcessedStatus(status);
                        }
                    }

                }
            }
        } else {
            String message = "Error empty file: " + deviceFile.getURI();

            log.log(Level.SEVERE, message);

            deviceFile.setProcessedStatus(StatusCode.PROCESSED_WITH_ERRORS);
            deviceFile.addProcessingMessage(message);

            sendMail(fileProcessTaskRequest, message);
        }

        dfDao.save(dfList);

        return surveyInstances;
    }

    public static ArrayList<String> extractDataFromZip(ZipInputStream deviceZipFileInputStream)
            throws ZipException, IOException, SignedDataException {
        ArrayList<String> lines = new ArrayList<String>();
        String line = null;
        String surveyDataOnly = null;
        String dataSig = null;
        ZipEntry entry;
        while ((entry = deviceZipFileInputStream.getNextEntry()) != null) {
            log.info("Unzipping: " + entry.getName());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int size;
            while ((size = deviceZipFileInputStream.read(buffer, 0, buffer.length)) != -1) {
                out.write(buffer, 0, size);
            }
            line = out.toString("UTF-8");

            if (entry.getName().endsWith("txt")) {
                if (entry.getName().equals("regions.txt")) {
                    lines.add("regionFlag=true");
                } else {
                    surveyDataOnly = line;
                }
                String[] linesSplit = line.split("\n");
                for (String s : linesSplit) {
                    if (s.contains("\u0000")) {
                        s = s.replaceAll("\u0000", "");
                    }
                    lines.add(s);
                }
            } else if (entry.getName().endsWith(".sig")) {
                dataSig = line.trim();
            } else {
                S3Driver s3 = new S3Driver();
                String[] imageParts = entry.getName().split("/");
                // comment out while testing locally
                try {
                    // GAEImageAdapter gaeIA = new GAEImageAdapter();
                    // byte[] resizedImage =
                    // gaeIA.resizeImage(out.toByteArray(), 500, 500);
                    // s3.uploadFile("dru-test", imageParts[1], resizedImage);
                    GAEImageAdapter gaeImg = new GAEImageAdapter();
                    byte[] newImage = gaeImg.resizeImage(out.toByteArray(),
                            500, 500);
                    s3.uploadFile("dru-test", imageParts[1], newImage);
                    // add queue call to resize
                    Queue queue = QueueFactory.getDefaultQueue();

                    queue.add(TaskOptions.Builder.withUrl("imageprocessor").param("imageURL",
                            imageParts[1]));
                    log.info("submiting image resize for imageURL: "
                            + imageParts[1]);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                out.close();
            }
            deviceZipFileInputStream.closeEntry();
        }

        // check the signature if we have it
        String allowUnsigned = PropertyUtil.getProperty(ALLOW_UNSIGNED);

        if ("false".equalsIgnoreCase(allowUnsigned)) {

            if (dataSig == null) {
                throw new SignedDataException("Datafile does not have a signature");
            }

            if (surveyDataOnly == null) {
                throw new SignedDataException("data.txt not found in data zip");
            }

            try {
                MessageDigest sha1Digest = MessageDigest.getInstance("SHA1");
                byte[] digest = sha1Digest.digest(surveyDataOnly
                        .getBytes("UTF-8"));
                SecretKeySpec signingKey = new SecretKeySpec(PropertyUtil
                        .getProperty(SIGNING_KEY).getBytes("UTF-8"),
                        SIGNING_ALGORITHM);
                Mac mac = Mac.getInstance(SIGNING_ALGORITHM);
                mac.init(signingKey);
                byte[] hmac = mac.doFinal(digest);

                String encodedHmac = com.google.gdata.util.common.util.Base64
                        .encode(hmac);
                if (!encodedHmac.trim().equals(dataSig.trim())) {
                    throw new SignedDataException(
                            "Computed signature does not match the one submitted with the data");
                }
            } catch (GeneralSecurityException e) {
                throw new SignedDataException("Could not calculate signature", e);
            }
        }

        return lines;
    }

    /**
     * Requeue the file processing task for execution after TASK_RETRY_INTERVAL mins
     *
     * @param fileProcessingRequest
     */
    private void rescheduleTask(TaskRequest fileProcessingRequest) {
        Queue defaultQueue = QueueFactory.getDefaultQueue();
        TaskOptions options = TaskOptions.Builder.withUrl("/app_worker/task")
                .param(TaskRequest.ACTION_PARAM, TaskRequest.PROCESS_FILE_ACTION)
                .param(TaskRequest.TASK_RETRY_PARAM, fileProcessingRequest.getRetry().toString())
                .param(TaskRequest.FILE_NAME_PARAM, fileProcessingRequest.getFileName())
                .countdownMillis(Constants.TASK_RETRY_INTERVAL);

        if (fileProcessingRequest.getPhoneNumber() != null) {
            options.param(TaskRequest.PHONE_NUM_PARAM, fileProcessingRequest.getPhoneNumber());
        }

        if (fileProcessingRequest.getImei() != null) {
            options.param(TaskRequest.IMEI_PARAM, fileProcessingRequest.getImei());
        }

        if (fileProcessingRequest.getChecksum() != null) {
            options.param(TaskRequest.CHECKSUM_PARAM, fileProcessingRequest.getChecksum());
        }

        if (fileProcessingRequest.getOffset() != null) {
            options.param(TaskRequest.OFFSET_PARAM, fileProcessingRequest.getOffset().toString());
        }

        defaultQueue.add(options);
    }

    /**
     * Send an email regarding file processing status/outcome
     *
     * @param fileProcessingRequest
     * @param subject
     * @param messageBody
     */
    private void sendMail(TaskRequest fileProcessingRequest, String body) {
        String fileName = fileProcessingRequest.getFileName();
        String subject = "Device File Processing Error: " + fileName;
        String messageBody = DEVICE_FILE_PATH + fileName + "\n" + body;

        MailUtil.sendMail(FROM_ADDRESS, "FLOW", recepientList, subject, messageBody);
    }

    private String getNowDateTimeFormatted() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH:mm:ss");
        java.util.Date date = new java.util.Date();
        String dateTime = dateFormat.format(date);
        return dateTime;
    }

    private ProcessingAction dispatch(String surveyKey) {
        ProcessingAction pa = new ProcessingAction();

        pa.setAction("addAccessPoint");
        pa.setDispatchURL("/worker/task");
        pa.addParam("surveyId", surveyKey);
        return pa;
    }

    @Override
    protected RestRequest convertRequest() throws Exception {
        HttpServletRequest req = getRequest();
        RestRequest restRequest = new TaskRequest();
        restRequest.populateFromHttpRequest(req);
        return restRequest;
    }

    @Override
    protected RestResponse handleRequest(RestRequest request) throws Exception {
        RestResponse response = new RestResponse();
        TaskRequest taskReq = (TaskRequest) request;
        if (TaskRequest.PROCESS_FILE_ACTION.equalsIgnoreCase(taskReq
                .getAction())) {
            ingestFile(taskReq);
        } else if (TaskRequest.ADD_ACCESS_POINT_ACTION.equalsIgnoreCase(taskReq
                .getAction())) {
            addAccessPoint(taskReq);
        }
        return response;
    }

    @Override
    protected void writeOkResponse(RestResponse resp) throws Exception {
        getResponse().setStatus(200);
    }

    private void addAccessPoint(TaskRequest req) {
        Long surveyInstanceId = req.getSurveyId();
        log.info("Received Task Queue calls for surveyInstanceId: "
                + surveyInstanceId);

        aph.processSurveyInstance(surveyInstanceId.toString());
    }

    /**
     * handles the callback from the device indicating that a new data file is available. This
     * method will call processFile to retrieve the file and persist the data to the data store it
     * will then add access points for each water point in the survey responses.
     *
     * @param req
     */
    @SuppressWarnings("rawtypes")
    private void ingestFile(TaskRequest req) {
        if (req.getFileName() != null) {
            log.info("	Task->processFile");
            ArrayList<SurveyInstance> surveyInstances = processFile(req);
            Map<Long, Survey> surveyMap = new HashMap<Long, Survey>();
            SurveyDAO surveyDao = new SurveyDAO();
            Queue defaultQueue = QueueFactory.getDefaultQueue();
            for (SurveyInstance instance : surveyInstances) {
                Survey s = surveyMap.get(instance.getSurveyId());
                if (s == null) {
                    s = surveyDao.getById(instance.getSurveyId());
                    surveyMap.put(instance.getSurveyId(), s);
                }
                if (s != null && s.getRequireApproval() != null && s.getRequireApproval()) {
                    // if the survey requires approval, don't run any of the
                    // processors
                    instance.setApprovedFlag("False");
                    continue;
                } else {
                    ProcessingAction pa = dispatch(instance.getKey().getId()
                            + "");
                    TaskOptions options = TaskOptions.Builder.withUrl(pa.getDispatchURL());
                    Iterator it = pa.getParams().keySet().iterator();
                    while (it.hasNext()) {
                        options.param("key", (String) it.next());
                    }
                    log.info("Received Task Queue calls for surveyInstanceKey: "
                            + instance.getKey().getId() + "");
                    aph.processSurveyInstance(instance.getKey().getId() + "");

                    defaultQueue.add(TaskOptions.Builder.withUrl("/app_worker/surveyalservlet")
                            .param(
                                    SurveyalRestRequest.ACTION_PARAM,
                                    SurveyalRestRequest.INGEST_INSTANCE_ACTION).param(
                                    SurveyalRestRequest.SURVEY_INSTANCE_PARAM,
                                    instance.getKey().getId() + ""));
                }
            }
            SurveyUtils.notifyReportService(surveyMap.keySet(), "invalidate");

            MessageDao msgDao = new MessageDao();
            Message message = new Message();
            message.setShortMessage(req.getFileName() + " processed - Surveys: "
                    + surveyMap.keySet());

            if (req.getFileName().startsWith("wfpGenerated")) {
                message.setActionAbout("bulkProcessed");
            } else {
                message.setActionAbout("fileProcessed");
            }

            if (surveyMap.keySet().size() == 1) {
                Survey s = surveyMap.values().iterator().next();
                if (s != null) {
                    message.setObjectId(s.getKey().getId());
                    message.setObjectTitle(s.getPath() + "/" + s.getName());
                }
            }
            msgDao.save(message);
        }
    }

}
