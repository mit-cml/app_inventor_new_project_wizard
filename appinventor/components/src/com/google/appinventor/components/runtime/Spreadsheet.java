// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2020 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0


package com.google.appinventor.components.runtime;

import static android.Manifest.permission.ACCOUNT_MANAGER;
import static android.Manifest.permission.GET_ACCOUNTS;
import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import android.app.Activity;
import android.util.Log;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.ClearValuesResponse;
import com.google.api.services.sheets.v4.model.DeleteDimensionRequest;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.UsesActivities;
import com.google.appinventor.components.annotations.UsesLibraries;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.annotations.androidmanifest.ActionElement;
import com.google.appinventor.components.annotations.androidmanifest.ActivityElement;
import com.google.appinventor.components.annotations.androidmanifest.IntentFilterElement;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.common.YaVersion;
import com.google.appinventor.components.runtime.util.AsynchUtil;
import com.google.appinventor.components.runtime.util.ChartDataSourceUtil;
import com.google.appinventor.components.runtime.util.CsvUtil;
import com.google.appinventor.components.runtime.util.MediaUtil;
import com.google.appinventor.components.runtime.util.YailList;
import gnu.lists.LList;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * Spreadsheet is a non-visible component for storing and receiving data from
 * a Google Sheets document using the Google Sheets API.
 *
 * In order to utilize this component, one must first have a Google Developer
 * Account. Then, one must create a new project under that Google Developer
 * Account, enable the Google Sheets API on that project, and finally create a
 * Service Account for the Sheets API.
 *
 * Instructions on how to create the Service Account, as well as where to find
 * other relevant information for using the Google Sheets Component, can be
 * found <a href='/reference/other/googlesheets-api-setup.html'>here</a>.
 *
 * Row and column numbers are 1-indexed.
 */
@DesignerComponent(version = YaVersion.SPREADSHEET_COMPONENT_VERSION,
    category = ComponentCategory.STORAGE,
    description = "Spreadsheet is a non-visible component for storing and " +
      "receiving data from a Spreadsheet document using the Google Sheets API. " +
      "<p>In order to utilize this component, one must first have a Google " +
      "Developer Account. Then, one must create a new project under that Google " +
      "Developer Account, enable the Google Sheets API on that project, and " +
      "finally create a Service Account for the Sheets API.</p>" +
      "<p>Instructions on how to create the Service Account, as well as where to " +
      "find other relevant information for using the Spreadsheet Component, " +
      "can be found <a href='/reference/other/googlesheets-api-setup.html'>" +
      "here</a>.</p>",
    nonVisible = true,
    iconName = "images/spreadsheet.png")
@SimpleObject
@UsesPermissions({
    INTERNET,
    ACCOUNT_MANAGER,
    GET_ACCOUNTS,
    WRITE_EXTERNAL_STORAGE,
    READ_EXTERNAL_STORAGE
})
@UsesLibraries({
    "googlesheets.jar",
    "jackson-core.jar",
    "google-api-client.jar",
    "google-api-client-jackson2.jar",
    "google-http-client.jar",
    "google-http-client-jackson2.jar",
    "google-oauth-client.jar",
    "google-oauth-client-jetty.jar",
    "grpc-context.jar",
    "opencensus.jar",
    "opencensus-contrib-http-util.jar",
    "guava.jar",
    "jetty.jar",
    "jetty-util.jar"
})
@UsesActivities(activities = {
    @ActivityElement(name = "com.google.appinventor.components.runtime.WebViewActivity",
       configChanges = "orientation|keyboardHidden",
       screenOrientation = "behind",
       intentFilters = {
           @IntentFilterElement(actionElements = {
               @ActionElement(name = "android.intent.action.MAIN")
           })
    })
})
public class Spreadsheet extends AndroidNonvisibleComponent implements Component,
    ObservableDataSource<YailList, Future<YailList>> {
  private static final String LOG_TAG = "SPREADSHEET";

  private static final String WEBVIEW_ACTIVITY_CLASS = WebViewActivity.class
    .getName();
  private int requestCode;
  public Spreadsheet(ComponentContainer componentContainer) {
    super(componentContainer.$form());
    this.container = componentContainer;
    this.activity = componentContainer.$context();
  }

  // Designer Properties
  private String apiKey;
  private String credentialsPath;
  private String spreadsheetID = "";
  // This gets changed to the name of the project by MockSpreadsheet by default
  private String ApplicationName = "App Inventor";

  // Variables for Authenticating the Spreadsheet Component
  private File cachedCredentialsFile = null;
  private String accessToken = null;
  private Sheets sheetsService = null;

  //   private final Activity activity;
  private final ComponentContainer container;
  private final Activity activity;

  private FutureTask<Void> lastTask = null;

  private YailList columns = new YailList();

  private Set<DataSourceChangeListener> observers = new HashSet<>();

  private HashMap<String, Integer> sheetIdMap = new HashMap<>();

  private int getSheetID(Sheets sheetsSvcParam, String sheetName) {
    if (sheetIdMap.containsKey(sheetName)) {
      return sheetIdMap.get(sheetName);
    } else {
      try {
        Sheets.Spreadsheets.Get getSheetRequest = sheetsSvcParam.spreadsheets().get(spreadsheetID);
        List<String> ranges = new ArrayList<>();
        ranges.add(sheetName);
        getSheetRequest.setRanges(ranges);
        getSheetRequest.setIncludeGridData(false);
        com.google.api.services.sheets.v4.model.Spreadsheet testSheet = getSheetRequest.execute();
        if (testSheet.size() == 0) {
          return -1;
        }
        int sheetID = testSheet.getSheets().get(0).getProperties().getSheetId();
        sheetIdMap.put(sheetName, sheetID);
        return sheetID;
      } catch (java.io.IOException e) {
        ErrorOccurred("getSheetID: IOException - " + e.getMessage());
        return -1;
      } catch (Exception e) {
        ErrorOccurred("getSheetID: Unknown Exception - " + e.getMessage());
        return -1;
      }
    }
  }

  /* Getter and Setters for Properties */

  @SimpleProperty
  public String CredentialsJson() {
    return credentialsPath;
  }

  @DesignerProperty(
    editorType = PropertyTypeConstants.PROPERTY_TYPE_ASSET,
    defaultValue = "")
  @SimpleProperty(
    description = "The JSON File with credentials for the Service Account")
  public void CredentialsJson (String credentialsPath) {
    this.credentialsPath = credentialsPath;
  }

  @SimpleProperty
  public String SpreadsheetID() {
    return spreadsheetID;
  }

  @DesignerProperty(
    editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING,
    defaultValue = "")
  @SimpleProperty(
    description="The ID for the Google Sheets file you want to edit. You can " +
      "find the spreadsheetID in the URL of the Google Sheets file.")
  public void SpreadsheetID(String spreadsheetID) {
    if (spreadsheetID.startsWith("https:")) {
      // URL: https://docs.google.com/spreadsheets/d/<id>/edit#gid=0
      String[] parts = spreadsheetID.substring(8).split("/");
      spreadsheetID = parts[3];
    }
    this.spreadsheetID = spreadsheetID;
  }

  /**
   * Specifies the name of the application given when doing an API call.
   *
   * @internaldoc
   * This is set programmatically
   * in {@link com.google.appinventor.client.editor.simple.components.MockSpreadsheet}
   * and consists of the current App Inventor project name.
   *
   * @param ApplicationName the name of the App
   */
  @SimpleProperty(
    userVisible = false)
  public String ApplicationName() {
    return ApplicationName;
  }

  @DesignerProperty(
    editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING,
    defaultValue = "App Inventor")
  @SimpleProperty(
    description="The name of your application, used when making API calls.")
  public void ApplicationName(String ApplicationName) {
    this.ApplicationName = ApplicationName;
  }

  /* Utility Functions for Making Calls */

  private GoogleCredential authorize() throws IOException {
    if (cachedCredentialsFile == null) {
      cachedCredentialsFile = MediaUtil.copyMediaToTempFile(
        container.$form(),
        this.credentialsPath
      );
    }

    // Convert the above java.io.File -> InputStream
    InputStream in = new FileInputStream(cachedCredentialsFile);

    // TODO: Catch Malformed Credentials JSON
    GoogleCredential credential = GoogleCredential.fromStream(in)
      .createScoped(Arrays.asList(SheetsScopes.SPREADSHEETS));
    credential.refreshToken();
    accessToken = credential.getAccessToken();
    Log.d(LOG_TAG, "Credential after refresh token: " + accessToken);
    return credential;
  }

  // Uses the Google Sheets Credentials to create a Google Sheets API instance
  // required for all other Google Sheets API calls
  private Sheets getSheetsService () throws IOException, GeneralSecurityException {
    // Generate a new sheets service only if there is not one already created
    if (sheetsService == null) {
      GoogleCredential credential = authorize();
      this.sheetsService = new Sheets.Builder(new NetHttpTransport(),
        JacksonFactory.getDefaultInstance(), credential)
        .setApplicationName(ApplicationName)
        .build();
    }
    return sheetsService;
  }

  // Yields the A1 notation for the column, e.g. col 1 = A, col 2 = B, etc
  private String getColString (int colNumber) {
    if (colNumber == 0)
      return "";
    String[] alphabet = {
      "A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R",
      "S","T","U","V","W","X","Y","Z"};
    String colReference = "";
    while (colNumber > 0) {
      String digit = alphabet[(colNumber-1) % 26];
      colReference = digit + colReference;
      colNumber = (int) Math.floor((colNumber-1) / 26);
    }
    return colReference;
  }

  /* Error Catching Handler */

  @SimpleEvent(
    description="Triggered whenever an API call encounters an error. Details " +
      "about the error are in `errorMessage`.")
  public void ErrorOccurred (final String errorMessage) {
    final Spreadsheet thisInstance = this;
    Log.d(LOG_TAG, errorMessage);
    activity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        EventDispatcher.dispatchEvent(thisInstance, "ErrorOccurred", errorMessage);
      }
    });
  }

  /* Helper Functions for the User */

  /**
   * Converts the integer representation of rows and columns to A1-Notation used
   * in Google Sheets for a single cell. For example, row 1 and col 2
   * corresponds to the string \"B1\".
   */
  @SimpleFunction(
    description="Converts the integer representation of rows and columns to " +
      "A1-Notation used in Google Sheets for a single cell.")
  public String GetCellReference(int row, int col) {
    String[] alphabet = {
      "A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R",
      "S","T","U","V","W","X","Y","Z"};
    String colRange = "";
    while (col > 0) {
      String digit = alphabet[(col-1) % 26];
      colRange = digit + colRange;
      col = (int) Math.floor((col-1) / 26);
    }
    return colRange + Integer.toString(row);
  }

  /**
   * Converts the integer representation of rows and columns for the corners of
   * the range to A1-Notation used in Google Sheets. For example, selecting the
   * range from row 1, col 2 to row 3, col 4 corresponds to the string "B1:D3".
   */
  @SimpleFunction(
    description="Converts the integer representation of rows and columns for " +
      "the corners of the range to A1-Notation used in Google Sheets.")
  public String GetRangeReference(int row1, int col1, int row2, int col2) {
    return GetCellReference(row1, col1) + ":" + GetCellReference(row2, col2);
  }

  /* Filters and Methods that Use Filters */

  /**
   * The callbeck event for the {@link #ReadWithQuery()} block. The `response`
   * is a list of rows, where each row satisfies the query.
   */
  @SimpleEvent(
      description="The callback event for the ReadWithExactQuery or ReadWithPartialQuery block. The " +
          "`response` is a list of rows numbers and a list of rows containing cell data.")
  public void GotFilterResult (final List<Integer> return_rows,
                               List<List<String>> return_data) {
    Log.d(LOG_TAG, "GotFilterResult got: " + return_rows);
    EventDispatcher.dispatchEvent(this, "GotFilterResult", return_rows, return_data);
  }

  // Helpers for making the HTTP Request for ReadWithQuery
  private static String getResponseContent(HttpURLConnection connection) throws IOException {
    // Use the content encoding to convert bytes to characters.
    String encoding = connection.getContentEncoding();
    if (encoding == null) {
      encoding = "UTF-8";
    }
    InputStreamReader reader = new InputStreamReader(getConnectionStream(connection), encoding);
    try {
      int contentLength = connection.getContentLength();
      StringBuilder sb = (contentLength != -1)
          ? new StringBuilder(contentLength)
          : new StringBuilder();
      char[] buf = new char[1024];
      int read;
      while ((read = reader.read(buf)) != -1) {
        sb.append(buf, 0, read);
      }
      return sb.toString();
    } finally {
      reader.close();
    }
  }

  private static InputStream getConnectionStream(HttpURLConnection connection) throws SocketTimeoutException {
    // According to the Android reference documentation for HttpURLConnection: If the HTTP response
    // indicates that an error occurred, getInputStream() will throw an IOException. Use
    // getErrorStream() to read the error response.
    try {
      return connection.getInputStream();
    } catch (SocketTimeoutException e) {
      throw e; //Rethrow exception - should not attempt to read stream for timeouts
    } catch (IOException e1) {
      // Use the error response for all other IO Exceptions.
      return connection.getErrorStream();
    }
  }

  /* Filters and Methods that Use Filters */

  /**
   *
   */
  @SimpleFunction(
      description="Filters a Google Sheet for rows where the given column number "
                      + "matches the provided value.")
  public void ReadWithExactFilter(final String sheetName, final int colID, final String value) {
    Log.d(LOG_TAG, "ReadRowsWithFilter colID " + colID + ", value " + value);

    if (spreadsheetID == "" || spreadsheetID == null) {
      ErrorOccurred("ReadWithExactFilter: " + "SpreadsheetID is empty.");
      return;
    }

    AsynchUtil.runAsynchronously(RetrieveSheet(sheetName, colID, value, true, true));
  }

  /**
   *
   */
  @SimpleFunction(
      description="Filters a Google Sheet for rows where the given column number "
                      + "contains the provided value string.")
  public void ReadWithPartialFilter(final String sheetName, final int colID, final String value) {
    Log.d(LOG_TAG, "ReadWithPartialFilter colID " + colID + ", value " + value);

    if (spreadsheetID == "" || spreadsheetID == null) {
      ErrorOccurred("ReadWithPartialFilter: " + "SpreadsheetID is empty.");
      return;
    }

    AsynchUtil.runAsynchronously(RetrieveSheet(sheetName, colID, value, false, true));
  }

  /* Row-wise Operations */

  /**
   * On the page with the provided sheetName, reads the row at the given
   * rowNumber and triggers the {@link #GotRowData()} callback event.
   */
  @SimpleFunction(
    description="On the page with the provided sheetName, this method will " +
      "read the row at the given rowNumber and trigger the GotRowData " +
      "callback event.")
  public void ReadRow (String sheetName, int rowNumber) {

    if (spreadsheetID == "" || spreadsheetID == null) {
      ErrorOccurred("ReadRow: " + "SpreadsheetID is empty.");
      return;
    }
    // If there is no credentials file,
    //   Make a simple HTTP Request
    // Otherwise (there is a credentials file),
    //   Use the Google Sheets API

    // Properly format the Range Reference
    Log.d(LOG_TAG, "Read Row number: " + rowNumber);
    final String rangeReference = sheetName +  "!" + rowNumber + ":" + rowNumber;

    // Asynchronously fetch the data in the cell
    AsynchUtil.runAsynchronously(new Runnable() {
      @Override
      public void run () {
        try {
          // If no Credentials.json is provided, attempt the HTTP request
          if (credentialsPath == null) {
            // Cleans the formatted url in case the sheetname needs to be cleaned
            String cleanRangeReference = "";
            try {
              cleanRangeReference = URLEncoder.encode(rangeReference, "UTF-8");
            } catch (UnsupportedEncodingException e) {
              ErrorOccurred("ReadRow: Error occurred encoding the query. UTF-8 is unsupported?");
              return;
            }

            // Formats the data into the URL to read the range
            String getUrl = String.format(
              "https://docs.google.com/spreadsheets/d/%s/export?format=csv&range=%s",
              spreadsheetID, cleanRangeReference);
            Log.d(LOG_TAG, "ReadRow url: " + getUrl);

            // Make the HTTP Request
            URL url = new URL(getUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
              connection.setRequestMethod("GET");
            // Catch Bad HTTP Request
            if (connection.getResponseCode() == 400) {
              ErrorOccurred("ReadRow: Bad HTTP Request. Please check the address and try again. " + getUrl);
              return;
            }

            // Parse the Response
            String responseContent = getResponseContent(connection);
            final YailList parsedCsv = CsvUtil.fromCsvTable(responseContent);

            for (Object elem : (LList) parsedCsv.getCdr()) {
              if (!(elem instanceof YailList))
                continue;
              final YailList row = (YailList) elem;
              // We need to re-enter the main thread before we can dispatch the event!
              activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  GotRowData(row);
                }
              });
              return;
            }
            ErrorOccurred("ReadRow: Could not find a row from the HTTP Request.");
            return;
          }

          // Run this if there is a credentials json provided.
          Sheets sheetsService = getSheetsService();
          ValueRange readResult = sheetsService.spreadsheets().values()
            .get(spreadsheetID, rangeReference ).execute();
          // Get the actual data from the response
          List<List<Object>> values = readResult.getValues();
          // If the data we got is empty, then return so.
          if (values == null || values.isEmpty())
            ErrorOccurred("ReadRow: No data found");

          // Format the result as a list of strings and run the callback
          else {
            final List<String> ret = new ArrayList<String>();
            for (Object obj : values.get(0)) {
              ret.add(String.format("%s", obj == null ? "" : obj));
            }

            // We need to re-enter the main thread before we can dispatch the event!
            activity.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                GotRowData(ret);
              }
            });
          }
        }
        // Handle Errors which may have occured while sending the Read Request!
        catch (Exception e) {
          e.printStackTrace();
          ErrorOccurred("ReadRow: " + e.getMessage());
        }
      }
    });
  }

  /**
   * The callback event for the {@link #ReadRow()} block. The `rowDataList` is a
   * list of text cell-values in order of increasing column number.
   */
  @SimpleEvent(
    description="The callback event for the ReadRow block. The `rowDataList` " +
      "is a list of cell values in order of increasing column number.")
  public void GotRowData (final List<String> rowDataList) {
    EventDispatcher.dispatchEvent(this, "GotRowData", rowDataList);
  }

  /**
   * Given a list of values as `data`, writes the values to the row  with the
   * given row number, overriding existing values from left to right. (Note: It
   * will not erase the entire row.) Once complete, it triggers the
   * {@link #FinishedWriteRow()} callback event.
   */
  @SimpleFunction(
    description="Given a list of values as `data`, writes the values to the " +
      "row of the sheet with the given row number.")
  public void WriteRow (String sheetName, int rowNumber, YailList data) {

    if (spreadsheetID == "" || spreadsheetID == null) {
      ErrorOccurred("WriteRow: " + "SpreadsheetID is empty.");
      return;
    } else if (credentialsPath == "" || credentialsPath == null) {
      ErrorOccurred("WriteRow: " + "Credentials JSON file is required.");
      return;
    }

    // Generates the A1 Reference for the operation
    final String rangeRef = String.format("%s!A%d", sheetName, rowNumber);

    // Generates the 2D list, which are the values to assign to the range
    LList rowValues = (LList) data.getCdr();
    List<List<Object>> values = new ArrayList<>();
    List<Object> row = new ArrayList<Object>(rowValues);
    values.add(row);

    // Sets the 2D list above to be the values in the body of the API Call
    final ValueRange body = new ValueRange()
      .setValues(values);

    // Wrap the API Call in an Async Utility
    AsynchUtil.runAsynchronously(new Runnable() {
      @Override
      public void run () {
        // Surround the operation with a try catch statement
        try {
          Sheets sheetsService = getSheetsService();
          // UpdateValuesResponse result =
          sheetsService.spreadsheets().values()
            .update(spreadsheetID, rangeRef, body)
            .setValueInputOption("USER_ENTERED")
            .execute();
          // Re-enter main thread to call the Event Block
          activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              FinishedWriteRow();
            }
          });
        }
        catch (Exception e) {
          e.printStackTrace();
          ErrorOccurred("WriteRow: " + e.getMessage());
        }
      }
    });
  }

  /**
   * The callback event for the {@link #WriteRow()} block, called once the
   * values on the table have been updated.
   */
  @SimpleEvent(
    description="The callback event for the WriteRow block, called after the " +
      "values on the table have finished updating")
  public void FinishedWriteRow () {
    EventDispatcher.dispatchEvent(this, "FinishedWriteRow");
  }

  /**
   * Given a list of values as `data`, appends the values to the next
   * empty row of the sheet. It will always start from the left most column and
   * continue to the right. Once complete, it triggers the {@link #FinishedAddRow()}
   * callback event. Additionally, this returns the row number for the new row.
   */
  @SimpleFunction(
    description="Given a list of values as `data`, appends the values " +
      "to the next empty row of the sheet. Additionally, this returns " +
      "the row number for the new row.")
  public void AddRow (final String sheetName, YailList data) {
    if (spreadsheetID == "" || spreadsheetID == null) {
      ErrorOccurred("AddRow: " + "SpreadsheetID is empty.");
      return;
    } else if (credentialsPath == "" || credentialsPath == null) {
      ErrorOccurred("AddRow: " + "Credentials JSON is required.");
      return;
    }

    final String rangeRef = sheetName;

    // Generates the 2D list, which are the values to assign to the range
    LList rowValues = (LList) data.getCdr();
    List<List<Object>> values = new ArrayList<>();
    List<Object> row = new ArrayList<Object>(rowValues);
    values.add(row);

    // Sets the 2D list above to be the values in the body of the API Call
    final ValueRange body = new ValueRange()
      .setValues(values).setRange(sheetName);

    // Run the API call asynchronously
    AsynchUtil.runAsynchronously(new Runnable() {
      @Override
      public void run () {
        try {
          Sheets sheetsService = getSheetsService();

          ValueRange readResult = sheetsService.spreadsheets().values()
              .get(spreadsheetID, rangeRef).execute();
          // Get the actual data from the response
          List<List<Object>> values = readResult.getValues();
          // If the data we got is empty, then return so.
          if (values == null || values.isEmpty())
            ErrorOccurred("AddRow: No data found");

          // nextCol gets mutated, keep addedColumn as a constant
          int max_row = values.size() + 1;

          // Sends the append values request
          AppendValuesResponse response = sheetsService.spreadsheets().values()
            .append(spreadsheetID, rangeRef + "!A" + max_row, body.setRange(rangeRef + "!A" + max_row))
            .setValueInputOption("USER_ENTERED") // USER_ENTERED or RAW
            .setInsertDataOption("INSERT_ROWS") // INSERT_ROWS or OVERRIDE
            .execute();

          // getUpdatedRange returns the range that updates were applied in A1
          String updatedRange = response.getUpdates().getUpdatedRange();
          // updatedRange is in the form SHEET_NAME!A#:END# => We want #
          String cell = updatedRange.split("!")[1].split(":")[0];
          // Remove non-numeric characters from the string
          final int rowNumber = Integer.parseInt(cell.replaceAll("[^\\d.]", ""));
          activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              FinishedAddRow(rowNumber);
            }
          });
          if (observers.size() > 0) {
            RetrieveSheet(sheetName, -1, null, false, false);
          }
        }
        catch (Exception e) {
          e.printStackTrace();
          ErrorOccurred("AddRow: " + e.getMessage());
        }
      }
    });
  }

  /**
   * The callback event for the {@link #AddRow()} block, called once the
   * values on the table have been updated. Additionally, this returns the
   * row number for the new row.
   */
  @SimpleEvent(
    description="The callback event for the AddRow block, called once the " +
      "values on the table have been updated.")
  public void FinishedAddRow (final int rowNumber) {
    EventDispatcher.dispatchEvent(this, "FinishedAddRow", rowNumber);
  }

  /**
   * Deletes the row with the given row number (1-indexed) from the table. This
   * does not clear the row, but removes it entirely. The sheet's grid id can be
   * found at the end of the url of the Google Sheets document, right after the
   * "gid=". Once complete, it triggers the {@link #FinishedRemoveRow()}
   * callback event.
   */
  @SimpleFunction(
    description="Deletes the row with the given row number from the table." +
      "This does not clear the row, but removes it entirely.")
  public void RemoveRow (final String sheetName, final int rowNumber) {
    AsynchUtil.runAsynchronously(new Runnable() {
      @Override
      public void run () {
        try{
          Sheets sheetsService = getSheetsService();
          int gridId = getSheetID(sheetsService, sheetName);

          if (gridId == -1) {
            ErrorOccurred("RemoveCol: sheetName not found");
            return;
          }

          DeleteDimensionRequest deleteRequest = new DeleteDimensionRequest()
            .setRange(
              new DimensionRange()
                .setSheetId(gridId)
                .setDimension("ROWS")
                .setStartIndex(rowNumber-1)
                .setEndIndex(rowNumber)
            );
          List<Request> requests = new ArrayList<>();
          requests.add(new Request().setDeleteDimension(deleteRequest));
          BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest()
            .setRequests(requests);
          sheetsService.spreadsheets().batchUpdate(spreadsheetID, body).execute();

          // Run the callback event block
          activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              FinishedRemoveRow();
            }
          });

        }
        catch (Exception e) {
          e.printStackTrace();
          ErrorOccurred("RemoveRow: " + e.getMessage());
        }
      }
    });
  }

  /**
   * The callback event for the {@link #RemoveRow()} block, called once the
   * values on the table have been updated.
   */
  @SimpleEvent(
    description="The callback event for the RemoveRow block, called once the" +
      "values on the table have been updated.")
  public void FinishedRemoveRow () {
    EventDispatcher.dispatchEvent(this, "FinishedRemoveRow");
  }

  /* Column-wise Operations */

  /**
   * On the page with the provided sheetName, reads the column at the given
   * colNumber and triggers the {@link #GotColData()} callback event.
   */
  @SimpleFunction(
    description="On the page with the provided sheetName, reads the column at " +
      "the given colNumber and triggers the GotColData callback event.")
  public void ReadCol (String sheetName, int colNumber) {

    if (spreadsheetID == "" || spreadsheetID == null) {
      ErrorOccurred("ReadCol: " + "SpreadsheetID is empty.");
      return;
    }
    // If there is no credentials file,
    //   Make a simple HTTP Request
    // Otherwise (there is a credentials file),
    //   Use the Google Sheets API

    // Converts the col number to the corresponding letter
    String colReference = getColString(colNumber);
    final String rangeRef = sheetName + "!" + colReference + ":" + colReference;

    // Asynchronously fetch the data in the cell and trigger the callback
    AsynchUtil.runAsynchronously(new Runnable() {
      @Override
      public void run () {
        try {
          // If no Credentials.json is provided, attempt the HTTP request
          if (credentialsPath == null) {
            // Cleans the formatted url in case the sheetname needs to be cleaned
            String cleanRangeReference = "";
            try {
              cleanRangeReference = URLEncoder.encode(rangeRef, "UTF-8");
            } catch (UnsupportedEncodingException e) {
              ErrorOccurred("ReadCol: Error occurred encoding the query. UTF-8 is unsupported?");
              return;
            }

            // Formats the data into the URL to read the range
            String getUrl = String.format(
              "https://docs.google.com/spreadsheets/d/%s/export?format=csv&range=%s",
              spreadsheetID, cleanRangeReference);
            Log.d(LOG_TAG, "ReadCol url: " + getUrl);

            // Make the HTTP Request
            URL url = new URL(getUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
              connection.setRequestMethod("GET");
            // Catch Bad HTTP Request
            if (connection.getResponseCode() == 400) {
              ErrorOccurred("ReadCol: Bad HTTP Request. Please check the address and try again. " + getUrl);
              return;
            }

            // Parse the Response
            String responseContent = getResponseContent(connection);
            YailList parsedCsv = CsvUtil.fromCsvTable(responseContent);
            final List<String> col = new ArrayList<String>();
            for (Object elem : (LList) parsedCsv.getCdr()) {
              if (!(elem instanceof YailList))
                continue;
              final YailList row = (YailList) elem;

              col.add(String.format("%s", row.isEmpty() ? "" : row.get(1)));
            }
            // We need to re-enter the main thread before we can dispatch the event!
            activity.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                GotColData(col);
              }
            });
            return;
          }

          // Run this if there is a credentials json provided.
          Sheets sheetsService = getSheetsService();

          ValueRange readResult = sheetsService.spreadsheets().values()
            .get(spreadsheetID, rangeRef).execute();
          List<List<Object>> values = readResult.getValues();

          // If the data we got is empty, then throw an error
          if (values == null || values.isEmpty()) {
            ErrorOccurred("ReadCol: No data found.");
            return;
          }

          // Format the result as a list of strings and run the callback
          final List<String> ret = new ArrayList<String>();
          for (List<Object> row : values) {
            ret.add(String.format("%s", row.isEmpty() ? "" : row.get(0), ""));
          }

          // We need to re-enter the main thread before we can dispatch the event!
          activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              GotColData(ret);
            }
          });
        }
        catch (Exception e) {
          e.printStackTrace();
          ErrorOccurred("ReadCol: " + e.getMessage());
        }
      }
    });
  }

  /**
   * The callback event for the {@link #ReadCol()} block. The `colDataList` is a
   * list of text cell-values in order of increasing row number.
   */
  @SimpleEvent(
    description="After calling the ReadCol method, the data in the column will " +
      "be stored as a list of text values in `colDataList`.")
  public void GotColData (final List<String> colDataList) {
    Log.d(LOG_TAG, "GotColData got: " + colDataList);
    EventDispatcher.dispatchEvent(this, "GotColData", colDataList);
  }

  /**
   * Given a list of values as `data`, writes the values to the column with the
   * given column number, overriding existing values from top down. (Note: It
   * will not erase the entire column.) Once complete, it triggers the
   * {@link #FinishedWriteCol()} callback event.
   */
  @SimpleFunction(
    description="Given a list of values as `data`, this method will write the " +
      "values to the column of the sheet and calls the FinishedWriteCol event " +
      "once complete.")
  public void WriteCol (String sheetName, int colNumber, YailList data) {
    if (spreadsheetID == "" || spreadsheetID == null) {
      ErrorOccurred("WriteCol: " + "SpreadsheetID is empty.");
      return;
    } else if (credentialsPath == "" || credentialsPath == null) {
      ErrorOccurred("WriteCol: " + "Credentials JSON is required.");
      return;
    }

    // Converts the col number to the corresponding letter
    String colReference = getColString(colNumber);
    final String rangeRef = sheetName + "!" + colReference + ":" + colReference;

    // Generates the body, which are the values to assign to the range
    List<List<Object>> values = new ArrayList<>();
    for (Object o : (LList) data.getCdr()) {
      List<Object> r = new ArrayList<Object>(Arrays.asList(o));
      values.add(r);
    }

    // Sets the 2D list above to be the values in the body of the API Call
    final ValueRange body = new ValueRange()
      .setValues(values);

    // Wrap the API Call in an Async Utility
    AsynchUtil.runAsynchronously(new Runnable() {
      @Override
      public void run () {
        // Surround the operation with a try catch statement
        try {
          Sheets sheetsService = getSheetsService();
          // UpdateValuesResponse result =
          sheetsService.spreadsheets().values()
            .update(spreadsheetID, rangeRef, body)
            .setValueInputOption("USER_ENTERED")
            .execute();
          // Run the callback function
          activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              FinishedWriteCol();
            }
          });
        }
        catch (IOException e) {
          e.printStackTrace();
          ErrorOccurred("WriteCol IOException: " + e.getMessage());
        }
        catch (GeneralSecurityException e) {
          e.printStackTrace();
          ErrorOccurred("WriteCol GeneralSecurityException: " + e.getMessage());
        }
      }
    });
  }

  /**
   * The callback event for the {@link #WriteCol()} block, called once the
   * values on the table have been updated.
   */
  @SimpleEvent(
    description="The callback event for the WriteCol block, called once the" +
      "values on the table have been updated.")
  public void FinishedWriteCol () {
    EventDispatcher.dispatchEvent(this, "FinishedWriteCol");
  }

  /**
   * Given a list of values as `data`, appends the values to the next empty
   * column of the sheet. It will always start from the top row and continue
   * downwards. Once complete, it triggers the {@link #FinishedAddCol()}
   * callback event.
   */
  @SimpleFunction(
    description="Given a list of values as `data`, appends the values to the " +
      "next empty column of the sheet.")
  public void AddCol (final String sheetName, YailList data) {
    if (spreadsheetID == "" || spreadsheetID == null) {
      ErrorOccurred("AddCol: " + "SpreadsheetID is empty.");
      return;
    } else if (credentialsPath == "" || credentialsPath == null) {
      ErrorOccurred("AddCol: " + "Credentials JSON is required.");
      return;
    }

    // Generates the body, which are the values to assign to the range
    List<List<Object>> values = new ArrayList<>();
    for (Object o : (LList) data.getCdr()) {
      List<Object> r = new ArrayList<Object>();
      r.add(o);
      values.add(r);
    }
    final ValueRange body = new ValueRange()
      .setValues(values);

    // Wrap the API Call in an Async Utility
    AsynchUtil.runAsynchronously(new Runnable() {
      @Override
      public void run () {
        // Surround the operation with a try catch statement
        try {
          Sheets sheetsService = getSheetsService();

          ValueRange readResult = sheetsService.spreadsheets().values()
            .get(spreadsheetID, sheetName).execute();
          // Get the actual data from the response
          List<List<Object>> values = readResult.getValues();
          // If the data we got is empty, then return so.
          if (values == null || values.isEmpty())
            ErrorOccurred("AddCol: No data found");

          // nextCol gets mutated, keep addedColumn as a constant
          int max_col = 0;
          for (List<Object> list : values){
            max_col = Math.max(max_col, list.size());
          }
          int nextCol = max_col + 1;
          final int addedColumn = nextCol;
          // Converts the col number to the corresponding letter
          String[] alphabet = {
            "A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R",
            "S","T","U","V","W","X","Y","Z"};
          String colReference = "";
          while (nextCol > 0) {
            String digit = alphabet[(nextCol-1) % 26];
            colReference = digit + colReference;
            nextCol = (int) Math.floor((nextCol-1) / 26);
          }
          String rangeRef = sheetName + "!" + colReference + "1";

          UpdateValuesResponse response = sheetsService.spreadsheets().values()
            .update(spreadsheetID, rangeRef, body)
            .setValueInputOption("USER_ENTERED")
            .execute();

          activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              FinishedAddCol(addedColumn);
            }
          });
        }
        catch (IOException e) {
          e.printStackTrace();
          ErrorOccurred("AddCol IOException: " + e.getMessage());
        }
        catch (GeneralSecurityException e) {
          e.printStackTrace();
          ErrorOccurred("AddCol GeneralSecurityException: " + e.getMessage());
        }
      }
    });
  }

  /**
   * The callback event for the {@link #AddCol()} block, called once the
   * values on the table have been updated. Additionally, this returns the
   * column number for the new column.
   */
  @SimpleEvent(
    description="This event will be triggered once the AddCol method has " +
      "finished executing and the values on the spreadsheet have been updated. " +
      "Additionally, this returns the column number for the new column.")
  public void FinishedAddCol (final int columnNumber) {
    EventDispatcher.dispatchEvent(this, "FinishedAddCol", columnNumber);
  }

  /**
   * Deletes the column with the given column number from the table. This does
   * not clear the column, but removes it entirely. The sheet's grid id can be
   * found at the end of the url of the Google Sheets document, right after the
   * "gid=". Once complete, it triggers the {@link #FinishedRemoveCol()}
   * callback event.
   */
  @SimpleFunction(
    description="Deletes the column with the given column number from the table." +
      "This does not clear the column, but removes it entirely.")
  public void RemoveCol (final String sheetName, final int colNumber) {

    AsynchUtil.runAsynchronously(new Runnable() {
      @Override
      public void run () {
        try{
          Sheets sheetsService = getSheetsService();
          int gridId = getSheetID(sheetsService, sheetName);
          if (gridId == -1) {
            ErrorOccurred("RemoveCol: sheetName not found");
            return;
          }

          DeleteDimensionRequest deleteRequest = new DeleteDimensionRequest()
            .setRange(
              new DimensionRange()
                .setSheetId(gridId)
                .setDimension("COLUMNS")
                .setStartIndex(colNumber-1)
                .setEndIndex(colNumber)
            );
          List<Request> requests = new ArrayList<>();
          requests.add(new Request().setDeleteDimension(deleteRequest));

          BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest()
            .setRequests(requests);
          sheetsService.spreadsheets().batchUpdate(spreadsheetID, body).execute();
          // Run the callback event
          activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              FinishedRemoveCol();
            }
          });
        }
        catch (Exception e) {
          e.printStackTrace();
          ErrorOccurred("RemoveCol: " + e.getMessage());
        }
      }
    });
  }

  /**
   * The callback event for the {@link #RemoveCol()} block, called once the
   * values on the table have been updated.
   */
  @SimpleEvent(
    description="The callback event for the RemoveCol block, called once the " +
      "values on the table have been updated.")
  public void FinishedRemoveCol () {
    EventDispatcher.dispatchEvent(this, "FinishedRemoveCol");
  }

  /* Cell-wise Operations */

  /**
   * On the page with the provided sheetName, reads the cell at the given
   * cellReference and triggers the {@link #GotCellData()} callback event. The
   * cellReference can be either a text block with A1-Notation, or the result of
   * the {@link #getCellReference()} block.
   */
  @SimpleFunction(
    description="On the page with the provided sheetName, reads the cell at " +
      "the given cellReference and triggers the GotCellData callback event.")
  public void ReadCell (final String sheetName, final String cellReference) {
    if (spreadsheetID == "" || spreadsheetID == null) {
      ErrorOccurred("ReadCell: " + "SpreadsheetID is empty.");
      return;
    }
    // If there is no credentials file,
    //   Make a simple HTTP Request
    // Otherwise (there is a credentials file),
    //   Use the Google Sheets API

    // 1. Check that the Cell Reference is actually a single cell
    if (!cellReference.matches("[a-zA-Z]+[0-9]+")) {
      ErrorOccurred("ReadCell: Invalid Cell Reference");
      return;
    }

    // 2. Asynchronously fetch the data in the cell
    AsynchUtil.runAsynchronously(new Runnable() {
      @Override
      public void run () {
        Log.d(LOG_TAG, "Reading Cell: " + cellReference);

        try {
          // If no Credentials.json is provided, attempt the HTTP request
          if (credentialsPath == null) {
            // Cleans the formatted url in case the sheetname needs to be cleaned
            String cleanRangeReference = "";
            try {
              cleanRangeReference = URLEncoder.encode(cellReference, "UTF-8");
            } catch (UnsupportedEncodingException e) {
              ErrorOccurred("ReadCell: Error occurred encoding the query. UTF-8 is unsupported?");
              return;
            }

            // Formats the data into the URL to read the range
            String getUrl = String.format(
              "https://docs.google.com/spreadsheets/d/%s/export?format=csv&range=%s",
              spreadsheetID, cleanRangeReference);
            Log.d(LOG_TAG, "ReadCell url: " + getUrl);

            // Make the HTTP Request
            URL url = new URL(getUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
              connection.setRequestMethod("GET");
            // Catch Bad HTTP Request
            if (connection.getResponseCode() == 400) {
              ErrorOccurred("ReadCell: Bad HTTP Request. Please check the address and try again. " + getUrl);
              return;
            }

            // Parse the Response
            String responseContent = getResponseContent(connection);
            YailList parsedCsv = CsvUtil.fromCsvTable(responseContent);

            for (Object elem : (LList) parsedCsv.getCdr()) {
              if (!(elem instanceof YailList))
                continue;
              YailList row = (YailList) elem;
              final String cellData = String.format("%s", row.isEmpty() ? "" : row.get(1));
              // We need to re-enter the main thread before we can dispatch the event!
              activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  GotCellData(cellData);
                }
              });
              return;
            }

            ErrorOccurred("ReadCell: Error reading cell data from HTTP Request");
            return;
          }

          // Run this if there is a credentials json provided.
          Sheets sheetsService = getSheetsService();
          ValueRange readResult = sheetsService.spreadsheets().values()
            .get(spreadsheetID, sheetName + "!" + cellReference).execute();
          List<List<Object>> values = readResult.getValues();

          // If the data we got is empty, then return so.
          if (values == null || values.isEmpty()) {
            activity.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                GotCellData("");
              }
            });
            return;
          }

          // Format the result as a string and run the call back
          final String result = String.format("%s", values.get(0)
              .isEmpty() ? "" : values.get(0).get(0));
          activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              GotCellData(result);
            }
          });
        }
        // Handle Errors which may have occured while sending the Read Request
        catch (Exception e) {
          e.printStackTrace();
          ErrorOccurred("ReadCell: " + e.getMessage());
        }
      }
    });
  }

  /**
   * The callback event for the {@link #ReadCell()} block. The `cellData` is
   * the text value in the cell.
   */
  @SimpleEvent(
    description="The callback event for the ReadCell block. The `cellData` " +
      "is the text value in the cell (and not the underlying formula).")
  public void GotCellData(final String cellData) {
    Log.d(LOG_TAG, "GotCellData got: " + cellData);
    EventDispatcher.dispatchEvent(this, "GotCellData", cellData);
  }

  /**
   * Given text or a number as `data`, writes the value to the cell. It will
   * override any existing data in the cell with the one provided. Once complete,
   * it triggers the {@link #FinishedWriteCell()} callback event.
   */
  @SimpleFunction(
    description="Given text or a number as `data`, writes the value into the " +
      "cell. Once complete, it triggers the FinishedWriteCell callback event")
  public void WriteCell (String sheetName, String cellReference, Object data) {
    if (spreadsheetID == "") {
      ErrorOccurred("WriteCell: " + "SpreadsheetID is empty.");
      return;
    } else if (credentialsPath == null) {
      ErrorOccurred("WriteCell: " + "Credentials JSON is required.");
      return;
    }

    // Generates the A1 Reference for the operation
    final String rangeRef = sheetName + "!" + cellReference;
    // Form the body as a 2D list of Strings, with only one string
    final ValueRange body = new ValueRange()
      .setValues(Arrays.asList(
        Arrays.asList(data)
      ));
    Log.d(LOG_TAG, "Writing Cell: " + rangeRef);

    // Wrap the API Call in an Async Utility
    AsynchUtil.runAsynchronously(new Runnable() {
      @Override
      public void run () {
        // Running the getSheetsService and executing the command may cause
        // IOException's and GeneralSecurityException's
        try {
          Sheets sheetsService = getSheetsService();

          // You can use the UpdateValuesResponse to find more data on the
          // result of the Write method.

          // UpdateValuesResponse result =
          sheetsService.spreadsheets().values()
            .update(spreadsheetID, rangeRef, body)
            .setValueInputOption("USER_ENTERED") // USER_ENTERED or RAW
            .execute();
          // Trigger the Callback
          activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              FinishedWriteCell();
            }
          });
        }
        catch (Exception e) {
          e.printStackTrace();
          ErrorOccurred("WriteCell: " + e.getMessage());
        }
      }
    });
  }

  /**
   * The callback event for the {@link #WriteCell()} block, called once the
   * values on the table have been updated.
   */
  @SimpleEvent(
    description="The callback event for the WriteCell block.")
  public void FinishedWriteCell () {
    EventDispatcher.dispatchEvent(this, "FinishedWriteCell");
  }

  /* Range-wise Operations */

  /**
   * On the page with the provided sheetName, reads the cells at the given
   * rangeReference and triggers the {@link #GotRangeData()} callback event. The
   * rangeReference can be either a text block with A1-Notation, or the result
   * of the {@link #getRangeReference()} block.
   */
  @SimpleFunction(
    description="On the page with the provided sheetName, reads the cells at " +
      "the given range. Triggers the getRangeReference once complete.")
  public void ReadRange (final String sheetName, final String rangeReference) {
    if (spreadsheetID == "" || spreadsheetID == null) {
      ErrorOccurred("ReadRange: " + "SpreadsheetID is empty.");
      return;
    }

    // If there is no credentials file,
    //   Make a simple HTTP Request
    // Otherwise (there is a credentials file),
    //   Use the Google Sheets API

    // Asynchronously fetch the data in the cell
    AsynchUtil.runAsynchronously(new Runnable() {
      @Override
      public void run () {
        Log.d(LOG_TAG, "Reading Range: " + rangeReference);

        try {
          // If no Credentials.json is provided, attempt the HTTP request
          if (credentialsPath == null) {
            // Cleans the formatted url in case the sheetname needs to be cleaned
            String cleanRangeReference = "";
            try {
              cleanRangeReference = URLEncoder.encode(rangeReference, "UTF-8");
            } catch (UnsupportedEncodingException e) {
              ErrorOccurred("ReadRange: Error occurred encoding the query. UTF-8 is unsupported?");
              return;
            }

            // Formats the data into the URL to read the range
            String getUrl = String.format(
              "https://docs.google.com/spreadsheets/d/%s/export?format=csv&range=%s",
              spreadsheetID, cleanRangeReference);
            Log.d(LOG_TAG, "ReadRange url: " + getUrl);

            // Make the HTTP Request
            URL url = new URL(getUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
              connection.setRequestMethod("GET");
            // Catch Bad HTTP Request
            if (connection.getResponseCode() == 400) {
              ErrorOccurred("ReadRange: Bad HTTP Request. Please check the address and try again. " + getUrl);
              return;
            }

            // Parse the Response
            String responseContent = getResponseContent(connection);
            final YailList parsedCsv = CsvUtil.fromCsvTable(responseContent);
            // We need to re-enter the main thread before we can dispatch the event!
            activity.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                GotRangeData(parsedCsv);
              }
            });
            return;
          }

          // Run this if there is a credentials json provided.
          Sheets sheetsService = getSheetsService();
          ValueRange readResult = sheetsService.spreadsheets().values()
            .get(spreadsheetID, sheetName + "!" + rangeReference).execute();
          // Get the actual data from the response
          List<List<Object>> values = readResult.getValues();

          // No Data Found
          if (values == null || values.isEmpty()) {
            ErrorOccurred("ReadRange: No data found.");
            return;
          }
          // Format the result as a string and run the call back
          final List<List<String>> ret = new ArrayList<List<String>>();
          // For every object in the result, convert it to a string
          for (List<Object> row : values) {
            List<String> cellRow = new ArrayList<String>();
            for (Object cellValue : row) {
              cellRow.add(String.format("%s", cellValue == null ? "" : cellValue));
            }
            ret.add(cellRow);
          }

          // Run the callback event
          activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              GotRangeData(ret);
            }
          });
        }
        // Handle Errors which may have occured while sending the Read Request!
        catch (Exception e) {
          e.printStackTrace();
          ErrorOccurred("ReadRange: " + e.getMessage());
        }
      }
    });
  }

  /**
   * The callback event for the {@link #ReadRange()} block. The `rangeData` is
   * a list of rows, where the dimensions are the same as the rangeReference.
   */
  @SimpleEvent(
    description="The callback event for the ReadRange block. The `rangeData` " +
      "is a list of rows with the requested dimensions.")
  public void GotRangeData (List<List<String>> rangeData) {
    Log.d(LOG_TAG, "GotRangeData got: " + rangeData);
    EventDispatcher.dispatchEvent(this, "GotRangeData", rangeData);
  }

  /**
   * Given list of lists as `data`, writes the values to cells in the range. The
   * number of rows and columns in the range must match the dimensions of your
   * data. This method will override existing data in the range. Once complete,
   * it triggers the {@link #FinishedWriteRange()} callback event.
   */
  @SimpleFunction(
    description="Given list of lists as `data`, writes the values into the " +
      "range. The number of rows and columns in the range reference must " +
      "match the dimensions of the data.")
  public void WriteRange (String sheetName, String rangeReference, YailList data) {
    if (spreadsheetID == "" || spreadsheetID == null) {
      ErrorOccurred("WriteRange: " + "SpreadsheetID is empty.");
      return;
    } else if (credentialsPath == null) {
      ErrorOccurred("WriteRange: " + "Credentials JSON is required.");
      return;
    }
    // (TODO) Check that the range reference is in A1 notation

    // Generates the A1 Reference for the operation
    final String rangeRef = sheetName + "!" + rangeReference;
    Log.d(LOG_TAG, "Writing Range: " + rangeRef);

    // Generates the body, which are the values to assign to the range
    List<List<Object>> values = new ArrayList<>();
    int cols = -1;
    for (Object elem : (LList) data.getCdr()) {
      if (!(elem instanceof YailList))
        continue;
      YailList row = (YailList) elem;
      // construct the row that we will add to the list of rows
      List<Object> r = new ArrayList<Object>();
      for (Object o : (LList) row.getCdr())
        r.add(o);
      values.add(r);
      // Catch rows of unequal length
      if (cols == -1) cols = r.size();
      if (r.size() != cols) {
        ErrorOccurred("WriteRange: Rows must have the same length");
        return;
      }
    }

    // Check that values has at least 1 row
    if (values.size() == 0) {
      ErrorOccurred("WriteRange: Data must be a list of lists.");
      return;
    }

    final ValueRange body = new ValueRange()
      .setValues(values);
    Log.d(LOG_TAG, "Body's Range in A1: " + body.getRange());
    // Wrap the API Call in an Async Utility
    AsynchUtil.runAsynchronously(new Runnable() {
      @Override
      public void run () {
        try {
          Sheets sheetsService = getSheetsService();
          // UpdateValuesResponse result =
          sheetsService.spreadsheets().values()
            .update(spreadsheetID, rangeRef, body)
            .setValueInputOption("USER_ENTERED") // USER_ENTERED or RAW
            .execute();
          activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              FinishedWriteRange();
            }
          });
        }
        catch (Exception e) {
          e.printStackTrace();
          ErrorOccurred("WriteRange: " + e.getMessage());
        }
      }
    });
  }

  /**
   * The callback event for the {@link #WriteRange()} block, called once the
   * values on the table have been updated.
   */
  @SimpleEvent(
    description="The callback event for the WriteRange block.")
  public void FinishedWriteRange () {
    EventDispatcher.dispatchEvent(this, "FinishedWriteRange");
  }

  /**
   * Empties the cells in the given range. Once complete, this block triggers
   * the {@link #FinishedClearRange()} callback event.
   */
  @SimpleFunction(
    description="Empties the cells in the given range. Once complete, this " +
      "block triggers the FinishedClearRange callback event.")
  public void ClearRange (String sheetName, String rangeReference) {
    if (spreadsheetID == "" || spreadsheetID == null) {
      ErrorOccurred("ClearRange: " + "SpreadsheetID is empty.");
      return;
    } else if (credentialsPath == null) {
      ErrorOccurred("ClearRange: " + "Credential JSON is required.");
      return;
    }

    final String rangeRef = sheetName + "!" + rangeReference;
    Log.d(LOG_TAG, "Clearing Range: " + rangeRef);

    // Runs the Clear call asynchronously
    AsynchUtil.runAsynchronously(new Runnable() {
      @Override
      public void run () {
        try {
          Sheets sheetsService = getSheetsService();
          ClearValuesResponse response =
          sheetsService.spreadsheets().values()
            .clear(spreadsheetID, rangeRef, new ClearValuesRequest())
            .execute();
        }
        catch (Exception e) {
          e.printStackTrace();
          ErrorOccurred("ClearRange: " + e.getMessage());
        }
      }
    });
  }

  /**
   * The callback event for the {@link #ClearRange()} block, called once the
   * values on the table have been updated.
   */
  @SimpleEvent(
    description="The callback event for the ClearRange block.")
  public void FinishedClearRange () {
    EventDispatcher.dispatchEvent(this, "FinishedClearRange");
  }

  /* Sheet-wise Operations */

  /**
   * Reads the <b>entire</b> Google Sheets document and triggers the
   * {@link #GotSheetData()} callback event.
   */
  @SimpleFunction(
    description="Reads the *entire* Google Sheet document and triggers the " +
      "GotSheetData callback event.")
  public void ReadSheet (final String sheetName) {

    if (spreadsheetID == "" || spreadsheetID == null) {
      ErrorOccurred("ReadSheet: " + "SpreadsheetID is empty.");
      return;
    }
    AsynchUtil.runAsynchronously(RetrieveSheet(sheetName, -1, null, false, true));
  }

  Runnable RetrieveSheet(final String sheetName, final int colID, final String value,
      final boolean exact, final boolean fireEvent) {
    return new Runnable() {
      @Override
      public void run() {
        if (spreadsheetID == "" || spreadsheetID == null) {
          ErrorOccurred("ReadSheet: " + "SpreadsheetID is empty.");
          return;
        }
        Log.d(LOG_TAG, "Reading Sheet: " + sheetName);

        try {
          // If no Credentials.json is provided, attempt the HTTP request
          if (credentialsPath == null)
          {
            Log.d(LOG_TAG, "Reading Sheet: No credentials");
            // Cleans the formatted url in case the sheetname needs to be cleaned
            String cleanRangeReference = "";
            try {
              cleanRangeReference = URLEncoder.encode(sheetName + "!", "UTF-8");
            } catch (UnsupportedEncodingException e) {
              ErrorOccurred("ReadRange: Error occurred encoding the query. UTF-8 is unsupported?");
              return;
            }

            // Formats the data into the URL to read the range
            String getUrl = String.format(
                "https://docs.google.com/spreadsheets/d/%s/export?format=csv&sheet=%s",
                spreadsheetID, cleanRangeReference);

            // Make the HTTP Request
            URL url = new URL(getUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            // Catch Bad HTTP Request
            if (connection.getResponseCode() == 400) {
              ErrorOccurred("ReadSheet: Bad HTTP Request. Please check the address and try again. " + getUrl);
              return;
            }

            // Parse the Response
            final String responseContent = getResponseContent(connection);
            final YailList parsedCsv = CsvUtil.fromCsvTable(responseContent);
            // We need to re-enter the main thread before we can dispatch the event!
            activity.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                if (fireEvent) {
                  if (colID >= 0) {
                    try {
                      List<Integer> return_rows = new YailList();
                      List<List<String>> return_data = new YailList();
                      int rowNum = 0;
                      while (rowNum < parsedCsv.size()) {
                        YailList sheet_row = CsvUtil.fromCsvRow(parsedCsv.get(rowNum).toString());
                        if (sheet_row.size() >= colID) {
                          if ((exact && sheet_row.get(colID - 1).equals(value))
                              || (!exact && sheet_row.get(colID - 1).toString().contains(value))) {
                            return_rows.add(rowNum);
                            return_data.add(sheet_row);
                          }
                          rowNum++;
                        }
                      }
                      GotFilterResult(return_rows, return_data);
                    } catch (Exception e) {
                      Log.d(LOG_TAG, "ReadWithFilter (no creds) Error: " + e.getMessage());
                      ErrorOccurred(e.getMessage());
                    }
                  } else {
                    GotSheetData(parsedCsv);
                  }
                }

                updateColumns(parsedCsv);

                notifyDataObservers(null, null);
              }
            });
            return;
          }
          Log.d(LOG_TAG, "Reading Sheet: Credentials located.");

          // Run this if there is a credentials json provided.
          Sheets sheetsService = getSheetsService();
          Log.d(LOG_TAG, "Reading Sheet: Got sheet service");
          // Spreadsheet sheet = sheetsService.spreadsheets().get(spreadsheetID).execute();
          ValueRange readResult = sheetsService.spreadsheets().values()
              .get(spreadsheetID, sheetName).execute();
          Log.d(LOG_TAG, "Got read result");
          // Get the actual data from the response
          List<List<Object>> values = readResult.getValues();
          Log.d(LOG_TAG, "Reading Sheet: values count " + values.size());

          // No Data Found
          if (values == null || values.isEmpty()) {
            ErrorOccurred("ReadSheet: No data found.");
            return;
          }
          // Format the result as a string and run the call back
          final List<List<String>> ret = new ArrayList<List<String>>();
          // For every object in the result, convert it to a string
          Log.d(LOG_TAG, "RetriveSheet data: " + values);

          for (List<Object> row : values) {
            List<String> cellRow = new ArrayList<String>();
            for (Object cellValue : row) {
              cellRow.add(String.format("%s", cellValue == null ? "" : cellValue));
            }
            ret.add(cellRow);
          }
          Log.d(LOG_TAG, "RetriveSheet return rowcount: " + ret.size());

          // We need to re-enter the main thread before we can dispatch the event!
          activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              Log.d(LOG_TAG, "RetriveSheet UIThread " );
              if (colID >= 0) {
                Log.d(LOG_TAG, "RetriveWithFilter: colID " + colID);
                try
                {
                List<Integer> return_rows = new ArrayList<>();
                List<List<String>> return_data = new ArrayList<>();
                int rowNum = 0;
                while (rowNum < ret.size()) {
                  Log.d(LOG_TAG, "Reading row row: " + rowNum);
                  List<String> sheet_row = ret.get(rowNum);
                  Log.d(LOG_TAG, "Read with Filter row: " + sheet_row);
                  if (sheet_row.size() >=(colID))
                  {
                    Log.d(LOG_TAG, "Checking field : |" + sheet_row.get(colID - 1) + "|");
                    if (exact && sheet_row.get(colID - 1).equals(value)
                    || (!exact && sheet_row.get(colID - 1).contains(value)))
                    {
                      Log.d(LOG_TAG, "Read with Filter check col: " + rowNum);
                      return_rows.add(rowNum);
                      return_data.add(sheet_row);
                    }
                  }
                  rowNum++;
                }
                GotFilterResult(return_rows, return_data);
                } catch (Exception e)
                {
                  Log.d(LOG_TAG, "Read with Filter Error: " + e.getClass().getName() + e.getMessage());
                  ErrorOccurred(e.getMessage());
                  return;
                }
              } else {
                GotSheetData(ret);
              }

              updateColumns(YailList.makeList(ret));
              notifyDataObservers(null, null);
            }
          });
        }
        // Handle Errors which may have occured while sending the Read Request!
        catch (Exception e) {
          e.printStackTrace();
          ErrorOccurred("RetrieveSheet Error: " + e.getMessage());
        }
      }
    };
  }

  /**
   * The callback event for the {@link #ReadSheet()} block. The `sheetData` is a
   * list of rows.
   */
  @SimpleEvent(
    description="The callback event for the ReadSheet block. The `sheetData` " +
      "is a list of rows.")
  public void GotSheetData (final List<List<String>> sheetData) {
    Log.d(LOG_TAG, "GotSheetData got: " + sheetData);
    EventDispatcher.dispatchEvent(this, "GotSheetData", sheetData);
  }

  //region ObservableDataSource Implementation

  @Override
  public Future<YailList> getDataValue(final YailList key) {
    return getDataValue(key, false);
  }

  public Future<YailList> getDataValue(final YailList key, final boolean useHeaders) {
    final FutureTask<Void> currentTask = lastTask;

    FutureTask<YailList> getDataValueTask = new FutureTask<>(
        new Callable<YailList>() {
          @Override
          public YailList call() throws Exception {
            if (currentTask != null && !currentTask.isDone() && !currentTask.isCancelled()) {
              try {
                currentTask.get();
              } catch (Exception e) {
                e.printStackTrace();
              }
            }

            return getColumns(key, useHeaders);
          }
        });

    AsynchUtil.runAsynchronously(getDataValueTask);
    return getDataValueTask;
  }

  @Override
  public void addDataObserver(DataSourceChangeListener dataComponent) {
    observers.add(dataComponent);
  }

  @Override
  public void removeDataObserver(DataSourceChangeListener dataComponent) {
    observers.remove(dataComponent);
  }

  @Override
  public void notifyDataObservers(YailList key, Object newValue) {
    for (DataSourceChangeListener dataComponent : observers) {
      dataComponent.onDataSourceValueChange(this, null, columns);
    }
  }

  //endregion

  private void updateColumns(final YailList parsedCsv) {
    try {
      columns = ChartDataSourceUtil.getTranspose(parsedCsv);
    } catch (Exception e) {
      columns = new YailList();
    }
  }

  private YailList getColumn(String column) {
    YailList result = new YailList();

    for (int i = 0; i < columns.size(); i++) {
      YailList list = (YailList) columns.getObject(i);

      if (!list.isEmpty() && list.getString(0).equals(column)) {
        result = list;
        break;
      }
    }

    return result;
  }

  private YailList getColumn(int column) {
    YailList result = new YailList();

    if (column < columns.size()) {
      result = (YailList) columns.getObject(column);
    }

    return result;
  }

  YailList getColumns(YailList keyColumns, boolean useHeaders) {
    List<YailList> resultingColumns = new ArrayList<>();
    for (int i = 0; i < keyColumns.size(); i++) {
      String columnName = keyColumns.getString(i);
      YailList column;
      if (useHeaders) {
        column = getColumn(columnName);
      } else {
        int colIndex = 0;
        for (char c : columnName.toCharArray()) {
          colIndex *= 26;
          colIndex += c - 'A';
        }
        column = getColumn(colIndex);
      }
      resultingColumns.add(column);
    }

    return YailList.makeList(resultingColumns);
  }
}
