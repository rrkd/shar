package au.com.iglooit.shar.model.vo;

/**
 * Created with IntelliJ IDEA.
 * User: nicholas.zhu
 * Date: 19/09/2014
 * Time: 6:25 AM
 */
public class DriverFileResponse extends JsonResponse {
    private String fileUrl;

    public DriverFileResponse() {
        this("OK", "");
    }

    public DriverFileResponse(String status, String errorMessage) {
        super(status, errorMessage);
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }
}
