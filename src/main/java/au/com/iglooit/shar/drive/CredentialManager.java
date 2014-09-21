package au.com.iglooit.shar.drive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.appengine.auth.oauth2.AppEngineCredentialStore;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Credential manager to get, save, delete user credentials.
 *
 * @author jbd@google.com (Burcu Dogan)
 */
public class CredentialManager {

    /**
     * Client secrets object.
     */
    private GoogleClientSecrets clientSecrets;

    /**
     * Transport layer for OAuth2 client.
     */
    private HttpTransport transport;

    /**
     * JSON factory for OAuth2 client.
     */
    private JsonFactory jsonFactory;

    private String latestUserId;

    /**
     * Scopes for which to request access from the user.
     */
    public static final List<String> SCOPES = Arrays.asList(
            // Required to access and manipulate files.
            "https://www.googleapis.com/auth/drive.file",
            // Required to identify the user in our data store.
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile");

    /**
     * Credential store to get, save, delete user credentials.
     */
    private static AppEngineCredentialStore credentialStore =
            new AppEngineCredentialStore();

    /**
     * Credential Manager constructor.
     *
     * @param clientSecrets App client secrets to be used during OAuth2 exchanges.
     * @param transport     Transportation layer for OAuth2 client.
     * @param factory       JSON factory for OAuth2 client.
     */
    public CredentialManager(GoogleClientSecrets clientSecrets,
                             HttpTransport transport, JsonFactory factory) {
        this.clientSecrets = clientSecrets;
        this.transport = transport;
        this.jsonFactory = factory;
    }

    /**
     * Builds an empty credential object.
     *
     * @return An empty credential object.
     */
    public Credential buildEmpty() {
        return new GoogleCredential.Builder()
                .setClientSecrets(this.clientSecrets)
                .setTransport(transport)
                .setJsonFactory(jsonFactory)
                .build();
    }

    /**
     * Returns credentials of the given user, returns null if there are none.
     *
     * @param userId The id of the user.
     * @return A credential object or null.
     */
    public Credential get(String userId) {
        Credential credential = buildEmpty();
        if (credentialStore.load(userId, credential)) {
            return credential;
        }
        return null;
    }

    public Credential getDefault() {
        Credential credential = buildEmpty();
        // Get the Datastore Service
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Query query = new Query("DefaultUser");
        PreparedQuery pq = datastore.prepare(query);
        Iterator<Entity> iterable = pq.asIterable().iterator();
        if (iterable.hasNext()) {
            String userId = (String) iterable.next().getProperty("userId");
            if (credentialStore.load(userId, credential)) {
                return credential;
            }
        }

        return null;
    }

    /**
     * Saves credentials of the given user.
     *
     * @param userId     The id of the user.
     * @param credential A credential object to save.
     */
    public void save(String userId, Credential credential) {
        latestUserId = userId;
        saveOrUpdateCredential(userId, credential);
        credentialStore.store(userId, credential);
    }

    /**
     * Deletes credentials of the given user.
     *
     * @param userId The id of the user.
     */
    public void delete(String userId) {
        credentialStore.delete(userId, get(userId));
    }

    /**
     * Generates a consent page url.
     *
     * @return A consent page url string for user redirection.
     */
    public String getAuthorizationUrl() {
        GoogleAuthorizationCodeRequestUrl urlBuilder =
                new GoogleAuthorizationCodeRequestUrl(
                        clientSecrets.getWeb().getClientId(),
                        clientSecrets.getWeb().getRedirectUris().get(0),
                        SCOPES).setAccessType("offline").setApprovalPrompt("force");
        return urlBuilder.build();
    }

    /**
     * Retrieves a new access token by exchanging the given code with OAuth2
     * end-points.
     *
     * @param code Exchange code.
     * @return A credential object.
     */
    public Credential retrieve(String code) {
        try {
            GoogleTokenResponse response = new GoogleAuthorizationCodeTokenRequest(
                    transport,
                    jsonFactory,
                    clientSecrets.getWeb().getClientId(),
                    clientSecrets.getWeb().getClientSecret(),
                    code,
                    clientSecrets.getWeb().getRedirectUris().get(0)).execute();
            Credential credential = buildEmpty();
            credential.setAccessToken(response.getAccessToken());
            credential.setRefreshToken(response.getRefreshToken());
            return credential;
        } catch (IOException e) {
            new RuntimeException("An unknown problem occured while retrieving token");
        }
        return null;
    }

    /**
     * refresh token by stored token
     *
     * @return Credential
     */
    public Credential refreshToken() {
        Credential savedCredential = buildEmpty();
        // Get the Datastore Service
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        String userId = "";
        Query query = new Query("DefaultUser");
        PreparedQuery pq = datastore.prepare(query);
        Iterator<Entity> iterable = pq.asIterable().iterator();
        if (iterable.hasNext()) {
            userId = (String) iterable.next().getProperty("userId");
            if (credentialStore.load(userId, savedCredential)) {

            }
        }
        GoogleCredential credentials = new GoogleCredential.Builder()
                .setClientSecrets(clientSecrets.getWeb().getClientId(),
                        clientSecrets.getWeb().getClientSecret())
                .setJsonFactory(jsonFactory)
                .setTransport(transport).build()
                .setRefreshToken(savedCredential.getRefreshToken())
                .setAccessToken(savedCredential.getAccessToken());
        saveOrUpdateCredential(userId, credentials);
        return credentials;
    }

    private void saveOrUpdateCredential(String userId, Credential credential) {
        Entity defaultUser = null;
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Query query = new Query("DefaultUser");
        PreparedQuery pq = datastore.prepare(query);
        Iterator<Entity> iterable = pq.asIterable().iterator();
        if (iterable.hasNext()) {
            defaultUser = iterable.next();
        } else {
            Key key = KeyFactory.createKey("id", userId);
            defaultUser = new Entity("DefaultUser", key);
        }
        defaultUser.setProperty("userId", userId);
        defaultUser.setProperty("accessToken", credential.getAccessToken());
        defaultUser.setProperty("refreshToken", credential.getRefreshToken());

        datastore.put(defaultUser);
    }
}

