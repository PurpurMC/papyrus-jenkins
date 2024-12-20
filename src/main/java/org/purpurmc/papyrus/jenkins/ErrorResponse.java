package org.purpurmc.papyrus.jenkins;

public final record ErrorResponse(String error) {
    public static final ErrorResponse BUILD_ALREADY_EXISTS = create("build already exists");
    public static final ErrorResponse BUILD_NOT_FOUND = create("build not found");
    public static final ErrorResponse FILE_DOWNLOAD_ERROR = create("couldn't access file");
    public static final ErrorResponse FILE_UPLOAD_ERROR = create("couldn't upload file");
    public static final ErrorResponse INVALID_AUTH_TOKEN = create("invalid auth token");
    public static final ErrorResponse INVALID_STATE_KEY = create("invalid state key");
    public static final ErrorResponse PROJECT_NOT_FOUND = create("project not found");
    public static final ErrorResponse VERSION_NOT_FOUND = create("version not found");
    public static final ErrorResponse NO_HANDLER_FOUND_EXCEPTION = create("endpoint not found");

    public static ErrorResponse create(String error) {
        return new ErrorResponse(error);
    }
}
