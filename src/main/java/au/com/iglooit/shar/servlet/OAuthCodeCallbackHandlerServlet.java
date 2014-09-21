package au.com.iglooit.shar.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: nicholas.zhu
 * Date: 19/09/2014
 * Time: 8:57 PM
 */
public class OAuthCodeCallbackHandlerServlet extends DrEditServlet {

    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // handle OAuth2 callback
        handleCallbackIfRequired(req, resp);
    }
}
