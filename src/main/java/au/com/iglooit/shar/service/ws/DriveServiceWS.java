package au.com.iglooit.shar.service.ws;

import au.com.iglooit.shar.drive.CredentialManager;
import au.com.iglooit.shar.exception.AppX;
import au.com.iglooit.shar.model.vo.ClientFile;
import au.com.iglooit.shar.model.vo.DriverFileResponse;
import au.com.iglooit.shar.model.vo.JsonResponse;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Userinfo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

/**
 * Created with IntelliJ IDEA.
 * User: nicholas.zhu
 * Date: 19/09/2014
 * Time: 2:26 PM
 */

public class DriveServiceWS {
    /**
     * Default transportation layer for Google Apis Java client.
     */
    protected static final HttpTransport TRANSPORT = new NetHttpTransport();

    /**
     * Default JSON factory for Google Apis Java client.
     */
    protected static final JsonFactory JSON_FACTORY = new JacksonFactory();

    /**
     * Key to get/set userId from and to the session.
     */
    public static final String KEY_SESSION_USERID = "user_id";

    /**
     * Default MIME type of files created or handled by DrEdit.
     * This is also set in the Google APIs Console under the Drive SDK tab.
     */
    public static final String DEFAULT_MIMETYPE = "text/plain";

    /**
     * Path component under war/ to locate client_secrets.json file.
     */
    public static final String CLIENT_SECRETS_FILE_PATH
            = "/WEB-INF/client_secrets_shar.json";

    /**
     * A credential manager to get, set, delete credential objects.
     */
    private CredentialManager credentialManager = null;

    @PostConstruct
    public void init() throws Exception {
        // init credential manager
        credentialManager = new CredentialManager(
                getClientSecrets(), TRANSPORT, JSON_FACTORY);
    }

    @RequestMapping(value = "/drive/v",
            method = RequestMethod.GET)
    public
    @ResponseBody
    JsonResponse version(HttpServletRequest req) {
        Drive service = getDriveService(getCredential(req));
        try {
            About about = service.about().get().execute();
            return new JsonResponse("OK",about.toPrettyString());
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 401) {
                // The user has revoked our token or it is otherwise bad.
                // Delete the local copy so that their next page load will recover.
//                deleteCredential(req, resp);
//                sendGoogleJsonResponseError(resp, e);
            }
            throw new AppX("Version Error", e);
        } catch (IOException e) {
            throw new AppX("Version Error", e);
        }
    }

    @RequestMapping(value = "/drive/f",
            method = RequestMethod.POST,
            headers = {"Content-type=application/json"})
    public
    @ResponseBody
    DriverFileResponse uploadFile(@RequestBody ClientFile clientFile, HttpServletRequest req) {
        Drive service = getDriveService(getCredential(req));
        File file = clientFile.toFile();
        try {
            if (!clientFile.content.equals("")) {

                file = service.files().insert(file,
                        ByteArrayContent.fromString(clientFile.mimeType, clientFile.content))
                        .execute();

            } else {
                file = service.files().insert(file).execute();
            }
        } catch (IOException e) {
            throw new AppX("Can't upload file to driver", e);
        }
        DriverFileResponse resp = new DriverFileResponse();
        resp.setFileUrl(file.getDownloadUrl());
        return resp;
    }

    /**
     * Redirects to OAuth2 consent page if user is not logged in.
     * @param req   Request object.
     * @param resp  Response object.
     */
    protected void loginIfRequired(HttpServletRequest req,
                                   HttpServletResponse resp) {
        Credential credential = getCredential(req);
        if (credential == null) {
            // redirect to authorization url
            try {
                resp.sendRedirect(credentialManager.getAuthorizationUrl());
            } catch (IOException e) {
                throw new RuntimeException("Can't redirect to auth page");
            }
        }
    }

    /**
     * If OAuth2 redirect callback is invoked and there is a code query param,
     * retrieve user credentials and profile. Then, redirect to the home page.
     * @param req   Request object.
     * @param resp  Response object.
     * @throws IOException
     */
    protected void handleCallbackIfRequired(HttpServletRequest req,
                                            HttpServletResponse resp) throws IOException {
        String code = req.getParameter("code");
        if (code != null) {
            // retrieve new credentials with code
            Credential credential = credentialManager.retrieve(code);
            // request userinfo
            Oauth2 service = getOauth2Service(credential);
            try {
                Userinfo about = service.userinfo().get().execute();
                String id = about.getId();
                credentialManager.save(id, credential);
                req.getSession().setAttribute(KEY_SESSION_USERID, id);
            } catch (IOException e) {
                throw new RuntimeException("Can't handle the OAuth2 callback, " +
                        "make sure that code is valid.");
            }
            resp.sendRedirect("/");
        }
    }

    /**
     * Returns the credentials of the user in the session. If user is not in the
     * session, returns null.
     * @param req   Request object.
     * @return      Credential object of the user in session or null.
     */
    protected Credential getCredential(HttpServletRequest req) {
        String userId = (String) req.getSession().getAttribute(KEY_SESSION_USERID);
        if (userId != null) {
            return credentialManager.get(userId);
        }
        return null;
    }

    /**
     * Deletes the credentials of the user in the session permanently and removes
     * the user from the session.
     * @param req   Request object.
     */
    protected void deleteCredential(HttpServletRequest req) {
        String userId = (String) req.getSession().getAttribute(KEY_SESSION_USERID);
        if (userId != null) {
            credentialManager.delete(userId);
            req.getSession().removeAttribute(KEY_SESSION_USERID);
        }
    }

    /**
     * Build and return a Drive service object based on given request parameters.
     * @param credential User credentials.
     * @return Drive service object that is ready to make requests, or null if
     *         there was a problem.
     */
    protected Drive getDriveService(Credential credential) {
        return new Drive.Builder(TRANSPORT, JSON_FACTORY, credential).build();
    }

    /**
     * Build and return an Oauth2 service object based on given request parameters.
     * @param credential User credentials.
     * @return Drive service object that is ready to make requests, or null if
     *         there was a problem.
     */
    protected Oauth2 getOauth2Service(Credential credential) {
        return new Oauth2.Builder(TRANSPORT, JSON_FACTORY, credential).build();
    }

    /**
     * Reads client_secrets.json and creates a GoogleClientSecrets object.
     * @return A GoogleClientsSecrets object.
     */
    private GoogleClientSecrets getClientSecrets() {
        // TODO: do not read on each request
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        InputStream stream =
                classloader.getResourceAsStream(CLIENT_SECRETS_FILE_PATH);
        try {
            return GoogleClientSecrets.load(JSON_FACTORY, stream);
        } catch (IOException e) {
            throw new RuntimeException("No client_secrets.json found");
        }
    }

    /**
     * Download the content of the given file.
     *
     * @param service Drive service to use for downloading.
     * @param file File metadata object whose content to download.
     * @return String representation of file content.  String is returned here
     *         because this app is setup for text/plain files.
     * @throws IOException Thrown if the request fails for whatever reason.
     */
    private String downloadFileContent(Drive service, File file)
            throws IOException {
        GenericUrl url = new GenericUrl(file.getDownloadUrl());
        HttpResponse response = service.getRequestFactory().buildGetRequest(url)
                .execute();
        try {
            return new Scanner(response.getContent()).useDelimiter("\\A").next();
        } catch (java.util.NoSuchElementException e) {
            return "";
        }
    }
}
